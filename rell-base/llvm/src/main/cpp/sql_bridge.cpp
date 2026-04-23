// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// sql_bridge.cpp — the SQL / database JNI back-call for the Rell LLVM backend.
//
// SQL is where the JIT deliberately stops. DbAt/ColAt at-expressions and Update/Delete writes
// generate PostgreSQL through DbSqlGen and run against a live JDBC connection inside the
// interpreter; none of that is reproducible as native IR, and re-deriving it in C++ would be a
// determinism (and maintenance) catastrophe. So this file is a THIN trampoline: given a live
// JVM frame handle and a dense db-node id, it calls straight back into the JVM interpreter and
// marshals the single result (for at-exprs) or a status int (for writes) across JNI.
//
//   DbAtExpr / ColAtExpr      -> rell_db_eval_expr -> Llvm_DbBridge.evalExpr(id, frame)
//                                  -> Rt_Interpreter.evaluateExpr(expr, frame)
//   UpdateStatement / Delete  -> rell_db_exec_stmt -> Llvm_DbBridge.execStmt(id, frame)
//                                  -> Rt_InterpreterImpl.executeStmt(stmt, frame)
//
// The JVM-side Llvm_DbBridge (built in the Wiring phase, the mirror of Llvm_SysBridge) holds the
// dense, walk-order Array<RR-node> the DbNodeTable interned here, plus the live Llvm_CallEnv
// addressed by RellCallCtx.frameHandle. Native code never sees an RR_Expr / Rt_Frame directly:
// it passes (nodeId, frameHandle) and lets the JVM resolve both. This keeps the C++ side free of
// any SQL knowledge and keeps the two id spaces in lockstep (see DbNodeTable::intern and ABI §5).
//
// EMBEDDING POLICY (what stays native around a db call vs. what soft-fails) — see the long
// // DETERMINISM: note over rell_db_eval_expr: an at-expression USED AS A VALUE inside an
// otherwise-native function can stay native — the enclosing IR just makes one JNI call and gets a
// RellValue back. Update/Delete STATEMENTS likewise embed as a status-returning JNI call. What we
// do NOT attempt here is splitting a db sub-tree (a DbExpr buried inside the WHERE/what of an
// at-expr) out for native evaluation: the whole at-node is handed to the JVM as one unit. Any db
// shape the lowering files cannot express as "evaluate this whole node via JNI" must EmitContext
// ::fail() the enclosing function (the jni_bridge return-0 soft-fail), never emit partial SQL.

#include <jni.h>

#include <mutex>

#include "app_generated.h"
#include "rell_runtime.h"

namespace ir = rell::ir;

namespace rell::llvm_rt {

// =====================================================================================
// Cached JVM db-bridge handles.
//
// Mirrors the discipline of ensureRellRefsInitialized (§4c): looked up once under a
// std::once_flag against the cached g_vm's JNIEnv, and a missing class/method is a HARD ABI
// breakage (the Wiring phase failed to ship Llvm_DbBridge) — throwRuntime + return a poison
// result, NEVER a soft-fail. The class ref is promoted to a global so it survives past the
// initialising JNI frame; the jmethodIDs are stable for the lifetime of the loaded class.
//
// Llvm_DbBridge is the SQL mirror of Llvm_SysBridge: a JVM object indexed by the same dense,
// walk-order id space this file's DbNodeTable assigns, holding the live Llvm_CallEnv addressed by
// RellCallCtx.frameHandle. Its two entry points:
//
//   evalExpr(I J)Ljava/lang/Object;   // (nodeId, frameHandle) -> Rt_Value  (DbAt/ColAt)
//   execStmt(I J)I                    // (nodeId, frameHandle) -> status int (Update/Delete)
// =====================================================================================

namespace {

std::once_flag g_dbBridgeInit;
bool g_dbBridgeOk = false;

jclass g_dbBridgeClass = nullptr;       // global ref to net.postchain.rell.llvm.Llvm_DbBridge
jmethodID g_evalExprMethod = nullptr;   // static Object evalExpr(int, long)
jmethodID g_execStmtMethod = nullptr;   // static int    execStmt(int, long)

// One-time resolution of the JVM db-bridge entry points. Sets g_dbBridgeOk on success; leaves it
// false (and clears any FindClass/GetStaticMethodID pending exception) on a hard ABI miss so the
// caller can raise a clean RuntimeException. We deliberately do NOT abort the process here
// (unlike a value-marshalling miss) because the SQL bridge is only reached lazily, the first time
// a JIT'd function embeds a db node; surfacing it as a Java exception is friendlier and still
// non-recoverable from the native side.
void initDbBridgeOnce(JNIEnv *env) {
    std::call_once(g_dbBridgeInit, [env]() {
        jclass local = env->FindClass("net/postchain/rell/llvm/Llvm_DbBridge");
        if (local == nullptr) {
            // FindClass left NoClassDefFoundError pending; clear it — the caller turns the
            // !g_dbBridgeOk state into its own throwRuntime with a precise message.
            if (env->ExceptionCheck() == JNI_TRUE) env->ExceptionClear();
            return;
        }
        auto *global = reinterpret_cast<jclass>(env->NewGlobalRef(local));
        env->DeleteLocalRef(local);
        if (global == nullptr) return;

        jmethodID eval =
            env->GetStaticMethodID(global, "evalExpr", "(IJ)Ljava/lang/Object;");
        jmethodID exec = env->GetStaticMethodID(global, "execStmt", "(IJ)I");
        if (eval == nullptr || exec == nullptr) {
            if (env->ExceptionCheck() == JNI_TRUE) env->ExceptionClear();
            env->DeleteGlobalRef(global);
            return;
        }

        g_dbBridgeClass = global;
        g_evalExprMethod = eval;
        g_execStmtMethod = exec;
        g_dbBridgeOk = true;
    });
}

}  // namespace

// =====================================================================================
// DbNodeTable — dense db-node interning, walk-order mirrored JVM-side.
//
// Same contract/shape as SysFnTable (ABI §4/§5): the lowering walk calls intern() once per
// DbAt/ColAt/Update/Delete node it encounters, in a FIXED deterministic order; the returned id is
// the index into the JVM-side dense node array Llvm_DbBridge holds. Unlike SysFnTable there is no
// name to dedup on — a db node is identified positionally — so intern() just hands out the next
// id and bumps the count. The JVM side rebuilds the exact same array by replaying the identical
// RR-tree walk, so ids agree on both sides without any cross-boundary name table.
//
// DETERMINISM: the id space is consensus-irrelevant in itself (it never reaches the chain), but
// it MUST be a pure function of the walk order so a given RR node maps to the same native call
// site every compile. The walk that drives intern() lives in the lowering files and is fixed;
// this counter just reflects it.
// =====================================================================================

DbNodeId DbNodeTable::intern() {
    return count_++;
}

// =====================================================================================
// rell_db_eval_expr — evaluate a DbAtExpr/ColAtExpr by id via the JVM interpreter.
//
// DETERMINISM / EMBEDDING: an at-expression appears in the RR-tree as an ordinary value-producing
// Expr (ExprUnion_DbAtExpr / _ColAtExpr). When such an expr sits inside an otherwise-native
// function body, the lowering files emit a single call to this entry instead of inline IR; the
// enclosing function stays native and just receives the RellValue result. We do NOT try to peel a
// db sub-expression (a DbExpr inside the at-node's where/what) out for separate native handling:
// SQL generation (DbSqlGen) and row materialisation are the JVM's job end-to-end, and re-deriving
// either in C++ risks a non-bit-exact result on a consensus path. The whole at-node is one JNI
// unit; anything finer-grained MUST soft-fail the enclosing function in the lowering pass.
//
// On a pending Rt_Exception from the JVM (e.g. cardinality violation: @{} matched 0 or >1 rows),
// we leave the exception pending and return rv_none() so the trampoline aborts the native frame
// and the JVM sees the same Rt_Exception the interpreter would have thrown (ABI §6).
// =====================================================================================

extern "C" RellValue rell_db_eval_expr(JNIEnv *env, DbNodeId nodeId, RellCallCtx *ctx) {
    if (env == nullptr || ctx == nullptr || ctx->arena == nullptr) {
        throwRuntime(env, "rell_db_eval_expr: null env/ctx/arena");
        return rv_none();
    }
    // kDbNodeIdNone means the lowering walk never interned this node — it should have soft-failed
    // the function instead of emitting a call here. Reaching it at run time is a hard ABI fault.
    if (nodeId == kDbNodeIdNone) {
        throwRuntime(env, "rell_db_eval_expr: uninterned db node (should have soft-failed)");
        return rv_none();
    }

    initDbBridgeOnce(env);
    if (!g_dbBridgeOk) {
        throwRuntime(env, "rell_db_eval_expr: Llvm_DbBridge.evalExpr unavailable");
        return rv_none();
    }

    // Push a small local frame: the call returns one Rt_Value local ref, and the JVM side may
    // allocate transient locals while materialising rows. 8 is ample for a single result + slack.
    if (env->PushLocalFrame(8) != 0) {
        // OutOfMemoryError pending from the JVM; propagate as a pending exception (§6).
        return rv_none();
    }

    jobject result = env->CallStaticObjectMethod(
        g_dbBridgeClass, g_evalExprMethod, static_cast<jint>(nodeId), ctx->frameHandle);

    if (exceptionPending(env)) {
        // Do NOT clear: pop the frame (NULL keeps nothing) and return poison; the JVM keeps the
        // pending Rt_Exception and the trampoline turns rv_none() into "abort". (§6)
        env->PopLocalFrame(nullptr);
        return rv_none();
    }

    // Unwrap the result into a RellValue. A null jobject from evalExpr would be an ABI fault
    // (Rell `null` is the Rt_NullValue singleton, never a JNI null) — from_jvm enforces that.
    // Heap/escaping results are adopt()-ed into ctx->arena, so we POP the local frame passing the
    // result through PopLocalFrame so the global ref the arena now owns is the survivor (the local
    // is discarded). from_jvm itself can leave a pending exception only via the hard-fault path,
    // which it raises as a Java exception; re-check after the pop to be safe.
    RellValue value = from_jvm(env, *ctx->arena, result);
    env->PopLocalFrame(nullptr);
    if (exceptionPending(env)) return rv_none();
    return value;
}

// =====================================================================================
// rell_db_exec_stmt — execute an UpdateStatement/DeleteStatement by id via the JVM interpreter.
//
// Pure side effect from native's view: the SQL UPDATE/DELETE runs JVM-side and we get back only a
// status int. Encoding mirrors Rt_InterpreterImpl.executeStmt's Rt_StatementResult? as the JVM
// bridge flattens it:
//   0           -> NORMAL / no control-flow result (the only outcome a db write produces).
//   negative    -> a Java exception is pending; abort the native frame (§6).
// A db write never yields RETURN/BREAK/CONTINUE, so a non-zero, non-negative status is unexpected;
// we still return it verbatim and let the trampoline decide (it treats only negative as abort).
//
// DETERMINISM: row counts, cardinality checks, and constraint violations are all the JVM's; we
// neither inspect nor reorder them. Returning the JVM's status unchanged keeps the native path
// bit-identical to the interpreter for the enclosing function.
// =====================================================================================

extern "C" int32_t rell_db_exec_stmt(JNIEnv *env, DbNodeId nodeId, RellCallCtx *ctx) {
    if (env == nullptr || ctx == nullptr) {
        throwRuntime(env, "rell_db_exec_stmt: null env/ctx");
        return -1;
    }
    if (nodeId == kDbNodeIdNone) {
        throwRuntime(env, "rell_db_exec_stmt: uninterned db node (should have soft-failed)");
        return -1;
    }

    initDbBridgeOnce(env);
    if (!g_dbBridgeOk) {
        throwRuntime(env, "rell_db_exec_stmt: Llvm_DbBridge.execStmt unavailable");
        return -1;
    }

    // A write returns no value but the JVM may use transient locals while binding/executing the
    // statement; a small frame keeps those from leaking into the caller's frame.
    if (env->PushLocalFrame(4) != 0) {
        return -1;  // OOM pending (§6).
    }

    jint status = env->CallStaticIntMethod(
        g_dbBridgeClass, g_execStmtMethod, static_cast<jint>(nodeId), ctx->frameHandle);

    if (exceptionPending(env)) {
        env->PopLocalFrame(nullptr);
        return -1;  // pending Rt_Exception; abort (§6).
    }

    env->PopLocalFrame(nullptr);
    return static_cast<int32_t>(status);
}

}  // namespace rell::llvm_rt
