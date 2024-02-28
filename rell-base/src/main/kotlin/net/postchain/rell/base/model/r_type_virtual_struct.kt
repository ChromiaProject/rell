package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtual
import net.postchain.gtv.GtvVirtualArray
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Lib_Type_VirtualStruct
import net.postchain.rell.base.runtime.*

class R_VirtualStructType(val innerType: R_StructType): R_VirtualType(innerType) {
    override fun equals0(other: R_Type): Boolean = other is R_VirtualStructType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun isCacheable() = true
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_VirtualStruct(this)

    override fun getLibType0() = C_LibType.make(
        Lib_Rell.VIRTUAL_TYPE,
        innerType,
        valueMembers = lazy { Lib_Type_VirtualStruct.getValueMembers(this) },
    )
}

class Rt_VirtualStructValue(
        gtv: Gtv,
        private val type: R_VirtualStructType,
        private val attributes: List<Rt_Value?>
): Rt_VirtualValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_STRUCT.type()

    override fun type() = type
    override fun asVirtualStruct() = this
    override fun toFormatArg() = str()
    override fun equals(other: Any?) = other === this || (other is Rt_VirtualStructValue && attributes == other.attributes)
    override fun hashCode() = type.hashCode() * 31 + attributes.hashCode()

    override fun str(format: StrFormat) = Rt_StructValue.str(this, type, type.innerType.struct, attributes, format)
    override fun strCode(showTupleFieldNames: Boolean) =
            Rt_StructValue.strCode(this, type, type.innerType.struct, attributes)

    fun get(index: Int): Rt_Value {
        val value = attributes[index]
        if (value == null) {
            val typeName = type.innerType.name
            val attr = type.innerType.struct.attributesList[index].name
            throw Rt_Exception.common("virtual_struct:get:novalue:$typeName:$attr", "Attribute '$typeName.$attr' has no value")
        }
        return value
    }

    override fun toFull0(): Rt_Value {
        val fullAttrValues = attributes.map { toFull(it!!) }.toMutableList()
        return Rt_StructValue(type.innerType, fullAttrValues)
    }
}

class GtvRtConversion_VirtualStruct(private val type: R_VirtualStructType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualStruct(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualStruct(ctx: GtvToRtContext, type: R_VirtualStructType, v: Gtv): Rt_Value {
            val attrValues = decodeAttrs(ctx, type, v)
            val rtAttrValues = type.innerType.struct.attributesList.mapIndexed { i, attr ->
                val gtvAttr = if (i < attrValues.size) attrValues[i] else null
                if (gtvAttr == null) null else {
                    val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(type.name, attr))
                    decodeVirtualElement(attrCtx, attr.type, gtvAttr)
                }
            }
            return Rt_VirtualStructValue(v, type, rtAttrValues)
        }

        private fun decodeAttrs(ctx: GtvToRtContext, type: R_VirtualStructType, v: Gtv): List<Gtv?> {
            return if (v !is GtvVirtual) {
                GtvRtConversion_Struct.gtvToAttrValues(ctx, type, type.innerType.struct, v)
            } else {
                decodeVirtualArray(ctx, type, v, type.innerType.struct.attributes.size)
            }
        }

        fun decodeVirtualArray(ctx: GtvToRtContext, type: R_Type, v: Gtv, maxSize: Int): List<Gtv?> {
            if (v !is GtvVirtualArray) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }

            val actualCount = v.array.size
            if (actualCount > maxSize) {
                throw GtvRtConversion_Struct.errWrongArraySize(ctx, type, maxSize, actualCount)
            }

            return v.array.toList()
        }
    }
}
