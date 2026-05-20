/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_ListType
import net.postchain.rell.base.runtime.*

object Lib_Type_List {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("list", since = SINCE0) {
            """
                Represents a mutable array list. Subtype of `collection<T>`.

                @see 1. <a href="../collection/index.html"><code>collection</code> - Rell Standard Library</a>
            """.comment()
            generic("T")
            parent("collection<T>")

            rTypeMeta(R_ListType.META)

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new empty list.")
                bodyMeta {
                    val elemR = typeArgR("T")
                    bodyContext { ctx ->
                        val listType = Rt_ListType(ctx.exeCtx.appCtx.interpreter.resolveRType(elemR))
                        Rt_ListValue(listType)
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new list by copying the values from another iterable.")
                val values by param(
                    "iterable<-T>",
                    cast = Rt_IterableValue,
                    comment = "an iterable containing values with which to initialize this list",
                )
                bodyMeta {
                    val elemR = typeArgR("T")
                    bodyContext { ctx ->
                        val listType = Rt_ListType(ctx.exeCtx.appCtx.interpreter.resolveRType(elemR))
                        val list = mutableListOf<Rt_Value>()
                        values.forEach { list.add(it) }
                        Rt_ListValue(listType, list)
                    }
                }
            }

            function("get", "T", pure = true, since = SINCE0) {
                """
                    Retrieve the element at the specified index.
                    @throws exception if the provided index is out of bounds
                """.comment()
                val self by self(Rt_ListValue)
                val index by param(Rt_IntValue, comment = "the index of the element to retrieve")
                body {
                    val list = self.elements
                    val i = index.value
                    if (i < 0 || i >= list.size) {
                        throw Rt_Exception.common(
                            "fn:list.get:index:${list.size}:$i",
                            "List index out of bounds: $i (size ${list.size})",
                        )
                    }
                    list[i.toInt()]
                }
            }

            function("index_of", "integer", pure = true, since = "0.9.0") {
                """
                    Search for the first occurrence of the specified element within this list.
                    @return the index of the first occurrence of the element within this list, or `-1` if the element
                    does not occur in this list
                """.comment()
                val self by self(Rt_ListValue)
                alias("indexOf", deprecated = C_MessageType.ERROR, since = SINCE0)
                val value by param("T", cast = Rt_Value, comment = "the element for which to search")
                body {
                    val list = self.elements
                    Rt_IntValue.get(list.indexOf(value).toLong())
                }
            }

            function("remove_at", "T", since = "0.9.0") {
                """
                    Remove and return the element at the specified index. The indices of elements occurring after the
                    specified index decrease by 1, and the size of this list decreases by 1.
                    @return the element that was at the specified index
                    @throws exception if the index is out of bounds
                """.comment()
                val self by self(Rt_ListValue)
                alias("removeAt", deprecated = C_MessageType.ERROR, since = SINCE0)
                val index by param(Rt_IntValue, comment = "the index of the element to remove")
                body {
                    val list = self.elements
                    val i = index.value

                    if (i < 0 || i >= list.size) {
                        throw Rt_Exception.common(
                            "fn:list.remove_at:index:${list.size}:$i",
                            "Index out of range: $i (size ${list.size})",
                        )
                    }

                    val r = list.removeAt(i.toInt())
                    r
                }
            }

            function("sub", "list<T>", pure = true, since = SINCE0) {
                """
                    Returns a sublist of this list starting from the specified index (inclusive).

                    Equivalent to `list.sub(index, list.size())`.

                    Note that:
                    - `my_list.sub(my_list.size() - 1)` returns a list containing only the last element of `my_list`
                        (assuming `my_list.size > 0`)
                    - `my_list.sub(my_list.size())` returns an empty list
                    - `my_list.sub(my_list.size() + 1)` throws an exception
                    @throws exception if the `start` index greater than this list's size
                """.comment()
                val self by self(Rt_ListValue)
                val start by param(Rt_IntValue, comment = "the starting index of the sublist (inclusive)")
                body {
                    val type = self.type
                    val list = self.elements
                    calcSub(type, list, start.value, list.size.toLong())
                }
            }

            function("sub", "list<T>", pure = true, since = SINCE0) {
                """
                    Returns a sublist of this list starting from the specified start index (inclusive) to the specified
                    end index (exclusive).

                    For the list `my_list`, `my_list.sub(start, my_list.size())` is equivalent to `my_list.sub(start)`.

                    @throws exception when:
                    - the `start` or `end` indexes are greater than this list's size
                    - the `start` index is greater than the `end` index
                """.comment()
                val self by self(Rt_ListValue)
                val start by param(Rt_IntValue, comment = "the start index of the sublist (inclusive)")
                val end by param(Rt_IntValue, comment = "the end index of the sublist (exclusive)")
                body {
                    calcSub(self.type, self.elements, start.value, end.value)
                }
            }

            function("set", "T", since = SINCE0) {
                """
                    Set the element at the specified index, overwriting the element that was previously at that index.
                    The size of the list is unchanged.
                    @return the overwritten element
                    @throws exception if the index is out of bounds
                """.comment()
                val self by self(Rt_ListValue)
                alias("_set", deprecated = C_MessageType.WARNING, since = SINCE0)
                val index by param(Rt_IntValue, comment = "the index of the element to set")
                val value by param("T", cast = Rt_Value, comment = "the value to set")
                body {
                    val list = self.elements
                    val i = index.value

                    if (i < 0 || i >= list.size) {
                        throw Rt_Exception.common(
                            "fn:list.set:index:${list.size}:$i",
                            "Index out of range: $i (size ${list.size})",
                        )
                    }

                    val r = list.set(i.toInt(), value)
                    r
                }
            }

            function("add", "boolean", since = SINCE0) {
                """
                    Insert a value at the specified index. Any elements previously occurring at and after the specified
                    index have their indices increased by 1, and the size of the list increases by 1.

                    Note that for a list `my_list`:
                    - `my_list.add(my_list.size(), x)` "appends" `x` to the end of the list
                    - `my_list.add(my_list.size() + 1, x)` throws an exception

                    Note also that `my_list.add(my_list.size(), x)` is equivalent to `my_list.add(x)` (inherited from
                    `collection<T>`).
                    @return `true`
                    @throws exception if the index is out of bounds
                """.comment()
                val self by self(Rt_ListValue)
                val index by param(Rt_IntValue, comment = "The index at which to add the element.")
                val value by param("T", cast = Rt_Value, comment = "The value to add.")
                body {
                    val list = self.elements
                    val i = index.value

                    if (i < 0 || i > list.size) {
                        throw Rt_Exception.common(
                            "fn:list.add:index:${list.size}:$i",
                            "Index out of range: $i (size ${list.size})",
                        )
                    }

                    list.add(i.toInt(), value)
                    Rt_BooleanValue.TRUE
                }
            }

            function("add_all", "boolean", since = "0.9.0") {
                """
                    Insert all elements from a collection at the specified index. Any elements previously occurring at
                    and after the specified index have their indices increased by the size of the given collection. The
                    size of the list increases by the size of the given collection.

                    Note that for a list `my_list`:
                    - `my_list.add_all(my_list.size(), x)` "appends" the elements of the collection `x` to the end of
                        the list
                    - `my_list.add_all(my_list.size() + 1, x)` throws an exception

                    Note also that `my_list.add_all(my_list.size(), x)` is equivalent to `my_list.add_all(x)` (inherited
                    from `collection<T>`).
                    @return `true`
                    @throws exception if the specified index is out of bounds
                """.comment()

                val self by self(Rt_ListValue)
                alias("addAll", deprecated = C_MessageType.ERROR, since = SINCE0)
                val index by param(Rt_IntValue, comment = "the starting index at which to add the elements")
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the collection containing elements to add",
                )

                body(Rt_BooleanValue) {
                    val list = self.elements
                    val i = index.value
                    val col = values.collection

                    if (i < 0 || i > list.size) {
                        throw Rt_Exception.common(
                            "fn:list.add_all:index:${list.size}:$i",
                            "Index out of range: $i (size ${list.size})"
                        )
                    }

                    list.addAll(i.toInt(), col)
                }
            }

            function("sort", "unit", since = "0.11.0") {
                comment("Sort this list in place.")
                val self by self(Rt_ListValue)
                alias("_sort", deprecated = C_MessageType.WARNING, since = "0.8.0")
                bodyMeta {
                    val comparator = Lib_Type_Collection.getSortComparatorRr(this, typeArgRrType("T"), typeArgR("T").name)
                    body {
                        val list = self.elements
                        list.sortWith(comparator)
                        Rt_UnitValue
                    }
                }
            }

            function("repeat", "list<T>", since = "0.11.0") {
                """
                    Repeat this list `n` times.

                    Examples:
                    - `[1, 2, 3].repeat(3)` returns `[1, 2, 3, 1, 2, 3, 1, 2, 3]`
                    - `list<T>().repeat(3)` returns `[]` (for any type `T`)
                    - `[3].repeat(0)` returns `[]`

                    @throws exception when:
                    - `n` is negative
                    - `n` is greater than `(2^31)-1`
                    - the resulting list has size greater than `(2^31)-1`
                    @return a new list with the elements from this list repeated `n` times
                """.comment()

                val self by self(Rt_ListValue)
                val n by param(Rt_IntValue, comment = "the number of times to repeat this list")

                body {
                    val list = self.elements
                    val total = rtCheckRepeatArgs(list.size, n.value, "list")

                    val resList: MutableList<Rt_Value> = ArrayList(total)
                    if (n.value > 0 && list.isNotEmpty()) {
                        for (_ in 0 until n.value) {
                            resList += list
                        }
                    }

                    Rt_ListValue(self.type, resList)
                }
            }

            function("reverse", "unit", since = "0.11.0") {
                """
                    Reverses the order of the elements in the list in place.

                    Where this list is bound to the variable `l`, then the statement `l.reverse();` is equivalent to
                    `l = l.reversed();`.
                """.comment()

                val self by self(Rt_ListValue)

                body {
                    val list = self.elements
                    list.reverse()
                    Rt_UnitValue
                }
            }

            function("reversed", "list<T>", since = "0.11.0") {
                """
                    Returns a new list with the elements of this list in reverse order.

                    Examples:
                    - `[].reversed()` returns `[]`
                    - `[1].reversed()` returns `[1]`
                    - `[1, 2, 3].reversed()` returns `[3, 2, 1]`
                """.comment()
                val self by self(Rt_ListValue)
                body {
                    val list = self.elements
                    val resList = list.toMutableList()
                    resList.reverse()
                    Rt_ListValue(self.type, resList)
                }
            }

            function("add_all_copy", "list<T>", since = "0.14.16") {
                """
                    Returns a new list that is the concatenation of the elements of this list with those of the given
                    collection.

                    `a.add_all_copy(b)` is equivalent to `a + b`, where `a` and `b` are lists.

                    Examples:
                    - `[].add_all_copy([])` returns `[]`
                    - `[1].add_all_copy([2])` returns `[1, 2]`
                    - `[[1]].add_all_copy([[2]])` returns `[[1], [2]]`
                """.comment()
                val self by self(Rt_ListValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the other collection",
                )
                body { evalConcatList(self, values) }
            }

            function("remove_all_copy", "list<T>", since = "0.14.16") {
                """
                    Returns a new list whose elements are those found in this list and not in the given collection.

                    `a.remove_all_copy(b)` is equivalent to `a - b`, where `a` and `b` are lists.

                    Examples:
                    - `[1].remove_all_copy([1])` returns `[]`
                    - `[1].remove_all_copy([2])` returns `[1]`
                    - `[1, 2, 3, 5].remove_all_copy([2, 3, 4])` returns `[1, 5]`
                """.comment()
                val self by self(Rt_ListValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the other collection",
                )
                body { evalSubList(self, values) }
            }

            function("retain_all_copy", "list<T>", since = "0.14.16") {
                """
                    Return a new list whose elements are those found in both this list and the given collection, or in
                    other words, the intersection of this list and the given collection.
                    @return a new list whose elements are the intersection of this list and the given collection

                    `a.retain_all_copy(b)` is equivalent to `a & b`, where `a` and `b` are lists.

                    Examples:
                    - `[1].retain_all_copy([1])` returns `[1]`
                    - `[1].retain_all_copy([2])` returns `[]`
                    - `[1, 2, 3].retain_all_copy([2, 3, 4])` returns `[2, 3]`
                """.comment()
                val self by self(Rt_ListValue)
                val values by param(
                    "collection<-T>",
                    cast = Rt_CollectionValue,
                    comment = "the other collection",
                )
                body { evalIntersectList(self, values) }
            }
        }
    }

    private fun calcSub(type: Rt_ValueClass<*>, list: MutableList<Rt_Value>, start: Long, end: Long): Rt_Value {
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (start < 0 || end < start || end > list.size) {
            throw Rt_Exception.common("fn:list.sub:args:${list.size}:$start:$end",
                "Invalid range: start = $start, end = $end, size = ${list.size}")
        }
        val r = list.subList(start.toInt(), end.toInt())
        return Rt_ListValue(type, r)
    }

    fun rtCheckRepeatArgs(s: Int, n: Long, type: String): Int {
        return if (n < 0) {
            throw Rt_Exception.common("fn:$type.repeat:n_negative:$n", "Negative count: $n")
        } else if (n > Integer.MAX_VALUE) {
            throw Rt_Exception.common("fn:$type.repeat:n_out_of_range:$n", "Count out of range: $n")
        } else {
            val total = Math.multiplyExact(s.toLong(), n) // Must never fail, but using multiplyExact() for extra safety
            if (total > Integer.MAX_VALUE) {
                throw Rt_Exception.common("fn:$type.repeat:too_big:$total", "Resulting size is too large: $s * $n = $total")
            }
            total.toInt()
        }
    }
}
