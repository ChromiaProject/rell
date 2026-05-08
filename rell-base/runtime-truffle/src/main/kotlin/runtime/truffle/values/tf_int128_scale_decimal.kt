/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.values

import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.runtime.Rt_DecimalValue
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 128-bit-mantissa + int-scale decimal leaf for the Truffle JIT hot path. Sits between
 * [Tf_LongScaleDecimal] (mantissa fits Long) and the BigDecimal slow path: when long-mantissa
 * arithmetic overflows, widening to 128 bits keeps a workload like `power(2.5, 17)` or `exp(x)`'s
 * Taylor-series multiplications in primitives instead of paying `BigInteger`/`BigDecimal`
 * allocation per step.
 *
 * Storage convention: the 128-bit signed mantissa is `(hi << 64) | (lo as unsigned)`. [hi] is
 * a signed Long carrying the sign of the whole 128-bit value; [lo] is treated as unsigned (the
 * lower 64 bits of the magnitude in two's-complement).
 *
 * Cross-leaf consistency: [value] materialises a canonical-scale BigDecimal (padded to scale
 * `Lib_DecimalMath.DECIMAL_FRAC_DIGITS=20`) lazily, so [Rt_DecimalValue.equals] / [hashCode]
 * agree with `Rt_BigDecimalValue` for the same numeric value. Cross-leaf [valueEquals] against
 * [Tf_LongScaleDecimal] routes through `super.valueEquals` (BigDecimal compare) — the
 * long↔128 fast path is intentionally skipped for now.
 *
 * Division is deferred entirely to BigDecimal — 128-bit / 128-bit Knuth division is too much
 * code for this iteration. Add/Sub/Mul and compare are the operations that pay off here.
 */
data class Tf_Int128ScaleDecimal(val hi: Long, val lo: Long, val scale: Int): Rt_DecimalValue {
    override fun equals(other: Any?): Boolean = other === this || (other is Rt_DecimalValue && valueEquals(other))
    override fun hashCode(): Int = value.hashCode()

    // Plain field, no @Volatile: same rationale as Tf_LongScaleDecimal. The race is benign
    // (two threads compute an equal canonical BigDecimal); volatile would defeat field caching
    // across the loop body.
    private var cachedBd: BigDecimal? = null

    override val value: BigDecimal
        get() {
            val cached = cachedBd
            if (cached != null) return cached
            val materialised = if (isZero()) {
                BigDecimal.ZERO
            } else {
                val raw = BigDecimal(toBigInteger(), scale)
                if (scale <= Lib_DecimalMath.DECIMAL_FRAC_DIGITS) {
                    raw.setScale(Lib_DecimalMath.DECIMAL_FRAC_DIGITS)
                } else {
                    raw
                }
            }
            cachedBd = materialised
            return materialised
        }

    /**
     * Same-leaf same-scale equality skips BigDecimal materialisation. When scales disagree, or
     * the other side is a different leaf shape (e.g. [Tf_LongScaleDecimal] or
     * `Rt_BigDecimalValue`), fall through to the BigDecimal-backed comparison. The
     * long↔128 fast equality is intentionally skipped — it would require canonicalising both
     * mantissas to a common 128-bit representation, which is more work than the rare hit
     * justifies.
     */
    override fun valueEquals(other: Rt_DecimalValue): Boolean {
        if (other is Tf_Int128ScaleDecimal && scale == other.scale) {
            return hi == other.hi && lo == other.lo
        }
        return super.valueEquals(other)
    }

    /**
     * 128-bit absolute value: negate when the sign bit is set. The only corner case is
     * `(hi=Long.MIN_VALUE, lo=0)` — the most-negative 128-bit value, magnitude 2^127, which
     * negates to itself and stays negative. Defer to the BigDecimal fallback for that case.
     *
     * 128-bit two's-complement negation: result.lo = -lo (unsigned wrap); result.hi = -hi when
     * lo == 0, else ~hi (because the carry from negating a non-zero lo lands in hi).
     */
    override fun fastAbs(): Rt_DecimalValue {
        if (hi >= 0L) return this
        val resultLo = -lo
        val resultHi = if (lo == 0L) -hi else hi.inv()
        if (resultHi < 0L) return super.fastAbs()
        return Tf_Int128ScaleDecimal(resultHi, resultLo, scale)
    }

    /**
     * Floor (round toward negative infinity, scale 0). At 128 bits this is non-trivial — the
     * divisor `10^scale` and the result both span 128 bits in general. Defer to the BigDecimal
     * fallback. The `fastFloor` win comes from the binary ops; floor is a rare hot-path op.
     */
    override fun fastFloor(): Rt_DecimalValue = super.fastFloor()

    /**
     * Truncate-to-Long. The 128-bit mantissa rarely fits Long after truncation, so defer to the
     * BigDecimal fallback (which handles the range check and overflow error uniformly).
     */
    override fun fastToInteger(): Long = super.fastToInteger()

    /** True when the 128-bit mantissa is zero. */
    private fun isZero(): Boolean = hi == 0L && lo == 0L

    /**
     * Returns the mantissa as a signed Long when the 128-bit value fits Long, or `null`
     * otherwise. Used by the Mul fast path to pick a small-side multiplier without paying
     * BigInteger materialisation. A signed-Long-fitting 128-bit value has `hi` equal to the
     * sign-extension of `lo` (all-zero bits when lo is non-negative, all-one bits when lo is
     * negative).
     */
    fun asSignedLong(): Long? {
        if (hi == 0L && lo >= 0L) return lo
        if (hi == -1L && lo < 0L) return lo
        return null
    }

    /**
     * Materialise the 128-bit signed mantissa as a [BigInteger]. Constructs a 16-byte
     * big-endian two's-complement representation that [BigInteger]'s byte-array constructor
     * decodes directly.
     */
    private fun toBigInteger(): BigInteger {
        val bytes = ByteArray(16)
        bytes[0] = (hi ushr 56).toByte()
        bytes[1] = (hi ushr 48).toByte()
        bytes[2] = (hi ushr 40).toByte()
        bytes[3] = (hi ushr 32).toByte()
        bytes[4] = (hi ushr 24).toByte()
        bytes[5] = (hi ushr 16).toByte()
        bytes[6] = (hi ushr 8).toByte()
        bytes[7] = hi.toByte()
        bytes[8] = (lo ushr 56).toByte()
        bytes[9] = (lo ushr 48).toByte()
        bytes[10] = (lo ushr 40).toByte()
        bytes[11] = (lo ushr 32).toByte()
        bytes[12] = (lo ushr 24).toByte()
        bytes[13] = (lo ushr 16).toByte()
        bytes[14] = (lo ushr 8).toByte()
        bytes[15] = lo.toByte()
        return BigInteger(bytes)
    }

    companion object {
        val ZERO: Tf_Int128ScaleDecimal = Tf_Int128ScaleDecimal(0L, 0L, 0)

        /**
         * Try to encode `bd` as a 128-bit-mantissa value at its natural (stripped) scale.
         * Returns `null` when the value falls outside Rell's decimal range, or when the
         * unscaled mantissa doesn't fit 128 bits. Mirrors [Tf_LongScaleDecimal.tryFrom].
         */
        fun tryFrom(bd: BigDecimal): Tf_Int128ScaleDecimal? {
            if (Lib_DecimalMath.scale(bd) == null) return null
            val stripped = bd.stripTrailingZeros()
            val unscaled = stripped.unscaledValue()
            if (unscaled.bitLength() >= 128) return null
            val (hi, lo) = bigIntegerToInt128(unscaled)
            return Tf_Int128ScaleDecimal(hi, lo, stripped.scale())
        }

        /**
         * Fast re-encoding for the slow-path arithmetic tail. Precondition: `bd` is the output
         * of [Lib_DecimalMath.scale] — so it's either `BigDecimal.ZERO` or a canonical-scale
         * value. Strips trailing zeros via Long arithmetic on the bottom 64 bits when possible.
         *
         * **Gated to always return `null`** following the MNA `bench_perlin_noise` regression
         * profile (May 2026): the Int128 re-encoding tail consumed ~22% of that workload's
         * truffle profile (`div128By10` 14.2%, `tryFromCanonical` body 6.7%, supporting
         * helpers ~1%) without enough downstream Int128 fast-path hits to amortise. With the
         * gate, slow-path arithmetic results stay as plain `Rt_DecimalValue` (BigDecimal-backed),
         * matching pre-Item-3 behaviour for the re-encoding tail; the Tf_Int128ScaleDecimal
         * data class and its arithmetic helpers (`fromLongScale`, `fastInt128Scale`,
         * direct-construction Mul fast path in `tf_op_nodes.kt`) remain so a smarter
         * heuristic can re-enable re-encoding selectively. Profiled-confirmed safe across
         * `bench_locations` (uses no decimal arithmetic) and `bench_decimal_pow` (values
         * exceed Int128 range immediately, never benefited from re-encoding).
         */
        fun tryFromCanonical(@Suppress("UNUSED_PARAMETER") bd: BigDecimal): Tf_Int128ScaleDecimal? {
            return null
        }

        /**
         * Trivial widening of a long-scale leaf to 128 bits: sign-extend the 64-bit mantissa.
         */
        fun fromLongScale(l: Tf_LongScaleDecimal): Tf_Int128ScaleDecimal {
            val hi = l.mantissa shr 63 // arithmetic shift sign-extends
            return Tf_Int128ScaleDecimal(hi, l.mantissa, l.scale)
        }

        /**
         * Decode a 128-bit-or-smaller [BigInteger] as `(hi, lo)`. Precondition:
         * `unscaled.bitLength() < 128`. Uses sign-extending shifts.
         */
        private fun bigIntegerToInt128(unscaled: BigInteger): Pair<Long, Long> {
            val hi = unscaled.shiftRight(64).toLong() // sign-extends for negative values
            val lo = unscaled.toLong() // truncating low 64 bits
            return hi to lo
        }
    }
}

/* -------------------------------------------------------------------------------------------- *
 *  128-bit math helpers — internal arithmetic primitives used by the 128-bit fast path in
 *  Tf_BinaryNode.DecimalArith / DecimalCmp. All return null on overflow (where applicable) so
 *  the caller can defer to the BigDecimal slow path.
 * -------------------------------------------------------------------------------------------- */

/**
 * 128-bit two's-complement negation. Returns `null` for the most-negative value
 * `(Long.MIN_VALUE, 0)`, which has no representable signed-128-bit negation.
 */
internal fun neg128(hi: Long, lo: Long): LongArray? {
    if (hi == Long.MIN_VALUE && lo == 0L) return null
    val negLo = -lo
    val negHi = if (lo == 0L) -hi else hi.inv()
    return longArrayOf(negHi, negLo)
}

/**
 * 128-bit signed add with overflow detection. Returns `null` when the result doesn't fit a
 * signed 128-bit integer.
 *
 * Overflow rule (two's complement): when both inputs share a sign and the result has the
 * opposite sign, the add overflowed.
 */
internal fun add128(ahi: Long, alo: Long, bhi: Long, blo: Long): LongArray? {
    val rlo = alo + blo
    // Carry out of the low limb when unsigned-add overflows: rlo < alo (or equivalently < blo)
    // when treated as unsigned. Use Long.compareUnsigned.
    val carry = if (java.lang.Long.compareUnsigned(rlo, alo) < 0) 1L else 0L
    val rhi = ahi + bhi + carry
    // Signed overflow: ahi and bhi have the same sign, rhi has the opposite sign.
    if ((ahi xor rhi) < 0L && (bhi xor rhi) < 0L) return null
    return longArrayOf(rhi, rlo)
}

/**
 * 128-bit signed subtract with overflow detection. Returns `null` when the result doesn't fit a
 * signed 128-bit integer.
 *
 * Overflow rule: subtraction overflows when the subtrahend's sign differs from the minuend's
 * and the result's sign differs from the minuend's.
 */
internal fun sub128(ahi: Long, alo: Long, bhi: Long, blo: Long): LongArray? {
    val rlo = alo - blo
    val borrow = if (java.lang.Long.compareUnsigned(alo, blo) < 0) 1L else 0L
    val rhi = ahi - bhi - borrow
    // Signed overflow: a and b have different signs, and result's sign differs from a.
    if ((ahi xor bhi) < 0L && (ahi xor rhi) < 0L) return null
    return longArrayOf(rhi, rlo)
}

/**
 * Multiply a signed 128-bit value `(hi, lo)` by an unsigned-positive Long `m` (m >= 0). Returns
 * the resulting `(hi, lo)`, or `null` when the result doesn't fit a signed 128-bit integer.
 *
 * Strategy: split into sign × magnitude, multiply the magnitude by `m` using unsigned 128-bit
 * × 64-bit multiplication (via [Math.unsignedMultiplyHigh]), then re-sign. Reject when the
 * product magnitude needs more than 127 bits (would not round-trip through signed 128).
 *
 * Precondition: `m >= 0`. Callers that want to multiply by a negative value should negate the
 * 128-bit operand first (overflow-checked).
 */
internal fun mul128by64Unsigned(hi: Long, lo: Long, m: Long): LongArray? {
    if (m < 0L) return null // contract: callers must sign-normalise m to [0, Long.MAX]
    if (m == 0L) return longArrayOf(0L, 0L)
    if (m == 1L) return longArrayOf(hi, lo)
    if (hi == 0L && lo == 0L) return longArrayOf(0L, 0L)

    // Sign-magnitude decomposition of (hi, lo). For hi == Long.MIN_VALUE && lo == 0, abs would
    // wrap — that's the "most negative" 128-bit value, magnitude 2^127, which can't be
    // multiplied by any m >= 2 without overflow anyway. Reject up front.
    val negative: Boolean
    val mhi: Long
    val mlo: Long
    if (hi >= 0L) {
        negative = false
        mhi = hi
        mlo = lo
    } else {
        if (hi == Long.MIN_VALUE && lo == 0L) return null
        negative = true
        // -value via two's complement on 128 bits: lo' = -lo (unsigned wrap),
        // hi' = ~hi + (1 if lo == 0 else 0).
        mlo = -lo
        mhi = if (lo == 0L) -hi else hi.inv()
    }

    // Magnitude × m (unsigned). Result = mhi * m * 2^64 + mlo * m.
    //   lowProd = mlo * m (low 128 bits of unsigned product, split into hi/lo)
    val lowProdLo = mlo * m
    val lowProdHi = Math.unsignedMultiplyHigh(mlo, m)
    //   highProd = mhi * m (when mhi >= 0; mhi < 0 here means magnitude requires more than
    //   127 bits, but we forced sign-magnitude above so mhi >= 0). Use unsigned multiply to
    //   capture the full product in case mhi has the high bit set (still possible: magnitudes
    //   up to 2^127 - 1 fit, with mhi up to 2^63 - 1).
    val highProdLo = mhi * m
    val highProdHi = Math.unsignedMultiplyHigh(mhi, m)
    //   total = highProd << 64 + lowProd
    val resLo = lowProdLo
    val resHiUnsigned = lowProdHi + highProdLo
    // Carry from the (lowProdHi + highProdLo) addition into the top 64 bits.
    val midCarry = if (java.lang.Long.compareUnsigned(resHiUnsigned, lowProdHi) < 0) 1L else 0L
    val topUnsigned = highProdHi + midCarry
    // The top 64 bits of the full unsigned 192-bit product must be zero, AND the result's high
    // bit must be zero (so the 128-bit unsigned magnitude fits a signed 128-bit value when
    // re-signed).
    if (topUnsigned != 0L) return null
    if (resHiUnsigned < 0L) return null

    return if (negative) {
        // Re-apply sign via 128-bit two's-complement negation. The result magnitude here is
        // <= 2^127 - 1 (we just rejected the high-bit case), so negation always fits.
        val negLo = -resLo
        val negHi = if (resLo == 0L) -resHiUnsigned else resHiUnsigned.inv()
        longArrayOf(negHi, negLo)
    } else {
        longArrayOf(resHiUnsigned, resLo)
    }
}

/**
 * Multiply a signed 128-bit value `(hi, lo)` by `10^k`. Returns `null` on overflow or when
 * `k` exceeds the precomputed table. `k = 0` is a no-op fast path.
 */
internal fun mul128by10Pow(hi: Long, lo: Long, k: Int): LongArray? {
    if (k == 0) return longArrayOf(hi, lo)
    if (k < 0 || k > MAX_LONG_POW10) return null
    return mul128by64Unsigned(hi, lo, POW10_LONG[k])
}

/**
 * Compare two signed 128-bit values. Returns negative / zero / positive following the
 * [Comparable.compareTo] contract.
 *
 * Signed compare on `hi` (which carries the sign), then unsigned compare on `lo`.
 */
internal fun cmp128(ahi: Long, alo: Long, bhi: Long, blo: Long): Int {
    val hiCmp = ahi.compareTo(bhi)
    if (hiCmp != 0) return hiCmp
    return java.lang.Long.compareUnsigned(alo, blo)
}

/** Largest `k` for which `10^k` fits in a [Long]. */
private const val MAX_LONG_POW10: Int = 18

/** `POW10_LONG[i] = 10^i` for `i in 0..MAX_LONG_POW10`. */
private val POW10_LONG: LongArray = LongArray(MAX_LONG_POW10 + 1).also {
    var p = 1L
    for (i in 0..MAX_LONG_POW10) {
        it[i] = p
        if (i < MAX_LONG_POW10) p *= 10L
    }
}

/**
 * 128-bit unsigned divide-by-10. Returns `(quotientHi, quotientLo, remainder)`. Used by
 * [Tf_Int128ScaleDecimal.tryFromCanonical] for trailing-zero stripping. Treats `(hi, lo)` as
 * an unsigned 128-bit integer; callers must handle sign separately if the input is negative.
 *
 * Algorithm: long division base-2^32 to keep each step in pure Long arithmetic. Splits the
 * 128-bit value into four 32-bit limbs and divides each by 10, propagating the remainder
 * downward.
 */
internal fun div128By10(hi: Long, lo: Long): Triple<Long, Long, Int> {
    // Sign-magnitude: callers may pass negative values; for this helper to make sense as
    // "divide the unscaled mantissa by 10 to strip a trailing zero" we operate on the
    // magnitude and re-apply the sign. (For trailing-zero stripping the sign is preserved,
    // and a value ending in zero is unaffected by the sign-flip mod 10.)
    val negative = hi < 0L
    val mhi: Long
    val mlo: Long
    if (negative) {
        mlo = -lo
        mhi = if (lo == 0L) -hi else hi.inv()
    } else {
        mhi = hi
        mlo = lo
    }

    // Split into four 32-bit unsigned limbs (most-significant first).
    val l3 = (mhi ushr 32) and 0xFFFF_FFFFL
    val l2 = mhi and 0xFFFF_FFFFL
    val l1 = (mlo ushr 32) and 0xFFFF_FFFFL
    val l0 = mlo and 0xFFFF_FFFFL

    var rem = 0L
    val q3: Long
    val q2: Long
    val q1: Long
    val q0: Long
    val cur3 = (rem shl 32) or l3
    q3 = cur3 / 10L
    rem = cur3 % 10L
    val cur2 = (rem shl 32) or l2
    q2 = cur2 / 10L
    rem = cur2 % 10L
    val cur1 = (rem shl 32) or l1
    q1 = cur1 / 10L
    rem = cur1 % 10L
    val cur0 = (rem shl 32) or l0
    q0 = cur0 / 10L
    rem = cur0 % 10L

    val qHiUnsigned = (q3 shl 32) or q2
    val qLoUnsigned = (q1 shl 32) or q0

    return if (negative) {
        // Re-apply sign to the quotient. Remainder for negative magnitudes always reads back
        // as the corresponding unsigned magnitude remainder; flip its sign for callers that
        // care (none of the current ones do — div128By10 is only used by tryFromCanonical
        // which checks remainder == 0).
        val negLo = -qLoUnsigned
        val negHi = if (qLoUnsigned == 0L) -qHiUnsigned else qHiUnsigned.inv()
        Triple(negHi, negLo, -rem.toInt())
    } else {
        Triple(qHiUnsigned, qLoUnsigned, rem.toInt())
    }
}
