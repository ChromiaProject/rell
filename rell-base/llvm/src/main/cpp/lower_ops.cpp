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
//     CmpType INTEGER/ROWID/BOOLEAN/ENUM        -> inline icmp on the i64 payload
//     CmpType DECIMAL/BIG_INTEGER/TEXT/         -> soft-fail (comparator is type-specific and
//             BYTE_ARRAY/ENTITY                     not exposed as a callable sysfn name)
//   BinaryExpr, arithmetic/logical (bin.op()):
//     ADD/SUB/MUL/DIV/MOD_INTEGER               -> intrinsicInteger (checked overflow)
//     ADD/SUB/MUL/DIV/MOD_BIG_INTEGER           -> intrinsicBigInteger (envelope-gated)
//     ADD/SUB/MUL/DIV/MOD_DECIMAL               -> intrinsicDecimal   (envelope-gated)
//     CONCAT_TEXT / CONCAT_BYTE_ARRAY           -> intrinsicText / intrinsicByteArray
//     AND / OR                                  -> inline short-circuit (BBs + phi)
//     EQ / NE                                   -> inline i64 equality on inline-tag operands
//                                                  (INTEGER/ROWID/BOOLEAN/ENUM/NULL/UNIT) only;
//                                                  any other operand kind soft-fails
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

namespace ir = rell::ir;

namespace rell::llvm_rt {

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

// =====================================================================================
// Comparisons (BinaryExpr with a non-null CmpInfo).
//
// Only the i64-backed inline tags can be compared with a plain icmp and stay bit-exact:
// INTEGER (signed i64), ROWID (i64 >= 0), BOOLEAN (0/1), ENUM (i64 ordinal). The Rell
// comparator for these is the natural signed integer order, which icmp reproduces exactly.
//
// DETERMINISM: DECIMAL/BIG_INTEGER comparison cannot use the inline payload — DEC_LONG
// carries a (mantissa, scale) pair whose order is NOT the order of the raw mantissa, and
// BIG_INTEGER may be a HANDLE. TEXT/BYTE_ARRAY/ENTITY comparison is a type-specific
// comparator with no callable sysfn name reachable here. All of these soft-fail.
// =====================================================================================

llvm::Value *lowerComparison(EmitContext &ec, const ir::BinaryExpr &bin, const ir::CmpInfo &cmp) {
    switch (cmp.cmp_type()) {
        case ir::CmpType_INTEGER:
        case ir::CmpType_ROWID:
        case ir::CmpType_BOOLEAN:
            break;  // inline-comparable below
        case ir::CmpType_ENUM:
        case ir::CmpType_BIG_INTEGER:
        case ir::CmpType_DECIMAL:
        case ir::CmpType_TEXT:
        case ir::CmpType_BYTE_ARRAY:
        case ir::CmpType_ENTITY:
            // DETERMINISM (C1): ENUM is HANDLE-backed (from_jvm has no inline-ordinal case), so
            // payload.i64 is a jobject pointer, not the ordinal — an icmp would compare addresses
            // (wrong equality, address-order, non-deterministic across nodes). The others have
            // type-specific comparators not exposed as callable sysfns here. All soft-fail.
            return ec.failExpr("comparison cmp_type routes to JVM interpreter: " +
                               std::to_string(static_cast<int>(cmp.cmp_type())));
        default:
            return ec.failExpr("unknown CmpType: " +
                               std::to_string(static_cast<int>(cmp.cmp_type())));
    }

    llvm::Value *lhs = lowerOperand(ec, bin.left());
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, bin.right());
    if (rhs == nullptr) return nullptr;

    auto &b = ec.builder();
    llvm::Value *a = payloadI64(ec, lhs);
    llvm::Value *c = payloadI64(ec, rhs);

    llvm::Value *cmpBit = nullptr;
    switch (cmp.op()) {
        case ir::CmpOp_LT: cmpBit = b.CreateICmpSLT(a, c, "cmp_lt"); break;
        case ir::CmpOp_GT: cmpBit = b.CreateICmpSGT(a, c, "cmp_gt"); break;
        case ir::CmpOp_LE: cmpBit = b.CreateICmpSLE(a, c, "cmp_le"); break;
        case ir::CmpOp_GE: cmpBit = b.CreateICmpSGE(a, c, "cmp_ge"); break;
        default:
            return ec.failExpr("unknown CmpOp: " + std::to_string(static_cast<int>(cmp.op())));
    }

    // Pack the i1 result into a BOOLEAN runtime value (payload 0/1).
    llvm::Value *asI64 = b.CreateZExt(cmpBit, b.getInt64Ty(), "cmp_i64");
    return ec.packInline(RellTag::BOOLEAN, b.getInt32(0), asI64);
}

// =====================================================================================
// Equality EQ / NE (BinaryOp, not CmpInfo).
//
// Bit-exact only for the i64-backed inline tags whose payload equality coincides with Rell
// value equality: INTEGER, ROWID, BOOLEAN. ENUM is excluded (C1: HANDLE-backed, payload is a
// pointer not an ordinal); NULL/UNIT excluded (H2: degenerate / Nullable-typed, not icmp-safe).
//
// DETERMINISM: DEC_LONG equality is NOT i64-payload equality (1.5 == 1.50 with different
// mantissa/scale), and HANDLE-backed values (text, byte_array, collections, struct, ...)
// need the JVM's structural equals(). Both soft-fail. The operand TYPE drives the decision:
// we only inline when both operands are statically one of the i64-equality-safe primitives.
// =====================================================================================

bool isI64EqualitySafeType(const ir::Type *type) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    if (prim == nullptr) {
        // DETERMINISM (C1): enums are HANDLE-backed — payload.i64 is a jobject pointer, not the
        // ordinal — so inline i64 equality would compare addresses. Soft-fail enum EQ/NE until an
        // inline ordinal representation exists.
        return false;
    }
    switch (prim->kind()) {
        case ir::PrimitiveTypeKind_INTEGER:
        case ir::PrimitiveTypeKind_ROWID:
        case ir::PrimitiveTypeKind_BOOLEAN:
            return true;
        default:
            // DETERMINISM (H2): UNIT/NULL omitted — unit==unit is degenerate/dead and null-eq is a
            // Nullable operand that is not i64-payload comparable. Keep icmp to INT/ROWID/BOOLEAN.
            return false;
    }
}

llvm::Value *lowerEquality(EmitContext &ec, const ir::BinaryExpr &bin, bool negated) {
    const ir::Expr *left = bin.left();
    const ir::Expr *right = bin.right();
    if (left == nullptr || right == nullptr) {
        return ec.failExpr("equality has null operand");
    }

    // Gate on operand static types: only inline i64-equality-safe primitives/enums. Anything
    // nullable, decimal, big_integer, or handle-backed soft-fails to the JVM equals().
    const auto *lv = left->expr_type() == ir::ExprUnion_VarExpr ? left->expr_as_VarExpr() : nullptr;
    const auto *rv = right->expr_type() == ir::ExprUnion_VarExpr ? right->expr_as_VarExpr() : nullptr;
    // Type info is most reliably read off the BinaryExpr operands themselves; every Expr
    // variant we can inline carries a type(). Reuse the VarExpr fast path where present, else
    // fall through to a soft-fail if we cannot prove the operand types are inline-safe.
    const ir::Type *lt = lv != nullptr ? lv->type() : nullptr;
    const ir::Type *rt = rv != nullptr ? rv->type() : nullptr;
    if (!isI64EqualitySafeType(lt) || !isI64EqualitySafeType(rt)) {
        // DETERMINISM: cannot prove both operands are i64-equality-safe; structural/decimal
        // equality must run on the JVM. Soft-fail.
        return ec.failExpr("equality operand types not inline i64-equality-safe");
    }

    llvm::Value *lhs = lowerOperand(ec, left);
    if (lhs == nullptr) return nullptr;
    llvm::Value *rhs = lowerOperand(ec, right);
    if (rhs == nullptr) return nullptr;

    auto &b = ec.builder();
    llvm::Value *a = payloadI64(ec, lhs);
    llvm::Value *c = payloadI64(ec, rhs);
    llvm::Value *eq = negated ? b.CreateICmpNE(a, c, "ne") : b.CreateICmpEQ(a, c, "eq");
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
            auto &b = ec.builder();
            // DETERMINISM: integer negation overflows at Long.MIN_VALUE (the JVM raises
            // Rt_Exception via LongMath.checkedNegate). Route through the checked integer
            // intrinsic as 0 - x so the overflow check is identical, rather than emitting a
            // wrapping `neg` inline.
            bool escaped = false;
            llvm::Value *res = intrinsicInteger(ec, ir::BinaryOp_SUB_INTEGER, b.getInt64(0),
                                                payloadI64(ec, v), &escaped);
            if (escaped) {
                return ec.failExpr("integer negation escaped inline envelope");
            }
            return res;
        }

        case ir::UnaryOp_MINUS_BIG_INTEGER: {
            llvm::Value *v = lowerExpr(ec, *operand);
            if (v == nullptr) return nullptr;
            auto &b = ec.builder();
            // DETERMINISM: negate as 0 - x through the envelope-gated big_integer intrinsic so
            // out-of-envelope operands escape rather than producing a wrong inline i64.
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
            // DETERMINISM: negate as 0 - x through the decimal intrinsic; the zero operand
            // carries scale 0 and the intrinsic reconciles scales / gates the envelope.
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
