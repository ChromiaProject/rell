/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.GtvCompatibility
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.lib.type.Lib_Type_Any as AnyFns

object Lib_Type_Collection {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("collection", abstract = true, hidden = true, since = SINCE0) {
            comment("""
                A generic type for mutable ordered collections of elements. Subtype of `iterable<T>`. Supports many
                standard operations such add insertion, removal, lookup and sorting.
            """)
            generic("T")
            parent("iterable<T>")

            function("to_text", "text", since = "0.9.0") {
                comment("Returns a textual representation of this collection.")
                alias("str", since = SINCE0)
                bodyRaw(AnyFns.ToText_NoDb)
            }

            function("empty", "boolean", pure = true, since = SINCE0) {
                comment("""
                    Check if this collection is empty.
                    @return `true` if this collection is empty, `false` otherwise
                """)
                body { self ->
                    val col = self.asCollection()
                    Rt_BooleanValue.get(col.isEmpty())
                }
            }

            function("size", "integer", pure = true, since = SINCE0) {
                comment("Get the size (number of elements) of this collection.")
                alias("len", deprecated = C_MessageType.ERROR, since = SINCE0)
                body { self ->
                    val col = self.asCollection()
                    Rt_IntValue.get(col.size.toLong())
                }
            }

            function("contains", "boolean", pure = true, since = SINCE0) {
                comment("""
                    Check if this collection contains the given element.
                    @return `true` if this collection contains the given element, `false` otherwise
                """)
                param("value", "T", comment = "the element to look up")
                body { self, value ->
                    val col = self.asCollection()
                    Rt_BooleanValue.get(col.contains(value))
                }
            }

            function("contains_all", "boolean", pure = true, since = "0.9.0") {
                comment("""
                    Check if this collection contains all elements of another collection.
                    @return `true` if this collection contains all elements of the given collection, `false` otherwise
                """)
                alias("containsAll", deprecated = C_MessageType.ERROR, since = SINCE0)
                param("values", type = "collection<-T>", comment = "the collection to check against")
                body { self, values ->
                    val col1 = self.asCollection()
                    val col2 = values.asCollection()
                    Rt_BooleanValue.get(col1.containsAll(col2))
                }
            }

            function("add", "boolean", since = SINCE0) {
                comment("""
                    Append an element to the end of this collection.

                    The element is not added if this collection does not allow duplicates and the element is already
                    contained in this collection.
                    @return `true` if the element was added, `false` otherwise
                """)
                param("value", "T", comment = "the element to add")
                body { self, value ->
                    val col = self.asCollection()
                    Rt_BooleanValue.get(col.add(value))
                }
            }

            function("add_all", "boolean", since = "0.9.0") {
                comment("""
                    Add all elements from another collection to the end of this collection.

                    If this collection does not allow duplicates, then only those elements not already contained in this
                    collection are added.
                    @return `true` if any elements were added to this collection, `false` if it was not modified
                """)
                alias("addAll", deprecated = C_MessageType.ERROR, since = SINCE0)
                param("values", type = "collection<-T>", comment = "the collection of elements to add")
                body { self, values ->
                    val col = self.asCollection()
                    Rt_BooleanValue.get(col.addAll(values.asCollection()))
                }
            }

            function("remove", "boolean", since = SINCE0) {
                comment("""
                    Remove an element from this collection.
                    @return `true` if the element was successfully removed, `false` if it was not present in the
                    collection
                """)
                param("value", "T", comment = "the element to remove")
                body { self, value ->
                    val col = self.asCollection()
                    Rt_BooleanValue.get(col.remove(value))
                }
            }

            function("remove_all", "boolean", since = "0.9.0") {
                comment("""
                    Remove all elements in another collection from this collection.
                    @return `true` if any elements were removed from this collection, `false` if it was not modified
                """)
                alias("removeAll", deprecated = C_MessageType.ERROR, since = SINCE0)
                param("values", type = "collection<-T>", comment = "the collection of elements to remove")
                body { self, values ->
                    val col1 = self.asCollection()
                    val col2 = values.asCollection()
                    Rt_BooleanValue.get(col1.removeAll(col2))
                }
            }

            function("retain_all", "boolean", since = "0.14.16") {
                comment("""
                    Retain in this collection only those elements found in the given collection. In other words, remove
                    from this collection all elements that are not found in the given collection.
                    @return `true` if any elements were removed from this collection, `false` if it was not modified
                """)
                param("values", type = "collection<-T>", comment = "the collection of elements to retain")
                body { self, values ->
                    val col1 = self.asCollection()
                    val col2 = values.asCollection()
                    Rt_BooleanValue.get(col1.retainAll(col2))
                }
            }

            function("clear", "unit", since = SINCE0) {
                comment("""
                    Clear this collection; i.e. remove all its elements. Immediately after this method returns, this
                    collection is empty.
                """)
                body { self ->
                    val col = self.asCollection()
                    col.clear()
                    Rt_UnitValue
                }
            }

            function("sorted", "list<T>", pure = true, since = "0.8.0") {
                comment("""
                    Sorts the elements of this collection into a list. This collection is not modified.
                    @return a sorted list containing the same elements as this collection
                """)
                bodyMeta {
                    val valueRtType = typeArgRt("T")
                    val comparator = getSortComparatorRt(this, valueRtType)
                    val listType = rtListType(valueRtType)
                    body { self ->
                        val col = self.asCollection()
                        val copy = ArrayList(col)
                        copy.sortWith(comparator)
                        Rt_ListValue(listType, copy)
                    }
                }
            }
        }
    }

    fun getSortComparatorRt(m: Ld_FunctionMetaBodyDsl, valueType: Rt_Type): Comparator<Rt_Value> {
        val comparator = valueType.comparator
        return if (comparator != null) {
            comparator
        } else {
            // Must not happen, because there are type constraints (comparable), but checking for extra safety.
            val fnName = m.fnQualifiedName
            val code = "fn:$fnName:not_comparable:${valueType.name}"
            val msg = "Cannot sort values of non-comparable type ${valueType.name}"
            m.validationError(code, msg)
            Comparator { _, _ -> 0 }
        }
    }
}

sealed class GtvRtConversion_Collection(
    protected val typeName: String,
    elementConversion: Lazy<Rt_TypeGtvConversion>,
    rtType: Lazy<Rt_Type>,
): GtvRtConversion {
    protected val elementConversion by elementConversion
    protected val rtType by rtType

    final override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    final override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv =
        GtvArray(rt.asCollection().map { elementConversion.rtToGtv(it, pretty) }.toTypedArray())
}
