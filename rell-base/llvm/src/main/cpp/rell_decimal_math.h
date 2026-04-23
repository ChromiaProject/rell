// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_decimal_math.h — the Rell SEMANTICS layer over RellBigInt / RellBigDec.
//
// This header is the C++ mirror of the Rell runtime's numeric envelope:
//   * Lib_DecimalMath / Rt_DecimalValue.getOrNull  (decimal canonicalization + overflow)
//   * Lib_BigIntegerMath / Rt_BigIntegerValue.getOrNull (big_integer range + overflow)
// Everything here is bit-for-bit consensus behaviour: the canonical decimal form (scale
// pinned to 20, integer-part-digit range, HALF_UP), the big_integer range (±(10^131072-1)),
// the truncate-toward-zero division, the exact error codes/messages. The pure OpenJDK
// arithmetic lives in RellBigInt (rell_bigint.h) and RellBigDec (rell_bigdec.h); this layer
// only adds the Rell envelope (normalization + range checks + exception codes).
//
// Source of truth (Rell runtime, this repo):
//   rell-base/runtime-core/src/main/kotlin/lib/type/lib_type_decimal.kt    (Lib_DecimalMath)
//   rell-base/runtime-core/src/main/kotlin/lib/type/lib_type_biginteger.kt (Lib_BigIntegerMath)
//   rell-base/runtime-core/src/main/kotlin/runtime/rt_value_decimal.kt     (Rt_DecimalValue)
//   rell-base/runtime-core/src/main/kotlin/runtime/rt_value_biginteger.kt  (Rt_BigIntegerValue)
//
// PURE C++17: depends only on the standard library + RellBigInt + RellBigDec. No JNI/LLVM.

#ifndef RELL_DECIMAL_MATH_H
#define RELL_DECIMAL_MATH_H

#include <cstdint>
#include <string>

#include "rell_bigdec.h"
#include "rell_bigint.h"

namespace rell::num {

// =====================================================================================
// Constants (mirror Lib_DecimalMath / Lib_BigIntegerMath)
// =====================================================================================

// Max integer-part digits for a decimal AND the big_integer precision: 10^131072.
inline constexpr int32_t DECIMAL_INT_DIGITS = 131072;   // Lib_DecimalMath.DECIMAL_INT_DIGITS
// Canonical decimal scale (fraction digits). Every stored decimal has scale == 20.
inline constexpr int32_t DECIMAL_FRAC_DIGITS = 20;      // Lib_DecimalMath.DECIMAL_FRAC_DIGITS
// Lib_BigIntegerMath.PRECISION (same 131072).
inline constexpr int32_t BIGINT_PRECISION = 131072;     // Lib_BigIntegerMath.PRECISION

// Error codes (exact Rell codes carried by RellArithError).
inline constexpr char BIGINT_OVERFLOW_CODE[] = "bigint:overflow";
inline constexpr char DECIMAL_OVERFLOW_CODE[] = "decimal:overflow";

// Exact overflow messages. Rt_*Value.errOverflow builds:
//   "<base> (allowed range is -10^131072..10^131072, exclusive)"
// where <base> is "Big integer value out of range" / "Decimal value out of range".
inline const std::string &bigint_overflow_message() {
    static const std::string m =
        "Big integer value out of range (allowed range is -10^131072..10^131072, exclusive)";
    return m;
}
inline const std::string &decimal_overflow_message() {
    static const std::string m =
        "Decimal value out of range (allowed range is -10^131072..10^131072, exclusive)";
    return m;
}

// =====================================================================================
// big_integer range (Lib_BigIntegerMath.MAX_VALUE / MIN_VALUE = ±(10^131072 - 1))
// =====================================================================================

// MAX_VALUE = 10^131072 - 1 (lazily built once; ~13600 limbs).
const RellBigInt &bigint_max_value();
// MIN_VALUE = -(10^131072 - 1).
const RellBigInt &bigint_min_value();

// bigint_range_check(v): if v in [MIN_VALUE, MAX_VALUE] return v unchanged; else throw
// RellArithError("bigint:overflow", bigint_overflow_message()). Mirrors
// Rt_BigIntegerValue.getOrNull/get. (Comparison is inclusive on both ends, exactly as
// Kotlin `v < MIN_VALUE || v > MAX_VALUE`.)
const RellBigInt &bigint_range_check(const RellBigInt &v);

// =====================================================================================
// big_integer ops (Lib_BigIntegerMath + Rt_BigIntegerValue.get range check)
// =====================================================================================
//
// add/subtract/multiply are exact BigInteger arithmetic, THEN range-checked. divide
// truncates toward zero; remainder takes the dividend's sign. div/mod by zero: the Rell
// runtime lets BigInteger throw "/ by zero" (ArithmeticException) which the interpreter
// maps to a generic Rt error — RellBigInt::divide/remainder already throw
// RellArithError("","/ by zero"); the integration layer maps that. We do NOT range-check
// divide/remainder results (they cannot exceed the operands' magnitude).

RellBigInt bigint_add(const RellBigInt &a, const RellBigInt &b);
RellBigInt bigint_sub(const RellBigInt &a, const RellBigInt &b);
RellBigInt bigint_mul(const RellBigInt &a, const RellBigInt &b);
RellBigInt bigint_div(const RellBigInt &a, const RellBigInt &b);  // truncate toward zero
RellBigInt bigint_rem(const RellBigInt &a, const RellBigInt &b);  // sign of dividend

// pow(base, exp): exp >= 0. Mirrors Lib_BigIntegerMath.genericPower + NumericType_BigInteger.pow:
//   * exp < 0 -> RellArithError("","Negative exponent: <exp>")  (Rt maps exp_negative).
//   * fast cases: exp==0 -> 1; exp==1 -> base; base==0 -> 0; base==1 -> 1;
//                 base==-1 -> (exp even ? 1 : -1).
//   * else: pre-check overflow via bit length so we never compute a monstrous result:
//       baseExp = max(|base|.bitLength() - 1, 0); resExp = baseExp * exp (as int64);
//       if resExp + 1 > MAX_VALUE.bitLength() -> overflow.
//     Then compute base.pow(exp) and final range-check. Overflow -> RellArithError(
//     "bigint:overflow", bigint_overflow_message()). (The Rell code throws
//     ArithmeticException "...out of range" which Rt remaps to an overflow Rt_Exception;
//     we surface the canonical bigint:overflow directly.)
RellBigInt bigint_pow(const RellBigInt &base, int64_t exp);

// sqrt(v): BigInteger.sqrt — floor of exact sqrt, v >= 0 only. Negative -> RellArithError(
// "","negative BigInteger"). Result is always in range (sqrt shrinks magnitude), no check.
RellBigInt bigint_sqrt(const RellBigInt &v);

// =====================================================================================
// decimal normalization (Lib_DecimalMath.scale + Rt_DecimalValue.getOrNull)
// =====================================================================================
//
// decimal_scale_normalize(v): the canonicalization every produced decimal passes through.
// Returns the canonical RellBigDec, or throws RellArithError("decimal:overflow",
// decimal_overflow_message()) on out-of-range. EXACT algorithm (Rt_DecimalValue.getOrNull
// wrapping Lib_DecimalMath.scale):
//
//   if v.signum() == 0:
//       return ZERO  (RellBigDec::zero(), i.e. unscaled 0, scale 0).   // NOTE: scale 0!
//
//   scale     = v.scale();
//   intDigits = v.precision() - scale;          // = order of magnitude of the integer part
//   if intDigits > 131072:           throw decimal:overflow.
//   else if intDigits < -20:         return ZERO.   // underflow to 0 (|v| < 0.5e-20-ish)
//
//   if scale <= 20:
//       return v.setScale(20);                  // pad trailing zeros to scale 20 (EXACT,
//                                               // mode irrelevant: only ever increases scale).
//   else:  // scale > 20: drop digits, rounding HALF_UP
//       v2 = v.setScale(20, RoundingMode::HALF_UP);
//       intDigits2 = v2.precision() - v2.scale();   // rounding may add ONE integer digit
//       if intDigits2 > 131072:      throw decimal:overflow.
//       return v2;
//
// Post-condition: the result has scale exactly 20, EXCEPT the ZERO sentinel which has
// scale 0 (confirmed: Rt_DecimalValue.ZERO == BigDecimal.ZERO, scale 0; and
// Lib_DecimalMath.scale returns BigDecimal.ZERO for the zero / underflow cases).
RellBigDec decimal_scale_normalize(const RellBigDec &v);

// =====================================================================================
// decimal ops (Lib_DecimalMath + Rt_DecimalValue.get normalization)
// =====================================================================================
//
// Each op does the exact BigDecimal arithmetic, then decimal_scale_normalize on the
// result (mirroring Rt_DecimalValue.get(v) called on every produced value).

// add/subtract/multiply: exact, then normalize.
RellBigDec decimal_add(const RellBigDec &a, const RellBigDec &b);
RellBigDec decimal_sub(const RellBigDec &a, const RellBigDec &b);
RellBigDec decimal_mul(const RellBigDec &a, const RellBigDec &b);

// divide: a.divide(b, 20, HALF_UP), then normalize. (b == 0 -> RellArithError("","/ by
// zero") from RellBigDec::divide, mapped by the integration layer.) The result already
// has scale 20, so normalize is mostly a range check; still apply it for the
// intDigits>131072 overflow case.
RellBigDec decimal_div(const RellBigDec &a, const RellBigDec &b);

// remainder: a.remainder(b) (exact, sign of dividend), then normalize. b == 0 -> "/ by zero".
RellBigDec decimal_rem(const RellBigDec &a, const RellBigDec &b);

// =====================================================================================
// decimal parse / toString (Lib_DecimalMath.parse / Lib_DecimalMath.toString)
// =====================================================================================

// decimal_parse(s): Lib_DecimalMath.parse + Rt_DecimalValue.get:
//   * leading "."  -> prefix "0"  ("-." -> "-0.", "+." -> "0.").
//   * strip trailing zeros from the FRACTIONAL part as a STRING op (removeTrailingZeros:
//     trims '0's after the point, drops a dangling '.', preserves any exponent suffix).
//   * BigDecimal(t)  (RellBigDec::parse), then decimal_scale_normalize.
//   * malformed input -> RellArithError("decimal:invalid:<s>", "Invalid decimal value:
//     '<s>'").  (Rt_DecimalValue.get(String) wraps NumberFormatException into that code.)
RellBigDec decimal_parse(const std::string &s);

// decimal_to_string(v): Lib_DecimalMath.toString — v.toPlainString() then string-level
// removeTrailingZeros (NOT BigDecimal.stripTrailingZeros). Since the canonical form has
// scale 20, toPlainString never uses E-notation here. removeTrailingZeros:
//   * find the fractional part [fracStart, fracEnd) of the plain string;
//   * trim trailing '0' down to fracStart; if a lone '.' remains, drop it too;
//   * reattach any (non-existent here) exponent tail.
// ZERO -> "0". Examples: 2.50000000000000000000 -> "2.5"; 7.00..0 -> "7";
// 0.10000000000000000000 -> "0.1".
std::string decimal_to_string(const RellBigDec &v);

}  // namespace rell::num

#endif  // RELL_DECIMAL_MATH_H
