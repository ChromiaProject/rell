/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.google.common.math.LongMath
import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VirtualListType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_ListComparator
import net.postchain.rell.base.utils.immListOf

object Lib_Type_List {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("list", since = SINCE0) {
            comment("""
                Represents a mutable array list. Subtype of `collection<T>`.

                @see collection for inherited values and methods
            """)
            generic("T")
            parent("collection<T>")

            rType { t -> R_ListType(t) }

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new empty list.")
                bodyMeta {
                    val elementType = fnBodyMeta.typeArg("T")
                    val rKind = R_CollectionKind_List(R_ListType(elementType))
                    body { ->
                        rKind.makeRtValue(immListOf())
                    }
                }
            }

            constructor(pure = true, since = SINCE0) {
                comment("Construct a new list by copying the values from another iterable.")
                param("values", type = "iterable<-T>") {
                    comment("an iterable containing values with which to initialize this list")
                }
                bodyMeta {
                    val elementType = fnBodyMeta.typeArg("T")
                    val rKind = R_CollectionKind_List(R_ListType(elementType))
                    body { arg ->
                        val iterable = arg.asIterable()
                        rKind.makeRtValue(iterable)
                    }
                }
            }

            function("get", "T", pure = true, since = SINCE0) {
                comment("""
                    Retrieve the element at the specified index.
                    @throws exception if the provided index is out of bounds
                """)
                param("index", "integer", comment = "the index of the element to retrieve")
                body { self, index ->
                    val list = self.asList()
                    val i = index.asInteger()
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
                comment("""
                    Search for the first occurrence of the specified element within this list.
                    @return the index of the first occurrence of the element within this list, or `-1` if the element
                    does not occur in this list
                """)
                alias("indexOf", deprecated = C_MessageType.ERROR, since = SINCE0)
                param("value", "T", comment = "the element for which to search")
                body { self, value ->
                    val list = self.asList()
                    Rt_IntValue.get(list.indexOf(value).toLong())
                }
            }

            function("remove_at", "T", since = "0.9.0") {
                comment("""
                    Remove and return the element at the specified index. The indices of elements occurring after the
                    specified index decrease by 1, and the size of this list decreases by 1.
                    @return the element that was at the specified index
                    @throws exception if the index is out of bounds
                """)
                alias("removeAt", deprecated = C_MessageType.ERROR, since = SINCE0)
                param("index", "integer", comment = "the index of the element to remove")
                body { self, index ->
                    val list = self.asList()
                    val i = index.asInteger()

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
                comment("""
                    Returns a sublist of this list starting from the specified index (inclusive).

                    Equivalent to `list.sub(index, list.size())`.

                    Note that:
                    - `my_list.sub(my_list.size() - 1)` returns a list containing only the last element of `my_list`
                        (assuming `my_list.size > 0`)
                    - `my_list.sub(my_list.size())` returns an empty list
                    - `my_list.sub(my_list.size() + 1)` throws an exception
                    @throws exception if the `start` index greater than this list's size
                """)
                param("start", "integer", comment = "the starting index of the sublist (inclusive)")
                body { self, start ->
                    val type = self.type()
                    val list = self.asList()
                    val startIndex = start.asInteger()
                    calcSub(type, list, startIndex, list.size.toLong())
                }
            }

            function("sub", "list<T>", pure = true, since = SINCE0) {
                comment("""
                    Returns a sublist of this list starting from the specified start index (inclusive) to the specified
                    end index (exclusive).

                    For the list `my_list`, `my_list.sub(start, my_list.size())` is equivalent to `my_list.sub(start)`.

                    @throws exception when:
                    - the `start` or `end` indexes are greater than this list's size
                    - the `start` index is greater than the `end` index
                """)
                param("start", "integer", comment = "the start index of the sublist (inclusive)")
                param("end", "integer", comment = "the end index of the sublist (exclusive)")
                body { self, start, end ->
                    val type = self.type()
                    val list = self.asList()
                    val startIndex = start.asInteger()
                    val endIndex = end.asInteger()
                    calcSub(type, list, startIndex, endIndex)
                }
            }

            function("set", "T", since = SINCE0) {
                comment("""
                    Set the element at the specified index, overwriting the element that was previously at that index.
                    The size of the list is unchanged.
                    @return the overwritten element
                    @throws exception if the index is out of bounds
                """)
                alias("_set", deprecated = C_MessageType.WARNING, since = SINCE0)
                param("index", "integer", comment = "the index of the element to set")
                param("value", "T", comment = "the value to set")
                body { self, index, value ->
                    val list = self.asList()
                    val i = index.asInteger()

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
                comment("""
                    Insert a value at the specified index. Any elements previously occurring at and after the specified
                    index have their indices increased by 1, and the size of the list increases by 1.

                    Note that for a list `my_list`:
                    - `my_list.add(my_list.size(), x)` "appends" `x` to the end of the list
                    - `my_list.add(my_list.size() + 1, x)` throws an exception

                    Note also that `my_list.add(my_list.size(), x)` is equivalent to `my_list.add(x)` (inherited from
                    `collection<T>`).
                    @return `true`
                    @throws exception if the index is out of bounds
                """)
                param("index", "integer", comment = "The index at which to add the element.")
                param("value", "T", comment = "The value to add.")
                body { self, index, value ->
                    val list = self.asList()
                    val i = index.asInteger()

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
                comment("""
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
                """)
                alias("addAll", deprecated = C_MessageType.ERROR, since = SINCE0)
                param("index", "integer", comment = "the starting index at which to add the elements")
                param("values", "collection<-T>", comment = "the collection containing elements to add")
                body { self, index, values ->
                    val list = self.asList()
                    val i = index.asInteger()
                    val col = values.asCollection()

                    if (i < 0 || i > list.size) {
                        throw Rt_Exception.common("fn:list.add_all:index:${list.size}:$i", "Index out of range: $i (size ${list.size})")
                    }

                    val r = list.addAll(i.toInt(), col)
                    Rt_BooleanValue.get(r)
                }
            }

            function("sort", "unit", since = "0.11.0") {
                comment("Sort this list in place.")
                alias("_sort", deprecated = C_MessageType.WARNING, since = "0.8.0")
                bodyMeta {
                    val valueType = fnBodyMeta.typeArg("T")
                    val comparator = Lib_Type_Collection.getSortComparator(this, valueType)
                    body { a ->
                        val list = a.asList()
                        list.sortWith(comparator)
                        Rt_UnitValue
                    }
                }
            }

            function("repeat", "list<T>", since = "0.11.0") {
                comment("""
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
                """)
                param("n", "integer", comment = "the number of times to repeat this list")
                body { self, n ->
                    val list = self.asList()
                    val nRepeats = n.asInteger()

                    val total = rtCheckRepeatArgs(list.size, nRepeats, "list")

                    val resList: MutableList<Rt_Value> = ArrayList(total)
                    if (nRepeats > 0 && list.isNotEmpty()) {
                        for (i in 0 until nRepeats) {
                            resList.addAll(list)
                        }
                    }

                    Rt_ListValue(self.type(), resList)
                }
            }

            function("reverse", "unit", since = "0.11.0") {
                comment("""
                    Reverses the order of the elements in the list in place.

                    Where this list is bound to the variable `l`, then the statement `l.reverse();` is equivalent to
                    `l = l.reversed();`.
                """)
                body { self ->
                    val list = self.asList()
                    list.reverse()
                    Rt_UnitValue
                }
            }

            function("reversed", "list<T>", since = "0.11.0") {
                comment("""
                    Returns a new list with the elements of this list in reverse order.

                    Examples:
                    - `[].reversed()` returns `[]`
                    - `[1].reversed()` returns `[1]`
                    - `[1, 2, 3].reversed()` returns `[3, 2, 1]`
                """)
                body { self ->
                    val list = self.asList()
                    val resList = list.toMutableList()
                    resList.reverse()
                    Rt_ListValue(self.type(), resList)
                }
            }
        }
    }

    private fun calcSub(type: R_Type, list: MutableList<Rt_Value>, start: Long, end: Long): Rt_Value {
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
            val total = LongMath.checkedMultiply(s.toLong(), n) // Must never fail, but using checkedMultiply() for extra safety
            if (total > Integer.MAX_VALUE) {
                throw Rt_Exception.common("fn:$type.repeat:too_big:$total", "Resulting size is too large: $s * $n = $total")
            }
            total.toInt()
        }
    }
}

private class R_CollectionKind_List(type: R_Type): R_CollectionKind(type) {
    override fun makeRtValue(col: Iterable<Rt_Value>): Rt_Value {
        val list = mutableListOf<Rt_Value>()
        list.addAll(col)
        return Rt_ListValue(type, list)
    }
}

class R_ListType(elementType: R_Type): R_CollectionType(elementType, "list") {
    val virtualType = R_VirtualListType(this)

    override fun equals0(other: R_Type): Boolean = other is R_ListType && elementType == other.elementType
    override fun hashCode0() = elementType.hashCode()

    override fun fromCli(s: String): Rt_Value = Rt_ListValue(this, s.split(",").map { elementType.fromCli(it) }.toMutableList())
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_List(this)
    override fun getLibTypeDef() = Lib_Rell.LIST_TYPE

    override fun comparator(): Comparator<Rt_Value>? {
        val elemComparator = elementType.comparator()
        return if (elemComparator == null) null else Rt_ListComparator(elemComparator)
    }
}

class Rt_ListValue(private val type: R_Type, private val elements: MutableList<Rt_Value>): Rt_Value() {
    init {
        check(type is R_ListType) { "wrong type: ${type.str()}" }
    }

    override val valueType = Rt_CoreValueTypes.LIST.type()

    override fun type() = type
    override fun asIterable(): Iterable<Rt_Value> = elements
    override fun asCollection() = elements
    override fun asList() = elements
    override fun toFormatArg() = elements

    override fun equals(other: Any?) = other === this || (other is Rt_ListValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, elements)
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
        fun checkIndex(size: Int, index: Long) {
            if (index < 0 || index >= size) {
                throw Rt_Exception.common("list:index:$size:$index", "List index out of bounds: $index (size $size)")
            }
        }

        fun strCode(type: R_Type, elements: List<Rt_Value?>): String {
            val elems = elements.joinToString(",") { it?.strCode(false) ?: "null" }
            return "${type.strCode()}[$elems]"
        }
    }
}

private class GtvRtConversion_List(type: R_ListType): GtvRtConversion_Collection(type) {
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val elementType = type.elementType
        val array = GtvRtUtils.gtvToArray(ctx, gtv, type)
        val rtList = array.map { elementType.gtvToRt(ctx, it) }
        return ctx.rtValue {
            Rt_ListValue(type, rtList.toMutableList())
        }
    }
}
