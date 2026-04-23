// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigdec.h — rell::num::RellBigDec: a faithful C++17 reimplementation of
// java.math.BigDecimal (OpenJDK 21, jdk-21+35), used by the Rell LLVM backend to
// evaluate `decimal` arithmetic natively, bit-exact with the JVM, with zero JNI.
//
// This is a PURE numerics header layered directly on RellBigInt (rell_bigint.h):
// it depends only on the C++ standard library and RellBigInt. No JNI, no LLVM, no
// FlatBuffers. The Rell semantic envelope (canonical scale 20, integer-digit range
// limit, overflow error codes/messages, normalization) lives in rell_decimal_math.*
// layered ON TOP of this class; this header models ONLY the faithful OpenJDK
// BigDecimal arithmetic and rounding.
//
// =====================================================================================
// REPRESENTATION (mirrors OpenJDK BigDecimal exactly, MINUS the intCompact fast path)
// =====================================================================================
//
//   RellBigInt unscaled_;   // the unscaled value U  (BigDecimal.intVal)
//   int32_t    scale_;      // the scale S           (BigDecimal.scale)
//
// The represented number is exactly  U * 10^(-S).  S may be negative (then the value
// is U * 10^|S|, an integer with trailing zeros) or positive (a fractional value).
//
// OpenJDK additionally keeps a `long intCompact` fast path (and a sentinel INFLATED)
// purely for performance; it is numerically redundant with intVal and we DELIBERATELY
// omit it — we always use the RellBigInt `unscaled_` form. This costs speed, never
// correctness: every value U*10^-S has exactly one (U,S) with U not constrained to be
// coprime to 10 (BigDecimal does NOT auto-strip trailing zeros — 2.0 and 2.00 are
// distinct objects with scales 1 and 2; they compare EQUAL but are not equals()).
//
// =====================================================================================
// ROUNDING (faithful port of BigDecimal.divideAndRound / needIncrement /
//           commonNeedIncrement; see algorithm notes at bottom of file)
// =====================================================================================
//
// RoundingMode mirrors java.math.RoundingMode and its legacy int codes (the ROUND_*
// ordinals BigDecimal switches on). The increment decision is driven by:
//   * cmpFracHalf = sign(2*|remainder| - |divisor|)  in {-1,0,+1}
//       (-1: fraction < 0.5, closer to the truncated quotient;
//         0: fraction exactly 0.5, a tie;
//        +1: fraction > 0.5, closer to the next quotient.)
//   * oddQuot     = low bit of the truncated quotient magnitude (for HALF_EVEN).
//   * qsign       = sign of the (true) quotient (for CEILING/FLOOR).
// See commonNeedIncrement notes for the exact switch.

#ifndef RELL_BIGDEC_H
#define RELL_BIGDEC_H

#include <cstdint>
#include <string>
#include <utility>

#include "rell_bigint.h"

namespace rell::num {

// -------------------------------------------------------------------------------------
// RoundingMode — mirrors java.math.RoundingMode. The integer values are the OpenJDK
// `BigDecimal.ROUND_*` legacy ordinals that divideAndRound/commonNeedIncrement switch
// over, so the rounding logic ports across with identical numeric codes.
// -------------------------------------------------------------------------------------
enum class RoundingMode : int {
    UP = 0,           // away from zero
    DOWN = 1,         // toward zero (truncate)
    CEILING = 2,      // toward +infinity
    FLOOR = 3,        // toward -infinity
    HALF_UP = 4,      // ties away from zero        (Rell's mode)
    HALF_DOWN = 5,    // ties toward zero
    HALF_EVEN = 6,    // ties to even
    UNNECESSARY = 7,  // assert exact; throws if rounding would occur
};

// =====================================================================================
// RellBigDec — faithful java.math.BigDecimal port. Immutable value semantics: every
// operation returns a fresh RellBigDec; *this is never mutated.
//
// NOTE: like BigDecimal, this class does NOT normalize trailing zeros. Scale is an
// explicit, value-bearing field. The Rell canonicalization (pin scale to 20, range
// checks) is a SEPARATE pass in rell_decimal_math.h.
// =====================================================================================
class RellBigDec {
public:
    // ---------------------------------------------------------------------------------
    // Construction / factories
    // ---------------------------------------------------------------------------------

    // ZERO: unscaled 0, scale 0.
    RellBigDec();

    // Exact: value = unscaled * 10^(-scale). Does not strip or pad.
    RellBigDec(RellBigInt unscaled, int32_t scale);

    // valueOf(unscaledVal, scale): BigDecimal.valueOf(long, int) — unscaledVal * 10^-scale.
    static RellBigDec valueOf(int64_t unscaledVal, int32_t scale);
    static RellBigDec valueOf(int64_t val);  // scale 0

    static RellBigDec zero();
    static RellBigDec one();
    static RellBigDec ten();

    // Parse a decimal string exactly as BigDecimal(String):
    //   [sign] digits [ '.' digits ] [ ('e'|'E') [sign] digits ]
    // At least one digit must appear in the significand (before or after the point).
    // The scale of the result = (#fraction digits) - exponent. Examples:
    //   "1.00"   -> unscaled 100,  scale 2
    //   "1E3"    -> unscaled 1,    scale -3   (value 1000)
    //   "0.500"  -> unscaled 500,  scale 3
    //   "-0"     -> unscaled 0,    scale 0
    // Throws RellArithError("","invalid decimal string ...") on malformed input.
    static RellBigDec parse(const std::string &s);

    // ---------------------------------------------------------------------------------
    // Inspectors
    // ---------------------------------------------------------------------------------

    const RellBigInt &unscaledValue() const noexcept { return unscaled_; }
    int32_t scale() const noexcept { return scale_; }

    int signum() const noexcept { return unscaled_.signum(); }
    bool isZero() const noexcept { return unscaled_.isZero(); }

    // precision(): number of decimal digits in the unscaled value (>= 1; ZERO has
    // precision 1). Faithful to BigDecimal.precision()/bigDigitLength: estimate from
    // bitLength via the 646456993/2^31 ~ log10(2) constant, then correct by one
    // compare against 10^estimate. See notes.
    int32_t precision() const;

    // ---------------------------------------------------------------------------------
    // Unary
    // ---------------------------------------------------------------------------------

    RellBigDec negate() const;  // -this, same scale.
    RellBigDec abs() const;     // |this|, same scale.

    // ---------------------------------------------------------------------------------
    // Comparison
    // ---------------------------------------------------------------------------------

    // compareTo: VALUE comparison across differing scales (2.0 vs 2.00 -> 0). Aligns the
    // lower-scale operand up by 10^|scaleDiff| before comparing magnitudes; short-circuits
    // on sign, and (when scales differ) on adjusted exponent precision()-scale. -1/0/+1.
    int compareTo(const RellBigDec &val) const;

    // equals: BigDecimal.equals — value AND scale must match (2.0 != 2.00).
    bool equals(const RellBigDec &val) const noexcept;

    // ---------------------------------------------------------------------------------
    // Exact arithmetic (no rounding; result scale per BigDecimal rules)
    // ---------------------------------------------------------------------------------

    // add/subtract: align to the LARGER scale (raise the smaller-scale operand's unscaled
    // value by 10^scaleDiff), add unscaled values; result scale = max(scaleA, scaleB).
    RellBigDec add(const RellBigDec &augend) const;
    RellBigDec subtract(const RellBigDec &subtrahend) const;

    // multiply: unscaled product; result scale = scaleA + scaleB. Exact.
    RellBigDec multiply(const RellBigDec &multiplicand) const;

    // remainder: this - this.divideToIntegralValue(divisor) * divisor. The integer
    // quotient truncates toward zero; the remainder carries the dividend's sign. Result
    // value is exact; its scale = max(scaleA, scaleB). Throws on divisor == 0.
    RellBigDec remainder(const RellBigDec &divisor) const;

    // ---------------------------------------------------------------------------------
    // Division with explicit target scale + rounding (BigDecimal.divide(d,scale,mode))
    // ---------------------------------------------------------------------------------

    // divide(divisor, targetScale, mode): result has EXACTLY scale=targetScale, rounded
    // per `mode`. This is the method Rell's decimal `/` uses (targetScale=20, HALF_UP).
    // Algorithm (faithful BigDecimal.divide(BigInteger,int,BigInteger,int,int,int)):
    //   Let it reach target scale by scaling either dividend or divisor up by 10^raise,
    //   then divideAndRound. Throws RellArithError("","/ by zero") on divisor == 0;
    //   throws "Rounding necessary" for UNNECESSARY when inexact.
    RellBigDec divide(const RellBigDec &divisor, int32_t targetScale, RoundingMode mode) const;

    // ---------------------------------------------------------------------------------
    // Scale manipulation
    // ---------------------------------------------------------------------------------

    // setScale(newScale, mode): exact rescale. If newScale >= scale, pad by multiplying
    // unscaled by 10^(newScale-scale) (mode irrelevant, always exact). If newScale < scale,
    // divide unscaled by 10^(scale-newScale) and round per mode. ZERO rescales freely.
    RellBigDec setScale(int32_t newScale, RoundingMode mode) const;

    // movePointLeft(n): value/10^n. movePointRight(n): value*10^n. Implemented via scale
    // adjustment exactly as BigDecimal: movePointRight lowers scale (floored at 0 only by
    // negative-scale handling), movePointLeft raises it; both keep the unscaled value when
    // the new scale stays non-negative, else multiply. See .cpp.
    RellBigDec movePointLeft(int32_t n) const;
    RellBigDec movePointRight(int32_t n) const;

    // scaleByPowerOfTen(n): value * 10^n by setting scale = scale - n (unscaled unchanged).
    RellBigDec scaleByPowerOfTen(int32_t n) const;

    // stripTrailingZeros: remove trailing-zero factors of ten from the unscaled value,
    // lowering scale accordingly, until the unscaled value is not divisible by 10 (or is
    // 0 -> returns ZERO at scale 0). Faithful to createAndStripZerosToMatchScale with
    // preferredScale = -infinity. NOTE: BigDecimal can leave a NEGATIVE scale here
    // (e.g. 600 -> unscaled 6, scale -2); this matches the JVM.
    RellBigDec stripTrailingZeros() const;

    // ---------------------------------------------------------------------------------
    // Conversions
    // ---------------------------------------------------------------------------------

    // toBigInteger: truncate toward zero (drop the fraction). = unscaled / 10^scale when
    // scale>0, unscaled * 10^(-scale) when scale<0.
    RellBigInt toBigInteger() const;

    // toString: faithful BigDecimal.toString() — scientific/engineering layout. Uses a
    // plain form when scale>=0 and the adjusted exponent (precision-1-scale) >= -6,
    // otherwise E-notation. See layoutChars notes.
    std::string toString() const;

    // toPlainString: BigDecimal.toPlainString() — never E-notation; always
    // unscaled-with-decimal-point form (may emit many leading/trailing zeros).
    std::string toPlainString() const;

private:
    RellBigInt unscaled_;
    int32_t scale_;

    // compareMagnitude(val): |this| vs |val|, scale-aligned, ignoring sign. {-1,0,+1}.
    int compareMagnitude(const RellBigDec &val) const;

    // divideToIntegralValue(divisor): integer part of this/divisor, truncated toward zero,
    // as an exact unscaled RellBigInt (the integer quotient). Used by remainder().
    RellBigInt divideToIntegralQuotient(const RellBigDec &divisor) const;

    // ----- rounding helpers (the faithful needIncrement chain) -----
    //
    // divideAndRoundToScale: given dividend' and divisor' already aligned so that
    // floor(dividend'/divisor') has the desired target scale, compute the rounded
    // quotient unscaled value (the increment decision via needIncrement). `qsign` is the
    // sign of the quotient. Returns the rounded quotient magnitude-with-sign.
    static RellBigInt divideAndRound(const RellBigInt &dividend, const RellBigInt &divisor,
                                     RoundingMode mode);

    // needIncrement(divisor, mode, qsign, q, r): is the truncated quotient q to be bumped
    // by qsign? r is the (nonzero) remainder, divisor the (positive-magnitude) divisor.
    // cmpFracHalf = compareHalf(|r|, |divisor|) = sign(2|r| - |divisor|).
    static bool needIncrement(const RellBigInt &divisor, RoundingMode mode, int qsign,
                              const RellBigInt &q, const RellBigInt &r);

    // commonNeedIncrement: the shared switch. cmpFracHalf in {-1,0,+1}; oddQuot = q is odd.
    static bool commonNeedIncrement(RoundingMode mode, int qsign, int cmpFracHalf,
                                    bool oddQuot);

    // compareHalf(r, divisor): sign(2*|r| - |divisor|), in {-1,0,+1}. Both args are
    // compared by magnitude (sign ignored).
    static int compareHalf(const RellBigInt &r, const RellBigInt &divisor);

    // Multiply unscaled by 10^n (n >= 0). Helper for scale alignment.
    static RellBigInt bigMultiplyPowerTen(const RellBigInt &v, int32_t n);
    // 10^n as a RellBigInt (n >= 0).
    static RellBigInt bigTenToThe(int32_t n);
};

inline bool operator==(const RellBigDec &a, const RellBigDec &b) noexcept {
    return a.equals(b);
}
inline bool operator!=(const RellBigDec &a, const RellBigDec &b) noexcept {
    return !a.equals(b);
}
inline bool operator<(const RellBigDec &a, const RellBigDec &b) { return a.compareTo(b) < 0; }
inline bool operator>(const RellBigDec &a, const RellBigDec &b) { return a.compareTo(b) > 0; }
inline bool operator<=(const RellBigDec &a, const RellBigDec &b) { return a.compareTo(b) <= 0; }
inline bool operator>=(const RellBigDec &a, const RellBigDec &b) { return a.compareTo(b) >= 0; }

// =====================================================================================
// ALGORITHM NOTES FOR THE PORT AGENT (rell_bigdec_*.cpp)
// =====================================================================================
//
// All line references are to OpenJDK 21 (jdk-21+35) BigDecimal.java. We always use the
// `intVal` (RellBigInt) path; ignore every `intCompact`/INFLATED branch.
//
// ----- commonNeedIncrement(mode, qsign, cmpFracHalf, oddQuot)  -----
// EXACT switch (this is the only place rounding policy lives):
//   UNNECESSARY  -> throw RellArithError("","Rounding necessary")
//   UP           -> return true
//   DOWN         -> return false
//   CEILING      -> return qsign > 0
//   FLOOR        -> return qsign < 0
//   HALF_UP / HALF_DOWN / HALF_EVEN:
//       if cmpFracHalf < 0  -> false   (fraction < 0.5: stay)
//       if cmpFracHalf > 0  -> true    (fraction > 0.5: bump)
//       else (tie, ==0):
//           HALF_DOWN -> false
//           HALF_UP   -> true          (Rell's mode: ties away from zero)
//           HALF_EVEN -> oddQuot       (bump only if truncated quotient is odd)
//
// ----- needIncrement -----
// Precondition: remainder r != 0. cmpFracHalf = compareHalf(r, divisor) where
// compareHalf returns sign(2*|r| - |divisor|): compute 2*|r| (left shift / *2 on the
// magnitude) and compareMagnitude against |divisor|. oddQuot = (q's low limb & 1) != 0,
// i.e. q is odd. qsign passed in from the caller.
//
// ----- divideAndRound(dividend, divisor, mode) [our private helper] -----
// q, r = dividend.divideAndRemainder(divisor)   (truncates toward zero; r has dividend
// sign). qsign = sign(dividend) * sign(divisor) (i.e. the quotient's sign; if q==0 but a
// bump is needed, the increment direction is qsign). If r == 0: return q. Else if
// needIncrement(divisor, mode, qsign, q, r): return q + (qsign>0 ? +1 : -1) [add ONE of
// the quotient's sign to the magnitude — i.e. q.add(qsign>0?ONE:ONE.negate())]. Else q.
// IMPORTANT: BigDecimal increments the MAGNITUDE away from zero; since q already carries
// qsign, adding a same-signed ONE achieves "q + qsign" on the signed value. When q==0,
// qsign still determines the rounded direction (e.g. 1/3 HALF_UP at scale 0 with negative
// quotient).
//
// ----- divide(divisor, targetScale, mode)  (BigDecimal.divide all-BigInteger path) -----
// Goal: produce floor/rounded (this/divisor) at exactly `targetScale`. Let
//   ds = this.scale, vs = divisor.scale, U = this.unscaled, V = divisor.unscaled.
//   if  (targetScale + vs) > ds:        // need to scale the DIVIDEND up
//       raise = (targetScale + vs) - ds
//       q = divideAndRound(U * 10^raise, V, mode)
//   else:                               // scale the DIVISOR up
//       raise = (ds - targetScale) - vs
//       q = divideAndRound(U, V * 10^raise, mode)
//   result = RellBigDec(q, targetScale).
// (Both branches arrange that the integer division yields the unscaled value at
// targetScale. checkScale overflow guards are unnecessary within the Rell envelope.)
// Throw "/ by zero" if V == 0 BEFORE scaling.
//
// ----- setScale(newScale, mode) -----
// if newScale == scale: return this. if signum==0: return ZERO at newScale.
// if newScale > scale:  raise = newScale-scale; unscaled' = U * 10^raise; scale'=newScale.
// else:                 drop  = scale-newScale; q = divideAndRound(U, 10^drop, mode);
//                       result = RellBigDec(q, newScale).
//
// ----- add/subtract  (BigDecimal.add(BigInteger,int,BigInteger,int)) -----
// rscale = max(scaleA, scaleB). Raise the lower-scale unscaled value by 10^(diff) so both
// sit at rscale, then add (subtract: negate the second unscaled first). Result scale =
// rscale. (No rounding: exact.)
//
// ----- multiply -----
// unscaled' = U_a * U_b; scale' = scaleA + scaleB. Exact.
//
// ----- compareTo / compareMagnitude -----
// compareTo: if signs differ, decide by sign. If both zero, 0. Else cmp = compareMagnitude
// and return (sign>0 ? cmp : -cmp). compareMagnitude (val both nonzero): if scales equal,
// compare U magnitudes directly. Else let sdiff = scaleA - scaleB; first compare adjusted
// exponents xae=precisionA-scaleA vs yae=precisionB-scaleB (as the magnitude's order of
// magnitude); if they differ that decides it. Only if xae==yae do we actually align:
// raise the operand with the SMALLER scale by 10^|sdiff| (sdiff<0 -> raise A; sdiff>0 ->
// raise B) and compareMagnitude the resulting unscaled values.
//
// ----- precision / bigDigitLength -----
// if U == 0 -> 1. Else r = (int)(((bitLength(U) + 1) * 646456993L) >> 31); then
// (|U| < 10^r) ? r : r+1.  bitLength here is RellBigInt::bitLength().
//
// ----- stripTrailingZeros / createAndStripZerosToMatchScale(preferred = -inf) -----
// if U == 0 -> ZERO (scale 0). Loop while |U| >= 10 and scale > preferredScale:
//   if U is odd -> break (can't end in 0). qr = U.divideAndRemainder(10);
//   if qr.remainder != 0 -> break. U = qr.quotient; scale -= 1.
// return RellBigDec(U, scale). For stripTrailingZeros, preferredScale = INT32_MIN-ish
// (effectively unbounded) so it strips every trailing zero; scale may go negative.
//
// ----- toBigInteger -----
// scale<=0: U * 10^(-scale). scale>0: U / 10^scale (truncate toward zero, exact integer
// division dropping the fraction).
//
// ----- remainder via divideToIntegralQuotient -----
// integer quotient iq = trunc(this/divisor) as a RellBigInt = compute over the unscaled
// values with scale alignment: bring both to common scale c=max(scaleA,scaleB), then
// iq = (U_a*10^(c-scaleA)) truncating-divide (U_b*10^(c-scaleB)). remainder value =
// this - iq*divisor  (compute as RellBigDec: multiply iq[scale 0] by divisor, subtract
// from this; the exact subtract yields the BigDecimal remainder with scale
// max(scaleA,scaleB) and the dividend's sign).
//
// ----- toString / layoutChars -----
// if scale == 0: return unscaled.toString(). Else: let coeff = |unscaled|.toString(),
// coeffLen = coeff.length, adjusted = -(long)scale + (coeffLen - 1).
//   PLAIN form when scale >= 0 && adjusted >= -6:
//     pad = scale - coeffLen;
//     if pad >= 0:  "0." + ('0' * pad) + coeff
//     else:         coeff[0 .. coeffLen+pad) + "." + coeff[coeffLen+pad .. end)
//                   (i.e. insert '.' so that `scale` digits follow it)
//   else E-notation (scientific, sci=true): first digit, then '.' + rest if coeffLen>1,
//     then "E" + (adjusted>0? "+":"") + adjusted (only if adjusted != 0).
//   Prefix '-' when signum < 0 (Rell display strips trailing zeros first — see
//   rell_decimal_math.h decimal_to_string).
//
// ----- TODO(perf) markers to carry into the .cpp -----
//   // TODO(perf): cache precision() like BigDecimal's `precision` field.
//   // TODO(perf): BigDecimal's intCompact long fast path is intentionally omitted.

}  // namespace rell::num

#endif  // RELL_BIGDEC_H
