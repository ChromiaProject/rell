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
//   emitted IR must DETECT the same overflow / div-by-zero conditions and, on detection, RECORD
//   the exact Rell error code into the integer-error channel (rell_int_overflow / rell_int_div0,
//   jni_bridge.cpp) so the Kotlin invokeNative() raises the identical Rt_Exception — instead of
//   silently wrapping the way jni_bridge.cpp's prototype `CreateAdd` did.
//
// The overflow detection here is expressed via LLVM's checked-arithmetic intrinsics
// (llvm.sadd/ssub/smul.with.overflow) and an explicit divisor-zero test. On the overflow/div0
// bit we branch to a recorder block that calls the channel back-call, then rejoin and return the
// (defined, JVM-discarded) numeric result; the non-error path stays straight-line and JITed. This
// is a CONSENSUS-CORRECT no-fallback path: the integer arithmetic ops never set "*escaped" or
// soft-fail. Operands are assumed already INTEGER-tagged i64 (the caller has unpacked field 2 of
// the runtime value and proven the tag); results are re-packed as INTEGER.
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
// intrinsics on i64, returning the { i64 result, i1 overflow } aggregate. The CALLER branches on
// the overflow bit into a recorder block that calls the integer-error channel (rell_int_overflow),
// then rejoins; the non-overflow path stays straight-line.
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

// Emit a call to the extern "C" `rell_int_overflow(char op, i64 a, i64 b)` back-call. This records
// the (op, a, b) into the thread-local integer-error channel that callI64Function clears before the
// run and pollIntOverflow() reads after it; the Kotlin invokeNative() then raises the exact
// Rt_Exception. Symbol is resolved by the JIT's process-symbol generator (see jni_bridge.cpp).
void emitIntOverflowCall(EmitContext &ec, char opChar, llvm::Value *a, llvm::Value *b) {
    auto &b_ = ec.builder();
    auto *i8Ty = b_.getInt8Ty();
    auto *i64Ty = b_.getInt64Ty();
    auto *voidTy = llvm::Type::getVoidTy(ec.ctx());
    auto *fnTy = llvm::FunctionType::get(voidTy, {i8Ty, i64Ty, i64Ty}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_int_overflow", fnTy);
    b_.CreateCall(callee, {b_.getInt8(static_cast<uint8_t>(opChar)), a, b});
}

// Emit a call to the extern "C" `rell_int_div0(char op, i64 a)` back-call (op is '/' or '%'),
// recording the div-by-zero into the same channel with the div0 discriminator.
void emitIntDiv0Call(EmitContext &ec, char opChar, llvm::Value *a) {
    auto &b_ = ec.builder();
    auto *i8Ty = b_.getInt8Ty();
    auto *i64Ty = b_.getInt64Ty();
    auto *voidTy = llvm::Type::getVoidTy(ec.ctx());
    auto *fnTy = llvm::FunctionType::get(voidTy, {i8Ty, i64Ty}, /*isVarArg=*/false);
    llvm::FunctionCallee callee = ec.module().getOrInsertFunction("rell_int_div0", fnTy);
    b_.CreateCall(callee, {b_.getInt8(static_cast<uint8_t>(opChar)), a});
}

}  // namespace

// =====================================================================================
// intrinsicInteger — the header-declared overlay entry for BinaryExpr on integer operands.
//
// INLINE-COVERED (pure i64, with bit-exact overflow/div0 detection — ALWAYS JITed, never escapes):
//   ADD_INTEGER, SUB_INTEGER, MUL_INTEGER  — checked via *.with.overflow; on overflow, record
//                                            expr:<op>:overflow:<a>:<b> into the integer-error
//                                            channel and continue (result discarded JVM-side).
//   DIV_INTEGER, MOD_INTEGER               — zero-divisor guard records expr:<op>:div0:<a>; the
//                                            Long.MIN/-1 input is computed inline via the JVM's
//                                            exact wrap result (Long.MIN for `/`, 0 for `%`) over
//                                            a sanitised divisor (never a UB sdiv/srem).
//
// ERROR REPORTING (no fallback): overflow / div0 do NOT route to rell_sysfn_call or soft-fail.
//   The exact Rt_Exception is raised by Kotlin invokeNative() from the thread-local channel that
//   pollIntOverflow() reads — consensus-identical code strings to evalIntArith (rt_ops.kt).
//
// SOFT-FAIL (ec.fail(), whole function -> JVM interpreter):
//   any BinaryOp that is not one of the five integer arithmetic ops above. Comparisons
//   (CmpInfo) and equality (EQ/NE) are NOT this overlay's responsibility — the caller dispatches
//   those before reaching here; if one slips through we fail rather than guess.
//
// *escaped semantics (per header): set true with a nullptr return when the result/operands fall
// outside the safe envelope; the caller then emits the slow path. Never set true with non-null.
// The five integer arithmetic ops NEVER escape: their overflow/div0 conditions are detected with a
// runtime branch that records the exact Rell error code into the integer-error channel
// (rell_int_overflow / rell_int_div0 in jni_bridge.cpp), which callI64Function clears before the
// run and pollIntOverflow() reports after it; the Kotlin invokeNative() then raises the identical
// Rt_Exception and discards the (still-defined) numeric result. The non-error path is straight-line
// and stays JITed — this is the consensus-correct realisation of C2 with NO interpreter fallback.
// =====================================================================================
llvm::Value *intrinsicInteger(EmitContext &ec, ir::BinaryOp op, llvm::Value *a, llvm::Value *b,
                              bool *escaped) {
    *escaped = false;
    auto &b_ = ec.builder();
    llvm::Function *fn = b_.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();

    switch (op) {
        case ir::BinaryOp_ADD_INTEGER:
        case ir::BinaryOp_SUB_INTEGER:
        case ir::BinaryOp_MUL_INTEGER: {
            llvm::Intrinsic::ID id = op == ir::BinaryOp_ADD_INTEGER ? llvm::Intrinsic::sadd_with_overflow
                                   : op == ir::BinaryOp_SUB_INTEGER ? llvm::Intrinsic::ssub_with_overflow
                                                                    : llvm::Intrinsic::smul_with_overflow;
            char opChar = op == ir::BinaryOp_ADD_INTEGER ? '+'
                        : op == ir::BinaryOp_SUB_INTEGER ? '-'
                                                         : '*';
            llvm::Value *ovf = nullptr;
            llvm::Value *res = emitCheckedBinop(ec, id, a, b, &ovf);
            // DETERMINISM (C2): Math.{add,subtract,multiply}Exact throw Rt_Exception on overflow.
            // We KEEP the native fast path: on the overflow bit, branch to a block that records the
            // exact Rell error code (expr:<op>:overflow:<a>:<b>) into the thread-local channel that
            // pollIntOverflow() reads, then rejoin. The wrapped i64 `res` is still produced for a
            // defined SSA value; it is DISCARDED by the Kotlin side once the pending flag is seen,
            // so the result never silently feeds a downstream op. No poison, no soft-fail: the
            // non-overflow path stays straight-line and JITed.
            auto *ovfBB = llvm::BasicBlock::Create(ctx, "int_ovf", fn);
            auto *contBB = llvm::BasicBlock::Create(ctx, "int_ovf_cont", fn);
            b_.CreateCondBr(ovf, ovfBB, contBB);
            b_.SetInsertPoint(ovfBB);
            emitIntOverflowCall(ec, opChar, a, b);
            b_.CreateBr(contBB);
            b_.SetInsertPoint(contBB);
            return packInt(ec, res);
        }

        case ir::BinaryOp_DIV_INTEGER:
        case ir::BinaryOp_MOD_INTEGER: {
            // DETERMINISM (C2): evalIntArith for "/" and "%" throws Rt_Exception("expr:<op>:div0:<a>")
            // when the divisor is 0. Long.MIN_VALUE / -1 (and % -1) does NOT throw on the JVM: `/`
            // yields Long.MIN_VALUE and `%` yields 0L (Kotlin wrap) — but LLVM `sdiv`/`srem` on that
            // pair is UNDEFINED BEHAVIOUR. So we (1) on b==0, record the div0 code into the channel
            // and rejoin (the produced value is discarded JVM-side); (2) for the MIN/-1 case, divide
            // by a sanitised divisor to keep the IR defined and SELECT in the exact JVM wrap result
            // (Long.MIN for `/`, 0 for `%`); (3) otherwise emit a real inline sdiv/srem. Native fast
            // path is preserved; no poison, no soft-fail.
            char opChar = op == ir::BinaryOp_DIV_INTEGER ? '/' : '%';
            llvm::Value *zero = b_.getInt64(0);
            llvm::Value *negOne = b_.getInt64(-1);
            llvm::Value *intMin = b_.getInt64(INT64_MIN);

            llvm::Value *bIsZero = b_.CreateICmpEQ(b, zero, "div_bzero");

            // div0 guard: record the error, then fall through to a well-defined (sanitised) compute
            // whose result is discarded by the Kotlin poll. Keeping the divide defined avoids UB even
            // on the error edge.
            auto *div0BB = llvm::BasicBlock::Create(ctx, "int_div0", fn);
            auto *computeBB = llvm::BasicBlock::Create(ctx, "int_div_compute", fn);
            b_.CreateCondBr(bIsZero, div0BB, computeBB);
            b_.SetInsertPoint(div0BB);
            emitIntDiv0Call(ec, opChar, a);
            b_.CreateBr(computeBB);

            b_.SetInsertPoint(computeBB);
            // MIN/-1 wrap handling: sanitise the divisor so sdiv/srem is never UB, then pick the
            // JVM's exact wrap result for that single input pair.
            llvm::Value *aIsMin = b_.CreateICmpEQ(a, intMin, "div_amin");
            llvm::Value *bIsNegOne = b_.CreateICmpEQ(b, negOne, "div_bneg1");
            llvm::Value *minOverflow = b_.CreateAnd(aIsMin, bIsNegOne, "div_minovf");
            // Sanitise away both the zero (already recorded) and the MIN/-1 UB inputs.
            llvm::Value *unsafeDivisor = b_.CreateOr(bIsZero, minOverflow, "div_unsafe");
            llvm::Value *safeB = b_.CreateSelect(unsafeDivisor, b_.getInt64(1), b, "div_safeb");
            llvm::Value *raw = op == ir::BinaryOp_DIV_INTEGER ? b_.CreateSDiv(a, safeB, "sdiv")
                                                              : b_.CreateSRem(a, safeB, "srem");
            // JVM wrap result for Long.MIN op -1: `/` -> Long.MIN, `%` -> 0.
            llvm::Value *wrapVal = op == ir::BinaryOp_DIV_INTEGER ? intMin : zero;
            llvm::Value *quot = b_.CreateSelect(minOverflow, wrapVal, raw, "div_wrap");
            return packInt(ec, quot);
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
// subtractExact(0, v), which throws on v == Long.MIN_VALUE. We emit the same as a checked 0 - v;
// the single overflowing input (Long.MIN) records the exact Rt_Exception code into the
// integer-error channel and continues, exactly as the binary ops do. No soft-fail, stays JITed.
// Called from lower_ops.cpp's lowerUnary.
// =====================================================================================
llvm::Value *emitIntegerUnaryMinus(EmitContext &ec, llvm::Value *v) {
    auto &b_ = ec.builder();
    llvm::Function *fn = b_.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();
    // DETERMINISM (C2): evaluateUnaryOp "Minus_Integer" is subtractExact(0, v), which throws
    // Rt_Exception("expr:-:overflow:<v>") on v == Long.MIN_VALUE (its negation is itself). Detect
    // that single input, record the exact code into the integer-error channel via the 'n' (unary
    // negate) discriminator, then continue. The wrapped `0 - v` is a defined SSA value, discarded
    // JVM-side once the pending flag is observed. Native fast path preserved; no soft-fail.
    llvm::Value *isMin = b_.CreateICmpEQ(v, b_.getInt64(INT64_MIN), "neg_min");
    auto *ovfBB = llvm::BasicBlock::Create(ctx, "neg_ovf", fn);
    auto *contBB = llvm::BasicBlock::Create(ctx, "neg_ovf_cont", fn);
    b_.CreateCondBr(isMin, ovfBB, contBB);
    b_.SetInsertPoint(ovfBB);
    emitIntOverflowCall(ec, 'n', v, b_.getInt64(0));
    b_.CreateBr(contBB);
    b_.SetInsertPoint(contBB);
    return packInt(ec, b_.CreateSub(b_.getInt64(0), v, "neg"));
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
//     integer.abs()  -> abs(x): bit-exact; abs(Long.MIN_VALUE) records the JVM's overflow code
//                       (abs:integer:overflow:<v>) into the integer-error channel, never escapes.
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

// abs(x): bit-exact with the JVM's checked abs. abs(Long.MIN_VALUE) overflows on the JVM (no
// representable positive), so that single input records the exact Rt_Exception code into the
// integer-error channel and continues — never a poison, never a soft-fail.
llvm::Value *emitIntegerAbs(EmitContext &ec, llvm::Value *x) {
    auto &b_ = ec.builder();
    llvm::Function *fn = b_.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();
    // DETERMINISM (C2): Lib_Math.Abs_Integer throws Rt_Exception("abs:integer:overflow:<v>") on
    // x == Long.MIN_VALUE (no representable positive). Detect that single input, record the exact
    // code into the integer-error channel via the 'a' (abs) discriminator, then continue. The
    // wrapped result is a defined SSA value, discarded JVM-side once pending is observed. Native
    // fast path preserved; no soft-fail.
    llvm::Value *isMin = b_.CreateICmpEQ(x, b_.getInt64(INT64_MIN), "abs_min");
    auto *ovfBB = llvm::BasicBlock::Create(ctx, "abs_ovf", fn);
    auto *contBB = llvm::BasicBlock::Create(ctx, "abs_ovf_cont", fn);
    b_.CreateCondBr(isMin, ovfBB, contBB);
    b_.SetInsertPoint(ovfBB);
    emitIntOverflowCall(ec, 'a', x, b_.getInt64(0));
    b_.CreateBr(contBB);
    b_.SetInsertPoint(contBB);
    // abs(x) = x < 0 ? -x : x. For x == Long.MIN the -x wraps back to Long.MIN; that value is
    // discarded JVM-side (pending set above), so it never feeds a downstream op.
    llvm::Value *neg = b_.CreateSub(b_.getInt64(0), x, "abs_neg");
    llvm::Value *isNeg = b_.CreateICmpSLT(x, b_.getInt64(0), "abs_isneg");
    llvm::Value *r = b_.CreateSelect(isNeg, neg, x, "abs");
    return packInt(ec, r);
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
