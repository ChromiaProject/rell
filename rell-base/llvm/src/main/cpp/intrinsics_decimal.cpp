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
// WHY ADD/SUB/MUL ESCAPE (the conservative posture)
// -------------------------------------------------
// The reference fast paths are RUNTIME-DATA-DEPENDENT: whether a value stays in-envelope depends
// on the operand mantissas/scales, which here are runtime SSA values (llvm::Value*), not compile-
// time constants. The Truffle code answers "in envelope?" with `Math.addExact`/`multiplyExact`
// catching ArithmeticException and `return null` — i.e. a RUNTIME branch into a slow path that
// reboxes the operands and calls back into Lib_DecimalMath.
//
// Emitting that runtime branch correctly needs the slow-path CONTINUATION — the SysFnId of the
// decimal op plus the call args and RellCallCtx — none of which the overlay signature carries
// (it gets only the EmitContext and the unpacked i64 operands). The overlay therefore CANNOT
// emit the `rell_sysfn_call` fallback for the overflow branch itself. Per the determinism rule,
// emitting an inline result WITHOUT that runtime guard would silently truncate/wrap on overflow
// or skip the cross-leaf canonicalisation — a consensus divergence. So the active overlay
// `*escaped = true`s every decimal op: lower_ops.cpp, which DOES hold the call context, emits the
// JVM slow path. This is the correct, deterministic floor (mirrors intrinsics_bytearray.cpp's
// all-escape posture for the same "no safe inline without the slow-path branch" reason).
//
// The proven Long-arithmetic algorithms are still committed here as reusable IR emitters
// (emitDecLongAddSub / emitDecLongMul / emitDecLongCompare) — overflow-checked via the LLVM
// sadd/ssub/smul.with.overflow intrinsics and a compile-time-materialised POW10 table — so that
// once lower_ops.cpp is extended to hand this overlay a runtime slow-path block (the JVM-reboxing
// continuation to branch to on the `overflow` edge), the fast path is wired by swapping the
// escape for a call to these emitters. Until then they are reachable only behind the
// `kDecLongRuntimeFastPathEnabled` gate (false), so the active behaviour is all-escape and the
// emitters are exercised by the type checker / -Wunused gate, never by live lowering.
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

// Lower bound on the int128/long path's negative scale: DecimalMul rejects newScale below
// -DECIMAL_INT_DIGITS up front (those fail Lib_DecimalMath.scale anyway). 131072.
constexpr int32_t kDecimalIntDigits = 131072;

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

// MASTER SWITCH for the runtime inline decimal fast path. FALSE today: the overlay entry escapes
// every decimal op to the JVM because it cannot emit the overflow-branch slow-path continuation
// (see the file header). Flip to true ONLY when lower_ops.cpp is extended to pass this overlay a
// runtime slow-path block to branch to on the overflow / out-of-envelope edge, AND the wiring is
// validated bit-for-bit against Rt_InterpreterImpl. Until then, leaving it false is the
// determinism-correct floor.
constexpr bool kDecLongRuntimeFastPathEnabled = false;

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

// Materialise 10^k as an i64 constant for a COMPILE-TIME-KNOWN k in 0..kMaxAlignDelta. The
// reference only ever indexes DECIMAL_POW10 with a delta already bounded by kMaxAlignDelta, so a
// dynamic (runtime) scale delta is NOT a candidate for an i64 constant — a runtime delta means
// the fast path is not statically expressible and the op escapes. This helper exists for the
// future const-scale specialisation (when both operand scales are compile-time constants).
[[maybe_unused]] llvm::Constant *pow10Const(EmitContext &ec, int32_t k) {
    // Caller MUST have checked 0 <= k <= kMaxAlignDelta; out-of-range is a lowering bug.
    return ec.builder().getInt64(kPow10[static_cast<size_t>(k)]);
}

// ----------------------------------------------------------------------------------------------
// Proven Long-arithmetic fast-path IR emitters (gated; not live until lower_ops.cpp supplies the
// runtime slow-path continuation). Each is a faithful IR transcription of its Kotlin reference;
// the `*overflowOut` i1 collects every edge on which the reference returns null (defer to JVM).
// The CALLER branches: !overflow ⇒ use the returned DEC_LONG; overflow ⇒ JVM slow path.
//
// IMPORTANT: these assume both operand scales are in 0..kMaxAlignDelta (the DEC_LONG invariant)
// AND that the scale delta is computed dynamically. Because a runtime POW10 lookup is needed for
// scale alignment and a data-dependent table index cannot be a single i64 constant, these
// emitters are written for the CONST-SCALE case only — they require both scales to be LLVM
// constants so the delta and POW10 factor fold at emit time. lower_ops.cpp must check that
// precondition (ConstantInt scales) before calling; otherwise it escapes. This keeps the IR
// straight-line and bit-exact rather than emitting a runtime POW10 gather we cannot prove.
// ----------------------------------------------------------------------------------------------

// Returns the constant i32 scale if `scale` is an llvm::ConstantInt in 0..kMaxAlignDelta, else -1.
int32_t constScaleOrNeg(llvm::Value *scale) {
    if (auto *ci = llvm::dyn_cast_or_null<llvm::ConstantInt>(scale)) {
        const int64_t s = ci->getSExtValue();
        if (s >= 0 && s <= kMaxAlignDelta) return static_cast<int32_t>(s);
    }
    return -1;
}

// Pack a DEC_LONG runtime value (tag DEC_LONG, given scale constant, i64 mantissa).
llvm::Value *packDecLong(EmitContext &ec, int32_t scale, llvm::Value *mantissa) {
    return ec.packInline(RellTag::DEC_LONG, ec.builder().getInt32(scale), mantissa);
}

// fastLongScaleAddSub transcription (tf_op_nodes.kt:370-415), const-scale specialisation.
// Aligns the smaller-scale mantissa by 10^delta (delta <= kMaxAlignDelta, else escape), then
// add/sub-Exact at the larger scale. Sets *overflowOut on any alignment OR final-op overflow.
// Returns the DEC_LONG result value (valid when !*overflowOut).
llvm::Value *emitDecLongAddSub(EmitContext &ec, llvm::Value *aMant, int32_t aScale,
                               llvm::Value *bMant, int32_t bScale, bool subtract,
                               llvm::Value **overflowOut) {
    auto &b_ = ec.builder();
    llvm::Value *lm = aMant;
    llvm::Value *rm = bMant;
    int32_t resultScale;
    llvm::Value *alignOvf = b_.getFalse();

    if (aScale == bScale) {
        resultScale = aScale;
    } else if (aScale < bScale) {
        const int32_t delta = bScale - aScale;
        if (delta > kMaxAlignDelta) {  // reference: return null
            *overflowOut = b_.getTrue();
            return packDecLong(ec, aScale, aMant);  // dummy; caller ignores on overflow
        }
        auto chk = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, aMant,
                                 b_.getInt64(kPow10[static_cast<size_t>(delta)]));
        lm = chk.value;
        alignOvf = chk.overflow;
        resultScale = bScale;
    } else {
        const int32_t delta = aScale - bScale;
        if (delta > kMaxAlignDelta) {
            *overflowOut = b_.getTrue();
            return packDecLong(ec, aScale, aMant);
        }
        auto chk = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, bMant,
                                 b_.getInt64(kPow10[static_cast<size_t>(delta)]));
        rm = chk.value;
        alignOvf = chk.overflow;
        resultScale = aScale;
    }

    auto op = emitCheckedOp(
        ec, subtract ? llvm::Intrinsic::ssub_with_overflow : llvm::Intrinsic::sadd_with_overflow,
        lm, rm);
    *overflowOut = b_.CreateOr(alignOvf, op.overflow, "addsub.ovf");
    return packDecLong(ec, resultScale, op.value);
}

// DecimalMul.fastLongScale transcription (tf_op_nodes.kt:518-548), const-scale specialisation.
// newScale = s1 + s2; product = m1*m2 (overflow-checked). If newScale <= kDecimalFracDigits ⇒
// (product, newScale). Else drop k = newScale - 20 digits with HALF_UP away-from-zero rounding
// (k <= kMaxAlignDelta, else escape), result scale kDecimalFracDigits. Negative-scale guard:
// newScale < -kDecimalIntDigits ⇒ escape. Sets *overflowOut on any escape/overflow edge.
llvm::Value *emitDecLongMul(EmitContext &ec, llvm::Value *aMant, int32_t aScale,
                            llvm::Value *bMant, int32_t bScale, llvm::Value **overflowOut) {
    auto &b_ = ec.builder();
    const int32_t newScale = aScale + bScale;

    // newScale and the rescale shift k are compile-time constants here (both scales are const),
    // so the whole branch structure folds — no runtime scale dispatch.
    if (newScale < -kDecimalIntDigits) {  // reference: return null
        *overflowOut = b_.getTrue();
        return packDecLong(ec, 0, aMant);  // dummy; caller ignores on overflow
    }

    auto prod = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, aMant, bMant);
    llvm::Value *product = prod.value;

    if (newScale <= kDecimalFracDigits) {
        *overflowOut = prod.overflow;
        // resultScale = newScale; clamp the (compile-time) scale into the packed i32. The
        // reference allows newScale down to -kDecimalIntDigits; DEC_LONG carries an i32 scale,
        // which holds that range, so no clamp is needed.
        return packDecLong(ec, newScale, product);
    }

    const int32_t k = newScale - kDecimalFracDigits;
    if (k > kMaxAlignDelta) {  // reference: return null
        *overflowOut = b_.getTrue();
        return packDecLong(ec, 0, aMant);
    }
    auto *divisor = b_.getInt64(kPow10[static_cast<size_t>(k)]);
    auto *quotient = b_.CreateSDiv(product, divisor, "mul.q");
    auto *remainder = b_.CreateSRem(product, divisor, "mul.r");

    // HALF_UP away-from-zero: round when |remainder| * 2 >= divisor. |remainder| < divisor <=
    // 10^18 and 2 * 10^18 < Long.MAX, so the doubled abs fits i64 (no overflow) — same proof as
    // the reference comment. abs(remainder): remainder is in (-divisor, divisor); negate if < 0.
    auto *zero = b_.getInt64(0);
    auto *remNeg = b_.CreateICmpSLT(remainder, zero, "rem.neg");
    auto *remAbs = b_.CreateSelect(remNeg, b_.CreateNeg(remainder), remainder, "rem.abs");
    auto *twiceAbs = b_.CreateShl(remAbs, b_.getInt64(1), "rem.2x");
    auto *roundUp = b_.CreateICmpSGE(twiceAbs, divisor, "round.up");

    // delta = product >= 0 ? +1 : -1
    auto *prodNonNeg = b_.CreateICmpSGE(product, zero, "prod.nonneg");
    auto *delta = b_.CreateSelect(prodNonNeg, b_.getInt64(1), b_.getInt64(-1), "round.delta");
    // rounded = roundUp ? addExact(quotient, delta) : quotient
    auto roundedChk = emitCheckedOp(ec, llvm::Intrinsic::sadd_with_overflow, quotient, delta);
    auto *rounded = b_.CreateSelect(roundUp, roundedChk.value, quotient, "mul.rounded");
    // overflow edges: product overflow OR (roundUp AND rounding addExact overflow).
    auto *roundOvf = b_.CreateAnd(roundUp, roundedChk.overflow, "round.ovf");
    *overflowOut = b_.CreateOr(prod.overflow, roundOvf, "mul.ovf");

    return packDecLong(ec, kDecimalFracDigits, rounded);
}

// DecimalCmp.compareLongScale transcription (tf_op_nodes.kt:871-896), const-scale specialisation.
// Same scale ⇒ compare mantissas. Else align the smaller-scale mantissa by 10^delta (delta <=
// kMaxAlignDelta, else escape) and compare. On alignment overflow the reference defers to the
// 128-bit tier (return null) — so we escape too (*overflowOut). Produces an i32 sign in {-1,0,1}
// in `*signOut` (valid when !*overflowOut). The caller maps the sign to the CmpOp (LT/GT/LE/GE).
// [[maybe_unused]]: the comparison fast path is wired through the CmpInfo lowering, not the
// binary-op overlay, so this emitter has no live caller until that path is extended.
[[maybe_unused]] llvm::Value *emitDecLongCompare(EmitContext &ec, llvm::Value *aMant, int32_t aScale,
                                llvm::Value *bMant, int32_t bScale, llvm::Value **signOut,
                                llvm::Value **overflowOut) {
    auto &b_ = ec.builder();
    llvm::Value *lm = aMant;
    llvm::Value *rm = bMant;
    *overflowOut = b_.getFalse();

    if (aScale != bScale) {
        const int32_t delta = (aScale > bScale) ? (aScale - bScale) : (bScale - aScale);
        if (delta > kMaxAlignDelta) {  // reference: return null ⇒ defer to JVM
            *overflowOut = b_.getTrue();
        } else {
            auto *pow = b_.getInt64(kPow10[static_cast<size_t>(delta)]);
            if (aScale < bScale) {
                auto chk = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, aMant, pow);
                lm = chk.value;
                *overflowOut = chk.overflow;  // align overflow ⇒ JVM (don't answer by sign)
            } else {
                auto chk = emitCheckedOp(ec, llvm::Intrinsic::smul_with_overflow, bMant, pow);
                rm = chk.value;
                *overflowOut = chk.overflow;
            }
        }
    }

    // sign = (lm > rm) - (lm < rm), as i32 in {-1,0,1}. Matches Long.compareTo's sign.
    auto *gt = b_.CreateZExt(b_.CreateICmpSGT(lm, rm, "cmp.gt"), b_.getInt32Ty(), "cmp.gt.i32");
    auto *lt = b_.CreateZExt(b_.CreateICmpSLT(lm, rm, "cmp.lt"), b_.getInt32Ty(), "cmp.lt.i32");
    *signOut = b_.CreateSub(gt, lt, "cmp.sign");
    return *signOut;
}

}  // namespace

// Binary-op overlay for decimal operands. lower_ops.cpp hands us the two operands already
// unpacked into i64 mantissas + i32 scales (both proven in-envelope DEC_LONGs). Contract
// (rell_runtime.h §9): on success return the result runtime value; to defer, set *escaped = true
// and return nullptr. *escaped true and a non-null return are mutually exclusive.
//
// ACTIVE BEHAVIOUR: all-escape. Every decimal binary op routes to the JVM via the universal JNI
// caller. The proven inline emitters above are reachable only behind the (false)
// kDecLongRuntimeFastPathEnabled gate; until lower_ops.cpp supplies the runtime overflow-branch
// slow-path continuation, escaping is the only way to honour the determinism rule for the
// runtime-data-dependent overflow / scale-alignment edges.
llvm::Value *intrinsicDecimal(EmitContext &ec, ir::BinaryOp op, llvm::Value *aMant,
                              llvm::Value *aScale, llvm::Value *bMant, llvm::Value *bScale,
                              bool *escaped) {
    // Defensive: a null sink or operand is an upstream lowering bug, not an envelope miss —
    // soft-fail the whole function rather than risk an incorrect emit.
    if (escaped == nullptr) {
        return ec.failExpr("intrinsicDecimal: null escaped sink");
    }
    if (aMant == nullptr || aScale == nullptr || bMant == nullptr || bScale == nullptr) {
        *escaped = false;
        return ec.failExpr("intrinsicDecimal: null operand");
    }

    // The (currently-disabled) const-scale fast path. Both operand scales must be compile-time
    // constants in 0..kMaxAlignDelta for the alignment/rescale factors to fold to i64 constants;
    // a runtime scale would need a data-dependent POW10 gather we will not prove bit-exact here.
    if (kDecLongRuntimeFastPathEnabled) {
        const int32_t as = constScaleOrNeg(aScale);
        const int32_t bs = constScaleOrNeg(bScale);
        if (as >= 0 && bs >= 0) {
            llvm::Value *overflow = nullptr;
            switch (op) {
                case ir::BinaryOp_ADD_DECIMAL:
                case ir::BinaryOp_SUB_DECIMAL: {
                    // NOTE: when wired, the caller passes a slow-path block to branch to on
                    // `overflow`; here we only produce the fast value + overflow predicate.
                    (void)emitDecLongAddSub(ec, aMant, as, bMant, bs,
                                            op == ir::BinaryOp_SUB_DECIMAL, &overflow);
                    break;
                }
                case ir::BinaryOp_MUL_DECIMAL:
                    (void)emitDecLongMul(ec, aMant, as, bMant, bs, &overflow);
                    break;
                default:
                    break;
            }
        }
    }

    switch (op) {
        case ir::BinaryOp_ADD_DECIMAL:
        case ir::BinaryOp_SUB_DECIMAL:
        case ir::BinaryOp_MUL_DECIMAL:
            // DETERMINISM: add/sub/mul have a proven Long-arithmetic fast path (emitDecLong*
            // above), but it is correct ONLY guarded by a runtime overflow / scale-alignment
            // branch into a JVM slow path. The overlay signature carries no slow-path
            // continuation (no SysFnId / args / RellCallCtx), so we cannot emit that branch
            // here. Emitting the inline result unguarded would wrap on Long overflow or skip the
            // canonical-scale rescale — a consensus divergence. Escape to the JVM, which owns
            // Lib_DecimalMath.{add,subtract,multiply}.
            *escaped = true;
            return nullptr;

        case ir::BinaryOp_DIV_DECIMAL:
        case ir::BinaryOp_MOD_DECIMAL:
            // DETERMINISM: no Long-arithmetic div/mod leaf exists even in the proven Truffle
            // reference — decimal division needs MathContext rounding (HALF_UP to scale 20) and
            // scale growth that a hand-rolled i64 path cannot reproduce bit-for-bit. Always JVM.
            *escaped = true;
            return nullptr;

        default:
            // Comparisons (<, <=, >, >=) arrive through the CmpInfo path in lower_ops.cpp, not as
            // a plain BinaryOp here; equality (EQ/NE) likewise. Any other op reaching this overlay
            // for a decimal operand is unexpected.
            // DETERMINISM: never emit an approximate native op for an unrecognised decimal binary
            // op — route to the JVM, the canonical owner of decimal semantics.
            *escaped = true;
            return nullptr;
    }
}

}  // namespace rell::llvm_rt
