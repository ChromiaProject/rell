/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
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
        type("range", rType = R_RangeType) {
            parent(type = "iterable<integer>")

            constructor(pure = true) {
                param("end", "integer")
                body { a ->
                    calcRange(0, a.asInteger(), 1)
                }
            }

            constructor(pure = true) {
                param("start", "integer")
                param("end", "integer")
                param("step", "integer", arity = L_ParamArity.ZERO_ONE)
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
    override fun toFormatArg() = str()
    override fun str(format: StrFormat) = "range($start,$end,$step)"
    override fun strCode(showTupleFieldNames: Boolean) = "range[$start,$end,$step]"

    override fun asIterable(): Iterable<Rt_Value> = this
    override fun iterator(): Iterator<Rt_Value> = RangeIterator(this)

    override fun equals(other: Any?) = other is Rt_RangeValue && start == other.start && end == other.end && step == other.step
    override fun hashCode() = Objects.hash(start, end, step)

    fun contains(v: Long): Boolean {
        if (step > 0) {
            if (v < start || v >= end) return false
        } else {
            check(step < 0)
            if (v > start || v <= end) return false
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

            override fun hasNext(): Boolean {
                if (range.step > 0) {
                    return current < range.end
                } else {
                    return current > range.end
                }
            }

            override fun next(): Rt_Value {
                val res = current
                current = LongMath.saturatedAdd(current, range.step)
                return Rt_IntValue.get(res)
            }
        }
    }
}
