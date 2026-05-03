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
                    val elemR = typeArgR("T")
                    bodyContext { ctx ->
                        val setType = Rt_SetType(ctx.exeCtx.appCtx.interpreter.resolveRType(elemR))
                        Rt_SetValue(setType, mutableSetOf())
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new set by copying the values from another iterable.")
                param("values", type = "iterable<-T>") {
                    comment("an iterable containing values with which to initialize this set")
                }
                bodyMeta {
                    val elemR = typeArgR("T")
                    bodyContext { ctx, arg ->
                        val setType = Rt_SetType(ctx.exeCtx.appCtx.interpreter.resolveRType(elemR))
                        val iterable = arg.asIterable()
                        val set = mutableSetOf<Rt_Value>()
                        iterable.forEach { set.add(it) }
                        Rt_SetValue(setType, set)
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
                body(::evalUnionSet)
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
                body(::evalSubSet)
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
                body(::evalIntersectSet)
            }
        }
    }
}
