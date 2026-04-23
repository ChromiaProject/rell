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

#include <llvm/Config/llvm-config.h>
#include <llvm/ExecutionEngine/Orc/JITTargetMachineBuilder.h>
#include <llvm/ExecutionEngine/Orc/LLJIT.h>
#include <llvm/ExecutionEngine/Orc/ThreadSafeModule.h>
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/Verifier.h>
#include <llvm/Support/Error.h>
#include <llvm/Support/TargetSelect.h>

#include "app_generated.h"

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

class Lowerer {
public:
    Lowerer(llvm::LLVMContext &ctx,
            llvm::Module &module,
            const ir::FunctionBody &body,
            const std::string &fnName,
            std::string &errorOut)
        : ctx_(ctx),
          module_(module),
          body_(body),
          fnName_(fnName),
          builder_(ctx),
          error_(errorOut) {}

    bool run() {
        if (!isIntegerType(body_.type())) return fail("return type is not integer");

        const auto *params = body_.params();
        if (params == nullptr) return fail("missing params");
        const auto *paramPtrs = body_.param_ptrs();
        if (paramPtrs == nullptr || paramPtrs->size() != params->size()) {
            return fail("param_ptrs size mismatch");
        }
        for (flatbuffers::uoffset_t i = 0; i < params->size(); ++i) {
            const auto *p = params->Get(i);
            if (p == nullptr) return fail("null FunctionParam");
            if (!isIntegerType(p->type())) return fail("non-integer param type");
            if (p->default_expr() != nullptr) return fail("param has default expression");
        }

        // Build the (block_uid, offset) → param-index map: a VarExpr.ptr that matches
        // any of these is a parameter read.
        for (flatbuffers::uoffset_t i = 0; i < paramPtrs->size(); ++i) {
            const auto *vp = paramPtrs->Get(i);
            if (vp == nullptr) return fail("null param VarPtr");
            paramIndexByPtr_[{vp->block_uid(), vp->offset()}] = static_cast<int>(i);
        }

        // Function signature: `i64 @<name>(i64* %args)`. The caller (`callI64Function`)
        // passes a contiguous LongArray; per-slot loads via GEP keep the calling-convention
        // surface trivial (one pointer in, one i64 out) regardless of the param count.
        auto *i64Ty = builder_.getInt64Ty();
        auto *ptrTy = llvm::PointerType::getUnqual(ctx_);
        auto *fnTy = llvm::FunctionType::get(i64Ty, {ptrTy}, /*isVarArg=*/false);
        fn_ = llvm::Function::Create(fnTy, llvm::Function::ExternalLinkage, fnName_, &module_);
        argsPtr_ = fn_->getArg(0);
        argsPtr_->setName("args");
        auto *entry = llvm::BasicBlock::Create(ctx_, "entry", fn_);
        builder_.SetInsertPoint(entry);

        const auto *stmt = body_.body();
        if (stmt == nullptr) return fail("missing body Stmt");

        llvm::Value *result = lowerTopLevelReturn(*stmt);
        if (result == nullptr) return false;
        builder_.CreateRet(result);

        std::string verifyErr;
        llvm::raw_string_ostream verifyOs(verifyErr);
        if (llvm::verifyFunction(*fn_, &verifyOs)) {
            return fail("verifier: " + verifyErr);
        }
        return true;
    }

private:
    // Accepts the body shape "Return <e>" or "Block { Return <e> }".
    llvm::Value *lowerTopLevelReturn(const ir::Stmt &stmt) {
        const ir::ReturnStatement *ret = stmt.stmt_as_ReturnStatement();
        if (ret == nullptr) {
            const auto *block = stmt.stmt_as_BlockStatement();
            if (block == nullptr || block->stmts() == nullptr || block->stmts()->size() != 1) {
                fail("body is not a single Return / Block{Return}");
                return nullptr;
            }
            const auto *inner = block->stmts()->Get(0);
            if (inner == nullptr) {
                fail("null inner statement in block");
                return nullptr;
            }
            ret = inner->stmt_as_ReturnStatement();
            if (ret == nullptr) {
                fail("Block body is not a single Return");
                return nullptr;
            }
        }
        const auto *retExpr = ret->expr();
        if (retExpr == nullptr) {
            fail("Return has no expression");
            return nullptr;
        }
        return lowerExpr(*retExpr);
    }

    llvm::Value *lowerExpr(const ir::Expr &expr) {
        switch (expr.expr_type()) {
            case ir::ExprUnion_VarExpr: return lowerVar(*expr.expr_as_VarExpr());
            case ir::ExprUnion_ConstantValueExpr: return lowerConst(*expr.expr_as_ConstantValueExpr());
            case ir::ExprUnion_BinaryExpr: return lowerBinary(*expr.expr_as_BinaryExpr());
            default: {
                fail("unsupported ExprUnion variant: " +
                     std::to_string(static_cast<int>(expr.expr_type())));
                return nullptr;
            }
        }
    }

    llvm::Value *lowerVar(const ir::VarExpr &var) {
        if (!isIntegerType(var.type())) {
            fail("VarExpr has non-integer type");
            return nullptr;
        }
        const auto *ptr = var.ptr();
        if (ptr == nullptr) {
            fail("VarExpr.ptr is null");
            return nullptr;
        }
        auto it = paramIndexByPtr_.find({ptr->block_uid(), ptr->offset()});
        if (it == paramIndexByPtr_.end()) {
            fail("VarExpr does not reference a function parameter");
            return nullptr;
        }
        auto *i64Ty = builder_.getInt64Ty();
        auto *slot = builder_.CreateInBoundsGEP(
            i64Ty, argsPtr_, builder_.getInt64(it->second),
            "param_slot_" + std::to_string(it->second));
        return builder_.CreateLoad(i64Ty, slot, "p" + std::to_string(it->second));
    }

    llvm::Value *lowerConst(const ir::ConstantValueExpr &c) {
        const auto *tv = c.typed_value();
        if (tv == nullptr) {
            fail("ConstantValueExpr has no TypedValue");
            return nullptr;
        }
        if (!isIntegerType(tv->type())) {
            fail("ConstantValueExpr type is not integer");
            return nullptr;
        }
        const auto *intVal = tv->value_as_IntValue();
        if (intVal == nullptr) {
            fail("ConstantValueExpr ValueUnion is not IntValue");
            return nullptr;
        }
        return builder_.getInt64(intVal->value());
    }

    llvm::Value *lowerBinary(const ir::BinaryExpr &bin) {
        if (!isIntegerType(bin.type())) {
            fail("BinaryExpr result is not integer");
            return nullptr;
        }
        const auto *left = bin.left();
        const auto *right = bin.right();
        if (left == nullptr || right == nullptr) {
            fail("BinaryExpr has null operand");
            return nullptr;
        }
        llvm::Value *a = lowerExpr(*left);
        if (a == nullptr) return nullptr;
        llvm::Value *b = lowerExpr(*right);
        if (b == nullptr) return nullptr;
        switch (bin.op()) {
            case ir::BinaryOp_ADD_INTEGER: return builder_.CreateAdd(a, b, "add");
            case ir::BinaryOp_SUB_INTEGER: return builder_.CreateSub(a, b, "sub");
            case ir::BinaryOp_MUL_INTEGER: return builder_.CreateMul(a, b, "mul");
            default: {
                fail("unsupported BinaryOp: " + std::to_string(static_cast<int>(bin.op())));
                return nullptr;
            }
        }
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

                Lowerer lowerer(*ctx, *module, *body, fnName, err);
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

    using Fn = int64_t (*)(int64_t *);
    Fn fn = reinterpret_cast<Fn>(static_cast<uintptr_t>(fnPtr));
    static_assert(sizeof(jlong) == sizeof(int64_t),
                  "jlong must be 64-bit on this platform");
    int64_t result = fn(reinterpret_cast<int64_t *>(args));

    env->ReleaseLongArrayElements(argsArr, args, JNI_ABORT);
    return static_cast<jlong>(result);
}

// Silence -Wunused-function for the JNI callback scaffold helpers while no IR-emitted
// callbacks land. Removing the wrapper would also drop the cached JavaVM* — keep it.
[[maybe_unused]] static void *suppress_unused_jvm_handle = reinterpret_cast<void *>(&jvmHandle);
