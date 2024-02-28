package net.postchain.rell.base.model

import com.google.common.collect.Iterables
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtual
import net.postchain.gtv.GtvVirtualDictionary
import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.Rt_MapValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immListOf

class R_VirtualMapType(val innerType: R_MapType): R_VirtualType(innerType) {
    private val virtualValueType: R_Type = S_VirtualType.virtualMemberType(innerType.valueType)
    val virtualEntryType: R_TupleType = R_TupleType.create(innerType.keyType, virtualValueType)

    override fun equals0(other: R_Type): Boolean = other is R_VirtualMapType && innerType == other.innerType
    override fun hashCode0() = innerType.hashCode()
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_VirtualMap(this)

    override fun getLibType0(): C_LibType {
        return C_LibType.make(Lib_Rell.VIRTUAL_MAP_TYPE, innerType.keyType, innerType.valueType, virtualValueType)
    }
}

class Rt_VirtualMapValue(
    gtv: Gtv,
    private val type: R_VirtualMapType,
    private val map: Map<Rt_Value, Rt_Value>,
): Rt_VirtualValue(gtv) {
    override val valueType = Rt_CoreValueTypes.VIRTUAL_MAP.type()

    override fun type() = type
    override fun asMap() = map
    override fun toFormatArg() = map
    override fun strCode(showTupleFieldNames: Boolean) = Rt_MapValue.strCode(type, showTupleFieldNames, map)

    override fun str(format: StrFormat): String {
       return map
           .entries
           .joinToString(", ", "{", "}") { "${it.key.str(format)}=${it.value.str(format)}" }
    }

    override fun equals(other: Any?) = other === this || (other is Rt_VirtualMapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun asIterable(): Iterable<Rt_Value> {
        return Iterables.transform(map.entries) { entry ->
            Rt_TupleValue(type.virtualEntryType, immListOf(entry.key, entry.value))
        }
    }

    override fun toFull0(): Rt_Value {
        val resMap = map
                .mapKeys { (k, _) -> toFull(k) }
                .mapValues { (_, v) -> toFull(v) }
                .toMutableMap()
        return Rt_MapValue(type.innerType, resMap)
    }
}

class GtvRtConversion_VirtualMap(private val type: R_VirtualMapType): GtvRtConversion_Virtual() {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val virtual = deserialize(ctx, gtv)
        return decodeVirtualMap(ctx, type, virtual)
    }

    companion object {
        fun decodeVirtualMap(ctx: GtvToRtContext, type: R_VirtualMapType, v: Gtv): Rt_Value {
            val gtvMap = decodeMap(ctx, v, type)
            val rtMap = gtvMap
                    .mapValues { (_, v) -> decodeVirtualElement(ctx, type.innerType.valueType, v) }
                    .mapKeys { (k, _) -> Rt_TextValue.get(k) }
            return Rt_VirtualMapValue(v, type, rtMap)
        }

        private fun decodeMap(ctx: GtvToRtContext, v: Gtv, type: R_Type): Map<String, Gtv> {
            if (v !is GtvVirtual) {
                return GtvRtUtils.gtvToMap(ctx, v, type)
            }
            if (v !is GtvVirtualDictionary) {
                val cls = v.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialized_type:$cls", "Wrong deserialized Gtv type: $cls")
            }
            return v.dict
        }
    }
}
