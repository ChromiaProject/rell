/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.R_VirtualSetType

class Rt_VirtualSetValue(
    gtv: Gtv,
    override val type: Rt_ValueClass<*>,
    private val innerSetRtType: Rt_ValueClass<*>,
    internal val elements: Set<Rt_Value>,
): Rt_VirtualCollectionValue(gtv), Rt_IterableValue {
    override fun iterator(): Iterator<Rt_Value> = elements.iterator()

    override val name
        get() = Companion.name

    override fun strCode(showTupleFieldNames: Boolean) = Rt_SetValue.strCode(type, elements, showTupleFieldNames)
    override fun str(format: Rt_StrFormat): String = elements.joinToString(", ", "[", "]") { it.str(format) }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualSetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it) }.toMutableSet()
        return Rt_SetValue(innerSetRtType, resElements)
    }

    override fun size() = elements.size

    fun contains(value: Rt_Value) = elements.contains(value)

    companion object: Rt_ValueClass<Rt_VirtualSetValue> {
        override val name
            get() = "virtual_set"

        override val klass = Rt_VirtualSetValue::class

        fun gtvConversion(type: R_VirtualSetType): Rt_GtvCompatibleValueClass<*> = gtvConversionOf { ctx, gtv ->
            decodeVirtualSet(ctx, type, deserializeVirtual(ctx, gtv))
        }
    }
}
