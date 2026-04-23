// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigint.h — rell::num::RellBigInt: a faithful C++17 reimplementation of
// java.math.BigInteger (OpenJDK 21, jdk-21+35), used by the Rell LLVM backend to
// evaluate big_integer arithmetic natively, bit-exact with the JVM, with zero JNI.
//
// This is a PURE numerics header: it depends only on the C++ standard library
// (<cstdint>, <vector>, <string>, <stdexcept>) and must compile/test standalone.
// No JNI, no LLVM, no FlatBuffers. The Rell semantic envelope (range limits,
// overflow error codes/messages, normalization) lives in rell_bigint_math.* /
// rell_decimal_math.* layered ON TOP of this class; this header models ONLY the
// faithful OpenJDK BigInteger arithmetic. The only Rell-flavoured thing here is
// RellArithError (so callers can convert to Rt_Exception), and even that is thrown
// only for the genuinely-arithmetic conditions BigInteger itself signals
// (division by zero, negative sqrt, negative exponent, longValueExact overflow).
//
// =====================================================================================
// LIMB REPRESENTATION (mirrors OpenJDK BigInteger.mag/signum exactly)
// =====================================================================================
//
//   std::vector<uint32_t> mag_;   // magnitude, BIG-ENDIAN: mag_[0] is MOST significant
//   int32_t               signum_; // -1, 0, +1
//
// ENDIANNESS: BIG-ENDIAN, identical to OpenJDK's `int[] mag`. mag_[0] is the most
// significant 32-bit limb; mag_[mag_.size()-1] is the least significant. Each limb is
// an unsigned 32-bit "int" word; OpenJDK stores these as signed `int` and masks with
// LONG_MASK (0xffffffffL) on use — we store them already-unsigned as uint32_t and
// promote to uint64_t for arithmetic, which is the same bit pattern.
//
// INVARIANTS (enforced by every factory/operation; checked by normalize()):
//   * No leading zero limbs: if mag_ is non-empty, mag_[0] != 0.
//   * Minimality: signum_ == 0  <=>  mag_.empty().  (ZERO is the empty magnitude.)
//   * signum_ is exactly one of {-1, 0, +1}.
//   * There is exactly ONE representation per integer value.
//
// (OpenJDK additionally caps magnitude at MAX_MAG_LENGTH = Integer.MAX_VALUE/32 + 1
//  ~ 67M limbs; the Rell envelope (|v| < 10^131072 ~ 435000 bits ~ 13600 limbs) is far
//  below that, so we do not need that guard for correctness — see rell_bigint_math.*.)
//
// =====================================================================================
// DIVISION CONTRACT (Knuth Algorithm D, from OpenJDK MutableBigInteger.divideKnuth)
// =====================================================================================
//
// divide() truncates toward ZERO (quotient sign = signum_a * signum_b; the magnitude
// is floor(|a|/|b|)). remainder() takes the sign of the DIVIDEND (a), matching
// BigInteger.remainder: a == divide(a,b)*b + remainder(a,b). divideAndRemainder()
// returns both consistently. Division by zero throws RellArithError("","/ by zero")
// at the magnitude level; the Rell-specific div0 message is applied by the caller.
//
// The magnitude division (impl in rell_bigint_muldiv.cpp) is Knuth Algorithm D on
// big-endian uint32_t limbs:
//   * Single-limb divisor -> divideOneWord (long division, 64/32 word steps).
//   * Else normalize by left-shifting BOTH operands by clz(divisor[0]) so the divisor's
//     top limb has its MSB set; estimate each quotient digit qhat from the top two
//     dividend limbs over the top divisor limb, refine qhat with the (qhat,rhat)
//     correction loop using the second divisor limb, then mulsub the qhat*divisor
//     product out of the running remainder; if that under-flows (over-estimate) add the
//     divisor back once (divadd) and decrement qhat. Finally right-shift the remainder
//     back by the normalization shift. Helpers: mulsub, divadd, divWord (see .cpp).
//
// Correctness target: exact for operands up to ~10^131072 (~435000 bits). Karatsuba /
// Toom-Cook multiply and Burnikel-Ziegler division are PERF-only and intentionally
// omitted (see // TODO(perf) markers): schoolbook multiply + Knuth-D division are
// O(n^2) but bit-exact, which is what consensus requires.

#ifndef RELL_BIGINT_H
#define RELL_BIGINT_H

#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace rell::num {

// -------------------------------------------------------------------------------------
// RellArithError — carries the exact Rell error code + message strings so the
// integration layer can map it onto Rt_Exception. The PURE BigInteger operations throw
// this only for the conditions BigInteger itself raises (div/0, sqrt of negative,
// negative exponent, longValueExact out of long range); they use empty code "" with the
// plain arithmetic message. The Rell envelope layer (rell_bigint_math.*) throws it with
// code "bigint:overflow" / "decimal:overflow" and the range message.
class RellArithError : public std::runtime_error {
public:
    RellArithError(std::string code, const std::string &message)
        : std::runtime_error(message), code_(std::move(code)) {}

    // Rell error code, e.g. "bigint:overflow" / "decimal:overflow"; empty for the
    // plain arithmetic conditions BigInteger raises internally.
    const std::string &code() const noexcept { return code_; }

private:
    std::string code_;
};

// =====================================================================================
// RellBigInt — faithful java.math.BigInteger port. Immutable value semantics: every
// operation returns a fresh RellBigInt; *this is never mutated.
// =====================================================================================
class RellBigInt {
public:
    // ---------------------------------------------------------------------------------
    // Factories / constructors            (impl: rell_bigint_core.cpp)
    // ---------------------------------------------------------------------------------

    // ZERO: signum 0, empty magnitude.
    RellBigInt();

    // Exact integer from a signed 64-bit value.
    static RellBigInt fromInt64(int64_t value);

    // Parse a base-10 decimal string: optional leading '+'/'-', then one or more ASCII
    // digits. Leading zeros allowed ("007" == 7); "-0"/"+0"/"0" == ZERO. No whitespace,
    // no underscores, no radix prefixes. Throws RellArithError("","invalid decimal
    // string ...") on malformed input. Mirrors BigInteger(String, 10) (radix 10 only;
    // built via destructiveMulAdd in 9-digit groups — see .cpp).
    static RellBigInt fromDecimalString(const std::string &s);

    // Two's-complement, BIG-ENDIAN bytes, exactly as java.math.BigInteger(byte[]):
    //   * empty input -> throws (BigInteger rejects zero-length).
    //   * if the top bit of bytes[0] is set, the value is NEGATIVE: makePositive
    //     (strip the sign-extension 0xFF bytes, two's-complement back to magnitude),
    //     signum = -1.
    //   * else stripLeadingZeroBytes, signum = (mag empty ? 0 : +1).
    // This is the inverse of toBytes().
    static RellBigInt fromBytes(const std::vector<uint8_t> &bytes);

    static RellBigInt zero();
    static RellBigInt one();

    // ---------------------------------------------------------------------------------
    // Marshalling out                     (impl: rell_bigint_core.cpp)
    // ---------------------------------------------------------------------------------

    // Two's-complement, BIG-ENDIAN, minimal length, exactly as BigInteger.toByteArray():
    //   * ZERO -> { 0x00 } (single zero byte).
    //   * positive: emit magnitude big-endian, prepending one 0x00 byte iff the top bit
    //     of the most-significant byte would otherwise read as negative.
    //   * negative: emit the two's-complement encoding of the magnitude, prepending one
    //     0xFF byte iff needed so the sign bit is set.
    // Round-trips with fromBytes(). (impl detail: byteLength = bitLength()/8 + 1.)
    std::vector<uint8_t> toBytes() const;

    // ---------------------------------------------------------------------------------
    // Inspectors / sign / unary           (impl: rell_bigint_core.cpp)
    // ---------------------------------------------------------------------------------

    int signum() const noexcept { return signum_; }
    bool isZero() const noexcept { return signum_ == 0; }
    bool isNegative() const noexcept { return signum_ < 0; }

    RellBigInt negate() const;  // -this (flips signum; ZERO stays ZERO).
    RellBigInt abs() const;     // |this|.

    // compareTo: -1 / 0 / +1, total order over the integers (signum first, then
    // magnitude). compareMagnitude compares |this| vs |val| only (length, then limbs
    // most- to least-significant).
    int compareTo(const RellBigInt &val) const noexcept;
    int compareMagnitude(const RellBigInt &val) const noexcept;
    bool equals(const RellBigInt &val) const noexcept;

    // ---------------------------------------------------------------------------------
    // Core arithmetic                     (impl: rell_bigint_core.cpp)
    //
    // add/subtract/multiply are EXACT (no range check — the Rell envelope check is a
    // separate pass in rell_bigint_math.*). Magnitude helpers addMag/subMag (subMag
    // requires the first arg >= second) handle the sign algebra; multiply is schoolbook
    // multiplyToLen (O(n*m)). // TODO(perf): Karatsuba >= 80 limbs, Toom-Cook >= 240.
    // ---------------------------------------------------------------------------------

    RellBigInt add(const RellBigInt &val) const;
    RellBigInt subtract(const RellBigInt &val) const;
    RellBigInt multiply(const RellBigInt &val) const;

    // ---------------------------------------------------------------------------------
    // Division & multiplicative ops       (impl: rell_bigint_muldiv.cpp)
    //
    // divide: truncate toward zero. remainder: sign of dividend (*this).
    // Both throw RellArithError("","/ by zero") when val.isZero(). divideAndRemainder
    // returns {quotient, remainder} computed together via one Knuth-D pass.
    // ---------------------------------------------------------------------------------

    RellBigInt divide(const RellBigInt &val) const;
    RellBigInt remainder(const RellBigInt &val) const;
    std::pair<RellBigInt, RellBigInt> divideAndRemainder(const RellBigInt &val) const;

    // ---------------------------------------------------------------------------------
    // Extra ops                           (impl: rell_bigint_extra.cpp)
    // ---------------------------------------------------------------------------------

    // pow(exponent): this ** exponent, exponent >= 0. Negative exponent throws
    // RellArithError("","Negative exponent"). 0**0 == ONE. Sign of result is negative
    // iff this<0 and exponent is odd. Binary exponentiation (square-and-multiply),
    // factoring out powers of two as OpenJDK does. (uint32_t exponent: Rell pow takes a
    // non-negative integer exponent.)
    RellBigInt pow(uint32_t exponent) const;

    // sqrt(): floor of the exact square root, non-negative operands only. Throws
    // RellArithError("","negative BigInteger") if this<0. Bit-exact with
    // BigInteger.sqrt() (Newton iteration on MutableBigInteger; long fast path when
    // bitLength()<=63, else seeded from a double estimate and refined by
    // xk1 = (xk + this/xk)/2 until xk1 >= xk).
    RellBigInt sqrt() const;

    // bitLength(): number of bits in the minimal two's-complement representation,
    // EXCLUDING the sign bit (BigInteger.bitLength). 0 for ZERO. For positive powers of
    // two and for negatives the OpenJDK off-by-one rule applies (negative exact powers
    // of two have one fewer bit) — replicated in .cpp.
    int64_t bitLength() const noexcept;

    // longValue(): low 64 bits, SILENTLY TRUNCATING (signum * low two limbs), matching
    // BigInteger.longValue(). longValueExact(): same value but throws
    // RellArithError("","BigInteger out of long range") unless the value fits a signed
    // 64-bit long exactly.
    int64_t longValue() const noexcept;
    int64_t longValueExact() const;

    // ---------------------------------------------------------------------------------
    // toString / hash                     (impl: rell_bigint_core.cpp)
    // ---------------------------------------------------------------------------------

    // Canonical base-10 string with a leading '-' for negatives, no leading zeros,
    // "0" for ZERO. Faithful to BigInteger.toString(10): repeatedly divide the
    // magnitude by 10^9 collecting 9-digit groups (destructive divide), then print the
    // most-significant group without padding and every following group zero-padded to 9.
    // // TODO(perf): Schoenhage recursive base conversion above 20 limbs.
    std::string toString() const;

    // Cheap 32-bit hash over (signum_, mag_) — FNV-1a style, value-stable. Not required
    // to match Java's hashCode; provided so RellBigInt can key C++ containers.
    std::size_t hash() const noexcept;

private:
    // Big-endian magnitude (mag_[0] most significant); signum in {-1,0,+1}.
    std::vector<uint32_t> mag_;
    int32_t signum_;

    // Internal ctor from an owned magnitude + sign; normalizes (strips leading zero
    // limbs, forces signum 0 iff empty). impl: rell_bigint_core.cpp.
    RellBigInt(std::vector<uint32_t> mag, int32_t signum);

    // Strip leading zero limbs and reconcile signum with emptiness. impl: core.
    void normalize();

    // ----- magnitude-only helpers (operate on big-endian uint32_t vectors) -----
    //  addMag:   |x| + |y|, propagating a 32-bit carry from least- to most-significant.
    //  subMag:   |big| - |little|, REQUIRES |big| >= |little|; borrow propagation.
    //  mulMag:   schoolbook multiplyToLen, |x| * |y|.            (impl: muldiv)
    //  divMag:   Knuth Algorithm D, returns {quotientMag, remainderMag}. (impl: muldiv)
    //  cmpMag:   compare two magnitudes (length then limbs).     (impl: core)
    static std::vector<uint32_t> addMag(const std::vector<uint32_t> &x,
                                        const std::vector<uint32_t> &y);
    static std::vector<uint32_t> subMag(const std::vector<uint32_t> &big,
                                        const std::vector<uint32_t> &little);
    static std::vector<uint32_t> mulMag(const std::vector<uint32_t> &x,
                                        const std::vector<uint32_t> &y);
    static std::pair<std::vector<uint32_t>, std::vector<uint32_t>> divMag(
        const std::vector<uint32_t> &dividend, const std::vector<uint32_t> &divisor);
    static int cmpMag(const std::vector<uint32_t> &x,
                      const std::vector<uint32_t> &y) noexcept;
};

inline bool operator==(const RellBigInt &a, const RellBigInt &b) noexcept {
    return a.equals(b);
}
inline bool operator!=(const RellBigInt &a, const RellBigInt &b) noexcept {
    return !a.equals(b);
}
inline bool operator<(const RellBigInt &a, const RellBigInt &b) noexcept {
    return a.compareTo(b) < 0;
}
inline bool operator>(const RellBigInt &a, const RellBigInt &b) noexcept {
    return a.compareTo(b) > 0;
}
inline bool operator<=(const RellBigInt &a, const RellBigInt &b) noexcept {
    return a.compareTo(b) <= 0;
}
inline bool operator>=(const RellBigInt &a, const RellBigInt &b) noexcept {
    return a.compareTo(b) >= 0;
}

}  // namespace rell::num

#endif  // RELL_BIGINT_H
