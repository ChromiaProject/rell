/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv

class Rt_VirtualTupleValue(
    gtv: Gtv,
    private val rtType: Rt_Type,
    private val innerTupleRtType: Rt_Type,
    private val fieldNames: List<String?>,
    private val elements: List<Rt_Value?>,
): Rt_VirtualValue(gtv) {

    override val valueType = Rt_CoreValueTypes.VIRTUAL_TUPLE.type()

    override fun type() = rtType
    override fun asVirtualTuple() = this
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualTupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun str(format: StrFormat): String {
        return "virtual(${
            elements.indices.joinToString(",") { i ->
                val v = elements[i]?.str(format) ?: "null"
                val n = fieldNames[i]
                if (n == null) v else "$n=$v"
            }
        })"
    }

    override fun strCode(showTupleFieldNames: Boolean): String {
        return "virtual(${
            elements.indices.joinToString(",") { i ->
                val v = elements[i]?.strCode() ?: "null"
                val n = fieldNames[i]
                if (n == null || !showTupleFieldNames) v else "$n=$v"
            }
        })"
    }

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }
        return Rt_TupleValue(innerTupleRtType, resElements)
    }

    fun get(index: Int): Rt_Value {
        val value = elements[index]
        if (value == null) {
            val attr = fieldNames[index] ?: "$index"
            throw Rt_Exception.common("virtual_tuple:get:novalue:$attr", "Field '$attr' has no value")
        }
        return value
    }
}
