/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.Rt_SetValue

class Rt_VirtualSetValue(
    gtv: Gtv,
    private val rtType: Rt_Type,
    private val innerSetRtType: Rt_Type,
    private val elements: Set<Rt_Value>,
): Rt_VirtualCollectionValue(gtv) {

    override val valueType = Rt_CoreValueTypes.VIRTUAL_SET.type()

    override fun type() = rtType
    override fun asVirtualCollection() = this
    override fun asVirtualSet() = this
    override fun strCode(showTupleFieldNames: Boolean) = Rt_SetValue.strCode(rtType, elements, showTupleFieldNames)
    override fun str(format: StrFormat): String = elements.joinToString(", ", "[", "]") { it.str(format) }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualSetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it) }.toMutableSet()
        return Rt_SetValue(innerSetRtType, resElements)
    }

    override fun size() = elements.size
    override fun asIterable() = elements

    fun contains(value: Rt_Value) = elements.contains(value)
}
