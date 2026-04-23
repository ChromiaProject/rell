// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// value.cpp — the tagged RellValue runtime: arena, marshalling, and the back-call entry
// points declared in rell_runtime.h.
//
// This is the value/ABI layer of the Rell LLVM backend. It owns:
//
//   * the shared error helpers (throwIllegalArgument / throwRuntime / jvmHandle) — the same
//     idioms jni_bridge.cpp defines for the prototype slice, surfaced here once for every
//     per-category .cpp;
//   * RellArena — the per-call bump owner of JNI global refs;
//   * the cached RellRefs table (jclass / jfieldID / jmethodID), populated once under a
//     std::once_flag, aborting hard (throwRuntime) on any missing symbol — a missing class/ID
//     is a build/ABI breakage, never a soft-fail;
//   * from_jvm / to_jvm — native RellValue <-> jobject Rt_Value, both directions, with the
//     inline fast paths for boolean / integer / rowid / unit / null and the long-fitting
//     decimal & big_integer envelopes; everything else (and any out-of-envelope numeric)
//     stays an opaque arena-owned HANDLE;
//   * the universal JNI stdlib caller rell_sysfn_call and the SQL back-calls
//     rell_db_eval_expr / rell_db_exec_stmt, plus the SysFnTable / DbNodeTable interning
//     tables.
//
// CORRECTNESS RULE (consensus-critical): a value the inline lattice cannot reproduce
// bit-exactly is carried as a HANDLE and routed through the JVM. The native side NEVER
// reimplements Lib_DecimalMath.scale() / Lib_BigIntegerMath bounds: the DEC_LONG /
// BIGINT_LONG inline path is only ENTERED for (a) JVM-validated constants/values the lowering
// already proved in-envelope, or (b) pure in-envelope intrinsic results. An arbitrary
// Rt_DecimalValue / Rt_BigIntegerValue arriving from the JVM is adopted as a HANDLE unless it
// provably long-fits via a JVM-side check (BigInteger.bitLength), never cracked into
// mantissa/scale in C++.

#include "rell_runtime.h"

#include <cstring>

namespace rell::llvm_rt {

// =====================================================================================
// Shared error helpers (mirrors jni_bridge.cpp exactly).
// =====================================================================================

void throwIllegalArgument(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/IllegalArgumentException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

void throwRuntime(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

// =====================================================================================
// 3. Per-call arena.
// =====================================================================================

RellArena::RellArena(JNIEnv *env) : env_(env) {}

RellArena::~RellArena() {
    // Single DeleteGlobalRef sweep. Exception-tolerant: this runs while a JVM exception may be
    // pending (the trampoline abort path), and DeleteGlobalRef does not interact with the
    // pending-exception state, so we never clear or check it here.
    for (jobject g : globals_) {
        if (g != nullptr) env_->DeleteGlobalRef(g);
    }
    globals_.clear();
}

jobject RellArena::track(jobject ref) {
    if (ref == nullptr) return nullptr;
    jobject global = env_->NewGlobalRef(ref);
    // NewGlobalRef only returns null on OOM; surface it as a hard fault rather than silently
    // dropping the ref (which would corrupt the value lattice).
    if (global == nullptr) {
        throwRuntime(env_, "RellArena: NewGlobalRef returned null (out of memory)");
        return nullptr;
    }
    globals_.push_back(global);
    return global;
}

RellValue RellArena::adopt(jobject rtValue) {
    jobject global = track(rtValue);
    return rv_handle(global);
}

// =====================================================================================
// 4c. Cached JNI references.
//
// Populated once under g_refsOnce against the cached g_vm's JNIEnv. Every class is held as a
// global jclass ref; the singleton objects (Rt_NullValue / Rt_UnitValue / the bridge tables)
// are NOT cached as objects here because their backing globals are owned JVM-side — we look up
// the INSTANCE static fields lazily through the cached field IDs instead.
//
// A missing class/field/method aborts hard via the throwing g_refsError path (build/ABI
// breakage), NEVER a soft-fail.
// =====================================================================================

namespace {

constexpr const char *kRtIntValue = "net/postchain/rell/base/runtime/Rt_IntValue";
constexpr const char *kRtBooleanValue = "net/postchain/rell/base/runtime/Rt_BooleanValue";
constexpr const char *kRtRowidValue = "net/postchain/rell/base/runtime/Rt_RowidValue";
constexpr const char *kRtBigIntegerValue = "net/postchain/rell/base/runtime/Rt_BigIntegerValue";
constexpr const char *kRtDecimalValue = "net/postchain/rell/base/runtime/Rt_DecimalValue";
constexpr const char *kRtNullValue = "net/postchain/rell/base/runtime/Rt_NullValue";
constexpr const char *kRtUnitValue = "net/postchain/rell/base/runtime/Rt_UnitValue";

// Kotlin compiles `companion object` factories into both a synthetic Companion class and, for
// @JvmStatic members, static methods on the outer class. Rt_RowidValue.get / Rt_BigIntegerValue
// .get(BigInteger) are NOT @JvmStatic, so they live on the Companion instance; Rt_IntValue.get,
// Rt_BooleanValue.get, Rt_DecimalValue.get, Rt_BigIntegerValue.get(Long) ARE @JvmStatic.
constexpr const char *kCompanionField = "Companion";

// The JVM-side bridge classes the Wiring phase provides. Looked up here so the universal
// stdlib caller and the SQL back-calls can dispatch without re-resolving per call.
constexpr const char *kLlvmSysBridge = "net/postchain/rell/llvm/Llvm_SysBridge";
constexpr const char *kLlvmCallEnv = "net/postchain/rell/llvm/Llvm_CallEnv";

constexpr const char *kJavaBigInteger = "java/math/BigInteger";
constexpr const char *kJavaBigDecimal = "java/math/BigDecimal";

struct RellRefs {
    // ---- value classes (global jclass refs) -----------------------------------------
    jclass intValue = nullptr;
    jclass booleanValue = nullptr;  // the sealed interface; runtime objects implement it.
    jclass rowidValue = nullptr;
    jclass bigIntegerValue = nullptr;
    jclass decimalValue = nullptr;  // the Rt_DecimalValue interface.
    jclass nullValue = nullptr;
    jclass unitValue = nullptr;

    // ---- primitive backing fields (real @JvmRecord val fields) -----------------------
    jfieldID intValueField = nullptr;          // Rt_IntValue.value : J
    jfieldID rowidValueField = nullptr;        // Rt_RowidValue.value : J
    jfieldID bigIntegerValueField = nullptr;   // Rt_BigIntegerValue.value : Ljava/math/BigInteger;

    // ---- getter methods (interface-backed, no plain field) ---------------------------
    jmethodID booleanGetter = nullptr;   // Rt_BooleanValue.getValue() : Z
    jmethodID decimalGetter = nullptr;   // Rt_DecimalValue.getValue() : Ljava/math/BigDecimal;

    // ---- factories -------------------------------------------------------------------
    jmethodID intGet = nullptr;          // static Rt_IntValue.get(J) : Rt_IntValue
    jmethodID booleanGet = nullptr;      // static Rt_BooleanValue.get(Z) : Rt_BooleanValue
    jmethodID bigIntegerGetLong = nullptr;  // static Rt_BigIntegerValue.get(J) : Rt_BigIntegerValue
    jmethodID decimalGetBd = nullptr;    // static Rt_DecimalValue.get(Ljava/math/BigDecimal;) : ...

    // Companion-instance factories (not @JvmStatic).
    jclass rowidCompanionClass = nullptr;
    jobject rowidCompanion = nullptr;    // global ref to Rt_RowidValue.Companion
    jmethodID rowidGet = nullptr;        // Rt_RowidValue.Companion.get(J) : Rt_RowidValue

    // ---- singletons (INSTANCE static fields) -----------------------------------------
    jfieldID nullInstance = nullptr;     // Rt_NullValue.INSTANCE
    jfieldID unitInstance = nullptr;     // Rt_UnitValue.INSTANCE

    // ---- java.math helpers -----------------------------------------------------------
    jclass bigInteger = nullptr;
    jclass bigDecimal = nullptr;
    jmethodID bigIntegerBitLength = nullptr;  // BigInteger.bitLength() : I
    jmethodID bigIntegerLongValue = nullptr;  // BigInteger.longValue() : J
    jmethodID bigDecimalValueOf = nullptr;    // static BigDecimal.valueOf(JI) : BigDecimal

    // ---- bridge entry points ---------------------------------------------------------
    jclass sysBridge = nullptr;
    jmethodID sysDispatch = nullptr;     // static Llvm_SysBridge.dispatch(I[Ljava/lang/Object;J)Ljava/lang/Object;
    jclass callEnv = nullptr;
    jmethodID evalExpr = nullptr;        // static Llvm_CallEnv.evalDbExpr(IJ)Ljava/lang/Object;
    jmethodID execStmt = nullptr;        // static Llvm_CallEnv.execDbStmt(IJ)I
};

RellRefs g_refs;
std::once_flag g_refsOnce;
std::string g_refsError;  // non-empty after a failed init; checked by ensureRellRefsInitialized.

// Promote a local jclass to a cached global ref. Records an error (and returns nullptr) on a
// missing class so the once-init can report the first failure.
jclass findGlobalClass(JNIEnv *env, const char *name) {
    jclass local = env->FindClass(name);
    if (local == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (g_refsError.empty()) g_refsError = std::string("class not found: ") + name;
        return nullptr;
    }
    auto global = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    if (global == nullptr && g_refsError.empty()) {
        g_refsError = std::string("NewGlobalRef failed for class: ") + name;
    }
    return global;
}

jfieldID findField(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (cls == nullptr) return nullptr;
    jfieldID id = env->GetFieldID(cls, name, sig);
    if (id == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (g_refsError.empty()) g_refsError = std::string("field not found: ") + name + sig;
    }
    return id;
}

jfieldID findStaticField(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (cls == nullptr) return nullptr;
    jfieldID id = env->GetStaticFieldID(cls, name, sig);
    if (id == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (g_refsError.empty()) g_refsError = std::string("static field not found: ") + name + sig;
    }
    return id;
}

jmethodID findMethod(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (cls == nullptr) return nullptr;
    jmethodID id = env->GetMethodID(cls, name, sig);
    if (id == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (g_refsError.empty()) g_refsError = std::string("method not found: ") + name + sig;
    }
    return id;
}

jmethodID findStaticMethod(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    if (cls == nullptr) return nullptr;
    jmethodID id = env->GetStaticMethodID(cls, name, sig);
    if (id == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (g_refsError.empty()) g_refsError = std::string("static method not found: ") + name + sig;
    }
    return id;
}

void initRefs(JNIEnv *env) {
    g_refs.intValue = findGlobalClass(env, kRtIntValue);
    g_refs.booleanValue = findGlobalClass(env, kRtBooleanValue);
    g_refs.rowidValue = findGlobalClass(env, kRtRowidValue);
    g_refs.bigIntegerValue = findGlobalClass(env, kRtBigIntegerValue);
    g_refs.decimalValue = findGlobalClass(env, kRtDecimalValue);
    g_refs.nullValue = findGlobalClass(env, kRtNullValue);
    g_refs.unitValue = findGlobalClass(env, kRtUnitValue);

    // Primitive backing fields. Rt_IntValue / Rt_RowidValue are @JvmRecord data classes whose
    // `val value: Long` is a real `J` field; Rt_BigIntegerValue.value is a BigInteger field.
    g_refs.intValueField = findField(env, g_refs.intValue, "value", "J");
    g_refs.rowidValueField = findField(env, g_refs.rowidValue, "value", "J");
    g_refs.bigIntegerValueField =
        findField(env, g_refs.bigIntegerValue, "value", "Ljava/math/BigInteger;");

    // Rt_BooleanValue is a sealed *interface*: `value` is an abstract property with no backing
    // field, so we must invoke the getter on the concrete TRUE/FALSE objects. Same for
    // Rt_DecimalValue (interface; impl Rt_BigDecimalValue / long-scale leaves).
    g_refs.booleanGetter = findMethod(env, g_refs.booleanValue, "getValue", "()Z");
    g_refs.decimalGetter =
        findMethod(env, g_refs.decimalValue, "getValue", "()Ljava/math/BigDecimal;");

    // @JvmStatic factories live as static methods on the outer class.
    g_refs.intGet = findStaticMethod(env, g_refs.intValue, "get",
                                     "(J)Lnet/postchain/rell/base/runtime/Rt_IntValue;");
    g_refs.booleanGet = findStaticMethod(env, g_refs.booleanValue, "get",
                                         "(Z)Lnet/postchain/rell/base/runtime/Rt_BooleanValue;");
    g_refs.bigIntegerGetLong =
        findStaticMethod(env, g_refs.bigIntegerValue, "get",
                         "(J)Lnet/postchain/rell/base/runtime/Rt_BigIntegerValue;");
    g_refs.decimalGetBd =
        findStaticMethod(env, g_refs.decimalValue, "get",
                         "(Ljava/math/BigDecimal;)Lnet/postchain/rell/base/runtime/Rt_DecimalValue;");

    // Rt_RowidValue.get is NOT @JvmStatic — it lives on the Companion instance.
    {
        jfieldID companionField =
            findStaticField(env, g_refs.rowidValue, kCompanionField,
                            "Lnet/postchain/rell/base/runtime/Rt_RowidValue$Companion;");
        if (companionField != nullptr) {
            jobject companion = env->GetStaticObjectField(g_refs.rowidValue, companionField);
            if (companion != nullptr) {
                g_refs.rowidCompanion = env->NewGlobalRef(companion);
                g_refs.rowidCompanionClass =
                    static_cast<jclass>(env->NewGlobalRef(env->GetObjectClass(companion)));
                env->DeleteLocalRef(companion);
                g_refs.rowidGet =
                    findMethod(env, g_refs.rowidCompanionClass, "get",
                               "(J)Lnet/postchain/rell/base/runtime/Rt_RowidValue;");
            } else if (g_refsError.empty()) {
                g_refsError = "Rt_RowidValue.Companion is null";
            }
        }
    }

    // Singletons.
    g_refs.nullInstance =
        findStaticField(env, g_refs.nullValue, "INSTANCE",
                        "Lnet/postchain/rell/base/runtime/Rt_NullValue;");
    g_refs.unitInstance =
        findStaticField(env, g_refs.unitValue, "INSTANCE",
                        "Lnet/postchain/rell/base/runtime/Rt_UnitValue;");

    // java.math helpers for the numeric envelope checks / DEC_LONG rebox.
    g_refs.bigInteger = findGlobalClass(env, kJavaBigInteger);
    g_refs.bigDecimal = findGlobalClass(env, kJavaBigDecimal);
    g_refs.bigIntegerBitLength = findMethod(env, g_refs.bigInteger, "bitLength", "()I");
    g_refs.bigIntegerLongValue = findMethod(env, g_refs.bigInteger, "longValue", "()J");
    g_refs.bigDecimalValueOf =
        findStaticMethod(env, g_refs.bigDecimal, "valueOf", "(JI)Ljava/math/BigDecimal;");

    // Bridge entry points (Wiring phase). The dispatch signature mirrors the ABI exactly:
    // Llvm_SysBridge.dispatch(int sysfnId, Object[] args, long frameHandle) : Object.
    g_refs.sysBridge = findGlobalClass(env, kLlvmSysBridge);
    g_refs.sysDispatch =
        findStaticMethod(env, g_refs.sysBridge, "dispatch", "(I[Ljava/lang/Object;J)Ljava/lang/Object;");
    g_refs.callEnv = findGlobalClass(env, kLlvmCallEnv);
    g_refs.evalExpr = findStaticMethod(env, g_refs.callEnv, "evalDbExpr", "(IJ)Ljava/lang/Object;");
    g_refs.execStmt = findStaticMethod(env, g_refs.callEnv, "execDbStmt", "(IJ)I");
}

}  // namespace

void ensureRellRefsInitialized(JNIEnv *env) {
    std::call_once(g_refsOnce, [env]() { initRefs(env); });
    if (!g_refsError.empty()) {
        // Hard ABI/build breakage. Throwing here aborts the whole compile/run — a missing
        // Rt_Value class or factory means the native runtime can never be correct.
        throwRuntime(env, ("RellRefs init failed: " + g_refsError).c_str());
    }
}

// =====================================================================================
// 4a. JVM -> native (unwrap).
//
// Class-identity dispatch in the same order the ABI documents. A null jobject where a value is
// required is a HARD error (the caller passed a missing slot), NOT Rell `null` — that is the
// Rt_NullValue singleton.
// =====================================================================================

RellValue from_jvm(JNIEnv *env, RellArena &arena, jobject rtValue) {
    ensureRellRefsInitialized(env);
    if (env->ExceptionCheck()) return rv_none();

    if (rtValue == nullptr) {
        throwIllegalArgument(env, "from_jvm: null Rt_Value (a missing slot, not Rell null)");
        return rv_none();
    }

    // Singletons first (cheap reference identity via IsInstanceOf on the singleton class).
    if (env->IsInstanceOf(rtValue, g_refs.nullValue)) return rv_null();
    if (env->IsInstanceOf(rtValue, g_refs.unitValue)) return rv_unit();

    // Boolean: sealed interface, value via getter (TRUE/FALSE objects have no backing field).
    if (env->IsInstanceOf(rtValue, g_refs.booleanValue)) {
        jboolean b = env->CallBooleanMethod(rtValue, g_refs.booleanGetter);
        if (env->ExceptionCheck()) return rv_none();
        return rv_boolean(b == JNI_TRUE);
    }

    // Integer: real `J` backing field.
    if (env->IsInstanceOf(rtValue, g_refs.intValue)) {
        jlong v = env->GetLongField(rtValue, g_refs.intValueField);
        return rv_integer(static_cast<int64_t>(v));
    }

    // Rowid: real `J` backing field; invariant >= 0 already enforced JVM-side at construction.
    if (env->IsInstanceOf(rtValue, g_refs.rowidValue)) {
        jlong v = env->GetLongField(rtValue, g_refs.rowidValueField);
        return rv_rowid(static_cast<int64_t>(v));
    }

    // BigInteger: long-fit gate via BigInteger.bitLength (a value fits i64 iff bitLength < 64,
    // covering Long.MIN..Long.MAX exactly — bitLength excludes the sign bit and BigInteger uses
    // two's-complement-free magnitude, so Long.MIN's |v|=2^63 has bitLength 64 and is excluded;
    // Long.MIN itself has bitLength 63, included). Anything wider stays a HANDLE.
    // DETERMINISM: we never crack a BigInteger's bytes in C++ — bitLength()/longValue() are the
    // JVM's own, so the inline value is bit-exact and out-of-envelope values route via HANDLE.
    if (env->IsInstanceOf(rtValue, g_refs.bigIntegerValue)) {
        jobject bi = env->GetObjectField(rtValue, g_refs.bigIntegerValueField);
        if (bi == nullptr) {
            throwRuntime(env, "Rt_BigIntegerValue.value is null");
            return rv_none();
        }
        jint bits = env->CallIntMethod(bi, g_refs.bigIntegerBitLength);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(bi);
            return rv_none();
        }
        if (bits < 64) {
            jlong v = env->CallLongMethod(bi, g_refs.bigIntegerLongValue);
            env->DeleteLocalRef(bi);
            if (env->ExceptionCheck()) return rv_none();
            return rv_bigint_long(static_cast<int64_t>(v));
        }
        env->DeleteLocalRef(bi);
        // Out of i64 envelope: keep the whole Rt_BigIntegerValue as a handle.
        return arena.adopt(rtValue);
    }

    // Decimal: the ABI forbids reconstructing (mantissa, scale) in C++ (that is
    // Lib_DecimalMath.scale's job). An arbitrary Rt_DecimalValue from the JVM is therefore
    // always adopted as a HANDLE; the DEC_LONG inline tag is only ever produced by the lowering
    // from JVM-validated constants or by pure in-envelope intrinsics, never here.
    // DETERMINISM: no C++ scale stripping — avoids any divergence from Lib_DecimalMath.scale.
    if (env->IsInstanceOf(rtValue, g_refs.decimalValue)) {
        return arena.adopt(rtValue);
    }

    // Everything else (text, byte_array, json, gtv, collections, struct, entity, tuple,
    // virtual, range, function, enum, ...) is the universal HANDLE escape.
    return arena.adopt(rtValue);
}

// =====================================================================================
// 4b. native -> JVM (rebox).
//
// Returns a LOCAL ref owned by the caller's JNI frame, except HANDLE which returns the
// arena-owned global ref directly. Inline tags hit the cached factories; those factories can
// throw Rt_Exception (decimal/bigint overflow, negative rowid) — the caller MUST ExceptionCheck.
// =====================================================================================

jobject to_jvm(JNIEnv *env, RellArena &arena, const RellValue &value) {
    ensureRellRefsInitialized(env);
    if (env->ExceptionCheck()) return nullptr;

    switch (value.tag) {
        case RellTag::NULL_:
            return env->GetStaticObjectField(g_refs.nullValue, g_refs.nullInstance);

        case RellTag::UNIT:
            return env->GetStaticObjectField(g_refs.unitValue, g_refs.unitInstance);

        case RellTag::BOOLEAN:
            return env->CallStaticObjectMethod(g_refs.booleanValue, g_refs.booleanGet,
                                               value.payload.i64 != 0 ? JNI_TRUE : JNI_FALSE);

        case RellTag::INTEGER:
            return env->CallStaticObjectMethod(g_refs.intValue, g_refs.intGet,
                                               static_cast<jlong>(value.payload.i64));

        case RellTag::ROWID:
            // Companion-instance factory (Rt_RowidValue.get is not @JvmStatic). The init check
            // (value >= 0) is enforced JVM-side; a negative would throw, hence ExceptionCheck.
            return env->CallObjectMethod(g_refs.rowidCompanion, g_refs.rowidGet,
                                         static_cast<jlong>(value.payload.i64));

        case RellTag::BIGINT_LONG:
            // Long-fitting big_integer: the @JvmStatic Rt_BigIntegerValue.get(Long) is the
            // canonical, range-checked factory. In-envelope so no overflow, but the call can
            // still throw in principle — ExceptionCheck downstream.
            return env->CallStaticObjectMethod(g_refs.bigIntegerValue, g_refs.bigIntegerGetLong,
                                               static_cast<jlong>(value.payload.i64));

        case RellTag::DEC_LONG: {
            // Rebox via BigDecimal.valueOf(unscaledLong, scale) -> Rt_DecimalValue.get(BigDecimal).
            // DETERMINISM: this is exactly the JVM's own path (Tf_LongScaleDecimal.value builds
            // BigDecimal.valueOf(mantissa, scale) before Lib_DecimalMath.scale via
            // Rt_DecimalValue.get), so the result is bit-exact without depending on the Truffle
            // module. get() can throw decimal:overflow — caller ExceptionChecks.
            jobject bd = env->CallStaticObjectMethod(g_refs.bigDecimal, g_refs.bigDecimalValueOf,
                                                     static_cast<jlong>(value.payload.i64),
                                                     static_cast<jint>(value.scale));
            if (bd == nullptr || env->ExceptionCheck()) return nullptr;
            jobject dec = env->CallStaticObjectMethod(g_refs.decimalValue, g_refs.decimalGetBd, bd);
            env->DeleteLocalRef(bd);
            return dec;
        }

        case RellTag::HANDLE:
            // The arena already owns this global ref; hand it back directly. (Owned by the
            // arena, not the caller's frame — do NOT DeleteGlobalRef it here.)
            return value.payload.handle;

        case RellTag::NONE:
        default:
            // NONE is the poison/abort sentinel and must never be reboxed.
            throwRuntime(env, "to_jvm: NONE/poison RellValue cannot be reboxed");
            return nullptr;
    }
}

// Sections 5 (universal stdlib caller), 6 (SQL back-call) and the interning tables live in
// their canonical homes: stdlib_bridge.cpp (rell_sysfn_call, SysFnTable) and sql_bridge.cpp
// (rell_db_eval_expr, rell_db_exec_stmt, DbNodeTable). value.cpp owns only the value/arena/
// marshalling layer (g_refs, to_jvm, from_jvm, ensureRellRefsInitialized).

}  // namespace rell::llvm_rt
