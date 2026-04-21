/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.Rt_ListValue

class Rt_VirtualListValue(
    gtv: Gtv,
    private val rtType: Rt_Type,
    /** Inner non-virtual list's runtime type, used by [toFull0]. */
    private val innerListRtType: Rt_Type,
    private val elements: List<Rt_Value?>,
): Rt_VirtualCollectionValue(gtv) {

    override val valueType = Rt_CoreValueTypes.VIRTUAL_LIST.type()

    override fun type() = rtType
    override fun asVirtualCollection() = this
    override fun asVirtualList() = this
    override fun strCode(showTupleFieldNames: Boolean) = Rt_ListValue.strCode(rtType, elements)
    override fun str(format: StrFormat) = elements.joinToString(", ", "[", "]") { it?.str(format) ?: "null" }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualListValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }.toMutableList()
        return Rt_ListValue(innerListRtType, resElements)
    }

    override fun size() = elements.size
    override fun asIterable() = elements.filterNotNull()

    fun contains(index: Long) = index >= 0 && index < elements.size && elements[index.toInt()] != null

    fun get(index: Long): Rt_Value {
        Rt_ListValue.checkIndex(elements.size, index)
        val value = elements[index.toInt()] ?: throw Rt_Exception.common(
            "virtual_list:get:novalue:$index",
            "Element $index has no value",
        )
        return value
    }
}
