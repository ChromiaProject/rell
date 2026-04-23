// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_runtime.h — authoritative shared header for the Rell LLVM backend native runtime.
//
// This header is the single source of truth that every per-category .cpp file in the LLVM
// backend includes. It declares:
//
//   1. The tagged `RellValue` struct + `RellTag` enum — the C++-side value representation:
//      inline primitives (boolean / integer / rowid / long-fitting decimal / long-fitting
//      big_integer) carried by value, and an opaque `HANDLE` (a JNI global ref to a JVM
//      `Rt_Value`) for everything else (text, byte_array, collections, struct, entity, gtv,
//      json, and any decimal/bigint outside the long-fitting envelope).
//
//   2. `RellArena` — a per-call RAII bump owner of every JNI global ref created during one
//      native function invocation. Released in a single sweep at call exit.
//
//   3. The marshalling ABI — native `RellValue` <-> jobject `Rt_Value`, both directions,
//      with the lazily-cached jclass/jfieldID/jmethodID tables that back it.
//
//   4. The universal JNI stdlib caller `rell_sysfn_call(...)` plus the interned sysfn-id table
//      type — the correctness floor that makes 100% of the stdlib reachable from JIT'd code
//      by calling BACK into the JVM's `R_SysFunction`.
//
//   5. The SQL back-call decls — `DbAtExpr`/`ColAtExpr` evaluation and `Update`/`Delete`
//      execution routed into the JVM interpreter, with the interned node-id tables.
//
//   6. The Lowerer-facing `EmitContext` — the emit-time bundle (LLVMContext&, Module&,
//      IRBuilder&, param/var slot map, sysfn-id table, db-node-id table, box/unbox helpers,
//      and the fail()/soft-fail signal) shared by the per-category lowering files.
//
// Style mirrors jni_bridge.cpp exactly: `namespace ir = rell::ir;`, the
// fail()/throwRuntime/throwIllegalArgument idioms, LLVM 19+ IRBuilder API, defensive
// null-checks even after VerifyAppBuffer.
//
// CORRECTNESS RULE (consensus-critical): anything a lowering pass cannot reproduce
// bit-exactly must either (a) become a HANDLE that routes the value through the JVM, or
// (b) make the WHOLE function soft-fail (the jni_bridge return-0 path) so the JVM
// interpreter runs it. Never emit incorrect IR. Determinism is non-negotiable.

#ifndef RELL_RUNTIME_H
#define RELL_RUNTIME_H

#include <jni.h>

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include <llvm/IR/DerivedTypes.h>
#include <llvm/IR/Function.h>
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/Type.h>
#include <llvm/IR/Value.h>

#include "app_generated.h"

namespace ir = rell::ir;

namespace rell::llvm_rt {

// =====================================================================================
// Shared error helpers (same idioms as jni_bridge.cpp; defined once in value.cpp).
//
// throwIllegalArgument / throwRuntime raise a *Java* exception across JNI for HARD ABI
// faults (null cached ID, malformed handle, out-of-range index). They are NOT the
// soft-fail path: "this node is outside the JIT envelope" is signalled by a return-0 /
// EmitContext::fail(), never by throwing.
// =====================================================================================

void throwIllegalArgument(JNIEnv *env, const char *message);
void throwRuntime(JNIEnv *env, const char *message);

// Cached JavaVM*, captured in JNI_OnLoad (jni_bridge.cpp owns g_vm). Attaches/returns the
// JNIEnv* for the current thread; used by back-call entry points that don't already hold one.
JavaVM *jvmHandle();

// =====================================================================================
// 1. Tagged value representation
// =====================================================================================

// Discriminant for RellValue. uint8_t so the struct stays 16 bytes.
//
// HANDLE is the universal escape hatch: any value the inline lattice cannot represent —
// INCLUDING a decimal/bigint that overflows the long envelope — is carried as a global-ref
// handle to its JVM Rt_Value. Correctness rule: when unsure, produce a HANDLE (route through
// the JVM), never an incorrect inline.
enum class RellTag : uint8_t {
    NONE        = 0,  // uninitialized / poison; never a valid live value. Also the JIT
                      //   trampoline's "exception pending — abort" sentinel (see §7).
    NULL_       = 1,  // Rell `null` — the Rt_NullValue singleton (distinct from a C++ nullptr).
    UNIT        = 2,  // Rt_UnitValue singleton.
    BOOLEAN     = 3,  // inline; payload.i64 in {0,1} only.
    INTEGER     = 4,  // inline i64 (full range).
    ROWID       = 5,  // inline i64, invariant >= 0 (mirrors Rt_RowidValue's init check).
    DEC_LONG    = 6,  // long-fitting decimal: mantissa in payload.i64, scale in `scale`.
                      //   Represents mantissa / 10^scale at natural STRIPPED scale
                      //   (mirrors Tf_LongScaleDecimal: 1.5 -> (mantissa=15, scale=1)).
    BIGINT_LONG = 7,  // long-fitting big_integer: exact i64 in payload.i64 (|v| <= Long.MAX).
    HANDLE      = 8,  // opaque, arena-owned JNI global ref (jobject) to ANY Rt_Value.
};

// 16-byte POD, trivially copyable. Passed by value through the LLVM calling convention as
// the struct type produced by rellValueLlvmType() == { i8 tag, i32 scale, i64 payload }.
//
// No constructors/destructors: the lifetime of an embedded HANDLE jobject is owned by the
// RellArena that created it, NEVER by this struct. Copying a RellValue does not copy the
// global ref — copies alias the same arena-owned handle.
struct RellValue {
    RellTag tag;     // offset 0
    int32_t scale;   // offset 4 — meaningful ONLY when tag == DEC_LONG; otherwise 0.
    union {
        int64_t i64;     // BOOLEAN(0/1), INTEGER, ROWID, BIGINT_LONG, DEC_LONG mantissa.
        jobject handle;  // HANDLE: arena-owned JNI global ref to an Rt_Value.
    } payload;       // offset 8
};

static_assert(sizeof(RellValue) == 16, "RellValue ABI is 16 bytes");
static_assert(sizeof(jobject) <= sizeof(int64_t), "jobject must fit the i64 payload slot");

// ---- inline constructors (header-only; cheap, no JNI) -------------------------------

inline RellValue rv_none() { return RellValue{RellTag::NONE, 0, {0}}; }
inline RellValue rv_null() { return RellValue{RellTag::NULL_, 0, {0}}; }
inline RellValue rv_unit() { return RellValue{RellTag::UNIT, 0, {0}}; }

inline RellValue rv_boolean(bool b) {
    RellValue v{RellTag::BOOLEAN, 0, {0}};
    v.payload.i64 = b ? 1 : 0;
    return v;
}
inline RellValue rv_integer(int64_t n) {
    RellValue v{RellTag::INTEGER, 0, {0}};
    v.payload.i64 = n;
    return v;
}
inline RellValue rv_rowid(int64_t n) {
    // Caller MUST ensure n >= 0 (envelope predicate); negative is a caller bug.
    RellValue v{RellTag::ROWID, 0, {0}};
    v.payload.i64 = n;
    return v;
}
inline RellValue rv_dec_long(int64_t mantissa, int32_t scale) {
    RellValue v{RellTag::DEC_LONG, scale, {0}};
    v.payload.i64 = mantissa;
    return v;
}
inline RellValue rv_bigint_long(int64_t n) {
    RellValue v{RellTag::BIGINT_LONG, 0, {0}};
    v.payload.i64 = n;
    return v;
}
inline RellValue rv_handle(jobject globalRef) {
    RellValue v{RellTag::HANDLE, 0, {0}};
    v.payload.handle = globalRef;
    return v;
}

inline bool rv_is_none(const RellValue &v) { return v.tag == RellTag::NONE; }
inline bool rv_is_handle(const RellValue &v) { return v.tag == RellTag::HANDLE; }

// =====================================================================================
// 2. Envelope predicates (the long-fitting gate) — pure C++, no JNI.
//
// Intrinsics use these to decide inline vs. escape. The C++ side does NOT reimplement
// Lib_DecimalMath.scale()/Lib_BigIntegerMath bounds: the DEC_LONG / BIGINT_LONG inline path
// is only ever ENTERED for (a) constants/values the JVM already validated and handed across
// as (mantissa, scale) / i64, or (b) results of pure intrinsics that stayed in-envelope.
// Anything else escapes to a JVM reboxing producing a HANDLE.
//
// Rell decimals are NOT MathContext.DECIMAL128: the JVM envelope is integer-part digits
// <= Lib_DecimalMath.DECIMAL_INT_DIGITS (131072) with fraction held to DECIMAL_FRAC_DIGITS
// (20). The intrinsic fast path is the proven Tf_LongScaleDecimal trick: scale <= 18 (the
// POW10 table bound) AND the stripped unscaled value fits i64.
// =====================================================================================

// Maximum DEC_LONG scale the native intrinsics will act on (POW10 table bound). Beyond this,
// or for any cross-leaf / scale-mismatched op, fall through to the JVM. Mirrors
// Tf_LongScaleDecimal.MAX_LONG_SCALE.
constexpr int32_t kDecLongMaxScale = 18;

// True iff a DEC_LONG with this scale is safe for the pure C++ intrinsic fast paths.
inline bool dec_long_intrinsic_safe(int32_t scale) {
    return scale >= 0 && scale <= kDecLongMaxScale;
}

// BigInteger: the whole inline envelope is "fits i64". (Long.MIN..Long.MAX maps 1:1 to
// BIGINT_LONG; anything wider must be a HANDLE.) This predicate is for results computed in
// i64 space; arbitrary BigIntegers from the JVM are gated by the unwrap path (§4a) which
// reuses BigInteger.bitLength rather than guessing in C++.
inline bool bigint_long_fits(int64_t /*v*/) { return true; }

// =====================================================================================
// 3. Per-call arena
//
// RellArena owns every JNI global ref created during one native function invocation. It is
// RAII-scoped to the JNI entry trampoline that JIT'd code returns through, so partial native
// execution that throws still releases all refs. Not thread-shared; one arena per JNI call
// frame.
// =====================================================================================

class RellArena {
public:
    explicit RellArena(JNIEnv *env);
    ~RellArena();

    RellArena(const RellArena &) = delete;
    RellArena &operator=(const RellArena &) = delete;

    // Promote a local (or any) ref to a tracked global ref and wrap it as a HANDLE RellValue.
    // The passed-in ref is NOT deleted here (the JNI frame owns locals); the arena adds and
    // later releases a global ref. Only ever called when boxing a heap/escaping value.
    RellValue adopt(jobject rtValue);

    // Raw tracked-global-ref creation for internal marshalling use: NewGlobalRef + record,
    // returns the global ref. Returns nullptr (no record) if `ref` is nullptr.
    jobject track(jobject ref);

    JNIEnv *env() const { return env_; }
    std::size_t size() const { return globals_.size(); }

private:
    JNIEnv *env_;
    std::vector<jobject> globals_;  // bump list; each DeleteGlobalRef'd in the dtor.
};

// =====================================================================================
// 4. Marshalling ABI — native RellValue <-> jobject Rt_Value
//
// Backed by RellRefs (§4c), a table of cached jclass/jfieldID/jmethodID populated once under
// std::once_flag against the cached g_vm's JNIEnv. A missing class/ID is a build/ABI breakage
// and aborts hard (throwRuntime), never a soft-fail.
// =====================================================================================

// 4a. JVM -> native (unwrap). Reads a jobject Rt_Value into a RellValue. Heap/complex values
//     and out-of-envelope decimal/bigint are arena.adopt()-ed to a HANDLE. A null jobject
//     where a value is required is a HARD error (throwIllegalArgument), NOT Rell `null`.
RellValue from_jvm(JNIEnv *env, RellArena &arena, jobject rtValue);

// 4b. native -> JVM (rebox). Materializes a RellValue back to a jobject Rt_Value, returning a
//     LOCAL ref owned by the caller's JNI frame (if it must escape into another arena, adopt
//     it there). For HANDLE, returns the arena-owned global ref directly (the arena still
//     owns it). Inline tags call the cached factories (Rt_IntValue.get, Rt_BooleanValue.get,
//     Rt_RowidValue.get, Rt_BigIntegerValue.get, the long-scale decimal ctor, the
//     Rt_NullValue/Rt_UnitValue singletons). These factories can throw Rt_Exception (e.g.
//     decimal overflow, negative rowid); the caller MUST ExceptionCheck after (see §7).
jobject to_jvm(JNIEnv *env, RellArena &arena, const RellValue &value);

// 4c. Cached IDs table. Lazily initialized once; aborts hard if any lookup is null. Holds
//     global jclass refs + the primitive jfieldIDs (Rt_IntValue/Rt_BooleanValue/Rt_RowidValue
//     value fields — real backing fields of @JvmRecord/data-class vals), the getter jmethodIDs
//     for the interface-backed Rt_DecimalValue/Rt_BigIntegerValue `.value`, the @JvmStatic
//     factory method IDs, and the Companion object global refs where a factory lives on the
//     Kotlin companion (Rt_RowidValue.get, Rt_BigIntegerValue.get).
void ensureRellRefsInitialized(JNIEnv *env);

// =====================================================================================
// 5. Universal JNI stdlib caller — the correctness floor.
//
// Any RR_FunctionCallTarget.{SysGlobal,SysMember,NativeUser} / MemberCalculator.SysFunction /
// SysQueryBody reachable BY NAME becomes callable from native code: rell_sysfn_call boxes the
// RellValue args to an Object[] of reboxed Rt_Values (inside a pushed local frame), invokes
// the JVM dispatch entry (Llvm_SysBridge.dispatch(sysfnId, args, frameHandle)) which indexes a
// dense Array<R_SysFunction?> and calls R_SysFunction.call(ctx, args) — the SAM all arities
// funnel through — then unwraps the single Rt_Value result back via from_jvm (§4a),
// adopt()-ing an escaping result into `ctx.arena`.
// =====================================================================================

// Interned sysfn id. Built at JIT time by a deterministic FlatBuffer walk that collects every
// FnTarget_{SysGlobal,SysMember,NativeUser}.fn_name() and MemberCalculator_SysFunction
// .fn_name() / SysQueryBody.fn_name(); the inverse name table is read JVM-side to build the
// dense R_SysFunction array. -1 means "name not interned" (must soft-fail, never call).
using SysFnId = int32_t;
constexpr SysFnId kSysFnIdNone = -1;

// Forward-decl: the per-call context threaded as a hidden trailing param into every JIT'd
// function and every back-call. See §6.
struct RellCallCtx;

// The universal caller. nargs RellValues at `args`; returns the unwrapped result. On a pending
// Rt_Exception from the JVM call, returns rv_none() WITHOUT clearing the exception (§7).
extern "C" RellValue rell_sysfn_call(JNIEnv *env, SysFnId sysfnId, const RellValue *args,
                                     int32_t nargs, RellCallCtx *ctx);

// Interning table: maps a stdlib fn name (the FlatBuffer String) to a dense SysFnId. The same
// deterministic walk is mirrored JVM-side so the id spaces agree. Built once per compiled App.
class SysFnTable {
public:
    SysFnTable() = default;

    // Intern a name, returning its dense id (creating one on first sight). Deterministic:
    // ids are assigned in first-encounter order over the fixed RR-tree walk.
    SysFnId intern(const std::string &name);

    // Look up an already-interned name; kSysFnIdNone if absent.
    SysFnId lookup(const std::string &name) const;

    // The id-ordered name list the JVM side consumes to build the dense R_SysFunction array.
    const std::vector<std::string> &names() const { return names_; }
    std::size_t size() const { return names_.size(); }

private:
    std::unordered_map<std::string, SysFnId> byName_;
    std::vector<std::string> names_;  // index == SysFnId
};

// =====================================================================================
// 6. SQL back-call — DbAt/ColAt evaluation and Update/Delete execution via the interpreter.
//
// DbAtExpr/ColAtExpr -> rell_db_eval_expr -> Rt_Interpreter.evaluateExpr(expr, frame) (already
// on the interface; reached via the Llvm_Backend). UpdateStatement/DeleteStatement ->
// rell_db_exec_stmt -> Rt_InterpreterImpl.executeStmt(stmt, frame) (internal, reached via
// Llvm_Backend.delegate). Nodes are interned to dense int32 ids over a fixed deterministic
// RR-tree walk mirrored on both sides; the native side never builds SQL itself.
//
// RellCallCtx carries the opaque frame handle (a jlong handle to the JVM Llvm_CallEnv wrapping
// the live Rt_Frame — opaque to native code; the exact descriptor is the Wiring phase's
// choice) plus the active arena and the interning tables. It is threaded as a hidden trailing
// param into every JIT'd function and back-call.
// =====================================================================================

struct RellCallCtx {
    JNIEnv *env;          // current-thread JNIEnv.
    RellArena *arena;     // per-call arena owning all transient global refs.
    jlong frameHandle;    // opaque handle to the JVM Llvm_CallEnv (live Rt_Frame). 0 if none.
    const SysFnTable *sysFns;  // sysfn interning table (read-only at run time).
};

// Interned db-node id (DbAtExpr/ColAtExpr/UpdateStatement/DeleteStatement). Same kSysFnIdNone
// convention: -1 means "not interned — soft-fail".
using DbNodeId = int32_t;
constexpr DbNodeId kDbNodeIdNone = -1;

// Evaluate a DbAtExpr/ColAtExpr by id, calling back into Rt_Interpreter.evaluateExpr. Returns
// the unwrapped result; rv_none() (exception left pending) on a JVM Rt_Exception (§7).
extern "C" RellValue rell_db_eval_expr(JNIEnv *env, DbNodeId nodeId, RellCallCtx *ctx);

// Execute an UpdateStatement/DeleteStatement by id via Rt_InterpreterImpl.executeStmt. The
// Rt_StatementResult? is encoded to int32 (0 == NORMAL/no-result); negative on pending
// exception (§7). Pure side-effect from native's view (returns a status, not a RellValue).
extern "C" int32_t rell_db_exec_stmt(JNIEnv *env, DbNodeId nodeId, RellCallCtx *ctx);

// Db-node interning table — same shape/contract as SysFnTable but over the deterministic
// DbAt/ColAt/Update/Delete walk. Ids mirrored JVM-side.
class DbNodeTable {
public:
    DbNodeTable() = default;
    DbNodeId intern();  // assign the next dense id in walk order.
    std::size_t size() const { return count_; }

private:
    int32_t count_ = 0;
};

// =====================================================================================
// 7. Exception / error propagation across JNI.
//
// After EVERY call back into the JVM (rell_sysfn_call, rell_db_eval_expr, rell_db_exec_stmt,
// and any rebox/unwrap whose factory can throw — Rt_DecimalValue.get overflow, Rt_RowidValue
// negative), the caller MUST env->ExceptionCheck(). On a pending exception: do NOT clear it.
// Stop native execution, let the arena destructor run (DeleteGlobalRef is exception-safe), and
// return the poison sentinel rv_none() (or, for statements, a negative status) so the JIT
// trampoline boundary turns it into "exception pending — abort". The JVM then sees the thrown
// Rt_Exception, identical to interpreter behaviour.
//
// "Outside the JIT envelope" is NOT an exception: it is a HANDLE (route the value) or an
// EmitContext::fail() function-level soft-fail. Only genuine Rell runtime errors (Rt_Exception)
// and hard ABI faults raise across JNI.
// =====================================================================================

// True iff a JVM exception is pending; convenience wrapper used at every back-call boundary.
inline bool exceptionPending(JNIEnv *env) { return env->ExceptionCheck() == JNI_TRUE; }

// =====================================================================================
// 8. Lowerer-facing emit context.
//
// EmitContext is the bundle the per-category lowering files (lower_expr / lower_ops /
// lower_stmt / lower_call) and the intrinsic overlays share. It carries the LLVM emit state,
// the param/var slot map (mirroring jni_bridge's paramIndexByPtr_ keyed on
// {block_uid, offset}), the sysfn/db interning tables, the box/unbox helpers between an
// llvm::Value* carrying a RellValue (the runtime value SSA) and the host i64 fast paths, and
// the fail()/soft-fail signal that propagates the jni_bridge return-0 semantics.
//
// RUNTIME-VALUE LLVM REPRESENTATION: a Rell value flowing through JIT'd code is an
// llvm::Value* of type rellValueLlvmType() == { i8, i32, i64 } (matching RellValue's ABI).
// Pure intrinsics that have proven their operands are INTEGER/BOOLEAN/ROWID/DEC_LONG/
// BIGINT_LONG may extract the i64 payload, compute in i64 space, and re-pack — staying inside
// the inline envelope. The moment an operand or result might escape the envelope, the lowering
// must route through a runtime call (rell_sysfn_call / box to HANDLE) or fail().
// =====================================================================================

// LLVM struct type for a runtime RellValue: { i8 tag, i32 scale, i64 payload }. Field order
// MUST match the RellValue C++ layout. Defined once (lower_expr.cpp) and cached per Module.
llvm::StructType *rellValueLlvmType(llvm::LLVMContext &ctx);

// Reuse jni_bridge's VarPtr (block_uid, offset) map key so the slot map is interchangeable.
struct VarPtrKey {
    uint32_t block_uid;
    int32_t offset;
    bool operator==(const VarPtrKey &o) const {
        return block_uid == o.block_uid && offset == o.offset;
    }
};

struct VarPtrKeyHash {
    std::size_t operator()(const VarPtrKey &k) const noexcept {
        return std::hash<uint64_t>{}((uint64_t(k.block_uid) << 32) | uint32_t(k.offset));
    }
};

class EmitContext {
public:
    EmitContext(llvm::LLVMContext &ctx, llvm::Module &module, llvm::IRBuilder<> &builder,
                const ir::App &app, SysFnTable &sysFns, DbNodeTable &dbNodes,
                std::string &errorOut)
        : ctx_(ctx),
          module_(module),
          builder_(builder),
          app_(app),
          sysFns_(sysFns),
          dbNodes_(dbNodes),
          error_(errorOut) {}

    EmitContext(const EmitContext &) = delete;
    EmitContext &operator=(const EmitContext &) = delete;

    llvm::LLVMContext &ctx() const { return ctx_; }
    llvm::Module &module() const { return module_; }
    llvm::IRBuilder<> &builder() const { return builder_; }
    const ir::App &app() const { return app_; }
    SysFnTable &sysFns() const { return sysFns_; }
    DbNodeTable &dbNodes() const { return dbNodes_; }

    // The runtime value SSA type ({ i8, i32, i64 }) for this module.
    llvm::StructType *valueType() { return rellValueLlvmType(ctx_); }

    // ---- soft-fail signal (mirrors jni_bridge's Lowerer::fail) -----------------------
    // Record the first failure reason and return nullptr/false so callers can early-out.
    // A failed EmitContext makes the WHOLE function soft-fail (compileFunctionByIndex
    // returns 0; the JVM interpreter runs it). NEVER emit IR after fail().
    std::nullptr_t failExpr(const std::string &msg) {
        fail(msg);
        return nullptr;
    }
    bool fail(const std::string &msg) {
        if (error_.empty()) error_ = msg;
        failed_ = true;
        return false;
    }
    bool failed() const { return failed_; }
    const std::string &error() const { return error_; }

    // ---- param/var slot map ----------------------------------------------------------
    // A VarExpr.ptr matching an entry is a parameter/local read; payload is the LLVM slot
    // (an alloca of valueType(), or the i64 GEP into the args pointer for the integer slice).
    std::unordered_map<VarPtrKey, llvm::Value *, VarPtrKeyHash> &slots() { return slots_; }
    llvm::Value *slotFor(const ir::VarPtr &vp) {
        auto it = slots_.find({vp.block_uid(), vp.offset()});
        return it == slots_.end() ? nullptr : it->second;
    }

    // ---- box/unbox between a host i64 fast path and a runtime-value SSA ---------------
    // pack: build a { i8, i32, i64 } aggregate from a tag constant, an i32 scale, and an i64
    //   payload. unpackPayloadI64: extract field 2 (the i64 payload). unpackTag / unpackScale:
    //   extract fields 0/1. These are pure IR shape ops — they do NOT validate the envelope;
    //   the CALLER must have proven the value stays inline before computing in i64 space.
    llvm::Value *packInline(RellTag tag, llvm::Value *scaleI32, llvm::Value *payloadI64);
    llvm::Value *packInteger(llvm::Value *payloadI64) {
        return packInline(RellTag::INTEGER, builder_.getInt32(0), payloadI64);
    }
    llvm::Value *unpackPayloadI64(llvm::Value *runtimeValue);
    llvm::Value *unpackTag(llvm::Value *runtimeValue);     // -> i8
    llvm::Value *unpackScale(llvm::Value *runtimeValue);   // -> i32

private:
    llvm::LLVMContext &ctx_;
    llvm::Module &module_;
    llvm::IRBuilder<> &builder_;
    const ir::App &app_;
    SysFnTable &sysFns_;
    DbNodeTable &dbNodes_;
    std::string &error_;
    bool failed_ = false;
    std::unordered_map<VarPtrKey, llvm::Value *, VarPtrKeyHash> slots_;
};

// =====================================================================================
// 9. Per-category lowering + intrinsic entry points.
//
// Each lowering file exposes one entry the others call; all return an llvm::Value* of
// valueType() (the runtime RellValue SSA) or nullptr after ec.fail(). The intrinsic overlays
// (intrinsics_*) take already-unpacked operands and either produce an in-envelope result or
// signal "escaped" so the caller routes to rell_sysfn_call.
// =====================================================================================

// lower_expr.cpp — the Expr-union dispatcher (VarExpr, ConstantValueExpr, IfExpr, etc.).
llvm::Value *lowerExpr(EmitContext &ec, const ir::Expr &expr);

// lower_ops.cpp — BinaryExpr/UnaryExpr (+ CmpInfo comparisons), dispatching into the
// intrinsics_* overlays; routes out-of-envelope ops to rell_sysfn_call.
llvm::Value *lowerBinary(EmitContext &ec, const ir::BinaryExpr &bin);
llvm::Value *lowerUnary(EmitContext &ec, const ir::UnaryExpr &un);

// lower_stmt.cpp — the Stmt-union dispatcher (Block/Return/If/While/For/Assign/Var/...).
// Returns true on success; false (after ec.fail()) makes the function soft-fail.
bool lowerStmt(EmitContext &ec, const ir::Stmt &stmt);

// lower_call.cpp — FunctionCallExpr / MemberExpr, routing SysGlobal/SysMember/NativeUser/
// SysFunction targets to rell_sysfn_call and DB members to the SQL back-call.
llvm::Value *lowerCall(EmitContext &ec, const ir::FunctionCallExpr &call);
llvm::Value *lowerMember(EmitContext &ec, const ir::MemberExpr &member);

// Intrinsic overlays. Each takes the unpacked i64 operands (already proven in-envelope by the
// caller) plus the EmitContext for IR emission, and returns the result runtime value, or
// nullptr with `*escaped = true` when the result/operands fall outside the safe envelope (the
// caller then emits the rell_sysfn_call slow path). `*escaped` is never set true together with
// a non-null return.
llvm::Value *intrinsicInteger(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                              bool *escaped);
llvm::Value *intrinsicDecimal(EmitContext &ec, ir::BinaryOp op, llvm::Value *aMant,
                              llvm::Value *aScale, llvm::Value *bMant, llvm::Value *bScale,
                              bool *escaped);
llvm::Value *intrinsicBigInteger(EmitContext &ec, ir::BinaryOp op, llvm::Value *a,
                                 llvm::Value *b, bool *escaped);
llvm::Value *intrinsicText(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                           bool *escaped);
llvm::Value *intrinsicByteArray(EmitContext &ec, ir::BinaryOp op, llvm::Value *a,
                                llvm::Value *b, bool *escaped);
llvm::Value *intrinsicMath(EmitContext &ec, SysFnId fn, const llvm::ArrayRef<llvm::Value *> args,
                           bool *escaped);

}  // namespace rell::llvm_rt

#endif  // RELL_RUNTIME_H
