// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// lower_ops.cpp — BinaryExpr / UnaryExpr lowering for the Rell LLVM backend.
//
// This is the arithmetic/logical/comparison hot path. It dispatches each BinaryExpr and
// UnaryExpr to the appropriate inline IR sequence or intrinsic overlay, and routes every
// case it cannot reproduce bit-exactly to the JVM (either via the universal stdlib caller
// or a function-level soft-fail). Mirrors jni_bridge.cpp's lowerBinary style: defensive
// null-checks, ec.fail() with a reason string, never emit IR after a failure.
//
// Coverage map (inline = native IR; intrinsic = intrinsics_* overlay, may itself escape;
// soft-fail = whole function falls back to Rt_InterpreterImpl):
//
//   BinaryExpr, comparison (bin.cmp() != nullptr):
//     CmpType INTEGER/ROWID/BOOLEAN             -> inline icmp on the i64 payload
//     CmpType ENUM                              -> inline signed icmp on the Int ordinal (payload.i64)
//                                                  (bit-exact with rt_ops.kt R_CmpType_Enum; operands
//                                                  are statically the same enum type)
//     CmpType BIG_INTEGER                       -> inline signed i64 cmp on the BIGINT_LONG payload
//                                                  (wide HANDLE operand escapes -> interpreter)
//     CmpType DECIMAL                           -> scale-aware DEC_LONG compare (1.0 == 1.00);
//                                                  wide HANDLE / alignment overflow escapes
//     CmpType TEXT/BYTE_ARRAY/ENTITY            -> soft-fail (type-specific comparators not exposed
//                                                  as a callable sysfn name)
//   BinaryExpr, arithmetic/logical (bin.op()):
//     ADD/SUB/MUL/DIV/MOD_INTEGER               -> intrinsicInteger (checked overflow)
//     ADD/SUB/MUL/DIV/MOD_BIG_INTEGER           -> intrinsicBigInteger (envelope-gated)
//     ADD/SUB/MUL/DIV/MOD_DECIMAL               -> intrinsicDecimal   (envelope-gated)
//     CONCAT_TEXT / CONCAT_BYTE_ARRAY           -> intrinsicText / intrinsicByteArray
//     AND / OR                                  -> inline short-circuit (BBs + phi)
//     EQ / NE                                   -> inline equality: i64 icmp for INTEGER/ROWID/
//                                                  BOOLEAN/ENUM/BIG_INTEGER, scale-aware compare for
//                                                  DECIMAL; NULL/UNIT and handle-backed kinds
//                                                  soft-fail; wide decimal/bigint escape
//     EQ_REF / NE_REF                           -> soft-fail (reference identity)
//     CONCAT_LIST / IN_* / SUB_LIST / SUB_SET / -> soft-fail (collection/range ops; reach the
//             UNION_SET / INTERSECT_* / MERGE_MAP   JVM through the stdlib caller elsewhere)
//   UnaryExpr (un.op()):
//     NOT                                       -> inline boolean negate
//     MINUS_INTEGER                             -> inline checked negate
//     MINUS_BIG_INTEGER / MINUS_DECIMAL         -> intrinsic (0 - x) via the binary overlay
//
// DETERMINISM: every arithmetic intrinsic is responsible for matching the JVM's
// LongMath.checked* / Lib_DecimalMath / Lib_BigIntegerMath semantics; when an operand or
// result leaves the long-fitting envelope the intrinsic sets *escaped and we emit the
// rell_sysfn_call slow path, or — when no callable sysfn name is in scope here — soft-fail.

#include "rell_runtime.h"

#include <llvm/IR/Intrinsics.h>

namespace ir = rell::ir;

namespace rell::llvm_rt {

// Defined in intrinsics_integer.cpp. Checked unary negate: `-x` records the exact
// Rt_Exception code (expr:-:overflow:<v>) into the integer-error channel on x == Long.MIN and
// returns the (JVM-discarded) wrapped value. NOT the same as intrinsicInteger(SUB, 0, v): that
// would record a binary code (expr:-:overflow:0:<v>), diverging from evaluateUnaryOp.
llvm::Value *emitIntegerUnaryMinus(EmitContext &ec, llvm::Value *v);

namespace {

// ---- small helpers mirroring jni_bridge's defensive style -----------------------------

bool isPrimitiveKind(const ir::Type *type, ir::PrimitiveTypeKind kind) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    return prim != nullptr && prim->kind() == kind;
}

bool isBooleanType(const ir::Type *type) {
    return isPrimitiveKind(type, ir::PrimitiveTypeKind_BOOLEAN);
}

// Lower a sub-expression through the shared Expr dispatcher (lower_expr.cpp). Returns the
// runtime-value SSA ({ i8, i32, i64 }) or nullptr after ec.fail().
llvm::Value *lowerOperand(EmitContext &ec, const ir::Expr *expr) {
    if (expr == nullptr) return ec.failExpr("BinaryExpr/UnaryExpr has null operand");
    return lowerExpr(ec, *expr);
}

// Extract the i64 payload from a runtime-value SSA (field 2 of { i8, i32, i64 }).
llvm::Value *payloadI64(EmitContext &ec, llvm::Value *runtimeValue) {
    return ec.unpackPayloadI64(runtimeValue);
}

// DETERMINISM (H3): the inline payload-icmp paths (lowerComparison/lowerEquality) trust the static
// type gate to guarantee the lowered operand's runtime tag is one of the i64-backed inline tags
// (INTEGER/ROWID/BOOLEAN). A mis-lowered sub-expression that produced a HANDLE-tagged value would
// otherwise be compared as a raw jobject pointer — exactly the C1 class of bug. In DEBUG builds we
// emit a guardrail: assert the unpacked tag matches the expected inline tag and llvm.trap()
// otherwise, so a lowering bug aborts at the point of emission's runtime rather than silently
// diverging. In release builds this is a no-op (NDEBUG), keeping the hot path branch-free.
//
// INVARIANT (release builds rely on this): lowerExpr MUST preserve the operand's static inline tag.
// Any mismatch is a lowering bug; the correct response upstream is to soft-fail, not to icmp.
void assertInlineTag(EmitContext &ec, llvm::Value *runtimeValue, RellTag expected) {
#ifndef NDEBUG
    auto &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();
    llvm::Value *tag = ec.unpackTag(runtimeValue);  // i8
    llvm::Value *ok = b.CreateICmpEQ(
        tag, b.getInt8(static_cast<uint8_t>(expected)), "tag_ok");
    auto *contBB = llvm::BasicBlock::Create(ctx, "tag_cont", fn);
    auto *trapBB = llvm::BasicBlock::Create(ctx, "tag_trap", fn);
    b.CreateCondBr(ok, contBB, trapBB);
    b.SetInsertPoint(trapBB);
    llvm::Function *trap =
        llvm::Intrinsic::getOrInsertDeclaration(&ec.module(), llvm::Intrinsic::trap);
    b.CreateCall(trap, {});
    b.CreateUnreachable();
    b.SetInsertPoint(contBB);
#else
    (void)ec;
    (void)runtimeValue;
    (void)expected;
#endif
}

// Map an inline-comparable CmpType to the RellTag its operands must carry.
RellTag cmpTypeInlineTag(ir::CmpType t) {
    switch (t) {
        case ir::CmpType_INTEGER: return RellTag::INTEGER;
        case ir::CmpType_ROWID:   return RellTag::ROWID;
        case ir::CmpType_BOOLEAN: return RellTag::BOOLEAN;
        case ir::CmpType_ENUM:    return RellTag::ENUM;
        default:                  return RellTag::NONE;  // never reached: caller gates cmp_type
    }
}

// =====================================================================================
// Comparisons (BinaryExpr with a non-null CmpInfo).
//
// The i64-backed inline tags compare with a plain signed icmp: INTEGER (signed i64), ROWID
// (i64 >= 0), BOOLEAN (0/1), and BIG_INTEGER's inline BIGINT_LONG slice (exact i64, |v| <= Long.MAX).
// The Rell comparator for these is the natural signed integer order, which icmp reproduces exactly.
// DECIMAL uses a SCALE-AWARE compare (emitDecimalCompareSign): DEC_LONG carries (mantissa, scale),
// and the order is NOT the raw mantissa order, so mantissas are aligned by 10^scaleDelta before the
// signed compare; from_jvm hands stripped canonical forms so 1.0 vs 1.00 compare EQUAL (both (1,0)).
//
// RUNTIME ESCAPE: a wide decimal/big_integer operand is a HANDLE at runtime; the per-type tag guard
// (emitEscapeIfNotTag) escapes those to the interpreter, as does a decimal scale-alignment overflow.
//
// ENUM is inline-comparable: from_jvm cracks an enum to the ENUM tag carrying the Int ordinal in
// payload.i64, so a signed icmp on the ordinal is bit-exact with rt_ops.kt R_CmpType_Enum
// (rrAttr.value.compareTo) — both operands are statically the SAME enum type (compiler-guaranteed).
// TEXT/BYTE_ARRAY/ENTITY comparison is a type-specific comparator with no callable sysfn name
// reachable here, so those soft-fail.
// =====================================================================================

llvm::Value *lowerComparison(EmitContext &ec, const ir::BinaryExpr &bin, const ir::CmpInfo &cmp) {
    const ir::CmpType cmpType = cmp.cmp_type();
    switch (cmpType) {
        case ir::CmpType_INTEGER:
        case ir::CmpType_ROWID:
        case ir::CmpType_BOOLEAN:
        case ir::CmpType_BIG_INTEGER:
        case ir::CmpType_DECIMAL:
        case ir::CmpType_ENUM:
            break;  // inline-comparable below (i64 payload, or DEC_LONG scale-aware)
        case ir::CmpType_BYTE_ARRAY:
            break;  // native UNSIGNED lexicographic compare (BYTEARRAY carrier), handled below.
        case ir::CmpType_TEXT:
            break;  // native String.compareTo (TEXT carrier, UNSIGNED code-unit order), handled below.
        case ir::CmpType_ENTITY:
            // DETERMINISM: entity comparison is a type-specific comparator with no callable sysfn name
            // reachable here. Soft-fail. (ENUM is inline — Int ordinal; BYTE_ARRAY/TEXT are native — the
            // carrier exposes the bytes/code-units for the exact compare, see below.)
            return ec.failExpr("comparison cmp_type routes to JVM interpreter: " +
                               std::to_string(static_cast<int>(cmpType)));
        default:
            return ec.failExpr("unknown CmpType: " + std::to_string(static_cast<int>(cmpType)));
    }

    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;

    auto &b = ec.builder();

    // For BIG_INTEGER / DECIMAL the operands may be wide HANDLEs at runtime (the static gate only
    // proves the static type). Tag-guard them: a wrong tag escapes to the interpreter. INTEGER/ROWID/
    // BOOLEAN never escape to a HANDLE (value.cpp), so they keep the debug-only assertInlineTag.
    llvm::Value *cmpBit = nullptr;
    if (cmpType == ir::CmpType_DECIMAL) {
        // REVIEW_coverage target 2: scale-aware DEC_LONG compare. emitDecimalCompareSign aligns
        // mantissas (escaping on alignment overflow) and yields an i32 sign in {-1,0,1}. 1.0 vs 1.00
        // compare EQUAL because from_jvm hands stripped canonical forms (both -> (1,0)); the sign is
        // 0. Map the sign to the CmpOp via a signed compare against 0.
        emitEscapeIfNotTag(ec, lhs, RellTag::DEC_LONG);
        emitEscapeIfNotTag(ec, rhs, RellTag::DEC_LONG);
        llvm::Value *sign = emitDecimalCompareSign(ec, payloadI64(ec, lhs), ec.unpackScale(lhs),
                                                   payloadI64(ec, rhs), ec.unpackScale(rhs));
        llvm::Value *z = b.getInt32(0);
        switch (cmp.op()) {
            case ir::CmpOp_LT: cmpBit = b.CreateICmpSLT(sign, z, "cmp_lt"); break;
            case ir::CmpOp_GT: cmpBit = b.CreateICmpSGT(sign, z, "cmp_gt"); break;
            case ir::CmpOp_LE: cmpBit = b.CreateICmpSLE(sign, z, "cmp_le"); break;
            case ir::CmpOp_GE: cmpBit = b.CreateICmpSGE(sign, z, "cmp_ge"); break;
            default:
                return ec.failExpr("unknown CmpOp: " + std::to_string(static_cast<int>(cmp.op())));
        }
    } else if (cmpType == ir::CmpType_BYTE_ARRAY) {
        // Native UNSIGNED lexicographic compare over the BYTEARRAY carriers' bytes. rell_bytearray_cmp
        // returns an i32 sign in {-1,0,1} (bit-exact with rt_ops.kt compareByteArrays:
        // Integer.compareUnsigned per byte, then length). byte_array is never a HANDLE at runtime under
        // the value ABI (from_jvm always cracks Rt_ByteArrayValue to a BYTEARRAY), so no tag guard /
        // escape is needed — the static cmp_type proves both operands are byte_array carriers. Map the
        // sign to the CmpOp via a signed compare against 0, exactly like the DECIMAL sign path.
        llvm::Value *sign = emitByteArrayCompareSign(ec, lhs, rhs);
        llvm::Value *z = b.getInt32(0);
        switch (cmp.op()) {
            case ir::CmpOp_LT: cmpBit = b.CreateICmpSLT(sign, z, "cmp_lt"); break;
            case ir::CmpOp_GT: cmpBit = b.CreateICmpSGT(sign, z, "cmp_gt"); break;
            case ir::CmpOp_LE: cmpBit = b.CreateICmpSLE(sign, z, "cmp_le"); break;
            case ir::CmpOp_GE: cmpBit = b.CreateICmpSGE(sign, z, "cmp_ge"); break;
            default:
                return ec.failExpr("unknown CmpOp: " + std::to_string(static_cast<int>(cmp.op())));
        }
    } else if (cmpType == ir::CmpType_TEXT) {
        // Native String.compareTo over the TEXT carriers' UTF-16 code units. rell_text_cmp returns an i32
        // sign in {-1,0,1} (bit-exact with rt_ops.kt R_CmpType_Text: value.compareTo — first differing
        // code unit compared UNSIGNED, then length). text is never a HANDLE at runtime under the value
        // ABI (from_jvm always cracks Rt_TextValue to a TEXT carrier), so no tag guard / escape is needed
        // — the static cmp_type proves both operands are text carriers. Map the sign to the CmpOp via a
        // signed compare against 0, exactly like the DECIMAL / BYTE_ARRAY sign paths.
        llvm::Value *sign = emitTextCompareSign(ec, lhs, rhs);
        llvm::Value *z = b.getInt32(0);
        switch (cmp.op()) {
            case ir::CmpOp_LT: cmpBit = b.CreateICmpSLT(sign, z, "cmp_lt"); break;
            case ir::CmpOp_GT: cmpBit = b.CreateICmpSGT(sign, z, "cmp_gt"); break;
            case ir::CmpOp_LE: cmpBit = b.CreateICmpSLE(sign, z, "cmp_le"); break;
            case ir::CmpOp_GE: cmpBit = b.CreateICmpSGE(sign, z, "cmp_ge"); break;
            default:
                return ec.failExpr("unknown CmpOp: " + std::to_string(static_cast<int>(cmp.op())));
        }
    } else {
        // INTEGER / ROWID / BOOLEAN / ENUM / BIG_INTEGER: signed i64 compare on the payload. For
        // BIG_INTEGER the payload is the exact i64 of an inline BIGINT_LONG (|v| <= Long.MAX) and the
        // Rell comparator is the natural signed integer order, which icmp reproduces; a wide HANDLE
        // operand escapes via the tag guard. INTEGER/ROWID/BOOLEAN/ENUM are never HANDLEs (value.cpp
        // cracks enums to an inline ENUM tag), so they only carry the debug-only assertInlineTag. ENUM
        // compares the Int ordinal (payload.i64) with a signed icmp — bit-exact with rt_ops.kt
        // R_CmpType_Enum (rrAttr.value.compareTo) since both operands are statically the SAME enum
        // type (compiler-guaranteed), so the typeIdx in `scale` is identical and irrelevant to order.
        if (cmpType == ir::CmpType_BIG_INTEGER) {
            emitEscapeIfNotTag(ec, lhs, RellTag::BIGINT_LONG);
            emitEscapeIfNotTag(ec, rhs, RellTag::BIGINT_LONG);
        } else {
            RellTag expectTag = cmpTypeInlineTag(cmpType);
            assertInlineTag(ec, lhs, expectTag);
            assertInlineTag(ec, rhs, expectTag);
        }
        llvm::Value *a = payloadI64(ec, lhs);
        llvm::Value *c = payloadI64(ec, rhs);
        switch (cmp.op()) {
            case ir::CmpOp_LT: cmpBit = b.CreateICmpSLT(a, c, "cmp_lt"); break;
            case ir::CmpOp_GT: cmpBit = b.CreateICmpSGT(a, c, "cmp_gt"); break;
            case ir::CmpOp_LE: cmpBit = b.CreateICmpSLE(a, c, "cmp_le"); break;
            case ir::CmpOp_GE: cmpBit = b.CreateICmpSGE(a, c, "cmp_ge"); break;
            default:
                return ec.failExpr("unknown CmpOp: " + std::to_string(static_cast<int>(cmp.op())));
        }
    }

    // Pack the i1 result into a BOOLEAN runtime value (payload 0/1).
    llvm::Value *asI64 = b.CreateZExt(cmpBit, b.getInt64Ty(), "cmp_i64");
    return ec.packInline(RellTag::BOOLEAN, b.getInt32(0), asI64);
}

// =====================================================================================
// Equality EQ / NE (BinaryOp, not CmpInfo).
//
// Bit-exact for the i64-backed inline tags whose payload equality coincides with Rell value
// equality: INTEGER, ROWID, BOOLEAN, and ENUM (payload.i64 = Int ordinal; Rt_RR_EnumValue.equals
// keys on ordinal + type name, and operands are statically the same enum type). NULL/UNIT excluded
// (H2: degenerate / Nullable-typed, not icmp-safe).
//
// DETERMINISM: DEC_LONG equality is NOT i64-payload equality (1.5 == 1.50 with different
// mantissa/scale), and HANDLE-backed values (text, byte_array, collections, struct, ...)
// need the JVM's structural equals(). Both soft-fail. The operand TYPE drives the decision:
// we only inline when both operands are statically one of the i64-equality-safe primitives.
// =====================================================================================

// Equality classification of a statically-known operand type. I64_PAYLOAD: payload icmp is bit-exact
// (INTEGER/ROWID/BOOLEAN, BIG_INTEGER's inline i64 slice, and ENUM's Int ordinal). DECIMAL: NOT
// i64-payload equality (1.0 == 1.00) — needs the scale-aware compare (sign == 0). UNSAFE: everything
// else (handle-backed structural equals, nullable) soft-fails.
enum class EqClass { UNSAFE, I64_PAYLOAD, DECIMAL, BYTE_ARRAY, TEXT, COMPOSITE_COLLECTION };

EqClass eqClassOf(const ir::Type *type) {
    if (type == nullptr) return EqClass::UNSAFE;
    // Enum: now carried inline (ENUM tag, payload.i64 = Int ordinal). Equality is ordinal equality —
    // Rt_RR_EnumValue.equals compares rrAttr.value (ordinal) AND typeName, and both operands are
    // statically the SAME enum type (compiler-guaranteed), so an i64-payload icmp on the ordinal is
    // bit-exact. (C1 fix: previously enum was HANDLE-backed and soft-failed here.)
    if (type->type_type() == ir::TypeUnion_EnumType) return EqClass::I64_PAYLOAD;
    // Composite (tuple/struct) and homogeneous collections (list/set/map): structural equality via the
    // native rell_value_equals walk over the COMPOSITE/LIST reps (set/map operands stay opaque HANDLEs,
    // so the helper escapes the WHOLE comparison to the interpreter — see rell_value_equals). The
    // operand static type only selects this class; the runtime decides native-vs-escape per element.
    // Bit-exact with Rt_Value.equals (tuple/struct field-wise, list/set/map element-wise) or a faithful
    // fall-back. STRUCT/TUPLE/LIST round-trip natively; SET/MAP route through the back-call.
    switch (type->type_type()) {
        case ir::TypeUnion_StructType:
        case ir::TypeUnion_TupleType:
        case ir::TypeUnion_ListType:
        case ir::TypeUnion_SetType:
        case ir::TypeUnion_MapType:
            return EqClass::COMPOSITE_COLLECTION;
        default:
            break;
    }
    const auto *prim = type->type_as_PrimitiveType();
    if (prim == nullptr) {
        return EqClass::UNSAFE;
    }
    switch (prim->kind()) {
        case ir::PrimitiveTypeKind_INTEGER:
        case ir::PrimitiveTypeKind_ROWID:
        case ir::PrimitiveTypeKind_BOOLEAN:
            return EqClass::I64_PAYLOAD;
        case ir::PrimitiveTypeKind_BIG_INTEGER:
            // The inline BIGINT_LONG payload is the exact i64; equality == i64 equality. A wide
            // HANDLE operand escapes via the runtime tag guard in lowerEquality.
            return EqClass::I64_PAYLOAD;
        case ir::PrimitiveTypeKind_DECIMAL:
            return EqClass::DECIMAL;
        case ir::PrimitiveTypeKind_BYTE_ARRAY:
            // byte_array == / != is native content equality (the BYTEARRAY carrier exposes the bytes);
            // bit-exact with Rt_ByteArrayValue.equals (contentEquals).
            return EqClass::BYTE_ARRAY;
        case ir::PrimitiveTypeKind_TEXT:
            // text == / != is native code-unit content equality (the TEXT carrier exposes the UTF-16
            // units); bit-exact with Rt_TextValue.equals (String content equality).
            return EqClass::TEXT;
        default:
            // DETERMINISM (H2): UNIT/NULL omitted — unit==unit is degenerate/dead and null-eq is a
            // Nullable operand that is not i64-payload comparable.
            return EqClass::UNSAFE;
    }
}

// Equality class of an OPERAND expression. Normally driven by the operand's static result type
// (eqClassOf), but a StructExpr constructor `S(...)` carries NO Type table (only a struct_def_index),
// so exprStaticResultType returns nullptr for it. Such an operand still produces a struct COMPOSITE at
// runtime (lowerStructExpr), so classify it by expr shape: a StructExpr is COMPOSITE_COLLECTION. (A
// TupleExpr / ListLiteralExpr DO carry a Type, so the type-based eqClassOf already classifies them.)
EqClass eqClassOfOperand(const ir::Expr *expr) {
    EqClass byType = eqClassOf(exprStaticResultType(expr));
    if (byType != EqClass::UNSAFE) return byType;
    if (expr != nullptr && expr->expr_type() == ir::ExprUnion_StructExpr) {
        return EqClass::COMPOSITE_COLLECTION;
    }
    return EqClass::UNSAFE;
}

// The RellTag an i64-payload-equality operand carries at runtime (for the tag guard / assert).
RellTag eqI64InlineTag(const ir::Type *type) {
    if (type != nullptr && type->type_type() == ir::TypeUnion_EnumType) return RellTag::ENUM;
    const auto *prim = type != nullptr ? type->type_as_PrimitiveType() : nullptr;
    if (prim == nullptr) return RellTag::NONE;
    switch (prim->kind()) {
        case ir::PrimitiveTypeKind_INTEGER:     return RellTag::INTEGER;
        case ir::PrimitiveTypeKind_ROWID:       return RellTag::ROWID;
        case ir::PrimitiveTypeKind_BOOLEAN:     return RellTag::BOOLEAN;
        case ir::PrimitiveTypeKind_BIG_INTEGER: return RellTag::BIGINT_LONG;
        default:                                return RellTag::NONE;
    }
}

llvm::Value *lowerEquality(EmitContext &ec, const ir::BinaryExpr &bin, bool negated) {
    const ir::Expr *left = bin.left();
    const ir::Expr *right = bin.right();
    if (left == nullptr || right == nullptr) {
        return ec.failExpr("equality has null operand");
    }

    // Gate on operand static types (eqClassOf): INTEGER/ROWID/BOOLEAN/BIG_INTEGER take the i64-
    // payload icmp; DECIMAL takes the scale-aware compare (1.0 == 1.00); everything else (nullable,
    // handle-backed structural equals) soft-fails. DETERMINISM (H1): we read the static result type
    // off each operand via exprStaticResultType — this is the compile-time type the frontend resolved
    // for the carrier, NOT a runtime tag, so it is identical for a VarExpr and the constant/binary/
    // call/member shapes the helper covers (e.g. `x == 0` reads INTEGER off the literal's TypedValue).
    // An operand whose variant the helper cannot type yields nullptr -> UNSAFE, so the equality
    // SOFT-FAILS to the JVM equals(). Both operands must share the SAME class (a mixed pair soft-
    // fails). DEC_LONG / BIGINT_LONG wide values are HANDLEs at runtime and escape via the per-class
    // tag guard below regardless of the operand shape. NULL/UNIT stay excluded (H2).
    const ir::Type *lt = exprStaticResultType(left);
    const ir::Type *rt = exprStaticResultType(right);
    // Classify by operand expr (type-based, falling back to expr shape for a StructExpr constructor,
    // which carries no Type table — see eqClassOfOperand). lt/rt are still used by the I64 branch's
    // eqI64InlineTag, where the operand type is always present (a StructExpr is never an I64 class).
    const EqClass lClass = eqClassOfOperand(left);
    const EqClass rClass = eqClassOfOperand(right);
    if (lClass == EqClass::UNSAFE || rClass != lClass) {
        // DETERMINISM: cannot prove both operands share an inline-equality-safe class; structural
        // equality (and mixed classes) must run on the JVM. Soft-fail.
        return ec.failExpr("equality operand types not inline-equality-safe");
    }

    llvm::Value *lhs = lowerOperand(ec, left);
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, right);
    if (rhs == nullptr) return nullptr;

    auto &b = ec.builder();
    llvm::Value *eq = nullptr;

    if (lClass == EqClass::BYTE_ARRAY) {
        // Native content equality (Rt_ByteArrayValue.equals == contentEquals). emitByteArrayEq returns
        // a BOOLEAN runtime value directly (negated for NE), so return it as-is.
        return emitByteArrayEq(ec, lhs, rhs, negated);
    }

    if (lClass == EqClass::TEXT) {
        // Native code-unit content equality (Rt_TextValue.equals == String content equality). emitTextEq
        // returns a BOOLEAN runtime value directly (negated for NE), so return it as-is. text is never a
        // HANDLE at runtime under the value ABI (from_jvm always cracks Rt_TextValue to a TEXT carrier).
        return emitTextEq(ec, lhs, rhs, negated);
    }

    if (lClass == EqClass::COMPOSITE_COLLECTION) {
        // Structural equality over the native COMPOSITE (tuple/struct) / LIST reps via the recursive
        // rell_value_equals helper. A set/map operand is an opaque HANDLE at runtime, and any HANDLE
        // element (set/map/gtv/json/virtual/range/wide numeric/bare tuple) makes the helper call
        // rell_jit_escape so the WHOLE call re-runs on the interpreter (bit-exact) — never a wrong
        // result. emitCompositeCollectionEq returns a BOOLEAN runtime value directly (negated for NE).
        return emitCompositeCollectionEq(ec, lhs, rhs, negated);
    }

    if (lClass == EqClass::DECIMAL) {
        // DETERMINISM: DEC_LONG equality is value equality, not payload equality (1.0 == 1.00). Use
        // the scale-aware compare: equal iff sign == 0. from_jvm hands stripped canonical forms, so
        // equal values share (mantissa, scale) and the same-scale branch suffices; the scale-aware
        // path is correct regardless. Wide HANDLE operands escape via the tag guard.
        emitEscapeIfNotTag(ec, lhs, RellTag::DEC_LONG);
        emitEscapeIfNotTag(ec, rhs, RellTag::DEC_LONG);
        llvm::Value *sign = emitDecimalCompareSign(ec, payloadI64(ec, lhs), ec.unpackScale(lhs),
                                                   payloadI64(ec, rhs), ec.unpackScale(rhs));
        llvm::Value *z = b.getInt32(0);
        eq = negated ? b.CreateICmpNE(sign, z, "ne") : b.CreateICmpEQ(sign, z, "eq");
    } else {
        // I64_PAYLOAD: INTEGER/ROWID/BOOLEAN/ENUM (never HANDLEs; debug-only assert) or BIG_INTEGER
        // (wide operands escape via the tag guard; the inline i64 slice's equality == i64 equality).
        // ENUM carries the Int ordinal in payload.i64; ordinal equality == enum value equality for
        // statically same-typed operands (Rt_RR_EnumValue.equals: ordinal + typeName), bit-exact.
        const RellTag tag = eqI64InlineTag(lt);
        if (tag == RellTag::BIGINT_LONG) {
            emitEscapeIfNotTag(ec, lhs, RellTag::BIGINT_LONG);
            emitEscapeIfNotTag(ec, rhs, RellTag::BIGINT_LONG);
        } else {
            // H3: debug-only guard that each lowered operand carries the promised inline tag (no-op
            // in release). Catches a mis-lowered HANDLE-tagged operand before a raw-pointer compare.
            assertInlineTag(ec, lhs, tag);
            assertInlineTag(ec, rhs, tag);
        }
        llvm::Value *a = payloadI64(ec, lhs);
        llvm::Value *c = payloadI64(ec, rhs);
        eq = negated ? b.CreateICmpNE(a, c, "ne") : b.CreateICmpEQ(a, c, "eq");
    }

    llvm::Value *asI64 = b.CreateZExt(eq, b.getInt64Ty(), "eq_i64");
    return ec.packInline(RellTag::BOOLEAN, b.getInt32(0), asI64);
}

// =====================================================================================
// Short-circuit AND / OR.
//
// Rell `and`/`or` are short-circuiting on booleans (no null operands here — nullable boolean
// logic is a different lowering path). We materialise the left value, branch on its payload,
// and only evaluate the right in the branch that needs it, then phi the boolean result.
//
// DETERMINISM: short-circuit means the right operand's side effects must not run when the
// left decides the result — exactly mirrored by the conditional branch below.
// =====================================================================================

llvm::Value *lowerShortCircuit(EmitContext &ec, const ir::BinaryExpr &bin, bool isAnd) {
    if (!isBooleanType(bin.type())) {
        return ec.failExpr("AND/OR result type is not boolean");
    }
    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;

    auto &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();

    // left as i1
    llvm::Value *leftBit =
        b.CreateICmpNE(payloadI64(ec, lhs), b.getInt64(0), isAnd ? "and_l" : "or_l");
    llvm::BasicBlock *entryBB = b.GetInsertBlock();
    llvm::BasicBlock *rhsBB = llvm::BasicBlock::Create(ctx, isAnd ? "and_rhs" : "or_rhs", fn);
    llvm::BasicBlock *contBB = llvm::BasicBlock::Create(ctx, isAnd ? "and_cont" : "or_cont", fn);

    // AND: if left is true, evaluate right; else result is false (skip right).
    // OR:  if left is false, evaluate right; else result is true (skip right).
    if (isAnd) {
        b.CreateCondBr(leftBit, rhsBB, contBB);
    } else {
        b.CreateCondBr(leftBit, contBB, rhsBB);
    }

    // rhs block
    b.SetInsertPoint(rhsBB);
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;
    llvm::Value *rightBit = b.CreateICmpNE(payloadI64(ec, rhs), b.getInt64(0), "rhs_bit");
    llvm::BasicBlock *rhsEndBB = b.GetInsertBlock();  // lowering right may have added blocks
    b.CreateBr(contBB);

    // cont block: phi the short-circuit constant from entry with the evaluated right bit.
    b.SetInsertPoint(contBB);
    llvm::PHINode *phi = b.CreatePHI(b.getInt1Ty(), 2, isAnd ? "and_res" : "or_res");
    // Constant from the entry edge: AND short-circuits to false, OR short-circuits to true.
    phi->addIncoming(b.getInt1(isAnd ? false : true), entryBB);
    phi->addIncoming(rightBit, rhsEndBB);

    llvm::Value *asI64 = b.CreateZExt(phi, b.getInt64Ty(), "sc_i64");
    return ec.packInline(RellTag::BOOLEAN, b.getInt32(0), asI64);
}

// =====================================================================================
// Intrinsic-routed arithmetic (integer / big_integer / decimal / concat).
//
// Each overlay receives the unpacked operands and may set *escaped to request the slow path.
// Today the slow path here is a soft-fail: the overlay-level rell_sysfn_call wiring belongs to
// lower_call.cpp (which holds the SysFnId for the corresponding stdlib operator). When an
// overlay escapes, we soft-fail the whole function so the JVM interpreter runs it bit-exactly.
//
// DETERMINISM: escape => we have NO bit-exact inline result, so we must not emit any IR for
// the result. Soft-fail is the only correct action without a resolved SysFnId in scope.
// =====================================================================================

llvm::Value *lowerIntegerArith(EmitContext &ec, const ir::BinaryExpr &bin) {
    // DETERMINISM: match the i64 oracle (jni_bridge.cpp Lowerer::applyIntArith) EXACTLY. The oracle
    // lowers only the wrapping/checked ADD/SUB/MUL family inline and soft-fails DIV/MOD to the
    // interpreter (Llvm_IntCoverageTest's `div`/`mod safely soft-fails` asserts jitHits==0). The
    // value path MUST make the same decision so the unchanged coverage tests pass and no path
    // diverges from the known-good i64 behaviour. div/mod still run bit-exactly — on the JVM.
    if (bin.op() == ir::BinaryOp_DIV_INTEGER || bin.op() == ir::BinaryOp_MOD_INTEGER) {
        return ec.failExpr("integer div/mod soft-fails to the interpreter (oracle parity)");
    }
    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;
    bool escaped = false;
    llvm::Value *res =
        intrinsicInteger(ec, bin.op(), payloadI64(ec, lhs), payloadI64(ec, rhs), &escaped);
    if (escaped) {
        return ec.failExpr("integer arithmetic escaped inline envelope");
    }
    return res;  // nullptr already implies ec.fail() inside the intrinsic.
}

llvm::Value *lowerBigIntegerArith(EmitContext &ec, const ir::BinaryExpr &bin) {
    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;
    // RUNTIME tag guard: a big_integer operand whose value exceeds i64 is a HANDLE at runtime, not an
    // inline BIGINT_LONG. The static type gate only proves the STATIC type is big_integer; the wide
    // slice escapes (rell_jit_escape -> the JVM re-runs the call). Without this, unpacking the i64
    // payload of a HANDLE would read a jobject pointer as a number (a consensus split).
    emitEscapeIfNotTag(ec, lhs, RellTag::BIGINT_LONG);
    emitEscapeIfNotTag(ec, rhs, RellTag::BIGINT_LONG);
    bool escaped = false;
    llvm::Value *res =
        intrinsicBigInteger(ec, bin.op(), payloadI64(ec, lhs), payloadI64(ec, rhs), &escaped);
    if (escaped) {
        return ec.failExpr("big_integer arithmetic escaped inline envelope");
    }
    return res;
}

llvm::Value *lowerDecimalArith(EmitContext &ec, const ir::BinaryExpr &bin) {
    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;
    // RUNTIME tag guard: a decimal operand outside the long-fit envelope is a HANDLE at runtime, not
    // an inline DEC_LONG. Escape the wide slice to the interpreter (see lowerBigIntegerArith). The
    // mantissa/scale unpack below is valid only after this guard proves DEC_LONG.
    emitEscapeIfNotTag(ec, lhs, RellTag::DEC_LONG);
    emitEscapeIfNotTag(ec, rhs, RellTag::DEC_LONG);
    bool escaped = false;
    llvm::Value *res = intrinsicDecimal(ec, bin.op(), payloadI64(ec, lhs), ec.unpackScale(lhs),
                                        payloadI64(ec, rhs), ec.unpackScale(rhs), &escaped);
    if (escaped) {
        return ec.failExpr("decimal arithmetic escaped inline envelope");
    }
    return res;
}

llvm::Value *lowerConcat(EmitContext &ec, const ir::BinaryExpr &bin, bool isText) {
    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;
    bool escaped = false;
    llvm::Value *res = isText ? intrinsicText(ec, bin.op(), lhs, rhs, &escaped)
                              : intrinsicByteArray(ec, bin.op(), lhs, rhs, &escaped);
    if (escaped) {
        return ec.failExpr(isText ? "text concat escaped inline envelope"
                                  : "byte_array concat escaped inline envelope");
    }
    return res;
}

}  // namespace

// =====================================================================================
// Native byte_array EQ/NE and comparison emit helpers (declared in rell_runtime.h §9). External
// linkage so lower_ops's equality/comparison dispatch reaches them; spill both BYTEARRAY operands into
// stack slots and call the sret-style / scalar-returning runtime helpers. Bit-exact with the
// interpreter (Rt_ByteArrayValue.equals == contentEquals; rt_ops.kt compareByteArrays == UNSIGNED
// lexicographic then length).
// =====================================================================================

namespace {
// Spill a runtime value into a fresh entry-block stack slot and return the slot pointer.
llvm::Value *spillToSlot(EmitContext &ec, llvm::Value *v, const char *name) {
    llvm::IRBuilder<> &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *slot = entryB.CreateAlloca(ec.valueType(), nullptr, name);
    b.CreateStore(v, slot);
    return slot;
}
}  // namespace

llvm::Value *emitByteArrayEq(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool negated) {
    llvm::IRBuilder<> &ib = ec.builder();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    llvm::Value *aSlot = spillToSlot(ec, a, "ba_eq_a");
    llvm::Value *bSlot = spillToSlot(ec, b, "ba_eq_b");
    llvm::Function *fn = ib.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *outSlot = entryB.CreateAlloca(ec.valueType(), nullptr, "ba_eq_out");

    // void rell_bytearray_eq(ptr out, ptr a, ptr b) -> out is a BOOLEAN runtime value.
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), {ptrTy, ptrTy, ptrTy},
                                         /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_bytearray_eq", fnTy);
    ib.CreateCall(callee, {outSlot, aSlot, bSlot});
    llvm::Value *eqVal = ib.CreateLoad(ec.valueType(), outSlot, "ba_eq");
    if (!negated) return eqVal;
    // NE: negate the BOOLEAN payload (0/1) in IR (xor 1), repacking as a BOOLEAN runtime value.
    llvm::Value *payload = ec.unpackPayloadI64(eqVal);
    llvm::Value *flipped = ib.CreateXor(payload, ib.getInt64(1), "ba_ne");
    return ec.packInline(RellTag::BOOLEAN, ib.getInt32(0), flipped);
}

llvm::Value *emitByteArrayCompareSign(EmitContext &ec, llvm::Value *a, llvm::Value *b) {
    llvm::IRBuilder<> &ib = ec.builder();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    llvm::Value *aSlot = spillToSlot(ec, a, "ba_cmp_a");
    llvm::Value *bSlot = spillToSlot(ec, b, "ba_cmp_b");
    // int32_t rell_bytearray_cmp(ptr a, ptr b) -> sign in {-1,0,1}.
    auto *fnTy = llvm::FunctionType::get(ib.getInt32Ty(), {ptrTy, ptrTy}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_bytearray_cmp", fnTy);
    return ib.CreateCall(callee, {aSlot, bSlot}, "ba_cmp");
}

// =====================================================================================
// Native text EQ/NE and comparison emit helpers (declared in rell_runtime.h §9). Same shape as the
// byte_array helpers: spill both TEXT operands into stack slots and call the sret-style / scalar-
// returning runtime helpers. Bit-exact with the interpreter (Rt_TextValue.equals == String content
// equality; rt_ops.kt R_CmpType_Text == value.compareTo, UNSIGNED 16-bit code-unit order then length).
// =====================================================================================

llvm::Value *emitTextEq(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool negated) {
    llvm::IRBuilder<> &ib = ec.builder();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    llvm::Value *aSlot = spillToSlot(ec, a, "txt_eq_a");
    llvm::Value *bSlot = spillToSlot(ec, b, "txt_eq_b");
    llvm::Function *fn = ib.GetInsertBlock()->getParent();
    llvm::IRBuilder<> entryB(&fn->getEntryBlock(), fn->getEntryBlock().begin());
    llvm::Value *outSlot = entryB.CreateAlloca(ec.valueType(), nullptr, "txt_eq_out");

    // void rell_text_eq(ptr out, ptr a, ptr b) -> out is a BOOLEAN runtime value.
    auto *fnTy = llvm::FunctionType::get(llvm::Type::getVoidTy(ec.ctx()), {ptrTy, ptrTy, ptrTy},
                                         /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_text_eq", fnTy);
    ib.CreateCall(callee, {outSlot, aSlot, bSlot});
    llvm::Value *eqVal = ib.CreateLoad(ec.valueType(), outSlot, "txt_eq");
    if (!negated) return eqVal;
    // NE: negate the BOOLEAN payload (0/1) in IR (xor 1), repacking as a BOOLEAN runtime value.
    llvm::Value *payload = ec.unpackPayloadI64(eqVal);
    llvm::Value *flipped = ib.CreateXor(payload, ib.getInt64(1), "txt_ne");
    return ec.packInline(RellTag::BOOLEAN, ib.getInt32(0), flipped);
}

llvm::Value *emitTextCompareSign(EmitContext &ec, llvm::Value *a, llvm::Value *b) {
    llvm::IRBuilder<> &ib = ec.builder();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    llvm::Value *aSlot = spillToSlot(ec, a, "txt_cmp_a");
    llvm::Value *bSlot = spillToSlot(ec, b, "txt_cmp_b");
    // int32_t rell_text_cmp(ptr a, ptr b) -> sign in {-1,0,1}.
    auto *fnTy = llvm::FunctionType::get(ib.getInt32Ty(), {ptrTy, ptrTy}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_text_cmp", fnTy);
    return ib.CreateCall(callee, {aSlot, bSlot}, "txt_cmp");
}

// =====================================================================================
// Native composite/collection EQ/NE emit helper (rell_runtime.h §9). Spills both already-lowered
// COMPOSITE (tuple/struct) / LIST operands into stack slots and calls the scalar-returning
// rell_value_equals(&a, &b) helper (i32 in {0,1}; calls rell_jit_escape on any opaque HANDLE element
// so the whole call re-runs on the interpreter, bit-exact). Maps the i32 to a BOOLEAN runtime value,
// negated for NE. Same spill-and-call shape as emitByteArrayEq / emitTextEq.
// =====================================================================================

llvm::Value *emitCompositeCollectionEq(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool negated) {
    llvm::IRBuilder<> &ib = ec.builder();
    auto *ptrTy = llvm::PointerType::getUnqual(ec.ctx());
    llvm::Value *aSlot = spillToSlot(ec, a, "eq_a");
    llvm::Value *bSlot = spillToSlot(ec, b, "eq_b");
    // int32_t rell_value_equals(ptr a, ptr b) -> 1 equal / 0 not-equal-or-escaped.
    auto *fnTy = llvm::FunctionType::get(ib.getInt32Ty(), {ptrTy, ptrTy}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_value_equals", fnTy);
    llvm::Value *eqI32 = ib.CreateCall(callee, {aSlot, bSlot}, "rv_eq");
    llvm::Value *zero = ib.getInt32(0);
    // NE: result != 0 maps "equal(1)" to false. EQ: result != 0 maps "equal(1)" to true.
    llvm::Value *bit = negated ? ib.CreateICmpEQ(eqI32, zero, "rv_ne")
                               : ib.CreateICmpNE(eqI32, zero, "rv_eq_bit");
    llvm::Value *asI64 = ib.CreateZExt(bit, ib.getInt64Ty(), "rv_eq_i64");
    return ec.packInline(RellTag::BOOLEAN, ib.getInt32(0), asI64);
}

// =====================================================================================
// BinaryExpr entry point.
// =====================================================================================

llvm::Value *lowerBinary(EmitContext &ec, const ir::BinaryExpr &bin) {
    // Comparison takes priority: a non-null CmpInfo means op() is unused (cheat-sheet rule).
    if (const ir::CmpInfo *cmp = bin.cmp()) {
        return lowerComparison(ec, bin, *cmp);
    }

    switch (bin.op()) {
        // ---- integer arithmetic (checked) ----
        case ir::BinaryOp_ADD_INTEGER:
        case ir::BinaryOp_SUB_INTEGER:
        case ir::BinaryOp_MUL_INTEGER:
        case ir::BinaryOp_DIV_INTEGER:
        case ir::BinaryOp_MOD_INTEGER:
            return lowerIntegerArith(ec, bin);

        // ---- big_integer arithmetic (envelope-gated) ----
        case ir::BinaryOp_ADD_BIG_INTEGER:
        case ir::BinaryOp_SUB_BIG_INTEGER:
        case ir::BinaryOp_MUL_BIG_INTEGER:
        case ir::BinaryOp_DIV_BIG_INTEGER:
        case ir::BinaryOp_MOD_BIG_INTEGER:
            return lowerBigIntegerArith(ec, bin);

        // ---- decimal arithmetic (envelope-gated) ----
        case ir::BinaryOp_ADD_DECIMAL:
        case ir::BinaryOp_SUB_DECIMAL:
        case ir::BinaryOp_MUL_DECIMAL:
        case ir::BinaryOp_DIV_DECIMAL:
        case ir::BinaryOp_MOD_DECIMAL:
            return lowerDecimalArith(ec, bin);

        // ---- text / byte_array concat ----
        case ir::BinaryOp_CONCAT_TEXT:
            return lowerConcat(ec, bin, /*isText=*/true);
        case ir::BinaryOp_CONCAT_BYTE_ARRAY:
            return lowerConcat(ec, bin, /*isText=*/false);

        // ---- short-circuit logical ----
        case ir::BinaryOp_AND:
            return lowerShortCircuit(ec, bin, /*isAnd=*/true);
        case ir::BinaryOp_OR:
            return lowerShortCircuit(ec, bin, /*isAnd=*/false);

        // ---- value equality (inline only for i64-equality-safe operands) ----
        case ir::BinaryOp_EQ:
            return lowerEquality(ec, bin, /*negated=*/false);
        case ir::BinaryOp_NE:
            return lowerEquality(ec, bin, /*negated=*/true);

        // DETERMINISM: reference identity (===, !==) cannot be reproduced from the inline
        // payload — it is JVM object identity. Soft-fail.
        case ir::BinaryOp_EQ_REF:
        case ir::BinaryOp_NE_REF:
            return ec.failExpr("reference equality (EQ_REF/NE_REF) is not JITable inline");

        // DETERMINISM: collection / range / set / map operators are handle-backed structural
        // operations. They reach the JVM through the stdlib caller in other lowering paths;
        // as a raw BinaryExpr here there is no resolved SysFnId, so soft-fail the function.
        case ir::BinaryOp_CONCAT_LIST:
        case ir::BinaryOp_IN_COLLECTION:
        case ir::BinaryOp_IN_VIRTUAL_LIST:
        case ir::BinaryOp_IN_VIRTUAL_SET:
        case ir::BinaryOp_IN_MAP:
        case ir::BinaryOp_IN_RANGE:
        case ir::BinaryOp_SUB_LIST:
        case ir::BinaryOp_SUB_SET:
        case ir::BinaryOp_UNION_SET:
        case ir::BinaryOp_INTERSECT_LIST:
        case ir::BinaryOp_INTERSECT_SET:
        case ir::BinaryOp_MERGE_MAP:
            return ec.failExpr("collection/range BinaryOp routes to JVM: " +
                               std::to_string(static_cast<int>(bin.op())));

        default:
            return ec.failExpr("unsupported BinaryOp: " +
                               std::to_string(static_cast<int>(bin.op())));
    }
}

// =====================================================================================
// UnaryExpr entry point.
// =====================================================================================

llvm::Value *lowerUnary(EmitContext &ec, const ir::UnaryExpr &un) {
    const ir::Expr *operand = un.expr();
    if (operand == nullptr) return ec.failExpr("UnaryExpr has null operand");

    switch (un.op()) {
        case ir::UnaryOp_NOT: {
            if (!isBooleanType(un.type())) {
                return ec.failExpr("NOT result type is not boolean");
            }
            llvm::Value *v = lowerExpr(ec, *operand);
            if (v == nullptr) return nullptr;
            auto &b = ec.builder();
            // payload is 0/1; flip via 1 ^ x to stay in the {0,1} envelope.
            llvm::Value *flipped = b.CreateXor(payloadI64(ec, v), b.getInt64(1), "not");
            return ec.packInline(RellTag::BOOLEAN, b.getInt32(0), flipped);
        }

        case ir::UnaryOp_MINUS_INTEGER: {
            llvm::Value *v = lowerExpr(ec, *operand);
            if (v == nullptr) return nullptr;
            // DETERMINISM: integer negation overflows at Long.MIN_VALUE (evaluateUnaryOp uses
            // subtractExact(0, v)). emitIntegerUnaryMinus emits the checked 0 - v and records the
            // exact UNARY code (expr:-:overflow:<v>) into the integer-error channel — using
            // intrinsicInteger(SUB, 0, v) here would record the BINARY code and diverge.
            return emitIntegerUnaryMinus(ec, payloadI64(ec, v));
        }

        case ir::UnaryOp_MINUS_BIG_INTEGER: {
            llvm::Value *v = lowerExpr(ec, *operand);
            if (v == nullptr) return nullptr;
            auto &b = ec.builder();
            // RUNTIME tag guard: a wide big_integer operand is a HANDLE — escape to the interpreter.
            emitEscapeIfNotTag(ec, v, RellTag::BIGINT_LONG);
            // DETERMINISM: negate as 0 - x through the envelope-gated big_integer intrinsic so
            // out-of-envelope operands escape (i64 overflow) rather than producing a wrong inline i64.
            bool escaped = false;
            llvm::Value *res = intrinsicBigInteger(ec, ir::BinaryOp_SUB_BIG_INTEGER, b.getInt64(0),
                                                   payloadI64(ec, v), &escaped);
            if (escaped) {
                return ec.failExpr("big_integer negation escaped inline envelope");
            }
            return res;
        }

        case ir::UnaryOp_MINUS_DECIMAL: {
            llvm::Value *v = lowerExpr(ec, *operand);
            if (v == nullptr) return nullptr;
            auto &b = ec.builder();
            // RUNTIME tag guard: a wide decimal operand is a HANDLE — escape to the interpreter.
            emitEscapeIfNotTag(ec, v, RellTag::DEC_LONG);
            // DETERMINISM (M1): negate as `0 - x` through the decimal intrinsic; the zero operand is
            // DEC_LONG (mantissa 0, scale 0). Aligning a zero mantissa to x's scale is exact, the
            // subtract is exact, and the intrinsic strips trailing zeros, so the result is the exact
            // canonical normal form the interpreter's subtract(ZERO, x) + Rt_DecimalValue.get yields.
            bool escaped = false;
            llvm::Value *res =
                intrinsicDecimal(ec, ir::BinaryOp_SUB_DECIMAL, b.getInt64(0), b.getInt32(0),
                                 payloadI64(ec, v), ec.unpackScale(v), &escaped);
            if (escaped) {
                return ec.failExpr("decimal negation escaped inline envelope");
            }
            return res;
        }

        default:
            return ec.failExpr("unsupported UnaryOp: " +
                               std::to_string(static_cast<int>(un.op())));
    }
}

}  // namespace rell::llvm_rt
