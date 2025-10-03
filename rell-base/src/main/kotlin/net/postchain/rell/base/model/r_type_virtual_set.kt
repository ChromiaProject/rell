/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.GtvRtConversion_Set
import net.postchain.rell.base.lib.type.R_SetType
import net.postchain.rell.base.lib.type.Rt_SetValue
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvToRtContext
import net.postchain.rell.base.runtime.Rt_CoreValueTypes
import net.postchain.rell.base.runtime.Rt_Value

class R_VirtualSetType(val innerType: R_SetType): R_VirtualCollectionType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualSetType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_VirtualSet(this)
    override fun typeDef() = Lib_Rell.VIRTUAL_SET_TYPE

    companion object {
        internal val META = R_TypeMeta.make { t, _ ->
            R_SetType(t).virtualType
        }
    }
}

class Rt_VirtualSetValue(
        gtv: Gtv,
        private val type: R_VirtualSetType,
        private val elements: Set<Rt_Value>
): Rt_VirtualCollectionValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_SET.type()

    override fun type() = type
    override fun asVirtualCollection() = this
    override fun asVirtualSet() = this
    override fun toFormatArg() = elements
    override fun strCode(showTupleFieldNames: Boolean) = Rt_SetValue.strCode(type, elements, showTupleFieldNames)
    override fun str(format: StrFormat): String = elements.joinToString(", ", "[", "]") { it.str(format) }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualSetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it) }.toMutableSet()
        return Rt_SetValue(type.innerType, resElements)
    }

    override fun size() = elements.size
    override fun asIterable() = elements

    fun contains(value: Rt_Value) = elements.contains(value)
}

class GtvRtConversion_VirtualSet(private val type: R_VirtualSetType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualSet(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualSet(ctx: GtvToRtContext, type: R_VirtualSetType, v: Gtv): Rt_Value {
            val rtList = GtvRtConversion_VirtualList.decodeVirtualElements(ctx, type.innerType, v)
            val rtSet = GtvRtConversion_Set.listToSet(ctx, rtList.filterNotNull())
            return Rt_VirtualSetValue(v, type, rtSet)
        }
    }
}
