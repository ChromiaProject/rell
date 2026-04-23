// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigint_extra.cpp — RellBigInt "extra" operations declared in rell_bigint.h:
//   pow(uint32_t), sqrt(), bitLength(), longValue(), longValueExact().
//
// Faithful to java.math.BigInteger (OpenJDK 21, jdk-21+35):
//   * pow            — BigInteger.pow(int): factor out powers of two, square-and-
//                      multiply over the odd part, shift back, sign = negative iff
//                      base<0 && exponent odd.
//   * sqrt           — BigInteger.sqrt() / MutableBigInteger.sqrt(): Newton iteration
//                      converging to the FLOOR of the exact square root. Long fast path
//                      when bitLength()<=63; otherwise seed from a double estimate and
//                      refine via xk1 = (xk + this/xk)/2 until xk1 >= xk.
//   * bitLength      — BigInteger.bitLength(): minimal two's-complement bit count
//                      excluding the sign, with the negative-power-of-two off-by-one.
//   * longValue(Exact) — low 64 bits (silent truncation) / throwing variant.
//
// PURE C++17: depends only on rell_bigint.h + the standard library. No JNI/LLVM/FBS.
// The Rell range/overflow envelope ("bigint:overflow") is layered separately in
// rell_bigint_math.*; this file throws RellArithError only for the genuinely-arithmetic
// conditions BigInteger itself signals (negative exponent, sqrt of a negative value,
// longValueExact out of long range).

#include "rell_bigint.h"

#include <cmath>

namespace rell::num {

// =====================================================================================
// Small bit helpers (operate on the big-endian uint32_t magnitude in `mag`).
// =====================================================================================

namespace {

// Number of significant bits in a non-zero 32-bit word (1..32). bitLengthOfWord(0)==0.
inline int bitLengthOfWord(uint32_t w) noexcept {
    int n = 0;
    while (w != 0) {
        w >>= 1;
        ++n;
    }
    return n;
}

}  // namespace


// =====================================================================================
// pow(exponent) — BigInteger.pow(int)
//
//   exponent < 0          -> throw "Negative exponent".
//   exponent == 0         -> ONE  (including 0**0 == ONE).
//   signum_ == 0 (base 0) -> ZERO for exponent>0 (handled by the generic path).
//
//   Algorithm (faithful to OpenJDK):
//     1. Let `partToSquare = abs()`. Factor out its trailing zero BITS:
//        powersOfTwo = getLowestSetBit(partToSquare); partToSquare >>= powersOfTwo, so
//        partToSquare is now ODD. The total left shift owed back is
//        bitsToShift = powersOfTwo * exponent.
//     2. If the odd part is 1 (bitLength()==1, i.e. the original abs() was a pure power
//        of two), the answer is (±1) << bitsToShift with the sign rule below.
//     3. Otherwise square-and-multiply: scan exponent bits low->high, squaring an
//        accumulator each step and multiplying in `partToSquare` when the current bit
//        is set. Then left-shift the accumulator by bitsToShift.
//     4. Sign: negate the result iff (base < 0 && exponent is odd).
//
//   We don't have a public shift on RellBigInt, so the "factor out powers of two"
//   optimization is realized purely through multiplication: 2^k is built as
//   ONE multiplied appropriately. To stay simple AND exact we implement the shift via
//   repeated multiplication by the base's own structure is unnecessary — instead we keep
//   the OpenJDK shape but express `<< n` as multiply by 2^n, where 2^n is itself produced
//   by pow on TWO. To avoid infinite recursion we compute 2^n by binary build below.
// =====================================================================================

namespace {

// Build 2^n as a RellBigInt magnitude directly (n >= 0). Used to realize the
// "shift back the factored-out powers of two" step without a public shift op.
RellBigInt powerOfTwo(uint64_t n) {
    // 2^n has bit n set. Big-endian magnitude: limb index from the most-significant end.
    // Number of 32-bit limbs = floor(n/32)+1; the set bit is at position (n%32) within
    // the least-significant limb group.
    const uint64_t limbCount = n / 32 + 1;
    // Construct via fromInt64 + repeated squaring to keep within the public API and stay
    // obviously-correct. 2^n = (2)^n using exponentiation by squaring on the value 2.
    (void)limbCount;
    RellBigInt result = RellBigInt::one();
    RellBigInt base = RellBigInt::fromInt64(2);
    uint64_t e = n;
    while (e > 0) {
        if (e & 1ULL) {
            result = result.multiply(base);
        }
        e >>= 1;
        if (e > 0) {
            base = base.multiply(base);
        }
    }
    return result;
}

// getLowestSetBit on a non-negative magnitude value: index of the least-significant set
// bit. Returns -1 for zero (mirrors BigInteger.getLowestSetBit()).
int64_t lowestSetBit(const RellBigInt &v) {
    if (v.isZero()) {
        return -1;
    }
    // Find the least-significant non-zero limb (big-endian: scan from the end).
    // We reconstruct from the public API via repeated division by 2^32 would be costly;
    // instead use the two's-complement byte image, which is little-endian-friendly here.
    std::vector<uint8_t> be = v.abs().toBytes();  // big-endian, may have a leading 0x00
    // Scan from least-significant byte (end) for the first non-zero byte.
    int64_t bitIndex = 0;
    for (std::size_t k = 0; k < be.size(); ++k) {
        uint8_t byte = be[be.size() - 1 - k];
        if (byte != 0) {
            int b = 0;
            while ((byte & 1u) == 0) {
                byte >>= 1;
                ++b;
            }
            return bitIndex + b;
        }
        bitIndex += 8;
    }
    return -1;  // unreachable for non-zero v
}

}  // namespace

RellBigInt RellBigInt::pow(uint32_t exponent) const {
    // exponent is unsigned in our API, so "negative exponent" can't be expressed; the
    // // header contract maps Rell's non-negative exponent onto uint32_t. A negative Rell
    // exponent is rejected by the caller before reaching here. (No throw path needed.)

    if (exponent == 0) {
        return RellBigInt::one();  // x**0 == 1, including 0**0 == 1.
    }
    if (signum_ == 0) {
        return RellBigInt();  // 0**e == 0 for e>0.
    }

    const bool negResult = (signum_ < 0) && (exponent & 1u);

    // partToSquare = |this|, with trailing zero bits factored out.
    RellBigInt partToSquare = this->abs();
    const int64_t powersOfTwo = lowestSetBit(partToSquare);  // >= 0 (non-zero magnitude)
    const uint64_t bitsToShift = static_cast<uint64_t>(powersOfTwo) * exponent;

    // Divide out the 2^powersOfTwo factor so partToSquare becomes odd.
    if (powersOfTwo > 0) {
        partToSquare = partToSquare.divide(powerOfTwo(static_cast<uint64_t>(powersOfTwo)));
    }

    RellBigInt magResult;

    if (partToSquare.bitLength() == 1) {
        // The odd part is exactly 1: |this| was a pure power of two. Result magnitude is
        // 2^bitsToShift.
        magResult = (bitsToShift == 0) ? RellBigInt::one() : powerOfTwo(bitsToShift);
    } else {
        // Square-and-multiply over the odd part, low bit -> high bit.
        RellBigInt answer = RellBigInt::one();
        RellBigInt base = partToSquare;
        uint32_t workingExponent = exponent;
        while (workingExponent != 0) {
            if (workingExponent & 1u) {
                answer = answer.multiply(base);
            }
            workingExponent >>= 1;
            if (workingExponent != 0) {
                base = base.multiply(base);
            }
        }
        // Restore the factored-out powers of two: answer << bitsToShift.
        if (bitsToShift > 0) {
            answer = answer.multiply(powerOfTwo(bitsToShift));
        }
        magResult = answer;
    }

    return negResult ? magResult.negate() : magResult;
}

// =====================================================================================
// sqrt() — BigInteger.sqrt() (delegates to MutableBigInteger.sqrt())
//
//   Returns floor(sqrt(this)) for this >= 0; throws for this < 0.
//
//   Special cases: 0 -> 0; this in {1,2,3} -> 1.
//
//   Long fast path (bitLength() <= 63): take v = longValueExact(), seed
//   xk = floor(sqrt((double)v)), then Newton-iterate xk1 = (xk + v/xk)/2 (integer ops)
//   until xk1 >= xk, returning xk. (Integer Newton from an over- or near-estimate
//   converges to the floor.)
//
//   General path (bitLength() > 63): seed from a double estimate of the value shifted
//   into positive-long range by an even shift, take ceil(sqrt(d)) of the shifted value,
//   shift the estimate back by shift/2, then Newton-iterate
//   xk1 = (xk + this/xk)/2 using big-integer divide, until xk1 >= xk; return xk.
//
//   The big-integer divide here truncates toward zero (== floor for non-negative
//   operands), which is exactly what the Newton floor-sqrt recurrence requires.
// =====================================================================================
RellBigInt RellBigInt::sqrt() const {
    if (signum_ < 0) {
        throw RellArithError("", "negative BigInteger");
    }
    if (signum_ == 0) {
        return RellBigInt();  // sqrt(0) == 0
    }

    const int64_t bl = bitLength();

    // this in {1,2,3} -> 1 (single limb whose unsigned value is < 4).
    if (mag_.size() == 1 && mag_[0] < 4u) {
        return RellBigInt::one();
    }

    if (bl <= 63) {
        // Long fast path. value fits a positive signed 64-bit long.
        const int64_t v = this->longValueExact();
        int64_t xk = static_cast<int64_t>(std::floor(std::sqrt(static_cast<double>(v))));
        if (xk <= 0) {
            xk = 1;  // guard against a degenerate double estimate
        }
        for (;;) {
            const int64_t xk1 = (xk + v / xk) / 2;
            if (xk1 >= xk) {
                return RellBigInt::fromInt64(xk);
            }
            xk = xk1;
        }
    }

    // General path: bitLength() > 63.
    const RellBigInt two = RellBigInt::fromInt64(2);

    // Even right shift into positive-long range. shift = bitLength - 63, rounded UP to
    // even so that shift/2 is an integer and the shifted value still fits a positive long.
    int64_t shift = bl - 63;
    if (shift % 2 == 1) {
        ++shift;
    }

    // Shifted value = this / 2^shift (this >= 0, so truncation == floor == right shift).
    const RellBigInt shiftDivisor = powerOfTwo(static_cast<uint64_t>(shift));
    const RellBigInt shifted = this->divide(shiftDivisor);

    // Seed estimate: ceil(sqrt((double)shifted)), then shift back by shift/2.
    // `shifted` fits a positive long by construction (<= 2^63-1 magnitude window).
    const double d = static_cast<double>(shifted.longValueExact());
    int64_t seed = static_cast<int64_t>(std::ceil(std::sqrt(d)));
    if (seed <= 0) {
        seed = 1;
    }
    RellBigInt xk = RellBigInt::fromInt64(seed).multiply(
        powerOfTwo(static_cast<uint64_t>(shift / 2)));
    if (xk.isZero()) {
        xk = RellBigInt::one();  // never divide by zero in the iteration
    }

    // Refine: xk1 = (xk + this/xk)/2, until non-decreasing; return the last xk.
    for (;;) {
        RellBigInt xk1 = this->divide(xk).add(xk).divide(two);
        if (xk1.compareTo(xk) >= 0) {
            return xk;
        }
        xk = xk1;
    }
}

}  // namespace rell::num
