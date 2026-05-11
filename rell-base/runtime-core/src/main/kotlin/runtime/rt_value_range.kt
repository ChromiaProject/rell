/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import com.google.common.math.LongMath
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type

@JvmRecord
data class Rt_RangeValue(val start: Long, val end: Long, val step: Long):
    Rt_Value, Iterable<Rt_Value>, Rt_IterableValue, Comparable<Rt_RangeValue> {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.RANGE

    override fun str(format: Rt_StrFormat) = "range($start,$end,$step)"
    override fun strCode(showTupleFieldNames: Boolean) = "range[$start,$end,$step]"

    override fun iterator(): Iterator<Rt_Value> = RangeIterator(this)

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

    companion object: Rt_ValueClass<Rt_RangeValue> {
        override val name
            get() = "range"
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.RANGE)

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> (a as Rt_RangeValue).compareTo((b as Rt_RangeValue)) }

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
