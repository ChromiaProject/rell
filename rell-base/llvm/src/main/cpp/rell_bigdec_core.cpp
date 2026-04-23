// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigdec_core.cpp — core RellBigDec operations: construction, sign/unary,
// scale-aligned add/subtract, value comparison (compareTo / compareMagnitude),
// representation equality (equals), precision, decimal-point shifts
// (movePointLeft/Right, scaleByPowerOfTen) and truncating toBigInteger.
//
// Faithful translation of the corresponding java.math.BigDecimal methods
// (OpenJDK 21, jdk-21+35), always using the BigInteger (`unscaled_`) path and
// ignoring every `intCompact`/INFLATED fast-path branch (we never store the
// compact form — see rell_bigdec.h). Rounding, division, setScale, toString and
// parsing live in sibling translation units; this file implements only the ops
// listed above.

#include "rell_bigdec.h"

namespace rell::num {

// =====================================================================================
// Construction / factories
// =====================================================================================

RellBigDec::RellBigDec() : unscaled_(RellBigInt::zero()), scale_(0) {}

RellBigDec::RellBigDec(RellBigInt unscaled, int32_t scale)
    : unscaled_(std::move(unscaled)), scale_(scale) {}

// valueOf(int64) / valueOf(int64, int32) are defined in rell_bigdec_str.cpp.

RellBigDec RellBigDec::zero() {
    return RellBigDec();
}

RellBigDec RellBigDec::one() {
    return RellBigDec(RellBigInt::one(), 0);
}

RellBigDec RellBigDec::ten() {
    return RellBigDec(RellBigInt::fromInt64(10), 0);
}

// =====================================================================================
// Unary  (BigDecimal.negate() / abs() — always the intVal path)
// =====================================================================================

RellBigDec RellBigDec::negate() const {
    return RellBigDec(unscaled_.negate(), scale_);
}

RellBigDec RellBigDec::abs() const {
    return signum() < 0 ? negate() : *this;
}

// =====================================================================================
// precision()  (BigDecimal.precision() -> bigDigitLength)
//
// r = (int)(((bitLength(U) + 1) * 646456993L) >>> 31);  // ~ floor((bitLen+1)*log10(2))
// (|U| < 10^r) ? r : r+1.   U == 0 -> 1.
// TODO(perf): BigDecimal caches this in a `precision` field; we recompute each call.
// =====================================================================================

int32_t RellBigDec::precision() const {
    if (unscaled_.signum() == 0) {
        return 1;
    }
    int64_t bitLen = unscaled_.bitLength();
    // 646456993/2^31 approximates log10(2) accurately up to the max reportable
    // bitLength; the unsigned >>31 matches Java's `>>> 31`.
    int32_t r = static_cast<int32_t>(
        (static_cast<uint64_t>(bitLen + 1) * 646456993ULL) >> 31);
    // bigTenToThe(r) is a sibling-provided static (rell_bigdec.h).
    RellBigInt tenToR = bigTenToThe(r);
    // compareMagnitude: |U| vs 10^r (10^r is positive, U may be negative).
    return (unscaled_.compareMagnitude(tenToR) < 0) ? r : r + 1;
}

// =====================================================================================
// add / subtract  (BigDecimal.add(BigInteger,int,BigInteger,int))
//
// rscale starts at this.scale; sdiff = this.scale - augend.scale. If sdiff < 0 raise
// THIS unscaled by 10^(-sdiff) and adopt augend.scale; if sdiff > 0 raise AUGEND's
// unscaled by 10^sdiff. Net result scale = max(thisScale, augendScale). Then add the
// (now scale-aligned) unscaled values. Exact, no rounding.
// =====================================================================================

RellBigDec RellBigDec::add(const RellBigDec &augend) const {
    RellBigInt fst = unscaled_;
    RellBigInt snd = augend.unscaled_;
    int32_t rscale = scale_;
    int64_t sdiff = static_cast<int64_t>(scale_) - augend.scale_;
    if (sdiff != 0) {
        if (sdiff < 0) {
            int32_t raise = static_cast<int32_t>(-sdiff);
            rscale = augend.scale_;
            fst = bigMultiplyPowerTen(fst, raise);
        } else {
            int32_t raise = static_cast<int32_t>(sdiff);
            snd = bigMultiplyPowerTen(snd, raise);
        }
    }
    RellBigInt sum = fst.add(snd);
    return RellBigDec(std::move(sum), rscale);
}

RellBigDec RellBigDec::subtract(const RellBigDec &subtrahend) const {
    // this - subtrahend == this + (-subtrahend); BigDecimal negates the subtrahend's
    // unscaled value and routes through the same add(BigInteger,...) helper.
    return add(subtrahend.negate());
}

// =====================================================================================
// compareTo / compareMagnitude  (BigDecimal.compareTo / compareMagnitude, intVal path)
// =====================================================================================

int RellBigDec::compareTo(const RellBigDec &val) const {
    int xsign = signum();
    int ysign = val.signum();
    if (xsign != ysign) {
        return (xsign > ysign) ? 1 : -1;
    }
    if (xsign == 0) {
        return 0;
    }
    int cmp = compareMagnitude(val);
    return (xsign > 0) ? cmp : -cmp;
}

int RellBigDec::compareMagnitude(const RellBigDec &val) const {
    // Both inflated path. Handle zeros first (mirrors xs==0 / ys==0 shortcuts).
    if (unscaled_.signum() == 0) {
        return (val.unscaled_.signum() == 0) ? 0 : -1;
    }
    if (val.unscaled_.signum() == 0) {
        return 1;
    }

    int64_t sdiff = static_cast<int64_t>(scale_) - val.scale_;
    if (sdiff != 0) {
        // Avoid aligning scales if the adjusted exponents already differ.
        int64_t xae = static_cast<int64_t>(precision()) - scale_;      // order of magnitude of this
        int64_t yae = static_cast<int64_t>(val.precision()) - val.scale_;
        if (xae < yae) {
            return -1;
        }
        if (xae > yae) {
            return 1;
        }
        // Adjusted exponents equal: align by raising the SMALLER-scale operand.
        if (sdiff < 0) {
            RellBigInt rb = bigMultiplyPowerTen(unscaled_, static_cast<int32_t>(-sdiff));
            return rb.compareMagnitude(val.unscaled_);
        } else {  // sdiff > 0
            RellBigInt rb = bigMultiplyPowerTen(val.unscaled_, static_cast<int32_t>(sdiff));
            return unscaled_.compareMagnitude(rb);
        }
    }
    // Equal scales: compare unscaled magnitudes directly.
    return unscaled_.compareMagnitude(val.unscaled_);
}

// =====================================================================================
// equals  (BigDecimal.equals — value AND scale; 2.0 != 2.00)
// =====================================================================================

bool RellBigDec::equals(const RellBigDec &val) const noexcept {
    if (scale_ != val.scale_) {
        return false;
    }
    return unscaled_.equals(val.unscaled_);
}

// =====================================================================================
// movePointLeft / movePointRight / scaleByPowerOfTen
//
// movePointLeft(n): newScale = scale + n; value unchanged, scale raised. If newScale
//   ends up negative, normalize it to 0 via setScale(0, UNNECESSARY) (exact — the
//   negative scale guarantees the dropped positions are implicit trailing zeros, so
//   no rounding actually occurs).
// movePointRight(n): newScale = scale - n; symmetric.
// scaleByPowerOfTen(n): value * 10^n == set scale = scale - n, unscaled unchanged
//   (may leave a negative scale; BigDecimal does not normalize it here).
//
// We use int64_t for the intermediate newScale to mirror BigDecimal's `(long)scale + n`
// guard against int overflow; within the Rell envelope it always fits int32.
// =====================================================================================

RellBigDec RellBigDec::movePointLeft(int32_t n) const {
    if (n == 0 && scale_ >= 0) {
        return *this;
    }
    int64_t newScale64 = static_cast<int64_t>(scale_) + n;
    // TODO(scale-overflow): BigDecimal.checkScale throws on int overflow for nonzero
    // values; the Rell envelope keeps scales tiny (<= 20 canonical) so this never
    // trips. If it ever could, this truncation would be wrong.
    int32_t newScale = static_cast<int32_t>(newScale64);
    RellBigDec num(unscaled_, newScale);
    return num.scale_ < 0 ? num.setScale(0, RoundingMode::UNNECESSARY) : num;
}

RellBigDec RellBigDec::movePointRight(int32_t n) const {
    if (n == 0 && scale_ >= 0) {
        return *this;
    }
    int64_t newScale64 = static_cast<int64_t>(scale_) - n;
    int32_t newScale = static_cast<int32_t>(newScale64);
    RellBigDec num(unscaled_, newScale);
    return num.scale_ < 0 ? num.setScale(0, RoundingMode::UNNECESSARY) : num;
}

RellBigDec RellBigDec::scaleByPowerOfTen(int32_t n) const {
    int64_t newScale64 = static_cast<int64_t>(scale_) - n;
    int32_t newScale = static_cast<int32_t>(newScale64);
    return RellBigDec(unscaled_, newScale);
}

// =====================================================================================
// toBigInteger  (BigDecimal.toBigInteger == setScale(0, ROUND_DOWN).unscaledValue())
//
// Truncate toward zero, dropping the fraction. We replicate the value of
// setScale(0, DOWN) directly: scale <= 0 -> U * 10^(-scale); scale > 0 -> U / 10^scale
// (truncating integer division, which IS what divideAndRound(U, 10^scale, DOWN)
// produces — DOWN never increments). This avoids constructing the intermediate
// RellBigDec and depending on the sibling setScale for the common path.
// =====================================================================================

RellBigInt RellBigDec::toBigInteger() const {
    if (scale_ == 0) {
        return unscaled_;
    }
    if (scale_ < 0) {
        // value = U * 10^(-scale), already an integer.
        return bigMultiplyPowerTen(unscaled_, static_cast<int32_t>(-static_cast<int64_t>(scale_)));
    }
    // scale > 0: integer part = trunc(U / 10^scale). RellBigInt::divide truncates
    // toward zero, exactly matching setScale(0, ROUND_DOWN).
    RellBigInt divisor = bigTenToThe(scale_);
    return unscaled_.divide(divisor);
}

}  // namespace rell::num
