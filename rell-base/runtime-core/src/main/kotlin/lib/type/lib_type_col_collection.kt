/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.lib.type.Lib_Type_Any as AnyFns

object Lib_Type_Collection {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("collection", abstract = true, hidden = true, since = SINCE0) {
            """
                A generic type for mutable ordered collections of elements. Subtype of `iterable<T>`. Supports many
                standard operations such add insertion, removal, lookup and sorting.
            """.comment()

            generic("T")
            parent("iterable<T>")

            function("to_text", "text", since = "0.9.0") {
                comment("Returns a textual representation of this collection.")
                alias("str", since = SINCE0)
                bodyRaw(AnyFns.ToText_NoDb)
            }

            function("empty", "boolean", pure = true, since = SINCE0) {
                """
                    Check if this collection is empty.
                    @return `true` if this collection is empty, `false` otherwise
                """.comment()

                val self by self(Rt_CollectionValue)

                body(Rt_BooleanValue) {
                    self.collection.isEmpty()
                }
            }

            function("size", "integer", pure = true, since = SINCE0) {
                comment("Get the size (number of elements) of this collection.")
                alias("len", deprecated = C_MessageType.ERROR, since = SINCE0)
                val self by self(Rt_CollectionValue)
                body {
                    Rt_IntValue.get(self.collection.size.toLong())
                }
            }

            function("contains", "boolean", pure = true, since = SINCE0) {
                """
                    Check if this collection contains the given element.
                    @return `true` if this collection contains the given element, `false` otherwise
                """.comment()
                val self by self(Rt_CollectionValue)
                val value by param("T", cast = Rt_Value, comment = "the element to look up")
                body(Rt_BooleanValue) {
                    self.collection.contains(value)
                }
            }

            function("contains_all", "boolean", pure = true, since = "0.9.0") {
                """
                    Check if this collection contains all elements of another collection.
                    @return `true` if this collection contains all elements of the given collection, `false` otherwise
                """.comment()
                alias("containsAll", deprecated = C_MessageType.ERROR, since = SINCE0)
                val self by self(Rt_CollectionValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the collection to check against",
                )
                body(Rt_BooleanValue) {
                    self.collection.containsAll(values.collection)
                }
            }

            function("add", "boolean", since = SINCE0) {
                """
                    Append an element to the end of this collection.

                    The element is not added if this collection does not allow duplicates and the element is already
                    contained in this collection.
                    @return `true` if the element was added, `false` otherwise
                """.comment()

                val self by self(Rt_CollectionValue)
                val value by param("T", cast = Rt_Value, comment = "the element to add")

                body(Rt_BooleanValue) {
                    self.collection.add(value)
                }
            }

            function("add_all", "boolean", since = "0.9.0") {
                """
                    Add all elements from another collection to the end of this collection.

                    If this collection does not allow duplicates, then only those elements not already contained in this
                    collection are added.
                    @return `true` if any elements were added to this collection, `false` if it was not modified
                """.comment()

                alias("addAll", deprecated = C_MessageType.ERROR, since = SINCE0)

                val self by self(Rt_CollectionValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the collection of elements to add",
                )

                body(Rt_BooleanValue) {
                    self.collection.addAll(values.collection)
                }
            }

            function("remove", "boolean", since = SINCE0) {
                """
                    Remove an element from this collection.
                    @return `true` if the element was successfully removed, `false` if it was not present in the
                    collection
                """.comment()

                val self by self(Rt_CollectionValue)
                val value by param("T", cast = Rt_Value, comment = "the element to remove")

                body(Rt_BooleanValue) {
                    self.collection.remove(value)
                }
            }

            function("remove_all", "boolean", since = "0.9.0") {
                """
                    Remove all elements in another collection from this collection.
                    @return `true` if any elements were removed from this collection, `false` if it was not modified
                """.comment()

                alias("removeAll", deprecated = C_MessageType.ERROR, since = SINCE0)

                val self by self(Rt_CollectionValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the collection of elements to remove",
                )

                body(Rt_BooleanValue) {
                    self.collection.removeAll(values.collection.toSet())
                }
            }

            function("retain_all", "boolean", since = "0.14.16") {
                """
                    Retain in this collection only those elements found in the given collection. In other words, remove
                    from this collection all elements that are not found in the given collection.
                    @return `true` if any elements were removed from this collection, `false` if it was not modified
                """.comment()

                val self by self(Rt_CollectionValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the collection of elements to retain",
                )

                body(Rt_BooleanValue) {
                    self.collection.retainAll(values.collection.toSet())
                }
            }

            function("clear", "unit", since = SINCE0) {
                """
                    Clear this collection; i.e. remove all its elements. Immediately after this method returns, this
                    collection is empty.
                """.comment()

                val self by self(Rt_CollectionValue)

                body {
                    self.collection.clear()
                    Rt_UnitValue
                }
            }

            function("sorted", "list<T>", pure = true, since = "0.8.0") {
                """
                    Sorts the elements of this collection into a list. This collection is not modified.
                    @return a sorted list containing the same elements as this collection
                """.comment()

                val self by self(Rt_CollectionValue)

                bodyMeta {
                    val elemR = typeArgR("T")
                    val comparator = getSortComparatorRr(this, typeArgRrType("T"), elemR.name)
                    bodyContext { ctx ->
                        val elemRt = ctx.exeCtx.appCtx.interpreter.resolveRType(elemR)
                        val listType = Rt_ListType(elemRt)
                        val copy = ArrayList(self.collection)
                        copy.sortWith(comparator)
                        Rt_ListValue(listType, copy)
                    }
                }
            }
        }
    }

    fun getSortComparatorRr(
        m: Ld_FunctionMetaBodyDsl,
        rrType: RR_Type,
        typeName: String,
    ): Comparator<Rt_Value> {
        val comparator = createComparator(rrType)

        return if (comparator != null) {
            comparator
        } else {
            // Must not happen, because there are type constraints (comparable), but checking for extra safety.
            val fnName = m.fnQualifiedName
            val code = "fn:$fnName:not_comparable:$typeName"
            val msg = "Cannot sort values of non-comparable type $typeName"
            m.validationError(code, msg)
            Comparator { _, _ -> 0 }
        }
    }
}


