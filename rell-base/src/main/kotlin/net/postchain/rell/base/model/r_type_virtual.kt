/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvVirtual
import net.postchain.gtv.merkle.proof.GtvMerkleProofTreeFactory
import net.postchain.gtv.merkle.proof.toGtvVirtual
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.R_SetType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.toGtv

sealed class R_VirtualType(private val baseInnerType: R_Type): R_Type("virtual<${baseInnerType.name}>") {
    private val isError = baseInnerType.isError()

    final override fun isReference() = true
    final override fun isError() = isError
    final override fun strCode() = name
    final override fun isDirectPure() = false    // Maybe it's actually pure.

    final override fun toMetaGtv() = mapOf(
            "type" to "virtual".toGtv(),
            "value" to baseInnerType.toMetaGtv()
    ).toGtv()
}

sealed class Rt_VirtualValue(val gtv: Gtv): Rt_Value() {
    override fun asVirtual() = this

    fun toFull(): Rt_Value {
        if (gtv is GtvVirtual) {
            val typeStr = type().name
            throw Rt_Exception.common("virtual:to_full:notfull:$typeStr", "Value of type $typeStr is not full")
        }
        val res = toFull0()
        return res
    }

    protected abstract fun toFull0(): Rt_Value

    companion object {
        fun toFull(v: Rt_Value): Rt_Value {
            return if (v is Rt_VirtualValue) v.toFull() else v
        }
    }
}

sealed class GtvRtConversion_Virtual: GtvRtConversion() {
    final override fun directCompatibility() = R_GtvCompatibility(fromGtv = true, toGtv = false)
    final override fun rtToGtv(rt: Rt_Value, pretty: Boolean) =
            throw Rt_GtvError.exception("virtual:to_gtv", "Cannot convert virtual to Gtv")

    companion object {
        fun deserialize(ctx: GtvToRtContext, gtv: Gtv): Gtv {
            if (gtv !is GtvArray) {
                val cls = gtv.javaClass.simpleName
                throw GtvRtUtils.errGtv(ctx, "virtual:type:$cls", "Wrong Gtv type: $cls")
            }

            val proof = try {
                GtvMerkleProofTreeFactory().deserialize(gtv)
            } catch (e: Exception) {
                throw GtvRtUtils.errGtv(ctx, "virtual:deserialize:${e.javaClass.canonicalName}",
                        "Virtual proof deserialization failed: ${e.message}")
            }

            val virtual = proof.toGtvVirtual()
            return virtual
        }

        fun decodeVirtualElement(ctx: GtvToRtContext, type: R_Type, gtv: Gtv): Rt_Value {
            return when (type) {
                is R_StructType -> GtvRtConversion_VirtualStruct.decodeVirtualStruct(ctx, type.struct.virtualType, gtv)
                is R_ListType -> GtvRtConversion_VirtualList.decodeVirtualList(ctx, type.virtualType, gtv)
                is R_SetType -> GtvRtConversion_VirtualSet.decodeVirtualSet(ctx, type.virtualType, gtv)
                is R_MapType -> GtvRtConversion_VirtualMap.decodeVirtualMap(ctx, type.virtualType, gtv)
                is R_TupleType -> GtvRtConversion_VirtualTuple.decodeVirtualTuple(ctx, type.virtualType, gtv)
                is R_NullableType -> if (gtv.isNull()) Rt_NullValue else decodeVirtualElement(ctx, type.valueType, gtv)
                else -> type.gtvToRt(ctx, gtv)
            }
        }
    }
}
