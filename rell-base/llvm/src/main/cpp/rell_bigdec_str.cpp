// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigdec_str.cpp — RellBigDec string I/O and value-of/strip factories.
//
// Faithful translation of the OpenJDK 21 (jdk-21+35) java.math.BigDecimal
// string handling: the BigDecimal(char[]) parsing grammar, layoutChars (the
// toString scientific-vs-plain layout), toPlainString/getValueString, the
// valueOf(long[,scale]) factories, and createAndStripZerosToMatchScale (used by
// stripTrailingZeros with preferredScale = -infinity).
//
// We always use the RellBigInt (`unscaled_`) representation; OpenJDK's
// `intCompact` long fast path is intentionally omitted — it is a pure-speed
// redundancy, never a correctness difference. // TODO(perf): intCompact omitted.
//
// Pure C++17: depends only on the standard library and RellBigInt/RellBigDec.

#include "rell_bigdec.h"

#include <cstdint>
#include <string>

#include "rell_bigint.h"

namespace rell::num {

namespace {

// Throw the BigDecimal(String) "malformed input" condition. The Rell envelope
// (rell_decimal_math.*) catches and re-wraps this with the `decimal:invalid:<s>`
// code; here we follow the rell_bigdec.h contract of an empty code with a plain
// arithmetic-style message.
[[noreturn]] void invalid(const std::string &s) {
    throw RellArithError("", "invalid decimal string \"" + s + "\"");
}

}  // namespace

// =====================================================================================
// parse — BigDecimal(char[] in, int offset, int len) with MathContext.UNLIMITED.
//
// Grammar:  [sign] significand [ ('e'|'E') [sign] exponent ]
//   significand : digits | digits '.' digits | digits '.' | '.' digits
// At least one digit must appear in the significand. The result is
//   unscaled = (signed integer formed by the significand digits, leading zeros
//              dropped for precision but value preserved)
//   scale    = (#fraction digits) - exponent
//
// OpenJDK tracks `prec` (significand precision, leading zeros suppressed) and
// `scl` (running scale: +1 per fraction digit, minus the parsed exponent). We
// only need the *value* and the scale here — precision() is recomputed lazily
// elsewhere — but we faithfully reproduce the digit/dot/exponent state machine,
// including the "no digits found" and "more than one decimal point" rejections.
// =====================================================================================
RellBigDec RellBigDec::parse(const std::string &s) {
    const char *in = s.c_str();
    std::size_t len = s.size();
    std::size_t offset = 0;

    if (len == 0) {
        invalid(s);
    }

    // ----- sign -----
    bool isneg = false;
    if (in[offset] == '-') {
        isneg = true;
        ++offset;
        --len;
    } else if (in[offset] == '+') {
        ++offset;
        --len;
    }

    // ----- significand -----
    bool dot = false;       // a '.' has been seen
    bool anyDigit = false;  // at least one significand digit seen
    int64_t scl = 0;        // running scale (long in OpenJDK; consensus range fits int64)
    // Accumulate the significand digits (sign-free) as a decimal string for the
    // RellBigInt constructor. We append every digit (including redundant leading
    // zeros) — RellBigInt::fromDecimalString collapses leading zeros, and the
    // value is identical either way. scale counts only fraction digits.
    std::string coeff;
    coeff.reserve(len);

    for (; len > 0; ++offset, --len) {
        char c = in[offset];
        if (c >= '0' && c <= '9') {
            coeff.push_back(c);
            anyDigit = true;
            if (dot) {
                ++scl;
            }
        } else if (c == '.') {
            if (dot) {
                invalid(s);  // more than one decimal point
            }
            dot = true;
        } else if (c == 'e' || c == 'E') {
            // parseExp consumes the rest of the buffer (offset..offset+len) and
            // returns the (signed) exponent; scl -= exponent.
            // ---- inlined parseExp ----
            std::size_t eoff = offset + 1;  // skip the 'e'/'E'
            std::size_t elen = len - 1;
            if (elen == 0) {
                invalid(s);  // no exponent digits
            }
            bool negexp = false;
            char ec = in[eoff];
            if (ec == '-') {
                negexp = true;
                ++eoff;
                --elen;
            } else if (ec == '+') {
                ++eoff;
                --elen;
            }
            if (elen == 0) {
                invalid(s);  // sign with no digits
            }
            int64_t exp = 0;
            for (std::size_t i = 0; i < elen; ++i) {
                char d = in[eoff + i];
                if (d < '0' || d > '9') {
                    invalid(s);  // not a digit
                }
                exp = exp * 10 + (d - '0');
                // Guard against absurd exponents well below the consensus range
                // (OpenJDK rejects > 10 nonzero digits / Exponent overflow).
                if (exp > 4000000000LL) {
                    invalid(s);
                }
            }
            if (negexp) {
                exp = -exp;
            }
            scl -= exp;
            // parseExp consumed the remainder; break out of the significand loop.
            len = 0;
            break;
        } else {
            invalid(s);  // not a digit, dot, or exponent mark
        }
    }

    if (!anyDigit) {
        invalid(s);  // no digits found
    }

    // OpenJDK: `if ((int) scl != scl) throw Exponent overflow`. The Rell decimal
    // envelope range is |scale| well within int32, but we keep the guard so a
    // pathological exponent throws rather than silently truncating.
    if (scl > 2147483647LL || scl < -2147483648LL) {
        invalid(s);
    }

    // Build the unscaled value. coeff holds the significand digits without sign
    // and without the decimal point; prepend '-' for negatives. An all-zero
    // significand yields ZERO with the computed scale (e.g. "0.00" -> 0 scale 2,
    // "-0" -> 0 scale 0), matching BigDecimal.
    if (isneg) {
        coeff.insert(coeff.begin(), '-');
    }
    RellBigInt unscaled = RellBigInt::fromDecimalString(coeff);

    return RellBigDec(std::move(unscaled), static_cast<int32_t>(scl));
}

// =====================================================================================
// valueOf — BigDecimal.valueOf(long, int) / valueOf(long).
// Numerically just RellBigDec(fromInt64(unscaledVal), scale); the OpenJDK cache
// of small constants is a perf detail.
// =====================================================================================
RellBigDec RellBigDec::valueOf(int64_t unscaledVal, int32_t scale) {
    return RellBigDec(RellBigInt::fromInt64(unscaledVal), scale);
}

RellBigDec RellBigDec::valueOf(int64_t val) {
    return RellBigDec(RellBigInt::fromInt64(val), 0);
}

// =====================================================================================
// stripTrailingZeros — createAndStripZerosToMatchScale(intVal, scale, MIN).
//
// Remove trailing-zero factors of ten from the unscaled value, lowering scale by
// one each time, until the unscaled value is not divisible by ten (or is odd, a
// fast reject) or is ZERO. preferredScale = -infinity here, so it strips every
// trailing zero; the resulting scale MAY be negative (e.g. 600 -> 6 / scale -2),
// matching the JVM. ZERO collapses to scale 0.
// =====================================================================================
RellBigDec RellBigDec::stripTrailingZeros() const {
    if (unscaled_.isZero()) {
        return RellBigDec();  // ZERO at scale 0
    }

    RellBigInt u = unscaled_;
    int64_t scale = scale_;  // widen so the (scale - 1) steps cannot underflow int32

    const RellBigInt ten = RellBigInt::fromInt64(10);
    // preferredScale = Long.MIN_VALUE ==> `scale > preferredScale` is always true,
    // so the loop runs purely on the divisibility condition.
    while (u.compareMagnitude(ten) >= 0) {
        // Odd magnitude can't end in a zero digit — fast reject (matches
        // intVal.testBit(0), the low bit of the magnitude).
        if ((u.toBytes().back() & 1) != 0) {
            break;
        }
        std::pair<RellBigInt, RellBigInt> qr = u.divideAndRemainder(ten);
        if (!qr.second.isZero()) {
            break;  // non-zero remainder: last digit isn't 0
        }
        u = qr.first;
        --scale;
    }

    // The consensus envelope keeps scale within int32 (canonical 20); the widen
    // above is only to mirror OpenJDK's `(long) scale - 1` overflow guard.
    if (scale > 2147483647LL || scale < -2147483648LL) {
        // TODO: unreachable within the Rell decimal range; throw rather than
        // silently truncate if a caller ever feeds an extreme scale.
        throw RellArithError("", "scale out of range in stripTrailingZeros");
    }

    return RellBigDec(std::move(u), static_cast<int32_t>(scale));
}

// =====================================================================================
// toPlainString — never E-notation.
//   scale == 0 : just the unscaled integer string.
//   scale  < 0 : integer with `-scale` trailing zeros appended (no decimal point).
//   scale  > 0 : getValueString(signum, |unscaled|.toString(), scale).
// =====================================================================================
std::string RellBigDec::toPlainString() const {
    if (scale_ == 0) {
        return unscaled_.toString();
    }

    if (scale_ < 0) {  // no decimal point; append trailing zeros
        if (unscaled_.isZero()) {
            return "0";
        }
        std::string str = unscaled_.toString();
        // -scale_ as a non-negative count (scale_ < 0 here, so this is positive).
        int64_t trailingZeros = -static_cast<int64_t>(scale_);
        str.reserve(str.size() + static_cast<std::size_t>(trailingZeros));
        for (int64_t i = 0; i < trailingZeros; ++i) {
            str.push_back('0');
        }
        return str;
    }

    // scale_ > 0 : insert a decimal point into the magnitude string.
    const int signum = unscaled_.signum();
    std::string intString = unscaled_.abs().toString();

    // ----- getValueString(signum, intString, scale) -----
    // insertionPoint = intString.length() - scale.
    int64_t insertionPoint = static_cast<int64_t>(intString.size()) - scale_;
    std::string result;
    if (insertionPoint == 0) {  // point right before the digits
        result = (signum < 0 ? "-0." : "0.") + intString;
    } else if (insertionPoint > 0) {  // point inside the digits
        result = intString;
        result.insert(result.begin() + insertionPoint, '.');
        if (signum < 0) {
            result.insert(result.begin(), '-');
        }
    } else {  // zeros between point and digits
        result = signum < 0 ? "-0." : "0.";
        for (int64_t i = 0; i < -insertionPoint; ++i) {
            result.push_back('0');
        }
        result += intString;
    }
    return result;
}

// =====================================================================================
// toString — BigDecimal.toString() == layoutChars(sci=true).
//
// Decides plain vs scientific layout from scale and the adjusted exponent
//   adjusted = -scale + (coeffLen - 1)
// where coeffLen is the digit count of |unscaled|.
//   PLAIN  when  scale >= 0 && adjusted >= -6
//   SCIENTIFIC (E-notation) otherwise.
// =====================================================================================
std::string RellBigDec::toString() const {
    if (scale_ == 0) {  // zero scale is trivial
        return unscaled_.toString();
    }

    // Significand digits as an absolute value.
    std::string coeff = unscaled_.abs().toString();
    std::string buf;
    if (signum() < 0) {
        buf.push_back('-');  // prefix '-' if negative
    }

    int64_t coeffLen = static_cast<int64_t>(coeff.size());
    int64_t adjusted = -static_cast<int64_t>(scale_) + (coeffLen - 1);

    if (scale_ >= 0 && adjusted >= -6) {  // plain number
        int64_t pad = static_cast<int64_t>(scale_) - coeffLen;  // count of padding zeros
        if (pad >= 0) {                                         // 0.xxx form
            buf.push_back('0');
            buf.push_back('.');
            for (; pad > 0; --pad) {
                buf.push_back('0');
            }
            buf += coeff;
        } else {  // xx.xx form
            // first (-pad) digits, then '.', then the remaining `scale` digits.
            buf.append(coeff, 0, static_cast<std::size_t>(-pad));
            buf.push_back('.');
            buf.append(coeff, static_cast<std::size_t>(-pad), static_cast<std::size_t>(scale_));
        }
    } else {  // E-notation (scientific; sci == true)
        buf.push_back(coeff[0]);  // first character
        if (coeffLen > 1) {       // more digits to come
            buf.push_back('.');
            buf.append(coeff, 1, static_cast<std::size_t>(coeffLen - 1));
        }
        if (adjusted != 0) {
            buf.push_back('E');
            if (adjusted > 0) {  // force '+' for a positive exponent
                buf.push_back('+');
            }
            buf += std::to_string(adjusted);
        }
    }

    return buf;
}

}  // namespace rell::num
