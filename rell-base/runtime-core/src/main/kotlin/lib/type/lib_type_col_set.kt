/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_SetType
import net.postchain.rell.base.runtime.*

object Lib_Type_Set {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("set", since = SINCE0) {
            """
                A mutable set of elements of type `T`, where `T` is immutable. Subtype of `collection<T>`. Implemented
                as a hash-set, with iteration order determined by the order in which the elements were added.
                @see 1. <a href="../collection/index.html"><code>collection</code> - Rell Standard Library</a>
            """.comment()
            generic("T", subOf = "immutable")
            parent("collection<T>")

            rTypeMeta(R_SetType.META)

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new empty set.")
                bodyMeta {
                    val elemR = typeArgR("T")
                    bodyContext { ctx ->
                        val setType = Rt_SetType(ctx.exeCtx.appCtx.interpreter.resolveRType(elemR))
                        Rt_SetValue(setType, mutableSetOf())
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new set by copying the values from another iterable.")
                val values by param(
                    "iterable<-T>",
                    cast = Rt_IterableValue,
                    comment = "an iterable containing values with which to initialize this set",
                )
                bodyMeta {
                    val elemR = typeArgR("T")
                    bodyContext { ctx ->
                        val setType = Rt_SetType(ctx.exeCtx.appCtx.interpreter.resolveRType(elemR))
                        val set = mutableSetOf<Rt_Value>()
                        values.forEach { set.add(it) }
                        Rt_SetValue(setType, set)
                    }
                }
            }

            function("add_all_copy", "set<T>", since = "0.14.16") {
                """
                    Returns a new set containing the elements of this set and the elements of a given collection.

                    `a.add_all_copy(b)` is equivalent to `a + b`, where `a` and `b` are sets.

                    Examples:
                    - `set([1]).add_all_copy(set([1]))` returns `set([1])`
                    - `set([1, 2, 3]).add_all_copy(set([2, 3, 4]))` returns `set([1, 2, 3, 4])`
                """.comment()
                val self by self(Rt_SetValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the other collection",
                )
                body { evalUnionSet(self, values) }
            }

            function("remove_all_copy", "set<T>", since = "0.14.16") {
                """
                    Returns a new set containing the elements of this set, but without any elements that occur in the
                    given collection.

                    `a.remove_all_copy(b)` is equivalent to `a - b`, where `a` and `b` are sets.

                    Examples:
                    - `set([1]).remove_all_copy(set([1]))` returns `set([])`
                    - `set([1, 2, 3]).remove_all_copy(set([2, 3, 4]))` returns `set([1])`
                """.comment()
                val self by self(Rt_SetValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the other collection",
                )
                body { evalSubSet(self, values) }
            }

            function("retain_all_copy", "set<T>", since = "0.14.16") {
                """
                    Returns a new set whose elements are those found in both this set and the given collection, or in
                    other words, the intersection of this set and the given collection.
                    @return a new set whose elements are the intersection of this set and the given collection

                    `a.retain_all_copy(b)` is equivalent to `a & b`, where `a` and `b` are sets.

                    Examples:
                    - `set([1]).retain_all_copy(set([1]))` returns `set([1])`
                    - `set([1]).retain_all_copy(set([2]))` returns `set([])`
                    - `set([1, 2, 3]).retain_all_copy(set([2, 3, 4]))` returns `set([2, 3])`
                """.comment()
                val self by self(Rt_SetValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the other collection",
                )
                body { evalIntersectSet(self, values) }
            }
        }
    }
}
