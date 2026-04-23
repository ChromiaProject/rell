// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// intrinsics_integer.cpp — pure C++ integer fast-path overlay for the Rell LLVM backend.
//
// This file emits inline LLVM IR for the integer arithmetic operators and the small set of
// integer stdlib functions whose JVM implementation is a pure i64 computation that the
// interpreter performs with java.lang.Math.*Exact (CHECKED overflow). The hard rule of this
// overlay (consensus-critical, see rell_runtime.h §"CORRECTNESS RULE"):
//
//   The Rell interpreter's integer ops are NOT wrapping. evaluateBinaryOp (rt_ops.kt) uses
//   Math.addExact/subtractExact/multiplyExact, which THROW Rt_Exception on overflow; unary
//   minus uses subtractExact(0, v); div/mod throw on a zero divisor. To stay bit-exact, the
//   emitted IR must DETECT the same overflow / div-by-zero conditions and, on detection, take
//   the JVM slow path (the universal stdlib caller, which reproduces the exact Rt_Exception)
//   instead of silently wrapping the way jni_bridge.cpp's prototype `CreateAdd` did.
//
// The overflow detection here is therefore expressed via LLVM's checked-arithmetic
// intrinsics (llvm.sadd/ssub/smul.with.overflow) and an explicit divisor-zero test. The
// "*escaped" out-parameter signals the caller (lowerBinary in lower_ops.cpp) to drop the
// inline value and re-issue the operation as a rell_sysfn_call back-call so the JVM raises
// the identical Rt_Exception. Operands are assumed already INTEGER-tagged i64 (the caller has
// unpacked field 2 of the runtime value and proven the tag); results are re-packed as INTEGER.
//
// Style mirrors jni_bridge.cpp / rell_runtime.h: namespace rell::llvm_rt, defensive checks,
// LLVM 19+ IRBuilder API, never emit IR after ec.fail().

#include "rell_runtime.h"

#include <llvm/IR/Constants.h>
#include <llvm/IR/Intrinsics.h>

namespace rell::llvm_rt {

namespace {

// ---------------------------------------------------------------------------------------
// Checked-arithmetic helper: emit a call to one of the llvm.{sadd,ssub,smul}.with.overflow
// intrinsics on i64, returning the { i64 result, i1 overflow } aggregate. The CALLER decides
// what to do with the overflow bit; this overlay routes overflow to the JVM slow path rather
// than branching in-IR, keeping the inline path branch-free and the soft-fail surface narrow.
// ---------------------------------------------------------------------------------------
llvm::Value *emitCheckedBinop(EmitContext &ec, llvm::Intrinsic::ID id, llvm::Value *a,
                              llvm::Value *b, llvm::Value **overflowOut) {
    auto &b_ = ec.builder();
    auto *i64Ty = b_.getInt64Ty();
    // LLVM 19: getOrInsertDeclaration replaces the removed getDeclaration; the with-overflow
    // intrinsics are overloaded on a single integer type, so pass {i64Ty} as the type list.
    llvm::Function *decl =
        llvm::Intrinsic::getOrInsertDeclaration(&ec.module(), id, {i64Ty});
    llvm::Value *agg = b_.CreateCall(decl, {a, b}, "chk");
    *overflowOut = b_.CreateExtractValue(agg, {1}, "ovf");
    return b_.CreateExtractValue(agg, {0}, "res");
}

// Re-pack an i64 result as an INTEGER runtime value (tag=INTEGER, scale=0).
llvm::Value *packInt(EmitContext &ec, llvm::Value *payloadI64) {
    return ec.packInteger(payloadI64);
}

}  // namespace

// =====================================================================================
// intrinsicInteger — the header-declared overlay entry for BinaryExpr on integer operands.
//
// INLINE-COVERED (pure i64, with bit-exact overflow/div0 detection):
//   ADD_INTEGER, SUB_INTEGER, MUL_INTEGER  — checked via *.with.overflow; on overflow -> escape.
//   DIV_INTEGER, MOD_INTEGER               — emitted with a zero-divisor guard AND the
//                                            Long.MIN/-1 overflow guard; either guard -> escape.
//
// ESCAPED (routed to rell_sysfn_call by the caller, which reproduces the exact Rt_Exception):
//   any overflow of add/sub/mul; div/mod by zero; Long.MIN_VALUE / -1 (and % -1) — the one
//   i64 division that traps on the JVM and is UB for LLVM `sdiv`. We never emit a bare sdiv on
//   a possibly-overflowing pair.
//
// SOFT-FAIL (ec.fail(), whole function -> JVM interpreter):
//   any BinaryOp that is not one of the five integer arithmetic ops above. Comparisons
//   (CmpInfo) and equality (EQ/NE) are NOT this overlay's responsibility — the caller dispatches
//   those before reaching here; if one slips through we fail rather than guess.
//
// *escaped semantics (per header): set true with a nullptr return when the result/operands fall
// outside the safe envelope; the caller then emits the slow path. Never set true with non-null.
// For the trapping div/mod inputs we cannot prove safety statically, so instead of escaping
// unconditionally we emit a runtime guard that branches the *trapping* inputs to the slow path
// while keeping the common case inline. That guard is materialised by the caller via the
// returned "needs-guard" contract below; to keep this overlay self-contained and bit-exact we
// take the conservative, always-correct route: emit the full guarded sequence here using the
// EmitContext's current insertion block, leaving the slow-path block to lower_ops.cpp.
// =====================================================================================
llvm::Value *intrinsicInteger(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                              bool *escaped) {
    *escaped = false;
    auto &b_ = ec.builder();

    // DETERMINISM (C2): ADD/SUB/MUL overflow and DIV/MOD div0 + Long.MIN/-1 must reproduce the
    // JVM's exact Rt_Exception via a runtime branch into rell_sysfn_call with the integer
    // operator's sysfn id. That guarded slow path is NOT yet wired in lower_ops.cpp, and the
    // NONE-poison sentinel emitted below has no consumer (it would surface as a RuntimeException
    // in to_jvm, or be silently read as 0) — both are consensus splits. Until the slow-path
    // branch + sysfn-id threading lands, ESCAPE the whole op to the interpreter. The checked
    // sequences below are retained verbatim so they can be re-enabled behind that branch.
    switch (op) {
        case ir::BinaryOp_ADD_INTEGER:
        case ir::BinaryOp_SUB_INTEGER:
        case ir::BinaryOp_MUL_INTEGER:
        case ir::BinaryOp_DIV_INTEGER:
        case ir::BinaryOp_MOD_INTEGER:
            *escaped = true;
            return nullptr;
        default:
            break;
    }

    switch (op) {
        case ir::BinaryOp_ADD_INTEGER:
        case ir::BinaryOp_SUB_INTEGER:
        case ir::BinaryOp_MUL_INTEGER: {
            llvm::Intrinsic::ID id = op == ir::BinaryOp_ADD_INTEGER ? llvm::Intrinsic::sadd_with_overflow
                                   : op == ir::BinaryOp_SUB_INTEGER ? llvm::Intrinsic::ssub_with_overflow
                                                                    : llvm::Intrinsic::smul_with_overflow;
            llvm::Value *ovf = nullptr;
            llvm::Value *res = emitCheckedBinop(ec, id, a, b, &ovf);
            // DETERMINISM: Math.{add,subtract,multiply}Exact throw Rt_Exception on overflow;
            // wrapping (LLVM's plain add/sub/mul) would diverge from the JVM. We select between
            // the in-envelope inline result and a NONE poison keyed on the overflow bit, then
            // the function-level guard (lower_ops.cpp) turns a NONE-tagged result into the
            // rell_sysfn_call slow path that raises the identical exception. The select keeps
            // the hot path branch-free; correctness lives in the tag, not in a guess.
            llvm::Value *inlineVal = packInt(ec, res);
            llvm::Value *poison = ec.packInline(RellTag::NONE, b_.getInt32(0), b_.getInt64(0));
            return b_.CreateSelect(ovf, poison, inlineVal, "int_arith_checked");
        }

        case ir::BinaryOp_DIV_INTEGER:
        case ir::BinaryOp_MOD_INTEGER: {
            // DETERMINISM: evalIntArith for "/" and "%" throws Rt_Exception("div0") when the
            // divisor is 0. Additionally, Long.MIN_VALUE / -1 OVERFLOWS: on the JVM `a / b`
            // yields Long.MIN_VALUE (no throw for "/", and 0L for "%"), but LLVM `sdiv`/`srem`
            // on that pair is UNDEFINED BEHAVIOUR. We must never emit a bare sdiv/srem that
            // could hit it. So: detect (b == 0) OR (a == Long.MIN && b == -1); on either,
            // produce a NONE poison so the function-level guard routes to the JVM slow path,
            // which reproduces the exact div0 Rt_Exception for b==0 and the exact wrapping
            // result for the MIN/-1 case. The common case stays an inline sdiv/srem on a pair
            // we have proven safe.
            auto *i64Ty = b_.getInt64Ty();
            llvm::Value *zero = b_.getInt64(0);
            llvm::Value *negOne = b_.getInt64(-1);
            llvm::Value *intMin = b_.getInt64(INT64_MIN);

            llvm::Value *bIsZero = b_.CreateICmpEQ(b, zero, "div_bzero");
            llvm::Value *aIsMin = b_.CreateICmpEQ(a, intMin, "div_amin");
            llvm::Value *bIsNegOne = b_.CreateICmpEQ(b, negOne, "div_bneg1");
            llvm::Value *minOverflow = b_.CreateAnd(aIsMin, bIsNegOne, "div_minovf");
            llvm::Value *unsafe = b_.CreateOr(bIsZero, minOverflow, "div_unsafe");

            // Guard the divide so the UNSAFE path never executes a trapping sdiv/srem: divide by
            // a sanitised divisor (1 when unsafe) purely to keep the IR well-defined; the result
            // of that sanitised divide is discarded by the select below in favour of the poison.
            llvm::Value *safeB = b_.CreateSelect(unsafe, b_.getInt64(1), b, "div_safeb");
            llvm::Value *quot = op == ir::BinaryOp_DIV_INTEGER
                                    ? b_.CreateSDiv(a, safeB, "sdiv")
                                    : b_.CreateSRem(a, safeB, "srem");
            (void)i64Ty;

            llvm::Value *inlineVal = packInt(ec, quot);
            llvm::Value *poison = ec.packInline(RellTag::NONE, b_.getInt32(0), b_.getInt64(0));
            return b_.CreateSelect(unsafe, poison, inlineVal, "int_divmod_checked");
        }

        default:
            // Not an integer arithmetic op. Comparisons (CmpInfo) and EQ/NE/EQ_REF/NE_REF are
            // handled upstream; reaching here with anything else means the caller mis-dispatched
            // or the op is genuinely unsupported on integers — soft-fail rather than emit wrong IR.
            ec.fail("intrinsicInteger: unsupported BinaryOp " +
                    std::to_string(static_cast<int>(op)));
            *escaped = false;
            return nullptr;
    }
}

// =====================================================================================
// emitIntegerUnaryMinus — unary `-x` on an INTEGER operand (UnaryOp_MINUS_INTEGER).
//
// INLINE-COVERED with overflow detection. DETERMINISM: evaluateUnaryOp "Minus_Integer" computes
// subtractExact(0, v), which throws on v == Long.MIN_VALUE. We emit the same as a checked
// 0 - v and route the single overflowing input (Long.MIN) to the JVM slow path via a NONE
// poison, exactly as the binary ops do. Called from lower_ops.cpp's lowerUnary.
// =====================================================================================
llvm::Value *emitIntegerUnaryMinus(EmitContext &ec, llvm::Value *v) {
    (void)v;
    // DETERMINISM (C2): subtractExact(0, v) throws Rt_Exception on v == Long.MIN_VALUE. Reproducing
    // that needs a runtime branch into rell_sysfn_call (not yet wired); the NONE-poison sentinel
    // has no consumer. Soft-fail the whole function until the guarded slow path exists.
    ec.fail("emitIntegerUnaryMinus: checked negation needs the unwired slow-path branch — soft-fail");
    return nullptr;
}

// =====================================================================================
// Integer STDLIB functions.
//
// The integer stdlib splits cleanly into "pure i64, bit-exactly inlinable" and "must go to the
// JVM". This overlay inlines ONLY the first group; everything else returns nullptr from
// tryEmitIntegerSysFn so lower_call.cpp falls back to rell_sysfn_call (the universal stdlib
// caller). The classification below is deliberate and conservative:
//
//   INLINE-COVERED:
//     integer.abs()  -> abs(x): bit-exact via 0-x checked; Long.MIN_VALUE escapes (JVM abs throws).
//     integer.sign() -> -1/0/1: pure compare, never overflows, always inline.
//     min(a,b)/max(a,b) on integer -> select on a signed compare; never overflows.
//
//   ROUTED TO JNI (rell_sysfn_call) — NOT inlined here:
//     integer.pow(e)        : the JVM uses checked multiply with per-step overflow + negative-exp
//                             Rt_Exception semantics; an inline loop risks bit-divergence and the
//                             exact error message. DETERMINISM: route to JVM. (Also: pow may
//                             return big_integer in some overloads — type-dependent.)
//     integer.to_text(),
//     integer.to_text(radix),
//     integer.from_text(...),
//     integer.to_hex(), from_hex(...): all PRODUCE OR CONSUME a text/byte_array, i.e. a HANDLE
//                             value, with locale-independent but JVM-defined formatting (radix
//                             bounds checks, sign handling, error messages). DETERMINISM: these
//                             cross the inline/HANDLE boundary and must marshal through the JVM
//                             (Long.toString / parseLong-equivalent in Rt_*); inlining string
//                             formatting in C++ would risk a non-bit-exact rendering. Route to JNI.
//     integer.to_decimal(), to_big_integer(), to_gtv(), etc.: type conversions producing
//                             DEC_LONG/BIGINT_LONG/HANDLE — handled by the decimal/bigint overlays
//                             or the JVM, not here.
//
// tryEmitIntegerSysFn returns the inline runtime value, or nullptr (WITHOUT ec.fail()) to mean
// "not an inlinable integer stdlib fn — use the JNI slow path". It only ec.fail()s on a
// malformed call shape it cannot safely hand off either.
// =====================================================================================

namespace {

// abs(x): bit-exact with the JVM's checked abs. abs(Long.MIN_VALUE) overflows on the JVM
// (no representable positive), so route that single input to the slow path via NONE poison.
llvm::Value *emitIntegerAbs(EmitContext &ec, llvm::Value *x) {
    (void)x;
    // DETERMINISM (C2): abs(Long.MIN_VALUE) throws Rt_Exception on the JVM (no representable
    // positive). The NONE-poison sentinel for that single input has no consumer-side slow path,
    // so soft-fail the whole function until the guarded rell_sysfn_call branch is wired.
    ec.fail("emitIntegerAbs: checked abs needs the unwired slow-path branch — soft-fail");
    return nullptr;
}

// sign(x): -1/0/1. Pure signed comparison, never overflows -> always inline.
llvm::Value *emitIntegerSign(EmitContext &ec, llvm::Value *x) {
    auto &b_ = ec.builder();
    llvm::Value *zero = b_.getInt64(0);
    llvm::Value *isPos = b_.CreateICmpSGT(x, zero, "sgn_pos");
    llvm::Value *isNeg = b_.CreateICmpSLT(x, zero, "sgn_neg");
    // (isPos ? 1 : 0) + (isNeg ? -1 : 0) folded as two selects.
    llvm::Value *posPart = b_.CreateSelect(isPos, b_.getInt64(1), zero, "sgn_p");
    llvm::Value *sign = b_.CreateSelect(isNeg, b_.getInt64(-1), posPart, "sgn");
    return packInt(ec, sign);
}

// min(a,b) / max(a,b) on integer: signed compare + select, never overflows -> always inline.
llvm::Value *emitIntegerMinMax(EmitContext &ec, llvm::Value *a, llvm::Value *b, bool wantMin) {
    auto &b_ = ec.builder();
    llvm::Value *cmp = wantMin ? b_.CreateICmpSLE(a, b, "min_cmp")
                               : b_.CreateICmpSGE(a, b, "max_cmp");
    llvm::Value *sel = b_.CreateSelect(cmp, a, b, wantMin ? "min" : "max");
    return packInt(ec, sel);
}

}  // namespace

// Try to inline an integer stdlib function by its RR fn_name. `args` are already-unpacked i64
// operands (the caller has proven each is INTEGER-tagged). Returns the inline runtime value, or
// nullptr to mean "route to rell_sysfn_call". Does NOT ec.fail() for unknown names (that is a
// normal hand-off, not an error); only fails on a recognised name with a wrong arity, where
// falling through would be a caller bug worth surfacing as a soft-fail.
//
// Name set mirrors the integer stdlib member/global functions reachable as
// FnTarget_SysMember/SysGlobal fn_name(). Unknown -> nullptr (JNI). The math globals min/max are
// also offered by the math overlay; this entry covers the integer-typed specialisation only.
llvm::Value *tryEmitIntegerSysFn(EmitContext &ec, const std::string &fnName,
                                 llvm::ArrayRef<llvm::Value *> args) {
    if (fnName == "integer.abs" || fnName == "abs") {
        if (args.size() != 1) return ec.failExpr("integer.abs: expected 1 arg");
        return emitIntegerAbs(ec, args[0]);
    }
    if (fnName == "integer.sign") {
        if (args.size() != 1) return ec.failExpr("integer.sign: expected 1 arg");
        return emitIntegerSign(ec, args[0]);
    }
    if (fnName == "min" || fnName == "integer.min") {
        if (args.size() != 2) return nullptr;  // non-binary min -> JNI (e.g. collection.min)
        return emitIntegerMinMax(ec, args[0], args[1], /*wantMin=*/true);
    }
    if (fnName == "max" || fnName == "integer.max") {
        if (args.size() != 2) return nullptr;  // non-binary max -> JNI
        return emitIntegerMinMax(ec, args[0], args[1], /*wantMin=*/false);
    }

    // integer.pow / to_text / from_text / to_hex / from_hex / to_decimal / to_big_integer / ...
    // DETERMINISM: string-producing/consuming and overflow-prone overloads must be reproduced by
    // the JVM bit-for-bit. Not inlined here — return nullptr so lower_call.cpp emits the
    // universal rell_sysfn_call slow path.
    return nullptr;
}

}  // namespace rell::llvm_rt
