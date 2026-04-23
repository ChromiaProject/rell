// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_decimal_math.cpp — the Rell SEMANTICS layer over RellBigInt / RellBigDec.
//
// Faithful C++ translation of the Rell runtime's numeric envelope (consensus code):
//   * Lib_DecimalMath / Rt_DecimalValue.getOrNull  (decimal canonicalization + overflow)
//   * Lib_BigIntegerMath / Rt_BigIntegerValue.getOrNull (big_integer range + overflow)
// Source of truth (this repo, Kotlin):
//   rell-base/runtime-core/src/main/kotlin/lib/type/lib_type_decimal.kt    (Lib_DecimalMath)
//   rell-base/runtime-core/src/main/kotlin/lib/type/lib_type_biginteger.kt (Lib_BigIntegerMath)
//   rell-base/runtime-core/src/main/kotlin/runtime/rt_value_decimal.kt     (Rt_DecimalValue)
//   rell-base/runtime-core/src/main/kotlin/runtime/rt_value_biginteger.kt  (Rt_BigIntegerValue)
//
// The bit-exact OpenJDK BigInteger/BigDecimal arithmetic lives in RellBigInt
// (rell_bigint.cpp) and RellBigDec (rell_bigdec.cpp). This file ONLY adds the Rell
// envelope: normalization (scale pinned to 20, integer-digit range), range checks, and
// the exact Rell exception codes/messages.

#include "rell_decimal_math.h"

#include <string>

namespace rell::num {

// =====================================================================================
// big_integer range bounds (Lib_BigIntegerMath.MAX_VALUE / MIN_VALUE = ±(10^131072 - 1))
//
// Kotlin:
//   val MAX_VALUE = BigInteger.TEN.pow(PRECISION).subtract(BigInteger.ONE)
//   val MIN_VALUE = -MAX_VALUE
// Built lazily ONCE (10^131072 is ~13600 limbs; computing it eagerly at static-init time
// would slow every process start). Magic-statics give us thread-safe one-time init.
// =====================================================================================

const RellBigInt &bigint_max_value() {
    static const RellBigInt max_value =
        RellBigInt::fromInt64(10).pow(static_cast<uint32_t>(BIGINT_PRECISION)).subtract(RellBigInt::one());
    return max_value;
}

const RellBigInt &bigint_min_value() {
    static const RellBigInt min_value = bigint_max_value().negate();
    return min_value;
}

// bigint_range_check: Rt_BigIntegerValue.getOrNull semantics —
//   if (v < MIN_VALUE || v > MAX_VALUE) null  else v.
// On out-of-range, Rt_BigIntegerValue.get throws:
//   Rt_Exception.common("bigint:overflow",
//     "Big integer value out of range (allowed range is -10^131072..10^131072, exclusive)")
const RellBigInt &bigint_range_check(const RellBigInt &v) {
    if (v < bigint_min_value() || v > bigint_max_value()) {
        throw RellArithError(BIGINT_OVERFLOW_CODE, bigint_overflow_message());
    }
    return v;
}

// =====================================================================================
// big_integer ops (Lib_BigIntegerMath + Rt_BigIntegerValue.get range check)
//
// add/subtract/multiply: exact, then range-check (Rt_BigIntegerValue.get is applied to
// every produced value). divide/remainder: exact, NO range check (the result magnitude
// can never exceed the operands'). div/mod by zero is raised inside RellBigInt as
// RellArithError("","/ by zero") and propagates unchanged — the integration layer maps it.
// =====================================================================================

RellBigInt bigint_add(const RellBigInt &a, const RellBigInt &b) {
    return bigint_range_check(a.add(b));
}

RellBigInt bigint_sub(const RellBigInt &a, const RellBigInt &b) {
    return bigint_range_check(a.subtract(b));
}

RellBigInt bigint_mul(const RellBigInt &a, const RellBigInt &b) {
    return bigint_range_check(a.multiply(b));
}

RellBigInt bigint_div(const RellBigInt &a, const RellBigInt &b) {
    // Lib_BigIntegerMath.divide = a.divide(b): truncate toward zero. No range check.
    return a.divide(b);
}

RellBigInt bigint_rem(const RellBigInt &a, const RellBigInt &b) {
    // Lib_BigIntegerMath.remainder = a.remainder(b): sign of dividend. No range check.
    return a.remainder(b);
}

// bigint_pow: Lib_BigIntegerMath.genericPower + NumericType_BigInteger.pow.
//
// Kotlin genericPower:
//   check(exp >= 0) else "Negative exponent: $exp"
//   exp == 0      -> one
//   exp == 1      -> base
//   base == zero  -> zero
//   base == one   -> one
//   base == -one  -> (exp even ? one : -one)
//   else          -> NumericType_BigInteger.pow(base, exp.toIntExact())
//
// NumericType_BigInteger.pow(base, exp):
//   baseExp = max(|base|.bitLength() - 1, 0)
//   resExp  = baseExp * exp                       (Math.multiplyExact — int64)
//   if (resExp + 1 > MAX_VALUE.bitLength()) throw ArithmeticException("...out of range")
//   res = base.pow(exp)
//   if (res !in MIN_VALUE..MAX_VALUE) throw ArithmeticException("...out of range")
//   return res
//
// In Kotlin this ArithmeticException is caught by genericPower and rethrown as
// Rt_Exception.common("<fn>:overflow:<baseStr>:<exp>", "Power overflow: ..."). Per the
// header contract (rell_decimal_math.h:94-105) the C++ layer surfaces the canonical
// bigint:overflow code+message directly, since the result genuinely exceeds big_integer
// range. Both are consensus-equivalent: the value cannot be represented and the operation
// throws. We keep the SAME pre-check (bit-length guard) so we never materialise a
// monstrous intermediate (e.g. (1E+1000)^250000), exactly as the JVM does.
RellBigInt bigint_pow(const RellBigInt &base, int64_t exp) {
    if (exp < 0) {
        // Rt maps "<fn>:exp_negative:<exp>" / "Negative exponent: <exp>". The PURE-arith
        // empty-code convention is used here; the integration layer supplies the fn name.
        throw RellArithError("", "Negative exponent: " + std::to_string(exp));
    }

    // Fast cases (genericPower switch). These never overflow, so no range check.
    if (exp == 0) return RellBigInt::one();
    if (exp == 1) return base;
    if (base.isZero()) return RellBigInt::zero();

    const RellBigInt one = RellBigInt::one();
    if (base == one) return one;
    const RellBigInt minus_one = one.negate();
    if (base == minus_one) {
        return ((exp & 1LL) == 0) ? one : minus_one;
    }

    // Overflow pre-check via bit length (NumericType_BigInteger.pow).
    // baseExp = max(|base|.bitLength() - 1, 0); resExp = baseExp * exp.
    const int64_t base_bit_len = base.abs().bitLength();
    const int64_t base_exp = (base_bit_len - 1) > 0 ? (base_bit_len - 1) : 0;

    // Math.multiplyExact(baseExp, exp): detect int64 overflow. baseExp >= 0 and exp >= 2
    // here, both non-negative, so a simple division-based check suffices.
    // TODO: this mirrors Math.multiplyExact throwing -> ArithmeticException -> overflow.
    int64_t res_exp;
    if (base_exp != 0 && exp > (INT64_MAX / base_exp)) {
        // Product would overflow int64 => certainly out of big_integer range.
        throw RellArithError(BIGINT_OVERFLOW_CODE, bigint_overflow_message());
    }
    res_exp = base_exp * exp;

    if (res_exp + 1 > bigint_max_value().bitLength()) {
        throw RellArithError(BIGINT_OVERFLOW_CODE, bigint_overflow_message());
    }

    // exp fits in int because Math.toIntExact succeeded in Kotlin (resExp+1 within
    // MAX_VALUE.bitLength() ~ 435417 implies exp itself is far below INT32_MAX whenever
    // baseExp >= 1; the baseExp==0 case is impossible here since |base| >= 2 after the
    // fast cases). pow takes uint32_t.
    const RellBigInt res = base.pow(static_cast<uint32_t>(exp));

    // Final range check (res !in MIN_VALUE..MAX_VALUE -> throw).
    return bigint_range_check(res);
}

// bigint_sqrt: BigInteger.sqrt — floor of exact sqrt, non-negative only. Negative throws
// inside RellBigInt::sqrt as RellArithError("","negative BigInteger"). The result shrinks
// the magnitude (sqrt(MAX) << MAX), so it is always in range; no range check needed.
RellBigInt bigint_sqrt(const RellBigInt &v) {
    return v.sqrt();
}

// =====================================================================================
// decimal normalization (Lib_DecimalMath.scale + Rt_DecimalValue.getOrNull)
//
// Faithful translation of Lib_DecimalMath.scale wrapped by Rt_DecimalValue.getOrNull:
//
//   getOrNull(v):
//     if v.signum() == 0: return ZERO            // ZERO == BigDecimal.ZERO, scale 0
//     t = scale(v); return (t == null) ? null : Rt_BigDecimalValue(t)
//   get(v): getOrNull(v) ?: throw errOverflow("decimal:overflow", "Decimal value out of range")
//
//   scale(v):
//     if v.signum() == 0: return BigDecimal.ZERO
//     scale = v.scale(); intDigits = v.precision() - scale
//     if intDigits > DECIMAL_INT_DIGITS:        return null
//     else if intDigits < -DECIMAL_FRAC_DIGITS: return BigDecimal.ZERO
//     if scale <= DECIMAL_FRAC_DIGITS:          return v.setScale(DECIMAL_FRAC_DIGITS)
//     else:
//       v2 = v.setScale(DECIMAL_FRAC_DIGITS, HALF_UP)
//       intDigits2 = v2.precision() - v2.scale()
//       return (intDigits2 > DECIMAL_INT_DIGITS) ? null : v2
//
// Post-condition: result has scale exactly DECIMAL_FRAC_DIGITS (20), EXCEPT the ZERO
// sentinel (scale 0). Note: setScale(20) for scale <= 20 only ever PADS (raises scale), so
// the rounding mode is irrelevant; we pass HALF_UP (never exercised on that branch).
// =====================================================================================

RellBigDec decimal_scale_normalize(const RellBigDec &v) {
    if (v.signum() == 0) {
        // Rt_DecimalValue.ZERO == BigDecimal.ZERO (unscaled 0, scale 0).
        return RellBigDec::zero();
    }

    const int32_t scale = v.scale();
    const int32_t int_digits = v.precision() - scale;
    if (int_digits > DECIMAL_INT_DIGITS) {
        throw RellArithError(DECIMAL_OVERFLOW_CODE, decimal_overflow_message());
    }
    if (int_digits < -DECIMAL_FRAC_DIGITS) {
        // Underflow to zero: |v| is below 0.5 * 10^-20 in magnitude order.
        return RellBigDec::zero();
    }

    if (scale <= DECIMAL_FRAC_DIGITS) {
        // Pad trailing zeros to scale 20 (exact; mode irrelevant on the padding branch).
        return v.setScale(DECIMAL_FRAC_DIGITS, RoundingMode::HALF_UP);
    }

    // scale > 20: drop digits, rounding HALF_UP. Rounding may bump the integer-part digit
    // count by one (e.g. 9.99..e+131071 carrying), so re-check.
    const RellBigDec v2 = v.setScale(DECIMAL_FRAC_DIGITS, RoundingMode::HALF_UP);
    const int32_t int_digits2 = v2.precision() - v2.scale();
    if (int_digits2 > DECIMAL_INT_DIGITS) {
        throw RellArithError(DECIMAL_OVERFLOW_CODE, decimal_overflow_message());
    }
    return v2;
}

// =====================================================================================
// decimal ops (Lib_DecimalMath + Rt_DecimalValue.get normalization)
//
// Each op performs the exact BigDecimal arithmetic, then normalizes the result through
// decimal_scale_normalize (mirroring Rt_DecimalValue.get(v) on every produced value).
// =====================================================================================

RellBigDec decimal_add(const RellBigDec &a, const RellBigDec &b) {
    return decimal_scale_normalize(a.add(b));
}

RellBigDec decimal_sub(const RellBigDec &a, const RellBigDec &b) {
    return decimal_scale_normalize(a.subtract(b));
}

RellBigDec decimal_mul(const RellBigDec &a, const RellBigDec &b) {
    return decimal_scale_normalize(a.multiply(b));
}

// decimal_div: Lib_DecimalMath.divide = a.divide(b, DECIMAL_FRAC_DIGITS, HALF_UP), then
// normalize. Division by zero throws RellArithError("","/ by zero") inside RellBigDec
// (mapped by the integration layer). The quotient already has scale 20; normalize is
// effectively a range check (and the ZERO collapse for a zero result).
RellBigDec decimal_div(const RellBigDec &a, const RellBigDec &b) {
    const RellBigDec q = a.divide(b, DECIMAL_FRAC_DIGITS, RoundingMode::HALF_UP);
    return decimal_scale_normalize(q);
}

// decimal_rem: Lib_DecimalMath.remainder = a.remainder(b) (exact, sign of dividend), then
// normalize. b == 0 -> "/ by zero" inside RellBigDec::remainder.
RellBigDec decimal_rem(const RellBigDec &a, const RellBigDec &b) {
    return decimal_scale_normalize(a.remainder(b));
}

// =====================================================================================
// decimal parse / toString (Lib_DecimalMath.parse / Lib_DecimalMath.toString)
// =====================================================================================

namespace {

// isDigit(s, i): Lib_DecimalMath.isDigit — ASCII '0'..'9'.
inline bool is_digit(const std::string &s, std::size_t i) {
    return s[i] >= '0' && s[i] <= '9';
}

// parseString(s): Lib_DecimalMath.parseString. Validates the number grammar and returns
// [fracStart, fracEnd) — the index range of the FRACTIONAL part of the string:
//   [sign] digit+ [ '.' digit+ ] [ ('e'|'E') [sign] digit+ ]
// fracStart = index of '.' (or of 'E' if no point, or n if neither);
// fracEnd   = index of 'E'/'e' (or n if no exponent).
// Throws std::invalid_argument (mapped to the NumberFormatException path) on malformed.
//
// Faithful to the Kotlin:
//   if i<n && (s[i]=='-'||s[i]=='+') ++i
//   verifyDigit(s, i++); while (i<n && isDigit) ++i
//   if (i<n && s[i]=='.') { fracStart=i; ++i; verifyDigit(s,i++); while(isDigit)++i }
//   if (i<n && (s[i]=='E'||s[i]=='e')) {
//       if (fracStart==n) fracStart=i; fracEnd=i; ++i;
//       if (i<n && sign) ++i; verifyDigit(s,i++); while(isDigit)++i }
//   if (i != n) throw
void parse_string(const std::string &s, std::size_t &frac_start, std::size_t &frac_end) {
    const std::size_t n = s.size();
    frac_start = n;
    frac_end = n;
    std::size_t i = 0;

    auto verify_digit = [&](std::size_t idx) {
        if (idx >= n || !is_digit(s, idx)) {
            throw std::invalid_argument("invalid decimal string");
        }
    };

    if (i < n && (s[i] == '-' || s[i] == '+')) ++i;
    verify_digit(i);
    ++i;
    while (i < n && is_digit(s, i)) ++i;

    if (i < n && s[i] == '.') {
        frac_start = i;
        ++i;
        verify_digit(i);
        ++i;
        while (i < n && is_digit(s, i)) ++i;
    }

    if (i < n && (s[i] == 'E' || s[i] == 'e')) {
        if (frac_start == n) frac_start = i;
        frac_end = i;
        ++i;
        if (i < n && (s[i] == '+' || s[i] == '-')) ++i;
        verify_digit(i);
        ++i;
        while (i < n && is_digit(s, i)) ++i;
    }

    if (i != n) {
        throw std::invalid_argument("invalid decimal string");
    }
}

// removeTrailingZeros(s): Lib_DecimalMath.removeTrailingZeros. Validates the number,
// finds the fractional part, trims trailing '0' from it down to (but not into) the
// integer part, drops a dangling '.', and reattaches any exponent tail.
//
// Kotlin:
//   val (fracStart, fracEnd) = parseString(s)
//   var i = fracEnd
//   while (i > fracStart && s[i-1] == '0') --i
//   if (i > fracStart && s[i-1] == '.') --i
//   return if (i == fracEnd) s
//          else if (fracEnd == s.length) s.substring(0, i)
//          else s.substring(0, i) + s.substring(fracEnd)
std::string remove_trailing_zeros(const std::string &s) {
    std::size_t frac_start, frac_end;
    parse_string(s, frac_start, frac_end);

    std::size_t i = frac_end;
    while (i > frac_start && s[i - 1] == '0') --i;
    if (i > frac_start && s[i - 1] == '.') --i;

    if (i == frac_end) {
        return s;
    } else if (frac_end == s.size()) {
        return s.substr(0, i);
    } else {
        return s.substr(0, i) + s.substr(frac_end);
    }
}

}  // namespace

// decimal_parse: Lib_DecimalMath.parse + Rt_DecimalValue.get.
//
// Kotlin Lib_DecimalMath.parse:
//   t = if s.startsWith(".")  -> "0$s"
//       elif s.startsWith("+.") -> "0" + s.substring(1)     (drops '+', so "0.<...>")
//       elif s.startsWith("-.") -> "-0" + s.substring(1)
//       else s
//   t = removeTrailingZeros(t)        // also VALIDATES the grammar
//   return BigDecimal(t)
// Rt_DecimalValue.get(String): catches NumberFormatException -> "decimal:invalid:<s>",
// "Invalid decimal value: '<s>'". Then get(BigDecimal) -> normalize (overflow possible).
RellBigDec decimal_parse(const std::string &s) {
    try {
        std::string t;
        if (s.rfind(".", 0) == 0) {            // startsWith(".")
            t = "0" + s;
        } else if (s.rfind("+.", 0) == 0) {    // startsWith("+.")
            t = "0" + s.substr(1);
        } else if (s.rfind("-.", 0) == 0) {    // startsWith("-.")
            t = "-0" + s.substr(1);
        } else {
            t = s;
        }

        t = remove_trailing_zeros(t);          // validates grammar; may throw
        const RellBigDec v = RellBigDec::parse(t);   // may throw on malformed
        return decimal_scale_normalize(v);     // may throw decimal:overflow
    } catch (const RellArithError &e) {
        // Re-throw overflow (and the "/ by zero"-style empty-code arith errors carry no
        // code) but convert pure parse failures (empty code from RellBigDec::parse, or our
        // own std::invalid_argument below) into the Rell "decimal:invalid" code.
        if (!e.code().empty()) {
            throw;  // decimal:overflow — propagate unchanged.
        }
        throw RellArithError("decimal:invalid:" + s, "Invalid decimal value: '" + s + "'");
    } catch (const std::exception &) {
        // NumberFormatException analogue (std::invalid_argument from parse_string /
        // std::out_of_range etc.) -> Rt_DecimalValue.get(String) maps to decimal:invalid.
        throw RellArithError("decimal:invalid:" + s, "Invalid decimal value: '" + s + "'");
    }
}

// decimal_to_string: Lib_DecimalMath.toString = removeTrailingZeros(v.toPlainString()).
// The canonical stored form has scale 20 (or 0 for ZERO), so toPlainString never emits
// E-notation here; removeTrailingZeros then trims the padding zeros for display.
// Examples: 2.50000000000000000000 -> "2.5"; 7.00..0 -> "7"; ZERO -> "0".
std::string decimal_to_string(const RellBigDec &v) {
    const std::string s = v.toPlainString();
    return remove_trailing_zeros(s);
}

}  // namespace rell::num
