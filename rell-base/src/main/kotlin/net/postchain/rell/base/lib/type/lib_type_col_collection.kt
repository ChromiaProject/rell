/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibTypeDef
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.lib.type.Lib_Type_Any as AnyFns

object Lib_Type_Collection {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("collection", abstract = true, hidden = true) {
            generic("T")
            parent("iterable<T>")

            function("to_text", "text") {
                alias("str")
                bodyRaw(AnyFns.ToText_NoDb)
            }

            function("empty", "boolean", pure = true) {
                body { a ->
                    val col = a.asCollection()
                    Rt_BooleanValue.get(col.isEmpty())
                }
            }

            function("size", "integer", pure = true) {
                alias("len", deprecated = C_MessageType.ERROR)
                body { a ->
                    val col = a.asCollection()
                    Rt_IntValue.get(col.size.toLong())
                }
            }

            function("contains", "boolean", pure = true) {
                param("value", "T")
                body { a, b ->
                    val col = a.asCollection()
                    Rt_BooleanValue.get(col.contains(b))
                }
            }

            function("contains_all", "boolean", pure = true) {
                alias("containsAll", deprecated = C_MessageType.ERROR)
                param("values", type = "collection<-T>")
                body { a, b ->
                    val col1 = a.asCollection()
                    val col2 = b.asCollection()
                    Rt_BooleanValue.get(col1.containsAll(col2))
                }
            }

            function("add", "boolean") {
                param("value", "T")
                body { a, b ->
                    val col = a.asCollection()
                    Rt_BooleanValue.get(col.add(b))
                }
            }

            function("add_all", "boolean") {
                alias("addAll", deprecated = C_MessageType.ERROR)
                param("values", type = "collection<-T>")
                body { a, b ->
                    val col = a.asCollection()
                    Rt_BooleanValue.get(col.addAll(b.asCollection()))
                }
            }

            function("remove", "boolean") {
                param("value", "T")
                body { a, b ->
                    val col = a.asCollection()
                    Rt_BooleanValue.get(col.remove(b))
                }
            }

            function("remove_all", "boolean") {
                alias("removeAll", deprecated = C_MessageType.ERROR)
                param("values", type = "collection<-T>")
                body { a, b ->
                    val col1 = a.asCollection()
                    val col2 = b.asCollection()
                    Rt_BooleanValue.get(col1.removeAll(col2))
                }
            }

            function("clear", "unit") {
                body { a ->
                    val col = a.asCollection()
                    col.clear()
                    Rt_UnitValue
                }
            }

            function("sorted", "list<T>", pure = true) {
                bodyMeta {
                    val valueType = fnBodyMeta.typeArg("T")
                    val comparator = getSortComparator(this, valueType)

                    val listType = R_ListType(valueType)
                    body { a ->
                        val col = a.asCollection()
                        val copy = ArrayList(col)
                        copy.sortWith(comparator)
                        Rt_ListValue(listType, copy)
                    }
                }
            }
        }
    }

    fun getSortComparator(m: Ld_FunctionMetaBodyDsl, valueType: R_Type): Comparator<Rt_Value> {
        val comparator = valueType.comparator()
        return if (comparator != null) comparator else {
            // Must not happen, because there are type constraints (comparable), but checking for extra safety.
            val fnName = m.fnQualifiedName
            val code = "fn:$fnName:not_comparable:${valueType.strCode()}"
            val msg = "Cannot sort values of non-comparable type ${valueType.name}"
            m.validationError(code, msg)
            return Comparator { _, _ -> 0 }
        }
    }
}

sealed class R_CollectionKind(val type: R_Type) {
    abstract fun makeRtValue(col: Iterable<Rt_Value>): Rt_Value
}

sealed class R_CollectionType(
    val elementType: R_Type,
    private val baseName: String,
): R_Type("$baseName<${elementType.strCode()}>") {
    private val isError = elementType.isError()

    final override fun isReference() = true
    final override fun isError() = isError
    final override fun isDirectMutable() = true
    final override fun componentTypes() = listOf(elementType)
    final override fun strCode() = name

    protected abstract fun getLibTypeDef(): C_LibTypeDef

    final override fun getLibType0() = C_LibType.make(getLibTypeDef(), elementType)

    final override fun toMetaGtv() = mapOf(
            "type" to baseName.toGtv(),
            "value" to elementType.toMetaGtv()
    ).toGtv()
}

sealed class GtvRtConversion_Collection(val type: R_CollectionType): GtvRtConversion() {
    final override fun directCompatibility() = R_GtvCompatibility(true, true)

    final override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val elementType = type.elementType
        return GtvArray(rt.asCollection().map { elementType.rtToGtv(it, pretty) }.toTypedArray())
    }
}
