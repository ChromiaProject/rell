/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.R_VirtualStructType

class Rt_VirtualStructValue(
    gtv: Gtv,
    override val type: Rt_ValueClass<*>,
    /** Inner non-virtual struct's runtime type, used by [toFull0]. */
    private val innerStructRtType: Rt_ValueClass<*>,
    private val structName: String,
    private val attrNames: List<String>,
    private val attributes: List<Rt_Value?>,
): Rt_VirtualValue(gtv) {

    override val name
        get() = Companion.name

    override fun equals(other: Any?) =
        other === this || (other is Rt_VirtualStructValue && attributes == other.attributes)

    // Hash by type.name for cross-construction-route consistency. See Rt_StructValue.hashCode.
    override fun hashCode() = type.name.hashCode() * 31 + attributes.hashCode()

    override fun str(format: Rt_StrFormat): String =
        Rt_StructValue.formatStr(this, type.name, attrNames, attributes, format)

    override fun strCode(showTupleFieldNames: Boolean): String =
        Rt_StructValue.formatStrCode(this, type.name, attrNames, attributes)

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

    companion object: Rt_ValueClass<Rt_VirtualStructValue> {
        override val name
            get() = "virtual_struct"
        fun gtvConversion(type: R_VirtualStructType): Rt_GtvCompatibleValueClass<*> = gtvConversionOf { ctx, gtv ->
            decodeVirtualStruct(ctx, type, deserializeVirtual(ctx, gtv))
        }
    }
}
