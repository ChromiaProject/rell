/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.R_VirtualTupleType

class Rt_VirtualTupleValue(
    gtv: Gtv,
    override val type: Rt_ValueClass<*>,
    private val innerTupleRtType: Rt_ValueClass<*>,
    private val fieldNames: List<String?>,
    private val elements: List<Rt_Value?>,
): Rt_VirtualValue(gtv) {

    override val name
        get() = Companion.name

    override fun equals(other: Any?) = other === this || (other is Rt_VirtualTupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun str(format: Rt_StrFormat): String {
        return "virtual(${
            elements.indices.joinToString(",") { i ->
                val v = elements[i]?.str(format) ?: "null"
                val n = fieldNames[i]
                if (n == null) v else "$n=$v"
            }
        })"
    }

    override fun strCode(showTupleFieldNames: Boolean): String = "virtual(${
        elements.indices.joinToString(",") { i ->
            val v = elements[i]?.strCode() ?: "null"
            val n = fieldNames[i]
            if (n == null || !showTupleFieldNames) v else "$n=$v"
        }
    })"

    override fun toFull0(): Rt_Value = Rt_TupleValue(innerTupleRtType, elements.map { toFull(it!!) })

    fun get(index: Int): Rt_Value {
        val value = elements[index]
        if (value == null) {
            val attr = fieldNames[index] ?: "$index"
            throw Rt_Exception.common("virtual_tuple:get:novalue:$attr", "Field '$attr' has no value")
        }
        return value
    }

    companion object: Rt_ValueClass<Rt_VirtualTupleValue> {
        override val name
            get() = "virtual_tuple"

        override val klass = Rt_VirtualTupleValue::class

        fun gtvConversion(type: R_VirtualTupleType): Rt_GtvCompatibleValueClass<*> = gtvConversionOf { ctx, gtv ->
            decodeVirtualTuple(ctx, type, deserializeVirtual(ctx, gtv))
        }
    }
}
