/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.google.common.math.LongMath
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.IndirectCallNode
import com.oracle.truffle.api.strings.TruffleString
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.truffle.Tf_Backend
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked
import net.postchain.rell.base.runtime.truffle.values.*
import java.math.BigDecimal
import kotlin.math.abs

/**
 * Maximum scale delta for which scale-alignment via `10^Δ` stays in Long. `10^18` ≈ 1.11e18
 * fits Long; `10^19` exceeds Long.MAX. Used by both [Tf_BinaryNode.DecimalArith]'s long-mantissa
 * Add/Sub fast path and [Tf_BinaryNode.DecimalCmp]'s long-mantissa scale-aligned compare.
 */
private const val DECIMAL_MAX_ALIGN_DELTA: Int = 18

private val DECIMAL_POW10: LongArray = LongArray(DECIMAL_MAX_ALIGN_DELTA + 1).also {
    var p = 1L
    for (i in 0..DECIMAL_MAX_ALIGN_DELTA) {
        it[i] = p
        if (i < DECIMAL_MAX_ALIGN_DELTA) p *= 10L
    }
}

/**
 * Native: binary operator. The translator picks a specialised subclass per op shape so the
 * runtime path is straight-line code rather than a `when (key)` dispatch — and so typed
 * `executeLong`/`executeBoolean` paths can chain through arithmetic without intermediate
 * [Rt_IntValue]/[Rt_BooleanValue] allocations.
 *
 * - [IntArith] specialises the five integer arithmetic ops; overrides [executeLong] so a
 *   chained expression like `a + b * c - 1` does five primitive ops and one box at the end.
 * - [BoolAnd] / [BoolOr] handle short-circuit logic on the typed-boolean path.
 * - [Generic] is the catch-all for collection/text/big-int/decimal arithmetic and unusual
 *   comparison shapes; it stays on the existing dispatch tables.
 */
internal sealed class Tf_BinaryNode: Tf_ExprNode() {

    /**
     * Integer arithmetic — `+ - * / %`. One concrete subclass per op so PE never sees a
     * `when (opKind)` dispatch — Kotlin's compiler emits a synthetic `else -> throw
     * NoWhenBranchMatchedException()` for every exhaustive `when` over an enum, and PE was
     * tracing into that constructor on every iteration (~1200 hot samples in profiling).
     *
     * Per-op subclasses also let Graal fold each call site to a single primitive operation
     * without any branching at all, which is what the typed `executeLong` chain is for.
     */
    internal abstract class IntArith(
        @field:Child protected var left: Tf_ExprNode,
        @field:Child protected var right: Tf_ExprNode,
        @field:CompilationFinal protected val errPos: ErrorPos?,
    ): Tf_BinaryNode() {
        protected abstract val opCode: String
        protected abstract fun op(l: Long, r: Long): Long

        final override fun executeLong(frame: VirtualFrame): Long {
            val l = left.executeLong(frame)
            val r = right.executeLong(frame)
            return try {
                op(l, r)
            } catch (_: ArithmeticException) {
                val e = Rt_Exception.common(
                    "expr:$opCode:overflow:$l:$r",
                    "Integer overflow: $l $opCode $r",
                )
                if (errPos != null) tfRethrowAt(tfRtFrame(frame), errPos, e) else throw e
            } catch (e: Rt_Exception) {
                if (errPos != null) tfRethrowAt(tfRtFrame(frame), errPos, e) else throw e
            }
        }

        final override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    internal class IntAdd(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        IntArith(left, right, errPos) {
        override val opCode
            get() = "+"

        override fun op(l: Long, r: Long): Long = LongMath.checkedAdd(l, r)
    }

    internal class IntSub(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        IntArith(left, right, errPos) {
        override val opCode
            get() = "-"

        override fun op(l: Long, r: Long): Long = LongMath.checkedSubtract(l, r)
    }

    internal class IntMul(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        IntArith(left, right, errPos) {
        override val opCode
            get() = "*"

        override fun op(l: Long, r: Long): Long = LongMath.checkedMultiply(l, r)
    }

    internal class IntDiv(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        IntArith(left, right, errPos) {
        override val opCode
            get() = "/"

        override fun op(l: Long, r: Long): Long {
            if (r == 0L) throw Rt_Exception.common("expr:/:div0:$l", "Division by zero: $l / 0")
            return l / r
        }
    }

    internal class IntMod(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        IntArith(left, right, errPos) {
        override val opCode
            get() = "%"

        override fun op(l: Long, r: Long): Long {
            if (r == 0L) throw Rt_Exception.common("expr:%:div0:$l", "Division by zero: $l % 0")
            return l % r
        }
    }

    /**
     * Integer comparisons (`<`, `<=`, `>`, `>=`) and equality (`==`, `!=`). One subclass per
     * operator — same rationale as [IntArith]: avoid the synthetic
     * `NoWhenBranchMatchedException` else-branch and let PE fold each comparison to one CMP.
     *
     * Equality (Eq/Ne) on integer-typed operands previously routed through [Generic]'s
     * `evaluateBinaryOp` dispatch (HashMap lookup keyed on op-key string + boxed
     * `Rt_IntValue.equals`). Profiling showed that path responsible for ~13K samples /
     * 30%+ of the profile on the bench workload.
     */
    internal abstract class IntBoolBinary(
        @field:Child protected var left: Tf_ExprNode,
        @field:Child protected var right: Tf_ExprNode,
    ): Tf_BinaryNode() {
        protected abstract fun op(l: Long, r: Long): Boolean
        final override fun executeBoolean(frame: VirtualFrame): Boolean =
            op(left.executeLong(frame), right.executeLong(frame))

        final override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    internal class IntLt(left: Tf_ExprNode, right: Tf_ExprNode): IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l < r
    }

    internal class IntLe(left: Tf_ExprNode, right: Tf_ExprNode): IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l <= r
    }

    internal class IntGt(left: Tf_ExprNode, right: Tf_ExprNode): IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l > r
    }

    internal class IntGe(left: Tf_ExprNode, right: Tf_ExprNode): IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l >= r
    }

    internal class IntEq(left: Tf_ExprNode, right: Tf_ExprNode): IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l == r
    }

    internal class IntNe(left: Tf_ExprNode, right: Tf_ExprNode): IntBoolBinary(left, right) {
        override fun op(l: Long, r: Long): Boolean = l != r
    }

    /** Short-circuit `&&`. Evaluates `right` only when `left` is true. */
    internal class BoolAnd(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
    ): Tf_BinaryNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            left.executeBoolean(frame) && right.executeBoolean(frame)

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    /** Short-circuit `||`. Evaluates `right` only when `left` is false. */
    internal class BoolOr(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
    ): Tf_BinaryNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            left.executeBoolean(frame) || right.executeBoolean(frame)

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    /**
     * Catch-all for everything that isn't integer arithmetic / integer comparison / `&&` / `||`.
     * Keeps the original op-key dispatch via [evaluateBinaryOp] / [evaluateCmpBinaryOp] but
     * routes the actual evaluation through a [TruffleBoundary] so PE doesn't try to inline
     * the giant `when (key)` dispatch tables in those helpers — the intermediate graph from
     * full table expansion is what breaks the inliner-depth budget. We lose the
     * constant-folded fast path for the rare ops, but the cold-path cost is one boundary
     * call per evaluation.
     *
     * The translator picks [GenericInt] / [GenericBool] / [Generic] by result type — typed
     * variants override `executeLong`/`executeBoolean` to unbox via [Tf_Unchecked.cast],
     * dropping the default `(execute() as Rt_IntValue).value` / `asBoolean()` chain (whose `typeError`
     * branch dominated PE-traced graphs even though it was never live at runtime).
     */
    internal open class Generic(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
        @field:CompilationFinal private val op: RR_BinaryOp,
        @field:CompilationFinal private val cmpInfo: RR_CmpBinaryOp?,
        @field:CompilationFinal private val errPos: ErrorPos?,
    ): Tf_BinaryNode() {
        final override fun execute(frame: VirtualFrame): Rt_Value {
            val l = left.execute(frame)
            val sc = shortCircuit(l)
            if (sc != null) return sc
            val r = right.execute(frame)
            return try {
                evaluate(l, r)
            } catch (e: Rt_Exception) {
                if (errPos != null) tfRethrowAt(tfRtFrame(frame), errPos, e) else throw e
            }
        }

        @TruffleBoundary
        private fun shortCircuit(l: Rt_Value): Rt_Value? = shortCircuitBinaryOp(op, l)

        @TruffleBoundary
        private fun evaluate(l: Rt_Value, r: Rt_Value): Rt_Value =
            if (cmpInfo != null) evaluateCmpBinaryOp(cmpInfo, l, r) else evaluateBinaryOp(op, l, r)
    }

    /**
     * Decimal arithmetic — `+ - * / %`. Specialises away the op-key HashMap dispatch in
     * `evaluateBinaryOp` (one [TruffleBoundary]-bracketed call per node, instead of a
     * boundary-free dispatch table that the inliner would otherwise expand fully). For Add and
     * Sub, opens a primitive long-mantissa fast path when both operands are
     * [Tf_LongScaleDecimal] with matching scale.
     *
     * Long-scale fast path safety: `Lib_DecimalMath.scale` canonicalises every produced decimal
     * to scale = `DECIMAL_FRAC_DIGITS` (= 20) for non-zero values, so any pair of values that
     * have travelled through the canonical factory will agree on scale and the equal-scale
     * Add/Sub stays purely in primitives. Mul produces scale = l + r and Div/Mod need rounding,
     * neither of which fits cleanly in primitives at Rell's configured precision; both go
     * straight to the BigDecimal slow path but still benefit from the dispatch elimination.
     *
     * After the BigDecimal slow path, ops where re-encoding can pay off
     * ([DecimalMul]/[DecimalDiv]) override [reencodeCanonical] to attempt
     * [Tf_LongScaleDecimal.tryFromCanonical]. Add/Sub/Mod skip re-encoding: the slow path there
     * fires precisely because long-mantissa arithmetic overflowed, so the canonical result
     * almost never fits `Long` again, and the strip-and-test cost dwarfs the rare hit.
     */
    internal abstract class DecimalArith(
        @field:Child protected var left: Tf_ExprNode,
        @field:Child protected var right: Tf_ExprNode,
        @field:CompilationFinal protected val errPos: ErrorPos?,
    ): Tf_BinaryNode() {
        protected abstract val opCode: String

        /**
         * Long-mantissa fast path. Returns a [Tf_LongScaleDecimal] when the operation can stay
         * in primitives without overflow or scale change; `null` defers to the next tier.
         * Default implementation: no fast path.
         */
        protected open fun fastLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Rt_DecimalValue? = null

        /**
         * 128-bit-mantissa fast path. Engages after [fastLongScale] returns `null`, after
         * widening the operands to [Tf_Int128ScaleDecimal]. Returns a fast-path result when
         * the operation stays in 128-bit primitives, or `null` to defer to the BigDecimal slow
         * path. Default implementation: no fast path.
         *
         * Workloads like `power(2.5, 17)` or `exp(x)` Taylor-series multiplications quickly
         * produce mantissas exceeding `Long.MAX_VALUE` but still fit comfortably in 128 bits;
         * this tier is what keeps them out of `BigInteger`/`BigDecimal` allocation.
         */
        protected open fun fastInt128Scale(l: Tf_Int128ScaleDecimal, r: Tf_Int128ScaleDecimal): Rt_DecimalValue? = null

        /** BigDecimal slow path. Subclasses delegate to [Lib_DecimalMath]; div0 is checked here. */
        protected abstract fun slowOp(l: BigDecimal, r: BigDecimal): BigDecimal

        /**
         * Re-encode the canonical slow-path result as [Tf_LongScaleDecimal] when worthwhile.
         * Default returns `null` — the result wraps as a generic `Rt_BigDecimalValue` and any
         * subsequent op pays the slow path. [DecimalMul]/[DecimalDiv] override to opt into
         * `tryFromCanonical`, since those produce small-magnitude results often enough that
         * keeping them on the long-scale chain is worth the unscaled-bitLength check.
         */
        protected open fun reencodeCanonical(canonical: BigDecimal): Rt_DecimalValue? = null

        final override fun execute(frame: VirtualFrame): Rt_Value {
            val lv: Rt_DecimalValue = Tf_Unchecked.cast(left.execute(frame))
            val rv: Rt_DecimalValue = Tf_Unchecked.cast(right.execute(frame))
            // Tier 1: long-scale fast path (both sides Tf_LongScaleDecimal).
            if (lv is Tf_LongScaleDecimal && rv is Tf_LongScaleDecimal) {
                fastLongScale(lv, rv)?.let {
                    return it
                }
                // Tier 2 (long-scale overflow path): widen both to 128-bit and retry.
                fastInt128Scale(
                    Tf_Int128ScaleDecimal.fromLongScale(lv),
                    Tf_Int128ScaleDecimal.fromLongScale(rv),
                )?.let {
                    return it
                }
            } else if (lv is Tf_Int128ScaleDecimal && rv is Tf_Int128ScaleDecimal) {
                // Tier 2 direct: both sides already at 128-bit.
                fastInt128Scale(lv, rv)?.let {
                    return it
                }
            } else if (lv is Tf_LongScaleDecimal && rv is Tf_Int128ScaleDecimal) {
                fastInt128Scale(Tf_Int128ScaleDecimal.fromLongScale(lv), rv)?.let {
                    return it
                }
            } else if (lv is Tf_Int128ScaleDecimal && rv is Tf_LongScaleDecimal) {
                fastInt128Scale(lv, Tf_Int128ScaleDecimal.fromLongScale(rv))?.let {
                    return it
                }
            }
            return try {
                slowPath(lv.value, rv.value)
            } catch (e: Rt_Exception) {
                if (errPos != null) tfRethrowAt(tfRtFrame(frame), errPos, e) else throw e
            }
        }

        @TruffleBoundary
        private fun slowPath(l: BigDecimal, r: BigDecimal): Rt_Value {
            val raw = slowOp(l, r)
            // Canonicalise: `Lib_DecimalMath.scale` rounds HALF_UP to `DECIMAL_FRAC_DIGITS=20`
            // (the canonical Rell scale) and range-checks. Slow-op subclasses (e.g. `multiply`)
            // return the un-canonicalised BigDecimal — we MUST use the canonical result here,
            // otherwise scale-21+ products keep their excess digits and diverge from the
            // tree-walker which always canonicalises.
            val canonical = Lib_DecimalMath.scale(raw) ?: throw Rt_DecimalValue.errOverflow(
                "expr:$opCode:overflow",
                "Decimal overflow: operator '$opCode'",
            )
            // Prefer Tf_LongScaleDecimal at natural scale when the unscaled mantissa fits Long
            // — that's what keeps the value participating in further long-scale fast paths.
            // Add/Sub/Mod opt out via the default `reencodeCanonical` (returns null), since
            // arriving at this slow path almost always means the result won't fit Long anyway.
            return reencodeCanonical(canonical) ?: Rt_DecimalValue.get(canonical)
        }

        /**
         * Shared Add/Sub fast path with scale alignment. When operand scales differ, the
         * smaller-scale mantissa is multiplied by `10^(Δscale)` to align against the larger
         * scale (overflow-checked). The result keeps the larger scale.
         *
         * Falls back to the BigDecimal slow path when:
         * - the scale delta is too large for `10^Δ` to fit Long (>18),
         * - alignment overflows Long, or
         * - the final add/sub overflows Long.
         */
        protected fun fastLongScaleAddSub(
            l: Tf_LongScaleDecimal,
            r: Tf_LongScaleDecimal,
            subtract: Boolean,
        ): Rt_DecimalValue? {
            val lm: Long
            val rm: Long
            val resultScale: Int
            when {
                l.scale == r.scale -> {
                    lm = l.mantissa
                    rm = r.mantissa
                    resultScale = l.scale
                }

                l.scale < r.scale -> {
                    val delta = r.scale - l.scale
                    if (delta > DECIMAL_MAX_ALIGN_DELTA) return null
                    lm = try {
                        Math.multiplyExact(l.mantissa, DECIMAL_POW10[delta])
                    } catch (_: ArithmeticException) {
                        return null
                    }
                    rm = r.mantissa
                    resultScale = r.scale
                }

                else -> {
                    val delta = l.scale - r.scale
                    if (delta > DECIMAL_MAX_ALIGN_DELTA) return null
                    rm = try {
                        Math.multiplyExact(r.mantissa, DECIMAL_POW10[delta])
                    } catch (_: ArithmeticException) {
                        return null
                    }
                    lm = l.mantissa
                    resultScale = l.scale
                }
            }
            return try {
                val resultMantissa = if (subtract) Math.subtractExact(lm, rm) else Math.addExact(lm, rm)
                Tf_LongScaleDecimal(resultMantissa, resultScale)
            } catch (_: ArithmeticException) {
                null
            }
        }

        /**
         * 128-bit Add/Sub fast path with scale alignment. Mirrors [fastLongScaleAddSub] but
         * uses [add128] / [sub128] / [mul128by10Pow] for the 128-bit primitives. Bails when
         * scale alignment overflows 128 bits or the final add/sub overflows.
         *
         * Limitation: scale-delta alignment uses [mul128by10Pow], which caps at `10^18` per
         * step. When the smaller-scale mantissa doesn't have room above for an 18-digit shift,
         * the result is the same `null` defer that long-scale already produced — but workloads
         * that arrive here have already overflowed Long, so the high mantissa is non-zero and
         * a single `* 10^18` stays in 128 bits unless it was already near 2^127.
         */
        protected fun fastInt128ScaleAddSub(
            l: Tf_Int128ScaleDecimal,
            r: Tf_Int128ScaleDecimal,
            subtract: Boolean,
        ): Rt_DecimalValue? {
            val lhi: Long
            val llo: Long
            val rhi: Long
            val rlo: Long
            val resultScale: Int
            when {
                l.scale == r.scale -> {
                    lhi = l.hi; llo = l.lo
                    rhi = r.hi; rlo = r.lo
                    resultScale = l.scale
                }

                l.scale < r.scale -> {
                    val delta = r.scale - l.scale
                    val aligned = mul128by10Pow(l.hi, l.lo, delta) ?: return null
                    lhi = aligned[0]; llo = aligned[1]
                    rhi = r.hi; rlo = r.lo
                    resultScale = r.scale
                }

                else -> {
                    val delta = l.scale - r.scale
                    val aligned = mul128by10Pow(r.hi, r.lo, delta) ?: return null
                    rhi = aligned[0]; rlo = aligned[1]
                    lhi = l.hi; llo = l.lo
                    resultScale = l.scale
                }
            }
            val res = (if (subtract) sub128(lhi, llo, rhi, rlo) else add128(lhi, llo, rhi, rlo))
                ?: return null
            return Tf_Int128ScaleDecimal(res[0], res[1], resultScale)
        }
    }

    internal class DecimalAdd(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        DecimalArith(left, right, errPos) {
        override val opCode get() = "+"

        override fun fastLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Rt_DecimalValue? =
            fastLongScaleAddSub(l, r, subtract = false)

        override fun fastInt128Scale(l: Tf_Int128ScaleDecimal, r: Tf_Int128ScaleDecimal): Rt_DecimalValue? =
            fastInt128ScaleAddSub(l, r, subtract = false)

        override fun slowOp(l: BigDecimal, r: BigDecimal): BigDecimal = Lib_DecimalMath.add(l, r)
    }

    internal class DecimalSub(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        DecimalArith(left, right, errPos) {
        override val opCode
            get() = "-"

        override fun fastLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Rt_DecimalValue? =
            fastLongScaleAddSub(l, r, subtract = true)

        override fun fastInt128Scale(l: Tf_Int128ScaleDecimal, r: Tf_Int128ScaleDecimal): Rt_DecimalValue? =
            fastInt128ScaleAddSub(l, r, subtract = true)

        override fun slowOp(l: BigDecimal, r: BigDecimal): BigDecimal = Lib_DecimalMath.subtract(l, r)
    }

    internal class DecimalMul(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        DecimalArith(left, right, errPos) {
        override val opCode
            get() = "*"

        /**
         * Long-mantissa Mul fast path. Two regimes:
         *
         * 1. `newScale = s1 + s2 <= DECIMAL_FRAC_DIGITS=20` → return `(m1*m2, newScale)` directly.
         *    `Math.multiplyExact` rejects overflow into the slow path.
         *
         * 2. `newScale > 20` → drop `k = newScale - 20` trailing fractional digits with
         *    Long-arithmetic HALF_UP rounding (away from zero), keeping the result at canonical
         *    scale 20. Required for cross-leaf parity with the slow path's
         *    `Lib_DecimalMath.scale(...)` (BigDecimal `setScale(20, HALF_UP)`); without rounding
         *    here, products like `0.0…054` would diverge from the canonical `0.0…05`.
         *
         *    Bails when `k > DECIMAL_MAX_ALIGN_DELTA=18` (`10^k` exceeds Long), or when the mantissa
         *    product itself overflows. Both cases drop to the BigDecimal slow path which can
         *    still narrow back to long-scale via `tryFrom` after canonicalisation.
         *
         * Negative-scale guard mirrors Add/Sub: scales below `-DECIMAL_INT_DIGITS` would fail
         * `Lib_DecimalMath.scale` anyway, so reject up front.
         */
        override fun fastLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Rt_DecimalValue? {
            val newScale = l.scale + r.scale
            if (newScale < -Lib_DecimalMath.DECIMAL_INT_DIGITS) return null
            val product: Long = try {
                Math.multiplyExact(l.mantissa, r.mantissa)
            } catch (_: ArithmeticException) {
                return null
            }
            if (newScale <= Lib_DecimalMath.DECIMAL_FRAC_DIGITS) {
                return Tf_LongScaleDecimal(product, newScale)
            }
            val k = newScale - Lib_DecimalMath.DECIMAL_FRAC_DIGITS
            if (k > DECIMAL_MAX_ALIGN_DELTA) return null
            val divisor = DECIMAL_POW10[k]
            val quotient = product / divisor
            val remainder = product % divisor

            // HALF_UP away-from-zero: round when |remainder| * 2 >= divisor. The doubled
            // remainder fits Long because |remainder| < divisor <= 10^18 and 2 * 10^18 < Long.MAX.
            val rounded = if (abs(remainder) * 2L >= divisor) {
                val delta = if (product >= 0L) 1L else -1L
                try {
                    Math.addExact(quotient, delta)
                } catch (_: ArithmeticException) {
                    return null
                }
            } else {
                quotient
            }
            return Tf_LongScaleDecimal(rounded, Lib_DecimalMath.DECIMAL_FRAC_DIGITS)
        }

        /**
         * 128-bit Mul fast path. Mirrors [fastLongScale] but operates on 128-bit mantissas.
         *
         * Strategy: pick the side whose mantissa fits a signed Long as the multiplier
         * (non-negative after sign normalisation), and multiply the other 128-bit operand by
         * it via [mul128by64Unsigned]. Then either:
         *
         * - `newScale <= 20` → return the product directly at scale `newScale`.
         * - `newScale > 20` → drop `k = newScale - 20` trailing fractional digits with HALF_UP
         *   rounding (away from zero). Implemented as `k` repeated [div128By10] steps tracking
         *   the last-stripped digit; HALF_UP rounds when the discarded digit is `>= 5`.
         *
         * When neither side fits a signed Long (i.e. both operands are genuinely 128-bit),
         * defer to BigDecimal — the full 128 × 128 → 128 case is rare in the workloads that
         * triggered the 128-bit tier (typically `c ** k` style where the multiplier stays
         * small) and a correct overflow-checked 128 × 128 implementation is more code than
         * the rare hit justifies.
         */
        override fun fastInt128Scale(l: Tf_Int128ScaleDecimal, r: Tf_Int128ScaleDecimal): Rt_DecimalValue? {
            val newScale = l.scale + r.scale
            if (newScale < -Lib_DecimalMath.DECIMAL_INT_DIGITS) return null

            // Pick the side whose mantissa fits a signed Long. mul128by64Unsigned requires
            // a non-negative multiplier, so when the picked side is negative we negate the
            // 128-bit operand instead and use the absolute multiplier.
            val rLong = r.asSignedLong()
            val lLong = if (rLong == null) l.asSignedLong() else null
            val bigHi: Long
            val bigLo: Long
            val mult: Long
            when {
                rLong != null -> {
                    if (rLong == Long.MIN_VALUE) return null
                    if (rLong >= 0L) {
                        bigHi = l.hi; bigLo = l.lo; mult = rLong
                    } else {
                        val neg = neg128(l.hi, l.lo) ?: return null
                        bigHi = neg[0]; bigLo = neg[1]; mult = -rLong
                    }
                }

                lLong != null -> {
                    if (lLong == Long.MIN_VALUE) return null
                    if (lLong >= 0L) {
                        bigHi = r.hi; bigLo = r.lo; mult = lLong
                    } else {
                        val neg = neg128(r.hi, r.lo) ?: return null
                        bigHi = neg[0]; bigLo = neg[1]; mult = -lLong
                    }
                }

                else -> return null
            }

            val product = mul128by64Unsigned(bigHi, bigLo, mult) ?: return null
            val productNegative = product[0] < 0L
            var pHi = product[0]
            var pLo = product[1]

            if (newScale <= Lib_DecimalMath.DECIMAL_FRAC_DIGITS) {
                return Tf_Int128ScaleDecimal(pHi, pLo, newScale)
            }
            val k = newScale - Lib_DecimalMath.DECIMAL_FRAC_DIGITS
            if (k > 18) return null

            // HALF_UP rounding across 128 bits: divide by 10^k via repeated div-by-10 and
            // capture the last discarded digit. HALF_UP rounds away from zero on `>= 5` —
            // tie-breaks always go away from zero regardless of any earlier discarded digits.
            var lastDigit = 0
            repeat(k) {
                val step = div128By10(pHi, pLo)
                lastDigit = step.third
                if (lastDigit < 0) lastDigit = -lastDigit
                pHi = step.first
                pLo = step.second
            }
            val rounded = if (lastDigit >= 5) {
                // Round away from zero. Use the *original* product sign — after div-by-10
                // the quotient may have rounded to (0, 0) for tiny negative products, but
                // round-away-from-zero must still produce -1, not +1.
                val delta = if (productNegative) longArrayOf(-1L, -1L) else longArrayOf(0L, 1L)
                add128(pHi, pLo, delta[0], delta[1]) ?: return null
            } else {
                longArrayOf(pHi, pLo)
            }
            return Tf_Int128ScaleDecimal(rounded[0], rounded[1], Lib_DecimalMath.DECIMAL_FRAC_DIGITS)
        }

        override fun slowOp(l: BigDecimal, r: BigDecimal): BigDecimal = Lib_DecimalMath.multiply(l, r)

        override fun reencodeCanonical(canonical: BigDecimal): Rt_DecimalValue? =
            Tf_LongScaleDecimal.tryFromCanonical(canonical)
                ?: Tf_Int128ScaleDecimal.tryFromCanonical(canonical)
    }

    internal class DecimalDiv(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        DecimalArith(left, right, errPos) {
        override val opCode
            get() = "/"

        /**
         * Long-mantissa Div fast path — produces a result at canonical scale
         * `DECIMAL_FRAC_DIGITS=20` with HALF_UP rounding, matching `Lib_DecimalMath.divide`'s
         * `BigDecimal.divide(b, 20, HALF_UP)`.
         *
         * Strategy: pre-multiply `l.mantissa` by `10^shift` where
         * `shift = 20 - l.scale + r.scale`, then do a single Long division. The numerator is
         * `lm * 10^(20 + rs - ls)` and the denominator is `rm`; the integer quotient is the
         * mantissa of the canonical result before rounding.
         *
         * HALF_UP rounding without overflow: |rem| < |rm|, so the half-threshold
         * `(|rm| + 1) ushr 1` is computed with unsigned shift to avoid the doubling-overflow
         * trap on near-`Long.MAX_VALUE` divisors. The +/-1 round step direction matches the
         * sign of the true rational quotient (`sign(scaledLm) XOR sign(rm)`).
         *
         * Bails to the BigDecimal slow path when:
         * - `r.mantissa == 0` (slow path raises the canonical div-by-zero error),
         * - `r.mantissa == Long.MIN_VALUE` (`Math.abs` would wrap),
         * - `shift` is negative or > [DECIMAL_MAX_ALIGN_DELTA],
         * - scaling `l.mantissa` overflows Long,
         * - the rounded quotient overflows Long.
         */
        override fun fastLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Rt_DecimalValue? {
            val rm = r.mantissa
            if (rm == 0L) return null
            if (rm == Long.MIN_VALUE) return null

            val shift = Lib_DecimalMath.DECIMAL_FRAC_DIGITS - l.scale + r.scale
            if (shift !in 0..DECIMAL_MAX_ALIGN_DELTA) return null

            val scaledLm = try {
                Math.multiplyExact(l.mantissa, DECIMAL_POW10[shift])
            } catch (_: ArithmeticException) {
                return null
            }

            // Guard the silent `Long.MIN_VALUE / -1` overflow that Java's `/` swallows: the
            // true quotient is +2^63 which doesn't fit Long. (All other Long-div cases are
            // overflow-free.)
            if (scaledLm == Long.MIN_VALUE && rm == -1L) return null

            val quotient = scaledLm / rm
            val remainder = scaledLm % rm
            if (remainder == 0L) {
                return Tf_LongScaleDecimal(quotient, Lib_DecimalMath.DECIMAL_FRAC_DIGITS)
            }

            // |remainder| < |rm|, and rm != Long.MIN_VALUE, so both abs() calls are safe.
            // (|remainder| < |rm| < 2^63 implies remainder != Long.MIN_VALUE.)
            val absRm = abs(rm)
            val absRem = abs(remainder)
            // HALF_UP: round when 2*|rem| >= |rm|, equivalently |rem| >= ceil(|rm|/2).
            // Use `(absRm + 1) ushr 1` so the +1 wrap on absRm == Long.MAX_VALUE still yields
            // the correct ceil(2^63 / 2) = 2^62 — Kotlin's `ushr` treats the operand as
            // unsigned, so the sign-bit carry from the wrap is preserved as the high bit.
            val halfUp = (absRm + 1L) ushr 1
            val rounded = if (absRem >= halfUp) {
                // Round away from zero; sign matches sign(scaledLm) XOR sign(rm).
                // scaledLm != 0 here (otherwise remainder would be 0), so the comparisons
                // are well-defined.
                val roundUp = (scaledLm > 0L) == (rm > 0L)
                try {
                    if (roundUp) Math.addExact(quotient, 1L) else Math.subtractExact(quotient, 1L)
                } catch (_: ArithmeticException) {
                    return null
                }
            } else {
                quotient
            }

            return Tf_LongScaleDecimal(rounded, Lib_DecimalMath.DECIMAL_FRAC_DIGITS)
        }

        override fun slowOp(l: BigDecimal, r: BigDecimal): BigDecimal {
            if (r.signum() == 0) throw Rt_Exception.common("expr:/:div0", "Division by zero: /")
            return Lib_DecimalMath.divide(l, r)
        }

        override fun reencodeCanonical(canonical: BigDecimal): Rt_DecimalValue? =
            Tf_LongScaleDecimal.tryFromCanonical(canonical)
    }

    internal class DecimalMod(left: Tf_ExprNode, right: Tf_ExprNode, errPos: ErrorPos?):
        DecimalArith(left, right, errPos) {
        override val opCode get() = "%"

        /**
         * Long-mantissa Mod fast path. Aligns operand scales like
         * [fastLongScaleAddSub], then takes the Long remainder — Java `%` returns a value
         * with the sign of the dividend, matching `BigDecimal.remainder`'s contract exactly.
         *
         * Result scale = `max(l.scale, r.scale)`; no canonicalisation to scale 20 is needed
         * because `Tf_LongScaleDecimal.value` re-pads on materialisation.
         *
         * Bails to the BigDecimal slow path when:
         * - `r.mantissa == 0` (slow path raises the canonical div-by-zero error),
         * - the alignment delta exceeds [DECIMAL_MAX_ALIGN_DELTA],
         * - scaling either mantissa to the aligned scale overflows Long.
         */
        override fun fastLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Rt_DecimalValue? {
            if (r.mantissa == 0L) return null

            val lm: Long
            val rm: Long
            val resultScale: Int
            when {
                l.scale == r.scale -> {
                    lm = l.mantissa
                    rm = r.mantissa
                    resultScale = l.scale
                }

                l.scale < r.scale -> {
                    val delta = r.scale - l.scale
                    if (delta > DECIMAL_MAX_ALIGN_DELTA) return null
                    lm = try {
                        Math.multiplyExact(l.mantissa, DECIMAL_POW10[delta])
                    } catch (_: ArithmeticException) {
                        return null
                    }
                    rm = r.mantissa
                    resultScale = r.scale
                }

                else -> {
                    val delta = l.scale - r.scale
                    if (delta > DECIMAL_MAX_ALIGN_DELTA) return null
                    rm = try {
                        Math.multiplyExact(r.mantissa, DECIMAL_POW10[delta])
                    } catch (_: ArithmeticException) {
                        return null
                    }
                    lm = l.mantissa
                    resultScale = l.scale
                }
            }
            // |lm % rm| < |rm|, so the result always fits Long without overflow.
            return Tf_LongScaleDecimal(lm % rm, resultScale)
        }

        // 128-bit Mod fast path: deferred. A correct 128 % 128 (or even 128 % 64) requires
        // proper Knuth long division to handle scale alignment when one operand is a 128-bit
        // mantissa, which is more code than the rare hit justifies. Mod is a marginal op in
        // the workloads we're optimising (the dominant cost is power/exp via Mul), so falling
        // through to BigDecimal here is acceptable.

        override fun slowOp(l: BigDecimal, r: BigDecimal): BigDecimal {
            if (r.signum() == 0) throw Rt_Exception.common("expr:%:div0", "Division by zero: %")
            return Lib_DecimalMath.remainder(l, r)
        }
    }

    /**
     * Decimal comparison (`< <= > >= == !=`). Mirrors [IntBoolBinary]'s typed
     * `executeBoolean` shape: a single primitive [Int] sign result feeds the per-op predicate.
     *
     * Long-mantissa fast path (no `BigDecimal` materialisation, no `Tf_LongScaleDecimal.value`
     * volatile load, no allocation) when both sides are [Tf_LongScaleDecimal]:
     * - same scale → compare mantissas directly with `Long.compare`.
     * - different scale, |Δ| ≤ 18 → multiply the smaller-scale mantissa by `10^Δ` to align,
     *   then compare. If alignment overflows Long, sign of the larger-scale mantissa decides
     *   (an overflowed alignment means the smaller-scale value's magnitude exceeds the
     *   larger-scale one, so the smaller-scale side is "bigger" in absolute terms — but its
     *   sign determines which way).
     * - |Δ| > 18 → fall through to the BigDecimal compare.
     *
     * This is the dominant cost driver in compare-heavy hot loops (Simplex noise's
     * `simplex_2d` does ~10 decimal compares per call × 8 octaves × 25 cells × 20 reps).
     * Without this node, `Generic.evaluate` routes through `evaluateCmpBinaryOp` which calls
     * `asDecimal().compareTo(asDecimal())` — and `asDecimal()` on a long-scale leaf reads
     * `.value`, triggering `BigDecimal.valueOf(...).setScale(20)` per side per compare.
     */
    internal abstract class DecimalCmp(
        @field:Child protected var left: Tf_ExprNode,
        @field:Child protected var right: Tf_ExprNode,
    ): Tf_BinaryNode() {
        protected abstract fun fromSign(sign: Int): Boolean

        final override fun executeBoolean(frame: VirtualFrame): Boolean {
            val lv = Tf_Unchecked.cast<Rt_DecimalValue>(left.execute(frame))
            val rv = Tf_Unchecked.cast<Rt_DecimalValue>(right.execute(frame))
            val sign = compareWithFallback(lv, rv)
            return fromSign(sign)
        }

        final override fun execute(frame: VirtualFrame): Rt_Value =
            Rt_BooleanValue.get(executeBoolean(frame))

        private fun compareWithFallback(lv: Rt_DecimalValue, rv: Rt_DecimalValue): Int {
            // Tier 1: long-scale fast compare.
            if (lv is Tf_LongScaleDecimal && rv is Tf_LongScaleDecimal) {
                val fast = compareLongScale(lv, rv)
                if (fast != null) return fast
                // Tier 2 (long-scale alignment overflow): widen both to 128-bit and retry.
                val fast128 = compareInt128Scale(
                    Tf_Int128ScaleDecimal.fromLongScale(lv),
                    Tf_Int128ScaleDecimal.fromLongScale(rv),
                )
                if (fast128 != null) return fast128
            } else if (lv is Tf_Int128ScaleDecimal && rv is Tf_Int128ScaleDecimal) {
                val fast = compareInt128Scale(lv, rv)
                if (fast != null) return fast
            } else if (lv is Tf_LongScaleDecimal && rv is Tf_Int128ScaleDecimal) {
                val fast = compareInt128Scale(Tf_Int128ScaleDecimal.fromLongScale(lv), rv)
                if (fast != null) return fast
            } else if (lv is Tf_Int128ScaleDecimal && rv is Tf_LongScaleDecimal) {
                val fast = compareInt128Scale(lv, Tf_Int128ScaleDecimal.fromLongScale(rv))
                if (fast != null) return fast
            }
            return slowCompare(lv, rv)
        }

        @TruffleBoundary
        private fun slowCompare(lv: Rt_DecimalValue, rv: Rt_DecimalValue): Int =
            lv.value.compareTo(rv.value)

        companion object {
            /**
             * Same-scale or scale-aligned long-scale compare. Returns the comparison sign or
             * `null` to defer to the next tier (128-bit, then BigDecimal).
             */
            internal fun compareLongScale(l: Tf_LongScaleDecimal, r: Tf_LongScaleDecimal): Int? {
                if (l.scale == r.scale) return l.mantissa.compareTo(r.mantissa)
                val delta = abs(l.scale - r.scale)
                if (delta > DECIMAL_MAX_ALIGN_DELTA) return null
                val pow = DECIMAL_POW10[delta]
                val (lm, rm) = if (l.scale < r.scale) {
                    val aligned = try {
                        Math.multiplyExact(l.mantissa, pow)
                    } catch (_: ArithmeticException) {
                        // Aligned overflow: |l| × 10^Δ > Long.MAX. Defer to 128-bit tier
                        // rather than answering by sign — at 128 bits the alignment may
                        // succeed and produce a precise comparison even when long-scale
                        // alignment overflowed.
                        return null
                    }
                    aligned to r.mantissa
                } else {
                    val aligned = try {
                        Math.multiplyExact(r.mantissa, pow)
                    } catch (_: ArithmeticException) {
                        return null
                    }
                    l.mantissa to aligned
                }
                return lm.compareTo(rm)
            }

            /**
             * Same-scale or scale-aligned 128-bit compare. Returns the comparison sign or
             * `null` to defer to the BigDecimal slow path (when scale-delta exceeds 18).
             *
             * When 128-bit alignment overflows, the side whose mantissa we tried to scale up
             * dominates in magnitude — its (signed) sign decides the comparison:
             * - if `l` was scaled up and overflowed: |l| > |r|, answer = sign(l).
             * - if `r` was scaled up and overflowed: |r| > |l|, answer = -sign(r).
             *
             * Since `hi` carries the sign of a 128-bit signed integer, `sign(x) = 1 if x.hi >= 0
             * && x.hi|x.lo != 0 else -1`. Zero would not have overflowed alignment, so the
             * dominator side is non-zero here.
             */
            internal fun compareInt128Scale(l: Tf_Int128ScaleDecimal, r: Tf_Int128ScaleDecimal): Int? {
                if (l.scale == r.scale) return cmp128(l.hi, l.lo, r.hi, r.lo)
                val delta = abs(l.scale - r.scale)
                if (delta > 18) return null
                if (l.scale < r.scale) {
                    val aligned = mul128by10Pow(l.hi, l.lo, delta)
                        ?: return if (l.hi >= 0L) 1 else -1
                    return cmp128(aligned[0], aligned[1], r.hi, r.lo)
                } else {
                    val aligned = mul128by10Pow(r.hi, r.lo, delta)
                        ?: return if (r.hi >= 0L) -1 else 1
                    return cmp128(l.hi, l.lo, aligned[0], aligned[1])
                }
            }
        }
    }

    internal class DecimalLt(left: Tf_ExprNode, right: Tf_ExprNode): DecimalCmp(left, right) {
        override fun fromSign(sign: Int): Boolean = sign < 0
    }

    internal class DecimalLe(left: Tf_ExprNode, right: Tf_ExprNode): DecimalCmp(left, right) {
        override fun fromSign(sign: Int): Boolean = sign <= 0
    }

    internal class DecimalGt(left: Tf_ExprNode, right: Tf_ExprNode): DecimalCmp(left, right) {
        override fun fromSign(sign: Int): Boolean = sign > 0
    }

    internal class DecimalGe(left: Tf_ExprNode, right: Tf_ExprNode): DecimalCmp(left, right) {
        override fun fromSign(sign: Int): Boolean = sign >= 0
    }

    internal class DecimalEq(left: Tf_ExprNode, right: Tf_ExprNode): DecimalCmp(left, right) {
        override fun fromSign(sign: Int): Boolean = sign == 0
    }

    internal class DecimalNe(left: Tf_ExprNode, right: Tf_ExprNode): DecimalCmp(left, right) {
        override fun fromSign(sign: Int): Boolean = sign != 0
    }

    /**
     * Native: text concatenation (`R_BinaryOp_Concat_Text`). Eliminates the op-key HashMap
     * dispatch in `evaluateBinaryOp` and produces a [Tf_TruffleStringText] result so downstream
     * text nodes (substring, encoding, further concat) stay on the TruffleString fast path.
     *
     * Fast path: when both operands are [Tf_TruffleStringText], dispatches through a cached
     * [TruffleString.ConcatNode] (`@Child`), giving Truffle a
     * proper PE-friendly call site that can do zero-copy / lazy concat where applicable.
     * Slow path: materialise both sides to Java `String`, concat, wrap.
     */
    internal class TextConcat(
        @field:Child private var left: Tf_ExprNode,
        @field:Child private var right: Tf_ExprNode,
    ): Tf_BinaryNode() {
        @field:Child
        private var concatNode: TruffleString.ConcatNode =
            TruffleString.ConcatNode.create()

        override fun execute(frame: VirtualFrame): Rt_Value {
            val l = Tf_Unchecked.cast<Rt_TextValue>(left.execute(frame))
            val r = Tf_Unchecked.cast<Rt_TextValue>(right.execute(frame))
            if (l is Tf_TruffleStringText && r is Tf_TruffleStringText) {
                return Tf_TruffleStringText(
                    concatNode.execute(l.ts, r.ts, Tf_TruffleStringText.ENCODING, false),
                )
            }
            return Tf_TruffleStringText.fromJavaString(l.value + r.value)
        }
    }

    /** Generic binary node whose result is statically integer-typed — unboxes on `executeLong`. */
    internal class GenericInt(
        left: Tf_ExprNode,
        right: Tf_ExprNode,
        op: RR_BinaryOp,
        cmpInfo: RR_CmpBinaryOp?,
        errPos: ErrorPos?,
    ): Generic(left, right, op, cmpInfo, errPos) {
        override fun executeLong(frame: VirtualFrame): Long = Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Generic binary node whose result is statically boolean-typed — unboxes on `executeBoolean`. */
    internal class GenericBool(
        left: Tf_ExprNode,
        right: Tf_ExprNode,
        op: RR_BinaryOp,
        cmpInfo: RR_CmpBinaryOp?,
        errPos: ErrorPos?,
    ): Generic(left, right, op, cmpInfo, errPos) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

}

/**
 * Native: unary operator. Sealed by op shape so the typed `executeLong` / `executeBoolean`
 * specialisations chain through [Tf_BinaryNode]'s primitive paths without intermediate
 * boxing.
 *
 * - [IntMinus] for integer negation; overrides [executeLong] with explicit overflow
 *   handling (mirrors the tree-walker's `expr:-:overflow:$v` error code exactly).
 * - [Not] for boolean negation; overrides [executeBoolean].
 * - [Generic] for `Minus_BigInteger` / `Minus_Decimal`, which still go through the
 *   `evaluateUnaryOp` dispatch table — the savings on those paths would be minor.
 */
internal sealed class Tf_UnaryNode: Tf_ExprNode() {
    internal class IntMinus(
        @field:Child private var expr: Tf_ExprNode,
        @field:CompilationFinal private val errPos: ErrorPos,
    ): Tf_UnaryNode() {
        override fun executeLong(frame: VirtualFrame): Long {
            val v = expr.executeLong(frame)
            return try {
                LongMath.checkedSubtract(0L, v)
            } catch (_: ArithmeticException) {
                tfRethrowAt(
                    tfRtFrame(frame),
                    errPos,
                    Rt_Exception.common("expr:-:overflow:$v", "Integer overflow: -($v)"),
                )
            }
        }

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    internal class Not(@field:Child private var expr: Tf_ExprNode): Tf_UnaryNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean = !expr.executeBoolean(frame)
        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }

    internal class Generic(
        @field:Child private var expr: Tf_ExprNode,
        @field:CompilationFinal private val op: RR_UnaryOp,
        @field:CompilationFinal private val errPos: ErrorPos,
    ): Tf_UnaryNode() {
        override fun execute(frame: VirtualFrame): Rt_Value {
            val v = expr.execute(frame)
            return try {
                evaluate(v)
            } catch (e: Rt_Exception) {
                tfRethrowAt(tfRtFrame(frame), errPos, e)
            }
        }

        @TruffleBoundary
        private fun evaluate(v: Rt_Value): Rt_Value = evaluateUnaryOp(op, v)
    }
}

/**
 * Native: implicit type conversion (Direct, IntegerToBigInteger, IntegerToDecimal,
 * BigIntegerToDecimal, Nullable). Adapter is `@CompilationFinal`; the singleton-object adapters
 * fold to no-ops or single conversion paths after PE.
 *
 * The translator picks [DirectInt] / [DirectBool] for the no-op-shape `Direct` adapter when the
 * inner expression is statically integer- or boolean-typed — those subclasses skip
 * `applyTypeAdapter` entirely and chain through the inner node's typed `executeLong` /
 * `executeBoolean`. Other adapter shapes (`IntegerToBigInteger`, `IntegerToDecimal`, ...) box
 * unconditionally because the conversion's output type is non-primitive.
 */
internal class Tf_TypeAdapterNode(
    @field:Child private var expr: Tf_ExprNode,
    @field:CompilationFinal private val adapter: RR_TypeAdapter,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value =
        applyTypeAdapter(adapter, expr.execute(frame))

    /**
     * `Direct` integer adapter — passes the inner expression's typed `executeLong` through
     * unchanged. Used when the translator proves the inner type is integer.
     */
    internal class DirectInt(
        @field:Child private var inner: Tf_ExprNode,
    ): Tf_ExprNode() {
        override fun executeLong(frame: VirtualFrame): Long = inner.executeLong(frame)
        override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    /** `Direct` boolean adapter — pass-through on the typed boolean path. See [DirectInt]. */
    internal class DirectBool(
        @field:Child private var inner: Tf_ExprNode,
    ): Tf_ExprNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean = inner.executeBoolean(frame)
        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }
}

/**
 * Native: an irreducible compile-time error placeholder (`RR_Expr.Error`). The compiler emits
 * these for nodes that survived type-checking but should be unreachable at runtime; the
 * tree-walker uses Kotlin's `error()` for the same case, throwing [IllegalStateException]. We
 * mirror that exact behaviour to keep differential parity.
 */
internal class Tf_ErrorNode(
    @field:CompilationFinal private val message: String,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value = error("RR_Expr.Error reached at runtime: $message")
}

/**
 * Native: full function call. Two specialised shapes:
 *
 * - [UserFn] for `RegularUser` / `AbstractUser` targets — invokes the callee through a
 *   [DirectCallNode] wrapping the cached Truffle [RootCallTarget],
 *   so Graal's PE can inline the callee body into the caller. This is the entire reason
 *   to be running on Truffle: cross-call inlining + per-callsite specialisation.
 *
 * - [Generic] for everything else — operations, native functions, function values,
 *   sys-functions, partial calls, abstract-overrides, extendables. These have no Truffle
 *   representation today and dispatch through the wrapped impl's `callTarget`.
 *
 * Both shapes share the argument-evaluation prelude (base, args, mapping, safe-null check).
 * Identity mapping is detected at translate time and the runtime skips the permutation in
 * that case.
 */
internal sealed class Tf_FunctionCallNode: Tf_ExprNode() {
    /**
     * User-function call site. Dispatches via [DirectCallNode] resolved lazily on the first
     * execution from a `@CompilationFinal` [RootCallTarget].
     *
     * **Direct, not indirect.** With Kotlin's null-check intrinsics stripped from all hot-path
     * modules (see `runtime-core`/`utils`/`runtime-interpreter`/`runtime-truffle`
     * `build.gradle.kts`), Graal's PE no longer chases `Intrinsics.checkNotNull → JDK Locale`
     * cycles, so the inliner-depth budget that previously forced [IndirectCallNode] is no
     * longer hit. With [DirectCallNode] each callee body inlines into the caller, which is
     * the entire point of running on Truffle — cross-call constant folding, escape analysis,
     * and primitive-typed `executeLong`/`executeBoolean` chains across function boundaries.
     *
     * **Translator-time validation pushdown.** When the translator can prove (a) every arg's
     * static type structurally matches its corresponding parameter's declared type and (b)
     * no parameter carries an `RR_SizeConstraint`, the call site sets `fastPath = true`,
     * `frameDescriptor` / `defId` / `paramOffsets` are pre-resolved at translate time, and
     * `setupCalleeFrameFast` runs inline — no [TruffleBoundary], no `validateParams`, no
     * `setParams` indirection. The frame is allocated and parameter slots are written
     * directly via [Rt_CallFrame.setUncheckedAt], which lets Graal's PE fold caller-side
     * argument values straight into the callee's body.
     *
     * **Slow path.** When `fastPath = false` (the rare cases: type-adapter reshapes, size
     * constraints), the legacy [TruffleBoundary] path through
     * `delegate.validateParams` / `createFrame` / `setParams` still runs, behind a lazy
     * resolution that mirrors how recursive functions need to be wired up.
     */
    internal open class UserFn(
        @field:Child private var base: Tf_ExprNode?,
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val safe: Boolean,
        @field:CompilationFinal private val identityMapping: Boolean,
        @field:CompilationFinal private val callPos: FilePos,
        @field:CompilationFinal private val fnDefIndex: Int,
        // --- Translate-time pushdown ---
        @field:CompilationFinal private val fastPath: Boolean,
        @field:CompilationFinal private val frameDescriptor: RR_FrameDescriptor,
        @field:CompilationFinal private val defId: DefinitionId,
        // `paramOffsets[i]` is the offset within the callee frame where evaluated[mapping[i]]
        // must be written. Pre-extracted from `fnBase.paramVars[i].ptr.offset` at translate time.
        @field:CompilationFinal(dimensions = 1) private val paramOffsets: IntArray,
        private val backend: Tf_Backend,
    ): Tf_FunctionCallNode() {

        @field:Child
        private var directCall: DirectCallNode? = null

        // Slow-path-only cache: populated lazily on the first call when `fastPath = false`.
        // The fast-path reads `frameDescriptor` / `defId` / `paramOffsets` straight from the
        // ctor-injected fields above, so these are dead code on the hot path.
        @field:CompilationFinal
        private var cachedFnBase: RR_FunctionBase? = null

        @field:CompilationFinal
        private var cachedFnName: String? = null

        /**
         * Lazy resolution of the [DirectCallNode]. Sidesteps a translator-time
         * chicken-and-egg: building `bench`'s body triggers translation of `is_prime` /
         * `collatz_steps` / `fib`, which in turn would try to resolve their own call targets
         * — and recursive functions like `fib` would loop. Resolving on first execute, behind
         * a `transferToInterpreterAndInvalidate()`, lets PE see a `@CompilationFinal` non-null
         * `DirectCallNode` on every subsequent call.
         *
         * [DirectCallNode.forceInlining] is requested up-front to push the runtime past the
         * default inlining cutoff for non-trivial chains. `traceTruffle` shows the bench's
         * `is_rule_violated → evaluate_int_variable_rule → variable_value` chain hitting
         * `Cutoff` (`Forced false`) at depth 3, where `variable_value` then routes through
         * `OptimizedCallTarget.callDirect → profileArguments → callBoundary` and the args
         * array allocated in [buildInnerCallArgs] / [buildInnerCallArgsIdentity] escapes
         * across the boundary. The hint is best-effort: the runtime still rejects forced
         * inlining for the recursion guard (`OptimizedCallTarget.callDirect`'s "Recursive"
         * early return) and the graph-size budget, so `fib`-style self-recursion is
         * unaffected.
         */
        private fun resolveCall(): DirectCallNode {
            val existing = directCall
            if (existing != null) return existing
            CompilerDirectives.transferToInterpreterAndInvalidate()
            val fn = backend.rrApp.allFunctions[fnDefIndex]
            // Slow path keeps a reference to the full RR_FunctionBase / fnName so
            // `setupCalleeFrameSlow` can hand `validateParams` / `setParams` what they need.
            // On the fast path these stay null and the field reads constant-fold to dead
            // weight that the inliner drops.
            if (!fastPath) {
                val fnBase = fn.fnBase
                cachedFnBase = fnBase
                cachedFnName = fnBase.defName.appLevelName
            }
            val node = DirectCallNode.create(backend.functionTarget(fn))
            node.forceInlining()
            directCall = insert(node)
            return node
        }

        @ExplodeLoop
        final override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = base?.execute(frame)
            // Reference equality — `Rt_NullValue` is a singleton object. `==` would compile
            // to `Intrinsics.areEqual`, whose contract (handle null receivers) drags in the
            // String/Throwable construction chain we're trying to keep out of PE.
            if (safe && baseValue === Rt_NullValue) return Rt_NullValue

            val call = resolveCall()
            val callArgs: Array<Any?> = if (fastPath && identityMapping) {
                // Identity + fast path: evaluate args directly into the inner-call slots.
                // Skips the per-call `Array<Rt_Value?>(args.size)` intermediate. paramOffsets.size
                // and args.size agree under identityMapping (translator invariant).
                // Arg-expression failures propagate without `callPos` wrapping — matches the
                // tree-walker, which never wraps caller-side arg evaluation (the arg expression
                // itself, e.g. a `ParameterDefaultValue`, owns its own stack-frame attribution).
                buildInnerCallArgsIdentity(frame)
            } else {
                val evaluated = Array(args.size) { args[it].execute(frame) }
                if (fastPath) {
                    buildInnerCallArgs(frame, evaluated)
                } else {
                    // Slow path crosses a `@TruffleBoundary` and needs an `Rt_CallFrame`
                    // anyway — extract it here so the boundary helper sees no `VirtualFrame`.
                    // Wrap only `validateParams` / `setupCalleeFrameSlow` (post-eval) with
                    // `callPos`, mirroring the tree-walker's `callTarget` catch around
                    // `validateParams` + `setParams`. Arg evaluation above stays unwrapped.
                    try {
                        buildOuterCallArgsSlow(tfRtFrame(frame), evaluated)
                    } catch (e: Rt_Exception) {
                        tfRethrowNested(tfRtFrame(frame), ErrorPos(callPos), e)
                    }
                }
            }

            return try {
                // Pass the args array to Truffle through the Java helper so the array becomes
                // `frame.arguments` directly (no spread copy on the way in). The callee root
                // returns the function's return value directly (or [Rt_UnitValue] when the
                // body falls through without an explicit `return`); reading from the
                // [TF_RETURN_VALUE_AUX_SLOT] of the callee frame would require materialising
                // it, so we channel the value through the call-target return slot instead.
                Tf_Unchecked.cast(Tf_Unchecked.callDirect(call, callArgs))
            } catch (e: Rt_Exception) {
                // Mirror the tree-walker's `Rt_InterpreterImpl.callTarget` wrapping: at each call
                // boundary, append the caller's `callPos` to the Rell-level stack via
                // `frame.error(.., nested = true)`. Without this, only the bottom-most frame survives
                // and `StackTraceTest`'s expected call chains collapse to a single entry.
                tfRethrowNested(tfRtFrame(frame), ErrorPos(callPos), e)
            }
        }

        /**
         * PE-visible inline arg packaging for the validated-at-translate-time path.
         *
         * No [TruffleBoundary]: every step here is straight-line Kotlin/JVM code that PE can
         * trace through. The translator already proved every arg's static type matches its
         * param's declared type and that no param carries a size constraint, so
         * `validateParams` / `setParams`'s type check are pure dead code at this site.
         *
         * Builds the inner-entry arguments shape `[null, callerRtFrame, arg1, …, argN]`:
         *
         * - `arguments[0] = null` — the inner-entry sentinel; the callee root's
         *   [net.postchain.rell.base.runtime.truffle.Tf_FunctionRootNode] dispatches on this.
         * - `arguments[1] = callerRtFrame` — the caller's [Rt_CallFrame], used only by
         *   [tfLazyAllocRtFrame] in the callee body if a slow-path node demands an
         *   [Rt_CallFrame]. The hot-path callee body never reads it.
         * - arguments[2 + i] = evaluated[mapping] — the i-th evaluated argument in
         *   callee-param order. The callee root writes each into the param's frame slot
         *   directly via `frame.setObject` — no [Rt_CallFrame] allocation.
         *
         * The mapping array is `@CompilationFinal` so PE unrolls the loop fully and inlines
         * each evaluated value into the callee's body argument graph. For a recursive
         * `is_prime(i)` call, this collapses (after PE) to a literal store of the caller's
         * `i` into the callee's param slot.
         *
         * The lazy [Rt_CallFrame] in the callee, if needed, references the caller's
         * [Rt_CallFrame] for `exeCtx` and `dbUpdateAllowed()` — so we cache `tfRtFrame(frame)`
         * here at translate-time-stable offsets. Reading from the caller's [VirtualFrame] aux
         * slot is one slot read which PE can fold to the actual frame pointer at the inlining.
         */
        @ExplodeLoop
        private fun buildInnerCallArgs(
            frame: VirtualFrame,
            evaluated: Array<Rt_Value>,
        ): Array<Any?> {
            val callArgs = arrayOfNulls<Any>(2 + paramOffsets.size)
            // arguments[0] stays null — the inner-entry sentinel.
            // arguments[1] = the outermost live caller's Rt_CallFrame, propagated via
            // [tfPropagateRtFrame] (no alloc, no boundary). On the hot path the callee never
            // touches it; on slow path it provides exeCtx + dbUpdateAllowed via
            // [tfLazyAllocRtFrame]. Using [tfRtFrame] here would eagerly trigger the
            // boundary-crossing lazy alloc for any caller whose own aux slot is empty (i.e.
            // every inner-entry caller), forcing the caller's [VirtualFrame] to materialise
            // and aborting tier-1 compilation with `SourceStackTraceBailoutException`.
            callArgs[1] = tfPropagateRtFrame(frame)
            for (i in paramOffsets.indices) {
                callArgs[2 + i] = evaluated[mapping[i]]
            }
            return callArgs
        }

        @ExplodeLoop
        private fun buildInnerCallArgsIdentity(frame: VirtualFrame): Array<Any?> {
            val callArgs = arrayOfNulls<Any>(2 + paramOffsets.size)
            callArgs[1] = tfPropagateRtFrame(frame)

            for (i in paramOffsets.indices) {
                callArgs[2 + i] = args[i].execute(frame)
            }

            return callArgs
        }

        @TruffleBoundary
        private fun buildOuterCallArgsSlow(
            caller: Rt_CallFrame,
            evaluated: Array<Rt_Value>,
        ): Array<Any?> {
            val mapped = if (identityMapping) {
                evaluated.asList()
            } else {
                buildList(mapping.size) {
                    for (i in mapping.indices) {
                        add(evaluated[mapping[i]])
                    }
                }
            }

            val fnBase: RR_FunctionBase = Tf_Unchecked.cast(cachedFnBase)
            val fnName: String = Tf_Unchecked.cast(cachedFnName)

            backend.delegate.validateParams(fnBase.params, mapped)
            val callee = backend.delegate.createFrame(
                caller.exeCtx,
                frameDescriptor,
                caller.dbUpdateAllowed(),
                defId,
            )
            backend.delegate.setParams(callee, fnBase.paramVars, mapped, fnName)
            return arrayOf(callee)
        }
    }

    /**
     * User-function call whose return type is statically integer. Overrides [executeLong]
     * to drop the default `(execute(frame) as Rt_IntValue).value` chain — `asInteger`'s `typeError`
     * branch dominates PE-traced graphs (~6.5K samples in profiling) even though it's
     * unreachable when the type system already proved the return type.
     */
    internal class UserFnInt(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        fnDefIndex: Int,
        fastPath: Boolean,
        frameDescriptor: RR_FrameDescriptor,
        defId: DefinitionId,
        paramOffsets: IntArray,
        backend: Tf_Backend,
    ): UserFn(
        base, args, mapping, safe, identityMapping, callPos, fnDefIndex,
        fastPath, frameDescriptor, defId, paramOffsets, backend,
    ) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** User-function call whose return type is statically boolean. See [UserFnInt]. */
    internal class UserFnBool(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        fnDefIndex: Int,
        fastPath: Boolean,
        frameDescriptor: RR_FrameDescriptor,
        defId: DefinitionId,
        paramOffsets: IntArray,
        backend: Tf_Backend,
    ): UserFn(
        base, args, mapping, safe, identityMapping, callPos, fnDefIndex,
        fastPath, frameDescriptor, defId, paramOffsets, backend,
    ) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

    /**
     * Generic call site for non-user-function targets (sys-fn, operation, native, partial,
     * function-value, abstract-override, extendable). Dispatches via [Tf_Backend.callTarget],
     * which routes to the wrapped impl. Specialising any of these would mostly mirror
     * existing impl helpers without giving Graal a smaller shape to inline.
     */
    internal open class Generic(
        @field:Child private var base: Tf_ExprNode?,
        @field:Children private val args: Array<Tf_ExprNode>,
        @field:CompilationFinal private val target: RR_FunctionCallTarget,
        @field:CompilationFinal(dimensions = 1) private val mapping: IntArray,
        @field:CompilationFinal private val safe: Boolean,
        @field:CompilationFinal private val identityMapping: Boolean,
        @field:CompilationFinal private val callPos: FilePos,
        private val backend: Tf_Backend,
    ): Tf_FunctionCallNode() {

        @ExplodeLoop
        final override fun execute(frame: VirtualFrame): Rt_Value {
            val baseValue = base?.execute(frame)
            if (safe && baseValue === Rt_NullValue) return Rt_NullValue

            val evaluated = Array(args.size) { args[it].execute(frame) }

            val mapped: List<Rt_Value> = if (identityMapping) {
                Tf_Unchecked.cast<Array<Rt_Value>>(evaluated).asList()
            } else {
                buildList(mapping.size) {
                    for (i in mapping) {
                        add(evaluated[i])
                    }
                }
            }
            return backend.callTarget(target, baseValue, mapped, tfRtFrame(frame), callPos)
        }
    }

    /** Sys-/native-/op-call whose return type is statically integer. See [UserFnInt]. */
    internal class GenericInt(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        target: RR_FunctionCallTarget,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        backend: Tf_Backend,
    ): Generic(base, args, target, mapping, safe, identityMapping, callPos, backend) {
        override fun executeLong(frame: VirtualFrame): Long =
            Tf_Unchecked.cast<Rt_IntValue>(execute(frame)).value
    }

    /** Sys-/native-/op-call whose return type is statically boolean. See [UserFnInt]. */
    internal class GenericBool(
        base: Tf_ExprNode?,
        args: Array<Tf_ExprNode>,
        target: RR_FunctionCallTarget,
        mapping: IntArray,
        safe: Boolean,
        identityMapping: Boolean,
        callPos: FilePos,
        backend: Tf_Backend,
    ): Generic(base, args, target, mapping, safe, identityMapping, callPos, backend) {
        override fun executeBoolean(frame: VirtualFrame): Boolean =
            Tf_Unchecked.cast<Rt_BooleanValue>(execute(frame)).value
    }

    companion object {
        /**
         * Pick the typed [Generic] subclass matching the static return type, or the untyped
         * [Generic] when the result is neither integer nor boolean. Used by the translator's
         * sys-fn dispatch fallback path so the result-type specialisation logic stays in one
         * place.
         */
        fun makeGeneric(
            base: Tf_ExprNode?,
            args: Array<Tf_ExprNode>,
            target: RR_FunctionCallTarget,
            mapping: IntArray,
            safe: Boolean,
            identityMapping: Boolean,
            callPos: FilePos,
            backend: Tf_Backend,
            resultIsInt: Boolean,
            resultIsBool: Boolean,
        ): Tf_ExprNode = when {
            resultIsInt -> GenericInt(base, args, target, mapping, safe, identityMapping, callPos, backend)
            resultIsBool -> GenericBool(base, args, target, mapping, safe, identityMapping, callPos, backend)
            else -> Generic(base, args, target, mapping, safe, identityMapping, callPos, backend)
        }
    }
}

/**
 * Native: struct construction (`RR_Expr.StructCreate`).
 *
 * Each `RR_Expr.StructCreate`'s `attrs` provides an attribute index plus an expression. The
 * declared attribute list is fixed at compile time, so we capture the per-position write order
 * (`attrIndices`) and the expression nodes parallel to it. Missing positions stay null, which
 * the tree-walker materialises as `Rt_NullValue` — we do the same.
 *
 * Per-attribute size constraints (`@max_size`, etc.) are checked through the wrapped impl;
 * this stays reference-correct without re-implementing the constraint dispatch tree here.
 * Constraints are translator-time visible: when none of the struct's attributes carries one we
 * pin [hasSizeConstraints] to `false` and skip the temporary value array entirely — each
 * attribute expression evaluates straight into the SOM instance (or the heap-struct array on
 * the fallback path). When at least one attribute has a constraint we keep the two-pass shape:
 * evaluate everything into a temporary array, run the constraint checks (which need the full
 * value set since constraints can reference sibling attributes via the wrapped impl), then
 * scatter into final storage.
 *
 * Storage: when [shape] is non-null, materialises a SOM-generated [Tf_DynStruct] instance and
 * writes attributes through SOM property handles — no `ArrayList` / `Object[]` indirection per
 * struct, and Graal's escape analysis can virtualise the allocation when the struct doesn't
 * escape its frame. Primitive `integer` / `boolean` attributes write through typed
 * `setLong` / `setBoolean` slots when the underlying expression node provides
 * `executeLong` / `executeBoolean`, eliminating the [Rt_IntValue] / [Rt_BooleanValue] box on
 * the create path.
 *
 * When SOM is unavailable in the current runtime (the polyglot registration hasn't been wired
 * up), [shape] is null and construction falls back to the canonical [Rt_HeapStruct] path via
 * `Rt_StructValue`'s companion `invoke` operator.
 */
internal class Tf_StructCreateNode(
    @field:CompilationFinal private val structDefIndex: Int,
    @field:CompilationFinal private val rtType: Rt_ValueClass<*>,
    @field:CompilationFinal(dimensions = 1) private val attrIndices: IntArray,
    @field:Children private val attrExprs: Array<Tf_ExprNode>,
    @field:CompilationFinal private val attrCount: Int,
    @field:CompilationFinal(dimensions = 1) private val attrNames: Array<String>,
    @field:CompilationFinal private val shape: Tf_StructShape?,
    private val backend: Tf_Backend,
): Tf_ExprNode() {
    /**
     * Translator-time check: any attribute carries an `RR_SizeConstraint`. When `false` the
     * temp `Array<Rt_Value?>` is bypassed — every attribute expression evaluates straight into
     * its destination slot (SOM property or heap-struct array element). The struct's attribute
     * list never changes after compilation so this is safe to pin `@CompilationFinal`.
     */
    @field:CompilationFinal
    private val hasSizeConstraints: Boolean = run {
        val attrs = backend.rrApp.allStructs[structDefIndex].struct.attributesList
        attrs.any { it.sizeConstraint != null }
    }

    /**
     * Cached SOM factory. Pinned `@CompilationFinal` and pulled out of [shape] up front so PE
     * folds the `factory.create(...)` call to a direct constructor invocation rather than a
     * virtual `getFactory()` / `factory.create` lookup chain on every iteration.
     */
    @field:CompilationFinal
    private val somFactory: Tf_StructFactory? = shape?.factory

    override fun execute(frame: VirtualFrame): Rt_Value {
        val somShape = shape
        val factory = somFactory
        if (somShape == null || factory == null) {
            return executeHeapFallback(frame)
        }
        return if (hasSizeConstraints) {
            executeSomWithConstraints(frame, somShape, factory)
        } else {
            executeSomNoConstraints(frame, somShape, factory)
        }
    }

    /**
     * SOM fast path with no size constraints — single pass, no temp array. Each attribute
     * expression evaluates straight into the corresponding SOM slot via typed setters when
     * the slot kind matches the expected primitive shape. The R_StructExpr invariant
     * (`R_StructExpr.init`) guarantees `attrIndices` covers every attribute, so no
     * fill-unwritten-slots pass is needed here.
     */
    @ExplodeLoop
    private fun executeSomNoConstraints(
        frame: VirtualFrame,
        somShape: Tf_StructShape,
        factory: Tf_StructFactory,
    ): Rt_Value {
        val instance = factory.create(somShape.rrType, rtType, somShape)

        for (i in attrIndices.indices) {
            writeAttr(frame, somShape, instance, attrIndices[i], attrExprs[i])
        }

        return instance
    }

    /**
     * SOM path with size constraints: evaluate everything into a temp array, run constraint
     * checks (which may inspect sibling values), then scatter into the SOM instance.
     */
    @ExplodeLoop
    private fun executeSomWithConstraints(
        frame: VirtualFrame,
        somShape: Tf_StructShape,
        factory: Tf_StructFactory,
    ): Rt_Value {
        val attrValues = arrayOfNulls<Rt_Value>(attrCount)

        for (i in attrIndices.indices) {
            attrValues[attrIndices[i]] = attrExprs[i].execute(frame)
        }

        runSizeConstraints(attrValues)

        val instance = factory.create(somShape.rrType, rtType, somShape)

        for (i in 0..<attrCount) {
            scatterValue(somShape, instance, i, attrValues[i] ?: Rt_NullValue)
        }

        return instance
    }

    @ExplodeLoop
    private fun executeHeapFallback(frame: VirtualFrame): Rt_Value {
        val attrValues = arrayOfNulls<Rt_Value>(attrCount)

        for (i in attrIndices.indices) {
            attrValues[attrIndices[i]] = attrExprs[i].execute(frame)
        }

        if (hasSizeConstraints) runSizeConstraints(attrValues)

        val values = ArrayList<Rt_Value>(attrCount)

        for (i in 0..<attrCount) {
            values += attrValues[i] ?: Rt_NullValue
        }

        return Rt_StructValue(rtType, attrNames.asList(), values)
    }

    private fun runSizeConstraints(attrValues: Array<Rt_Value?>) {
        val structDef = backend.rrApp.allStructs[structDefIndex]
        for (i in 0..<attrCount) {
            structDef.struct.attributesList[i].sizeConstraint?.let {
                backend.delegate.checkSizeConstraint(it, attrValues[i] ?: Rt_NullValue)
            }
        }
    }

    /**
     * Direct write: pick the typed primitive write when the slot kind is `long` / `boolean` and
     * the underlying expression node has a typed override (every primitive-typed expression in
     * the runtime overrides `executeLong` / `executeBoolean`), else fall back to a boxed write.
     * The default `executeLong` / `executeBoolean` on `Tf_ExprNode` boxes-then-unboxes, which
     * preserves correctness even if the translator hasn't picked a typed subclass for the
     * particular expression.
     */
    private fun writeAttr(
        frame: VirtualFrame,
        somShape: Tf_StructShape,
        instance: Tf_DynStruct,
        slotIdx: Int,
        attrExpr: Tf_ExprNode,
    ) {
        val prop = somShape.properties[slotIdx]
        when (somShape.slotKindAt(slotIdx)) {
            TF_SLOT_LONG -> prop.setLong(instance, attrExpr.executeLong(frame))
            TF_SLOT_BOOLEAN -> prop.setBoolean(instance, attrExpr.executeBoolean(frame))
            else -> prop.setObject(instance, attrExpr.execute(frame))
        }
    }

    /** Slot-kind-aware scatter for the post-constraint path; takes a pre-evaluated `Rt_Value`. */
    private fun scatterValue(somShape: Tf_StructShape, instance: Tf_DynStruct, slotIdx: Int, value: Rt_Value) {
        val prop = somShape.properties[slotIdx]
        when (somShape.slotKindAt(slotIdx)) {
            TF_SLOT_LONG -> prop.setLong(instance, (value as Rt_IntValue).value)
            TF_SLOT_BOOLEAN -> prop.setBoolean(instance, (value as Rt_BooleanValue).value)
            else -> prop.setObject(instance, value)
        }
    }

}

/**
 * Native: shared chooser for `when` expressions and statements. Returns the matched branch
 * index, or `elseIndex` (which may be -1) when no branch matched.
 *
 * Two variants:
 *   - [Iterative] — sequential equality scan over evaluated condition expressions
 *   - [Lookup] — pre-computed constant key list, scanned for value equality
 *
 * Both dispatch shapes are common enough to warrant separate sealed implementations rather than
 * a runtime mode field; this lets PE specialise each chooser site cleanly.
 */
internal sealed class Tf_WhenChooserNode: Tf_ExprNode() {
    /** Returns the matched branch index, or -1 when no branch matched and there is no else. */
    abstract fun chooseIndex(frame: VirtualFrame): Int

    /** Required by [Tf_ExprNode]; bridge unused — chooser is never executed as an expression. */
    final override fun execute(frame: VirtualFrame): Rt_Value =
        error("Tf_WhenChooserNode.execute: use chooseIndex instead")

    internal class Iterative(
        @field:Child private var keyExpr: Tf_ExprNode,
        @field:Children private val condExprs: Array<Tf_ExprNode>,
        @field:CompilationFinal(dimensions = 1) private val condIndices: IntArray,
        @field:CompilationFinal private val elseIndex: Int,
    ): Tf_WhenChooserNode() {
        @ExplodeLoop
        override fun chooseIndex(frame: VirtualFrame): Int {
            val key = keyExpr.execute(frame)

            for (idx in condExprs.indices) {
                // Use `equals` over `==` to avoid `Intrinsics.areEqual` on the Truffle hot path.
                // `key` is non-null per the `Tf_ExprNode.execute` contract.
                if (key.equals(condExprs[idx].execute(frame)))
                    return condIndices[idx]
            }

            return elseIndex
        }
    }

    internal class Lookup(
        @field:Child private var keyExpr: Tf_ExprNode,
        @field:CompilationFinal(dimensions = 1) private val keys: Array<Rt_Value>,
        @field:CompilationFinal(dimensions = 1) private val values: IntArray,
        @field:CompilationFinal private val elseIndex: Int,
    ): Tf_WhenChooserNode() {
        @ExplodeLoop
        override fun chooseIndex(frame: VirtualFrame): Int {
            val key = keyExpr.execute(frame)
            for (i in keys.indices) {
                // Use `equals` over `==` to avoid `Intrinsics.areEqual` on the Truffle hot path.
                // `keys[i]` originates from `delegate.toRtValue(...)` at translate time, non-null.
                if (keys[i].equals(key)) return values[i]
            }
            return elseIndex
        }
    }
}

/**
 * Native: `when` expression. Picks an index via the chooser, then evaluates the matched
 * branch. The compiler guarantees the chooser returns a valid index for expression-shaped
 * `when` (else-branch always present), so no defensive -1 handling is needed here.
 *
 * Typed subclasses ([IntWhen]/[BoolWhen]) chain through each branch's typed
 * `executeLong`/`executeBoolean` so the entire `when` stays primitive when the result type
 * is statically integer or boolean.
 */
internal open class Tf_WhenExprNode(
    @field:Child private var chooser: Tf_WhenChooserNode,
    @field:Children private val branches: Array<Tf_ExprNode>,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        val idx = chooser.chooseIndex(frame)
        check(idx >= 0) { "when expression: no matching branch and no else" }
        return branches[idx].execute(frame)
    }

    internal class IntWhen(
        @field:Child private var chooser: Tf_WhenChooserNode,
        @field:Children private val branches: Array<Tf_ExprNode>,
    ): Tf_ExprNode() {
        override fun executeLong(frame: VirtualFrame): Long {
            val idx = chooser.chooseIndex(frame)
            check(idx >= 0) { "when expression: no matching branch and no else" }
            return branches[idx].executeLong(frame)
        }

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_IntValue.get(executeLong(frame))
    }

    internal class BoolWhen(
        @field:Child private var chooser: Tf_WhenChooserNode,
        @field:Children private val branches: Array<Tf_ExprNode>,
    ): Tf_ExprNode() {
        override fun executeBoolean(frame: VirtualFrame): Boolean {
            val idx = chooser.chooseIndex(frame)
            check(idx >= 0) { "when expression: no matching branch and no else" }
            return branches[idx].executeBoolean(frame)
        }

        override fun execute(frame: VirtualFrame): Rt_Value = Rt_BooleanValue.get(executeBoolean(frame))
    }
}
