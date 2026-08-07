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
    ENUM        = 9,  // inline enum: payload.i64 = Int ordinal (Rt_RR_EnumValue.rrAttr.value),
                      //   `scale`/aux = the enum-type index (RR_App.allEnums index / EnumType.def_index).
                      //   from_jvm cracks (typeIdx, ordinal) via Llvm_SysBridge.enumCrack; to_jvm
                      //   reboxes via Llvm_SysBridge.enumValue. The interpreter compares enums by the
                      //   Int ordinal (rt_ops.kt R_CmpType_Enum: rrAttr.value.compareTo), and EQ by
                      //   ordinal+typeName, so for statically same-typed operands an ordinal icmp is
                      //   bit-exact. The typeIdx is carried so to_jvm rebuilds the EXACT enum type.
    LIST        = 11, // arena-owned native list: payload.list aliases a RellList* (NOT a JNI ref).
                      //   The RellList carries { jobject typeRef, std::vector<RellValue> elems }: a
                      //   global-ref to the list's JVM Rt_ValueClass type (so to_jvm rebuilds the EXACT
                      //   Rt_ListValue without naming a structural list type by index) plus the element
                      //   RellValues in iteration order. from_jvm reads Rt_ListValue.elements (size +
                      //   get(i)) and recursively from_jvm's each element; the typeRef is a global ref
                      //   to value.getType(). to_jvm rebuilds Rt_ListValue(typeRef, elems) via
                      //   Llvm_SysBridge.listValue, recursively to_jvm'ing each element — so
                      //   from_jvm∘to_jvm is identity. A ListLiteralExpr the body constructs obtains its
                      //   typeRef from a back-call (Llvm_SysBridge.listType) keyed by an interned
                      //   list-type id (ListTypeTable, walk-order mirrored JVM-side). The RellList and
                      //   its element RellValues are owned by the SAME RellArena (single sweep frees the
                      //   tree); the typeRef global ref is tracked in the arena too. Element reads
                      //   (subscript) index list->elems[i] natively; SUBSCRIPT bounds-checks against the
                      //   interpreter's exact list:index error. for-over-list iterates list->elems in
                      //   order. List EQUALITY / stdlib member-ops (.add/.size()/.contains/+/in/...) and
                      //   set/map literals soft-fail or route via the by-value rell_sysfn_call back-call.
    BYTEARRAY   = 12, // arena-owned native byte_array: payload.bytearray aliases a RellByteArray*
                      //   (NOT a JNI ref). The RellByteArray carries { uint8_t* data, int32 len } — a
                      //   plain heap buffer the RellArena owns and frees (delete[] data; delete carrier)
                      //   in its dtor, exactly like a COMPOSITE/LIST carrier. byte_array is a pure value
                      //   (content identity), so unlike a LIST there is NO typeRef global to track: the
                      //   type is statically byte_array. from_jvm reads Rt_ByteArrayValue's bytes via
                      //   JNI GetByteArrayRegion into a fresh arena buffer (a COPY — the JVM ByteArray is
                      //   not pinned across the call). to_jvm rebuilds the EXACT Rt_ByteArrayValue via
                      //   NewByteArray + SetByteArrayRegion + Llvm_SysBridge.byteArrayValue (which calls
                      //   Rt_ByteArrayValue.get, canonicalising empty -> the EMPTY singleton), so
                      //   from_jvm∘to_jvm is identity (content equals). Native ops are bit-exact with the
                      //   interpreter: subscript b[i] -> Rt_IntValue.get(data[i] & 0xFF) (UNSIGNED 0..255,
                      //   OOB raises the exact expr_bytearray_subscript_index Rt_Exception via the
                      //   byte-array-error channel); .size() -> integer (len); EQ/NE -> content equality
                      //   (memcmp of equal-length buffers); LT/GT/LE/GE -> UNSIGNED lexicographic compare
                      //   then length (rt_ops.kt compareByteArrays: Integer.compareUnsigned per byte);
                      //   concat -> a fresh arena buffer (a-bytes ++ b-bytes). Hex/base64/sha256/from_hex
                      //   etc. are NOT native — they route through the JVM (rell_sysfn_call back-call,
                      //   which soft-fails the function today: the by-value return ABI is not yet sret).
    TEXT        = 13, // arena-owned native text: payload.text aliases a RellText* (NOT a JNI ref). The
                      //   RellText carries { uint16_t* data, int32 len } — a heap buffer of UTF-16 CODE
                      //   UNITS (jchar), the EXACT physical layout of the JVM java.lang.String backing
                      //   Rt_TextValue (rt_value_text.kt: Rt_TextValue wraps a String). The RellArena owns
                      //   and frees it (delete[] data; delete carrier) in its dtor, exactly like a
                      //   BYTEARRAY carrier. text is a pure VALUE type (content identity), so — like a
                      //   byte_array and unlike a LIST — there is NO typeRef global to track: the type is
                      //   statically text. from_jvm reads the String's code units via GetStringLength +
                      //   GetStringRegion into a fresh arena buffer (a COPY — the JVM String chars are not
                      //   pinned across the call). to_jvm rebuilds the EXACT Rt_TextValue via NewString +
                      //   Llvm_SysBridge.textValue (which calls Rt_TextValue.get, canonicalising empty ->
                      //   the EMPTY singleton), so from_jvm∘to_jvm is identity (Rt_TextValue.equals is
                      //   String content equality). Native ops are bit-exact with the interpreter
                      //   (rt_value_text.kt / rt_ops.kt / rr_interpreter.kt): subscript t[i] ->
                      //   Rt_TextValue.get(String(data[i])) (a 1-CODE-UNIT text; OOB raises the exact
                      //   expr_text_subscript_index Rt_Exception via the text-error channel); .size() ->
                      //   integer (len == String.length == UTF-16 CODE UNITS, NOT code points — confirmed
                      //   lib_type_text.kt `size` body = `self.value.length.toLong()`); EQ/NE -> code-unit
                      //   content equality; LT/GT/LE/GE -> String.compareTo order (the first differing
                      //   code unit compared as an UNSIGNED 16-bit value — Java `char` is unsigned — then
                      //   the shorter string is LESS; rt_ops.kt R_CmpType_Text = value.compareTo(value));
                      //   concat -> a fresh arena buffer (a-units ++ b-units; rt_ops.kt R_BinaryOp_Concat_
                      //   Text = Rt_TextValue.get(a.value + b.value)). Non-trivial stdlib ops (sub/upper_
                      //   case/format/index_of/char_at/to_bytes/...) are NOT native — they route through
                      //   the JVM (rell_sysfn_call back-call, which soft-fails the function today: the
                      //   by-value return ABI is not yet sret). NOTE on surrogates: a non-BMP code point is
                      //   stored as a surrogate PAIR of two code units in the buffer, IDENTICAL to the JVM
                      //   String; subscript of a surrogate index yields a lone-surrogate 1-unit text, and
                      //   compare/eq/size operate on code units — all bit-exact with the interpreter, which
                      //   uses the same UTF-16 String operations.
    COMPOSITE   = 10, // arena-owned native composite (tuple OR struct): payload.composite aliases a
                      //   RellComposite* (it is NOT a JNI ref). `scale` mirrors the discriminant:
                      //   kCompositeTupleDisc (-1) == tuple, >= 0 == struct-def-index (RR_App
                      //   .allStructs index, mirroring ENUM's typeIdx so to_jvm rebuilds the EXACT
                      //   struct type via Llvm_SysBridge.structValue). The RellComposite (and its
                      //   recursively-marshalled field RellValues) is owned by the RellArena that
                      //   built it and freed in a single sweep at call exit — exactly like a HANDLE's
                      //   global ref. Field reads (StructAttr/TupleAttr) index composite->fields[i]
                      //   natively. SCOPE: structs are def-index-addressable so they round-trip fully
                      //   (from_jvm <-> to_jvm). A bare TUPLE crossing JNI cannot be reconstructed by
                      //   index (tuple types are structural, not in allStructs), so the value-ABI type
                      //   gate (jni_bridge.cpp) admits a tuple ONLY as an INTERMEDIATE — constructed
                      //   and field-read inside a body — never as a param/return; a tuple param/return
                      //   soft-fails the whole function. composite equality / to_gtv / any composite
                      //   sysfn soft-fails (the by-value rell_sysfn_call return ABI is not yet sret —
                      //   see lower_call.cpp), so those route to the interpreter, bit-exactly.
};

// 16-byte POD, trivially copyable. Passed by value through the LLVM calling convention as
// the struct type produced by rellValueLlvmType() == { i8 tag, i32 scale, i64 payload }.
//
// No constructors/destructors: the lifetime of an embedded HANDLE jobject is owned by the
// RellArena that created it, NEVER by this struct. Copying a RellValue does not copy the
// global ref — copies alias the same arena-owned handle.
struct RellValue;

// Arena-allocated native composite carrier (tuple OR struct). The COMPOSITE-tagged RellValue's
// payload aliases one of these. NOT a JNI ref: a plain C++ heap object the RellArena owns and
// `delete`s in its dtor (alongside the global-ref sweep). `scaleDisc` mirrors the COMPOSITE tag's
// `scale`: -1 == tuple, >= 0 == the RR_App.allStructs def-index (mirroring ENUM's typeIdx) so
// to_jvm rebuilds the EXACT struct type. `fields` are the field values in DECLARED order (matching
// Rt_TupleValue.elements / Rt_StructValue.get(i) order) and may each be inline / nested COMPOSITE /
// HANDLE — round-trip recursive marshalling. The `fields` vector owns only its RellValue cells;
// any HANDLE/nested-COMPOSITE inside them is owned by the SAME arena (see RellArena), so a single
// arena sweep frees the whole tree.
struct RellComposite {
    int32_t scaleDisc;            // -1 tuple; >=0 struct-def-index.
    std::vector<RellValue> fields;  // declared-order field values.
};

// Arena-allocated native list carrier. The LIST-tagged RellValue's payload aliases one of these.
// NOT a JNI ref: a plain C++ heap object the RellArena owns and `delete`s in its dtor. `typeRef` is
// an arena-tracked JNI global ref to the list's JVM `Rt_ValueClass` type (so to_jvm rebuilds the
// EXACT Rt_ListValue without naming a structural list type by index — lists are structural like
// tuples, so there is no allLists def-index to mirror the struct path). `elems` are the element
// values in ITERATION order (matching Rt_ListValue.elements) and may each be inline / nested
// COMPOSITE / nested LIST / HANDLE — round-trip recursive marshalling. The `elems` vector owns only
// its RellValue cells; any HANDLE/nested-COMPOSITE/nested-LIST inside them is owned by the SAME arena
// (see RellArena), so a single arena sweep frees the whole tree. `typeRef` may be nullptr only on a
// list still under construction (rell_make_list sets it before publishing).
struct RellList {
    jobject typeRef;              // arena-tracked global ref to the list's Rt_ValueClass type.
    std::vector<RellValue> elems;  // iteration-order element values.
};

// Arena-allocated native byte_array carrier. The BYTEARRAY-tagged RellValue's payload aliases one of
// these. NOT a JNI ref: a plain C++ heap object the RellArena owns and frees (`delete[] data` then
// `delete` the carrier) in its dtor. `data` is a heap byte buffer of `len` bytes (a COPY of the
// source bytes — never a pin into a JVM ByteArray, which would not survive the call frame). `data`
// may be nullptr iff `len == 0` (the empty byte_array, mirroring Rt_ByteArrayValue.EMPTY). The
// buffer is owned solely by THIS arena (a flat ownership entry like a HANDLE's global ref), so a
// single arena sweep frees it exactly once — no leak, no use-after-free, no double-free.
struct RellByteArray {
    uint8_t *data;  // owned heap buffer (delete[] in arena dtor); nullptr iff len == 0.
    int32_t len;    // byte count; always >= 0.
};

// Arena-allocated native text carrier. The TEXT-tagged RellValue's payload aliases one of these. NOT a
// JNI ref: a plain C++ heap object the RellArena owns and frees (`delete[] data` then `delete` the
// carrier) in its dtor. `data` is a heap buffer of `len` UTF-16 CODE UNITS (jchar == uint16_t) — the
// EXACT physical layout of the java.lang.String backing Rt_TextValue (a COPY of the source code units,
// never a pin into a JVM String, which would not survive the call frame). `data` may be nullptr iff
// `len == 0` (the empty text, mirroring Rt_TextValue.EMPTY). The buffer is owned solely by THIS arena (a
// flat ownership entry like a HANDLE's global ref or a BYTEARRAY's buffer), so a single arena sweep
// frees it exactly once — no leak, no use-after-free, no double-free. uint16_t (not jchar) so the header
// stays JNI-type-agnostic; jchar IS uint16_t on every supported platform (the JNI spec mandates 16-bit).
struct RellText {
    uint16_t *data;  // owned heap buffer of UTF-16 code units (delete[] in arena dtor); nullptr iff len == 0.
    int32_t len;     // code-unit count (== String.length); always >= 0.
};

struct RellValue {
    RellTag tag;     // offset 0
    int32_t scale;   // offset 4 — DEC_LONG: the decimal scale. ENUM: the enum-type index
                     //   (RR_App.allEnums index). COMPOSITE: the discriminant (-1 tuple /
                     //   struct-def-index). Otherwise 0.
    union {
        int64_t i64;          // BOOLEAN(0/1), INTEGER, ROWID, BIGINT_LONG, DEC_LONG mantissa, ENUM ordinal.
        jobject handle;       // HANDLE: arena-owned JNI global ref to an Rt_Value.
        RellComposite *composite;  // COMPOSITE: arena-owned native composite (NOT a JNI ref).
        RellList *list;            // LIST: arena-owned native list (NOT a JNI ref).
        RellByteArray *bytearray;  // BYTEARRAY: arena-owned native byte_array (NOT a JNI ref).
        RellText *text;            // TEXT: arena-owned native text (NOT a JNI ref).
    } payload;       // offset 8
};

static_assert(sizeof(RellValue) == 16, "RellValue ABI is 16 bytes");
static_assert(sizeof(jobject) <= sizeof(int64_t), "jobject must fit the i64 payload slot");
static_assert(sizeof(RellComposite *) <= sizeof(int64_t), "composite ptr must fit the i64 payload slot");
static_assert(sizeof(RellList *) <= sizeof(int64_t), "list ptr must fit the i64 payload slot");
static_assert(sizeof(RellByteArray *) <= sizeof(int64_t), "byte_array ptr must fit the i64 payload slot");
static_assert(sizeof(RellText *) <= sizeof(int64_t), "text ptr must fit the i64 payload slot");
static_assert(sizeof(jchar) == sizeof(uint16_t), "jchar must be a 16-bit UTF-16 code unit");

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
inline RellValue rv_enum(int64_t ordinal, int32_t typeIdx) {
    // Inline enum: payload.i64 = Int ordinal, `scale` reused as the enum-type index. The pair
    // (typeIdx, ordinal) round-trips through to_jvm to the exact JVM Rt_RR_EnumValue.
    RellValue v{RellTag::ENUM, typeIdx, {0}};
    v.payload.i64 = ordinal;
    return v;
}
inline RellValue rv_handle(jobject globalRef) {
    RellValue v{RellTag::HANDLE, 0, {0}};
    v.payload.handle = globalRef;
    return v;
}
inline RellValue rv_composite(RellComposite *comp, int32_t scaleDisc) {
    // The arena owns `comp`; this only aliases it. `scaleDisc` is mirrored into the value's `scale`
    // so the tag self-describes its discriminant without dereferencing the carrier.
    RellValue v{RellTag::COMPOSITE, scaleDisc, {0}};
    v.payload.composite = comp;
    return v;
}

inline RellValue rv_list(RellList *lst) {
    // The arena owns `lst`; this only aliases it. LIST carries no scale discriminant (the list type
    // is the carrier's typeRef global), so `scale` stays 0.
    RellValue v{RellTag::LIST, 0, {0}};
    v.payload.list = lst;
    return v;
}

inline RellValue rv_bytearray(RellByteArray *ba) {
    // The arena owns `ba`; this only aliases it. BYTEARRAY carries no scale discriminant (the type is
    // statically byte_array — a pure value type, no per-value type info), so `scale` stays 0.
    RellValue v{RellTag::BYTEARRAY, 0, {0}};
    v.payload.bytearray = ba;
    return v;
}

inline RellValue rv_text(RellText *t) {
    // The arena owns `t`; this only aliases it. TEXT carries no scale discriminant (the type is
    // statically text — a pure value type, no per-value type info), so `scale` stays 0.
    RellValue v{RellTag::TEXT, 0, {0}};
    v.payload.text = t;
    return v;
}

inline bool rv_is_none(const RellValue &v) { return v.tag == RellTag::NONE; }
inline bool rv_is_handle(const RellValue &v) { return v.tag == RellTag::HANDLE; }
inline bool rv_is_composite(const RellValue &v) { return v.tag == RellTag::COMPOSITE; }
inline bool rv_is_list(const RellValue &v) { return v.tag == RellTag::LIST; }
inline bool rv_is_bytearray(const RellValue &v) { return v.tag == RellTag::BYTEARRAY; }
inline bool rv_is_text(const RellValue &v) { return v.tag == RellTag::TEXT; }

// Tuple composites use scaleDisc == -1; struct composites carry their RR_App.allStructs def-index.
constexpr int32_t kCompositeTupleDisc = -1;
inline bool composite_is_tuple(const RellValue &v) {
    return v.tag == RellTag::COMPOSITE && v.scale == kCompositeTupleDisc;
}

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

    // Allocate a tracked RellComposite owned by this arena. The returned pointer is `delete`d in
    // the arena dtor (it is NOT a JNI ref). The composite's `fields` are initially empty; the
    // caller fills them. Any HANDLE / nested-COMPOSITE field stored inside must itself be owned by
    // THIS arena so the single arena sweep frees the whole tree. Returns nullptr only on bad_alloc
    // (which we let std::vector::push_back surface as a C++ exception — a hard fault, never a
    // soft-fail), so callers may assume non-null in normal operation.
    RellComposite *makeComposite(int32_t scaleDisc);

    // Allocate a tracked RellList owned by this arena. The returned pointer is `delete`d in the arena
    // dtor (it is NOT a JNI ref). `elems` are initially empty and `typeRef` is nullptr; the caller
    // fills them (and tracks the typeRef via track()). Same ownership contract as makeComposite: any
    // HANDLE / nested-COMPOSITE / nested-LIST element stored inside must itself be owned by THIS arena
    // so the single arena sweep frees the whole tree. Returns nullptr only on bad_alloc.
    RellList *makeList();

    // Allocate a tracked RellByteArray owned by this arena, copying `len` bytes from `src` into a
    // fresh heap buffer. The carrier AND its `data` buffer are freed in the arena dtor (delete[] data;
    // delete carrier). `src` may be nullptr iff `len == 0` (the empty byte_array). The bytes are
    // COPIED (never aliased to a JVM ByteArray, which would not survive the call frame). Returns
    // nullptr only on bad_alloc (a hard fault — callers may assume non-null in normal operation).
    RellByteArray *makeByteArray(const uint8_t *src, int32_t len);

    // Allocate a tracked RellText owned by this arena, copying `len` UTF-16 code units from `src` into a
    // fresh heap buffer. The carrier AND its `data` buffer are freed in the arena dtor (delete[] data;
    // delete carrier). `src` may be nullptr iff `len == 0` (the empty text). The code units are COPIED
    // (never aliased to a JVM String, which would not survive the call frame). Returns nullptr only on
    // bad_alloc (a hard fault — callers may assume non-null in normal operation).
    RellText *makeText(const uint16_t *src, int32_t len);

    JNIEnv *env() const { return env_; }
    std::size_t size() const { return globals_.size(); }

private:
    JNIEnv *env_;
    std::vector<jobject> globals_;  // bump list; each DeleteGlobalRef'd in the dtor.
    std::vector<RellComposite *> composites_;  // bump list; each `delete`d in the dtor.
    std::vector<RellList *> lists_;  // bump list; each `delete`d in the dtor.
    std::vector<RellByteArray *> byteArrays_;  // bump list; each `delete[] data` + `delete` in the dtor.
    std::vector<RellText *> texts_;  // bump list; each `delete[] data` + `delete` in the dtor.
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
//     `frameHandle` is the opaque Llvm_CallEnv handle (RellCallCtx.frameHandle / the ctxHandle the
//     JVM registry keys); it is used to crack an inline ENUM (def_index + ordinal) via
//     Llvm_SysBridge.enumCrack and to name a struct's def-index via Llvm_SysBridge.structDefIndex —
//     the call env carries the RR_App needed for both. A Rt_StructValue becomes a COMPOSITE: an
//     arena.makeComposite() carrier whose fields are RECURSIVELY from_jvm'd (each may be inline /
//     nested COMPOSITE / HANDLE), carrying its def-index as the discriminant so to_jvm rebuilds the
//     EXACT struct type. A Rt_TupleValue arriving HERE (a bare tuple param) is left as a plain
//     HANDLE — the value-ABI gate never admits a tuple param/return, so this only happens when a
//     tuple is nested inside another HANDLE-routed value; native tuple construction/access works on
//     COMPOSITEs the body builds, which never need a tuple from_jvm.
RellValue from_jvm(JNIEnv *env, RellArena &arena, jobject rtValue, jlong frameHandle);

// 4b. native -> JVM (rebox). Materializes a RellValue back to a jobject Rt_Value, returning a
//     LOCAL ref owned by the caller's JNI frame (if it must escape into another arena, adopt
//     it there). For HANDLE, returns the arena-owned global ref directly (the arena still
//     owns it). Inline tags call the cached factories (Rt_IntValue.get, Rt_BooleanValue.get,
//     Rt_RowidValue.get, Rt_BigIntegerValue.get, the long-scale decimal ctor, the
//     Rt_NullValue/Rt_UnitValue singletons). These factories can throw Rt_Exception (e.g.
//     decimal overflow, negative rowid); the caller MUST ExceptionCheck after (see §7). For ENUM,
//     `frameHandle` resolves the RR_App (via Llvm_SysBridge.enumValue) to rebuild the EXACT
//     Rt_RR_EnumValue from (typeIdx, ordinal) — round-trip identity with from_jvm. A STRUCT
//     COMPOSITE is reconstructed RECURSIVELY: each field is to_jvm'd, then Llvm_SysBridge.structValue
//     rebuilds the Rt_StructValue from its def-index + field values (mirroring the enum rebox), so
//     from_jvm∘to_jvm is identity. A TUPLE COMPOSITE reaching to_jvm is a wiring fault (the value-ABI
//     gate forbids a tuple param/return, and an intermediate constructed tuple is only ever
//     field-read, never reboxed), so to_jvm hard-faults on it rather than guessing a tuple type it
//     cannot reconstruct by index.
jobject to_jvm(JNIEnv *env, RellArena &arena, const RellValue &value, jlong frameHandle);

// 4c. Cached IDs table. Lazily initialized once; aborts hard if any lookup is null. Holds
//     global jclass refs + the primitive jfieldIDs (Rt_IntValue/Rt_BooleanValue/Rt_RowidValue
//     value fields — real backing fields of @JvmRecord/data-class vals), the getter jmethodIDs
//     for the interface-backed Rt_DecimalValue/Rt_BigIntegerValue `.value`, the @JvmStatic
//     factory method IDs, and the Companion object global refs where a factory lives on the
//     Kotlin companion (Rt_RowidValue.get, Rt_BigIntegerValue.get).
void ensureRellRefsInitialized(JNIEnv *env);

// Forward-decl the per-call context (fully defined in §6) so the composite helpers can name it.
struct RellCallCtx;

// 4d. Composite construction / field access runtime helpers (the first heap-value natives).
//
// These are the sret-style (out-pointer) runtime entry points the IR emits for TupleExpr /
// StructExpr construction and StructAttr / TupleAttr field reads. They take/return RellValue ONLY
// through pointers, so they sidestep the by-value aggregate-return ABI hazard that forces every
// rell_sysfn_call back-call to soft-fail (lower_call.cpp). Resolved by the JIT's process-symbol
// generator (same mechanism as rell_int_overflow / rell_jit_escape).
//
//   rell_make_composite: allocate a RellComposite in ctx->arena with discriminant `scaleDisc`
//     (kCompositeTupleDisc == -1 for a tuple, else the RR_App.allStructs def-index), copy the
//     `n` field RellValues from `fields` (in declared order), and write the COMPOSITE-tagged
//     RellValue aliasing it into *out. The arena owns the composite AND (transitively) every
//     HANDLE/nested-COMPOSITE field, so a single arena sweep frees the tree. Fields that are
//     inline/enum are copied by value; HANDLE/COMPOSITE fields alias the SAME arena's allocations
//     (they were produced by from_jvm / a prior rell_make_composite under this same ctx->arena).
//   rell_composite_get: read the `i`-th field of the COMPOSITE `*composite` into *out (a plain
//     16-byte copy; the aliased arena allocation keeps owning any HANDLE/COMPOSITE payload). This
//     is bit-exact with the interpreter's (base as Rt_StructValue).get(i) / Rt_TupleValue
//     .elements[i] (rr_interp_expr.kt). A bad index or non-COMPOSITE receiver is a wiring fault.
extern "C" void rell_make_composite(RellValue *out, RellCallCtx *ctx, int32_t scaleDisc,
                                    const RellValue *fields, int32_t n);
extern "C" void rell_composite_get(RellValue *out, const RellValue *composite, int32_t i);

// 4e. List construction / element access / size runtime helpers (mirroring the composite helpers).
//
//   rell_make_list: allocate a RellList in ctx->arena, copy the `n` element RellValues from `elems`
//     (in iteration order), resolve the list TYPE via the JVM (Llvm_SysBridge.listType keyed by the
//     interned `listTypeId` + ctx->frameHandle) and track its global ref, then write the LIST-tagged
//     RellValue aliasing it into *out. The arena owns the list AND (transitively) every HANDLE /
//     nested-COMPOSITE / nested-LIST element, so a single arena sweep frees the tree. Element values
//     that are inline/enum are copied by value; HANDLE/COMPOSITE/LIST elements alias the SAME arena's
//     allocations. Iteration order is preserved (the IR stores elems[i] in ListLiteralExpr source
//     order, matching the interpreter's `expr.exprs.map { evaluateExpr(it) }`). On a pending
//     Rt_Exception or a bad listTypeId, *out is poisoned (rv_none()) so the trampoline aborts.
//   rell_list_get: bounds-checked read of the `i`-th element of the LIST `*list` into *out. On an
//     out-of-bounds index (i < 0 || i >= size) it raises the EXACT interpreter Rt_Exception via the
//     JVM (Llvm_SysBridge.listIndexError: code "list:index:<size>:<i>", msg "List index out of
//     bounds: <i> (size <size>)") and poisons *out; the trampoline then aborts on the pending
//     exception, bit-exact with rr_interpreter.kt's Rt_ListValue.checkIndex. A valid read is a plain
//     16-byte copy (the element's HANDLE/COMPOSITE/LIST payload keeps being owned by the arena).
//   rell_list_size: native size of the LIST `*list` as an INTEGER RellValue (bit-exact with
//     Rt_ListValue.elements.size — Rt_CollectionValue size()).
extern "C" void rell_make_list(RellValue *out, RellCallCtx *ctx, int32_t listTypeId,
                               const RellValue *elems, int32_t n);
extern "C" void rell_list_get(RellValue *out, RellCallCtx *ctx, const RellValue *list, int64_t i);
extern "C" void rell_list_size(RellValue *out, const RellValue *list);

// =====================================================================================
// 4f. byte_array construction / access / ops runtime helpers (mirroring the list helpers).
//
// All sret-style (out-pointer) where the result is a RellValue, sidestepping the by-value aggregate-
// return ABI hazard; the comparison helper returns a scalar i32 sign (an ABI-safe small int). Resolved
// by the JIT's process-symbol generator (extern "C"). byte_array is a pure VALUE type (content
// identity), so — unlike a LIST — there is no typeRef to resolve: a constructed byte_array needs no
// JVM back-call, only an arena buffer. Every helper is bit-exact with the interpreter (rt_value_
// bytearray.kt / rt_ops.kt / rr_interpreter.kt), documented per helper.
//
//   rell_make_bytearray: allocate a RellByteArray in ctx->arena copying `n` bytes from `bytes` (a
//     stack/IR buffer of the x"..." literal), and write the BYTEARRAY-tagged RellValue aliasing it
//     into *out. The arena owns the carrier+buffer (single sweep frees it). `n == 0` is the empty
//     byte_array (data == nullptr) — to_jvm canonicalises it to Rt_ByteArrayValue.EMPTY on rebox.
//   rell_bytearray_size: native length as an INTEGER RellValue (bit-exact with the interpreter's
//     byte_array .size() -> value.size, an Int widened to Long).
//   rell_bytearray_get: bounds-checked read of the `i`-th byte as an INTEGER RellValue in [0,255]
//     (UNSIGNED: data[i] & 0xFF — bit-exact with rr_interpreter.kt ByteArraySubscript
//     `Rt_IntValue.get(ba[idx].toLong() and 0xFF)`). On OOB (i < 0 || i >= len) it records the EXACT
//     interpreter Rt_Exception into the byte-array-error channel (code
//     "expr_bytearray_subscript_index:<size>:<i>", msg "Byte array index out of range: <i> (size
//     <size>)") and poisons *out; the trampoline then aborts on the channel poll (callValueFunction
//     -> pollByteArrayError). A negative index is OOB (no Python-style wrap), per the interpreter.
//   rell_bytearray_concat: allocate a fresh arena buffer holding a-bytes ++ b-bytes and write the
//     BYTEARRAY result into *out. Bit-exact with rt_ops.kt R_BinaryOp_Concat_ByteArray
//     (Rt_ByteArrayValue.get(a.value + b.value)); concat has NO size envelope (the size constraint is
//     a struct/entity-attribute concern, not the `+` operator), so a pure native splice is exact.
//   rell_bytearray_eq: content equality as a BOOLEAN RellValue (bit-exact with
//     Rt_ByteArrayValue.equals: contentEquals — equal length AND equal bytes). NE is the IR negation.
//   rell_bytearray_cmp: UNSIGNED lexicographic sign in {-1,0,1} (bit-exact with rt_ops.kt
//     compareByteArrays: Integer.compareUnsigned per byte over min(len) bytes, then len compare). The
//     lower_ops CmpInfo path maps the sign to LT/GT/LE/GE via a signed compare against 0.
extern "C" void rell_make_bytearray(RellValue *out, RellCallCtx *ctx, const uint8_t *bytes,
                                    int32_t n);
extern "C" void rell_bytearray_size(RellValue *out, const RellValue *ba);
extern "C" void rell_bytearray_get(RellValue *out, const RellValue *ba, int64_t i);
extern "C" void rell_bytearray_concat(RellValue *out, RellCallCtx *ctx, const RellValue *a,
                                      const RellValue *b);
extern "C" void rell_bytearray_eq(RellValue *out, const RellValue *a, const RellValue *b);
extern "C" int32_t rell_bytearray_cmp(const RellValue *a, const RellValue *b);

// Native crypto hashes over a single BYTEARRAY operand, allocating the 32-byte digest in ctx->arena.
// Wire the self-contained rell::crypto hashes into the JIT (lower_call.cpp), replacing the
// rell_sysfn_call back-call for crypto.sha256 / byte_array.sha256 / crypto.keccak256. NO JNI, NO
// OpenSSL, NO libsecp256k1. Bit-exact with lib_crypto.kt:
//   rell_crypto_sha256   == MessageDigest("SHA-256")        (FIPS 180-4; NIST vectors).
//   rell_crypto_keccak256 == BouncyCastle Keccak.Digest256  (ORIGINAL Keccak, 0x01 pad — NOT SHA3).
// The signing family stays on the back-call (byte-identity unproven; libsecp256k1 not linked).
extern "C" void rell_crypto_sha256(RellValue *out, RellCallCtx *ctx, const RellValue *ba);
extern "C" void rell_crypto_keccak256(RellValue *out, RellCallCtx *ctx, const RellValue *ba);

// Record an out-of-bounds byte_array subscript into the per-thread byte-array-error channel
// (jni_bridge.cpp), mirroring rell_list_index_error. rell_bytearray_get calls this on OOB and returns
// rv_none(); callValueFunction polls the channel after the native run and raises the EXACT interpreter
// Rt_Exception (code "expr_bytearray_subscript_index:<size>:<index>", msg "Byte array index out of
// range: <index> (size <size>)" — rr_interpreter.kt ByteArraySubscript). extern "C" for the JIT's
// symbol generator.
extern "C" void rell_bytearray_index_error(int32_t size, int64_t index);

// =====================================================================================
// 4g. text construction / access / ops runtime helpers (mirroring the byte_array helpers §4f).
//
// All sret-style (out-pointer) where the result is a RellValue; the comparison helper returns a scalar
// i32 sign (an ABI-safe small int). Resolved by the JIT's process-symbol generator (extern "C"). text is
// a pure VALUE type (content identity over UTF-16 code units), so — like byte_array — there is no typeRef
// to resolve: a constructed text needs no JVM back-call, only an arena buffer. Every helper is bit-exact
// with the interpreter (rt_value_text.kt / rt_ops.kt / lib_type_text.kt / rr_interpreter.kt), documented
// per helper. The buffer holds UTF-16 code UNITS (jchar) — NOT code points and NOT bytes — matching
// java.lang.String exactly; surrogate pairs are stored as two units, identical to the JVM String.
//
//   rell_make_text: allocate a RellText in ctx->arena copying `n` UTF-16 code units from `units` (the
//     compile-time UTF-16 decode of the text literal), and write the TEXT-tagged RellValue aliasing it
//     into *out. The arena owns the carrier+buffer (single sweep frees it). `n == 0` is the empty text
//     (data == nullptr) — to_jvm canonicalises it to Rt_TextValue.EMPTY on rebox. Bit-exact with
//     rr_interpreter.kt RR_ConstantValue.Text -> Rt_TextValue.get(cv.value): the code units are the
//     literal's exact String contents.
//   rell_text_size: native length as an INTEGER RellValue (bit-exact with lib_type_text.kt `size` body
//     `self.value.length.toLong()` — String.length == UTF-16 CODE UNITS, NOT code points).
//   rell_text_get: bounds-checked read of the `i`-th code unit as a 1-code-unit TEXT RellValue (a fresh
//     arena buffer of length 1). Bit-exact with rr_interpreter.kt TextSubscript
//     `Rt_TextValue.get(text[idx.toInt()].toString())` (a single Java char -> a length-1 String; for a
//     surrogate index this is a lone-surrogate text, exactly as the interpreter produces). On OOB
//     (i < 0 || i >= len) it records the EXACT interpreter Rt_Exception into the text-error channel (code
//     "expr_text_subscript_index:<len>:<i>", msg "Index out of bounds: <i> (length <len>)") and poisons
//     *out; the trampoline then aborts on the channel poll. A negative index is OOB (no Python-style wrap).
//     Needs ctx->arena to allocate the 1-unit result buffer.
//   rell_text_concat: allocate a fresh arena buffer holding a-units ++ b-units and write the TEXT result
//     into *out. Bit-exact with rt_ops.kt R_BinaryOp_Concat_Text (Rt_TextValue.get(a.value + b.value));
//     String concatenation is a pure code-unit splice, so a native concat reproduces it exactly.
//   rell_text_eq: content equality as a BOOLEAN RellValue (bit-exact with Rt_TextValue.equals == String
//     content equality: equal length AND equal code units). NE is the IR negation.
//   rell_text_cmp: String.compareTo sign in {-1,0,1} (bit-exact with rt_ops.kt R_CmpType_Text ==
//     value.compareTo(value)). java.lang.String.compareTo compares the first differing CODE UNIT as an
//     UNSIGNED 16-bit value (Java `char` is unsigned 0..65535), and if one is a prefix of the other the
//     shorter is LESS. The lower_ops CmpInfo path maps the sign to LT/GT/LE/GE via a signed compare
//     against 0. CRITICAL: code units must compare UNSIGNED (uint16_t), so a non-BMP code unit (e.g. a
//     surrogate 0xD800..0xDFFF, or any unit >= 0x8000) orders ABOVE a BMP unit below it — exactly as
//     String.compareTo does.
extern "C" void rell_make_text(RellValue *out, RellCallCtx *ctx, const uint16_t *units, int32_t n);
//   rell_make_decimal: materialise a decimal CONSTANT from its plain string (`units` = the literal's
//     ASCII code units, the tokenizer's range-validated canonical form). Routes through the JVM once
//     (Llvm_SysBridge.decimalValue -> Rt_DecimalValue.get(BigDecimal(s))) then cracks the result back
//     via from_jvm, so the inline DEC_LONG is the EXACT stripped normal form (1.0 and 1.00 both ->
//     (1, 0)) or a wide HANDLE — NO C++ re-derivation of Lib_DecimalMath.scale / stripTrailingZeros.
//     Needs ctx->env + ctx->arena (the back-call + the HANDLE carrier owner). Bit-exact with
//     rr_interpreter.kt RR_ConstantValue.Decimal.
extern "C" void rell_make_decimal(RellValue *out, RellCallCtx *ctx, const uint16_t *units, int32_t n);
extern "C" void rell_text_size(RellValue *out, const RellValue *t);
extern "C" void rell_text_get(RellValue *out, RellCallCtx *ctx, const RellValue *t, int64_t i);
extern "C" void rell_text_concat(RellValue *out, RellCallCtx *ctx, const RellValue *a,
                                 const RellValue *b);
extern "C" void rell_text_eq(RellValue *out, const RellValue *a, const RellValue *b);
extern "C" int32_t rell_text_cmp(const RellValue *a, const RellValue *b);

// Record an out-of-bounds text subscript into the per-thread text-error channel (jni_bridge.cpp),
// mirroring rell_bytearray_index_error. rell_text_get calls this on OOB and returns rv_none();
// callValueFunction polls the channel after the native run and raises the EXACT interpreter Rt_Exception
// (code "expr_text_subscript_index:<len>:<index>", msg "Index out of bounds: <index> (length <len>)" —
// rr_interpreter.kt TextSubscript). extern "C" for the JIT's symbol generator.
extern "C" void rell_text_index_error(int32_t len, int64_t index);

// Member text stdlib ops (lib_type_text.kt) wired NATIVE over the UTF-16 code-unit buffer, bit-exact with
// java.lang.String. Boolean/integer results are sret RellValues; the error-bearing ops (char_at / sub /
// index_of/2 / repeat) record their op-specific Rt_Exception code+message into the general text-op error
// channel (rell_text_op_error) and poison *out. DEFERRED (NOT here, stay on rell_sysfn_call): upper_case/
// lower_case (LOCALE-sensitive String.uppercase/lowercase), format, split, trim, matches/like/regex,
// to_bytes/from_bytes, compare_to, reversed, last_index_of. See value.cpp for the per-op bit-exactness.
//
//   rell_text_starts_with/ends_with/contains  -> boolean (total; empty needle/prefix/suffix matches).
//   rell_text_index_of                         -> integer (first code-unit index or -1; empty -> 0).
//   rell_text_index_of_from(self, sub, start)  -> integer; PRE-CHECKS start in [0,len) (else custom error).
//   rell_text_char_at(self, i)                 -> integer (UNSIGNED 16-bit code unit; i in [0,len) else err).
//   rell_text_sub1(self, start) / sub2(start,end) -> text; calcSub range check (error-on-OOB, NOT clamp).
//   rell_text_repeat(self, n)                  -> text; rtCheckRepeatArgs (n<0 / n>Int.MAX / total>Int.MAX).
//   rell_text_replace(self, old, new)          -> text; LITERAL all-occurrence replace (escapes to the
//                                                 interpreter via rell_jit_escape when `old` is EMPTY).
extern "C" void rell_text_starts_with(RellValue *out, const RellValue *self, const RellValue *prefix);
extern "C" void rell_text_ends_with(RellValue *out, const RellValue *self, const RellValue *suffix);
extern "C" void rell_text_contains(RellValue *out, const RellValue *self, const RellValue *sub);
extern "C" void rell_text_index_of(RellValue *out, const RellValue *self, const RellValue *sub);
extern "C" void rell_text_index_of_from(RellValue *out, const RellValue *self, const RellValue *sub,
                                        int64_t start);
extern "C" void rell_text_char_at(RellValue *out, const RellValue *self, int64_t i);
extern "C" void rell_text_sub1(RellValue *out, RellCallCtx *ctx, const RellValue *self, int64_t start);
extern "C" void rell_text_sub2(RellValue *out, RellCallCtx *ctx, const RellValue *self, int64_t start,
                               int64_t end);
extern "C" void rell_text_repeat(RellValue *out, RellCallCtx *ctx, const RellValue *self, int64_t n);
extern "C" void rell_text_replace(RellValue *out, RellCallCtx *ctx, const RellValue *self,
                                  const RellValue *oldV, const RellValue *newV);

// Record a member text-op Rt_Exception (char_at / sub / index_of/2 / repeat) into the general text-op
// error channel (jni_bridge.cpp): the EXACT code + message the native helper built (verbatim from
// lib_type_text.kt). The helpers above call this on their error paths and poison *out; callValueFunction
// polls pollTextOpError after the run and raises the consensus-exact Rt_Exception. extern "C" for the JIT.
extern "C" void rell_text_op_error(const char *code, const char *message);

// Signal that the JIT'd body hit a case it cannot reproduce bit-exactly (here: rell_text_replace with an
// EMPTY target), so the whole call must re-run on the interpreter. Defined in jni_bridge.cpp (§ escape).
extern "C" void rell_jit_escape();

// =====================================================================================
// 5. Universal JNI stdlib caller — the correctness floor.
//
// Any RR_FunctionCallTarget.{SysGlobal,SysMember,NativeUser} / MemberCalculator.SysFunction /
// SysQueryBody reachable BY NAME becomes callable from native code: rell_sysfn_call boxes the
// RellValue args to an Rt_Value[] of reboxed Rt_Values (inside a pushed local frame), invokes
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

// Interned list-type id (a ListLiteralExpr's static list type). -1 means "not interned".
using ListTypeId = int32_t;
constexpr ListTypeId kListTypeIdNone = -1;

// List-type interning table. Positional, walk-order, like DbNodeTable: each ListLiteralExpr the
// lowering walks calls intern() once to claim the next dense id. The JVM mirror (Llvm_Backend) walks
// the SAME function body RR tree in the SAME deterministic pre-order, collecting each
// RR_Expr.ListLiteral.type, to build the dense Array<Rt_ValueClass> that Llvm_SysBridge.listType
// indexes — so the two id spaces agree without a shared key. Native code never names the structural
// list type itself; it only hands back the id, and the JVM resolves it via resolveType(RR_Type.List).
class ListTypeTable {
public:
    ListTypeTable() = default;
    ListTypeId intern() { return count_++; }  // assign the next dense id in walk order.
    std::size_t size() const { return static_cast<std::size_t>(count_); }

private:
    int32_t count_ = 0;
};

// JVM back-call: resolve an interned list-type id to its JVM Rt_ValueClass (Llvm_SysBridge.listType).
// Returns a LOCAL ref to the type object owned by the caller's JNI frame; nullptr (exception pending)
// on a bad id. Called by rell_make_list to obtain the typeRef for a constructed list.
jobject rell_list_type(JNIEnv *env, ListTypeId listTypeId, jlong frameHandle);

// (to_jvm rebuilds an Rt_ListValue by calling the cached Llvm_SysBridge.listValue method directly,
// like the struct rebox — no separate C wrapper is needed.)

// 4h. Native structural equality for composite / collection values (EQ / NE). Pure-native (no JNI):
// walks the native RellValue reps built by from_jvm / rell_make_composite / rell_make_list and recurses
// element-wise, reproducing Rt_Value.equals EXACTLY:
//   * tuple/struct (COMPOSITE): same discriminant (tuple disc -1 / struct def-index — reproducing the
//     `other is Rt_TupleValue` vs `Rt_StructValue` type check), same field count, then field-wise
//     recursive equals (bit-exact with Rt_TupleValue.equals == elements equality and
//     Rt_StructValue.structEquals == size + positional get(i) equality — neither compares the Rt type
//     beyond the value-class kind, which the discriminant already pins).
//   * list (LIST): same length, positional recursive element equals (Rt_ListValue.equals == elements
//     list equality).
//   * scalar leaves recurse with the SAME inline rules the lower_ops fast paths use:
//     BOOLEAN/INTEGER/ROWID/BIGINT_LONG/ENUM -> i64 payload equality (ENUM also pins the typeIdx in
//     `scale`); DEC_LONG -> (mantissa, scale) equality on the stripped canonical normal form (the M3
//     invariant: 1.0 and 1.00 both crack to (1,0), so payload+scale equality coincides with the
//     interpreter's scale-insensitive BigDecimal value equality); TEXT -> code-unit content equality;
//     BYTEARRAY -> byte content equality; NULL_/UNIT -> tag equality.
// CONSENSUS FALLBACK: any element still an opaque HANDLE (set, map, gtv, json, virtual, range, wide
// decimal/big_integer, or a bare tuple that stayed a HANDLE) cannot be compared bit-exactly in C++ —
// the helper calls rell_jit_escape() and returns a meaningless 0, so callValueFunction re-runs the
// WHOLE call on the interpreter (correct result, counted as a jitMiss). A tag MISMATCH between the two
// operands (impossible for statically-same-typed operands except via the wide-fit HANDLE escape) also
// escapes — never guess. Returns 1 (equal) / 0 (not equal or escaped). extern "C" for the JIT symbol
// generator; the lower_ops EQ/NE path maps the i32 to a BOOLEAN runtime value (negated for NE).
extern "C" int32_t rell_value_equals(const RellValue *a, const RellValue *b);

// Back-call (defined in jni_bridge.cpp) that records a runtime envelope escape on the per-thread
// channel so callValueFunction re-runs the WHOLE call on the interpreter. Declared here so pure-native
// runtime helpers (rell_value_equals) can signal "not provably bit-exact" without an emit-time IR call.
extern "C" void rell_jit_escape();

// Record an out-of-bounds list subscript into the per-thread list-error channel (jni_bridge.cpp),
// mirroring rell_int_overflow / rell_int_div0. rell_list_get calls this on OOB and returns rv_none();
// callValueFunction polls the channel after the native run and raises the EXACT interpreter
// Rt_Exception (code "list:index:<size>:<index>", msg "List index out of bounds: <index> (size
// <size>)" — Rt_ListValue.checkIndex). Using the channel (not a JNI throw) keeps the OOB run counted
// as a JIT hit and matches the integer-error mechanism. extern "C" for the JIT's symbol generator.
extern "C" void rell_list_index_error(int32_t size, int64_t index);

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

// Slot-map key for the value-ABI lowering's param/local alloca table. Identity is the frame
// OFFSET alone — NOT (block_uid, offset). The interpreter's frame storage (rt_frame.kt) is a flat
// array indexed by ptr.offset; offsets are frame-global and disjoint across blocks, and blockUid
// is only a runtime sanity assertion (Rt_CallFrame.checkPtr), never part of slot identity. A
// VarExpr reading a parameter from inside a nested block carries that block's block_uid while the
// parameter's declaring VarPtr carries the root block's — so keying on the pair spuriously misses
// the param slot and soft-fails the whole function (e.g. `if (a>b) return a; return b;`). The i64
// Lowerer (jni_bridge.cpp slotByOffset_) keys by offset for exactly this reason; mirror it here.
// block_uid is retained as a field for diagnostics but excluded from equality/hash.
struct VarPtrKey {
    uint32_t block_uid;
    int32_t offset;
    bool operator==(const VarPtrKey &o) const { return offset == o.offset; }
};

struct VarPtrKeyHash {
    std::size_t operator()(const VarPtrKey &k) const noexcept {
        return std::hash<int32_t>{}(k.offset);
    }
};

class EmitContext {
public:
    EmitContext(llvm::LLVMContext &ctx, llvm::Module &module, llvm::IRBuilder<> &builder,
                const ir::App &app, SysFnTable &sysFns, DbNodeTable &dbNodes,
                ListTypeTable &listTypes, std::string &errorOut)
        : ctx_(ctx),
          module_(module),
          builder_(builder),
          app_(app),
          sysFns_(sysFns),
          dbNodes_(dbNodes),
          listTypes_(listTypes),
          error_(errorOut) {}

    EmitContext(const EmitContext &) = delete;
    EmitContext &operator=(const EmitContext &) = delete;

    llvm::LLVMContext &ctx() const { return ctx_; }
    llvm::Module &module() const { return module_; }
    llvm::IRBuilder<> &builder() const { return builder_; }
    const ir::App &app() const { return app_; }
    SysFnTable &sysFns() const { return sysFns_; }
    DbNodeTable &dbNodes() const { return dbNodes_; }
    ListTypeTable &listTypes() const { return listTypes_; }

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

    // ---- self-recursion descriptor ---------------------------------------------------
    // The value-ABI compile driver (jni_bridge.cpp compileFunctionExtended) registers the
    // function it is lowering here so lower_call.cpp can emit a DIRECT self-recursive call —
    // the one cross-call shape the i64 oracle also supports (jni_bridge.cpp Lowerer::lowerSelfCall).
    // `selfFn`/`selfFnType` are the sret callee being built (`void(ptr result, ptr args, ptr ctx)`),
    // and `selfFnIndex` is its App.functions index so a FnTarget_RegularUser.fn_def_index() matching
    // it is provably the same function. selfFnIndex == -1 (the default) means "no self-call target"
    // (queries/constants), which can never match a non-negative fn_def_index.
    void setSelfFunction(llvm::Function *fn, llvm::FunctionType *fnType, int fnIndex) {
        selfFn_ = fn;
        selfFnType_ = fnType;
        selfFnIndex_ = fnIndex;
    }
    llvm::Function *selfFn() const { return selfFn_; }
    llvm::FunctionType *selfFnType() const { return selfFnType_; }
    int selfFnIndex() const { return selfFnIndex_; }

private:
    llvm::LLVMContext &ctx_;
    llvm::Module &module_;
    llvm::IRBuilder<> &builder_;
    const ir::App &app_;
    SysFnTable &sysFns_;
    DbNodeTable &dbNodes_;
    ListTypeTable &listTypes_;
    std::string &error_;
    bool failed_ = false;
    std::unordered_map<VarPtrKey, llvm::Value *, VarPtrKeyHash> slots_;
    llvm::Function *selfFn_ = nullptr;
    llvm::FunctionType *selfFnType_ = nullptr;
    int selfFnIndex_ = -1;
};

// =====================================================================================
// 9. Per-category lowering + intrinsic entry points.
//
// Each lowering file exposes one entry the others call; all return an llvm::Value* of
// valueType() (the runtime RellValue SSA) or nullptr after ec.fail(). The intrinsic overlays
// (intrinsics_*) take already-unpacked operands and either produce an in-envelope result or
// signal "escaped" so the caller routes to rell_sysfn_call.
// =====================================================================================

// =====================================================================================
// Runtime envelope-escape emit helpers (shared by intrinsics_decimal / intrinsics_biginteger /
// lower_ops). A decimal/big_integer long-fit fast path is bit-exact ONLY while every operand and
// intermediate stays inside the inline envelope (tag == DEC_LONG/BIGINT_LONG, scale <=
// kDecLongMaxScale, no i64 overflow). When that cannot be proven at emit time it is a RUNTIME
// property; these helpers emit the runtime branch that, on the escape edge, calls the extern "C"
// `rell_jit_escape()` back-call (jni_bridge.cpp) so callValueFunction re-runs the WHOLE call on the
// interpreter (bit-exact). The fast path still produces a defined SSA value on the escape edge; it is
// discarded JVM-side once the escape flag is observed, so nothing ever wraps into consensus.
// =====================================================================================

// Emit an unconditional call to `rell_jit_escape()`. Marks the current native call as having left
// the long-fit envelope; the JVM re-runs it on the interpreter.
void emitJitEscape(EmitContext &ec);

// Emit `if (cond) rell_jit_escape();` as straight-line control flow, leaving the builder positioned
// at the continuation block. `cond` is an i1. Used for the runtime overflow / scale-out-of-range /
// wrong-tag edges of the decimal/big_integer fast paths.
void emitJitEscapeIf(EmitContext &ec, llvm::Value *cond);

// Emit a runtime tag guard: if `runtimeValue`'s tag != `expected`, escape (the value is a wide
// HANDLE that the long-fit fast path cannot handle). Returns nothing; positions the builder at the
// continuation. The static type gate proves the STATIC type is decimal/big_integer, but a wide value
// is a HANDLE at runtime, so this guard is what makes the wide slice fall back to the interpreter.
void emitEscapeIfNotTag(EmitContext &ec, llvm::Value *runtimeValue, RellTag expected);

// lower_expr.cpp — the Expr-union dispatcher (VarExpr, ConstantValueExpr, IfExpr, etc.).
llvm::Value *lowerExpr(EmitContext &ec, const ir::Expr &expr);

// lower_expr.cpp — shared `when`-chooser helpers reused by the WhenStatement lowering
// (lower_stmt.cpp), so statement-`when` matches with the IDENTICAL chooser semantics and the same
// inline-key-type restriction as expression-`when` (no forked rules, no consensus divergence).
//   whenKeyTypeInlineComparable: true iff the static key type is a canonical inline carrier
//     (BOOLEAN/INTEGER/ROWID/ENUM); everything else must soft-fail.
//   emitWhenInlineEq: tag-THEN-payload i1 equality of two runtime values (caller must have proven
//     both use a canonical inline carrier).
//   exprStaticResultType: best-effort static result type of an Expr (nullptr when unreadable).
//   lowerInlineConstant: lower a ConstantValue to an inline runtime value (LookupWhenChooser keys);
//     nullptr after ec.fail() for a non-inline (heap/opaque/decimal) constant.
bool whenKeyTypeInlineComparable(const ir::Type *type);
llvm::Value *emitWhenInlineEq(EmitContext &ec, llvm::Value *a, llvm::Value *b);
const ir::Type *exprStaticResultType(const ir::Expr *expr);
llvm::Value *lowerInlineConstant(EmitContext &ec, const ir::ConstantValue &cv);

// lower_ops.cpp — BinaryExpr/UnaryExpr (+ CmpInfo comparisons), dispatching into the
// intrinsics_* overlays; routes out-of-envelope ops to rell_sysfn_call.
llvm::Value *lowerBinary(EmitContext &ec, const ir::BinaryExpr &bin);
llvm::Value *lowerUnary(EmitContext &ec, const ir::UnaryExpr &un);

// lower_stmt.cpp — the Stmt-union dispatcher (Block/Return/If/While/For/Assign/Var/...).
// Returns true on success; false (after ec.fail()) makes the function soft-fail.
bool lowerStmt(EmitContext &ec, const ir::Stmt &stmt);

// lower_stmt.cpp — the per-function driver helper. Establishes the FunctionLoweringScope (loop
// stack + lazy single-exit return path), lowers `body`, and hands back the shared return slot /
// return block so the calling JNI driver (jni_bridge.cpp's value-ABI compile entry) can attach
// the convention-correct epilogue (`ret` reading the slot). Returns false (after ec.fail()) when
// the body is outside the lowerable envelope — the whole function then soft-fails. `*returnSlotOut`
// / `*returnBlockOut` are null when no explicit ReturnStatement was lowered (fall-off-end body).
bool lowerFunctionBody(EmitContext &ec, const ir::Stmt &body, llvm::Value **returnSlotOut,
                       llvm::BasicBlock **returnBlockOut);

// lower_call.cpp — FunctionCallExpr / MemberExpr, routing SysGlobal/SysMember/NativeUser/
// SysFunction targets to rell_sysfn_call and DB members to the SQL back-call.
llvm::Value *lowerCall(EmitContext &ec, const ir::FunctionCallExpr &call);
llvm::Value *lowerMember(EmitContext &ec, const ir::MemberExpr &member);

// lower_expr.cpp — emit a native composite field read (StructAttr / TupleAttr): calls the sret-style
// rell_composite_get(out, &base, index) helper and returns the field runtime value. `base` is the
// already-lowered COMPOSITE receiver. Bit-exact with the interpreter's (base as Rt_StructValue)
// .get(index) / Rt_TupleValue.elements[index] (rr_interp_expr.kt). Used by lower_call.cpp's
// MemberCalculator StructAttr/TupleAttr cases.
llvm::Value *emitCompositeGet(EmitContext &ec, llvm::Value *base, int32_t index);

// lower_expr.cpp — emit a native list element read (ListSubscriptExpr): calls the sret-style
// rell_list_get(out, ctx, &base, index) helper (which bounds-checks and, on OOB, raises the exact
// interpreter list:index Rt_Exception via the JVM) and returns the element runtime value. `base` is
// the already-lowered LIST receiver; `indexI64` is the i64 index value. Bit-exact with the
// interpreter's Rt_ListValue.checkIndex + elements[idx] (rr_interpreter.kt ListSubscript).
llvm::Value *emitListGet(EmitContext &ec, llvm::Value *base, llvm::Value *indexI64);

// lower_expr.cpp — emit a native byte_array subscript read (ByteArraySubscriptExpr): spills the
// already-lowered BYTEARRAY receiver, calls the sret-style rell_bytearray_get(out, &base, indexI64)
// helper (which bounds-checks and, on OOB, records the exact interpreter expr_bytearray_subscript_index
// Rt_Exception into the byte-array-error channel), and returns the byte as an INTEGER runtime value in
// [0,255] (UNSIGNED). Bit-exact with rr_interpreter.kt ByteArraySubscript. Used by lower_expr.cpp's
// ByteArraySubscriptExpr case.
llvm::Value *emitByteArrayGet(EmitContext &ec, llvm::Value *base, llvm::Value *indexI64);

// lower_call.cpp — emit a native byte_array .size() read: spills the already-lowered BYTEARRAY
// receiver, calls the sret-style rell_bytearray_size(out, &base) helper, and returns the length as an
// INTEGER runtime value. Bit-exact with the interpreter's byte_array.size(). Used by lower_call.cpp's
// byte_array SysMember interception (before the rell_sysfn_call soft-fail floor).
llvm::Value *emitByteArraySize(EmitContext &ec, llvm::Value *base);

// lower_ops.cpp — emit a native byte_array content-equality (EQ / NE): spills both already-lowered
// BYTEARRAY operands, calls the sret-style rell_bytearray_eq(out, &a, &b) helper, and returns a BOOLEAN
// runtime value (negated for NE in the IR). Bit-exact with Rt_ByteArrayValue.equals (contentEquals).
llvm::Value *emitByteArrayEq(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool negated);

// lower_ops.cpp — emit a native byte_array UNSIGNED lexicographic comparison sign in {-1,0,1}: spills
// both already-lowered BYTEARRAY operands and calls the scalar-returning rell_bytearray_cmp(&a, &b)
// helper (i32). Bit-exact with rt_ops.kt compareByteArrays (Integer.compareUnsigned per byte, then
// length). The CmpInfo path maps the sign to LT/GT/LE/GE via a signed compare against 0.
llvm::Value *emitByteArrayCompareSign(EmitContext &ec, llvm::Value *a, llvm::Value *b);

// (emitMakeText is a file-local helper in lower_expr.cpp, mirroring emitMakeByteArray — a text CONSTANT
// is only built at its ConstantValueExpr lowering site, so it needs no cross-file declaration here.)

// lower_expr.cpp — emit a native text subscript read (TextSubscriptExpr): spills the already-lowered
// TEXT receiver, calls the sret-style rell_text_get(out, ctx, &base, indexI64) helper (which bounds-checks
// and, on OOB, records the exact interpreter expr_text_subscript_index Rt_Exception into the text-error
// channel), and returns a 1-code-unit TEXT runtime value. Bit-exact with rr_interpreter.kt TextSubscript.
llvm::Value *emitTextGet(EmitContext &ec, llvm::Value *base, llvm::Value *indexI64);

// lower_call.cpp — emit a native text .size() read: spills the already-lowered TEXT receiver, calls the
// sret-style rell_text_size(out, &base) helper, and returns the length (UTF-16 code units) as an INTEGER
// runtime value. Bit-exact with the interpreter's text.size() (String.length). Used by lower_call.cpp's
// text SysMember interception (before the rell_sysfn_call soft-fail floor).
llvm::Value *emitTextSize(EmitContext &ec, llvm::Value *base);

// lower_ops.cpp — emit a native text content-equality (EQ / NE): spills both already-lowered TEXT
// operands, calls the sret-style rell_text_eq(out, &a, &b) helper, and returns a BOOLEAN runtime value
// (negated for NE in the IR). Bit-exact with Rt_TextValue.equals (String content equality).
llvm::Value *emitTextEq(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool negated);

// lower_ops.cpp — emit a native text String.compareTo sign in {-1,0,1}: spills both already-lowered TEXT
// operands and calls the scalar-returning rell_text_cmp(&a, &b) helper (i32). Bit-exact with rt_ops.kt
// R_CmpType_Text (value.compareTo: UNSIGNED 16-bit code-unit order, then length). The CmpInfo path maps
// the sign to LT/GT/LE/GE via a signed compare against 0.
llvm::Value *emitTextCompareSign(EmitContext &ec, llvm::Value *a, llvm::Value *b);

// lower_ops.cpp — emit a native composite/collection structural equality (EQ / NE): spills both
// already-lowered COMPOSITE (tuple/struct) / LIST operands and calls the scalar-returning
// rell_value_equals(&a, &b) helper (i32 in {0,1}; calls rell_jit_escape on any unprovable element so
// the JVM re-runs the whole call). Returns a BOOLEAN runtime value (negated for NE in the IR).
// Bit-exact with Rt_Value.equals for tuple/struct/list (set/map operands are opaque HANDLEs and the
// helper escapes to the interpreter). Mirrors emitByteArrayEq / emitTextEq.
llvm::Value *emitCompositeCollectionEq(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool negated);

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

// Scale-aware decimal comparison for the CmpInfo path (lower_ops.cpp). Caller has tag-guarded both
// operands as DEC_LONG and unpacked their mantissa + scale. Returns an i32 sign in {-1,0,1}
// (Long.compareTo semantics); on a runtime scale-alignment overflow it calls rell_jit_escape and the
// sign is meaningless (discarded JVM-side). Bit-exact with Rt_Comparator (BigDecimal.compareTo).
llvm::Value *emitDecimalCompareSign(EmitContext &ec, llvm::Value *aMant, llvm::Value *aScale,
                                    llvm::Value *bMant, llvm::Value *bScale);
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
