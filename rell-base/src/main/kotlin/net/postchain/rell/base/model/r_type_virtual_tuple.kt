/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtual
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Lib_Type_VirtualTuple
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

class R_VirtualTupleType(val innerType: R_TupleType): R_VirtualType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualTupleType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_VirtualTuple(this)
    override fun getTypeMeta0(): R_TypeMeta = Meta()
    override fun getTypeArgs() = immListOf(innerType)
    override fun explicitComponentTypes() = immListOf(innerType)

    override fun getLibType0() = C_LibType.make(
        Lib_Rell.VIRTUAL_TYPE,
        innerType,
        valueMembers = lazy { Lib_Type_VirtualTuple.getValueMembers(this) },
    )

    private inner class Meta: R_TypeMeta() {
        override fun getTypeOrNull(args: ImmList<R_Type>): R_Type? {
            checkEquals(args.size, 1)
            val argType = args[0]
            return when (argType) {
                is R_TupleType -> R_VirtualTupleType(argType)
                else -> null
            }
        }
    }
}

class Rt_VirtualTupleValue(
    gtv: Gtv,
    private val type: R_VirtualTupleType,
    private val elements: List<Rt_Value?>,
): Rt_VirtualValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_TUPLE.type()

    override fun type() = type
    override fun asVirtualTuple() = this
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualTupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun str(format: StrFormat) = Rt_TupleValue.str("virtual", type.innerType, elements, format)
    override fun strCode(showTupleFieldNames: Boolean) =
            Rt_TupleValue.strCode("virtual", type.innerType, elements, showTupleFieldNames)

    override fun toFull0(): Rt_Value {
        val resElements = elements.map { toFull(it!!) }
        return Rt_TupleValue(type.innerType, resElements)
    }

    fun get(index: Int): Rt_Value {
        val value = elements[index]
        if (value == null) {
            val attr = type.innerType.fields[index].name ?: "$index"
            throw Rt_Exception.common("virtual_tuple:get:novalue:$attr", "Field '$attr' has no value")
        }
        return value
    }
}

class GtvRtConversion_VirtualTuple(val type: R_VirtualTupleType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualTuple(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualTuple(ctx: GtvToRtContext, type: R_VirtualTupleType, v: Gtv): Rt_Value {
            val fieldValues = decodeFields(ctx, type, v)
            val rtFieldValues = type.innerType.fields.mapIndexed { i, attr ->
                val gtvAttr = if (i < fieldValues.size) fieldValues[i] else null
                if (gtvAttr == null) null else decodeVirtualElement(ctx, attr.type, gtvAttr)
            }
            return Rt_VirtualTupleValue(v, type, rtFieldValues)
        }

        private fun decodeFields(ctx: GtvToRtContext, type: R_VirtualTupleType, v: Gtv): List<Gtv?> {
            return if (v !is GtvVirtual) {
                GtvRtConversion_Tuple.gtvArrayToFields(ctx, type.innerType, v)
            } else {
                GtvRtConversion_VirtualStruct.decodeVirtualArray(ctx, type, v, type.innerType.fields.size)
            }
        }
    }
}
