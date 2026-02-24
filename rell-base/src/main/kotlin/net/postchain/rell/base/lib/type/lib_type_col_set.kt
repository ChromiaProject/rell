/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.R_ListType.Companion
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_TypeMeta
import net.postchain.rell.base.model.R_VirtualSetType
import net.postchain.rell.base.model.expr.R_BinaryOp_Intersect_Set
import net.postchain.rell.base.model.expr.R_BinaryOp_Sub_Set
import net.postchain.rell.base.model.expr.R_BinaryOp_Union_Set
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.doc.DocType
import net.postchain.rell.base.utils.doc.DocUtils
import net.postchain.rell.base.utils.immListOf

object Lib_Type_Set {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("set", since = SINCE0) {
            comment("""
                A mutable set of elements of type `T`, where `T` is immutable. Subtype of `collection<T>`. Implemented
                as a hash-set, with iteration order determined by the order in which the elements were added.
                @see 1. <a href="../collection/index.html"><code>collection</code> - Rell Standard Library</a>
            """)
            generic("T", subOf = "immutable")
            parent("collection<T>")

            rTypeMeta(R_SetType.META)

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new empty set.")
                bodyMeta {
                    val elementType = fnBodyMeta.typeArg("T")
                    val rKind = R_CollectionKind_Set(R_SetType(elementType))
                    body { ->
                        rKind.makeRtValue(immListOf())
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new set by copying the values from another iterable.")
                param("values", type = "iterable<-T>") {
                    comment("an iterable containing values with which to initialize this set")
                }
                bodyMeta {
                    val elementType = fnBodyMeta.typeArg("T")
                    val rKind = R_CollectionKind_Set(R_SetType(elementType))
                    body { arg ->
                        val iterable = arg.asIterable()
                        rKind.makeRtValue(iterable)
                    }
                }
            }

            function("add_all_copy", "set<T>", since = "0.14.16") {
                comment("""
                    Returns a new set containing the elements of this set and the elements of a given collection.

                    `a.add_all_copy(b)` is equivalent to `a + b`, where `a` and `b` are sets.

                    Examples:
                    - `set([1]).add_all_copy(set([1]))` returns `set([1])`
                    - `set([1, 2, 3]).add_all_copy(set([2, 3, 4]))` returns `set([1, 2, 3, 4])`
                """)
                param("values", type = "collection<-T>", comment = "the other collection")
                body(R_BinaryOp_Union_Set::evaluate)
            }

            function("remove_all_copy", "set<T>", since = "0.14.16") {
                comment("""
                    Returns a new set containing the elements of this set, but without any elements that occur in the
                    given collection.

                    `a.remove_all_copy(b)` is equivalent to `a - b`, where `a` and `b` are sets.

                    Examples:
                    - `set([1]).remove_all_copy(set([1]))` returns `set([])`
                    - `set([1, 2, 3]).remove_all_copy(set([2, 3, 4]))` returns `set([1])`
                """)
                param("values", type = "collection<-T>", comment = "the other collection")
                body(R_BinaryOp_Sub_Set::evaluate)
            }

            function("retain_all_copy", "set<T>", since = "0.14.16") {
                comment("""
                    Returns a new set whose elements are those found in both this set and the given collection, or in
                    other words, the intersection of this set and the given collection.
                    @return a new set whose elements are the intersection of this set and the given collection

                    `a.retain_all_copy(b)` is equivalent to `a & b`, where `a` and `b` are sets.

                    Examples:
                    - `set([1]).retain_all_copy(set([1]))` returns `set([1])`
                    - `set([1]).retain_all_copy(set([2]))` returns `set([])`
                    - `set([1, 2, 3]).retain_all_copy(set([2, 3, 4]))` returns `set([2, 3])`
                """)
                param("values", type = "collection<-T>", comment = "the other collection")
                body(R_BinaryOp_Intersect_Set::evaluate)
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
    override fun getTypeMeta0() = META
    override fun docType() = DocUtils.docTypeGeneric("set", elementType)
    override fun isAssignableFrom(type: R_Type) = type is R_SetType && elementType.isAssignableArg(type.elementType)

    companion object {
        internal val META = R_TypeMeta.make { t -> R_SetType(t) }
    }
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

    override fun equals(other: Any?) = other === this || (other is Rt_SetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, elements, showTupleFieldNames)
    override fun str(format: StrFormat) = elements.joinToString(", ", "[", "]") { it.str(format) }

    override fun strPretty(indent: Int): String {
        if (elements.isEmpty()) {
            return str(StrFormat.V2)
        }
        val indentStr = "    ".repeat(indent)
        return elements.joinToString(",", "[", "\n$indentStr]") {
            val s = it.strPretty(indent + 1)
            "\n$indentStr    $s"
        }
    }

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
