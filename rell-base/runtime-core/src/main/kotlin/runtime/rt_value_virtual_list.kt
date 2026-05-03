/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.R_VirtualListType

class Rt_VirtualListValue(
    gtv: Gtv,
    override val type: Rt_ValueClass<*>,
    /** Inner non-virtual list's runtime type, used by [toFull0]. */
    private val innerListRtType: Rt_ValueClass<*>,
    internal val elements: List<Rt_Value?>,
): Rt_VirtualCollectionValue(gtv), Rt_IterableValue {
    override fun iterator(): Iterator<Rt_Value> = elements.filterNotNull().iterator()

    override val name
        get() = Companion.name

    override fun strCode(showTupleFieldNames: Boolean) = Rt_ListValue.strCode(type, elements)
    override fun str(format: Rt_StrFormat) = elements.joinToString(", ", "[", "]") { it?.str(format) ?: "null" }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualListValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }.toMutableList()
        return Rt_ListValue(innerListRtType, resElements)
    }

    override fun size() = elements.size

    fun contains(index: Long) = index >= 0 && index < elements.size && elements[index.toInt()] != null

    fun get(index: Long): Rt_Value {
        Rt_ListValue.checkIndex(elements.size, index)
        val value = elements[index.toInt()] ?: throw Rt_Exception.common(
            "virtual_list:get:novalue:$index",
            "Element $index has no value",
        )
        return value
    }

    companion object: Rt_ValueClass<Rt_VirtualListValue> {
        override val name
            get() = "virtual_list"

        override val klass = Rt_VirtualListValue::class

        fun gtvConversion(type: R_VirtualListType): Rt_GtvCompatibleValueClass<*> = gtvConversionOf { ctx, gtv ->
            decodeVirtualList(ctx, type, deserializeVirtual(ctx, gtv))
        }
    }
}
