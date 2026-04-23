// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_bigint_muldiv.cpp — RellBigInt schoolbook multiply and Knuth Algorithm D
// division/remainder, translated faithfully from OpenJDK 21 (jdk-21+35):
//   * multiply  <- BigInteger.multiplyToLen / implMultiplyToLen
//   * divMag    <- MutableBigInteger.divideMagnitude (+ divideOneWord, mulsub,
//                  divadd, divWord, copyAndShift, unsignedLongCompare)
//   * divide / remainder / divideAndRemainder <- BigInteger.divideKnuth /
//                  divideAndRemainderKnuth sign handling.
//
// Magnitudes here are BIG-ENDIAN std::vector<uint32_t> (index 0 = most significant),
// matching rell_bigint.h. OpenJDK's MutableBigInteger uses an (int[] value, int offset,
// int intLen) window; we operate on dense big-endian vectors instead and reproduce the
// same arithmetic. Each limb is treated as an unsigned 32-bit word, promoted to uint64
// for arithmetic — this is OpenJDK's `& LONG_MASK` (0xffffffffL) idiom made explicit.
//
// CORRECTNESS over speed: schoolbook O(n*m) multiply and O(n^2) Knuth-D division are
// bit-exact and sufficient for the Rell consensus range (~10^131072, ~13600 limbs).
// // TODO(perf): Karatsuba/Toom-Cook multiply, Burnikel-Ziegler division — all PERF-only.

#include "rell_bigint.h"

#include <algorithm>

namespace rell::num {

namespace {

constexpr uint64_t LONG_MASK = 0xffffffffULL;  // OpenJDK BigInteger.LONG_MASK

// Integer.numberOfLeadingZeros for a 32-bit word (returns 32 for 0). Used to compute the
// Knuth-D normalization shift so the divisor's top limb has its MSB set.
inline int numberOfLeadingZeros32(uint32_t i) {
    if (i == 0) return 32;
    int n = 0;
    if (i <= 0x0000FFFFu) { n += 16; i <<= 16; }
    if (i <= 0x00FFFFFFu) { n += 8;  i <<= 8;  }
    if (i <= 0x0FFFFFFFu) { n += 4;  i <<= 4;  }
    if (i <= 0x3FFFFFFFu) { n += 2;  i <<= 2;  }
    if (i <= 0x7FFFFFFFu) { n += 1; }
    return n;
}

// Strip leading zero limbs from a big-endian magnitude (empty vector == zero).
inline void stripLeadingZeros(std::vector<uint32_t> &m) {
    std::size_t i = 0;
    while (i < m.size() && m[i] == 0) ++i;
    if (i > 0) m.erase(m.begin(), m.begin() + static_cast<std::ptrdiff_t>(i));
}

// OpenJDK MutableBigInteger.unsignedLongCompare: true iff (one > two) unsigned.
inline bool unsignedLongCompare(uint64_t one, uint64_t two) { return one > two; }

// NOTE: OpenJDK's MutableBigInteger.divWord (64/32 unsigned divide returning (r<<32)|q)
// exists only to emulate unsigned division on Java's signed `long`, taken when the top
// bit of the 64-bit dividend estimate is set. In C++ uint64_t division is already
// unsigned, so both Java branches compute the identical quotient/remainder and the plain
// `dividendEstimate / divisorLong` covers every case — divWord is therefore not needed.

// OpenJDK MutableBigInteger.mulsub: q[offset..offset+len] -= x * a[0..len-1], returning
// the high carry/borrow word. q is the running remainder window, a is the (shifted)
// divisor, x is qhat. Operates in place on q, indexing the big-endian window q[start..].
inline uint32_t mulsub(std::vector<uint32_t> &q, const std::vector<uint32_t> &a,
                       uint32_t x, int len, int offset) {
    uint64_t xLong = x & LONG_MASK;
    uint64_t carry = 0;
    offset += len;  // point at the least-significant target word + 1
    for (int j = len - 1; j >= 0; j--) {
        uint64_t product = (a[static_cast<std::size_t>(j)] & LONG_MASK) * xLong + carry;
        uint64_t difference = static_cast<uint64_t>(q[static_cast<std::size_t>(offset)]) - product;
        q[static_cast<std::size_t>(offset--)] = static_cast<uint32_t>(difference);
        // carry = (product>>>32) + (low(difference) > ~low(product) ? 1 : 0)
        carry = (product >> 32) +
                (((difference & LONG_MASK) >
                  ((~static_cast<uint32_t>(product)) & LONG_MASK))
                     ? 1
                     : 0);
    }
    return static_cast<uint32_t>(carry);
}

// OpenJDK MutableBigInteger.divadd: result[offset..] += a[0..], returning the carry out.
// Used to add the divisor back when qhat was over-estimated by one.
inline uint32_t divadd(const std::vector<uint32_t> &a, std::vector<uint32_t> &result,
                       int offset) {
    uint64_t carry = 0;
    for (int j = static_cast<int>(a.size()) - 1; j >= 0; j--) {
        uint64_t sum = (a[static_cast<std::size_t>(j)] & LONG_MASK) +
                       (result[static_cast<std::size_t>(j + offset)] & LONG_MASK) + carry;
        result[static_cast<std::size_t>(j + offset)] = static_cast<uint32_t>(sum);
        carry = sum >> 32;
    }
    return static_cast<uint32_t>(carry);
}

// OpenJDK MutableBigInteger.copyAndShift: dst[dstFrom..] = (src[srcFrom..] << shift),
// 0 < shift < 32, over srcLen words, with cross-word bit carry. Big-endian.
inline void copyAndShift(const std::vector<uint32_t> &src, int srcFrom, int srcLen,
                         std::vector<uint32_t> &dst, int dstFrom, int shift) {
    int n2 = 32 - shift;
    uint32_t c = src[static_cast<std::size_t>(srcFrom)];
    for (int i = 0; i < srcLen - 1; i++) {
        uint32_t b = c;
        c = src[static_cast<std::size_t>(++srcFrom)];
        dst[static_cast<std::size_t>(dstFrom + i)] = (b << shift) | (c >> n2);
    }
    dst[static_cast<std::size_t>(dstFrom + srcLen - 1)] = c << shift;
}

// Right-shift a big-endian magnitude in place by 0 <= shift < 32 (used to undo the
// normalization shift on the remainder). Mirrors MutableBigInteger.rightShift for the
// sub-word case; the normalization shift is always < 32 since it is clz of one word.
inline void rightShiftInPlace(std::vector<uint32_t> &m, int shift) {
    if (shift == 0 || m.empty()) return;
    int n2 = 32 - shift;
    uint32_t carry = 0;  // bits shifted out of the more-significant word
    for (std::size_t i = 0; i < m.size(); i++) {
        uint32_t cur = m[i];
        m[i] = (carry << n2) | (cur >> shift);
        carry = cur;
    }
    stripLeadingZeros(m);
}

// ------------------------------------------------------------------------------------
// divideOneWord — OpenJDK MutableBigInteger.divideOneWord. Divides the big-endian
// magnitude `value` by a single 32-bit divisor, returning {quotientMag, remainderWord}.
// Standard 64/32 long-division word loop.
// ------------------------------------------------------------------------------------
std::pair<std::vector<uint32_t>, uint32_t> divideOneWord(const std::vector<uint32_t> &value,
                                                         uint32_t divisor) {
    uint64_t divisorLong = divisor & LONG_MASK;
    const int intLen = static_cast<int>(value.size());

    if (intLen == 1) {
        uint64_t dividendValue = value[0] & LONG_MASK;
        uint32_t q = static_cast<uint32_t>(dividendValue / divisorLong);
        uint32_t r = static_cast<uint32_t>(dividendValue - static_cast<uint64_t>(q) * divisorLong);
        std::vector<uint32_t> quot;
        if (q != 0) quot.push_back(q);
        return {std::move(quot), r};
    }

    std::vector<uint32_t> quotient(static_cast<std::size_t>(intLen), 0);

    uint32_t rem = value[0];
    uint64_t remLong = rem & LONG_MASK;
    if (remLong < divisorLong) {
        quotient[0] = 0;
    } else {
        quotient[0] = static_cast<uint32_t>(remLong / divisorLong);
        rem = static_cast<uint32_t>(remLong - (static_cast<uint64_t>(quotient[0]) * divisorLong));
        remLong = rem & LONG_MASK;
    }

    int xlen = intLen;
    while (--xlen > 0) {
        uint64_t dividendEstimate =
            (remLong << 32) | (value[static_cast<std::size_t>(intLen - xlen)] & LONG_MASK);
        uint32_t q;
        // In Java the >= 0 branch is the "fits a positive signed long" fast path; with
        // genuinely-unsigned uint64 arithmetic both branches compute the same quotient,
        // so a plain unsigned divide is correct and equivalent.
        q = static_cast<uint32_t>(dividendEstimate / divisorLong);
        rem = static_cast<uint32_t>(dividendEstimate - static_cast<uint64_t>(q) * divisorLong);
        quotient[static_cast<std::size_t>(intLen - xlen)] = q;
        remLong = rem & LONG_MASK;
    }

    stripLeadingZeros(quotient);
    return {std::move(quotient), rem};
}

// ------------------------------------------------------------------------------------
// divideMagnitude — OpenJDK MutableBigInteger.divideMagnitude, Knuth Algorithm D.
// PRECONDITION: |dividend| > |divisor| and divisor.size() >= 2 (the trivial cases —
// zero/less/equal divisor and single-word divisor — are handled by the caller divMag).
// Returns {quotientMag, remainderMag}, both big-endian, both already-stripped.
//
// Steps (TAOCP Vol.2 §4.3.1 Algorithm D, as coded by OpenJDK):
//   D1 normalize: left-shift both operands by clz(divisor[0]) so divisor's top limb has
//      its MSB set; the dividend gains a leading 0 word (rem has an extra high word).
//   D2..D7 main loop over quotient digits j:
//      - estimate qhat from the top two remainder words over the top divisor word dh,
//        special-casing nh==dh (qhat = 0xFFFFFFFF);
//      - refine qhat using the second divisor word dl (the (qhat,rhat) correction);
//      - multiply-subtract qhat*divisor out of the remainder window (mulsub);
//      - if it borrowed (qhat was one too big) add the divisor back (divadd) and qhat--.
//   D8 un-normalize: right-shift the remainder back by the same shift.
// ------------------------------------------------------------------------------------
std::pair<std::vector<uint32_t>, std::vector<uint32_t>> divideMagnitude(
    const std::vector<uint32_t> &dividend, const std::vector<uint32_t> &divisorIn) {
    const int dlen = static_cast<int>(divisorIn.size());
    const int intLen = static_cast<int>(dividend.size());

    int shift = numberOfLeadingZeros32(divisorIn[0]);

    std::vector<uint32_t> divisor(static_cast<std::size_t>(dlen));
    // rem holds the running remainder/dividend with one extra leading word (and, in the
    // shift-overflow case, a second). Big-endian; index 0 is the most-significant word.
    std::vector<uint32_t> rem;

    if (shift > 0) {
        copyAndShift(divisorIn, 0, dlen, divisor, 0, shift);
        if (numberOfLeadingZeros32(dividend[0]) >= shift) {
            // The shifted dividend still fits without an extra high word beyond the
            // single leading zero word we prepend below.
            rem.assign(static_cast<std::size_t>(intLen + 1), 0);
            // rem[1..intLen] = dividend << shift
            copyAndShift(dividend, 0, intLen, rem, 1, shift);
        } else {
            // Shifting the dividend produces an extra high word; build it manually.
            rem.assign(static_cast<std::size_t>(intLen + 2), 0);
            int rFrom = 0;
            uint32_t c = 0;
            int n2 = 32 - shift;
            for (int i = 1; i < intLen + 1; i++, rFrom++) {
                uint32_t b = c;
                c = dividend[static_cast<std::size_t>(rFrom)];
                rem[static_cast<std::size_t>(i)] = (b << shift) | (c >> n2);
            }
            rem[static_cast<std::size_t>(intLen + 1)] = c << shift;
        }
    } else {
        divisor.assign(divisorIn.begin(), divisorIn.end());
        rem.assign(static_cast<std::size_t>(intLen + 1), 0);
        // rem[1..intLen] = dividend; rem[0] = 0
        for (int i = 0; i < intLen; i++) {
            rem[static_cast<std::size_t>(i + 1)] = dividend[static_cast<std::size_t>(i)];
        }
    }

    // In OpenJDK, rem.intLen is set above and then incremented; nlen tracks the length of
    // the remainder window. With our dense representation, rem already includes the
    // leading word(s), so nlen == rem.size() and the per-digit window indices are direct.
    const int nlen = static_cast<int>(rem.size());
    const int limit = nlen - dlen;  // OpenJDK: nlen - dlen + 1 with the extra ++intLen.

    std::vector<uint32_t> q(static_cast<std::size_t>(limit), 0);

    uint32_t dh = divisor[0];
    uint64_t dhLong = dh & LONG_MASK;
    uint32_t dl = divisor[1];

    // Process the first (limit - 1) quotient digits, then the last digit separately
    // (OpenJDK splits the last iteration to allow the no-remainder mulsubBorrow path; we
    // always need the remainder, so the last digit uses the same mulsub/divadd as the
    // body — the split is preserved only for structural fidelity / index clarity).
    for (int j = 0; j < limit - 1; j++) {
        uint32_t qhat = 0;
        uint32_t qrem = 0;
        bool skipCorrection = false;
        uint32_t nh = rem[static_cast<std::size_t>(j)];
        uint32_t nm = rem[static_cast<std::size_t>(j + 1)];

        if (nh == dh) {
            qhat = ~0u;
            qrem = nh + nm;
            // skipCorrection iff (qrem) wrapped, i.e. unsigned qrem < nh.
            skipCorrection = (qrem + 0x80000000u) < (nh + 0x80000000u);
        } else {
            uint64_t nChunk = ((static_cast<uint64_t>(nh)) << 32) | (nm & LONG_MASK);
            qhat = static_cast<uint32_t>(nChunk / dhLong);
            qrem = static_cast<uint32_t>(nChunk - (static_cast<uint64_t>(qhat) * dhLong));
        }

        if (qhat == 0) continue;

        if (!skipCorrection) {  // refine qhat using the second divisor word dl
            uint64_t nl = rem[static_cast<std::size_t>(j + 2)] & LONG_MASK;
            uint64_t rs = ((static_cast<uint64_t>(qrem) & LONG_MASK) << 32) | nl;
            uint64_t estProduct = (dl & LONG_MASK) * (qhat & LONG_MASK);
            if (unsignedLongCompare(estProduct, rs)) {
                qhat--;
                qrem = static_cast<uint32_t>((qrem & LONG_MASK) + dhLong);
                if ((qrem & LONG_MASK) >= dhLong) {
                    estProduct -= (dl & LONG_MASK);
                    rs = ((static_cast<uint64_t>(qrem) & LONG_MASK) << 32) | nl;
                    if (unsignedLongCompare(estProduct, rs)) qhat--;
                }
            }
        }

        // D4/D5/D6: multiply-subtract qhat*divisor from the remainder window; add back on
        // over-estimate.
        rem[static_cast<std::size_t>(j)] = 0;
        uint32_t borrow = mulsub(rem, divisor, qhat, dlen, j);
        // borrow + 0x80000000 > nh2  <=>  unsigned borrow > unsigned nh.
        if ((borrow + 0x80000000u) > (nh + 0x80000000u)) {
            divadd(divisor, rem, j + 1);
            qhat--;
        }
        q[static_cast<std::size_t>(j)] = qhat;
    }

    // Last quotient digit (index limit - 1).
    if (limit - 1 >= 0) {
        uint32_t qhat = 0;
        uint32_t qrem = 0;
        bool skipCorrection = false;
        uint32_t nh = rem[static_cast<std::size_t>(limit - 1)];
        uint32_t nm = rem[static_cast<std::size_t>(limit)];

        if (nh == dh) {
            qhat = ~0u;
            qrem = nh + nm;
            skipCorrection = (qrem + 0x80000000u) < (nh + 0x80000000u);
        } else {
            uint64_t nChunk = ((static_cast<uint64_t>(nh)) << 32) | (nm & LONG_MASK);
            qhat = static_cast<uint32_t>(nChunk / dhLong);
            qrem = static_cast<uint32_t>(nChunk - (static_cast<uint64_t>(qhat) * dhLong));
        }

        if (qhat != 0) {
            if (!skipCorrection) {
                uint64_t nl = rem[static_cast<std::size_t>(limit + 1)] & LONG_MASK;
                uint64_t rs = ((static_cast<uint64_t>(qrem) & LONG_MASK) << 32) | nl;
                uint64_t estProduct = (dl & LONG_MASK) * (qhat & LONG_MASK);
                if (unsignedLongCompare(estProduct, rs)) {
                    qhat--;
                    qrem = static_cast<uint32_t>((qrem & LONG_MASK) + dhLong);
                    if ((qrem & LONG_MASK) >= dhLong) {
                        estProduct -= (dl & LONG_MASK);
                        rs = ((static_cast<uint64_t>(qrem) & LONG_MASK) << 32) | nl;
                        if (unsignedLongCompare(estProduct, rs)) qhat--;
                    }
                }
            }
            rem[static_cast<std::size_t>(limit - 1)] = 0;
            uint32_t borrow = mulsub(rem, divisor, qhat, dlen, limit - 1);
            if ((borrow + 0x80000000u) > (nh + 0x80000000u)) {
                divadd(divisor, rem, limit - 1 + 1);
                qhat--;
            }
            q[static_cast<std::size_t>(limit - 1)] = qhat;
        }
    }

    // D8: the remainder occupies the low `dlen` words of rem; un-normalize it.
    std::vector<uint32_t> remainderMag(rem.end() - dlen, rem.end());
    if (shift > 0) rightShiftInPlace(remainderMag, shift);
    stripLeadingZeros(remainderMag);

    stripLeadingZeros(q);
    return {std::move(q), std::move(remainderMag)};
}

}  // namespace

// =====================================================================================
// mulMag — BigInteger.multiplyToLen / implMultiplyToLen (schoolbook, big-endian).
// z has length xlen+ylen; the most-significant word lands at z[0]. // TODO(perf):
// Karatsuba >= 80 limbs, Toom-Cook >= 240.
// =====================================================================================
std::vector<uint32_t> RellBigInt::mulMag(const std::vector<uint32_t> &x,
                                         const std::vector<uint32_t> &y) {
    const int xlen = static_cast<int>(x.size());
    const int ylen = static_cast<int>(y.size());
    if (xlen == 0 || ylen == 0) return {};

    const int xstart = xlen - 1;
    const int ystart = ylen - 1;
    std::vector<uint32_t> z(static_cast<std::size_t>(xlen + ylen), 0);

    // First pass: multiply x's least-significant word through y.
    uint64_t carry = 0;
    for (int j = ystart, k = ystart + 1 + xstart; j >= 0; j--, k--) {
        uint64_t product = (y[static_cast<std::size_t>(j)] & LONG_MASK) *
                               (x[static_cast<std::size_t>(xstart)] & LONG_MASK) +
                           carry;
        z[static_cast<std::size_t>(k)] = static_cast<uint32_t>(product);
        carry = product >> 32;
    }
    z[static_cast<std::size_t>(xstart)] = static_cast<uint32_t>(carry);

    // Remaining passes: accumulate.
    for (int i = xstart - 1; i >= 0; i--) {
        carry = 0;
        for (int j = ystart, k = ystart + 1 + i; j >= 0; j--, k--) {
            uint64_t product = (y[static_cast<std::size_t>(j)] & LONG_MASK) *
                                   (x[static_cast<std::size_t>(i)] & LONG_MASK) +
                               (z[static_cast<std::size_t>(k)] & LONG_MASK) + carry;
            z[static_cast<std::size_t>(k)] = static_cast<uint32_t>(product);
            carry = product >> 32;
        }
        z[static_cast<std::size_t>(i)] = static_cast<uint32_t>(carry);
    }

    stripLeadingZeros(z);
    return z;
}

// =====================================================================================
// multiply — BigInteger.multiply: magnitude via mulMag, sign = signum_a * signum_b;
// zero if either operand is zero.
// =====================================================================================
RellBigInt RellBigInt::multiply(const RellBigInt &val) const {
    if (signum_ == 0 || val.signum_ == 0) return RellBigInt();
    std::vector<uint32_t> resultMag = mulMag(mag_, val.mag_);
    int32_t rsign = static_cast<int32_t>(signum_ * val.signum_);
    return RellBigInt(std::move(resultMag), rsign);
}

// =====================================================================================
// divMag — magnitude-level Knuth Algorithm D dispatcher, mirroring
// MutableBigInteger.divideKnuth's trivial-case handling. Returns {quotientMag,
// remainderMag}. Caller (divide/remainder/divideAndRemainder) applies signs.
//
//   divisor empty   -> handled by caller (throws / 0); not reached here.
//   dividend empty   -> {0, 0}.
//   |dividend| < |divisor| -> {0, dividend}.
//   |dividend| == |divisor| -> {1, 0}.
//   single-word divisor -> divideOneWord.
//   else                -> divideMagnitude (full Knuth-D).
// =====================================================================================
std::pair<std::vector<uint32_t>, std::vector<uint32_t>> RellBigInt::divMag(
    const std::vector<uint32_t> &dividend, const std::vector<uint32_t> &divisor) {
    if (dividend.empty()) return {{}, {}};

    int cmp = cmpMag(dividend, divisor);
    if (cmp < 0) return {{}, dividend};            // quotient 0, remainder = dividend
    if (cmp == 0) return {{1u}, {}};               // quotient 1, remainder 0

    if (divisor.size() == 1) {
        auto [quot, r] = divideOneWord(dividend, divisor[0]);
        std::vector<uint32_t> remMag;
        if (r != 0) remMag.push_back(r);
        return {std::move(quot), std::move(remMag)};
    }

    return divideMagnitude(dividend, divisor);
}

// =====================================================================================
// divide / remainder / divideAndRemainder — BigInteger.divideKnuth /
// divideAndRemainderKnuth sign handling. divide truncates toward zero (quotient sign =
// signum_a * signum_b); remainder takes the dividend's sign; a == q*b + r.
// =====================================================================================
RellBigInt RellBigInt::divide(const RellBigInt &val) const {
    if (val.signum_ == 0) {
        throw RellArithError("", "/ by zero");
    }
    if (signum_ == 0) return RellBigInt();  // 0 / x == 0

    auto [quotMag, remMag] = divMag(mag_, val.mag_);
    if (quotMag.empty()) return RellBigInt();
    int32_t qsign = static_cast<int32_t>(signum_ * val.signum_);
    return RellBigInt(std::move(quotMag), qsign);
}

RellBigInt RellBigInt::remainder(const RellBigInt &val) const {
    if (val.signum_ == 0) {
        throw RellArithError("", "/ by zero");
    }
    if (signum_ == 0) return RellBigInt();  // 0 % x == 0

    auto [quotMag, remMag] = divMag(mag_, val.mag_);
    (void)quotMag;
    if (remMag.empty()) return RellBigInt();
    return RellBigInt(std::move(remMag), signum_);  // remainder takes dividend's sign
}

std::pair<RellBigInt, RellBigInt> RellBigInt::divideAndRemainder(const RellBigInt &val) const {
    if (val.signum_ == 0) {
        throw RellArithError("", "/ by zero");
    }
    if (signum_ == 0) return {RellBigInt(), RellBigInt()};

    auto [quotMag, remMag] = divMag(mag_, val.mag_);

    RellBigInt q;
    if (!quotMag.empty()) {
        int32_t qsign = static_cast<int32_t>(signum_ == val.signum_ ? 1 : -1);
        q = RellBigInt(std::move(quotMag), qsign);
    }

    RellBigInt r;
    if (!remMag.empty()) {
        r = RellBigInt(std::move(remMag), signum_);
    }

    return {std::move(q), std::move(r)};
}

}  // namespace rell::num
