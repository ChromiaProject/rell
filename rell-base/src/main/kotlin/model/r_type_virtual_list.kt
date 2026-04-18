/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtual
import net.postchain.gtv.GtvVirtualArray
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.runtime.*

class R_VirtualListType(val innerType: R_ListType): R_VirtualCollectionType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualListType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_VirtualList(this)
    override fun typeDef() = Lib_Rell.VIRTUAL_LIST_TYPE

    companion object {
        internal val META = R_TypeMeta.make { t, _ ->
            R_ListType(t).virtualType
        }
    }
}

class Rt_VirtualListValue(
    gtv: Gtv,
    private val type: R_VirtualListType,
    private val elements: List<Rt_Value?>,
): Rt_VirtualCollectionValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_LIST.type()

    override fun type() = type
    override fun asVirtualCollection() = this
    override fun asVirtualList() = this
    override fun strCode(showTupleFieldNames: Boolean) = Rt_ListValue.strCode(type, elements)
    override fun str(format: StrFormat) = elements.joinToString(", ", "[", "]") { it?.str(format) ?: "null" }
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualListValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }.toMutableList()
        return Rt_ListValue(type.innerType, resElements)
    }

    override fun size() = elements.size
    override fun asIterable() = elements.filterNotNull()

    fun contains(index: Long) = index >= 0 && index < elements.size && elements[index.toInt()] != null

    fun get(index: Long): Rt_Value {
        Rt_ListValue.checkIndex(elements.size, index)
        val value = elements[index.toInt()] ?: throw Rt_Exception.common(
            "virtual_list:get:novalue:$index",
            "Element $index has no value"
        )
        return value
    }
}

class GtvRtConversion_VirtualList(private val type: R_VirtualListType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualList(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualList(ctx: GtvToRtContext, type: R_VirtualListType, v: Gtv): Rt_Value {
            val rtElements = decodeVirtualElements(ctx, type.innerType, v)
            return Rt_VirtualListValue(v, type, rtElements)
        }

        fun decodeVirtualElements(ctx: GtvToRtContext, innerType: R_CollectionType, v: Gtv): List<Rt_Value?> {
            val gtvElements = decodeElements(ctx, v, innerType)
            val rtElements = gtvElements.map {
                if (it == null) null else decodeVirtualElement(ctx, innerType.elementType, it)
            }
            return rtElements
        }

        private fun decodeElements(ctx: GtvToRtContext, v: Gtv, type: R_Type): List<Gtv?> {
            if (v !is GtvVirtual) {
                return GtvRtUtils.gtvToArray(ctx, v, type).toList()
            }
            if (v !is GtvVirtualArray) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }
            return v.array.toList()
        }
    }
}
