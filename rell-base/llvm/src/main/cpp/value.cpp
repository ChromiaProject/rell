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

#include "rell_crypto.h"

#include <cstring>

#include <llvm/IR/Constants.h>

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

    // Composite sweep: `delete` every native composite this arena allocated. A composite's `fields`
    // vector frees its own RellValue cells; the HANDLE / nested-COMPOSITE values INSIDE those cells
    // are owned by THIS same arena (the globals_ sweep above released the HANDLEs; the nested
    // composites are their own entries in composites_), so there is no double-free and no leak — the
    // ownership graph is flat (every node is a direct arena entry), not nested-owning.
    for (RellComposite *c : composites_) {
        delete c;
    }
    composites_.clear();

    // List sweep: same flat-ownership contract as composites. A RellList's `elems` vector frees its
    // own RellValue cells; the HANDLE / nested-COMPOSITE / nested-LIST values inside them are their
    // own arena entries (released by the globals_/composites_/lists_ sweeps), and the list's typeRef
    // global was tracked in globals_ (released above), so there is no double-free and no leak.
    for (RellList *l : lists_) {
        delete l;
    }
    lists_.clear();

    // byte_array sweep: each carrier owns its heap byte buffer (`delete[] data`) plus the carrier
    // itself (`delete`). A BYTEARRAY value is a flat ownership entry (its buffer is the only owned
    // resource; there is no nested arena-owned value inside it, unlike composites/lists), so a single
    // sweep frees it exactly once — no leak, no use-after-free.
    for (RellByteArray *ba : byteArrays_) {
        if (ba != nullptr) {
            delete[] ba->data;
            delete ba;
        }
    }
    byteArrays_.clear();

    // text sweep: same flat-ownership contract as byte_array. Each carrier owns its heap UTF-16 buffer
    // (`delete[] data`) plus the carrier (`delete`). A TEXT value is a flat ownership entry (its buffer
    // is the only owned resource; no nested arena-owned value inside it), so a single sweep frees it
    // exactly once — no leak, no use-after-free.
    for (RellText *t : texts_) {
        if (t != nullptr) {
            delete[] t->data;
            delete t;
        }
    }
    texts_.clear();
}

RellComposite *RellArena::makeComposite(int32_t scaleDisc) {
    // Heap-allocate and record. push_back may throw std::bad_alloc on OOM, which propagates as a
    // C++ exception (a hard fault, never a soft-fail); we do not catch it here.
    auto *c = new RellComposite();
    c->scaleDisc = scaleDisc;
    composites_.push_back(c);
    return c;
}

RellList *RellArena::makeList() {
    auto *l = new RellList();
    l->typeRef = nullptr;
    lists_.push_back(l);
    return l;
}

RellByteArray *RellArena::makeByteArray(const uint8_t *src, int32_t len) {
    // Defensive: a negative length is a caller bug; clamp to 0 (empty) rather than allocate garbage.
    if (len < 0) len = 0;
    auto *ba = new RellByteArray();
    ba->len = len;
    if (len == 0) {
        ba->data = nullptr;  // empty byte_array — to_jvm canonicalises to Rt_ByteArrayValue.EMPTY.
    } else {
        ba->data = new uint8_t[static_cast<size_t>(len)];
        if (src != nullptr) {
            std::memcpy(ba->data, src, static_cast<size_t>(len));
        } else {
            // A null src with len > 0 is a wiring fault; zero-fill so we never read uninitialised
            // bytes into consensus. (Never happens in normal operation — makeByteArray is only called
            // with a real buffer.)
            std::memset(ba->data, 0, static_cast<size_t>(len));
        }
    }
    byteArrays_.push_back(ba);
    return ba;
}

RellText *RellArena::makeText(const uint16_t *src, int32_t len) {
    // Defensive: a negative length is a caller bug; clamp to 0 (empty) rather than allocate garbage.
    if (len < 0) len = 0;
    auto *t = new RellText();
    t->len = len;
    if (len == 0) {
        t->data = nullptr;  // empty text — to_jvm canonicalises to Rt_TextValue.EMPTY.
    } else {
        t->data = new uint16_t[static_cast<size_t>(len)];
        if (src != nullptr) {
            std::memcpy(t->data, src, static_cast<size_t>(len) * sizeof(uint16_t));
        } else {
            // A null src with len > 0 is a wiring fault; zero-fill so we never read uninitialised code
            // units into consensus. (Never happens in normal operation — makeText is only called with a
            // real buffer, or with src == nullptr only when the caller fills it itself, e.g. text_get.)
            std::memset(t->data, 0, static_cast<size_t>(len) * sizeof(uint16_t));
        }
    }
    texts_.push_back(t);
    return t;
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
constexpr const char *kRtRREnumValue = "net/postchain/rell/base/runtime/Rt_RR_EnumValue";
constexpr const char *kRtStructValue = "net/postchain/rell/base/runtime/Rt_StructValue";
constexpr const char *kRtTupleValue = "net/postchain/rell/base/runtime/Rt_TupleValue";
constexpr const char *kRtListValue = "net/postchain/rell/base/runtime/Rt_ListValue";
constexpr const char *kRtByteArrayValue = "net/postchain/rell/base/runtime/Rt_ByteArrayValue";
constexpr const char *kRtTextValue = "net/postchain/rell/base/runtime/Rt_TextValue";

// Kotlin compiles `companion object` factories into both a synthetic Companion class and, for
// @JvmStatic members, static methods on the outer class. Rt_RowidValue.get is NOT @JvmStatic, so it
// lives on the Companion instance; Rt_IntValue.get, Rt_BooleanValue.get, Rt_DecimalValue.get, and
// Rt_BigIntegerValue.get(BigInteger) ARE @JvmStatic. (Rt_BigIntegerValue.get(Long) is NOT @JvmStatic
// — it forwards to get(BigInteger) — so we resolve and call the BigInteger-typed factory.)
constexpr const char *kCompanionField = "Companion";

constexpr const char *kJavaBigInteger = "java/math/BigInteger";
constexpr const char *kJavaBigDecimal = "java/math/BigDecimal";

// Lib_DecimalMath is a Kotlin `object`: its `scale(BigDecimal): BigDecimal?` is an INSTANCE method
// on the singleton (not @JvmStatic), reached via the INSTANCE static field. It is the canonical
// decimal normal-form gate the interpreter uses (Rt_DecimalValue.get -> Lib_DecimalMath.scale), so
// from_jvm calls it directly rather than re-deriving the envelope in C++ (DETERMINISM).
constexpr const char *kLibDecimalMath = "net/postchain/rell/base/lib/type/Lib_DecimalMath";

struct RellRefs {
    // ---- value classes (global jclass refs) -----------------------------------------
    jclass intValue = nullptr;
    jclass booleanValue = nullptr;  // the sealed interface; runtime objects implement it.
    jclass rowidValue = nullptr;
    jclass bigIntegerValue = nullptr;
    jclass decimalValue = nullptr;  // the Rt_DecimalValue interface.
    jclass nullValue = nullptr;
    jclass unitValue = nullptr;
    jclass rrEnumValue = nullptr;  // Rt_RR_EnumValue (the deserialized-path enum value class).
    jclass structValue = nullptr;  // Rt_StructValue (the sealed interface; all leaves implement it).
    jclass tupleValue = nullptr;   // Rt_TupleValue.
    jclass listValue = nullptr;    // Rt_ListValue.
    jclass byteArrayValue = nullptr;  // Rt_ByteArrayValue.
    jclass textValue = nullptr;       // Rt_TextValue (the sealed interface; Rt_JavaStringText implements it).

    // ---- Rt_ByteArrayValue accessor (the native byte_array from_jvm path) -------------
    jfieldID byteArrayValueField = nullptr;  // Rt_ByteArrayValue.value : [B (real backing field).

    // ---- Rt_TextValue accessor (the native text from_jvm path) ------------------------
    // Rt_TextValue is a sealed *interface* (rt_value_text.kt): `value: String` is an abstract property
    // with no backing field on the interface, so we invoke the getter on the concrete Rt_JavaStringText.
    jmethodID textGetter = nullptr;  // Rt_TextValue.getValue() : Ljava/lang/String;

    // ---- Rt_StructValue interface accessors (the native struct from_jvm path) --------
    jmethodID structSize = nullptr;  // Rt_StructValue.size() : I
    jmethodID structGet = nullptr;   // Rt_StructValue.get(I) : Rt_Value

    // ---- Rt_Value type accessor (the native list typeRef) ----------------------------
    jmethodID valueGetType = nullptr;  // Rt_Value.getType() : Rt_ValueClass — the list's typeRef.

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
    jmethodID bigIntegerGetBi = nullptr; // static Rt_BigIntegerValue.get(Ljava/math/BigInteger;) : Rt_BigIntegerValue
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
    jmethodID bigIntegerValueOf = nullptr;    // static BigInteger.valueOf(J) : BigInteger
    jmethodID bigDecimalValueOf = nullptr;    // static BigDecimal.valueOf(JI) : BigDecimal

    // ---- DEC_LONG cracking (from_jvm long-fit decimal) -------------------------------
    // Tf_LongScaleDecimal.tryFrom transcription, reusing the JVM's own methods so the inline
    // (mantissa, scale) is bit-exact with the interpreter's canonical normal form.
    jclass libDecimalMath = nullptr;            // Lib_DecimalMath (Kotlin object)
    jobject libDecimalMathInstance = nullptr;   // Lib_DecimalMath.INSTANCE (global ref)
    jmethodID libDecimalMathScale = nullptr;    // Lib_DecimalMath.scale(BigDecimal) : BigDecimal?
    jmethodID bigDecimalStripZeros = nullptr;   // BigDecimal.stripTrailingZeros() : BigDecimal
    jmethodID bigDecimalUnscaled = nullptr;     // BigDecimal.unscaledValue() : BigInteger
    jmethodID bigDecimalScale = nullptr;        // BigDecimal.scale() : I
    jmethodID bigDecimalSignum = nullptr;       // BigDecimal.signum() : I
};

RellRefs g_refs;
std::once_flag g_refsOnce;
std::string g_refsError;  // non-empty after a failed init; checked by ensureRellRefsInitialized.

// ---- Llvm_SysBridge enum rebox entry points (the ENUM tag <-> Rt_RR_EnumValue marshalling) ------
//
// Enum values cannot be cracked/rebuilt in C++ alone — naming the enum type (def_index) and
// reconstituting the canonical Rt_RR_EnumValue both need the RR_App, which lives JVM-side and is
// reached through the call env keyed by frameHandle. Resolved lazily once (separate from
// stdlib_bridge's dispatch cache; value.cpp owns the value layer). A missing class/method is a
// build/ABI breakage -> hard fault.
std::once_flag g_enumBridgeOnce;
jclass g_sysBridgeClassForEnum = nullptr;  // global ref to net/postchain/rell/llvm/Llvm_SysBridge
jmethodID g_enumCrackMethod = nullptr;     // static enumCrack(Rt_Value, long) : long
jmethodID g_enumValueMethod = nullptr;     // static enumValue(int, int, long) : Rt_Value
jmethodID g_structDefIndexMethod = nullptr;  // static structDefIndex(Rt_Value, long) : int
jmethodID g_structValueMethod = nullptr;     // static structValue(int, Rt_Value[], long) : Rt_Value
jmethodID g_listTypeMethod = nullptr;        // static listType(int, long) : Rt_ValueClass
jmethodID g_listValueMethod = nullptr;       // static listValue(Object, Rt_Value[], long) : Rt_Value
jmethodID g_listSizeMethod = nullptr;        // static listSize(Rt_Value) : int
jmethodID g_listGetMethod = nullptr;         // static listGet(Rt_Value, int) : Rt_Value
jmethodID g_byteArrayValueMethod = nullptr;  // static byteArrayValue(byte[]) : Rt_Value
jmethodID g_textValueMethod = nullptr;       // static textValue(String) : Rt_Value
jmethodID g_decimalValueMethod = nullptr;    // static decimalValue(String) : Rt_Value
std::string g_enumBridgeError;

void initEnumBridgeOnce(JNIEnv *env) {
    std::call_once(g_enumBridgeOnce, [env]() {
        jclass localCls = env->FindClass("net/postchain/rell/llvm/Llvm_SysBridge");
        if (localCls == nullptr) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            g_enumBridgeError = "Llvm_SysBridge class not found";
            return;
        }
        auto *globalCls = reinterpret_cast<jclass>(env->NewGlobalRef(localCls));
        env->DeleteLocalRef(localCls);
        if (globalCls == nullptr) {
            g_enumBridgeError = "NewGlobalRef failed for Llvm_SysBridge";
            return;
        }
        // Kotlin does NOT erase Rt_Value to Object: the descriptors carry the concrete Rt_Value type.
        jmethodID crack = env->GetStaticMethodID(
            globalCls, "enumCrack",
            "(Lnet/postchain/rell/base/runtime/Rt_Value;J)J");
        jmethodID value = env->GetStaticMethodID(
            globalCls, "enumValue",
            "(IIJ)Lnet/postchain/rell/base/runtime/Rt_Value;");
        // Struct rebox bridge (mirrors the enum pair): structDefIndex cracks a Rt_StructValue to its
        // RR_App.allStructs def-index (needs the RR_App via ctxHandle); structValue rebuilds the EXACT
        // Rt_StructValue from (def-index, field Rt_Values) — the same construction the interpreter uses
        // for an RR_Expr.StructCreate, so from_jvm∘to_jvm is identity.
        jmethodID structDef = env->GetStaticMethodID(
            globalCls, "structDefIndex",
            "(Lnet/postchain/rell/base/runtime/Rt_Value;J)I");
        jmethodID structVal = env->GetStaticMethodID(
            globalCls, "structValue",
            "(I[Lnet/postchain/rell/base/runtime/Rt_Value;J)Lnet/postchain/rell/base/runtime/Rt_Value;");
        // List bridge (mirrors the struct pair). listType resolves an interned list-type id to its
        // JVM Rt_ValueClass (via the RR_App keyed by ctxHandle); listValue rebuilds the EXACT
        // Rt_ListValue from (type, element Rt_Values); listSize/listGet crack a Rt_ListValue's
        // elements; listIndexError raises the interpreter's exact list:index Rt_Exception.
        jmethodID listType = env->GetStaticMethodID(
            globalCls, "listType",
            "(IJ)Lnet/postchain/rell/base/runtime/Rt_ValueClass;");
        jmethodID listVal = env->GetStaticMethodID(
            globalCls, "listValue",
            "(Lnet/postchain/rell/base/runtime/Rt_ValueClass;[Lnet/postchain/rell/base/runtime/Rt_Value;J)"
            "Lnet/postchain/rell/base/runtime/Rt_Value;");
        jmethodID listSize = env->GetStaticMethodID(
            globalCls, "listSize", "(Lnet/postchain/rell/base/runtime/Rt_Value;)I");
        jmethodID listGet = env->GetStaticMethodID(
            globalCls, "listGet",
            "(Lnet/postchain/rell/base/runtime/Rt_Value;I)Lnet/postchain/rell/base/runtime/Rt_Value;");
        // byte_array rebox bridge: byteArrayValue rebuilds the EXACT Rt_ByteArrayValue from a byte[]
        // via Rt_ByteArrayValue.get (which canonicalises empty -> the EMPTY singleton), so
        // from_jvm∘to_jvm is content-identity. No ctxHandle needed (byte_array is a pure value type).
        jmethodID byteArrayVal = env->GetStaticMethodID(
            globalCls, "byteArrayValue", "([B)Lnet/postchain/rell/base/runtime/Rt_Value;");
        // text rebox bridge: textValue rebuilds the EXACT Rt_TextValue from a String via
        // Rt_TextValue.get (which canonicalises empty -> the EMPTY singleton), so from_jvm∘to_jvm is
        // content-identity. No ctxHandle needed (text is a pure value type).
        jmethodID textVal = env->GetStaticMethodID(
            globalCls, "textValue", "(Ljava/lang/String;)Lnet/postchain/rell/base/runtime/Rt_Value;");
        // decimal CONSTANT materialiser bridge: decimalValue builds the EXACT Rt_DecimalValue from its
        // plain string via Rt_DecimalValue.get (the interpreter's RR_ConstantValue.Decimal construction);
        // rell_make_decimal then cracks it through from_jvm to the canonical inline DEC_LONG (or a wide
        // HANDLE). No ctxHandle needed (decimal is a pure value type).
        jmethodID decimalVal = env->GetStaticMethodID(
            globalCls, "decimalValue",
            "(Ljava/lang/String;)Lnet/postchain/rell/base/runtime/Rt_Value;");
        if (crack == nullptr || value == nullptr || structDef == nullptr || structVal == nullptr ||
            listType == nullptr || listVal == nullptr || listSize == nullptr || listGet == nullptr ||
            byteArrayVal == nullptr || textVal == nullptr || decimalVal == nullptr) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteGlobalRef(globalCls);
            g_enumBridgeError = "Llvm_SysBridge enum/struct/list/text/decimal bridge methods not found";
            return;
        }
        g_sysBridgeClassForEnum = globalCls;
        g_enumCrackMethod = crack;
        g_enumValueMethod = value;
        g_structDefIndexMethod = structDef;
        g_structValueMethod = structVal;
        g_listTypeMethod = listType;
        g_listValueMethod = listVal;
        g_listSizeMethod = listSize;
        g_listGetMethod = listGet;
        g_byteArrayValueMethod = byteArrayVal;
        g_textValueMethod = textVal;
        g_decimalValueMethod = decimalVal;
    });
}

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
    g_refs.rrEnumValue = findGlobalClass(env, kRtRREnumValue);
    g_refs.structValue = findGlobalClass(env, kRtStructValue);
    g_refs.tupleValue = findGlobalClass(env, kRtTupleValue);
    g_refs.listValue = findGlobalClass(env, kRtListValue);
    g_refs.byteArrayValue = findGlobalClass(env, kRtByteArrayValue);
    g_refs.textValue = findGlobalClass(env, kRtTextValue);

    // Rt_TextValue.value is an abstract interface property (no backing field on the sealed interface);
    // from_jvm invokes the getter on the concrete Rt_TextValue instance to obtain the String.
    g_refs.textGetter = findMethod(env, g_refs.textValue, "getValue", "()Ljava/lang/String;");

    // Rt_ByteArrayValue.value is a real `[B` backing field (the data class's `val value: ByteArray`),
    // read by from_jvm to copy the bytes into an arena buffer without a JVM call.
    g_refs.byteArrayValueField = findField(env, g_refs.byteArrayValue, "value", "[B");

    // Rt_Value.getType() returns the value's Rt_ValueClass — the list's typeRef carried across the
    // value ABI so to_jvm rebuilds the EXACT Rt_ListValue without naming a structural list type.
    g_refs.valueGetType =
        findMethod(env, findGlobalClass(env, "net/postchain/rell/base/runtime/Rt_Value"), "getType",
                   "()Lnet/postchain/rell/base/runtime/Rt_ValueClass;");

    // Rt_StructValue is a sealed interface; size()/get(Int) are abstract members reached via the
    // interface class (every concrete leaf implements them). Used by from_jvm to crack a struct
    // into a native COMPOSITE without reimplementing the field layout in C++.
    g_refs.structSize = findMethod(env, g_refs.structValue, "size", "()I");
    g_refs.structGet =
        findMethod(env, g_refs.structValue, "get", "(I)Lnet/postchain/rell/base/runtime/Rt_Value;");

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
    // Rt_BigIntegerValue.get(Long) is NOT @JvmStatic; only get(BigInteger) is. Resolve the
    // BigInteger-typed factory and rebox long-fitting bigints through BigInteger.valueOf below.
    g_refs.bigIntegerGetBi =
        findStaticMethod(env, g_refs.bigIntegerValue, "get",
                         "(Ljava/math/BigInteger;)Lnet/postchain/rell/base/runtime/Rt_BigIntegerValue;");
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
    g_refs.bigIntegerValueOf =
        findStaticMethod(env, g_refs.bigInteger, "valueOf", "(J)Ljava/math/BigInteger;");
    g_refs.bigDecimalValueOf =
        findStaticMethod(env, g_refs.bigDecimal, "valueOf", "(JI)Ljava/math/BigDecimal;");

    // DEC_LONG cracking helpers. Lib_DecimalMath.scale is the canonical normal-form gate; the
    // BigDecimal accessors reproduce Tf_LongScaleDecimal.tryFrom step-for-step using the JVM's own
    // methods (no C++ scale arithmetic) so the inline (mantissa, scale) is bit-exact.
    g_refs.libDecimalMath = findGlobalClass(env, kLibDecimalMath);
    {
        jfieldID instanceField =
            findStaticField(env, g_refs.libDecimalMath, "INSTANCE",
                            "Lnet/postchain/rell/base/lib/type/Lib_DecimalMath;");
        if (instanceField != nullptr) {
            jobject inst = env->GetStaticObjectField(g_refs.libDecimalMath, instanceField);
            if (inst != nullptr) {
                g_refs.libDecimalMathInstance = env->NewGlobalRef(inst);
                env->DeleteLocalRef(inst);
            } else if (g_refsError.empty()) {
                g_refsError = "Lib_DecimalMath.INSTANCE is null";
            }
        }
    }
    g_refs.libDecimalMathScale =
        findMethod(env, g_refs.libDecimalMath, "scale",
                   "(Ljava/math/BigDecimal;)Ljava/math/BigDecimal;");
    g_refs.bigDecimalStripZeros =
        findMethod(env, g_refs.bigDecimal, "stripTrailingZeros", "()Ljava/math/BigDecimal;");
    g_refs.bigDecimalUnscaled =
        findMethod(env, g_refs.bigDecimal, "unscaledValue", "()Ljava/math/BigInteger;");
    g_refs.bigDecimalScale = findMethod(env, g_refs.bigDecimal, "scale", "()I");
    g_refs.bigDecimalSignum = findMethod(env, g_refs.bigDecimal, "signum", "()I");

    // The Llvm_SysBridge.dispatch and Llvm_DbBridge.evalExpr/execStmt entry points are resolved
    // lazily where they are actually invoked (stdlib_bridge.cpp / sql_bridge.cpp), with the real
    // Rt_Value-typed JNI descriptors. They are intentionally NOT resolved here: doing so with stale
    // signatures would fail init and break every value-ABI op with "RellRefs init failed".
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

RellValue from_jvm(JNIEnv *env, RellArena &arena, jobject rtValue, jlong frameHandle) {
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

    // Decimal: crack a long-fitting value into an INLINE DEC_LONG at the interpreter's EXACT
    // canonical stripped normal form, else keep a HANDLE. This is a faithful transcription of
    // Tf_LongScaleDecimal.tryFrom (tf_long_scale_decimal.kt), reusing the JVM's OWN methods at every
    // step so the (mantissa, scale) is bit-exact — the llvm module does NOT depend on runtime-truffle,
    // so we call Lib_DecimalMath.scale + java.math directly rather than the Truffle leaf:
    //   1. bd = value.getValue() (already canonical: BigDecimal.ZERO scale 0, or scale 20).
    //   2. if Lib_DecimalMath.scale(bd) == null -> out of Rell's decimal range -> HANDLE.
    //   3. stripped = bd.stripTrailingZeros(); unscaled = stripped.unscaledValue().
    //   4. if unscaled.bitLength() >= 64 -> mantissa doesn't fit i64 -> HANDLE.
    //   5. DEC_LONG(mantissa = unscaled.longValue(), scale = stripped.scale()).
    // M3 INVARIANT: every inline DEC_LONG this produces is the interpreter's stripped normal form
    // (1.0 and 1.00 both -> (1, 0)), so DEC_LONG payload+scale equality coincides with value equality
    // and to_jvm's BigDecimal.valueOf(m, s) -> Rt_DecimalValue.get round-trips to the same canonical
    // value. ZERO collapses to (0, 0) (scale(0) returns BigDecimal.ZERO, strip keeps scale 0).
    if (env->IsInstanceOf(rtValue, g_refs.decimalValue)) {
        jobject bd = env->CallObjectMethod(rtValue, g_refs.decimalGetter);
        if (env->ExceptionCheck()) return rv_none();
        if (bd == nullptr) {
            throwRuntime(env, "Rt_DecimalValue.value is null");
            return rv_none();
        }
        // Step 2: bounds gate via Lib_DecimalMath.scale. A null return means out of range -> HANDLE.
        jobject scaled = env->CallObjectMethod(g_refs.libDecimalMathInstance,
                                               g_refs.libDecimalMathScale, bd);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(bd);
            return rv_none();
        }
        if (scaled == nullptr) {
            env->DeleteLocalRef(bd);
            return arena.adopt(rtValue);  // out of Rell decimal range — keep the wide HANDLE.
        }
        env->DeleteLocalRef(scaled);  // used only as the non-null bounds gate (matches tryFrom).

        // Steps 3-5: strip the ORIGINAL bd (tryFrom strips `bd`, not the scaled re-check value).
        jobject stripped = env->CallObjectMethod(bd, g_refs.bigDecimalStripZeros);
        env->DeleteLocalRef(bd);
        if (env->ExceptionCheck() || stripped == nullptr) {
            if (stripped != nullptr) env->DeleteLocalRef(stripped);
            return rv_none();
        }
        jobject unscaled = env->CallObjectMethod(stripped, g_refs.bigDecimalUnscaled);
        if (env->ExceptionCheck() || unscaled == nullptr) {
            env->DeleteLocalRef(stripped);
            if (unscaled != nullptr) env->DeleteLocalRef(unscaled);
            return rv_none();
        }
        jint bits = env->CallIntMethod(unscaled, g_refs.bigIntegerBitLength);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(stripped);
            env->DeleteLocalRef(unscaled);
            return rv_none();
        }
        if (bits >= 64) {
            // Mantissa doesn't fit i64 — keep the wide value as a HANDLE.
            env->DeleteLocalRef(stripped);
            env->DeleteLocalRef(unscaled);
            return arena.adopt(rtValue);
        }
        jlong mantissa = env->CallLongMethod(unscaled, g_refs.bigIntegerLongValue);
        env->DeleteLocalRef(unscaled);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(stripped);
            return rv_none();
        }
        jint scale = env->CallIntMethod(stripped, g_refs.bigDecimalScale);
        env->DeleteLocalRef(stripped);
        if (env->ExceptionCheck()) return rv_none();
        return rv_dec_long(static_cast<int64_t>(mantissa), static_cast<int32_t>(scale));
    }

    // Enum: crack to the inline ENUM tag carrying (typeIdx, ordinal). The ordinal is the canonical
    // identity of an enum value (Rt_RR_EnumValue.equals compares ordinal+typeName; rt_ops.kt
    // R_CmpType_Enum compares rrAttr.value, the Int ordinal), and the typeIdx (RR_App.allEnums index)
    // lets to_jvm rebuild the EXACT enum type on rebox. Both are obtained from the JVM via
    // Llvm_SysBridge.enumCrack (packed as (typeIdx << 32) | (ordinal & 0xffffffff)) because naming
    // the enum's def_index requires the RR_App, reached through frameHandle. Re-enables the
    // comparison/equality/when inline paths in lower_ops.cpp / lower_expr.cpp (the C1 fix): the icmp
    // is now on the Int ordinal, not a jobject pointer.
    if (g_refs.rrEnumValue != nullptr && env->IsInstanceOf(rtValue, g_refs.rrEnumValue)) {
        initEnumBridgeOnce(env);
        if (g_enumCrackMethod == nullptr) {
            throwRuntime(env, ("from_jvm enum: " +
                               (g_enumBridgeError.empty() ? std::string("enumCrack unavailable")
                                                          : g_enumBridgeError))
                                  .c_str());
            return rv_none();
        }
        jlong packed = env->CallStaticLongMethod(g_sysBridgeClassForEnum, g_enumCrackMethod,
                                                 rtValue, frameHandle);
        if (env->ExceptionCheck()) return rv_none();
        int32_t typeIdx = static_cast<int32_t>(packed >> 32);
        int32_t ordinal = static_cast<int32_t>(packed & 0xffffffffLL);
        return rv_enum(static_cast<int64_t>(ordinal), typeIdx);
    }

    // Struct: crack to a native COMPOSITE carrying its RR_App.allStructs def-index (mirroring the
    // enum typeIdx) and the RECURSIVELY-from_jvm'd attribute values, in declared order. The
    // def-index is obtained from the JVM (Llvm_SysBridge.structDefIndex) because naming it needs the
    // RR_App, reached through frameHandle — exactly as the enum case names its type. The fields are
    // read via the Rt_StructValue interface (size()/get(i)), which is the canonical attribute order
    // (rt_value_struct.kt) the interpreter's (base as Rt_StructValue).get(i) also uses, so a native
    // StructAttr read of composite->fields[i] is bit-exact. to_jvm rebuilds the EXACT Rt_StructValue
    // from (def-index, fields) via Llvm_SysBridge.structValue, so from_jvm∘to_jvm is identity.
    if (g_refs.structValue != nullptr && env->IsInstanceOf(rtValue, g_refs.structValue)) {
        initEnumBridgeOnce(env);
        if (g_structDefIndexMethod == nullptr || g_structValueMethod == nullptr) {
            throwRuntime(env, ("from_jvm struct: " +
                               (g_enumBridgeError.empty() ? std::string("struct bridge unavailable")
                                                          : g_enumBridgeError))
                                  .c_str());
            return rv_none();
        }
        jint defIndex = env->CallStaticIntMethod(g_sysBridgeClassForEnum, g_structDefIndexMethod,
                                                 rtValue, frameHandle);
        if (env->ExceptionCheck()) return rv_none();
        jint n = env->CallIntMethod(rtValue, g_refs.structSize);
        if (env->ExceptionCheck()) return rv_none();
        if (n < 0) {
            throwRuntime(env, "from_jvm struct: negative size");
            return rv_none();
        }
        // Build the carrier FIRST so a recursive field that is itself a composite shares this arena.
        RellComposite *comp = arena.makeComposite(static_cast<int32_t>(defIndex));
        comp->fields.reserve(static_cast<size_t>(n));
        for (jint i = 0; i < n; ++i) {
            jobject field = env->CallObjectMethod(rtValue, g_refs.structGet, i);
            if (env->ExceptionCheck()) return rv_none();
            // Recurse: a field may be inline / enum / nested struct (COMPOSITE) / anything else
            // (HANDLE). A null field is a hard ABI fault (from_jvm raises) — a struct attribute is
            // always a real Rt_Value (Rt_NullValue for a null attr), never a Java null.
            RellValue fv = from_jvm(env, arena, field, frameHandle);
            if (field != nullptr) env->DeleteLocalRef(field);
            if (env->ExceptionCheck()) return rv_none();
            comp->fields.push_back(fv);
        }
        return rv_composite(comp, static_cast<int32_t>(defIndex));
    }

    // List: crack to a native LIST carrying a global-ref to the value's JVM Rt_ValueClass type
    // (value.getType()) and the RECURSIVELY-from_jvm'd elements, in iteration order. Lists are
    // structural (no allLists def-index), so unlike structs we carry the runtime type by global ref
    // rather than by index; to_jvm rebuilds the EXACT Rt_ListValue from (typeRef, elems) via
    // Llvm_SysBridge.listValue, so from_jvm∘to_jvm is identity. Elements are read via the JVM bridge
    // (Llvm_SysBridge.listSize/listGet, the Rt_ListValue.elements order) — bit-exact with the
    // interpreter's elements[i] iteration / subscript (rr_interpreter.kt). A SET is deliberately NOT
    // cracked here (only Rt_ListValue): a set stays a HANDLE (set ops soft-fail), so we IsInstanceOf
    // the concrete Rt_ListValue class, not the Rt_CollectionValue interface.
    if (g_refs.listValue != nullptr && env->IsInstanceOf(rtValue, g_refs.listValue)) {
        initEnumBridgeOnce(env);
        if (g_listSizeMethod == nullptr || g_listGetMethod == nullptr) {
            throwRuntime(env, ("from_jvm list: " +
                               (g_enumBridgeError.empty() ? std::string("list bridge unavailable")
                                                          : g_enumBridgeError))
                                  .c_str());
            return rv_none();
        }
        // typeRef: a tracked global ref to the list's runtime type. The arena owns it (released in the
        // dtor sweep) — exactly like a HANDLE's global ref.
        jobject jtype = env->CallObjectMethod(rtValue, g_refs.valueGetType);
        if (env->ExceptionCheck() || jtype == nullptr) {
            if (jtype != nullptr) env->DeleteLocalRef(jtype);
            if (!env->ExceptionCheck()) throwRuntime(env, "from_jvm list: null Rt_ValueClass type");
            return rv_none();
        }
        jobject typeGlobal = arena.track(jtype);
        env->DeleteLocalRef(jtype);
        if (env->ExceptionCheck()) return rv_none();

        jint n = env->CallStaticIntMethod(g_sysBridgeClassForEnum, g_listSizeMethod, rtValue);
        if (env->ExceptionCheck()) return rv_none();
        if (n < 0) {
            throwRuntime(env, "from_jvm list: negative size");
            return rv_none();
        }
        // Build the carrier FIRST so a recursive element that is itself a list/composite shares arena.
        RellList *lst = arena.makeList();
        lst->typeRef = typeGlobal;
        lst->elems.reserve(static_cast<size_t>(n));
        for (jint i = 0; i < n; ++i) {
            jobject elem = env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_listGetMethod,
                                                       rtValue, i);
            if (env->ExceptionCheck()) return rv_none();
            // Recurse: an element may be inline / enum / nested struct / nested list / HANDLE. A null
            // element is a hard ABI fault (a list element is always a real Rt_Value, never Java null).
            RellValue ev = from_jvm(env, arena, elem, frameHandle);
            if (elem != nullptr) env->DeleteLocalRef(elem);
            if (env->ExceptionCheck()) return rv_none();
            lst->elems.push_back(ev);
        }
        return rv_list(lst);
    }

    // byte_array: crack to a native BYTEARRAY carrier holding a COPY of the bytes. The bytes are read
    // from the real `[B` backing field (Rt_ByteArrayValue.value) via GetByteArrayRegion into a fresh
    // arena buffer — never a pin into the JVM ByteArray (which would not survive the call frame).
    // byte_array is a pure value type (content identity), so there is no typeRef to carry (unlike a
    // list) and no def-index (unlike a struct): the type is statically byte_array. to_jvm rebuilds the
    // EXACT Rt_ByteArrayValue via Llvm_SysBridge.byteArrayValue (-> Rt_ByteArrayValue.get, which
    // canonicalises empty -> EMPTY), so from_jvm∘to_jvm is content-identity (Rt_ByteArrayValue.equals
    // is contentEquals). A SET/MAP is NOT cracked here (kept a HANDLE) — only Rt_ByteArrayValue.
    if (g_refs.byteArrayValue != nullptr && env->IsInstanceOf(rtValue, g_refs.byteArrayValue)) {
        if (g_refs.byteArrayValueField == nullptr) {
            throwRuntime(env, "from_jvm byte_array: value field unavailable");
            return rv_none();
        }
        jobject jbytesObj = env->GetObjectField(rtValue, g_refs.byteArrayValueField);
        if (jbytesObj == nullptr) {
            throwRuntime(env, "Rt_ByteArrayValue.value is null");
            return rv_none();
        }
        auto jbytes = static_cast<jbyteArray>(jbytesObj);
        const jsize n = env->GetArrayLength(jbytes);
        if (n < 0) {
            env->DeleteLocalRef(jbytesObj);
            throwRuntime(env, "from_jvm byte_array: negative length");
            return rv_none();
        }
        // Allocate the arena buffer FIRST, then copy directly into it (no intermediate std::vector).
        RellByteArray *ba = arena.makeByteArray(/*src=*/nullptr, static_cast<int32_t>(n));
        if (n > 0) {
            // GetByteArrayRegion copies the JVM bytes into our (uninitialised-but-allocated) buffer.
            // jbyte == int8_t; reinterpret to fill the uint8_t buffer (the bit pattern is identical).
            env->GetByteArrayRegion(jbytes, 0, n, reinterpret_cast<jbyte *>(ba->data));
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(jbytesObj);
                return rv_none();
            }
        }
        env->DeleteLocalRef(jbytesObj);
        return rv_bytearray(ba);
    }

    // text: crack to a native TEXT carrier holding a COPY of the String's UTF-16 code units. The units
    // are read via GetStringLength + GetStringRegion into a fresh arena buffer — never a pin into the JVM
    // String (GetStringChars/GetStringCritical would pin and require a matching Release; a COPY is simpler
    // and the carrier outlives the call frame). text is a pure value type (content identity), so there is
    // no typeRef (unlike a list) and no def-index (unlike a struct): the type is statically text. to_jvm
    // rebuilds the EXACT Rt_TextValue via NewString + Llvm_SysBridge.textValue (-> Rt_TextValue.get, which
    // canonicalises empty -> EMPTY), so from_jvm∘to_jvm is content-identity (Rt_TextValue.equals is String
    // content equality). The code units are the EXACT physical layout of the String — surrogate pairs are
    // copied as two units, identical to the JVM, so subscript/compare/eq/size are all bit-exact.
    if (g_refs.textValue != nullptr && env->IsInstanceOf(rtValue, g_refs.textValue)) {
        if (g_refs.textGetter == nullptr) {
            throwRuntime(env, "from_jvm text: getValue unavailable");
            return rv_none();
        }
        jobject jstrObj = env->CallObjectMethod(rtValue, g_refs.textGetter);
        if (env->ExceptionCheck()) return rv_none();
        if (jstrObj == nullptr) {
            throwRuntime(env, "Rt_TextValue.value is null");
            return rv_none();
        }
        auto jstr = static_cast<jstring>(jstrObj);
        const jsize n = env->GetStringLength(jstr);  // CODE UNITS (UTF-16), == String.length.
        if (n < 0) {
            env->DeleteLocalRef(jstrObj);
            throwRuntime(env, "from_jvm text: negative length");
            return rv_none();
        }
        // Allocate the arena buffer FIRST, then copy the code units directly into it. GetStringRegion
        // copies n UTF-16 units (jchar) starting at 0 — no pin, no Release needed.
        RellText *t = arena.makeText(/*src=*/nullptr, static_cast<int32_t>(n));
        if (n > 0) {
            env->GetStringRegion(jstr, 0, n, reinterpret_cast<jchar *>(t->data));
            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(jstrObj);
                return rv_none();
            }
        }
        env->DeleteLocalRef(jstrObj);
        return rv_text(t);
    }

    // Everything else (json, gtv, set, map, tuple, entity,
    // virtual, range, function, ...) is the universal HANDLE escape. A bare tuple lands here: the
    // value-ABI gate forbids tuple params/returns, and a struct field that is a tuple becomes a
    // HANDLE here (to_jvm hands it straight back, round-trip-correct; a native TupleAttr read on it
    // soft-fails, routing to the interpreter).
    //
    // SET / MAP (deliberately NOT cracked, unlike Rt_ListValue above): a Rt_SetValue / Rt_MapValue is
    // adopted as a single tracked global ref and reboxed by to_jvm's HANDLE case as the SAME global
    // ref, so from_jvm∘to_jvm hands back the IDENTICAL JVM instance — iteration order, key
    // equality/hashing, and gtv encoding are preserved trivially (nothing is re-encoded). This is
    // CONSENSUS-CRITICAL and intentional: a native set/map would have to reproduce JVM
    // LinkedHashMap/LinkedHashSet insertion order AND Rt_Value.hashCode/equals bit-for-bit, which is
    // not provable, so we keep them HANDLEs (the value-ABI gate admits set/map signatures via
    // isSetMapHandleType precisely BECAUSE this pass-through is identity; set/map OPERATIONS still
    // soft-fail in the body lowering). We adopt the WHOLE value, not its elements — there is no
    // element recursion for set/map (the carrier is opaque), unlike the list/struct cracking above.
    return arena.adopt(rtValue);
}

// =====================================================================================
// 4b. native -> JVM (rebox).
//
// Returns a LOCAL ref owned by the caller's JNI frame, except HANDLE which returns the
// arena-owned global ref directly. Inline tags hit the cached factories; those factories can
// throw Rt_Exception (decimal/bigint overflow, negative rowid) — the caller MUST ExceptionCheck.
// =====================================================================================

jobject to_jvm(JNIEnv *env, RellArena &arena, const RellValue &value, jlong frameHandle) {
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

        case RellTag::BIGINT_LONG: {
            // Long-fitting big_integer: the @JvmStatic factory is Rt_BigIntegerValue.get(BigInteger)
            // (get(Long) is not @JvmStatic and merely forwards to it), so build a BigInteger via
            // BigInteger.valueOf first. In-envelope so no overflow, but the call can still throw in
            // principle — ExceptionCheck downstream.
            jobject bi = env->CallStaticObjectMethod(g_refs.bigInteger, g_refs.bigIntegerValueOf,
                                                     static_cast<jlong>(value.payload.i64));
            if (bi == nullptr || env->ExceptionCheck()) return nullptr;
            jobject big = env->CallStaticObjectMethod(g_refs.bigIntegerValue, g_refs.bigIntegerGetBi,
                                                      bi);
            env->DeleteLocalRef(bi);
            return big;
        }

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

        case RellTag::ENUM: {
            // Rebuild the EXACT Rt_RR_EnumValue from (typeIdx, ordinal). The JVM (Llvm_SysBridge
            // .enumValue, via frameHandle's RR_App) does resolveType(RR_Type.Enum(typeIdx)) then
            // allEnums[typeIdx].attrs[ordinal] -> Rt_RR_EnumValue — the same construction the
            // interpreter uses for an RR_ConstantValue.Enum (rr_interpreter.kt), so the result is
            // bit-exact and from_jvm∘to_jvm round-trips to the identical canonical instance. A bad
            // ordinal/typeIdx (impossible from a well-formed inline ENUM) throws JVM-side.
            initEnumBridgeOnce(env);
            if (g_enumValueMethod == nullptr) {
                throwRuntime(env, ("to_jvm enum: " +
                                   (g_enumBridgeError.empty() ? std::string("enumValue unavailable")
                                                              : g_enumBridgeError))
                                      .c_str());
                return nullptr;
            }
            return env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_enumValueMethod,
                                               static_cast<jint>(value.scale),
                                               static_cast<jint>(value.payload.i64), frameHandle);
        }

        case RellTag::HANDLE:
            // The arena already owns this global ref; hand it back directly. (Owned by the
            // arena, not the caller's frame — do NOT DeleteGlobalRef it here.)
            return value.payload.handle;

        case RellTag::COMPOSITE: {
            // A tuple COMPOSITE reaching to_jvm is a wiring fault: the value-ABI gate forbids a tuple
            // param/return, and an intermediate constructed tuple is only ever field-read. Hard-fault
            // rather than guess a tuple type that is not def-index-addressable.
            if (composite_is_tuple(value)) {
                throwRuntime(env, "to_jvm: tuple COMPOSITE cannot be reboxed (gate should forbid it)");
                return nullptr;
            }
            RellComposite *comp = value.payload.composite;
            if (comp == nullptr) {
                throwRuntime(env, "to_jvm: COMPOSITE with null carrier");
                return nullptr;
            }
            initEnumBridgeOnce(env);
            if (g_structValueMethod == nullptr) {
                throwRuntime(env, ("to_jvm struct: " +
                                   (g_enumBridgeError.empty() ? std::string("structValue unavailable")
                                                              : g_enumBridgeError))
                                      .c_str());
                return nullptr;
            }
            const jsize n = static_cast<jsize>(comp->fields.size());
            // Box each field RECURSIVELY into an Rt_Value[]. A nested struct field is itself a
            // COMPOSITE (recurses here); a HANDLE field hands back its arena-owned global ref. The
            // array's element type must be the real Rt_Value class (the JVM verifies it against the
            // `Array<Rt_Value>` structValue param), so we look it up fresh here.
            jclass rtValueCls = env->FindClass("net/postchain/rell/base/runtime/Rt_Value");
            if (rtValueCls == nullptr) {
                if (env->ExceptionCheck()) return nullptr;
                throwRuntime(env, "to_jvm struct: Rt_Value class not found");
                return nullptr;
            }
            jobjectArray fieldArr = env->NewObjectArray(n, rtValueCls, nullptr);
            env->DeleteLocalRef(rtValueCls);
            if (fieldArr == nullptr) return nullptr;
            for (jsize i = 0; i < n; ++i) {
                jobject fv = to_jvm(env, arena, comp->fields[static_cast<size_t>(i)], frameHandle);
                if (env->ExceptionCheck()) return nullptr;
                env->SetObjectArrayElement(fieldArr, i, fv);
                if (env->ExceptionCheck()) return nullptr;
            }
            jobject result = env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_structValueMethod,
                                                         static_cast<jint>(value.scale), fieldArr,
                                                         frameHandle);
            env->DeleteLocalRef(fieldArr);
            return result;  // caller ExceptionChecks (the factory can throw on a malformed value).
        }

        case RellTag::LIST: {
            RellList *lst = value.payload.list;
            if (lst == nullptr) {
                throwRuntime(env, "to_jvm: LIST with null carrier");
                return nullptr;
            }
            if (lst->typeRef == nullptr) {
                throwRuntime(env, "to_jvm: LIST with null typeRef");
                return nullptr;
            }
            initEnumBridgeOnce(env);
            if (g_listValueMethod == nullptr) {
                throwRuntime(env, ("to_jvm list: " +
                                   (g_enumBridgeError.empty() ? std::string("listValue unavailable")
                                                              : g_enumBridgeError))
                                      .c_str());
                return nullptr;
            }
            const jsize n = static_cast<jsize>(lst->elems.size());
            // Box each element RECURSIVELY into an Rt_Value[] in iteration order (a nested list/struct
            // element recurses here; a HANDLE element hands back its arena-owned global ref).
            jclass rtValueCls = env->FindClass("net/postchain/rell/base/runtime/Rt_Value");
            if (rtValueCls == nullptr) {
                if (env->ExceptionCheck()) return nullptr;
                throwRuntime(env, "to_jvm list: Rt_Value class not found");
                return nullptr;
            }
            jobjectArray elemArr = env->NewObjectArray(n, rtValueCls, nullptr);
            env->DeleteLocalRef(rtValueCls);
            if (elemArr == nullptr) return nullptr;
            for (jsize i = 0; i < n; ++i) {
                jobject ev = to_jvm(env, arena, lst->elems[static_cast<size_t>(i)], frameHandle);
                if (env->ExceptionCheck()) return nullptr;
                env->SetObjectArrayElement(elemArr, i, ev);
                if (env->ExceptionCheck()) return nullptr;
            }
            // Rt_ListValue(typeRef, elems) — bit-exact with the interpreter's list construction (same
            // runtime type + element order). The typeRef is the arena-owned global; pass it directly.
            jobject result = env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_listValueMethod,
                                                         lst->typeRef, elemArr, frameHandle);
            env->DeleteLocalRef(elemArr);
            return result;  // caller ExceptionChecks.
        }

        case RellTag::BYTEARRAY: {
            RellByteArray *ba = value.payload.bytearray;
            if (ba == nullptr) {
                throwRuntime(env, "to_jvm: BYTEARRAY with null carrier");
                return nullptr;
            }
            initEnumBridgeOnce(env);
            if (g_byteArrayValueMethod == nullptr) {
                throwRuntime(env, ("to_jvm byte_array: " +
                                   (g_enumBridgeError.empty()
                                        ? std::string("byteArrayValue unavailable")
                                        : g_enumBridgeError))
                                      .c_str());
                return nullptr;
            }
            // Rebuild the EXACT Rt_ByteArrayValue: NewByteArray(len) + SetByteArrayRegion(bytes) ->
            // Llvm_SysBridge.byteArrayValue -> Rt_ByteArrayValue.get (canonicalises empty -> EMPTY). The
            // copied bytes are content-identical to the carrier's buffer, so from_jvm∘to_jvm is identity
            // (Rt_ByteArrayValue.equals == contentEquals).
            const jsize n = static_cast<jsize>(ba->len < 0 ? 0 : ba->len);
            jbyteArray jbytes = env->NewByteArray(n);
            if (jbytes == nullptr) return nullptr;  // OOM — local frame full / heap exhausted.
            if (n > 0 && ba->data != nullptr) {
                env->SetByteArrayRegion(jbytes, 0, n, reinterpret_cast<const jbyte *>(ba->data));
                if (env->ExceptionCheck()) {
                    env->DeleteLocalRef(jbytes);
                    return nullptr;
                }
            }
            jobject result =
                env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_byteArrayValueMethod, jbytes);
            env->DeleteLocalRef(jbytes);
            return result;  // caller ExceptionChecks.
        }

        case RellTag::TEXT: {
            RellText *t = value.payload.text;
            if (t == nullptr) {
                throwRuntime(env, "to_jvm: TEXT with null carrier");
                return nullptr;
            }
            initEnumBridgeOnce(env);
            if (g_textValueMethod == nullptr) {
                throwRuntime(env, ("to_jvm text: " +
                                   (g_enumBridgeError.empty() ? std::string("textValue unavailable")
                                                              : g_enumBridgeError))
                                      .c_str());
                return nullptr;
            }
            // Rebuild the EXACT Rt_TextValue: NewString(units, len) -> Llvm_SysBridge.textValue ->
            // Rt_TextValue.get (canonicalises empty -> EMPTY). The copied code units are content-identical
            // to the carrier's buffer, so from_jvm∘to_jvm is identity (Rt_TextValue.equals == String
            // content equality). NewString builds a String from UTF-16 code units directly — no UTF-8
            // round-trip, so surrogate pairs (and even lone surrogates from a subscript) survive exactly.
            const jsize n = static_cast<jsize>(t->len < 0 ? 0 : t->len);
            jstring jstr = env->NewString(reinterpret_cast<const jchar *>(t->data), n);
            if (jstr == nullptr) return nullptr;  // OOM — local frame full / heap exhausted.
            jobject result =
                env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_textValueMethod, jstr);
            env->DeleteLocalRef(jstr);
            return result;  // caller ExceptionChecks.
        }

        case RellTag::NONE:
        default:
            // NONE is the poison/abort sentinel and must never be reboxed.
            throwRuntime(env, "to_jvm: NONE/poison RellValue cannot be reboxed");
            return nullptr;
    }
}

// =====================================================================================
// 8. Runtime-value LLVM representation + the EmitContext pack/unpack shape ops.
//
// rellValueLlvmType() is the { i8 tag, i32 scale, i64 payload } aggregate that mirrors the
// RellValue C++ POD (static_assert'd 16 bytes in rell_runtime.h §1). It is the SSA type every
// lowering file threads as a runtime value; the JIT'd value-ABI function returns one by value and
// takes its args as a contiguous array of them, so the IR shape and the C++ struct must agree on
// the wire. The struct is identified ("RellValue") so repeated lookups in a Module reuse it.
//
// The pack/unpack members are pure IR shape ops (no envelope validation — the CALLER must have
// proven the value stays inline before computing in i64 space; see rell_runtime.h §8).
// =====================================================================================

llvm::StructType *rellValueLlvmType(llvm::LLVMContext &ctx) {
    // Reuse the named struct if this context already defined it (idempotent across the many
    // lowering call sites within one Module/Context).
    if (llvm::StructType *existing = llvm::StructType::getTypeByName(ctx, "RellValue")) {
        return existing;
    }
    return llvm::StructType::create(
        ctx, {llvm::Type::getInt8Ty(ctx), llvm::Type::getInt32Ty(ctx), llvm::Type::getInt64Ty(ctx)},
        "RellValue", /*isPacked=*/false);
}

llvm::Value *EmitContext::packInline(RellTag tag, llvm::Value *scaleI32, llvm::Value *payloadI64) {
    llvm::IRBuilder<> &b = builder_;
    llvm::StructType *valTy = rellValueLlvmType(ctx_);
    llvm::Value *agg = llvm::UndefValue::get(valTy);
    agg = b.CreateInsertValue(agg, b.getInt8(static_cast<uint8_t>(tag)), {0}, "rv.tag");
    agg = b.CreateInsertValue(agg, scaleI32, {1}, "rv.scale");
    agg = b.CreateInsertValue(agg, payloadI64, {2}, "rv.payload");
    return agg;
}

llvm::Value *EmitContext::unpackPayloadI64(llvm::Value *runtimeValue) {
    return builder_.CreateExtractValue(runtimeValue, {2}, "rv.payload");
}

llvm::Value *EmitContext::unpackTag(llvm::Value *runtimeValue) {
    return builder_.CreateExtractValue(runtimeValue, {0}, "rv.tag");
}

llvm::Value *EmitContext::unpackScale(llvm::Value *runtimeValue) {
    return builder_.CreateExtractValue(runtimeValue, {1}, "rv.scale");
}

// =====================================================================================
// Runtime envelope-escape emit helpers (rell_runtime.h §9). These emit the runtime branch into the
// extern "C" rell_jit_escape() back-call that callValueFunction observes to re-run the call on the
// interpreter. Resolved by the JIT's process-symbol generator (same mechanism as rell_int_overflow).
// =====================================================================================

void emitJitEscape(EmitContext &ec) {
    auto &b = ec.builder();
    auto *voidTy = llvm::Type::getVoidTy(ec.ctx());
    auto *fnTy = llvm::FunctionType::get(voidTy, {}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_jit_escape", fnTy);
    b.CreateCall(callee, {});
}

void emitJitEscapeIf(EmitContext &ec, llvm::Value *cond) {
    auto &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();
    auto *escBB = llvm::BasicBlock::Create(ctx, "jit_escape", fn);
    auto *contBB = llvm::BasicBlock::Create(ctx, "jit_escape_cont", fn);
    b.CreateCondBr(cond, escBB, contBB);
    b.SetInsertPoint(escBB);
    emitJitEscape(ec);
    b.CreateBr(contBB);
    b.SetInsertPoint(contBB);
}

void emitEscapeIfNotTag(EmitContext &ec, llvm::Value *runtimeValue, RellTag expected) {
    auto &b = ec.builder();
    llvm::Value *tag = ec.unpackTag(runtimeValue);  // i8
    llvm::Value *wrongTag =
        b.CreateICmpNE(tag, b.getInt8(static_cast<uint8_t>(expected)), "wrong_tag");
    emitJitEscapeIf(ec, wrongTag);
}

// =====================================================================================
// 4d. Composite construction / field access runtime helpers (rell_runtime.h §4d).
//
// sret-style (out-pointer) so they sidestep the by-value aggregate-return ABI hazard (the reason
// rell_sysfn_call soft-fails). Resolved by the JIT process-symbol generator (extern "C"). The IR
// (lower_expr.cpp / lower_call.cpp) emits the field RellValues into a stack RellValue[] then calls
// rell_make_composite; a StructAttr/TupleAttr read emits rell_composite_get.
// =====================================================================================

extern "C" void rell_make_composite(RellValue *out, RellCallCtx *ctx, int32_t scaleDisc,
                                    const RellValue *fields, int32_t n) {
    if (out == nullptr) return;  // nothing we can do; a null out is a hard wiring fault.
    if (ctx == nullptr || ctx->arena == nullptr || n < 0 || (n > 0 && fields == nullptr)) {
        // Hard ABI fault: cannot allocate without an arena. Poison the slot; callValueFunction turns
        // a NONE result into "abort" (no JVM exception is pending, so it surfaces as a wiring error).
        *out = rv_none();
        return;
    }
    // The arena owns the carrier; the field RellValues are COPIED in. Any HANDLE / nested-COMPOSITE
    // among them was produced earlier under this SAME ctx->arena (from_jvm or a prior
    // rell_make_composite), so the copy aliases an allocation the arena already owns — the single
    // arena sweep frees the whole tree exactly once. Declared-order is preserved (the IR stores
    // fields[i] in the TupleExpr/StructExpr declared order, matching Rt construction).
    RellComposite *comp = ctx->arena->makeComposite(scaleDisc);
    comp->fields.assign(fields, fields + n);
    *out = rv_composite(comp, scaleDisc);
}

extern "C" void rell_composite_get(RellValue *out, const RellValue *composite, int32_t i) {
    if (out == nullptr) return;
    if (composite == nullptr || composite->tag != RellTag::COMPOSITE ||
        composite->payload.composite == nullptr) {
        // A non-COMPOSITE receiver is a wiring fault (the lowering only emits this on a statically-
        // composite member access). Poison so the call aborts rather than reading garbage.
        *out = rv_none();
        return;
    }
    const RellComposite *comp = composite->payload.composite;
    if (i < 0 || static_cast<size_t>(i) >= comp->fields.size()) {
        *out = rv_none();
        return;
    }
    // Plain 16-byte copy. The field's HANDLE / nested-COMPOSITE payload (if any) keeps being owned
    // by the arena that built `comp`; the copy only aliases it (matching how a RellValue copy never
    // duplicates ownership — rell_runtime.h §1). Bit-exact with (base as Rt_StructValue).get(i) /
    // Rt_TupleValue.elements[i].
    *out = comp->fields[static_cast<size_t>(i)];
}

// =====================================================================================
// 4h. Native structural equality for composite / collection values (rell_runtime.h §4h).
//
// Pure-native recursive walk of the native RellValue reps, reproducing Rt_Value.equals EXACTLY for
// tuple/struct (COMPOSITE), list (LIST), and their scalar/nested leaves. Any opaque HANDLE element
// (set/map/gtv/json/virtual/range/wide-decimal/wide-bigint/bare-tuple) is NOT provably bit-exact in
// C++, so the helper calls rell_jit_escape() and returns a meaningless 0 — callValueFunction then
// re-runs the WHOLE call on the interpreter (correct, counted as a jitMiss). NEVER a wrong result.
// =====================================================================================

namespace {

// Signal "this comparison is not provably bit-exact in C++" and return the (discarded) sentinel. The
// caller's callValueFunction observes g_jitEscape and re-runs the whole call on the interpreter.
int32_t escapeEquals() {
    rell_jit_escape();
    return 0;
}

}  // namespace

extern "C" int32_t rell_value_equals(const RellValue *a, const RellValue *b) {
    if (a == nullptr || b == nullptr) {
        // A null operand is a wiring fault — escape so the interpreter (which never sees a C++ null)
        // re-runs the call rather than returning a guessed result.
        return escapeEquals();
    }

    // An opaque HANDLE on either side (set/map/gtv/json/virtual/range/wide numeric/bare tuple) cannot
    // be compared by content in C++: escape to the interpreter's Rt_Value.equals. Checked first so a
    // HANDLE vs non-HANDLE pair (e.g. a struct field that is a wide decimal in one operand) escapes
    // rather than mis-reporting a tag mismatch as "not equal".
    if (a->tag == RellTag::HANDLE || b->tag == RellTag::HANDLE) {
        return escapeEquals();
    }

    // A tag mismatch between two statically same-typed operands is impossible EXCEPT via the wide-fit
    // HANDLE escape (handled above). Any residual mismatch is unexpected; escape rather than guess.
    if (a->tag != b->tag) {
        return escapeEquals();
    }

    switch (a->tag) {
        case RellTag::NULL_:
        case RellTag::UNIT:
            // Singletons: equal iff same tag (already established). Bit-exact with the JVM singletons'
            // reference-identity equals.
            return 1;

        case RellTag::BOOLEAN:
        case RellTag::INTEGER:
        case RellTag::ROWID:
        case RellTag::BIGINT_LONG:
            // i64 payload equality == value equality (BIGINT_LONG is the exact inline i64; a wide
            // operand would be a HANDLE, escaped above).
            return a->payload.i64 == b->payload.i64 ? 1 : 0;

        case RellTag::ENUM:
            // Enum value equality: ordinal (payload.i64) AND enum-type index (scale). Rt_RR_EnumValue
            // .equals keys on ordinal + typeName; both operands are statically the same enum type, so
            // the typeIdx is identical — checking it too is defensive and never wrong.
            return (a->payload.i64 == b->payload.i64 && a->scale == b->scale) ? 1 : 0;

        case RellTag::DEC_LONG:
            // Stripped canonical normal form (M3 invariant in from_jvm): equal decimal values share
            // BOTH mantissa and scale (1.0 and 1.00 both crack to (1, 0)), so payload+scale equality
            // coincides with the interpreter's scale-insensitive BigDecimal value equality. No POW10
            // alignment is needed for EQUALITY (that is only the ORDERING concern of the cmp path).
            return (a->payload.i64 == b->payload.i64 && a->scale == b->scale) ? 1 : 0;

        case RellTag::BYTEARRAY: {
            // Content equality (Rt_ByteArrayValue.equals == contentEquals): same length AND same bytes.
            const RellByteArray *x = a->payload.bytearray;
            const RellByteArray *y = b->payload.bytearray;
            if (x == nullptr || y == nullptr) return escapeEquals();
            if (x->len != y->len) return 0;
            if (x->len == 0) return 1;  // both empty.
            return std::memcmp(x->data, y->data, static_cast<size_t>(x->len)) == 0 ? 1 : 0;
        }

        case RellTag::TEXT: {
            // Code-unit content equality (Rt_TextValue.equals == String content equality): same length
            // AND same UTF-16 code units. memcmp over len * sizeof(uint16_t) bytes.
            const RellText *x = a->payload.text;
            const RellText *y = b->payload.text;
            if (x == nullptr || y == nullptr) return escapeEquals();
            if (x->len != y->len) return 0;
            if (x->len == 0) return 1;  // both empty.
            return std::memcmp(x->data, y->data,
                               static_cast<size_t>(x->len) * sizeof(uint16_t)) == 0
                       ? 1
                       : 0;
        }

        case RellTag::COMPOSITE: {
            // tuple/struct: the discriminant pins the value-class kind (tuple disc -1 vs struct
            // def-index), reproducing the interpreter's `other is Rt_TupleValue` / `is Rt_StructValue`
            // type check. Then field count + positional recursive equals — bit-exact with
            // Rt_TupleValue.equals (elements equality) and Rt_StructValue.structEquals (size + get(i)).
            const RellComposite *x = a->payload.composite;
            const RellComposite *y = b->payload.composite;
            if (x == nullptr || y == nullptr) return escapeEquals();
            if (x->scaleDisc != y->scaleDisc) return 0;
            if (x->fields.size() != y->fields.size()) return 0;
            for (size_t i = 0; i < x->fields.size(); ++i) {
                int32_t r = rell_value_equals(&x->fields[i], &y->fields[i]);
                if (r == 0) {
                    // A nested escape (a HANDLE element deep in the tree) has already set the escape
                    // flag; returning 0 short-circuits the walk and the JVM re-runs the whole call.
                    return 0;
                }
            }
            return 1;
        }

        case RellTag::LIST: {
            // list: same length, positional recursive element equals (Rt_ListValue.equals == elements
            // list equality). The list TYPE is NOT compared (Rt_ListValue.equals checks only elements),
            // matching the interpreter exactly.
            const RellList *x = a->payload.list;
            const RellList *y = b->payload.list;
            if (x == nullptr || y == nullptr) return escapeEquals();
            if (x->elems.size() != y->elems.size()) return 0;
            for (size_t i = 0; i < x->elems.size(); ++i) {
                int32_t r = rell_value_equals(&x->elems[i], &y->elems[i]);
                if (r == 0) return 0;  // not-equal OR a nested escape (flag already set).
            }
            return 1;
        }

        case RellTag::NONE:
        default:
            // NONE is the poison sentinel; any other unexpected tag cannot be compared — escape.
            return escapeEquals();
    }
}

// =====================================================================================
// List JVM back-calls (rell_runtime.h §4e support). These wrap the Llvm_SysBridge list bridge —
// resolved lazily via initEnumBridgeOnce (the value layer's bridge cache).
// =====================================================================================

jobject rell_list_type(JNIEnv *env, ListTypeId listTypeId, jlong frameHandle) {
    initEnumBridgeOnce(env);
    if (g_listTypeMethod == nullptr) {
        throwRuntime(env, ("rell_list_type: " +
                           (g_enumBridgeError.empty() ? std::string("listType unavailable")
                                                      : g_enumBridgeError))
                              .c_str());
        return nullptr;
    }
    return env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_listTypeMethod,
                                       static_cast<jint>(listTypeId), frameHandle);
}

// rell_list_index_error (the per-thread list-error channel recorder) lives in jni_bridge.cpp
// alongside the integer-error / escape channels; rell_list_get below calls it on OOB.

// =====================================================================================
// 4e. List construction / element access / size runtime helpers (rell_runtime.h §4e).
//
// sret-style (out-pointer), resolved by the JIT process-symbol generator (extern "C"). rell_make_list
// needs the JNIEnv (a back-call to resolve the list type) so it reads ctx->env; rell_list_get and
// rell_list_size are pure-native (no JNI) — an OOB subscript records into the per-thread list-error
// channel (no JVM call from the native frame).
// =====================================================================================

extern "C" void rell_make_list(RellValue *out, RellCallCtx *ctx, int32_t listTypeId,
                               const RellValue *elems, int32_t n) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || ctx->env == nullptr || n < 0 ||
        (n > 0 && elems == nullptr)) {
        *out = rv_none();
        return;
    }
    JNIEnv *env = ctx->env;
    // Resolve the list TYPE via the JVM and track its global ref in the arena.
    jobject jtype = rell_list_type(env, listTypeId, ctx->frameHandle);
    if (jtype == nullptr || env->ExceptionCheck()) {
        *out = rv_none();  // exception left pending (or hard fault) -> trampoline aborts.
        return;
    }
    jobject typeGlobal = ctx->arena->track(jtype);
    env->DeleteLocalRef(jtype);
    if (typeGlobal == nullptr || env->ExceptionCheck()) {
        *out = rv_none();
        return;
    }
    // The arena owns the carrier; element RellValues are COPIED in (iteration order). Any HANDLE /
    // nested-COMPOSITE / nested-LIST among them was produced earlier under this SAME ctx->arena, so
    // the copy aliases an allocation the arena already owns — a single sweep frees the whole tree.
    RellList *lst = ctx->arena->makeList();
    lst->typeRef = typeGlobal;
    lst->elems.assign(elems, elems + n);
    *out = rv_list(lst);
}

extern "C" void rell_list_get(RellValue *out, RellCallCtx *ctx, const RellValue *list, int64_t i) {
    (void)ctx;  // ctx is threaded for symmetry / future use; the OOB error uses the per-thread channel.
    if (out == nullptr) return;
    if (list == nullptr || list->tag != RellTag::LIST || list->payload.list == nullptr) {
        *out = rv_none();
        return;
    }
    const RellList *lst = list->payload.list;
    const int64_t size = static_cast<int64_t>(lst->elems.size());
    // Bounds check EXACTLY as the interpreter (Rt_ListValue.checkIndex0): valid iff i in [0, size).
    // A negative index is OUT OF BOUNDS (Rell has no Python-style negative indexing — rr_interpreter
    // .kt ListSubscript), so it raises, not wraps. Record into the per-thread list-error channel
    // (jni_bridge.cpp); callValueFunction polls it and raises the exact Rt_Exception. Poison the slot
    // so the trampoline aborts the native frame rather than committing a junk element.
    if (i < 0 || i >= size) {
        rell_list_index_error(static_cast<int32_t>(size), i);
        *out = rv_none();
        return;
    }
    // Plain 16-byte copy; the element's HANDLE / nested payload keeps being owned by the arena that
    // built `lst`. Bit-exact with the interpreter's elements[idx.toInt()].
    *out = lst->elems[static_cast<size_t>(i)];
}

extern "C" void rell_list_size(RellValue *out, const RellValue *list) {
    if (out == nullptr) return;
    if (list == nullptr || list->tag != RellTag::LIST || list->payload.list == nullptr) {
        *out = rv_none();
        return;
    }
    *out = rv_integer(static_cast<int64_t>(list->payload.list->elems.size()));
}

// =====================================================================================
// 4f. byte_array construction / access / ops runtime helpers (rell_runtime.h §4f).
//
// sret-style (out-pointer) for RellValue results; the comparison helper returns a scalar i32. Pure
// native (no JNI) except rell_make_bytearray (allocates in ctx->arena). All bit-exact with the
// interpreter (rt_value_bytearray.kt / rt_ops.kt / rr_interpreter.kt).
// =====================================================================================

extern "C" void rell_make_bytearray(RellValue *out, RellCallCtx *ctx, const uint8_t *bytes,
                                    int32_t n) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || n < 0 || (n > 0 && bytes == nullptr)) {
        *out = rv_none();
        return;
    }
    // The arena owns the carrier + a COPY of the literal bytes (single sweep frees it). n == 0 is the
    // empty byte_array (data == nullptr); to_jvm canonicalises it to Rt_ByteArrayValue.EMPTY.
    RellByteArray *ba = ctx->arena->makeByteArray(bytes, n);
    *out = rv_bytearray(ba);
}

extern "C" void rell_bytearray_size(RellValue *out, const RellValue *ba) {
    if (out == nullptr) return;
    if (ba == nullptr || ba->tag != RellTag::BYTEARRAY || ba->payload.bytearray == nullptr) {
        *out = rv_none();
        return;
    }
    // Bit-exact with the interpreter's byte_array .size() (value.size, an Int widened to Long).
    *out = rv_integer(static_cast<int64_t>(ba->payload.bytearray->len));
}

extern "C" void rell_bytearray_get(RellValue *out, const RellValue *ba, int64_t i) {
    if (out == nullptr) return;
    if (ba == nullptr || ba->tag != RellTag::BYTEARRAY || ba->payload.bytearray == nullptr) {
        *out = rv_none();
        return;
    }
    const RellByteArray *b = ba->payload.bytearray;
    const int64_t size = static_cast<int64_t>(b->len);
    // Bounds check EXACTLY as rr_interpreter.kt ByteArraySubscript: valid iff i in [0, size). A
    // negative index is OUT OF BOUNDS (no Python-style wrap). Record into the per-thread byte-array-
    // error channel (jni_bridge.cpp); callValueFunction polls it and raises the exact Rt_Exception.
    if (i < 0 || i >= size) {
        rell_bytearray_index_error(static_cast<int32_t>(size), i);
        *out = rv_none();
        return;
    }
    // UNSIGNED byte 0..255: data[i] & 0xFF — bit-exact with Rt_IntValue.get(ba[idx].toLong() and 0xFF).
    *out = rv_integer(static_cast<int64_t>(b->data[static_cast<size_t>(i)]) & 0xFF);
}

extern "C" void rell_bytearray_concat(RellValue *out, RellCallCtx *ctx, const RellValue *a,
                                      const RellValue *b) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || a == nullptr || b == nullptr ||
        a->tag != RellTag::BYTEARRAY || b->tag != RellTag::BYTEARRAY ||
        a->payload.bytearray == nullptr || b->payload.bytearray == nullptr) {
        *out = rv_none();
        return;
    }
    const RellByteArray *ba = a->payload.bytearray;
    const RellByteArray *bb = b->payload.bytearray;
    // No size envelope on `+` (the size constraint is a struct/entity-attribute concern). Bit-exact
    // with rt_ops.kt R_BinaryOp_Concat_ByteArray (Rt_ByteArrayValue.get(a.value + b.value)).
    const int64_t total = static_cast<int64_t>(ba->len) + static_cast<int64_t>(bb->len);
    if (total < 0 || total > 0x7fffffffLL) {
        // A combined length exceeding Int range cannot be a valid Rell byte_array (the JVM ByteArray
        // is Int-indexed); poison so the trampoline aborts rather than truncating silently.
        *out = rv_none();
        return;
    }
    RellByteArray *res = ctx->arena->makeByteArray(/*src=*/nullptr, static_cast<int32_t>(total));
    if (ba->len > 0) std::memcpy(res->data, ba->data, static_cast<size_t>(ba->len));
    if (bb->len > 0) {
        std::memcpy(res->data + ba->len, bb->data, static_cast<size_t>(bb->len));
    }
    *out = rv_bytearray(res);
}

extern "C" void rell_bytearray_eq(RellValue *out, const RellValue *a, const RellValue *b) {
    if (out == nullptr) return;
    if (a == nullptr || b == nullptr || a->tag != RellTag::BYTEARRAY ||
        b->tag != RellTag::BYTEARRAY || a->payload.bytearray == nullptr ||
        b->payload.bytearray == nullptr) {
        *out = rv_none();
        return;
    }
    const RellByteArray *ba = a->payload.bytearray;
    const RellByteArray *bb = b->payload.bytearray;
    // Content equality (Rt_ByteArrayValue.equals == contentEquals): equal length AND equal bytes.
    bool eq = ba->len == bb->len &&
              (ba->len == 0 || std::memcmp(ba->data, bb->data, static_cast<size_t>(ba->len)) == 0);
    *out = rv_boolean(eq);
}

extern "C" int32_t rell_bytearray_cmp(const RellValue *a, const RellValue *b) {
    // A malformed operand is a wiring fault: return 0 (the trampoline never reaches this with a
    // non-BYTEARRAY operand — the static cmp_type gate proves both are byte_array).
    if (a == nullptr || b == nullptr || a->tag != RellTag::BYTEARRAY ||
        b->tag != RellTag::BYTEARRAY || a->payload.bytearray == nullptr ||
        b->payload.bytearray == nullptr) {
        return 0;
    }
    const RellByteArray *ba = a->payload.bytearray;
    const RellByteArray *bb = b->payload.bytearray;
    // UNSIGNED lexicographic, then length — bit-exact with rt_ops.kt compareByteArrays
    // (Integer.compareUnsigned per byte over min(len), then size.compareTo). memcmp compares as
    // unsigned char, identical to the per-byte unsigned compare; the result is normalised to {-1,0,1}.
    const int32_t minLen = ba->len < bb->len ? ba->len : bb->len;
    if (minLen > 0) {
        int c = std::memcmp(ba->data, bb->data, static_cast<size_t>(minLen));
        if (c < 0) return -1;
        if (c > 0) return 1;
    }
    if (ba->len < bb->len) return -1;
    if (ba->len > bb->len) return 1;
    return 0;
}

// =====================================================================================
// Native crypto hashes (sha256 / keccak256) — sret-style, bit-exact with lib_crypto.kt.
//
// These wire the self-contained rell::crypto hashes (rell_crypto.cpp) directly into the JIT,
// replacing the rell_sysfn_call back-call for the two hashing functions. Both read the single
// BYTEARRAY operand's arena buffer (the input is cracked to a native BYTEARRAY by from_jvm — see
// the byte_array §4f helpers above) and write a fresh 32-byte BYTEARRAY result into ctx->arena
// (canonicalised to Rt_ByteArrayValue on rebox by to_jvm). NO JNI, NO OpenSSL, NO libsecp256k1.
//
// DETERMINISM (consensus-critical): bit-exact with the interpreter:
//   * sha256  == lib_crypto.kt Sha256  -> java.security.MessageDigest("SHA-256"). rell::crypto::sha256
//     is the FIPS 180-4 reference (NIST "abc" / empty / 56-byte vectors asserted in crypto_self_test).
//   * keccak256 == lib_crypto.kt keccak256 -> BouncyCastle Keccak.Digest256 (ORIGINAL Keccak,
//     padding suffix 0x01 — NOT SHA3-256's 0x06). rell::crypto::keccak256 uses suffix 0x01 and matches
//     the Ethereum empty-input vector c5d2460186f7233c...85a470 (asserted in crypto_self_test).
// The signing family (get_signature/verify_signature/eth_*/privkey_to_pubkey/...) is NOT wired here:
// its byte-identity vs the JVM is unproven (REVIEW H2) and it depends on libsecp256k1, so those stay
// on the rell_sysfn_call back-call (lower_call.cpp falls through for every non-hash crypto name).
namespace {
// Shared body: hash the single BYTEARRAY operand into a fresh 32-byte arena BYTEARRAY. `hash` is the
// 32-byte digest the rell::crypto entry point produced over the operand's bytes.
inline void writeHash32(RellValue *out, RellCallCtx *ctx, const std::array<uint8_t, 32> &hash) {
    RellByteArray *res = ctx->arena->makeByteArray(hash.data(), 32);
    *out = rv_bytearray(res);
}
}  // namespace

extern "C" void rell_crypto_sha256(RellValue *out, RellCallCtx *ctx, const RellValue *ba) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || ba == nullptr ||
        ba->tag != RellTag::BYTEARRAY || ba->payload.bytearray == nullptr) {
        *out = rv_none();
        return;
    }
    const RellByteArray *b = ba->payload.bytearray;
    // data may be nullptr iff len == 0 (the empty byte_array); rell::crypto::sha256 tolerates a null
    // pointer with len 0 (its loop bodies never dereference when len == 0 — the empty-input vector is
    // asserted in crypto_self_test).
    auto hash = rell::crypto::sha256(b->data, static_cast<size_t>(b->len));
    writeHash32(out, ctx, hash);
}

extern "C" void rell_crypto_keccak256(RellValue *out, RellCallCtx *ctx, const RellValue *ba) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || ba == nullptr ||
        ba->tag != RellTag::BYTEARRAY || ba->payload.bytearray == nullptr) {
        *out = rv_none();
        return;
    }
    const RellByteArray *b = ba->payload.bytearray;
    auto hash = rell::crypto::keccak256(b->data, static_cast<size_t>(b->len));
    writeHash32(out, ctx, hash);
}

// rell_bytearray_index_error (the per-thread byte-array-error channel recorder) lives in
// jni_bridge.cpp alongside the integer-error / list-error / escape channels; rell_bytearray_get above
// calls it on OOB.

// =====================================================================================
// 4g. text construction / access / ops runtime helpers (rell_runtime.h §4g).
//
// sret-style (out-pointer) for RellValue results; the comparison helper returns a scalar i32. Pure
// native (no JNI) except rell_make_text / rell_text_get / rell_text_concat (which allocate in
// ctx->arena). All bit-exact with the interpreter (rt_value_text.kt / rt_ops.kt / lib_type_text.kt /
// rr_interpreter.kt). The buffer holds UTF-16 code UNITS — String semantics, NOT code points/bytes.
// =====================================================================================

extern "C" void rell_make_text(RellValue *out, RellCallCtx *ctx, const uint16_t *units, int32_t n) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || n < 0 || (n > 0 && units == nullptr)) {
        *out = rv_none();
        return;
    }
    // The arena owns the carrier + a COPY of the literal code units (single sweep frees it). n == 0 is
    // the empty text (data == nullptr); to_jvm canonicalises it to Rt_TextValue.EMPTY.
    RellText *t = ctx->arena->makeText(units, n);
    *out = rv_text(t);
}

// Decimal CONSTANT materialiser. A decimal literal cannot be normalised in C++ without re-deriving
// Lib_DecimalMath.scale / BigDecimal.stripTrailingZeros (the DETERMINISM trap that made decimal
// constants soft-fail). Instead we route through the JVM exactly once: build the literal's plain
// string (`units` are its ASCII code units, the tokenizer's range-validated canonical form), call
// Llvm_SysBridge.decimalValue -> Rt_DecimalValue.get(BigDecimal(s)) (the interpreter's
// RR_ConstantValue.Decimal construction), then crack the result back through from_jvm. from_jvm runs
// the SAME Tf_LongScaleDecimal transcription every reboxed decimal field already uses, so the inline
// DEC_LONG is the EXACT stripped normal form (1.0 and 1.00 both -> (1, 0)) — or a wide HANDLE if the
// mantissa doesn't fit i64. Bit-exact with the interpreter, no C++ re-normalisation. The arena owns
// the resulting carrier (HANDLE) the same way a from_jvm'd struct field would.
extern "C" void rell_make_decimal(RellValue *out, RellCallCtx *ctx, const uint16_t *units, int32_t n) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || ctx->env == nullptr || n < 0 ||
        (n > 0 && units == nullptr)) {
        *out = rv_none();
        return;
    }
    JNIEnv *env = ctx->env;
    initEnumBridgeOnce(env);
    if (g_decimalValueMethod == nullptr) {
        throwRuntime(env, ("rell_make_decimal: " +
                           (g_enumBridgeError.empty() ? std::string("decimalValue unavailable")
                                                      : g_enumBridgeError))
                              .c_str());
        *out = rv_none();
        return;
    }
    // Build the literal string (UTF-16 code units == ASCII digits/sign/dot/exponent) and rebuild the
    // canonical Rt_DecimalValue via the bridge.
    jstring jstr = env->NewString(reinterpret_cast<const jchar *>(units), static_cast<jsize>(n));
    if (jstr == nullptr || env->ExceptionCheck()) {
        *out = rv_none();
        return;
    }
    jobject jdec = env->CallStaticObjectMethod(g_sysBridgeClassForEnum, g_decimalValueMethod, jstr);
    env->DeleteLocalRef(jstr);
    if (jdec == nullptr || env->ExceptionCheck()) {
        *out = rv_none();  // exception left pending -> trampoline aborts.
        return;
    }
    // Crack to the canonical inline DEC_LONG (or a wide HANDLE), reusing from_jvm's exact normal-form
    // transcription. The arena owns any HANDLE carrier produced (single sweep frees it).
    RellValue v = from_jvm(env, *ctx->arena, jdec, ctx->frameHandle);
    env->DeleteLocalRef(jdec);
    if (env->ExceptionCheck()) {
        *out = rv_none();
        return;
    }
    *out = v;
}

extern "C" void rell_text_size(RellValue *out, const RellValue *t) {
    if (out == nullptr) return;
    if (t == nullptr || t->tag != RellTag::TEXT || t->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    // Bit-exact with lib_type_text.kt `size` body `self.value.length.toLong()` (String.length == UTF-16
    // CODE UNITS, NOT code points). The code-unit count is exactly `len`.
    *out = rv_integer(static_cast<int64_t>(t->payload.text->len));
}

extern "C" void rell_text_get(RellValue *out, RellCallCtx *ctx, const RellValue *t, int64_t i) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || t == nullptr || t->tag != RellTag::TEXT ||
        t->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    const RellText *tv = t->payload.text;
    const int64_t size = static_cast<int64_t>(tv->len);
    // Bounds check EXACTLY as rr_interpreter.kt TextSubscript: valid iff i in [0, size). A negative index
    // is OUT OF BOUNDS (no Python-style wrap). Record into the per-thread text-error channel
    // (jni_bridge.cpp); callValueFunction polls it and raises the exact Rt_Exception.
    if (i < 0 || i >= size) {
        rell_text_index_error(static_cast<int32_t>(size), i);
        *out = rv_none();
        return;
    }
    // A 1-CODE-UNIT text: Rt_TextValue.get(text[idx].toString()) — a single Java char widened to a
    // length-1 String (a lone surrogate if idx is inside a surrogate pair, exactly as the interpreter
    // produces). Allocate a fresh 1-unit arena buffer and copy the one code unit.
    RellText *res = ctx->arena->makeText(/*src=*/nullptr, 1);
    res->data[0] = tv->data[static_cast<size_t>(i)];
    *out = rv_text(res);
}

extern "C" void rell_text_concat(RellValue *out, RellCallCtx *ctx, const RellValue *a,
                                 const RellValue *b) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || a == nullptr || b == nullptr ||
        a->tag != RellTag::TEXT || b->tag != RellTag::TEXT || a->payload.text == nullptr ||
        b->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    const RellText *ta = a->payload.text;
    const RellText *tb = b->payload.text;
    // Bit-exact with rt_ops.kt R_BinaryOp_Concat_Text (Rt_TextValue.get(a.value + b.value)); String
    // concatenation is a pure code-unit splice. A combined length exceeding Int range cannot be a valid
    // Rell text (the JVM String is Int-indexed); poison so the trampoline aborts rather than truncating.
    const int64_t total = static_cast<int64_t>(ta->len) + static_cast<int64_t>(tb->len);
    if (total < 0 || total > 0x7fffffffLL) {
        *out = rv_none();
        return;
    }
    RellText *res = ctx->arena->makeText(/*src=*/nullptr, static_cast<int32_t>(total));
    if (ta->len > 0) {
        std::memcpy(res->data, ta->data, static_cast<size_t>(ta->len) * sizeof(uint16_t));
    }
    if (tb->len > 0) {
        std::memcpy(res->data + ta->len, tb->data, static_cast<size_t>(tb->len) * sizeof(uint16_t));
    }
    *out = rv_text(res);
}

extern "C" void rell_text_eq(RellValue *out, const RellValue *a, const RellValue *b) {
    if (out == nullptr) return;
    if (a == nullptr || b == nullptr || a->tag != RellTag::TEXT || b->tag != RellTag::TEXT ||
        a->payload.text == nullptr || b->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    const RellText *ta = a->payload.text;
    const RellText *tb = b->payload.text;
    // Content equality (Rt_TextValue.equals == String content equality): equal length AND equal code
    // units. memcmp over the UTF-16 buffers compares code units bit-for-bit (endianness is irrelevant:
    // both buffers are native-endian uint16_t produced by the same GetStringRegion/decode path).
    bool eq = ta->len == tb->len &&
              (ta->len == 0 ||
               std::memcmp(ta->data, tb->data, static_cast<size_t>(ta->len) * sizeof(uint16_t)) == 0);
    *out = rv_boolean(eq);
}

extern "C" int32_t rell_text_cmp(const RellValue *a, const RellValue *b) {
    // A malformed operand is a wiring fault: return 0 (the trampoline never reaches this with a non-TEXT
    // operand — the static cmp_type gate proves both are text).
    if (a == nullptr || b == nullptr || a->tag != RellTag::TEXT || b->tag != RellTag::TEXT ||
        a->payload.text == nullptr || b->payload.text == nullptr) {
        return 0;
    }
    const RellText *ta = a->payload.text;
    const RellText *tb = b->payload.text;
    // java.lang.String.compareTo: compare the first differing CODE UNIT as an UNSIGNED 16-bit value
    // (Java `char` is unsigned 0..65535), then the shorter string is LESS (length tiebreak). We CANNOT
    // use memcmp here: memcmp compares bytes, which would split each 16-bit unit into two bytes and (on
    // little-endian) compare the low byte first — diverging from a per-CODE-UNIT compare. So compare unit
    // by unit as uint16_t. CRITICAL: the comparison is UNSIGNED, so a unit >= 0x8000 (incl. surrogates
    // 0xD800..0xDFFF) orders ABOVE a smaller unit — exactly String.compareTo.
    const int32_t minLen = ta->len < tb->len ? ta->len : tb->len;
    for (int32_t k = 0; k < minLen; ++k) {
        const uint16_t ca = ta->data[k];
        const uint16_t cb = tb->data[k];
        if (ca != cb) return ca < cb ? -1 : 1;
    }
    if (ta->len < tb->len) return -1;
    if (ta->len > tb->len) return 1;
    return 0;
}

// =====================================================================================
// Member text stdlib ops (lib_type_text.kt) wired NATIVE — operating directly on the UTF-16 code-unit
// buffer, bit-exact with java.lang.String (the ground truth every text stdlib body delegates to). Each
// helper is sret-style (out-pointer) for a RellValue result, or returns a scalar for boolean/integer.
//
// CODE-UNIT vs CODE-POINT: every op below is UTF-16 code-UNIT based (String.startsWith/endsWith/
// indexOf/charAt/substring/replace all index code units, NOT code points), so a plain uint16_t scan
// over the carrier buffer reproduces them exactly — surrogate pairs are two units, identical to the JVM.
//
// DEFERRED (kept on the rell_sysfn_call back-call, NOT here): upper_case / lower_case (String.uppercase/
// lowercase are LOCALE-sensitive — they call the no-arg overloads that use Locale.getDefault(); a
// native ASCII fold would diverge on e.g. Turkish 'i' or the German ß, a consensus split), format
// (java.util.Formatter), split (allocates list<text>), trim / matches / like / regex_replace /
// match_groups / regex (java.util.regex semantics), to_bytes / from_bytes (UTF-8 transcode),
// compare_to / reversed / last_index_of (not requested). They route through the JVM unchanged.

// First index (in CODE UNITS) at or after `from` where `needle` occurs in `hay`, or -1. Bit-exact with
// java.lang.String.indexOf(String, int): an EMPTY needle matches at min(from, len) (so indexOf("") == 0
// for from <= 0; == len for from >= len); `from` is clamped to >= 0 by the caller's contract. Pure
// code-unit comparison.
static int32_t textIndexOf(const uint16_t *hay, int32_t hayLen, const uint16_t *needle,
                           int32_t needleLen, int32_t from) {
    if (from < 0) from = 0;
    // String.indexOf: an empty needle is found at position min(from, len) (clamped), matching the JVM.
    if (needleLen == 0) return from > hayLen ? hayLen : from;
    if (from > hayLen - needleLen) return -1;  // needle cannot fit at or after `from`.
    for (int32_t i = from; i <= hayLen - needleLen; ++i) {
        int32_t k = 0;
        while (k < needleLen && hay[i + k] == needle[k]) ++k;
        if (k == needleLen) return i;
    }
    return -1;
}

// .starts_with(prefix) -> boolean. Bit-exact with String.startsWith (code-unit prefix; empty prefix is
// always a prefix). NOT escaped — total, no error path.
extern "C" void rell_text_starts_with(RellValue *out, const RellValue *self, const RellValue *prefix) {
    if (out == nullptr) return;
    if (self == nullptr || prefix == nullptr || self->tag != RellTag::TEXT ||
        prefix->tag != RellTag::TEXT) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const RellText *p = prefix->payload.text;
    if (s == nullptr || p == nullptr) { *out = rv_none(); return; }
    bool ok = p->len <= s->len &&
              (p->len == 0 ||
               std::memcmp(s->data, p->data, static_cast<size_t>(p->len) * sizeof(uint16_t)) == 0);
    *out = rv_boolean(ok);
}

// .ends_with(suffix) -> boolean. Bit-exact with String.endsWith (code-unit suffix; empty suffix always
// matches). NOT escaped.
extern "C" void rell_text_ends_with(RellValue *out, const RellValue *self, const RellValue *suffix) {
    if (out == nullptr) return;
    if (self == nullptr || suffix == nullptr || self->tag != RellTag::TEXT ||
        suffix->tag != RellTag::TEXT) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const RellText *p = suffix->payload.text;
    if (s == nullptr || p == nullptr) { *out = rv_none(); return; }
    bool ok = p->len <= s->len &&
              (p->len == 0 ||
               std::memcmp(s->data + (s->len - p->len), p->data,
                           static_cast<size_t>(p->len) * sizeof(uint16_t)) == 0);
    *out = rv_boolean(ok);
}

// .contains(sub) -> boolean. Bit-exact with String.contains (== indexOf(sub) >= 0; empty sub -> true).
// NOT escaped.
extern "C" void rell_text_contains(RellValue *out, const RellValue *self, const RellValue *sub) {
    if (out == nullptr) return;
    if (self == nullptr || sub == nullptr || self->tag != RellTag::TEXT ||
        sub->tag != RellTag::TEXT) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const RellText *p = sub->payload.text;
    if (s == nullptr || p == nullptr) { *out = rv_none(); return; }
    const int32_t idx = textIndexOf(s->data, s->len, p->data, p->len, 0);
    *out = rv_boolean(idx >= 0);
}

// .index_of(sub) -> integer. Bit-exact with String.indexOf(sub) (first code-unit index or -1; empty sub
// -> 0). NOT escaped — total.
extern "C" void rell_text_index_of(RellValue *out, const RellValue *self, const RellValue *sub) {
    if (out == nullptr) return;
    if (self == nullptr || sub == nullptr || self->tag != RellTag::TEXT ||
        sub->tag != RellTag::TEXT) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const RellText *p = sub->payload.text;
    if (s == nullptr || p == nullptr) { *out = rv_none(); return; }
    *out = rv_integer(static_cast<int64_t>(textIndexOf(s->data, s->len, p->data, p->len, 0)));
}

// .index_of(sub, start) -> integer. The 2-arg overload PRE-CHECKS start in [0, len) and throws the EXACT
// custom Rt_Exception otherwise (lib_type_text.kt), then returns String.indexOf(sub, start). On the bad-
// start path it records the exact code/message into the general text-op error channel and poisons *out.
extern "C" void rell_text_index_of_from(RellValue *out, const RellValue *self, const RellValue *sub,
                                        int64_t start) {
    if (out == nullptr) return;
    if (self == nullptr || sub == nullptr || self->tag != RellTag::TEXT ||
        sub->tag != RellTag::TEXT) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const RellText *p = sub->payload.text;
    if (s == nullptr || p == nullptr) { *out = rv_none(); return; }
    const int32_t len = s->len;
    // lib_type_text.kt index_of/2: `if (start < 0 || start >= s.length) throw ... index ...`.
    if (start < 0 || start >= static_cast<int64_t>(len)) {
        const std::string code = "fn:text.index_of:index:" + std::to_string(len) + ":" +
                                 std::to_string(start);
        const std::string msg = "Index out of bounds: " + std::to_string(start) + " (length " +
                                std::to_string(len) + ")";
        rell_text_op_error(code.c_str(), msg.c_str());
        *out = rv_none();
        return;
    }
    *out = rv_integer(static_cast<int64_t>(
        textIndexOf(s->data, len, p->data, p->len, static_cast<int32_t>(start))));
}

// .char_at(i) -> integer (the 16-bit CODE-UNIT value, s[i].code). Bit-exact with lib_type_text.kt
// char_at: bounds i in [0, len), else the EXACT custom Rt_Exception; the result is the UNSIGNED 16-bit
// code unit widened to Long (Kotlin Char.code is 0..65535).
extern "C" void rell_text_char_at(RellValue *out, const RellValue *self, int64_t i) {
    if (out == nullptr) return;
    if (self == nullptr || self->tag != RellTag::TEXT) { *out = rv_none(); return; }
    const RellText *s = self->payload.text;
    if (s == nullptr) { *out = rv_none(); return; }
    const int32_t len = s->len;
    if (i < 0 || i >= static_cast<int64_t>(len)) {
        const std::string code = "fn:text.char_at:index:" + std::to_string(len) + ":" +
                                 std::to_string(i);
        const std::string msg = "Index out of bounds: " + std::to_string(i) + " (length " +
                                std::to_string(len) + ")";
        rell_text_op_error(code.c_str(), msg.c_str());
        *out = rv_none();
        return;
    }
    // c.code: the code unit as an UNSIGNED 16-bit int widened to Long. Mask to be explicit.
    *out = rv_integer(static_cast<int64_t>(s->data[static_cast<size_t>(i)]) & 0xFFFFLL);
}

// Shared substring core for .sub(start) / .sub(start, end). Bit-exact with lib_type_text.kt calcSub:
// validate `start in [0, len] && end in [start, len]` (a CLAMP would diverge — sub is error-on-OOB, NOT
// clamped), else throw the EXACT "fn:text.sub:range:<len>:<start>:<end>" Rt_Exception. Allocates the
// result in ctx->arena.
static void textSubImpl(RellValue *out, RellCallCtx *ctx, const RellText *s, int64_t start,
                        int64_t end) {
    const int64_t len = static_cast<int64_t>(s->len);
    // calcSub: `if (start < 0 || start > len || end < start || end > len) throw range`.
    if (start < 0 || start > len || end < start || end > len) {
        const std::string code = "fn:text.sub:range:" + std::to_string(len) + ":" +
                                 std::to_string(start) + ":" + std::to_string(end);
        const std::string msg = "Invalid range: start = " + std::to_string(start) + ", end = " +
                                std::to_string(end) + " (length " + std::to_string(len) + ")";
        rell_text_op_error(code.c_str(), msg.c_str());
        *out = rv_none();
        return;
    }
    const int32_t n = static_cast<int32_t>(end - start);
    RellText *res = ctx->arena->makeText(/*src=*/nullptr, n);
    if (n > 0) {
        std::memcpy(res->data, s->data + start, static_cast<size_t>(n) * sizeof(uint16_t));
    }
    *out = rv_text(res);
}

// .sub(start) -> text. calcSub(s, start, s.length): substring from start to end-of-string.
extern "C" void rell_text_sub1(RellValue *out, RellCallCtx *ctx, const RellValue *self,
                               int64_t start) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || self == nullptr || self->tag != RellTag::TEXT ||
        self->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    textSubImpl(out, ctx, s, start, static_cast<int64_t>(s->len));
}

// .sub(start, end) -> text. calcSub(s, start, end).
extern "C" void rell_text_sub2(RellValue *out, RellCallCtx *ctx, const RellValue *self, int64_t start,
                               int64_t end) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || self == nullptr || self->tag != RellTag::TEXT ||
        self->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    textSubImpl(out, ctx, self->payload.text, start, end);
}

// .repeat(n) -> text. Bit-exact with lib_type_text.kt repeat + Lib_Type_List.rtCheckRepeatArgs:
//   n < 0                          -> "fn:text.repeat:n_negative:<n>" / "Negative count: <n>"
//   n > Int.MAX                    -> "fn:text.repeat:n_out_of_range:<n>" / "Count out of range: <n>"
//   len*n > Int.MAX (total)        -> "fn:text.repeat:too_big:<total>" / "Resulting size is too large: <len> * <n> = <total>"
// else the result is the buffer repeated n times (empty/len-0 stays empty; n==0 -> empty). Allocates in
// ctx->arena.
extern "C" void rell_text_repeat(RellValue *out, RellCallCtx *ctx, const RellValue *self, int64_t n) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || self == nullptr || self->tag != RellTag::TEXT ||
        self->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const int64_t len = static_cast<int64_t>(s->len);
    if (n < 0) {
        const std::string code = "fn:text.repeat:n_negative:" + std::to_string(n);
        const std::string msg = "Negative count: " + std::to_string(n);
        rell_text_op_error(code.c_str(), msg.c_str());
        *out = rv_none();
        return;
    }
    if (n > 2147483647LL) {  // Integer.MAX_VALUE
        const std::string code = "fn:text.repeat:n_out_of_range:" + std::to_string(n);
        const std::string msg = "Count out of range: " + std::to_string(n);
        rell_text_op_error(code.c_str(), msg.c_str());
        *out = rv_none();
        return;
    }
    const int64_t total = len * n;  // len <= Int.MAX, n <= Int.MAX, so the product fits i64 (no overflow).
    if (total > 2147483647LL) {
        const std::string code = "fn:text.repeat:too_big:" + std::to_string(total);
        const std::string msg = "Resulting size is too large: " + std::to_string(len) + " * " +
                                std::to_string(n) + " = " + std::to_string(total);
        rell_text_op_error(code.c_str(), msg.c_str());
        *out = rv_none();
        return;
    }
    RellText *res = ctx->arena->makeText(/*src=*/nullptr, static_cast<int32_t>(total));
    for (int64_t k = 0; k < n; ++k) {
        if (len > 0) {
            std::memcpy(res->data + k * len, s->data, static_cast<size_t>(len) * sizeof(uint16_t));
        }
    }
    *out = rv_text(res);
}

// .replace(old_value, new_value) -> text. Bit-exact with String.replace(CharSequence, CharSequence): a
// LITERAL (not regex) replacement of ALL non-overlapping occurrences of `old_value`, scanned left-to-
// right. NON-EMPTY old_value only — an EMPTY old_value has subtle JDK splice semantics ("ab".replace("",
// "-") == "-a-b-") that we do NOT reproduce here; instead we ESCAPE to the interpreter (rell_jit_escape)
// so the JVM's String.replace runs it bit-exactly. Allocates the result in ctx->arena.
extern "C" void rell_text_replace(RellValue *out, RellCallCtx *ctx, const RellValue *self,
                                  const RellValue *oldV, const RellValue *newV) {
    if (out == nullptr) return;
    if (ctx == nullptr || ctx->arena == nullptr || self == nullptr || oldV == nullptr ||
        newV == nullptr || self->tag != RellTag::TEXT || oldV->tag != RellTag::TEXT ||
        newV->tag != RellTag::TEXT || self->payload.text == nullptr || oldV->payload.text == nullptr ||
        newV->payload.text == nullptr) {
        *out = rv_none();
        return;
    }
    const RellText *s = self->payload.text;
    const RellText *o = oldV->payload.text;
    const RellText *r = newV->payload.text;
    if (o->len == 0) {
        // Empty target: subtle JVM splice semantics. Re-run on the interpreter for a provable match.
        rell_jit_escape();
        *out = rv_none();
        return;
    }
    // Two-pass: count occurrences to size the result, then splice. Non-overlapping, left-to-right (after
    // a match the scan resumes at match-end), exactly as String.replace.
    int64_t count = 0;
    {
        int32_t i = 0;
        while (i <= s->len - o->len) {
            if (std::memcmp(s->data + i, o->data, static_cast<size_t>(o->len) * sizeof(uint16_t)) ==
                0) {
                ++count;
                i += o->len;
            } else {
                ++i;
            }
        }
    }
    const int64_t total =
        static_cast<int64_t>(s->len) + count * (static_cast<int64_t>(r->len) - o->len);
    if (total < 0 || total > 2147483647LL) {  // a result wider than Int cannot be a valid Rell text.
        *out = rv_none();
        return;
    }
    RellText *res = ctx->arena->makeText(/*src=*/nullptr, static_cast<int32_t>(total));
    int32_t srcI = 0;
    int32_t dstI = 0;
    while (srcI < s->len) {
        if (srcI <= s->len - o->len &&
            std::memcmp(s->data + srcI, o->data, static_cast<size_t>(o->len) * sizeof(uint16_t)) ==
                0) {
            if (r->len > 0) {
                std::memcpy(res->data + dstI, r->data,
                            static_cast<size_t>(r->len) * sizeof(uint16_t));
            }
            dstI += r->len;
            srcI += o->len;
        } else {
            res->data[dstI++] = s->data[srcI++];
        }
    }
    *out = rv_text(res);
}

// rell_text_index_error (the per-thread text-error channel recorder) lives in jni_bridge.cpp alongside
// the integer-error / list-error / byte-array-error / escape channels; rell_text_get above calls it on OOB.
// rell_text_op_error (the general text-op error channel, carrying an explicit code+message for the
// char_at / sub / index_of/2 / repeat custom Rt_Exceptions) and rell_jit_escape also live in jni_bridge.cpp.

// Sections 5 (universal stdlib caller), 6 (SQL back-call) and the interning tables live in
// their canonical homes: stdlib_bridge.cpp (rell_sysfn_call, SysFnTable) and sql_bridge.cpp
// (rell_db_eval_expr, rell_db_exec_stmt, DbNodeTable). value.cpp owns only the value/arena/
// marshalling layer (g_refs, to_jvm, from_jvm, ensureRellRefsInitialized).

}  // namespace rell::llvm_rt
