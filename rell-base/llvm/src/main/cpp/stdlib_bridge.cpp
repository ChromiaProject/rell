// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// stdlib_bridge.cpp — the universal JNI stdlib caller for the Rell LLVM backend.
//
// SCOPE OF THIS FILE
// ------------------
// This is the CORRECTNESS FLOOR (rell_runtime.h §5). It makes 100% of the Rell stdlib reachable
// from JIT'd native code by calling BACK into the JVM's R_SysFunction registry, instead of
// reimplementing any stdlib semantics in C++.
//
// It implements:
//
//   1. SysFnTable — the deterministic name->id interning table. A fixed RR-tree walk (mirrored
//      JVM-side) interns every FnTarget_{SysGlobal,SysMember,NativeUser}.fn_name(),
//      MemberCalculator_SysFunction.fn_name(), and SysQueryBody.fn_name() in first-encounter
//      order; the id-ordered names() list is read JVM-side to build the dense
//      Array<R_SysFunction?> so the two id spaces agree. Ids are private to one App's JIT
//      session and never cross a consensus boundary (DETERMINISM note below).
//
//   2. rell_sysfn_call(...) — given a dense SysFnId, the evaluated RellValue args, and the live
//      RellCallCtx, it: pushes a JNI local frame; reboxes each arg to a jobject Rt_Value via
//      to_jvm() into a java/lang/Object[]; (re)attaches the current thread to the cached g_vm if
//      it has no JNIEnv; invokes the cached static JVM entry
//      Llvm_SysBridge.dispatch(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)L..Rt_Value; with
//      (sysfnId, args, frameHandle); ExceptionChecks (on a pending Rt_Exception it returns
//      rv_none() WITHOUT clearing — the JIT trampoline unwinds, §7); and otherwise unwraps the
//      single Rt_Value result via from_jvm(), adopt()-ing an escaping result into ctx->arena.
//
// DETERMINISM (consensus-critical): this file performs NO Rell-semantic computation. Every value
// is produced by the JVM R_SysFunction, marshalled through the shared to_jvm/from_jvm ABI
// (value.cpp). The native side only shuffles handles and indexes. There is therefore no place a
// hand-rolled fast path could diverge from the interpreter — by construction, a sysfn call here
// is bit-exact with Rt_InterpreterImpl. Interning ids are session-private and rebuilt JVM-side
// from the native-provided name order, so id assignment order is irrelevant to results.
//
// Style mirrors jni_bridge.cpp / rell_runtime.h: `namespace ir = rell::ir;`, the
// throwRuntime/throwIllegalArgument idioms, the once-guarded lazy cache pattern (like g_jit), the
// exception-pending -> rv_none() contract from §7, and defensive null-checks throughout.

#include "rell_runtime.h"

#include <mutex>
#include <string>
#include <vector>

namespace rell::llvm_rt {

// =====================================================================================
// SysFnTable — deterministic name -> dense id interning (rell_runtime.h §5).
//
// First-encounter assignment over the fixed RR-tree walk. The same walk is performed JVM-side,
// and the JVM rebuilds its dense R_SysFunction array from names() (id order), so the id spaces
// agree without the ids themselves ever being serialized or crossing a consensus boundary.
// =====================================================================================

SysFnId SysFnTable::intern(const std::string &name) {
    auto it = byName_.find(name);
    if (it != byName_.end()) {
        return it->second;
    }
    const SysFnId id = static_cast<SysFnId>(names_.size());
    byName_.emplace(name, id);
    names_.push_back(name);
    return id;
}

SysFnId SysFnTable::lookup(const std::string &name) const {
    auto it = byName_.find(name);
    return it == byName_.end() ? kSysFnIdNone : it->second;
}

// =====================================================================================
// Cached JVM dispatch entry — Llvm_SysBridge.dispatch.
//
// Resolved lazily once under a std::once_flag (mirrors the g_jit init guard in jni_bridge.cpp).
// A null class / method lookup is a build-or-ABI breakage, not a soft-fail: it raises a Java
// RuntimeException across JNI (throwRuntime). The cached jclass is promoted to a global ref so it
// survives past the resolving call's local-frame teardown.
// =====================================================================================

namespace {

std::once_flag g_sysBridgeInit;
jclass g_sysBridgeClass = nullptr;   // global ref to net/postchain/rell/llvm/Llvm_SysBridge
jmethodID g_dispatchMethod = nullptr;
jclass g_rtValueClass = nullptr;     // global ref to Rt_Value (boxed-args array element type)
std::string g_sysBridgeInitError;

// Resolve a class by name and promote it to a global ref. Returns nullptr (clearing any pending
// exception and recording `what` into g_sysBridgeInitError) on failure.
jclass resolveGlobalClass(JNIEnv *env, const char *name, const char *what) {
    jclass localCls = env->FindClass(name);
    if (localCls == nullptr) {
        if (env->ExceptionCheck() == JNI_TRUE) env->ExceptionClear();
        g_sysBridgeInitError = what;
        return nullptr;
    }
    auto *globalCls = reinterpret_cast<jclass>(env->NewGlobalRef(localCls));
    env->DeleteLocalRef(localCls);
    if (globalCls == nullptr) g_sysBridgeInitError = what;
    return globalCls;
}

// Resolve and cache the static dispatch entry + java/lang/Object once, under a std::once_flag
// (mirrors the g_jit init guard in jni_bridge.cpp) so concurrent JIT'd callers race-free. `env`
// must be a valid JNIEnv for the calling thread. On any failure, leaves g_dispatchMethod ==
// nullptr and records g_sysBridgeInitError; the caller turns that into a throwRuntime. Does NOT
// itself throw, so the once_flag body stays exception-clean.
void initSysBridgeOnce(JNIEnv *env) {
    std::call_once(g_sysBridgeInit, [env]() {
        jclass bridgeCls =
            resolveGlobalClass(env, "net/postchain/rell/llvm/Llvm_SysBridge", "Llvm_SysBridge class not found");
        if (bridgeCls == nullptr) return;

        // Kotlin does NOT erase `Array<Rt_Value>` / `Rt_Value` to `Object[]` / `Object`: the real
        // JVM descriptor of `dispatch(Int, Array<Rt_Value>, Long): Rt_Value` is the Rt_Value-typed
        // one below. Looking up the Object-typed descriptor returns null -> "method not found".
        jmethodID mid = env->GetStaticMethodID(
            bridgeCls, "dispatch",
            "(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;");
        if (mid == nullptr) {
            if (env->ExceptionCheck() == JNI_TRUE) env->ExceptionClear();
            env->DeleteGlobalRef(bridgeCls);
            g_sysBridgeInitError = "Llvm_SysBridge.dispatch method not found";
            return;
        }

        // The args array passed to dispatch must be a real Rt_Value[] (not Object[]) — the JVM
        // verifies the array's component type against the `Array<Rt_Value>` parameter.
        jclass rtValueCls =
            resolveGlobalClass(env, "net/postchain/rell/base/runtime/Rt_Value", "Rt_Value class not found");
        if (rtValueCls == nullptr) {
            env->DeleteGlobalRef(bridgeCls);
            return;
        }

        g_sysBridgeClass = bridgeCls;
        g_dispatchMethod = mid;
        g_rtValueClass = rtValueCls;
    });
}

}  // namespace

// =====================================================================================
// rell_sysfn_call — the universal stdlib back-call (rell_runtime.h §5, ABI §4).
// =====================================================================================

extern "C" RellValue rell_sysfn_call(JNIEnv *env, SysFnId sysfnId, const RellValue *args,
                                     int32_t nargs, RellCallCtx *ctx) {
    // ---- hard ABI preconditions (caller/wiring bugs, NOT envelope misses) ----------------
    // A missing ctx/arena/env is a wiring fault; we cannot marshal without them. Per §7 these
    // are genuine ABI faults: raise a Java exception so the JIT trampoline aborts loudly rather
    // than silently returning a wrong value.
    if (ctx == nullptr || ctx->arena == nullptr) {
        if (env != nullptr) throwIllegalArgument(env, "rell_sysfn_call: null RellCallCtx/arena");
        return rv_none();
    }
    if (env == nullptr) env = ctx->env;
    if (env == nullptr) {
        // No JNIEnv on the ctx either: attach the current thread to the cached JavaVM*. JIT'd
        // code re-enters through the JNI trampoline that owns a live env, so this is a defensive
        // fallback for back-call sites that don't already hold one.
        JavaVM *vm = jvmHandle();
        if (vm == nullptr || vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK ||
            env == nullptr) {
            return rv_none();
        }
    }

    // sysfnId == kSysFnIdNone (-1) means "name was never interned" — the lowering pass should
    // have soft-failed the whole function instead of emitting this call. Treat as a hard fault.
    if (sysfnId < 0) {
        throwRuntime(env, "rell_sysfn_call: un-interned sysfn id (should have soft-failed)");
        return rv_none();
    }
    if (nargs < 0 || (nargs > 0 && args == nullptr)) {
        throwIllegalArgument(env, "rell_sysfn_call: bad args/nargs");
        return rv_none();
    }

    initSysBridgeOnce(env);
    if (g_dispatchMethod == nullptr || g_sysBridgeClass == nullptr || g_rtValueClass == nullptr) {
        throwRuntime(env, ("rell_sysfn_call: " +
                           (g_sysBridgeInitError.empty() ? std::string("dispatch unavailable")
                                                         : g_sysBridgeInitError))
                              .c_str());
        return rv_none();
    }

    // ---- push a local frame sized for the boxed args + the array + result + slack ---------
    // to_jvm() returns a local ref per inline arg; with the args array, the dispatch result, and
    // the reboxing factory temporaries, nargs + a small constant covers the live-local high-water
    // mark. EnsureLocalCapacity is the documented JNI contract; PushLocalFrame bounds it cleanly.
    const jint frameCapacity = static_cast<jint>(nargs) + 8;
    if (env->PushLocalFrame(frameCapacity) != 0) {
        // OutOfMemoryError pending from the JVM; leave it set and unwind (§7).
        return rv_none();
    }

    // ---- box each RellValue arg into the Rt_Value[] ---------------------------------------
    jobjectArray boxed = env->NewObjectArray(static_cast<jsize>(nargs), g_rtValueClass, nullptr);
    if (boxed == nullptr) {
        // OOM (pending exception) — pop the frame, leave the exception set, unwind.
        env->PopLocalFrame(nullptr);
        return rv_none();
    }

    for (int32_t i = 0; i < nargs; ++i) {
        // to_jvm materialises a LOCAL ref (for HANDLE args it returns the arena-owned global ref
        // directly; SetObjectArrayElement creates its own internal ref, so we don't transfer
        // ownership). Inline factories (Rt_IntValue.get, the long-scale decimal ctor, ...) can
        // throw Rt_Exception (decimal overflow, negative rowid); §7 requires an ExceptionCheck.
        jobject elem = to_jvm(env, *ctx->arena, args[i], ctx->frameHandle);
        if (env->ExceptionCheck() == JNI_TRUE) {
            // DETERMINISM: a factory-thrown Rt_Exception is a genuine Rell runtime error. Do NOT
            // clear it. Pop the frame (PopLocalFrame is exception-tolerant) and unwind so the JVM
            // sees the same Rt_Exception the interpreter would have thrown.
            env->PopLocalFrame(nullptr);
            return rv_none();
        }
        env->SetObjectArrayElement(boxed, static_cast<jsize>(i), elem);
        if (env->ExceptionCheck() == JNI_TRUE) {
            // ArrayStoreException would only fire on an ABI break (elem not an Object); treat as a
            // pending exception and unwind rather than masking it.
            env->PopLocalFrame(nullptr);
            return rv_none();
        }
    }

    // ---- invoke the JVM dispatch entry ----------------------------------------------------
    // Llvm_SysBridge.dispatch(sysfnId, args, frameHandle) indexes the dense per-App
    // Array<R_SysFunction?> and invokes R_SysFunction.call(ctx, args) — the SAM every arity
    // funnels through. frameHandle is the opaque jlong to the JVM Llvm_CallEnv (live Rt_Frame);
    // the native side never dereferences it.
    jobject resultObj = env->CallStaticObjectMethod(g_sysBridgeClass, g_dispatchMethod,
                                                    static_cast<jint>(sysfnId), boxed,
                                                    ctx->frameHandle);

    // §7: after EVERY back-call, ExceptionCheck and never clear. A pending Rt_Exception means the
    // stdlib function raised a Rell runtime error; return the poison sentinel and let the
    // trampoline turn it into "exception pending — abort" so the JVM sees it bit-exactly.
    if (env->ExceptionCheck() == JNI_TRUE) {
        env->PopLocalFrame(nullptr);
        return rv_none();
    }

    // ---- unwrap the single Rt_Value result ------------------------------------------------
    // from_jvm dispatches on class identity: inline primitives / long-fitting decimal+bigint are
    // carried by value; everything else (and a result that escapes the envelope) is arena.adopt()
    // -ed into a HANDLE. A null result jobject where a value is required is a hard ABI fault
    // (from_jvm raises throwIllegalArgument): a well-formed R_SysFunction always returns an
    // Rt_Value (Rt_NullValue/Rt_UnitValue for void-ish), never a Java null.
    RellValue out = from_jvm(env, *ctx->arena, resultObj, ctx->frameHandle);

    // from_jvm's own factory/helper back-calls (BigInteger.bitLength, Tf_LongScaleDecimal.tryFrom)
    // can leave a pending exception on a malformed value; honour §7 before returning.
    if (env->ExceptionCheck() == JNI_TRUE) {
        env->PopLocalFrame(nullptr);
        return rv_none();
    }

    // PopLocalFrame(nullptr) discards every local ref allocated in this frame (the boxed array,
    // the per-arg locals, the result local). The arena (not this frame) owns any HANDLE global
    // refs adopt()-ed inside from_jvm, so they correctly outlive the pop.
    env->PopLocalFrame(nullptr);
    return out;
}

}  // namespace rell::llvm_rt
