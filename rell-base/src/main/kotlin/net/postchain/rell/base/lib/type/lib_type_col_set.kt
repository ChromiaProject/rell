/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VirtualSetType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.immListOf

object Lib_Type_Set {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("set") {
            generic("T", subOf = "immutable")
            parent("collection<T>")

            rType { t -> R_SetType(t) }

            constructor(pure = true) {
                bodyMeta {
                    val elementType = fnBodyMeta.typeArg("T")
                    val rKind = R_CollectionKind_Set(R_SetType(elementType))
                    body { ->
                        rKind.makeRtValue(immListOf())
                    }
                }
            }

            constructor(pure = true) {
                param("values", type = "iterable<-T>")
                bodyMeta {
                    val elementType = fnBodyMeta.typeArg("T")
                    val rKind = R_CollectionKind_Set(R_SetType(elementType))
                    body { arg ->
                        val iterable = arg.asIterable()
                        rKind.makeRtValue(iterable)
                    }
                }
            }
        }
    }
}

private class R_CollectionKind_Set(type: R_Type): R_CollectionKind(type) {
    override fun makeRtValue(col: Iterable<Rt_Value>): Rt_Value {
        val set = mutableSetOf<Rt_Value>()
        set.addAll(col)
        return Rt_SetValue(type, set)
    }
}

class R_SetType(elementType: R_Type): R_CollectionType(elementType, "set") {
    val virtualType = R_VirtualSetType(this)

    override fun equals0(other: R_Type): Boolean = other is R_SetType && elementType == other.elementType
    override fun hashCode0() = elementType.hashCode()

    override fun fromCli(s: String): Rt_Value = Rt_SetValue(this, s.split(",").map { elementType.fromCli(it) }.toMutableSet())
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Set(this)
    override fun getLibTypeDef() = Lib_Rell.SET_TYPE
}

class Rt_SetValue(private val type: R_Type, private val elements: MutableSet<Rt_Value>): Rt_Value() {
    init {
        check(type is R_SetType) { "wrong type: ${type.str()}" }
    }

    override val valueType = Rt_CoreValueTypes.SET.type()

    override fun type() = type
    override fun asIterable(): Iterable<Rt_Value> = elements
    override fun asCollection() = elements
    override fun asSet() = elements
    override fun toFormatArg() = elements
    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, elements, showTupleFieldNames)
    override fun str() = elements.joinToString(", ", "[", "]") { it.str() }
    override fun equals(other: Any?) = other === this || (other is Rt_SetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    companion object {
        fun strCode(type: R_Type, elements: Set<Rt_Value>, showTupleFieldNames: Boolean): String =
                "${type.strCode()}[${elements.joinToString(",") { it.strCode(false) }}]"
    }
}

class GtvRtConversion_Set(type: R_SetType): GtvRtConversion_Collection(type) {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val array = GtvRtUtils.gtvToArray(ctx, gtv, type)
        val list = array.map { type.elementType.gtvToRt(ctx, it) }
        val set = listToSet(ctx, list)
        return ctx.rtValue {
            Rt_SetValue(type, set)
        }
    }

    companion object {
        fun listToSet(ctx: GtvToRtContext, elements: Iterable<Rt_Value>): MutableSet<Rt_Value> {
            val set = mutableSetOf<Rt_Value>()
            for (elem in elements) {
                if (!set.add(elem)) {
                    throw GtvRtUtils.errGtv(ctx, "set_dup:${elem.strCode()}", "Duplicate set element: ${elem.str()}")
                }
            }
            return set
        }
    }
}
