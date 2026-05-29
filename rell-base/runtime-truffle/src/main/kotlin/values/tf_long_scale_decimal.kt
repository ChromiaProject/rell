/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.values

import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.runtime.Rt_DecimalValue
import net.postchain.rell.base.runtime.truffle.values.Tf_LongScaleDecimal.Companion.MAX_LONG_SCALE
import net.postchain.rell.base.runtime.truffle.values.Tf_LongScaleDecimal.Companion.POW10_TABLE
import net.postchain.rell.base.runtime.truffle.values.Tf_LongScaleDecimal.Companion.tryFrom
import net.postchain.rell.base.runtime.truffle.values.Tf_LongScaleDecimal.Companion.tryFromCanonical
import java.math.BigDecimal

/**
 * Long-mantissa + int-scale decimal leaf for the Truffle JIT hot path. Avoids BigInteger /
 * BigDecimal allocation as long as the value's *natural* unscaled representation fits Long.
 *
 * Storage convention: [mantissa] / 10^[scale] is the represented value, using the BigDecimal's
 * own natural scale (not the canonical Rell scale of `Lib_DecimalMath.DECIMAL_FRAC_DIGITS=20`).
 * For example, `1.5` is stored as `(15, 1)` and `0.21132487` as `(21132487, 8)`. This avoids the
 * [Long] overflow that would occur if everything were padded to scale=20 — at which point any
 * value ≥ ~0.0922 needs more than 64 bits of mantissa and the fast path could never engage.
 *
 * Cross-leaf consistency: [value] materialises a canonical-scale BigDecimal (padded to scale 20)
 * lazily and caches the result, so [Rt_DecimalValue.equals] / [hashCode] (which use BigDecimal's
 * scale-sensitive `equals`) agree with `Rt_BigDecimalValue` for the same numeric value. The
 * cached BigDecimal is paid for only when a value escapes to a path that reads `.value`
 * (GTV/SQL/native conversions, error messages, slow-path arithmetic) — pure long-scale loops
 * never trigger the materialisation.
 *
 * Bounds: [tryFrom] verifies the value falls inside Rell's decimal range
 * (`Lib_DecimalMath.scale` returns non-null) before encoding. On `Long` overflow during arithmetic
 * the caller must use [Rt_DecimalValue.get].
 */
data class Tf_LongScaleDecimal(val mantissa: Long, val scale: Int): Rt_DecimalValue {
    override fun equals(other: Any?): Boolean = other === this || (other is Rt_DecimalValue && valueEquals(other))
    override fun hashCode(): Int = value.hashCode()

    // Plain field, no @Volatile: the race is benign — two threads racing on first read both
    // compute an equal BigDecimal (`Lib_DecimalMath.scale` is deterministic and idempotent),
    // and no consumer relies on cachedBd's publication ordering. Volatile defeats JIT field
    // caching across the loop body, which dominates Simplex-noise compare-heavy hot loops.
    private var cachedBd: BigDecimal? = null

    override val value: BigDecimal
        get() {
            val cached = cachedBd
            if (cached != null) return cached
            // Mirror Lib_DecimalMath.scale exactly: zero collapses to BigDecimal.ZERO
            // (scale 0) so cross-leaf equals against Rt_DecimalValue.ZERO matches; non-zero
            // pads to canonical DECIMAL_FRAC_DIGITS so it matches every other Rt_BigDecimalValue.
            val materialised = if (mantissa == 0L) {
                BigDecimal.ZERO
            } else {
                val raw = BigDecimal.valueOf(mantissa, scale)
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
     * Same-leaf, same-scale equality skips the BigDecimal materialisation. After
     * [tryFrom]'s `stripTrailingZeros`, semantically equal long-scale values land in canonical
     * form, so equal `(mantissa, scale)` is sufficient for equality. When scales disagree, fall
     * through to the BigDecimal-backed comparison (rare in compare-heavy hot loops where both
     * sides come from the same arithmetic chain and naturally share scale).
     */
    override fun valueEquals(other: Rt_DecimalValue): Boolean {
        if (other is Tf_LongScaleDecimal && scale == other.scale) {
            return mantissa == other.mantissa
        }
        return super.valueEquals(other)
    }

    /**
     * Long-mantissa abs: just `Math.abs(mantissa)`. The only case [Math.abs] mishandles is
     * `Long.MIN_VALUE`, which negates to itself and stays negative. Tf_LongScaleDecimal
     * mantissas are bounded by [Lib_DecimalMath.scale] (= less than 10^DECIMAL_INT_DIGITS *
     * 10^DECIMAL_FRAC_DIGITS); `Long.MIN_VALUE`'s magnitude (~9.22e18) is below `10^19` so
     * theoretically reachable, but stripTrailingZeros in [tryFrom] keeps mantissas as small as
     * possible. Falls back to BigDecimal on the corner case for safety.
     */
    override fun fastAbs(): Rt_DecimalValue {
        val m = mantissa
        if (m == Long.MIN_VALUE) return super.fastAbs()
        if (m >= 0) return this
        return Tf_LongScaleDecimal(-m, scale)
    }

    /**
     * Long-mantissa floor (round toward negative infinity, scale 0). For non-negative values
     * truncation matches floor; for negative values with non-zero fractional part we adjust
     * the truncated quotient by `-1`. Falls back to BigDecimal when scale exceeds the
     * [POW10_TABLE] bounds (>18).
     */
    override fun fastFloor(): Rt_DecimalValue {
        if (scale == 0) return this
        if (scale > MAX_LONG_SCALE) return super.fastFloor()
        val divisor = POW10_TABLE[scale]
        val q = mantissa / divisor
        val r = mantissa % divisor
        val floored = if (r < 0) q - 1 else q
        return Tf_LongScaleDecimal(floored, 0)
    }

    /**
     * Long-mantissa truncate-to-Long. Mantissa is a [Long] and the divisor `10^scale` is ≥ 1,
     * so the result trivially fits Long — no overflow check required. Falls back to BigDecimal
     * only when scale exceeds [POW10_TABLE] bounds.
     */
    override fun fastToInteger(): Long {
        if (scale == 0) return mantissa
        if (scale > MAX_LONG_SCALE) return super.fastToInteger()
        return mantissa / POW10_TABLE[scale]
    }

    companion object {
        /** Largest scale for which `10^scale` still fits in a [Long]. */
        private const val MAX_LONG_SCALE: Int = 18

        /** `POW10_TABLE_i = 10^i` for i in 0..[MAX_LONG_SCALE]. */
        private val POW10_TABLE: LongArray = LongArray(MAX_LONG_SCALE + 1).also {
            var p = 1L
            for (i in 0..MAX_LONG_SCALE) {
                it[i] = p
                if (i < MAX_LONG_SCALE) p *= 10L
            }
        }

        val ZERO: Tf_LongScaleDecimal = Tf_LongScaleDecimal(0L, 0)

        /**
         * Try to encode `bd` as a long-mantissa value at its natural (stripped) scale. Returns
         * `null` if the value falls outside Rell's decimal range (per [Lib_DecimalMath.scale]),
         * or if the unscaled mantissa doesn't fit a [Long]. Callers fall back to
         * [Rt_DecimalValue.get] for the BigDecimal-backed path.
         *
         * Used for non-canonical inputs (e.g. translator-time constant folding). Slow-path
         * arithmetic results MUST use [tryFromCanonical] instead — that path already knows the
         * input is in range and at scale `DECIMAL_FRAC_DIGITS`, so it can skip the
         * [Lib_DecimalMath.scale] re-check and the [BigDecimal.stripTrailingZeros] (which
         * dominates the Simplex profile at ~6% in `createAndStripZerosToMatchScale`).
         */
        fun tryFrom(bd: BigDecimal): Tf_LongScaleDecimal? {
            // Bounds check (canonical scale must produce an in-range value).
            if (Lib_DecimalMath.scale(bd) == null) return null
            // Strip trailing zeros so that constants like `1.0` (scale 1) and `1.00` (scale 2)
            // both encode as `(1, 0)` — keeps the unscaled magnitude as small as possible.
            val stripped = bd.stripTrailingZeros()
            val unscaled = stripped.unscaledValue()
            if (unscaled.bitLength() >= Long.SIZE_BITS) return null
            return Tf_LongScaleDecimal(unscaled.toLong(), stripped.scale())
        }

        /**
         * Fast re-encoding for the slow-path arithmetic tail. Precondition: `bd` is the output
         * of [Lib_DecimalMath.scale] — so it's either `BigDecimal.ZERO` (signum 0, scale 0) or a
         * canonical-scale value (`scale == DECIMAL_FRAC_DIGITS`, in-range). This lets us:
         *
         * - skip the redundant [Lib_DecimalMath.scale] bounds re-check,
         * - bail before any work when the unscaled mantissa won't fit `Long` (the common case
         *   for products with magnitude ≥ ~0.092 at canonical scale 20),
         * - strip trailing zeros on `Long` arithmetic instead of `BigDecimal.stripTrailingZeros`,
         *   which under the JDK calls `MutableBigInteger.divideAndRemainder` per zero stripped.
         *
         * The `(mantissa, scale)` returned matches what [tryFrom] would have produced from the
         * same canonical `bd`, so cross-leaf [valueEquals] semantics are preserved.
         */
        fun tryFromCanonical(bd: BigDecimal): Tf_LongScaleDecimal? {
            if (bd.signum() == 0) return ZERO
            val unscaled = bd.unscaledValue()
            if (unscaled.bitLength() >= Long.SIZE_BITS) return null
            var m = unscaled.toLong()
            var s = bd.scale()
            while (s > 0 && m % 10L == 0L) {
                m /= 10L
                s--
            }
            return Tf_LongScaleDecimal(m, s)
        }
    }
}
