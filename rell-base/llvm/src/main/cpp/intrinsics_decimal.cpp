// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// intrinsics_decimal.cpp — the decimal intrinsic overlay for the Rell LLVM backend.
//
// SCOPE OF THIS FILE
// ------------------
// Decimal is a SPLIT type in the inline value lattice. A long-fitting decimal travels through
// JIT'd code as a DEC_LONG (RellTag::DEC_LONG): an i64 mantissa in payload.i64 + a natural,
// trailing-zero-STRIPPED scale in `scale`, representing `mantissa / 10^scale` — exactly the
// Tf_LongScaleDecimal leaf the Truffle JIT already proves bit-exact against the JVM interpreter
// (Lib_DecimalMath). Anything outside that envelope is a HANDLE to a JVM Rt_DecimalValue.
//
// This overlay is the #1 determinism risk in the whole backend, so every guard below is copied
// verbatim from the proven reference — runtime-truffle/.../tf_op_nodes.kt (fastLongScaleAddSub /
// DecimalMul.fastLongScale / DecimalCmp.compareLongScale) and tf_long_scale_decimal.kt — NOT
// re-derived. The C++ side reproduces those Long-arithmetic fast paths bit-for-bit; whenever the
// reference would `return null` (defer to the 128-bit / BigDecimal tier), the C++ side routes to
// the JVM instead. There is exactly one source of truth and it is the JVM.
//
// EMIT CONTRACT (rell_runtime.h §9 / ABI.md §7)
// --------------------------------------------
// The overlay entry `intrinsicDecimal(ec, op, aMant, aScale, bMant, bScale, escaped)` receives
// the two operands ALREADY UNPACKED into i64 mantissas + i32 scales (the caller proved both are
// in-envelope DEC_LONGs). It returns the result runtime value (valueType() == { i8, i32, i64 })
// or sets `*escaped = true` and returns nullptr to make lower_ops.cpp emit the universal JNI
// caller (rell_sysfn_call). `*escaped` true and a non-null return are mutually exclusive.
//
// HOW ADD/SUB/MUL STAY BIT-EXACT (the runtime-escape posture)
// -----------------------------------------------------------
// The reference fast paths are RUNTIME-DATA-DEPENDENT: whether a value stays in-envelope depends on
// the operand mantissas/scales, which are runtime SSA values here. The Truffle code answers "in
// envelope?" with `Math.addExact`/`multiplyExact` catching ArithmeticException and `return null` —
// a RUNTIME branch into a slow path. The value ABI realises that branch via the envelope-escape
// channel (rell_jit_escape, jni_bridge.cpp): on any runtime overflow / scale-out-of-range edge the
// emitter calls rell_jit_escape() and callValueFunction re-runs the WHOLE call on the interpreter
// (bit-exact). A native DEC_LONG result is committed ONLY when no escape fired, so nothing ever
// wraps into consensus — the determinism rule holds without a per-op rell_sysfn_call continuation.
//
// The emitters (emitDecLongAddSubRuntime / emitDecLongMulRuntime / emitDecLongCompareRuntime) take
// the operands' RUNTIME mantissas + scales (from_jvm hands inline DEC_LONG across the value ABI),
// align via a runtime POW10 table load, compute overflow-checked, then strip trailing zeros to the
// canonical form. Every result equals what from_jvm(to_jvm(result)) would crack (the M3 invariant):
//   M1 — MINUS_DECIMAL is `0 - x`; the zero is DEC_LONG (0, 0), alignment of a zero mantissa is
//        exact, the subtract is exact, and stripTrailingZeros yields the interpreter's normal form.
//   M2 — mul rescales a product with scale > DECIMAL_FRAC_DIGITS to 20 with HALF_UP away-from-zero
//        (round when |rem|*2 >= divisor; the tie |rem|*2 == divisor rounds away from zero, NOT
//        half-even), exactly Lib_DecimalMath.scale's setScale(20, HALF_UP); then strips.
//   M3 — every packed DEC_LONG is stripped (emitStripTrailingZeros), so payload+scale equality
//        coincides with value equality and to_jvm round-trips to the same canonical BigDecimal.
//
// DIV / MOD / ROUND / POW / SQRT: always JVM. The reference has no Long-arithmetic div/mod leaf
// (rounding mode + scale growth make a bit-exact inline path infeasible), so they are not even
// candidates. See the per-op DETERMINISM notes.
//
// Style mirrors jni_bridge.cpp / rell_runtime.h: `namespace ir = rell::ir;`, the escaped/soft-
// fail signalling from §9, defensive null-checks, LLVM 19+ IRBuilder.

#include <array>

#include <llvm/IR/Intrinsics.h>

#include "rell_runtime.h"

namespace rell::llvm_rt {

namespace {

// Largest scale delta / canonical-rescale shift for which 10^k still fits a signed Long.
// 10^18 ≈ 1.11e18 fits; 10^19 exceeds Long.MAX. Mirrors tf_op_nodes.kt DECIMAL_MAX_ALIGN_DELTA
// and Tf_LongScaleDecimal.MAX_LONG_SCALE — identical to the header's kDecLongMaxScale.
constexpr int32_t kMaxAlignDelta = kDecLongMaxScale;  // 18

// Canonical Rell fraction scale (Lib_DecimalMath.DECIMAL_FRAC_DIGITS). Mul rescales any product
// with scale > this back down to it with HALF_UP (away-from-zero) rounding, exactly as
// DecimalMul.fastLongScale does. Hard-coded from the consensus constant; if the JVM constant
// ever changes this file must change with it (it is consensus-critical, so it will not change
// silently).
constexpr int32_t kDecimalFracDigits = 20;

// Negative result scales cannot arise here: from_jvm hands DEC_LONG operands with scale in 0..20
// (canonical stripped), so add/sub result scale = max(aScale, bScale) >= 0 and mul newScale =
// aScale + bScale >= 0. The Truffle reference's `-DECIMAL_INT_DIGITS` negative-scale guard is only
// reachable from raw BigDecimals with negative scales, which the value ABI never produces — so it is
// intentionally omitted (a negative scale would be a from_jvm invariant violation, not a runtime op).

// Compile-time POW10 table, 10^0 .. 10^18, mirroring tf_op_nodes.kt DECIMAL_POW10 /
// Tf_LongScaleDecimal.POW10_TABLE. Built the same way (repeated *10) so any value matches the
// JVM table bit-for-bit. Indexed only with a value already proven in 0..kMaxAlignDelta.
constexpr std::array<int64_t, kMaxAlignDelta + 1> kPow10 = [] {
    std::array<int64_t, kMaxAlignDelta + 1> t{};
    int64_t p = 1;
    for (int i = 0; i <= kMaxAlignDelta; ++i) {
        t[i] = p;
        if (i < kMaxAlignDelta) p *= 10;
    }
    return t;
}();

// LIVE. The decimal long-fit fast path is enabled: from_jvm now hands inline DEC_LONG operands
// (canonical stripped (mantissa, scale)) across the value ABI, and the runtime envelope-escape
// channel (rell_jit_escape, jni_bridge.cpp) lets any operand/intermediate that leaves the envelope
// at RUNTIME (wrong tag, scale > kDecLongMaxScale, alignment/op overflow) re-run the whole call on
// the interpreter. A native result is therefore committed ONLY when the op provably stayed in the
// envelope; otherwise the JVM produces it. This closes the determinism gap the gated flag guarded:
// there is no silent wrap, and the escape is bit-exact (the interpreter is the oracle).
//
// DETERMINISM (M1/M2/M3): the emitters below produce the EXACT canonical stripped DEC_LONG the
// interpreter would (Rt_DecimalValue.get -> Lib_DecimalMath.scale -> from_jvm strip):
//   add/sub — exact aligned sum, then stripTrailingZeros (M1: 0-x negation = exact, then strip).
//   mul     — exact product at scale s1+s2; if that scale > DECIMAL_FRAC_DIGITS, rescale to 20 with
//             HALF_UP away-from-zero (M2), THEN stripTrailingZeros. Matches a.multiply(b) reboxed
//             via Rt_DecimalValue.get then cracked by from_jvm.
// Every result is stripped, so it equals what from_jvm(to_jvm(result)) yields — the M3 invariant.

// True iff `type` is the primitive decimal type. Mirrors jni_bridge's isIntegerType gate,
// retargeted to DECIMAL. Kept local next to its overlay; used by future call-site lowering to
// recognise decimal operands.
[[maybe_unused]] bool isDecimalType(const ir::Type *type) {
    if (type == nullptr) return false;
    const auto *prim = type->type_as_PrimitiveType();
    return prim != nullptr && prim->kind() == ir::PrimitiveTypeKind_DECIMAL;
}

// Emit `{ result:i64, overflow:i1 }` for a signed-overflow-checked binary op via the LLVM
// llvm.s{add,sub,mul}.with.overflow.i64 intrinsics. These map directly to the host's checked
// arithmetic; the `overflow` bit is the IR equivalent of the reference's caught
// ArithmeticException from Math.{add,subtract,multiply}Exact. Returns {value, overflowBit}.
struct CheckedI64 {
    llvm::Value *value;     // i64 result (valid only when !overflow)
    llvm::Value *overflow;  // i1, true ⇒ the op overflowed signed i64
};

CheckedI64 emitCheckedOp(EmitContext &ec, llvm::Intrinsic::ID id, llvm::Value *a, llvm::Value *b) {
    auto &b_ = ec.builder();
    auto *i64Ty = b_.getInt64Ty();
    auto *callee = llvm::Intrinsic::getOrInsertDeclaration(&ec.module(), id, {i64Ty});
    auto *agg = b_.CreateCall(callee, {a, b}, "ovf");
    return {b_.CreateExtractValue(agg, 0, "ovf.val"),
            b_.CreateExtractValue(agg, 1, "ovf.bit")};
}


// Materialise the POW10 table as a private module-global `[19 x i64]` constant and return a load of
// element `idx` (a runtime i32 scale, already proven 0..kMaxAlignDelta by the caller's escape guard).
// DETERMINISM: this is a pure table load of the SAME 10^k constants the JVM POW10_TABLE holds; the
// runtime index is bit-exact (no arithmetic), so a data-dependent scale alignment is provable — the
// only freedom is `idx`, which the caller bounds to the table before calling.
llvm::Value *emitPow10Load(EmitContext &ec, llvm::Value *idx) {
    auto &b = ec.builder();
    auto &mod = ec.module();
    auto *i64Ty = b.getInt64Ty();
    auto *arrTy = llvm::ArrayType::get(i64Ty, kMaxAlignDelta + 1);
    llvm::GlobalVariable *gv = mod.getNamedGlobal("rell_dec_pow10");
    if (gv == nullptr) {
        std::vector<llvm::Constant *> elems;
        elems.reserve(kMaxAlignDelta + 1);
        for (int i = 0; i <= kMaxAlignDelta; ++i) {
            elems.push_back(llvm::ConstantInt::get(i64Ty, kPow10[static_cast<size_t>(i)]));
        }
        auto *init = llvm::ConstantArray::get(arrTy, elems);
        gv = new llvm::GlobalVariable(mod, arrTy, /*isConstant=*/true,
                                      llvm::GlobalValue::PrivateLinkage, init, "rell_dec_pow10");
    }
    // GEP [arrTy]* gv, 0, idx  ->  i64*
    llvm::Value *idx64 = b.CreateSExt(idx, i64Ty, "pow10.idx");
    llvm::Value *slot = b.CreateInBoundsGEP(arrTy, gv, {b.getInt64(0), idx64}, "pow10.slot");
    return b.CreateLoad(i64Ty, slot, "pow10");
}

// Pack a DEC_LONG runtime value (tag DEC_LONG, runtime i32 scale, i64 mantissa).
llvm::Value *packDecLong(EmitContext &ec, llvm::Value *scaleI32, llvm::Value *mantissa) {
    return ec.packInline(RellTag::DEC_LONG, scaleI32, mantissa);
}

// Strip trailing decimal zeros from a (mantissa, scale) pair IN IR, matching
// Tf_LongScaleDecimal.tryFromCanonical's strip loop and BigDecimal.stripTrailingZeros for the
// long-fit case: `while (scale > 0 && mantissa % 10 == 0) { mantissa /= 10; scale--; }`. The loop
// trip count is bounded by `scale` (<= 20), so it always terminates; division by the constant 10 is
// exact integer arithmetic. Produces the canonical stripped form so the result equals what from_jvm
// would crack from the reboxed value (M3). Returns {strippedMantissa, strippedScale} via out-params.
//
// IR shape: a small natural loop (header/body/exit) with phi nodes for (mantissa, scale).
void emitStripTrailingZeros(EmitContext &ec, llvm::Value *mantissaIn, llvm::Value *scaleIn,
                            llvm::Value **mantissaOut, llvm::Value **scaleOut) {
    auto &b = ec.builder();
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();
    auto *i64Ty = b.getInt64Ty();
    auto *i32Ty = b.getInt32Ty();

    llvm::BasicBlock *preBB = b.GetInsertBlock();
    auto *headBB = llvm::BasicBlock::Create(ctx, "strip.head", fn);
    auto *bodyBB = llvm::BasicBlock::Create(ctx, "strip.body", fn);
    auto *exitBB = llvm::BasicBlock::Create(ctx, "strip.exit", fn);
    b.CreateBr(headBB);

    // Header: phi the running (mantissa, scale); test `scale > 0 && mantissa % 10 == 0`.
    b.SetInsertPoint(headBB);
    llvm::PHINode *mPhi = b.CreatePHI(i64Ty, 2, "strip.m");
    llvm::PHINode *sPhi = b.CreatePHI(i32Ty, 2, "strip.s");
    mPhi->addIncoming(mantissaIn, preBB);
    sPhi->addIncoming(scaleIn, preBB);
    llvm::Value *scalePos = b.CreateICmpSGT(sPhi, b.getInt32(0), "strip.spos");
    llvm::Value *rem = b.CreateSRem(mPhi, b.getInt64(10), "strip.rem");
    llvm::Value *divisible = b.CreateICmpEQ(rem, b.getInt64(0), "strip.div10");
    llvm::Value *cont = b.CreateAnd(scalePos, divisible, "strip.cont");
    b.CreateCondBr(cont, bodyBB, exitBB);

    // Body: mantissa /= 10; scale -= 1; loop back.
    b.SetInsertPoint(bodyBB);
    llvm::Value *mNext = b.CreateSDiv(mPhi, b.getInt64(10), "strip.mnext");
    llvm::Value *sNext = b.CreateSub(sPhi, b.getInt32(1), "strip.snext");
    mPhi->addIncoming(mNext, bodyBB);
    sPhi->addIncoming(sNext, bodyBB);
    b.CreateBr(headBB);

    // Exit: the canonical stripped pair.
    b.SetInsertPoint(exitBB);
    *mantissaOut = mPhi;
    *scaleOut = sPhi;
}

// Align `mant` from `fromScale` up to `toScale` (toScale >= fromScale), multiplying by
// 10^(toScale - fromScale). Escapes (rell_jit_escape) if the delta exceeds kMaxAlignDelta or the
// multiply overflows i64. Returns the aligned mantissa (defined even on the escape edge — discarded
// JVM-side). DETERMINISM: mirrors fastLongScaleAddSub's alignment; the delta>kMaxAlignDelta and
// overflow edges are exactly where the reference returns null (defer to the wider tier).
llvm::Value *emitAlignUp(EmitContext &ec, llvm::Value *mant, llvm::Value *fromScale,
                         llvm::Value *toScale) {
    auto &b = ec.builder();
    llvm::Value *delta = b.CreateSub(toScale, fromScale, "align.delta");
    // delta > kMaxAlignDelta -> escape (10^delta would not fit i64).
    llvm::Value *deltaTooBig = b.CreateICmpSGT(delta, b.getInt32(kMaxAlignDelta), "align.toobig");
    emitJitEscapeIf(ec, deltaTooBig);
    // Clamp delta into [0, kMaxAlignDelta] so the POW10 load is always in-bounds even on the
    // (already-escaped) too-big edge; the value is discarded JVM-side.
    llvm::Value *clamped = b.CreateSelect(deltaTooBig, b.getInt32(0), delta, "align.clamp");
    llvm::Value *pow = emitPow10Load(ec, clamped);
    auto chk = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, mant, pow);
    emitJitEscapeIf(ec, chk.overflow);
    return chk.value;
}

// Runtime-scale add/sub. Aligns the smaller-scale operand up to the larger scale, adds/subtracts
// (overflow-checked, escape on overflow), then strips trailing zeros to the canonical DEC_LONG.
// DETERMINISM (M1): negation is lowered as `0 - x` (lower_ops.cpp), where the zero is DEC_LONG
// (mantissa 0, scale 0); alignment of a zero mantissa is exact (0 * 10^k == 0), the subtract is
// exact, and the strip yields the same canonical form the interpreter's subtract(ZERO, x) + rebox
// would. add/sub result scale before stripping is max(aScale, bScale) <= 20; Lib_DecimalMath.scale
// pads that to 20 and from_jvm strips back — equivalent to stripping the exact aligned sum here.
llvm::Value *emitDecLongAddSubRuntime(EmitContext &ec, llvm::Value *aMant, llvm::Value *aScale,
                                      llvm::Value *bMant, llvm::Value *bScale, bool subtract) {
    auto &b = ec.builder();
    // Common scale = max(aScale, bScale); align the smaller up.
    llvm::Value *aLess = b.CreateICmpSLT(aScale, bScale, "addsub.aless");
    llvm::Value *common = b.CreateSelect(aLess, bScale, aScale, "addsub.common");
    llvm::Value *la = emitAlignUp(ec, aMant, aScale, common);
    llvm::Value *lb = emitAlignUp(ec, bMant, bScale, common);
    auto op = emitCheckedOp(
        ec, subtract ? llvm::Intrinsic::ssub_with_overflow : llvm::Intrinsic::sadd_with_overflow,
        la, lb);
    emitJitEscapeIf(ec, op.overflow);
    llvm::Value *mOut = nullptr;
    llvm::Value *sOut = nullptr;
    emitStripTrailingZeros(ec, op.value, common, &mOut, &sOut);
    return packDecLong(ec, sOut, mOut);
}

// Runtime-scale multiply. newScale = aScale + bScale; product = aMant*bMant (overflow-checked,
// escape on overflow). If newScale <= DECIMAL_FRAC_DIGITS the exact product is the value; otherwise
// rescale down by k = newScale - 20 with HALF_UP away-from-zero (M2), escaping if k > kMaxAlignDelta.
// Then strip trailing zeros to canonical form.
// DETERMINISM (M2): the JVM does a.multiply(b) (exact, scale s1+s2) then Rt_DecimalValue.get ->
// Lib_DecimalMath.scale -> setScale(20, HALF_UP) when scale>20, then from_jvm strips. HALF_UP rounds
// when |rem|*2 >= divisor, magnitude away from zero (+/-1 by product sign). The tie |rem|*2==divisor
// rounds away from zero (NOT half-even). This emitter reproduces that exactly, then strips.
llvm::Value *emitDecLongMulRuntime(EmitContext &ec, llvm::Value *aMant, llvm::Value *aScale,
                                   llvm::Value *bMant, llvm::Value *bScale) {
    auto &b = ec.builder();
    llvm::Value *newScale = b.CreateAdd(aScale, bScale, "mul.newscale");
    auto prod = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, aMant, bMant);
    emitJitEscapeIf(ec, prod.overflow);
    llvm::Value *product = prod.value;

    // Branch on newScale <= DECIMAL_FRAC_DIGITS.
    llvm::Function *fn = b.GetInsertBlock()->getParent();
    auto &ctx = ec.ctx();
    llvm::Value *noRescale =
        b.CreateICmpSLE(newScale, b.getInt32(kDecimalFracDigits), "mul.norescale");
    auto *noBB = llvm::BasicBlock::Create(ctx, "mul.no_rescale", fn);
    auto *yesBB = llvm::BasicBlock::Create(ctx, "mul.rescale", fn);
    auto *joinBB = llvm::BasicBlock::Create(ctx, "mul.join", fn);
    b.CreateCondBr(noRescale, noBB, yesBB);

    // No rescale: (product, newScale).
    b.SetInsertPoint(noBB);
    llvm::Value *noMant = product;
    llvm::Value *noScale = newScale;
    b.CreateBr(joinBB);

    // Rescale: k = newScale - 20; escape if k > kMaxAlignDelta; HALF_UP away-from-zero.
    b.SetInsertPoint(yesBB);
    llvm::Value *k = b.CreateSub(newScale, b.getInt32(kDecimalFracDigits), "mul.k");
    llvm::Value *kTooBig = b.CreateICmpSGT(k, b.getInt32(kMaxAlignDelta), "mul.ktoobig");
    emitJitEscapeIf(ec, kTooBig);
    llvm::Value *kClamped = b.CreateSelect(kTooBig, b.getInt32(0), k, "mul.kclamp");
    llvm::Value *divisor = emitPow10Load(ec, kClamped);
    llvm::Value *quotient = b.CreateSDiv(product, divisor, "mul.q");
    llvm::Value *remainder = b.CreateSRem(product, divisor, "mul.r");
    // HALF_UP away-from-zero: round when |remainder| * 2 >= divisor. |rem| < divisor <= 10^18 and
    // 2*10^18 < Long.MAX, so the doubled abs cannot overflow i64.
    llvm::Value *zero = b.getInt64(0);
    llvm::Value *remNeg = b.CreateICmpSLT(remainder, zero, "mul.remneg");
    llvm::Value *remAbs = b.CreateSelect(remNeg, b.CreateNeg(remainder), remainder, "mul.remabs");
    llvm::Value *twiceAbs = b.CreateShl(remAbs, b.getInt64(1), "mul.2x");
    llvm::Value *roundUp = b.CreateICmpSGE(twiceAbs, divisor, "mul.roundup");
    llvm::Value *prodNonNeg = b.CreateICmpSGE(product, zero, "mul.prodnonneg");
    llvm::Value *delta = b.CreateSelect(prodNonNeg, b.getInt64(1), b.getInt64(-1), "mul.delta");
    auto roundedChk = emitCheckedOp(ec, llvm::Intrinsic::sadd_with_overflow, quotient, delta);
    // Rounding addExact can overflow only at the i64 boundary; escape if it does AND we round.
    llvm::Value *roundOvf = b.CreateAnd(roundUp, roundedChk.overflow, "mul.roundovf");
    emitJitEscapeIf(ec, roundOvf);
    llvm::Value *rescaledMant = b.CreateSelect(roundUp, roundedChk.value, quotient, "mul.rescaled");
    llvm::Value *rescaledScale = b.getInt32(kDecimalFracDigits);
    yesBB = b.GetInsertBlock();  // emitJitEscapeIf moved the insert point past new blocks.
    b.CreateBr(joinBB);

    // Join the two arms, then strip.
    b.SetInsertPoint(joinBB);
    llvm::PHINode *mantPhi = b.CreatePHI(b.getInt64Ty(), 2, "mul.mant");
    llvm::PHINode *scalePhi = b.CreatePHI(b.getInt32Ty(), 2, "mul.scale");
    mantPhi->addIncoming(noMant, noBB);
    mantPhi->addIncoming(rescaledMant, yesBB);
    scalePhi->addIncoming(noScale, noBB);
    scalePhi->addIncoming(rescaledScale, yesBB);

    llvm::Value *mOut = nullptr;
    llvm::Value *sOut = nullptr;
    emitStripTrailingZeros(ec, mantPhi, scalePhi, &mOut, &sOut);
    return packDecLong(ec, sOut, mOut);
}

// Runtime-scale compare. Returns an i32 sign in {-1,0,1} (Long.compareTo semantics) via *signOut.
// Aligns the smaller-scale operand up; escapes on alignment overflow. Scale-aware so 1.0 vs 1.00
// (different (mantissa, scale)) compare equal — though, since from_jvm hands stripped canonical
// forms, equal values already share (mantissa, scale) and the same-scale fast branch suffices.
void emitDecLongCompareRuntime(EmitContext &ec, llvm::Value *aMant, llvm::Value *aScale,
                               llvm::Value *bMant, llvm::Value *bScale, llvm::Value **signOut) {
    auto &b = ec.builder();
    llvm::Value *aLess = b.CreateICmpSLT(aScale, bScale, "cmp.aless");
    llvm::Value *common = b.CreateSelect(aLess, bScale, aScale, "cmp.common");
    llvm::Value *la = emitAlignUp(ec, aMant, aScale, common);
    llvm::Value *lb = emitAlignUp(ec, bMant, bScale, common);
    llvm::Value *gt = b.CreateZExt(b.CreateICmpSGT(la, lb, "cmp.gt"), b.getInt32Ty(), "cmp.gt.i32");
    llvm::Value *lt = b.CreateZExt(b.CreateICmpSLT(la, lb, "cmp.lt"), b.getInt32Ty(), "cmp.lt.i32");
    *signOut = b.CreateSub(gt, lt, "cmp.sign");
}

}  // namespace

// Exposed for lower_ops.cpp's CmpInfo path: scale-aware decimal compare producing an i32 sign in
// {-1,0,1}. Caller proved both operands are DEC_LONG-tagged (tag guard) and unpacked their mantissa
// + scale. On a scale-alignment overflow the emitter escapes (rell_jit_escape) and the sign is
// meaningless (discarded JVM-side). Bit-exact with Rt_Comparator (BigDecimal.compareTo) for the
// long-fit envelope.
llvm::Value *emitDecimalCompareSign(EmitContext &ec, llvm::Value *aMant, llvm::Value *aScale,
                                    llvm::Value *bMant, llvm::Value *bScale) {
    llvm::Value *sign = nullptr;
    emitDecLongCompareRuntime(ec, aMant, aScale, bMant, bScale, &sign);
    return sign;
}

// Binary-op overlay for decimal operands. The caller (lower_ops.cpp) has tag-guarded both operands
// (escape if not DEC_LONG) and unpacked their i64 mantissas + i32 scales. Contract (rell_runtime.h
// §9): on success return the result runtime value; set *escaped=true / return nullptr only for an
// op this overlay does not handle (never for a runtime envelope miss — that uses rell_jit_escape).
//
// LIVE: add/sub/mul are lowered inline via the runtime-scale emitters above, which call
// rell_jit_escape on any runtime overflow / scale-out-of-range edge so the JVM re-runs the call.
// DIV/MOD always escape unconditionally (no Long-arithmetic leaf — MathContext rounding + scale
// growth; the JVM owns Lib_DecimalMath.divide/remainder).
llvm::Value *intrinsicDecimal(EmitContext &ec, ir::BinaryOp op, llvm::Value *aMant,
                              llvm::Value *aScale, llvm::Value *bMant, llvm::Value *bScale,
                              bool *escaped) {
    if (escaped == nullptr) {
        return ec.failExpr("intrinsicDecimal: null escaped sink");
    }
    *escaped = false;
    if (aMant == nullptr || aScale == nullptr || bMant == nullptr || bScale == nullptr) {
        return ec.failExpr("intrinsicDecimal: null operand");
    }

    switch (op) {
        case ir::BinaryOp_ADD_DECIMAL:
            return emitDecLongAddSubRuntime(ec, aMant, aScale, bMant, bScale, /*subtract=*/false);
        case ir::BinaryOp_SUB_DECIMAL:
            return emitDecLongAddSubRuntime(ec, aMant, aScale, bMant, bScale, /*subtract=*/true);
        case ir::BinaryOp_MUL_DECIMAL:
            return emitDecLongMulRuntime(ec, aMant, aScale, bMant, bScale);

        case ir::BinaryOp_DIV_DECIMAL:
        case ir::BinaryOp_MOD_DECIMAL:
            // DETERMINISM: no Long-arithmetic div/mod leaf exists even in the proven Truffle
            // reference — decimal division needs MathContext rounding (HALF_UP to scale 20) and
            // scale growth a hand-rolled i64 path cannot reproduce bit-for-bit. Escape so the whole
            // call re-runs on the interpreter, which owns Lib_DecimalMath.divide/remainder.
            emitJitEscape(ec);
            // Produce a defined dummy DEC_LONG (discarded JVM-side once the escape flag is seen).
            return packDecLong(ec, ec.builder().getInt32(0), aMant);

        default:
            // EQ/NE and comparisons (CmpInfo) are handled in lower_ops.cpp, not here.
            *escaped = true;
            return nullptr;
    }
}

}  // namespace rell::llvm_rt
