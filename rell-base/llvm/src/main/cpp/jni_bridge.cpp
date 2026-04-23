// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// Native side of the Rell LLVM backend.
//
// The prototype lowers a Rell function body directly from the FlatBuffers-serialized
// RR_App on the C++ side and JIT-compiles it through ORC. Anything the lowering pass
// cannot handle (non-integer types, control flow, calls, db, etc.) makes the compile
// step report "not JITable" so the Kotlin side falls back to Rt_InterpreterImpl.
//
// The longer-term plan (see llvm.md) is to keep extending the IR coverage on this side
// and to call back into the JVM (Rt_InterpreterImpl + stdlib) for the remaining gaps —
// the GraalVM-style "interpreter when unsupported" pattern. The JNI callback scaffold
// (jvmFallbackInit / cached JavaVM*) is the seed of that bridge.

#include <jni.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <vector>

#include <llvm/Config/llvm-config.h>
#include <llvm/ExecutionEngine/Orc/JITTargetMachineBuilder.h>
#include <llvm/ExecutionEngine/Orc/LLJIT.h>
#include <llvm/ExecutionEngine/Orc/ExecutionUtils.h>
#include <llvm/ExecutionEngine/Orc/ThreadSafeModule.h>
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/Intrinsics.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/Verifier.h>
#include <llvm/Support/Error.h>
#include <llvm/Support/TargetSelect.h>

#include "app_generated.h"
#include "rell_numeric_ops.h"

namespace ir = rell::ir;

// =====================================================================================
// Module summarizer — unchanged from the earlier slice; surfaced for RellLlvmNativeTest.
// =====================================================================================

namespace {

std::string joinModuleName(const ir::ModuleName *name) {
    if (name == nullptr) return std::string();
    const auto *parts = name->parts();
    if (parts == nullptr) return std::string();
    std::string out;
    for (flatbuffers::uoffset_t i = 0; i < parts->size(); ++i) {
        if (i > 0) out.push_back('.');
        const auto *part = parts->Get(i);
        if (part != nullptr) out.append(part->c_str(), part->size());
    }
    return out;
}

void throwIllegalArgument(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/IllegalArgumentException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

void throwRuntime(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_summarizeApp(JNIEnv *env, jobject, jbyteArray bytes) {
    if (bytes == nullptr) {
        throwIllegalArgument(env, "bytes is null");
        return nullptr;
    }
    const jsize length = env->GetArrayLength(bytes);
    if (length <= 0) {
        throwIllegalArgument(env, "bytes is empty");
        return nullptr;
    }
    jbyte *data = env->GetByteArrayElements(bytes, nullptr);
    if (data == nullptr) return nullptr;

    std::ostringstream oss;
    {
        flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t *>(data),
                                       static_cast<size_t>(length));
        if (!ir::VerifyAppBuffer(verifier)) {
            env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
            throwIllegalArgument(env, "FlatBuffers verification failed");
            return nullptr;
        }

        const auto *app = ir::GetApp(data);
        llvm::LLVMContext probeContext;
        llvm::Module probeModule("rell-llvm-probe", probeContext);
        oss << "llvm=" << LLVM_VERSION_STRING
            << " probe-module=" << probeModule.getName().str() << "\n";

        const auto *modules = app->modules();
        oss << "modules=" << (modules != nullptr ? modules->size() : 0u) << "\n";
        if (modules != nullptr) {
            for (flatbuffers::uoffset_t i = 0; i < modules->size(); ++i) {
                const auto *m = modules->Get(i);
                if (m == nullptr) continue;
                oss << "- " << joinModuleName(m->name());
                if (m->test()) oss << " [test]";
                if (m->abstract()) oss << " [abstract]";
                if (m->external()) oss << " [external]";
                oss << "\n";
            }
        }
    }

    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return env->NewStringUTF(oss.str().c_str());
}

// =====================================================================================
// JNI callback scaffold (forward-looking).
//
// The lowering pass currently returns "not JITable" for any node it can't translate, and
// the Kotlin backend handles fallback at the Rt_Interpreter boundary. The next iteration
// will emit IR-level calls into JVM helpers — Rt_InterpreterImpl.evaluateExpr, stdlib
// functions, GTV codec, etc. — for unsupported nodes embedded inside an otherwise-
// compilable body. That needs a cached JavaVM* so JIT'd code can attach and dispatch.
// =====================================================================================

namespace {

JavaVM *g_vm = nullptr;

JavaVM *jvmHandle() {
    return g_vm;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_8;
}

// =====================================================================================
// ORC JIT setup.
// =====================================================================================

namespace {

std::once_flag g_jitInit;
std::unique_ptr<llvm::orc::LLJIT> g_jit;
std::atomic<uint64_t> g_fnCounter{0};
std::string g_jitInitError;

// Serialises addIRModule / lookup. JUnit runs our tests concurrently and the JIT pipeline
// is not internally thread-safe for module addition under contention on LLVM 22.
std::mutex g_jitMutex;

void initJitOnce() {
    std::call_once(g_jitInit, []() {
        llvm::InitializeNativeTarget();
        llvm::InitializeNativeTargetAsmPrinter();
        llvm::InitializeNativeTargetAsmParser();

        // Build the JIT with an explicit JITTargetMachineBuilder so the IR module's
        // data layout and target triple line up with the host. The default LLJITBuilder
        // is supposed to do this for us, but on LLVM 22 + macOS arm64 the AArch64
        // backend crashes inside CanLowerReturn unless we hand it a host-detected JTMB.
        auto jtmbOrErr = llvm::orc::JITTargetMachineBuilder::detectHost();
        if (!jtmbOrErr) {
            g_jitInitError = "detectHost: " + llvm::toString(jtmbOrErr.takeError());
            return;
        }
        auto jitOrErr = llvm::orc::LLJITBuilder()
                            .setJITTargetMachineBuilder(std::move(*jtmbOrErr))
                            .create();
        if (!jitOrErr) {
            g_jitInitError = llvm::toString(jitOrErr.takeError());
            return;
        }
        g_jit = std::move(*jitOrErr);

        // Let JIT'd code resolve the runtime back-call symbols (rell_sysfn_call, rell_db_eval_expr,
        // rell_db_exec_stmt — now extern "C") exported by librell-llvm in the host process.
        auto genOrErr = llvm::orc::DynamicLibrarySearchGenerator::GetForCurrentProcess(
            g_jit->getDataLayout().getGlobalPrefix());
        if (!genOrErr) {
            g_jitInitError = "symbol generator: " + llvm::toString(genOrErr.takeError());
            g_jit.reset();
            return;
        }
        g_jit->getMainJITDylib().addGenerator(std::move(*genOrErr));
    });
}

}  // namespace

// =====================================================================================
// RR_App → LLVM IR lowering.
//
// Compilable shape (the prototype's narrow gate):
//   - Function returns `integer`, all params are `integer`.
//   - Body is `return <expr>;` directly, or a single-statement Block containing a Return.
//   - <expr> is built from {VarExpr of integer param, ConstantValueExpr of IntValue,
//     BinaryExpr with op ∈ {ADD_INTEGER, SUB_INTEGER, MUL_INTEGER} on integer operands}.
//
// Overflow is unchecked (LLVM `add/sub/mul` wrap silently; the Rell interpreter uses
// LongMath.checked* — see Llvm_Backend's doc for why this is fine on the prototype slice).
// =====================================================================================

namespace {

// --- Integer-overflow error channel (mirrors the decimal `rell_num_pending` pattern) ----------
//
// The i64(i64*) calling convention has no out-of-band error slot, so JIT'd integer arithmetic
// signals an overflow the same way the decimal op glue signals its errors: a thread-local pending
// flag set by a back-call helper, read by callI64Function after the JIT'd function returns. The
// Kotlin side then raises the EXACT Rt_Exception the tree-walker would (same code + message),
// preserving consensus-identical behaviour. ADD/SUB/MUL/MINUS use llvm.{sadd,ssub,smul}.with.
// overflow checks (and 0 - x for MINUS) and, on the overflow bit, call rell_int_overflow into this
// channel before producing the wrapped value (which is then discarded by the pending check).
struct IntOverflowState {
    bool pending = false;
    char op = 0;       // '+', '-', '*'  (binary), or 'n' for unary negate
    int64_t a = 0;
    int64_t b = 0;
};

thread_local IntOverflowState g_intOverflow;

}  // namespace

// Back-call invoked by JIT'd integer arithmetic when an operation overflows. Records the operator
// and operands so the JVM can raise the precise Rt_Exception. Must be extern "C" so the JIT's
// process-symbol generator can resolve it by name. `op` is one of '+','-','*' (binary) or 'n'
// (unary negate); for 'n' only `a` is meaningful.
extern "C" void rell_int_overflow(char op, int64_t a, int64_t b) {
    if (!g_intOverflow.pending) {
        g_intOverflow.pending = true;
        g_intOverflow.op = op;
        g_intOverflow.a = a;
        g_intOverflow.b = b;
    }
}

namespace {

// Pair the (block_uid, offset) of a VarPtr struct so we can use it as a map key.
struct VarPtrKey {
    uint32_t block_uid;
    int32_t offset;
    bool operator==(const VarPtrKey &o) const {
        return block_uid == o.block_uid && offset == o.offset;
    }
};

struct VarPtrKeyHash {
    size_t operator()(const VarPtrKey &k) const noexcept {
        return std::hash<uint64_t>{}((uint64_t(k.block_uid) << 32) | uint32_t(k.offset));
    }
};

bool isIntegerType(const ir::Type *type) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    return prim != nullptr && prim->kind() == ir::PrimitiveTypeKind_INTEGER;
}

bool isBooleanType(const ir::Type *type) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    return prim != nullptr && prim->kind() == ir::PrimitiveTypeKind_BOOLEAN;
}

// integer OR boolean: both fit the i64 slot envelope (bool encoded as 0/1).
bool isIntOrBoolType(const ir::Type *type) {
    return isIntegerType(type) || isBooleanType(type);
}

// Extends the prototype i64 lowerer to full pure-integer/boolean control flow:
// general statement lowering (Block/Var/Assign/If/While/Break/Continue/Return/Empty/Expr) over
// i64 slots, integer/boolean expressions (comparisons, logical short-circuit, NOT, MINUS, IfExpr),
// and direct SELF-recursion. Every slot (param or local) is an i64 alloca in the entry block; a
// single return-value alloca + exit block carries all returns. Anything not provably bit-exact
// with the tree-walker (div/mod, cross-function calls, stdlib/sys, non-int/bool types, default
// params, when/for/lambda/db/...) makes the whole function soft-fail.
//
// Overflow on ADD/SUB/MUL and MINUS wraps silently, matching the existing prototype slice (the
// interpreter uses Math.*Exact; the prototype deliberately accepts the wrap difference).
class Lowerer {
public:
    // Generic constructor over the four body components, so a FunctionBody and a UserQueryBody
    // (structurally identical: ret type + params + param_ptrs + body Stmt) share one lowering core.
    // `retType` may be null for a constant-initializer entry (see runConstant), where there is no
    // statement body and the params/param_ptrs vectors are absent.
    Lowerer(llvm::LLVMContext &ctx,
            llvm::Module &module,
            const ir::Type *retType,
            const ::flatbuffers::Vector<::flatbuffers::Offset<ir::FunctionParam>> *params,
            const ::flatbuffers::Vector<const ir::VarPtr *> *paramPtrs,
            const ir::Stmt *bodyStmt,
            const std::string &fnName,
            int selfFunctionIndex,
            std::string &errorOut)
        : ctx_(ctx),
          module_(module),
          retType_(retType),
          params_(params),
          paramPtrs_(paramPtrs),
          bodyStmt_(bodyStmt),
          fnName_(fnName),
          selfFunctionIndex_(selfFunctionIndex),
          builder_(ctx),
          error_(errorOut) {}

    bool run() {
        if (!isIntegerType(retType_)) return fail("return type is not integer");

        const auto *params = params_;
        if (params == nullptr) return fail("missing params");
        const auto *paramPtrs = paramPtrs_;
        if (paramPtrs == nullptr || paramPtrs->size() != params->size()) {
            return fail("param_ptrs size mismatch");
        }
        for (flatbuffers::uoffset_t i = 0; i < params->size(); ++i) {
            const auto *p = params->Get(i);
            if (p == nullptr) return fail("null FunctionParam");
            if (!isIntegerType(p->type())) return fail("non-integer param type");
            if (p->default_expr() != nullptr) return fail("param has default expression");
        }

        // Function signature: `i64 @<name>(i64* %args)`. The caller (`callI64Function`)
        // passes a contiguous LongArray; per-slot loads via GEP keep the calling-convention
        // surface trivial (one pointer in, one i64 out) regardless of the param count.
        auto *i64Ty = builder_.getInt64Ty();
        auto *ptrTy = llvm::PointerType::getUnqual(ctx_);
        auto *fnTy = llvm::FunctionType::get(i64Ty, {ptrTy}, /*isVarArg=*/false);
        fn_ = llvm::Function::Create(fnTy, llvm::Function::ExternalLinkage, fnName_, &module_);
        selfFnTy_ = fnTy;
        argsPtr_ = fn_->getArg(0);
        argsPtr_->setName("args");
        auto *entry = llvm::BasicBlock::Create(ctx_, "entry", fn_);
        builder_.SetInsertPoint(entry);

        // Copy each incoming arg slot into a per-param i64 alloca, so params and locals are
        // treated uniformly (assignable l-values) and map a VarPtr → its alloca.
        for (flatbuffers::uoffset_t i = 0; i < paramPtrs->size(); ++i) {
            const auto *vp = paramPtrs->Get(i);
            if (vp == nullptr) return fail("null param VarPtr");
            auto *slot = builder_.CreateAlloca(i64Ty, nullptr, "p" + std::to_string(i));
            auto *src = builder_.CreateInBoundsGEP(i64Ty, argsPtr_, builder_.getInt64(i),
                                                   "arg_slot_" + std::to_string(i));
            builder_.CreateStore(builder_.CreateLoad(i64Ty, src, "arg_" + std::to_string(i)), slot);
            // The interpreter's frame storage is a flat array indexed by ptr.offset alone — the
            // block_uid is only a runtime sanity check (rt_frame.kt checkPtr). Offsets are
            // frame-global and disjoint across blocks, so the slot identity is the offset.
            slotByOffset_[vp->offset()] = slot;
        }

        // Single return path: a result alloca written by every Return, read once in the exit block.
        retAlloca_ = builder_.CreateAlloca(i64Ty, nullptr, "retval");
        retBlock_ = llvm::BasicBlock::Create(ctx_, "exit", fn_);

        const auto *stmt = bodyStmt_;
        if (stmt == nullptr) return fail("missing body Stmt");

        if (!lowerStmt(*stmt)) return false;

        // If control can fall off the end of the body without a Return, the Rell frontend
        // guarantees a unit-returning function — but this slice only accepts integer-returning
        // ones, where every path must return. A non-terminated current block here means we
        // could not prove that; soft-fail rather than emit an undef return.
        if (!builder_.GetInsertBlock()->getTerminator()) {
            return fail("control may fall off end of integer function body");
        }

        builder_.SetInsertPoint(retBlock_);
        builder_.CreateRet(builder_.CreateLoad(i64Ty, retAlloca_, "ret"));

        std::string verifyErr;
        llvm::raw_string_ostream verifyOs(verifyErr);
        if (llvm::verifyFunction(*fn_, &verifyOs)) {
            return fail("verifier: " + verifyErr);
        }
        return true;
    }

    // Entry point for a global constant initializer: build `i64 @<name>(i64* %args)` whose body is
    // `return <lowerExpr(initExpr)>`, with ZERO params (no slot map; `args` is never read). The
    // uniform i64*(->i64) signature lets the same callI64Function path invoke it. Integer-typed
    // constant only — any non-integer init expression soft-fails through lowerExpr/fail.
    bool runConstant(const ir::Type *constType, const ir::Expr &initExpr) {
        if (!isIntegerType(constType)) return fail("constant type is not integer");

        auto *i64Ty = builder_.getInt64Ty();
        auto *ptrTy = llvm::PointerType::getUnqual(ctx_);
        auto *fnTy = llvm::FunctionType::get(i64Ty, {ptrTy}, /*isVarArg=*/false);
        fn_ = llvm::Function::Create(fnTy, llvm::Function::ExternalLinkage, fnName_, &module_);
        selfFnTy_ = fnTy;
        argsPtr_ = fn_->getArg(0);
        argsPtr_->setName("args");
        auto *entry = llvm::BasicBlock::Create(ctx_, "entry", fn_);
        builder_.SetInsertPoint(entry);

        // No params, no locals: slotByOffset_ stays empty, so any VarExpr in the initializer
        // (a reference to another constant/value) finds no slot and soft-fails — exactly right,
        // since cross-definition references are outside the proven slice.
        llvm::Value *v = lowerExpr(initExpr);
        if (v == nullptr) return false;
        builder_.CreateRet(v);

        std::string verifyErr;
        llvm::raw_string_ostream verifyOs(verifyErr);
        if (llvm::verifyFunction(*fn_, &verifyOs)) {
            return fail("verifier: " + verifyErr);
        }
        return true;
    }

private:
    // --- Statements ---------------------------------------------------------

    // Returns false on soft-fail. After a successful call the current insert block may be
    // terminated (e.g. a Return / unconditional branch) — callers must check before emitting.
    bool lowerStmt(const ir::Stmt &stmt) {
        switch (stmt.stmt_type()) {
            case ir::StmtUnion_EmptyStatement: return true;
            case ir::StmtUnion_BlockStatement: return lowerBlock(*stmt.stmt_as_BlockStatement());
            case ir::StmtUnion_ExprStatement: return lowerExprStmt(*stmt.stmt_as_ExprStatement());
            case ir::StmtUnion_VarStatement: return lowerVarStmt(*stmt.stmt_as_VarStatement());
            case ir::StmtUnion_AssignStatement: return lowerAssign(*stmt.stmt_as_AssignStatement());
            case ir::StmtUnion_ReturnStatement: return lowerReturn(*stmt.stmt_as_ReturnStatement());
            case ir::StmtUnion_IfStatement: return lowerIf(*stmt.stmt_as_IfStatement());
            case ir::StmtUnion_WhileStatement: return lowerWhile(*stmt.stmt_as_WhileStatement());
            case ir::StmtUnion_BreakStatement: return lowerBreak();
            case ir::StmtUnion_ContinueStatement: return lowerContinue();
            default:
                return fail("unsupported StmtUnion variant: " +
                            std::to_string(static_cast<int>(stmt.stmt_type())));
        }
    }

    bool lowerBlock(const ir::BlockStatement &block) {
        const auto *stmts = block.stmts();
        if (stmts == nullptr) return fail("BlockStatement has null stmts");
        for (flatbuffers::uoffset_t i = 0; i < stmts->size(); ++i) {
            const auto *s = stmts->Get(i);
            if (s == nullptr) return fail("null statement in block");
            // Once the current block is terminated (Return/Break/Continue), the rest of the
            // block is dead code in this control path; stop emitting into a terminated block.
            if (builder_.GetInsertBlock()->getTerminator()) break;
            if (!lowerStmt(*s)) return false;
        }
        return true;
    }

    bool lowerExprStmt(const ir::ExprStatement &stmt) {
        const auto *e = stmt.expr();
        if (e == nullptr) return fail("ExprStatement has null expr");
        // Evaluate for side effects (e.g. a self-call), discard the value.
        return lowerExpr(*e) != nullptr;
    }

    bool lowerVarStmt(const ir::VarStatement &stmt) {
        const auto *decl = stmt.declarator();
        if (decl == nullptr) return fail("VarStatement has null declarator");
        const auto *simple =
            decl->declarator_as_SimpleVarDeclarator();
        if (simple == nullptr) {
            return fail("VarStatement declarator is not Simple (tuple/wildcard unsupported)");
        }
        if (!isIntOrBoolType(simple->type())) return fail("local var is not integer/boolean");
        const auto *ptr = simple->ptr();
        if (ptr == nullptr) return fail("VarStatement declarator has null ptr");
        // A non-DIRECT type adapter (int→bigint, int→decimal, nullable, ...) changes the stored
        // value's representation away from a plain i64 — soft-fail. DIRECT is an identity no-op.
        const auto *adapter = simple->adapter();
        if (adapter != nullptr && adapter->kind() != ir::TypeAdapterKind_DIRECT) {
            return fail("VarStatement has a value-changing type adapter");
        }

        llvm::Value *slot = entryAlloca("local");
        slotByOffset_[ptr->offset()] = slot;

        const auto *init = stmt.expr();
        if (init != nullptr) {
            llvm::Value *v = lowerExpr(*init);
            if (v == nullptr) return false;
            builder_.CreateStore(v, slot);
        } else {
            // Uninitialised local: the interpreter leaves the slot unset until first assignment.
            // Zero-init is observationally equivalent only if every read is dominated by a write,
            // which we cannot prove here — but a read of an unwritten Rell local is a compile
            // error upstream, so the slot is always written before use. Still, store 0 for a
            // defined value rather than leaking undef.
            builder_.CreateStore(builder_.getInt64(0), slot);
        }
        return true;
    }

    bool lowerAssign(const ir::AssignStatement &stmt) {
        const auto *dst = stmt.dst_expr();
        const auto *src = stmt.expr();
        if (dst == nullptr || src == nullptr) return fail("AssignStatement null operand");
        llvm::Value *slot = lvalueSlot(*dst);
        if (slot == nullptr) return false;

        auto *i64Ty = builder_.getInt64Ty();
        llvm::Value *rhs = lowerExpr(*src);
        if (rhs == nullptr) return false;

        // Compound assignment (`+=` etc.): combine current slot value with rhs via the same
        // wrapping integer arithmetic as the standalone binary ops. The `op` field is an
        // Optional<BinaryOp> — absent ⇒ plain `=`, present ⇒ compound. Only the wrapping
        // ADD/SUB/MUL family is supported; div/mod (and anything non-integer) soft-fails.
        const auto compoundOp = stmt.op();
        if (compoundOp.has_value()) {
            llvm::Value *cur = builder_.CreateLoad(i64Ty, slot, "cur");
            llvm::Value *combined = applyIntArith(compoundOp.value(), cur, rhs);
            if (combined == nullptr) return false;
            rhs = combined;
        }
        builder_.CreateStore(rhs, slot);
        return true;
    }

    bool lowerReturn(const ir::ReturnStatement &ret) {
        const auto *e = ret.expr();
        if (e == nullptr) return fail("integer function Return has no expression");
        llvm::Value *v = lowerExpr(*e);
        if (v == nullptr) return false;
        builder_.CreateStore(v, retAlloca_);
        builder_.CreateBr(retBlock_);
        return true;
    }

    bool lowerIf(const ir::IfStatement &stmt) {
        const auto *cond = stmt.cond();
        const auto *trueStmt = stmt.true_stmt();
        const auto *falseStmt = stmt.false_stmt();
        if (cond == nullptr || trueStmt == nullptr || falseStmt == nullptr) {
            return fail("IfStatement null child");
        }
        llvm::Value *condI1 = lowerCondI1(*cond);
        if (condI1 == nullptr) return false;

        auto *thenBB = llvm::BasicBlock::Create(ctx_, "if.then", fn_);
        auto *elseBB = llvm::BasicBlock::Create(ctx_, "if.else", fn_);
        auto *contBB = llvm::BasicBlock::Create(ctx_, "if.cont", fn_);
        builder_.CreateCondBr(condI1, thenBB, elseBB);

        builder_.SetInsertPoint(thenBB);
        if (!lowerStmt(*trueStmt)) return false;
        if (!builder_.GetInsertBlock()->getTerminator()) builder_.CreateBr(contBB);

        builder_.SetInsertPoint(elseBB);
        if (!lowerStmt(*falseStmt)) return false;
        if (!builder_.GetInsertBlock()->getTerminator()) builder_.CreateBr(contBB);

        builder_.SetInsertPoint(contBB);
        return true;
    }

    bool lowerWhile(const ir::WhileStatement &stmt) {
        const auto *cond = stmt.cond();
        const auto *bodyStmt = stmt.body();
        if (cond == nullptr || bodyStmt == nullptr) return fail("WhileStatement null child");

        auto *condBB = llvm::BasicBlock::Create(ctx_, "while.cond", fn_);
        auto *bodyBB = llvm::BasicBlock::Create(ctx_, "while.body", fn_);
        auto *exitBB = llvm::BasicBlock::Create(ctx_, "while.exit", fn_);
        builder_.CreateBr(condBB);

        builder_.SetInsertPoint(condBB);
        llvm::Value *condI1 = lowerCondI1(*cond);
        if (condI1 == nullptr) return false;
        builder_.CreateCondBr(condI1, bodyBB, exitBB);

        loopStack_.push_back({condBB, exitBB});
        builder_.SetInsertPoint(bodyBB);
        bool ok = lowerStmt(*bodyStmt);
        loopStack_.pop_back();
        if (!ok) return false;
        // `continue` jumps back to the condition; natural fall-through does too.
        if (!builder_.GetInsertBlock()->getTerminator()) builder_.CreateBr(condBB);

        builder_.SetInsertPoint(exitBB);
        return true;
    }

    bool lowerBreak() {
        if (loopStack_.empty()) return fail("break outside loop");
        builder_.CreateBr(loopStack_.back().breakBB);
        return true;
    }

    bool lowerContinue() {
        if (loopStack_.empty()) return fail("continue outside loop");
        builder_.CreateBr(loopStack_.back().continueBB);
        return true;
    }

    // Resolve an assignable destination expression to its i64 slot pointer.
    llvm::Value *lvalueSlot(const ir::Expr &expr) {
        const auto *var = expr.expr_as_VarExpr();
        if (var == nullptr) {
            fail("assignment target is not a simple variable");
            return nullptr;
        }
        if (!isIntOrBoolType(var->type())) {
            fail("assignment target is not integer/boolean");
            return nullptr;
        }
        const auto *ptr = var->ptr();
        if (ptr == nullptr) {
            fail("assignment target VarExpr.ptr is null");
            return nullptr;
        }
        auto it = slotByOffset_.find(ptr->offset());
        if (it == slotByOffset_.end()) {
            fail("assignment target is not a known param/local slot");
            return nullptr;
        }
        return it->second;
    }

    // --- Expressions --------------------------------------------------------

    llvm::Value *lowerExpr(const ir::Expr &expr) {
        switch (expr.expr_type()) {
            case ir::ExprUnion_VarExpr: return lowerVar(*expr.expr_as_VarExpr());
            case ir::ExprUnion_ConstantValueExpr: return lowerConst(*expr.expr_as_ConstantValueExpr());
            case ir::ExprUnion_BinaryExpr: return lowerBinary(*expr.expr_as_BinaryExpr());
            case ir::ExprUnion_UnaryExpr: return lowerUnary(*expr.expr_as_UnaryExpr());
            case ir::ExprUnion_IfExpr: return lowerIfExpr(*expr.expr_as_IfExpr());
            case ir::ExprUnion_FunctionCallExpr: return lowerSelfCall(*expr.expr_as_FunctionCallExpr());
            default: {
                fail("unsupported ExprUnion variant: " +
                     std::to_string(static_cast<int>(expr.expr_type())));
                return nullptr;
            }
        }
    }

    llvm::Value *lowerVar(const ir::VarExpr &var) {
        if (!isIntOrBoolType(var.type())) {
            fail("VarExpr has non-integer/boolean type");
            return nullptr;
        }
        const auto *ptr = var.ptr();
        if (ptr == nullptr) {
            fail("VarExpr.ptr is null");
            return nullptr;
        }
        auto it = slotByOffset_.find(ptr->offset());
        if (it == slotByOffset_.end()) {
            fail("VarExpr does not reference a known param/local slot");
            return nullptr;
        }
        return builder_.CreateLoad(builder_.getInt64Ty(), it->second, "ld");
    }

    llvm::Value *lowerConst(const ir::ConstantValueExpr &c) {
        const auto *tv = c.typed_value();
        if (tv == nullptr) {
            fail("ConstantValueExpr has no TypedValue");
            return nullptr;
        }
        if (isIntegerType(tv->type())) {
            const auto *intVal = tv->value_as_IntValue();
            if (intVal == nullptr) {
                fail("integer ConstantValueExpr ValueUnion is not IntValue");
                return nullptr;
            }
            return builder_.getInt64(intVal->value());
        }
        if (isBooleanType(tv->type())) {
            const auto *boolVal = tv->value_as_BoolValue();
            if (boolVal == nullptr) {
                fail("boolean ConstantValueExpr ValueUnion is not BoolValue");
                return nullptr;
            }
            return builder_.getInt64(boolVal->value() ? 1 : 0);
        }
        fail("ConstantValueExpr type is not integer/boolean");
        return nullptr;
    }

    // Wrapping integer arithmetic shared by BinaryExpr and compound assignment. Returns
    // nullptr (after fail()) for any op outside the proven ADD/SUB/MUL slice — notably
    // DIV/MOD, which can fault (div0 / MIN/-1) and have no i64-convention way to raise.
    llvm::Value *applyIntArith(ir::BinaryOp op, llvm::Value *a, llvm::Value *b) {
        switch (op) {
            case ir::BinaryOp_ADD_INTEGER:
                return emitCheckedArith(llvm::Intrinsic::sadd_with_overflow, '+', a, b);
            case ir::BinaryOp_SUB_INTEGER:
                return emitCheckedArith(llvm::Intrinsic::ssub_with_overflow, '-', a, b);
            case ir::BinaryOp_MUL_INTEGER:
                return emitCheckedArith(llvm::Intrinsic::smul_with_overflow, '*', a, b);
            default:
                fail("unsupported integer BinaryOp (div/mod and others soft-fail): " +
                     std::to_string(static_cast<int>(op)));
                return nullptr;
        }
    }

    // Emit overflow-CHECKED i64 arithmetic matching the interpreter's Math.{add,subtract,multiply}
    // Exact: on the overflow bit, call rell_int_overflow(op, a, b) (the pending-error channel) so
    // the JVM raises the exact Rt_Exception(expr:<op>:overflow:a:b). The wrapped value is still
    // produced for a defined SSA result; it is discarded once the pending flag is observed after
    // the function returns. The branch keeps the common (non-overflow) path straight-line.
    llvm::Value *emitCheckedArith(llvm::Intrinsic::ID id, char opChar, llvm::Value *a, llvm::Value *b) {
        auto *i64Ty = builder_.getInt64Ty();
        auto *fn = llvm::Intrinsic::getOrInsertDeclaration(&module_, id, {i64Ty});
        llvm::Value *agg = builder_.CreateCall(fn, {a, b}, "ovf_agg");
        llvm::Value *res = builder_.CreateExtractValue(agg, {0}, "arith");
        llvm::Value *ovf = builder_.CreateExtractValue(agg, {1}, "ovf");

        auto *ovfBB = llvm::BasicBlock::Create(ctx_, "ovf.raise", fn_);
        auto *contBB = llvm::BasicBlock::Create(ctx_, "ovf.cont", fn_);
        builder_.CreateCondBr(ovf, ovfBB, contBB);

        builder_.SetInsertPoint(ovfBB);
        emitOverflowCall(opChar, a, b);
        builder_.CreateBr(contBB);

        builder_.SetInsertPoint(contBB);
        return res;
    }

    // Emit a call to the extern "C" rell_int_overflow(char op, i64 a, i64 b) back-call.
    void emitOverflowCall(char opChar, llvm::Value *a, llvm::Value *b) {
        auto *i8Ty = builder_.getInt8Ty();
        auto *i64Ty = builder_.getInt64Ty();
        auto *voidTy = llvm::Type::getVoidTy(ctx_);
        auto *fnTy = llvm::FunctionType::get(voidTy, {i8Ty, i64Ty, i64Ty}, /*isVarArg=*/false);
        llvm::FunctionCallee callee = module_.getOrInsertFunction("rell_int_overflow", fnTy);
        builder_.CreateCall(callee, {builder_.getInt8(static_cast<uint8_t>(opChar)), a, b});
    }

    llvm::Value *lowerBinary(const ir::BinaryExpr &bin) {
        // Comparison dispatch: a non-null cmp() means this is LT/GT/LE/GE; bin.op() is unused.
        if (bin.cmp() != nullptr) return lowerComparison(bin);

        // Logical AND/OR are short-circuit — evaluate operands lazily across basic blocks.
        if (bin.op() == ir::BinaryOp_AND || bin.op() == ir::BinaryOp_OR) {
            return lowerLogical(bin);
        }

        const auto *left = bin.left();
        const auto *right = bin.right();
        if (left == nullptr || right == nullptr) {
            fail("BinaryExpr has null operand");
            return nullptr;
        }

        // EQ/NE on integer/boolean operands: value equality is an i64 icmp (bools are 0/1).
        if (bin.op() == ir::BinaryOp_EQ || bin.op() == ir::BinaryOp_NE) {
            if (!operandIsIntOrBool(*left) || !operandIsIntOrBool(*right)) {
                fail("EQ/NE operand is not integer/boolean");
                return nullptr;
            }
            if (!isBooleanType(bin.type())) {
                fail("EQ/NE result type is not boolean");
                return nullptr;
            }
            llvm::Value *a = lowerExpr(*left);
            if (a == nullptr) return nullptr;
            llvm::Value *b = lowerExpr(*right);
            if (b == nullptr) return nullptr;
            llvm::Value *cmp = bin.op() == ir::BinaryOp_EQ
                                   ? builder_.CreateICmpEQ(a, b, "eq")
                                   : builder_.CreateICmpNE(a, b, "ne");
            return builder_.CreateZExt(cmp, builder_.getInt64Ty(), "eqz");
        }

        // Arithmetic: integer result only (the wrapping ADD/SUB/MUL slice).
        if (!isIntegerType(bin.type())) {
            fail("BinaryExpr result is not integer");
            return nullptr;
        }
        llvm::Value *a = lowerExpr(*left);
        if (a == nullptr) return nullptr;
        llvm::Value *b = lowerExpr(*right);
        if (b == nullptr) return nullptr;
        return applyIntArith(bin.op(), a, b);
    }

    // True iff an operand expression's static type is integer or boolean (best-effort: only the
    // variants this lowerer can produce carry a readable type field).
    bool operandIsIntOrBool(const ir::Expr &e) {
        switch (e.expr_type()) {
            case ir::ExprUnion_VarExpr:
                return isIntOrBoolType(e.expr_as_VarExpr()->type());
            case ir::ExprUnion_ConstantValueExpr: {
                const auto *tv = e.expr_as_ConstantValueExpr()->typed_value();
                return tv != nullptr && isIntOrBoolType(tv->type());
            }
            case ir::ExprUnion_BinaryExpr:
                return isIntOrBoolType(e.expr_as_BinaryExpr()->type());
            case ir::ExprUnion_UnaryExpr:
                return isIntOrBoolType(e.expr_as_UnaryExpr()->type());
            case ir::ExprUnion_IfExpr:
                return isIntOrBoolType(e.expr_as_IfExpr()->type());
            case ir::ExprUnion_FunctionCallExpr:
                return isIntOrBoolType(e.expr_as_FunctionCallExpr()->type());
            default:
                return false;
        }
    }

    // LT/GT/LE/GE on integer or boolean. Boolean: false<true, so a 0/1 unsigned compare matches
    // Kotlin Boolean.compareTo. Integer: signed compare. Result is zext'd to i64 0/1.
    llvm::Value *lowerComparison(const ir::BinaryExpr &bin) {
        const auto *cmp = bin.cmp();
        const auto *left = bin.left();
        const auto *right = bin.right();
        if (left == nullptr || right == nullptr) {
            fail("comparison has null operand");
            return nullptr;
        }
        bool isInt = cmp->cmp_type() == ir::CmpType_INTEGER;
        bool isBool = cmp->cmp_type() == ir::CmpType_BOOLEAN;
        if (!isInt && !isBool) {
            fail("comparison cmp_type is not integer/boolean");
            return nullptr;
        }
        if (!isBooleanType(bin.type())) {
            fail("comparison result type is not boolean");
            return nullptr;
        }
        llvm::Value *a = lowerExpr(*left);
        if (a == nullptr) return nullptr;
        llvm::Value *b = lowerExpr(*right);
        if (b == nullptr) return nullptr;

        llvm::Value *res = nullptr;
        switch (cmp->op()) {
            case ir::CmpOp_LT:
                res = isInt ? builder_.CreateICmpSLT(a, b, "lt") : builder_.CreateICmpULT(a, b, "lt");
                break;
            case ir::CmpOp_GT:
                res = isInt ? builder_.CreateICmpSGT(a, b, "gt") : builder_.CreateICmpUGT(a, b, "gt");
                break;
            case ir::CmpOp_LE:
                res = isInt ? builder_.CreateICmpSLE(a, b, "le") : builder_.CreateICmpULE(a, b, "le");
                break;
            case ir::CmpOp_GE:
                res = isInt ? builder_.CreateICmpSGE(a, b, "ge") : builder_.CreateICmpUGE(a, b, "ge");
                break;
            default:
                fail("unsupported CmpOp");
                return nullptr;
        }
        return builder_.CreateZExt(res, builder_.getInt64Ty(), "cmpz");
    }

    // Short-circuit AND/OR over basic blocks, mirroring the interpreter's shortCircuitBinaryOp:
    //   AND: if !left → false, else evaluate right;  OR: if left → true, else evaluate right.
    llvm::Value *lowerLogical(const ir::BinaryExpr &bin) {
        const auto *left = bin.left();
        const auto *right = bin.right();
        if (left == nullptr || right == nullptr) {
            fail("logical op has null operand");
            return nullptr;
        }
        if (!isBooleanType(bin.type()) || !operandIsIntOrBool(*left) || !operandIsIntOrBool(*right)) {
            fail("logical op operands/result not boolean");
            return nullptr;
        }
        bool isAnd = bin.op() == ir::BinaryOp_AND;

        llvm::Value *lv = lowerExpr(*left);
        if (lv == nullptr) return nullptr;
        llvm::Value *lBool = builder_.CreateICmpNE(lv, builder_.getInt64(0), "lbool");
        llvm::BasicBlock *entryBB = builder_.GetInsertBlock();

        auto *rhsBB = llvm::BasicBlock::Create(ctx_, isAnd ? "and.rhs" : "or.rhs", fn_);
        auto *contBB = llvm::BasicBlock::Create(ctx_, isAnd ? "and.cont" : "or.cont", fn_);
        // AND: take rhs only when left is true; OR: take rhs only when left is false.
        if (isAnd) {
            builder_.CreateCondBr(lBool, rhsBB, contBB);
        } else {
            builder_.CreateCondBr(lBool, contBB, rhsBB);
        }

        builder_.SetInsertPoint(rhsBB);
        llvm::Value *rv = lowerExpr(*right);
        if (rv == nullptr) return nullptr;
        llvm::Value *rBool = builder_.CreateICmpNE(rv, builder_.getInt64(0), "rbool");
        llvm::BasicBlock *rhsEnd = builder_.GetInsertBlock();
        builder_.CreateBr(contBB);

        builder_.SetInsertPoint(contBB);
        auto *phi = builder_.CreatePHI(builder_.getInt1Ty(), 2, "logic");
        // From the entry path the result is determined by the short-circuit: AND→false, OR→true.
        phi->addIncoming(builder_.getInt1(!isAnd), entryBB);
        phi->addIncoming(rBool, rhsEnd);
        return builder_.CreateZExt(phi, builder_.getInt64Ty(), "logicz");
    }

    llvm::Value *lowerUnary(const ir::UnaryExpr &un) {
        const auto *operand = un.expr();
        if (operand == nullptr) {
            fail("UnaryExpr has null operand");
            return nullptr;
        }
        switch (un.op()) {
            case ir::UnaryOp_NOT: {
                if (!isBooleanType(un.type())) {
                    fail("NOT result is not boolean");
                    return nullptr;
                }
                llvm::Value *v = lowerExpr(*operand);
                if (v == nullptr) return nullptr;
                llvm::Value *isZero = builder_.CreateICmpEQ(v, builder_.getInt64(0), "notc");
                return builder_.CreateZExt(isZero, builder_.getInt64Ty(), "notz");
            }
            case ir::UnaryOp_MINUS_INTEGER: {
                if (!isIntegerType(un.type())) {
                    fail("MINUS_INTEGER result is not integer");
                    return nullptr;
                }
                llvm::Value *v = lowerExpr(*operand);
                if (v == nullptr) return nullptr;
                // -x is Math.negateExact: only Long.MIN_VALUE overflows (its negation is itself).
                // Detect that exact input and route to the overflow channel ('n' = unary negate),
                // matching the interpreter's Rt_Exception(expr:-:overflow:<v>).
                llvm::Value *isMin = builder_.CreateICmpEQ(v, builder_.getInt64(INT64_MIN), "neg_min");
                auto *ovfBB = llvm::BasicBlock::Create(ctx_, "neg.ovf", fn_);
                auto *contBB = llvm::BasicBlock::Create(ctx_, "neg.cont", fn_);
                builder_.CreateCondBr(isMin, ovfBB, contBB);
                builder_.SetInsertPoint(ovfBB);
                emitOverflowCall('n', v, builder_.getInt64(0));
                builder_.CreateBr(contBB);
                builder_.SetInsertPoint(contBB);
                return builder_.CreateSub(builder_.getInt64(0), v, "neg");
            }
            default:
                fail("unsupported UnaryOp (only NOT / MINUS_INTEGER): " +
                     std::to_string(static_cast<int>(un.op())));
                return nullptr;
        }
    }

    // cond ? a : b. Both arms are pure i64 expressions (no statements); use a select when both
    // can be emitted into the current block. If either arm itself needs basic blocks (short-circuit
    // logic / nested if-expr), the eager evaluation below still works because those helpers create
    // their own blocks and leave the builder at a single merge point before we read the value.
    llvm::Value *lowerIfExpr(const ir::IfExpr &ife) {
        const auto *cond = ife.cond();
        const auto *trueExpr = ife.true_expr();
        const auto *falseExpr = ife.false_expr();
        if (cond == nullptr || trueExpr == nullptr || falseExpr == nullptr) {
            fail("IfExpr null child");
            return nullptr;
        }
        if (!isIntOrBoolType(ife.type())) {
            fail("IfExpr result is not integer/boolean");
            return nullptr;
        }
        llvm::Value *condI1 = lowerCondI1(*cond);
        if (condI1 == nullptr) return nullptr;

        // Branch-based lowering so each arm is only evaluated on its taken path — this preserves
        // the interpreter's semantics (the untaken arm, possibly a self-call, must not run).
        auto *thenBB = llvm::BasicBlock::Create(ctx_, "ife.then", fn_);
        auto *elseBB = llvm::BasicBlock::Create(ctx_, "ife.else", fn_);
        auto *contBB = llvm::BasicBlock::Create(ctx_, "ife.cont", fn_);
        builder_.CreateCondBr(condI1, thenBB, elseBB);

        builder_.SetInsertPoint(thenBB);
        llvm::Value *tv = lowerExpr(*trueExpr);
        if (tv == nullptr) return nullptr;
        llvm::BasicBlock *thenEnd = builder_.GetInsertBlock();
        builder_.CreateBr(contBB);

        builder_.SetInsertPoint(elseBB);
        llvm::Value *fv = lowerExpr(*falseExpr);
        if (fv == nullptr) return nullptr;
        llvm::BasicBlock *elseEnd = builder_.GetInsertBlock();
        builder_.CreateBr(contBB);

        builder_.SetInsertPoint(contBB);
        auto *phi = builder_.CreatePHI(builder_.getInt64Ty(), 2, "ifev");
        phi->addIncoming(tv, thenEnd);
        phi->addIncoming(fv, elseEnd);
        return phi;
    }

    // Only a direct call to THE SAME function being compiled is inlined as a recursive call.
    // Any other target (different user fn, stdlib/sys, operation, query, partial, ...) soft-fails.
    llvm::Value *lowerSelfCall(const ir::FunctionCallExpr &callExpr) {
        if (callExpr.base() != nullptr) {
            fail("FunctionCallExpr has a base (member call) — unsupported");
            return nullptr;
        }
        if (callExpr.safe()) {
            fail("safe call (?.) unsupported");
            return nullptr;
        }
        if (!isIntegerType(callExpr.type())) {
            fail("self-call result type is not integer");
            return nullptr;
        }
        const auto *call = callExpr.call();
        if (call == nullptr) {
            fail("FunctionCallExpr has null call");
            return nullptr;
        }
        const auto *full = call->call_as_FullFunctionCall();
        if (full == nullptr) {
            fail("only full (non-partial) calls supported");
            return nullptr;
        }
        const auto *targetWrap = full->target();
        if (targetWrap == nullptr) {
            fail("call has null target");
            return nullptr;
        }
        const auto *regular = targetWrap->target_as_FnTarget_RegularUser();
        if (regular == nullptr) {
            fail("call target is not a regular user function");
            return nullptr;
        }
        if (static_cast<int>(regular->fn_def_index()) != selfFunctionIndex_) {
            fail("call target is a different user function (cross-call unsupported)");
            return nullptr;
        }

        const auto *args = full->args();
        const auto *mapping = full->mapping();
        if (args == nullptr || mapping == nullptr) {
            fail("self-call missing args/mapping");
            return nullptr;
        }
        const auto *params = params_;
        const uint32_t paramCount = params != nullptr ? params->size() : 0;
        if (args->size() != paramCount || mapping->size() != paramCount) {
            fail("self-call arg count differs from param count (defaults/varargs unsupported)");
            return nullptr;
        }

        // Evaluate args, then marshal into a fresh i64 array matching the i64* convention. The
        // mapping[i] is the parameter position each positional arg fills; require identity so the
        // slot order is exactly param order (no reordering / named-arg surprises).
        auto *i64Ty = builder_.getInt64Ty();
        llvm::Value *argArray =
            entryAllocaArray(i64Ty, paramCount, "callargs");
        for (uint32_t i = 0; i < paramCount; ++i) {
            if (mapping->Get(i) != static_cast<int32_t>(i)) {
                fail("self-call argument mapping is not identity");
                return nullptr;
            }
            const auto *argExpr = args->Get(i);
            if (argExpr == nullptr) {
                fail("null self-call argument");
                return nullptr;
            }
            llvm::Value *v = lowerExpr(*argExpr);
            if (v == nullptr) return nullptr;
            auto *slot = builder_.CreateInBoundsGEP(i64Ty, argArray, builder_.getInt64(i),
                                                    "callarg_" + std::to_string(i));
            builder_.CreateStore(v, slot);
        }
        return builder_.CreateCall(selfFnTy_, fn_, {argArray}, "selfcall");
    }

    // Lower a boolean condition expression to an i1 (for CondBr). The interpreter treats the
    // condition as a Boolean value; our boolean envelope is i64 0/1, so `!= 0` recovers the i1.
    llvm::Value *lowerCondI1(const ir::Expr &cond) {
        if (!operandIsIntOrBool(cond)) {
            fail("condition is not integer/boolean");
            return nullptr;
        }
        // Conditions must be boolean in Rell; we still only require an i64 envelope here and
        // compare against 0, which is exact for the 0/1 boolean encoding.
        llvm::Value *v = lowerExpr(cond);
        if (v == nullptr) return nullptr;
        return builder_.CreateICmpNE(v, builder_.getInt64(0), "cond");
    }

    // Allocate an i64 slot in the entry block (allocas must dominate all uses; keeping them in
    // the entry block is the canonical placement and avoids stack growth inside loops).
    llvm::Value *entryAlloca(const std::string &name) {
        llvm::IRBuilder<> tmp(&fn_->getEntryBlock(), fn_->getEntryBlock().getFirstInsertionPt());
        return tmp.CreateAlloca(builder_.getInt64Ty(), nullptr, name);
    }

    llvm::Value *entryAllocaArray(llvm::Type *elemTy, uint32_t n, const std::string &name) {
        llvm::IRBuilder<> tmp(&fn_->getEntryBlock(), fn_->getEntryBlock().getFirstInsertionPt());
        return tmp.CreateAlloca(elemTy, tmp.getInt64(n), name);
    }

    bool fail(const std::string &msg) {
        if (error_.empty()) error_ = msg;
        return false;
    }

    struct LoopTargets {
        llvm::BasicBlock *continueBB;
        llvm::BasicBlock *breakBB;
    };

    llvm::LLVMContext &ctx_;
    llvm::Module &module_;
    const ir::Type *retType_;
    const ::flatbuffers::Vector<::flatbuffers::Offset<ir::FunctionParam>> *params_;
    const ::flatbuffers::Vector<const ir::VarPtr *> *paramPtrs_;
    const ir::Stmt *bodyStmt_;
    const std::string &fnName_;
    int selfFunctionIndex_;
    llvm::IRBuilder<> builder_;
    std::string &error_;
    llvm::Function *fn_ = nullptr;
    llvm::FunctionType *selfFnTy_ = nullptr;
    llvm::Value *argsPtr_ = nullptr;
    llvm::AllocaInst *retAlloca_ = nullptr;
    llvm::BasicBlock *retBlock_ = nullptr;
    std::vector<LoopTargets> loopStack_;
    std::unordered_map<int32_t, llvm::Value *> slotByOffset_;
};

bool isDecimalType(const ir::Type *type) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    return prim != nullptr && prim->kind() == ir::PrimitiveTypeKind_DECIMAL;
}

// Lowers a pure-decimal function body `return <decimal arith over params>` to a JIT'd
//   i8* @<name>(i8** %args)
// that calls the extern "C" rell_num_decimal_* op glue (rell_numeric_ops.cpp). Each arg is the
// operand's plain decimal string (Rt_DecimalValue marshalled on the Kotlin side); the result is
// the Rell-stripped decimal string. NO JVM CALLBACK: every operation is native rell::num. Decimal
// CONSTANTS are not yet handled (params-only); anything else fails -> interpreter fallback. This
// is the first family proving "JNI calling back is not needed" end-to-end through the backend.
class DecimalLowerer {
public:
    DecimalLowerer(llvm::LLVMContext &ctx, llvm::Module &module, const ir::FunctionBody &body,
                   const std::string &fnName, std::string &errorOut)
        : ctx_(ctx), module_(module), body_(body), fnName_(fnName), builder_(ctx), error_(errorOut) {}

    bool run() {
        if (!isDecimalType(body_.type())) return fail("return type is not decimal");
        const auto *params = body_.params();
        const auto *paramPtrs = body_.param_ptrs();
        if (params == nullptr || paramPtrs == nullptr || paramPtrs->size() != params->size()) {
            return fail("params/param_ptrs mismatch");
        }
        for (flatbuffers::uoffset_t i = 0; i < params->size(); ++i) {
            const auto *p = params->Get(i);
            if (p == nullptr || !isDecimalType(p->type())) return fail("non-decimal param");
            if (p->default_expr() != nullptr) return fail("param has default expression");
        }
        for (flatbuffers::uoffset_t i = 0; i < paramPtrs->size(); ++i) {
            const auto *vp = paramPtrs->Get(i);
            if (vp == nullptr) return fail("null param VarPtr");
            paramIndexByPtr_[{vp->block_uid(), vp->offset()}] = static_cast<int>(i);
        }

        auto *ptrTy = llvm::PointerType::getUnqual(ctx_);
        auto *fnTy = llvm::FunctionType::get(ptrTy, {ptrTy}, /*isVarArg=*/false);  // i8*(i8**)
        fn_ = llvm::Function::Create(fnTy, llvm::Function::ExternalLinkage, fnName_, &module_);
        argsPtr_ = fn_->getArg(0);
        argsPtr_->setName("args");
        builder_.SetInsertPoint(llvm::BasicBlock::Create(ctx_, "entry", fn_));

        const auto *stmt = body_.body();
        if (stmt == nullptr) return fail("missing body Stmt");
        llvm::Value *handle = lowerTopLevelReturn(*stmt);
        if (handle == nullptr) return false;
        // Stringify the result decimal handle for the return value.
        llvm::Value *str = builder_.CreateCall(rt("rell_num_decimal_to_string", unaryTy()), {handle},
                                               "ret_str");
        builder_.CreateRet(str);

        std::string verifyErr;
        llvm::raw_string_ostream os(verifyErr);
        if (llvm::verifyFunction(*fn_, &os)) return fail("verifier: " + verifyErr);
        return true;
    }

private:
    llvm::PointerType *ptrTy() { return llvm::PointerType::getUnqual(ctx_); }
    llvm::FunctionType *unaryTy() { return llvm::FunctionType::get(ptrTy(), {ptrTy()}, false); }
    llvm::FunctionType *binTy() { return llvm::FunctionType::get(ptrTy(), {ptrTy(), ptrTy()}, false); }
    llvm::FunctionCallee rt(const char *name, llvm::FunctionType *ty) {
        return module_.getOrInsertFunction(name, ty);
    }

    llvm::Value *lowerTopLevelReturn(const ir::Stmt &stmt) {
        const ir::ReturnStatement *ret = stmt.stmt_as_ReturnStatement();
        if (ret == nullptr) {
            const auto *block = stmt.stmt_as_BlockStatement();
            if (block == nullptr || block->stmts() == nullptr || block->stmts()->size() != 1) {
                fail("body is not single Return / Block{Return}");
                return nullptr;
            }
            const auto *inner = block->stmts()->Get(0);
            if (inner == nullptr) { fail("null inner statement"); return nullptr; }
            ret = inner->stmt_as_ReturnStatement();
            if (ret == nullptr) { fail("Block body is not a single Return"); return nullptr; }
        }
        const auto *e = ret->expr();
        if (e == nullptr) { fail("Return has no expression"); return nullptr; }
        return lowerExpr(*e);
    }

    // Returns an i8* decimal handle (from the op glue).
    llvm::Value *lowerExpr(const ir::Expr &expr) {
        switch (expr.expr_type()) {
            case ir::ExprUnion_VarExpr: return lowerVar(*expr.expr_as_VarExpr());
            case ir::ExprUnion_BinaryExpr: return lowerBinary(*expr.expr_as_BinaryExpr());
            default: fail("unsupported decimal ExprUnion variant"); return nullptr;
        }
    }

    llvm::Value *lowerVar(const ir::VarExpr &var) {
        if (!isDecimalType(var.type())) { fail("VarExpr is not decimal"); return nullptr; }
        const auto *ptr = var.ptr();
        if (ptr == nullptr) { fail("VarExpr.ptr is null"); return nullptr; }
        auto it = paramIndexByPtr_.find({ptr->block_uid(), ptr->offset()});
        if (it == paramIndexByPtr_.end()) { fail("VarExpr is not a parameter"); return nullptr; }
        // load args[idx] (an i8* string), then parse it to a decimal handle.
        auto *slot = builder_.CreateInBoundsGEP(ptrTy(), argsPtr_, builder_.getInt64(it->second),
                                                "arg_slot");
        auto *argStr = builder_.CreateLoad(ptrTy(), slot, "arg_str");
        return builder_.CreateCall(rt("rell_num_decimal_parse", unaryTy()), {argStr}, "dec_parse");
    }

    llvm::Value *lowerBinary(const ir::BinaryExpr &bin) {
        if (!isDecimalType(bin.type())) { fail("BinaryExpr is not decimal"); return nullptr; }
        const char *sym = nullptr;
        switch (bin.op()) {
            case ir::BinaryOp_ADD_DECIMAL: sym = "rell_num_decimal_add"; break;
            case ir::BinaryOp_SUB_DECIMAL: sym = "rell_num_decimal_sub"; break;
            case ir::BinaryOp_MUL_DECIMAL: sym = "rell_num_decimal_mul"; break;
            case ir::BinaryOp_DIV_DECIMAL: sym = "rell_num_decimal_div"; break;
            case ir::BinaryOp_MOD_DECIMAL: sym = "rell_num_decimal_rem"; break;
            default: fail("unsupported decimal BinaryOp"); return nullptr;
        }
        const auto *l = bin.left();
        const auto *r = bin.right();
        if (l == nullptr || r == nullptr) { fail("decimal Binary null operand"); return nullptr; }
        llvm::Value *a = lowerExpr(*l);
        if (a == nullptr) return nullptr;
        llvm::Value *b = lowerExpr(*r);
        if (b == nullptr) return nullptr;
        return builder_.CreateCall(rt(sym, binTy()), {a, b}, "dec_op");
    }

    bool fail(const std::string &msg) {
        if (error_.empty()) error_ = msg;
        return false;
    }

    llvm::LLVMContext &ctx_;
    llvm::Module &module_;
    const ir::FunctionBody &body_;
    const std::string &fnName_;
    llvm::IRBuilder<> builder_;
    std::string &error_;
    llvm::Function *fn_ = nullptr;
    llvm::Value *argsPtr_ = nullptr;
    std::unordered_map<VarPtrKey, int, VarPtrKeyHash> paramIndexByPtr_;
};

}  // namespace

// Compile a function from the serialized App into a JIT-resident i64(i64*) entry point.
// Returns the function pointer as a jlong, or 0 if the function body lies outside the
// prototype's compilable slice (the Kotlin backend then routes to Rt_InterpreterImpl).
// Throws RuntimeException only on hard failures (JIT init, verifier rejection, lookup
// failure) — "not compilable" is a soft-fail signalled by a 0 return.
extern "C" JNIEXPORT jlong JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_compileFunctionByIndex(
    JNIEnv *env, jobject, jbyteArray appBytes, jint functionIndex) {
    if (appBytes == nullptr) {
        throwIllegalArgument(env, "appBytes is null");
        return 0;
    }
    if (functionIndex < 0) {
        throwIllegalArgument(env, "functionIndex is negative");
        return 0;
    }

    initJitOnce();
    if (!g_jit) {
        throwRuntime(env, ("LLJIT init failed: " + g_jitInitError).c_str());
        return 0;
    }

    const jsize length = env->GetArrayLength(appBytes);
    if (length <= 0) {
        throwIllegalArgument(env, "appBytes is empty");
        return 0;
    }
    jbyte *raw = env->GetByteArrayElements(appBytes, nullptr);
    if (raw == nullptr) return 0;

    std::lock_guard<std::mutex> lock(g_jitMutex);

    std::string fnName;
    std::string err;
    bool softFail = false;
    {
        flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t *>(raw),
                                       static_cast<size_t>(length));
        if (!ir::VerifyAppBuffer(verifier)) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "FlatBuffers verification failed");
            return 0;
        }
        const auto *app = ir::GetApp(raw);
        const auto *functions = app->functions();
        if (functions == nullptr || static_cast<uint32_t>(functionIndex) >= functions->size()) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "functionIndex out of range");
            return 0;
        }
        const auto *fnDef = functions->Get(functionIndex);
        if (fnDef == nullptr) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwRuntime(env, "FunctionDefinition is null");
            return 0;
        }
        if (fnDef->is_test()) {
            softFail = true;
            err = "test functions are not JITed";
        } else {
            const auto *body = fnDef->body();
            if (body == nullptr) {
                softFail = true;
                err = "abstract function (no body)";
            } else {
                auto ctx = std::make_unique<llvm::LLVMContext>();
                auto module = std::make_unique<llvm::Module>("rell_jit", *ctx);
                module->setDataLayout(g_jit->getDataLayout());
                module->setTargetTriple(g_jit->getTargetTriple());

                const uint64_t id = g_fnCounter.fetch_add(1);
                fnName = "rell_fn_" + std::to_string(id);

                Lowerer lowerer(*ctx, *module, body->type(), body->params(),
                                body->param_ptrs(), body->body(), fnName, functionIndex, err);
                if (!lowerer.run()) {
                    softFail = true;  // err is set inside Lowerer
                } else {
                    auto tsm = llvm::orc::ThreadSafeModule(
                        std::move(module), llvm::orc::ThreadSafeContext(std::move(ctx)));
                    if (auto addErr = g_jit->addIRModule(std::move(tsm))) {
                        err = "addIRModule: " + llvm::toString(std::move(addErr));
                        softFail = false;  // this is a hard failure path
                    }
                }
            }
        }
    }

    env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);

    if (softFail) {
        // Soft fail: Kotlin side falls back to the interpreter. No exception.
        return 0;
    }
    if (!err.empty()) {
        throwRuntime(env, ("Llvm_Lowerer: " + err).c_str());
        return 0;
    }

    auto symOrErr = g_jit->lookup(fnName);
    if (!symOrErr) {
        throwRuntime(env, ("Llvm_Lowerer: lookup failed: " + llvm::toString(symOrErr.takeError())).c_str());
        return 0;
    }
    return static_cast<jlong>(symOrErr->getValue());
}

// Compile the query at `queryIndex` in `app->queries()` into a JIT-resident i64(i64*) entry,
// mirroring compileFunctionByIndex. A UserQueryBody is structurally identical to a FunctionBody
// (ret_type + params + param_ptrs + body Stmt) and is lowered by the same Lowerer core. A
// SysQueryBody (stdlib query) soft-fails. selfFunctionIndex is -1: a query has no RegularUser
// self-target, so passing -1 makes the Lowerer's self-call match impossible (indices are >= 0).
extern "C" JNIEXPORT jlong JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_compileQueryByIndex(
    JNIEnv *env, jobject, jbyteArray appBytes, jint queryIndex) {
    if (appBytes == nullptr) {
        throwIllegalArgument(env, "appBytes is null");
        return 0;
    }
    if (queryIndex < 0) {
        throwIllegalArgument(env, "queryIndex is negative");
        return 0;
    }

    initJitOnce();
    if (!g_jit) {
        throwRuntime(env, ("LLJIT init failed: " + g_jitInitError).c_str());
        return 0;
    }

    const jsize length = env->GetArrayLength(appBytes);
    if (length <= 0) {
        throwIllegalArgument(env, "appBytes is empty");
        return 0;
    }
    jbyte *raw = env->GetByteArrayElements(appBytes, nullptr);
    if (raw == nullptr) return 0;

    std::lock_guard<std::mutex> lock(g_jitMutex);

    std::string fnName;
    std::string err;
    bool softFail = false;
    {
        flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t *>(raw),
                                       static_cast<size_t>(length));
        if (!ir::VerifyAppBuffer(verifier)) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "FlatBuffers verification failed");
            return 0;
        }
        const auto *app = ir::GetApp(raw);
        const auto *queries = app->queries();
        if (queries == nullptr || static_cast<uint32_t>(queryIndex) >= queries->size()) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "queryIndex out of range");
            return 0;
        }
        const auto *queryDef = queries->Get(queryIndex);
        if (queryDef == nullptr) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwRuntime(env, "QueryDefinition is null");
            return 0;
        }
        const auto *queryBody = queryDef->body();
        const auto *userBody = queryBody != nullptr ? queryBody->body_as_UserQueryBody() : nullptr;
        if (userBody == nullptr) {
            // SysQueryBody (stdlib query) or missing body: not JITable.
            softFail = true;
            err = "query has no UserQueryBody (sys query or absent body)";
        } else {
            auto ctx = std::make_unique<llvm::LLVMContext>();
            auto module = std::make_unique<llvm::Module>("rell_jit", *ctx);
            module->setDataLayout(g_jit->getDataLayout());
            module->setTargetTriple(g_jit->getTargetTriple());

            const uint64_t id = g_fnCounter.fetch_add(1);
            fnName = "rell_query_" + std::to_string(id);

            Lowerer lowerer(*ctx, *module, userBody->ret_type(), userBody->params(),
                            userBody->param_ptrs(), userBody->body(), fnName, /*selfFunctionIndex=*/-1, err);
            if (!lowerer.run()) {
                softFail = true;  // err is set inside Lowerer
            } else {
                auto tsm = llvm::orc::ThreadSafeModule(
                    std::move(module), llvm::orc::ThreadSafeContext(std::move(ctx)));
                if (auto addErr = g_jit->addIRModule(std::move(tsm))) {
                    err = "addIRModule: " + llvm::toString(std::move(addErr));
                    softFail = false;  // hard failure
                }
            }
        }
    }

    env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);

    if (softFail) return 0;
    if (!err.empty()) {
        throwRuntime(env, ("Llvm_Lowerer: " + err).c_str());
        return 0;
    }

    auto symOrErr = g_jit->lookup(fnName);
    if (!symOrErr) {
        throwRuntime(env, ("Llvm_Lowerer: lookup failed: " + llvm::toString(symOrErr.takeError())).c_str());
        return 0;
    }
    return static_cast<jlong>(symOrErr->getValue());
}

// Compile the global constant at `constIndex` in `app->constants()` into a JIT-resident i64(i64*)
// entry whose body is `return <init expr>`, taking zero params (the i64* arg is never read; the
// uniform signature keeps the callI64Function path uniform). Integer-typed constant only; any
// non-integer type or non-trivial initializer soft-fails.
extern "C" JNIEXPORT jlong JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_compileConstantByIndex(
    JNIEnv *env, jobject, jbyteArray appBytes, jint constIndex) {
    if (appBytes == nullptr) {
        throwIllegalArgument(env, "appBytes is null");
        return 0;
    }
    if (constIndex < 0) {
        throwIllegalArgument(env, "constIndex is negative");
        return 0;
    }

    initJitOnce();
    if (!g_jit) {
        throwRuntime(env, ("LLJIT init failed: " + g_jitInitError).c_str());
        return 0;
    }

    const jsize length = env->GetArrayLength(appBytes);
    if (length <= 0) {
        throwIllegalArgument(env, "appBytes is empty");
        return 0;
    }
    jbyte *raw = env->GetByteArrayElements(appBytes, nullptr);
    if (raw == nullptr) return 0;

    std::lock_guard<std::mutex> lock(g_jitMutex);

    std::string fnName;
    std::string err;
    bool softFail = false;
    {
        flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t *>(raw),
                                       static_cast<size_t>(length));
        if (!ir::VerifyAppBuffer(verifier)) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "FlatBuffers verification failed");
            return 0;
        }
        const auto *app = ir::GetApp(raw);
        const auto *constants = app->constants();
        if (constants == nullptr || static_cast<uint32_t>(constIndex) >= constants->size()) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "constIndex out of range");
            return 0;
        }
        const auto *constDef = constants->Get(constIndex);
        if (constDef == nullptr) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwRuntime(env, "GlobalConstantDefinition is null");
            return 0;
        }
        const auto *initExpr = constDef->value();
        if (initExpr == nullptr) {
            softFail = true;
            err = "constant has no initializer expression";
        } else {
            auto ctx = std::make_unique<llvm::LLVMContext>();
            auto module = std::make_unique<llvm::Module>("rell_jit", *ctx);
            module->setDataLayout(g_jit->getDataLayout());
            module->setTargetTriple(g_jit->getTargetTriple());

            const uint64_t id = g_fnCounter.fetch_add(1);
            fnName = "rell_const_" + std::to_string(id);

            Lowerer lowerer(*ctx, *module, /*retType=*/nullptr, /*params=*/nullptr,
                            /*paramPtrs=*/nullptr, /*bodyStmt=*/nullptr, fnName, /*selfFunctionIndex=*/-1, err);
            if (!lowerer.runConstant(constDef->type(), *initExpr)) {
                softFail = true;  // err is set inside Lowerer
            } else {
                auto tsm = llvm::orc::ThreadSafeModule(
                    std::move(module), llvm::orc::ThreadSafeContext(std::move(ctx)));
                if (auto addErr = g_jit->addIRModule(std::move(tsm))) {
                    err = "addIRModule: " + llvm::toString(std::move(addErr));
                    softFail = false;  // hard failure
                }
            }
        }
    }

    env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);

    if (softFail) return 0;
    if (!err.empty()) {
        throwRuntime(env, ("Llvm_Lowerer: " + err).c_str());
        return 0;
    }

    auto symOrErr = g_jit->lookup(fnName);
    if (!symOrErr) {
        throwRuntime(env, ("Llvm_Lowerer: lookup failed: " + llvm::toString(symOrErr.takeError())).c_str());
        return 0;
    }
    return static_cast<jlong>(symOrErr->getValue());
}

extern "C" JNIEXPORT jlong JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_callI64Function(JNIEnv *env, jobject, jlong fnPtr, jlongArray argsArr) {
    if (fnPtr == 0) {
        throwIllegalArgument(env, "function pointer is null");
        return 0;
    }
    if (argsArr == nullptr) {
        throwIllegalArgument(env, "args is null");
        return 0;
    }
    jlong *args = env->GetLongArrayElements(argsArr, nullptr);
    if (args == nullptr) return 0;

    // Clear the integer-overflow channel before running; JIT'd arithmetic sets it on overflow.
    g_intOverflow.pending = false;

    using Fn = int64_t (*)(int64_t *);
    Fn fn = reinterpret_cast<Fn>(static_cast<uintptr_t>(fnPtr));
    static_assert(sizeof(jlong) == sizeof(int64_t),
                  "jlong must be 64-bit on this platform");
    int64_t result = fn(reinterpret_cast<int64_t *>(args));

    env->ReleaseLongArrayElements(argsArr, args, JNI_ABORT);
    return static_cast<jlong>(result);
}

// Reports whether the most recent callI64Function overflowed and, if so, the exact Rell error code
// for the raised Rt_Exception (`expr:<op>:overflow:<a>:<b>` for binary, `expr:-:overflow:<v>` for
// unary negate). Returns null when there was no overflow. Clears the channel. The Kotlin caller
// raises the Rt_Exception so the error object is consensus-identical to the tree-walker's.
extern "C" JNIEXPORT jstring JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_pollIntOverflow(JNIEnv *env, jobject) {
    if (!g_intOverflow.pending) return nullptr;
    const IntOverflowState st = g_intOverflow;
    g_intOverflow.pending = false;

    std::string code;
    if (st.op == 'n') {
        code = "expr:-:overflow:" + std::to_string(st.a);
    } else {
        code = std::string("expr:") + st.op + ":overflow:" + std::to_string(st.a) + ":" +
               std::to_string(st.b);
    }
    return env->NewStringUTF(code.c_str());
}

// Compile a pure-decimal function (decimal return + decimal params, body = return <decimal arith>)
// to a JIT-resident i8*(i8**) entry running the arithmetic natively via rell_num_decimal_*
// (rell_numeric_ops.cpp) — ZERO JVM callback. Returns the fn pointer, or 0 for soft-fail.
extern "C" JNIEXPORT jlong JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_compileDecimalFunctionByIndex(
    JNIEnv *env, jobject, jbyteArray appBytes, jint functionIndex) {
    if (appBytes == nullptr) { throwIllegalArgument(env, "appBytes is null"); return 0; }
    if (functionIndex < 0) { throwIllegalArgument(env, "functionIndex is negative"); return 0; }
    initJitOnce();
    if (!g_jit) { throwRuntime(env, ("LLJIT init failed: " + g_jitInitError).c_str()); return 0; }
    const jsize length = env->GetArrayLength(appBytes);
    if (length <= 0) { throwIllegalArgument(env, "appBytes is empty"); return 0; }
    jbyte *raw = env->GetByteArrayElements(appBytes, nullptr);
    if (raw == nullptr) return 0;

    std::lock_guard<std::mutex> lock(g_jitMutex);
    std::string fnName;
    std::string err;
    bool softFail = false;
    {
        flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t *>(raw),
                                       static_cast<size_t>(length));
        if (!ir::VerifyAppBuffer(verifier)) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "FlatBuffers verification failed");
            return 0;
        }
        const auto *app = ir::GetApp(raw);
        const auto *functions = app->functions();
        if (functions == nullptr || static_cast<uint32_t>(functionIndex) >= functions->size()) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwIllegalArgument(env, "functionIndex out of range");
            return 0;
        }
        const auto *fnDef = functions->Get(functionIndex);
        if (fnDef == nullptr) {
            env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
            throwRuntime(env, "FunctionDefinition is null");
            return 0;
        }
        if (fnDef->is_test()) {
            softFail = true;
            err = "test functions are not JITed";
        } else {
            const auto *body = fnDef->body();
            if (body == nullptr) {
                softFail = true;
                err = "abstract function (no body)";
            } else {
                auto ctx = std::make_unique<llvm::LLVMContext>();
                auto module = std::make_unique<llvm::Module>("rell_jit_dec", *ctx);
                module->setDataLayout(g_jit->getDataLayout());
                module->setTargetTriple(g_jit->getTargetTriple());
                const uint64_t id = g_fnCounter.fetch_add(1);
                fnName = "rell_dec_fn_" + std::to_string(id);
                DecimalLowerer lowerer(*ctx, *module, *body, fnName, err);
                if (!lowerer.run()) {
                    softFail = true;
                } else {
                    auto tsm = llvm::orc::ThreadSafeModule(
                        std::move(module), llvm::orc::ThreadSafeContext(std::move(ctx)));
                    if (auto addErr = g_jit->addIRModule(std::move(tsm))) {
                        err = "addIRModule: " + llvm::toString(std::move(addErr));
                        softFail = false;
                    }
                }
            }
        }
    }
    env->ReleaseByteArrayElements(appBytes, raw, JNI_ABORT);
    if (softFail) return 0;
    if (!err.empty()) { throwRuntime(env, ("Llvm_DecimalLowerer: " + err).c_str()); return 0; }
    auto symOrErr = g_jit->lookup(fnName);
    if (!symOrErr) {
        throwRuntime(env,
                     ("Llvm_DecimalLowerer: lookup failed: " + llvm::toString(symOrErr.takeError()))
                         .c_str());
        return 0;
    }
    return static_cast<jlong>(symOrErr->getValue());
}

// Invoke a JIT'd decimal function: marshal operand strings to char**, run native, copy the result
// string out, reset the per-call pool. The computation makes NO JVM callback. On a native
// arithmetic error (overflow / div0), throws RuntimeException carrying the exact Rell code.
extern "C" JNIEXPORT jstring JNICALL
Java_net_postchain_rell_llvm_RellLlvmNative_callDecimalFunction(
    JNIEnv *env, jobject, jlong fnPtr, jobjectArray argsArr) {
    if (fnPtr == 0) { throwIllegalArgument(env, "function pointer is null"); return nullptr; }
    if (argsArr == nullptr) { throwIllegalArgument(env, "args is null"); return nullptr; }
    const jsize n = env->GetArrayLength(argsArr);
    std::vector<jstring> jstrs(static_cast<size_t>(n), nullptr);
    std::vector<const char *> cargs(static_cast<size_t>(n), nullptr);
    for (jsize i = 0; i < n; ++i) {
        jstrs[i] = reinterpret_cast<jstring>(env->GetObjectArrayElement(argsArr, i));
        if (jstrs[i] != nullptr) cargs[i] = env->GetStringUTFChars(jstrs[i], nullptr);
    }
    rell_num_pool_reset();
    using Fn = const char *(*)(const char **);
    Fn fn = reinterpret_cast<Fn>(static_cast<uintptr_t>(fnPtr));
    const char *result = fn(cargs.data());
    const bool pending = rell_num_pending() != 0;
    std::string errMsg;
    jstring out = nullptr;
    if (pending) {
        errMsg = std::string(rell_num_error_code()) + ": " + rell_num_error_message();
    } else {
        out = env->NewStringUTF(result != nullptr ? result : "");
    }
    for (jsize i = 0; i < n; ++i) {
        if (jstrs[i] != nullptr && cargs[i] != nullptr) {
            env->ReleaseStringUTFChars(jstrs[i], cargs[i]);
        }
    }
    rell_num_pool_reset();
    if (pending) { throwRuntime(env, errMsg.c_str()); return nullptr; }
    return out;
}

// Silence -Wunused-function for the JNI callback scaffold helpers while no IR-emitted
// callbacks land. Removing the wrapper would also drop the cached JavaVM* — keep it.
[[maybe_unused]] static void *suppress_unused_jvm_handle = reinterpret_cast<void *>(&jvmHandle);
