/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.google.common.math.LongMath
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import java.util.*

object Lib_Type_Range {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("range", rType = R_RangeType, since = "0.6.0") {
            comment("""
                A range of integer values. Ranges represent arithmetic sequences with defined start and end points, and
                a constant difference between consecutive elements. Ranges can be empty or contain any natural number of
                elements.

                Range is a subtype of `iterable<integer>`.
                @see 1. <a href="../iterable/index.html"><code>iterable</code> - Rell Standard Library</a>
            """)
            parent(type = "iterable<integer>")

            constructor(pure = true, since = "0.6.0") {
                comment("""
                    Construct a range, starting at `0` (inclusive), ending at `end` (exclusive), with a step size of `1`.

                    Examples:

                    - `list(range(0))` returns `[]`.
                    - `list(range(1))` returns `[0]`.
                    - `list(range(2))` returns `[0, 1]`.
                    - `list(range(10))` returns `[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]`.

                    Note that `range(x)` is equivalent to `range(0, x, 1)`.
                    @throws exception if `end < 0`
                """)
                param("end", "integer", comment = "end value (exclusive) for this range")
                body { a ->
                    calcRange(0, a.asInteger(), 1)
                }
            }

            constructor(pure = true, since = "0.6.0") {
                comment("""
                    Construct a range, starting at `start` (inclusive), ending at `end` (exclusive), with a "step size"
                    (i.e. difference between consecutive values) of `step`.

                    If `start > end`, then `step` must be negative. Conversely, if `start < end`, the `step` must be
                    positive. `step` cannot be `0`.

                    Examples:

                    - `list(range(1, 23, 3))` returns `[1, 4, 7, 10, 13, 16, 19, 22]`.
                    - `list(range(20, 1, -2))` returns `[20, 18, 16, 14, 12, 10, 8, 6, 4, 2]`.
                    - `list(range(-2, -8, -1))` returns `[-2, -3, -4, -5, -6, -7]`.
                    - `list(range(3, 3, 7))` returns `[]`. Indeed, for all `x`, and for all `y != 0`, `range(x, x, y)`
                    returns `[]`.

                    Note that `range(0, x, 1)` is equivalent to `range(x)`.
                    @throws exception when:
                    - `step == 0`
                    - `start > end` and `step > 0`
                    - `start < end` and `step < 0`
                """)
                param("start", "integer", comment = "start value for this range (inclusive)")
                param("end", "integer", comment = "end value for this range (exclusive)")
                param("step", "integer", arity = L_ParamArity.ZERO_ONE, comment = "step size for this range")
                bodyOpt2 { a, b, c ->
                    calcRange(a.asInteger(), b.asInteger(), c?.asInteger() ?: 1)
                }
            }
        }
    }

    private fun calcRange(start: Long, end: Long, step: Long): Rt_Value {
        if (step == 0L || (step > 0 && start > end) || (step < 0 && start < end)) {
            throw Rt_Exception.common("fn_range_args:$start:$end:$step",
                "Invalid range: start = $start, end = $end, step = $step")
        }
        return Rt_RangeValue(start, end, step)
    }
}

object R_RangeType: R_PrimitiveType("range") {
    override fun isDirectVirtualable() = false
    override fun isDirectPure() = true
    override fun isReference() = true
    override fun comparator() = Rt_Comparator.create { it.asRange() }
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_Rell.RANGE_TYPE
}

class Rt_RangeValue(val start: Long, val end: Long, val step: Long): Rt_Value(), Iterable<Rt_Value>, Comparable<Rt_RangeValue> {
    override val valueType = Rt_CoreValueTypes.RANGE.type()

    override fun type() = R_RangeType
    override fun asRange() = this
    override fun str(format: StrFormat) = "range($start,$end,$step)"
    override fun strCode(showTupleFieldNames: Boolean) = "range[$start,$end,$step]"

    override fun asIterable(): Iterable<Rt_Value> = this
    override fun iterator(): Iterator<Rt_Value> = RangeIterator(this)

    override fun equals(other: Any?) = other is Rt_RangeValue && start == other.start && end == other.end && step == other.step
    override fun hashCode() = Objects.hash(start, end, step)

    fun contains(v: Long): Boolean {
        if (step > 0) {
            if (v !in start..<end) return false
        } else {
            check(step < 0)
            if (v !in (end + 1)..start) return false
        }
        val m1 = valueMod(start, step)
        val m2 = valueMod(v, step)
        return m1 == m2
    }

    override fun compareTo(other: Rt_RangeValue): Int {
        var c = start.compareTo(other.start)
        if (c == 0) c = end.compareTo(other.end)
        if (c == 0) c = step.compareTo(other.step)
        return c
    }

    companion object {
        private fun valueMod(v: Long, m: Long): Long {
            val r = v % m
            if (r >= 0) return r
            return if (m > 0) r + m else r - m
        }

        private class RangeIterator(private val range: Rt_RangeValue): Iterator<Rt_Value> {
            private var current = range.start

            override fun hasNext(): Boolean = if (range.step > 0) {
                current < range.end
            } else {
                current > range.end
            }

            override fun next(): Rt_Value {
                val res = current
                current = LongMath.saturatedAdd(current, range.step)
                return Rt_IntValue.get(res)
            }
        }
    }
}
