// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigint_core.cpp — core of rell::num::RellBigInt: storage normalization,
// constructors/factories, sign/unary, comparison, add/subtract, two's-complement
// byte marshalling, bitLength/longValue, and base-10 toString.
//
// Faithful translation of the corresponding algorithms in java.math.BigInteger
// (OpenJDK 21, jdk-21+35). Magnitude is big-endian std::vector<uint32_t> (mag_[0] is
// the most-significant limb), exactly mirroring OpenJDK's int[] mag; signed limbs are
// modelled as already-unsigned uint32_t promoted to uint64_t for arithmetic (the same
// bit pattern as OpenJDK's `x & LONG_MASK`). See rell_bigint.h for full invariants.
//
// multiply/divide/remainder/divideAndRemainder live in rell_bigint_muldiv.cpp; pow/sqrt
// live in rell_bigint_extra.cpp. This file owns everything else.

#include "rell_bigint.h"

#include <algorithm>

namespace rell::num {

namespace {

// OpenJDK's LONG_MASK: low 32 bits set, used to treat an int as unsigned in long math.
constexpr uint64_t LONG_MASK = 0xffffffffULL;

// bitLengthForInt(int n) = 32 - numberOfLeadingZeros(n), for n != 0.
int bit_length_for_word(uint32_t n) {
    int len = 0;
    while (n != 0) {
        n >>= 1;
        ++len;
    }
    return len;
}

}  // namespace

// =====================================================================================
// Normalization / internal ctor
// =====================================================================================

void RellBigInt::normalize() {
    // Strip leading zero limbs (trustedStripLeadingZeroInts).
    std::size_t keep = 0;
    while (keep < mag_.size() && mag_[keep] == 0) {
        ++keep;
    }
    if (keep > 0) {
        mag_.erase(mag_.begin(), mag_.begin() + static_cast<std::ptrdiff_t>(keep));
    }
    // Minimality invariant: signum_ == 0  <=>  mag_.empty().
    if (mag_.empty()) {
        signum_ = 0;
    } else if (signum_ == 0) {
        // A non-empty magnitude must carry a sign; callers never pass signum 0 with a
        // non-empty magnitude, but guard anyway to keep the invariant total.
        signum_ = 1;
    }
}

RellBigInt::RellBigInt(std::vector<uint32_t> mag, int32_t signum)
    : mag_(std::move(mag)), signum_(signum) {
    normalize();
}

// =====================================================================================
// Factories / constructors
// =====================================================================================

RellBigInt::RellBigInt() : mag_(), signum_(0) {}

RellBigInt RellBigInt::zero() {
    return RellBigInt();
}

RellBigInt RellBigInt::one() {
    return fromInt64(1);
}

RellBigInt RellBigInt::fromInt64(int64_t value) {
    if (value == 0) {
        return RellBigInt();
    }
    int32_t signum = value < 0 ? -1 : 1;
    // Take absolute value as unsigned to handle INT64_MIN without UB.
    uint64_t u = value < 0 ? (~static_cast<uint64_t>(value) + 1ULL)
                           : static_cast<uint64_t>(value);
    uint32_t hi = static_cast<uint32_t>(u >> 32);
    uint32_t lo = static_cast<uint32_t>(u & LONG_MASK);
    std::vector<uint32_t> mag;
    if (hi != 0) {
        mag.push_back(hi);
        mag.push_back(lo);
    } else {
        mag.push_back(lo);
    }
    return RellBigInt(std::move(mag), signum);
}

// destructiveMulAdd(x, y, z): x = x*y + z, x big-endian, y,z 32-bit unsigned operands.
static void destructive_mul_add(std::vector<uint32_t> &x, uint32_t y, uint32_t z) {
    uint64_t ylong = static_cast<uint64_t>(y);
    uint64_t zlong = static_cast<uint64_t>(z);
    std::size_t len = x.size();

    // Multiply phase.
    uint64_t product;
    uint64_t carry = 0;
    for (std::size_t i = len; i-- > 0;) {
        product = ylong * (static_cast<uint64_t>(x[i]) & LONG_MASK) + carry;
        x[i] = static_cast<uint32_t>(product & LONG_MASK);
        carry = product >> 32;
    }

    // Add phase.
    uint64_t sum = (static_cast<uint64_t>(x[len - 1]) & LONG_MASK) + zlong;
    x[len - 1] = static_cast<uint32_t>(sum & LONG_MASK);
    carry = sum >> 32;
    for (std::size_t i = len - 1; i-- > 0;) {
        sum = (static_cast<uint64_t>(x[i]) & LONG_MASK) + carry;
        x[i] = static_cast<uint32_t>(sum & LONG_MASK);
        carry = sum >> 32;
    }
    // For y,z < 2^32 and the leading limb non-zero, the final carry is always 0 here
    // because the buffer was sized with a leading zero limb in fromDecimalString.
}

RellBigInt RellBigInt::fromDecimalString(const std::string &s) {
    const std::size_t n = s.size();
    if (n == 0) {
        throw RellArithError("", "invalid decimal string: empty");
    }

    std::size_t cursor = 0;
    int32_t sign = 1;
    char c0 = s[0];
    if (c0 == '-') {
        sign = -1;
        cursor = 1;
    } else if (c0 == '+') {
        sign = 1;
        cursor = 1;
    }
    if (cursor == n) {
        throw RellArithError("", "invalid decimal string: " + s);
    }

    // Skip leading zeros (count significant digit start).
    std::size_t first_significant = cursor;
    while (first_significant < n && s[first_significant] == '0') {
        ++first_significant;
    }
    // Validate ALL remaining chars are digits (including the skipped zeros region).
    for (std::size_t i = cursor; i < n; ++i) {
        char c = s[i];
        if (c < '0' || c > '9') {
            throw RellArithError("", "invalid decimal string: " + s);
        }
    }
    if (first_significant == n) {
        // Value is zero (e.g. "0", "-0", "+000").
        return RellBigInt();
    }

    const std::size_t num_digits = n - first_significant;

    // Estimate the magnitude size in 32-bit words. bitLength <= numDigits * log2(10);
    // OpenJDK uses (numDigits * 3402 >>> 10) + 1 bits, then ceil(/32). We mirror that
    // (3402/1024 ~ 3.3220 >= log2(10) ~ 3.3219).
    const uint64_t num_bits = ((static_cast<uint64_t>(num_digits) * 3402ULL) >> 10) + 1ULL;
    const std::size_t num_words = static_cast<std::size_t>((num_bits + 31ULL) / 32ULL) + 1;
    std::vector<uint32_t> mag(num_words, 0);

    // Process the first (possibly short) group, then full 9-digit groups.
    // digitsPerInt[10] = 9, intRadix[10] = 1_000_000_000.
    constexpr int DIGITS_PER_INT = 9;
    constexpr uint32_t INT_RADIX = 1000000000u;

    std::size_t pos = first_significant;
    std::size_t first_group_len = num_digits % DIGITS_PER_INT;
    if (first_group_len == 0) {
        first_group_len = DIGITS_PER_INT;
    }

    auto parse_group = [&](std::size_t start, std::size_t len) -> uint32_t {
        uint32_t v = 0;
        for (std::size_t i = 0; i < len; ++i) {
            v = v * 10u + static_cast<uint32_t>(s[start + i] - '0');
        }
        return v;
    };

    // Seed the magnitude with the first group (it fits in a single limb).
    mag[num_words - 1] = parse_group(pos, first_group_len);
    pos += first_group_len;

    // destructiveMulAdd over the remaining full groups.
    while (pos < n) {
        uint32_t group_val = parse_group(pos, DIGITS_PER_INT);
        pos += DIGITS_PER_INT;
        destructive_mul_add(mag, INT_RADIX, group_val);
    }

    return RellBigInt(std::move(mag), sign);
}

// ---- two's-complement, big-endian byte conversion ----

// Build a magnitude (big-endian limbs) from big-endian bytes, stripping leading zeros.
static std::vector<uint32_t> bytes_to_mag_stripping_zeros(const std::vector<uint8_t> &a,
                                                          std::size_t offset,
                                                          std::size_t length) {
    std::size_t end = offset + length;
    std::size_t keep = offset;
    while (keep < end && a[keep] == 0) {
        ++keep;
    }
    std::size_t byte_len = end - keep;
    std::size_t int_len = (byte_len + 3) / 4;
    std::vector<uint32_t> mag(int_len, 0);
    // Fill from least-significant byte upward.
    std::size_t b = end;  // one past the last byte
    for (std::size_t i = int_len; i-- > 0;) {
        uint32_t word = 0;
        for (int shift = 0; shift < 32 && b > keep; shift += 8) {
            word |= (static_cast<uint32_t>(a[--b]) << shift);
        }
        mag[i] = word;
    }
    return mag;  // already minimal (top limb non-zero unless all-zero -> empty result)
}

// makePositive: negative two's-complement big-endian bytes -> positive magnitude.
static std::vector<uint32_t> make_positive(const std::vector<uint8_t> &a,
                                           std::size_t offset, std::size_t length) {
    // Negate the full two's-complement value to recover the magnitude: pack ALL bytes
    // (un-stripped) into big-endian 32-bit limbs, then invert all bits and add one. The
    // input's high bit is set (it is negative); the inverted+incremented value is the
    // positive magnitude. Leading zero limbs are stripped by the RellBigInt ctor.
    std::size_t end = offset + length;

    // Pack bytes into big-endian limbs, least-significant byte first.
    std::size_t int_len = (length + 3) / 4;
    std::vector<uint32_t> result(int_len, 0);
    std::size_t b = end;
    for (std::size_t i = int_len; i-- > 0;) {
        uint32_t word = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            // After the real bytes run out, sign-extend with 0xFF (value is negative);
            // its complement is 0x00, contributing nothing to the magnitude.
            uint8_t byte = (b > offset) ? a[--b] : 0xFF;
            word |= (static_cast<uint32_t>(static_cast<uint8_t>(~byte)) << shift);
        }
        result[i] = word;
    }

    // Add one (two's complement): add 1, propagate carry from least-significant limb.
    for (std::size_t i = int_len; i-- > 0;) {
        uint64_t sum = (static_cast<uint64_t>(result[i]) & LONG_MASK) + 1ULL;
        result[i] = static_cast<uint32_t>(sum & LONG_MASK);
        if ((sum >> 32) == 0) {
            break;  // no carry out -> done
        }
    }
    return result;
}

RellBigInt RellBigInt::fromBytes(const std::vector<uint8_t> &bytes) {
    if (bytes.empty()) {
        // BigInteger(byte[]) rejects a zero-length array.
        throw RellArithError("", "Zero length BigInteger");
    }
    if (static_cast<int8_t>(bytes[0]) < 0) {
        // Negative: makePositive, signum -1.
        std::vector<uint32_t> mag = make_positive(bytes, 0, bytes.size());
        return RellBigInt(std::move(mag), -1);
    } else {
        std::vector<uint32_t> mag = bytes_to_mag_stripping_zeros(bytes, 0, bytes.size());
        int32_t signum = mag.empty() ? 0 : 1;
        return RellBigInt(std::move(mag), signum);
    }
}

// getInt(n): the n-th least-significant 32-bit word of the two's-complement form.
// n is 0-based counting from the least significant int.
int64_t RellBigInt::longValue() const noexcept {
    // longValue() == signum * (low two limbs as an unsigned 64-bit value), via getInt.
    // Replicated directly here since getInt is private to this translation unit logic.
    uint64_t result = 0;
    for (int i = 1; i >= 0; --i) {
        // getInt(i): out-of-range magnitude word is always 0; the sign transform below
        // produces the correct two's-complement sign extension.
        uint32_t mag_int;
        if (static_cast<std::size_t>(i) >= mag_.size()) {
            mag_int = 0u;
        } else {
            mag_int = mag_[mag_.size() - static_cast<std::size_t>(i) - 1];
        }
        uint32_t word;
        if (signum_ >= 0) {
            word = mag_int;
        } else {
            // firstNonzeroIntNum: least-significant index (0-based from LSB) of first
            // non-zero limb.
            std::size_t fn = 0;
            bool found = false;
            for (std::size_t j = mag_.size(); j-- > 0;) {
                if (mag_[j] != 0) {
                    fn = mag_.size() - j - 1;
                    found = true;
                    break;
                }
            }
            if (!found) {
                fn = 0;  // ZERO never reaches here (signum_<0 implies non-empty mag_)
            }
            word = (static_cast<std::size_t>(i) <= fn)
                       ? static_cast<uint32_t>(-static_cast<int64_t>(mag_int))
                       : ~mag_int;
        }
        result = (result << 32) | (static_cast<uint64_t>(word) & LONG_MASK);
    }
    return static_cast<int64_t>(result);
}

int64_t RellBigInt::longValueExact() const {
    if (mag_.size() <= 2 && bitLength() <= 63) {
        return longValue();
    }
    throw RellArithError("", "BigInteger out of long range");
}

// =====================================================================================
// Marshalling out
// =====================================================================================

std::vector<uint8_t> RellBigInt::toBytes() const {
    int64_t bit_len = bitLength();
    std::size_t byte_len = static_cast<std::size_t>(bit_len / 8) + 1;
    std::vector<uint8_t> result(byte_len, 0);

    // firstNonzeroIntNum for negative path.
    std::size_t first_nonzero = 0;
    if (signum_ < 0) {
        for (std::size_t j = mag_.size(); j-- > 0;) {
            if (mag_[j] != 0) {
                first_nonzero = mag_.size() - j - 1;
                break;
            }
        }
    }

    // getInt(intIndex): two's-complement word at 0-based LSB index.
    auto get_int = [&](std::size_t int_index) -> uint32_t {
        uint32_t mag_int;
        if (int_index >= mag_.size()) {
            mag_int = 0u;  // out-of-range word is 0; sign transform handles extension
        } else {
            mag_int = mag_[mag_.size() - int_index - 1];
        }
        if (signum_ >= 0) {
            return mag_int;
        }
        return (int_index <= first_nonzero)
                   ? static_cast<uint32_t>(-static_cast<int64_t>(mag_int))
                   : ~mag_int;
    };

    for (std::size_t i = byte_len, byte_pos = 0; i-- > 0;) {
        uint32_t word = get_int(byte_pos / 4);
        result[i] = static_cast<uint8_t>(word >> ((byte_pos % 4) * 8));
        ++byte_pos;
    }
    return result;
}

// =====================================================================================
// Sign / unary
// =====================================================================================

RellBigInt RellBigInt::negate() const {
    return RellBigInt(mag_, -signum_);
}

RellBigInt RellBigInt::abs() const {
    return signum_ < 0 ? negate() : *this;
}

// =====================================================================================
// Comparison
// =====================================================================================

int RellBigInt::cmpMag(const std::vector<uint32_t> &x,
                       const std::vector<uint32_t> &y) noexcept {
    if (x.size() < y.size()) {
        return -1;
    }
    if (x.size() > y.size()) {
        return 1;
    }
    for (std::size_t i = 0; i < x.size(); ++i) {
        uint32_t a = x[i];
        uint32_t b = y[i];
        if (a != b) {
            return a < b ? -1 : 1;
        }
    }
    return 0;
}

int RellBigInt::compareMagnitude(const RellBigInt &val) const noexcept {
    return cmpMag(mag_, val.mag_);
}

int RellBigInt::compareTo(const RellBigInt &val) const noexcept {
    if (signum_ == val.signum_) {
        if (signum_ == 0) {
            return 0;
        }
        int cmp = cmpMag(mag_, val.mag_);
        return signum_ > 0 ? cmp : -cmp;
    }
    return signum_ > val.signum_ ? 1 : -1;
}

bool RellBigInt::equals(const RellBigInt &val) const noexcept {
    return signum_ == val.signum_ && mag_ == val.mag_;
}

// =====================================================================================
// Magnitude add / subtract helpers (big-endian)
// =====================================================================================

std::vector<uint32_t> RellBigInt::addMag(const std::vector<uint32_t> &x,
                                         const std::vector<uint32_t> &y) {
    // Ensure x is the longer operand.
    const std::vector<uint32_t> *big = &x;
    const std::vector<uint32_t> *small = &y;
    if (big->size() < small->size()) {
        std::swap(big, small);
    }
    std::size_t big_len = big->size();
    std::size_t small_len = small->size();

    std::vector<uint32_t> result(big_len, 0);
    uint64_t sum = 0;

    std::size_t bi = big_len;
    std::size_t si = small_len;
    // Add common low part.
    while (si > 0) {
        --bi;
        --si;
        sum = (static_cast<uint64_t>((*big)[bi]) & LONG_MASK) +
              (static_cast<uint64_t>((*small)[si]) & LONG_MASK) + (sum >> 32);
        result[bi] = static_cast<uint32_t>(sum & LONG_MASK);
    }
    // Propagate carry through the rest of the longer operand.
    bool carry = (sum >> 32) != 0;
    while (bi > 0 && carry) {
        --bi;
        result[bi] = (*big)[bi] + 1u;
        carry = (result[bi] == 0);
    }
    // Copy any remaining high limbs.
    while (bi > 0) {
        --bi;
        result[bi] = (*big)[bi];
    }
    // If a carry escaped the top, prepend a new high limb.
    if (carry) {
        result.insert(result.begin(), 1u);
    }
    return result;
}

std::vector<uint32_t> RellBigInt::subMag(const std::vector<uint32_t> &big,
                                         const std::vector<uint32_t> &little) {
    // Requires |big| >= |little|. result = big - little.
    std::size_t big_len = big.size();
    std::size_t little_len = little.size();
    std::vector<uint32_t> result(big_len, 0);

    int64_t diff = 0;
    std::size_t bi = big_len;
    std::size_t li = little_len;
    while (li > 0) {
        --bi;
        --li;
        diff = (static_cast<int64_t>(big[bi]) & static_cast<int64_t>(LONG_MASK)) -
               (static_cast<int64_t>(little[li]) & static_cast<int64_t>(LONG_MASK)) +
               (diff >> 32);
        result[bi] = static_cast<uint32_t>(diff & static_cast<int64_t>(LONG_MASK));
    }
    // Propagate borrow.
    bool borrow = (diff >> 32) != 0;
    while (bi > 0 && borrow) {
        --bi;
        result[bi] = big[bi] - 1u;
        borrow = (big[bi] == 0);
    }
    while (bi > 0) {
        --bi;
        result[bi] = big[bi];
    }
    return result;  // RellBigInt ctor strips any leading zeros via normalize()
}

// =====================================================================================
// add / subtract (sign algebra)
// =====================================================================================

RellBigInt RellBigInt::add(const RellBigInt &val) const {
    if (val.signum_ == 0) {
        return *this;
    }
    if (signum_ == 0) {
        return val;
    }
    if (val.signum_ == signum_) {
        return RellBigInt(addMag(mag_, val.mag_), signum_);
    }
    // Opposite signs: subtract the smaller magnitude from the larger.
    int cmp = cmpMag(mag_, val.mag_);
    if (cmp == 0) {
        return RellBigInt();  // equal magnitudes, opposite signs -> ZERO
    }
    std::vector<uint32_t> result_mag =
        (cmp > 0) ? subMag(mag_, val.mag_) : subMag(val.mag_, mag_);
    int32_t result_sign = (cmp > 0) ? signum_ : val.signum_;
    return RellBigInt(std::move(result_mag), result_sign);
}

RellBigInt RellBigInt::subtract(const RellBigInt &val) const {
    if (val.signum_ == 0) {
        return *this;
    }
    if (signum_ == 0) {
        return val.negate();
    }
    if (val.signum_ != signum_) {
        // this - (-|val|) == this + |val| with this's sign, etc.
        return RellBigInt(addMag(mag_, val.mag_), signum_);
    }
    int cmp = cmpMag(mag_, val.mag_);
    if (cmp == 0) {
        return RellBigInt();
    }
    std::vector<uint32_t> result_mag =
        (cmp > 0) ? subMag(mag_, val.mag_) : subMag(val.mag_, mag_);
    int32_t result_sign = (cmp > 0) ? signum_ : -signum_;
    return RellBigInt(std::move(result_mag), result_sign);
}

// =====================================================================================
// bitLength
// =====================================================================================

int64_t RellBigInt::bitLength() const noexcept {
    if (mag_.empty()) {
        return 0;
    }
    std::size_t len = mag_.size();
    // n = ((len - 1) << 5) + bitLengthForInt(mag[0])
    int64_t n = (static_cast<int64_t>(len - 1) << 5) + bit_length_for_word(mag_[0]);
    if (signum_ < 0) {
        // Check if magnitude is a power of two: highest limb has exactly one bit set AND
        // every lower limb is zero.
        bool pow2 = (mag_[0] & (mag_[0] - 1)) == 0;  // single bit set in top limb
        if (pow2) {
            for (std::size_t i = 1; i < len; ++i) {
                if (mag_[i] != 0) {
                    pow2 = false;
                    break;
                }
            }
        }
        if (pow2) {
            --n;
        }
    }
    return n;
}

// =====================================================================================
// toString (base 10)
// =====================================================================================

std::string RellBigInt::toString() const {
    if (signum_ == 0) {
        return "0";
    }

    // Destructively divide a copy of the magnitude by 10^9, collecting 9-digit groups
    // from least- to most-significant. smallToString path, radix 10.
    constexpr uint32_t SUPER_RADIX = 1000000000u;  // 10^9
    constexpr int DIGITS_PER_GROUP = 9;

    std::vector<uint32_t> work = mag_;
    std::vector<uint32_t> groups;  // each is a value in [0, 10^9)

    // divideOneWord-style in-place division of `work` by SUPER_RADIX, returning remainder.
    auto div_by_super_radix = [&](std::vector<uint32_t> &m) -> uint32_t {
        uint64_t rem = 0;
        for (std::size_t i = 0; i < m.size(); ++i) {
            uint64_t cur = (rem << 32) | (static_cast<uint64_t>(m[i]) & LONG_MASK);
            m[i] = static_cast<uint32_t>(cur / SUPER_RADIX);
            rem = cur % SUPER_RADIX;
        }
        // Strip leading zero limbs after the division.
        std::size_t keep = 0;
        while (keep < m.size() && m[keep] == 0) {
            ++keep;
        }
        if (keep > 0) {
            m.erase(m.begin(), m.begin() + static_cast<std::ptrdiff_t>(keep));
        }
        return static_cast<uint32_t>(rem);
    };

    while (!work.empty()) {
        groups.push_back(div_by_super_radix(work));
    }

    // Assemble: most-significant group unpadded, every following group zero-padded to 9.
    std::string out;
    if (signum_ < 0) {
        out.push_back('-');
    }
    // groups are in least-significant-first order; emit reversed.
    std::size_t g = groups.size();
    // Leading group: no padding.
    --g;
    out += std::to_string(groups[g]);
    while (g > 0) {
        --g;
        std::string s = std::to_string(groups[g]);
        // Zero-pad to DIGITS_PER_GROUP.
        if (s.size() < static_cast<std::size_t>(DIGITS_PER_GROUP)) {
            out.append(static_cast<std::size_t>(DIGITS_PER_GROUP) - s.size(), '0');
        }
        out += s;
    }
    return out;
}

// =====================================================================================
// hash
// =====================================================================================

std::size_t RellBigInt::hash() const noexcept {
    // FNV-1a over signum + magnitude limbs. Value-stable; not Java-compatible.
    uint64_t h = 1469598103934665603ULL;  // FNV offset basis (64-bit)
    auto mix = [&](uint32_t v) {
        for (int b = 0; b < 4; ++b) {
            h ^= static_cast<uint8_t>(v >> (b * 8));
            h *= 1099511628211ULL;  // FNV prime
        }
    };
    mix(static_cast<uint32_t>(signum_));
    for (uint32_t limb : mag_) {
        mix(limb);
    }
    return static_cast<std::size_t>(h);
}

}  // namespace rell::num
