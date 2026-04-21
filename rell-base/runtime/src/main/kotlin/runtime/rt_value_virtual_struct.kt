/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv

class Rt_VirtualStructValue(
    gtv: Gtv,
    private val rtType: Rt_Type,
    /** Inner non-virtual struct's runtime type, used by [toFull0]. */
    private val innerStructRtType: Rt_Type,
    private val structName: String,
    private val attrNames: List<String>,
    private val attributes: List<Rt_Value?>,
): Rt_VirtualValue(gtv) {

    override val valueType = Rt_CoreValueTypes.VIRTUAL_STRUCT.type()

    override fun type() = rtType
    override fun asVirtualStruct() = this
    override fun equals(other: Any?) =
        other === this || (other is Rt_VirtualStructValue && attributes == other.attributes)

    override fun hashCode() = rtType.hashCode() * 31 + attributes.hashCode()

    override fun str(format: StrFormat): String =
        Rt_StructValue.formatStr(this, rtType.name, attrNames, attributes, format)

    override fun strCode(showTupleFieldNames: Boolean): String =
        Rt_StructValue.formatStrCode(this, rtType.name, attrNames, attributes)

    fun get(index: Int): Rt_Value {
        val value = attributes[index]
        if (value == null) {
            val attr = attrNames[index]
            throw Rt_Exception.common(
                "virtual_struct:get:novalue:$structName:$attr",
                "Attribute '$structName.$attr' has no value",
            )
        }
        return value
    }

    override fun toFull0(): Rt_Value {
        val fullAttrValues = attributes.map { toFull(it!!) }.toMutableList()
        return Rt_StructValue(innerStructRtType, attrNames, fullAttrValues)
    }
}
