/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.Multimap
import kotlinx.collections.immutable.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.minus as kminus
import kotlinx.collections.immutable.plus as kplus

class ListVsMap<K> private constructor(private val entries: ImmList<Map.Entry<K, *>>) {
    fun <W> listToMap(list: List<W>): ImmMap<K, W> {
        val copy = list.toImmList()
        checkEquals(copy.size, entries.size)
        return entries.mapIndexed { i, e -> e.key to copy[i] }.toImmMap()
    }

    companion object {
        fun <K, V> mapToList(map: Map<K, V>): Pair<List<V>, ListVsMap<K>> {
            val entries = map.entries.toImmList()
            val list = entries.mapToImmList { it.value }
            val listVsMap = ListVsMap(entries)
            return list to listVsMap
        }
    }
}

fun <T> chainToIterable(head: T?, nextGetter: (T) -> T?): Iterable<T> {
    return if (head == null) emptyList() else ChainIterable(head, nextGetter)
}

private class ChainIterable<T>(private val head: T, private val nextGetter: (T) -> T?): Iterable<T> {
    override fun iterator(): Iterator<T> = ChainIterator()

    private inner class ChainIterator: Iterator<T> {
        private var current: T? = head

        override fun hasNext() = current != null

        override fun next(): T {
            val cur = current
            cur ?: throw NoSuchElementException()
            current = nextGetter(cur)
            return cur
        }
    }
}

fun <T> Iterable<T>.startsWith(prefix: Iterable<T>): Boolean {
    val iter1 = iterator()
    val iter2 = prefix.iterator()
    while (iter2.hasNext()) {
        if (!iter1.hasNext()) return false
        if (iter1.next() != iter2.next()) return false
    }
    return true
}

@Suppress("unused")
fun <T> Array<out T?>.filterNotNullAllOrNull(): ImmList<T>? {
    val res: MutableList<T> = ArrayList(this.size)
    for (value in this) {
        value ?: return null
        res.add(value)
    }
    return res.toImmList()
}

fun <T> Iterable<T?>.filterNotNullAllOrNull(): ImmList<T>? {
    val res: MutableList<T> = ArrayList()
    for (value in this) {
        value ?: return null
        res.add(value)
    }
    return res.toImmList()
}

fun <T, R> Iterable<T>.mapNotNullAllOrNull(f: (T) -> R?): ImmList<R>? {
    val res: MutableList<R> = ArrayList()
    for (value in this) {
        val resValue = f(value)
        resValue ?: return null
        res.add(resValue)
    }
    return res.toImmList()
}

@Suppress("unused")
fun <T, R> Iterable<T>.mapIndexedNotNullAllOrNull(f: (Int, T) -> R?): ImmList<R>? {
    val res: MutableList<R> = ArrayList()
    for (entry in this.withIndex()) {
        val index = res.size
        val resValue = f(index, entry.value)
        resValue ?: return null
        res.add(resValue)
    }
    return res.toImmList()
}

fun <T, K, V> Iterable<T>.associateNotNullValues(f: (T) -> Pair<K, V?>): ImmMap<K, V> {
    return mapNotNull {
        val (k, v) = f(it)
        if (v == null) null else (k to v)
    }.toImmMap()
}

fun <T, R> Iterable<T>.mapView(op: (T) -> R): Iterable<R> = asSequence().map(op).asIterable()

inline fun <T> Iterable<T>.foldSimple(op: (T, T) -> T): T = reduce(op)

@Suppress("unused")
fun <T> Iterable<T>.separated(block: (T, T) -> T): List<T> {
    val res = mutableListOf<T>()
    var prev: T? = null
    var first = true
    for (cur in this) {
        if (!first && prev != null) {
            val sep = block(prev, cur)
            res.add(sep)
        }
        res.add(cur)
        prev = cur
        first = false
    }
    return res.toImmList()
}

fun <K, V, R> Map<K, V>.mapValuesNotNull(f: (Map.Entry<K, V>) -> R?): Map<K, R> {
    return mapNotNull {
        val r = f(it)
        if (r == null) null else (it.key to r)
    }.toImmMap()
}

fun <T> ImmList<T>.mapOrSame(f: (T) -> T): ImmList<T> {
    var res: MutableList<T>? = null

    for (i in this.indices) {
        val v = this[i]
        val v2 = f(v)
        if (res == null && v2 !== v) {
            res = ArrayList(this.size)
            for (j in 0 until i) {
                res.add(this[j])
            }
        }
        res?.add(v2)
    }

    return res?.toImmList() ?: this
}

fun <T> List<T>.mapIndexedOrSame(f: (Int, T) -> T): List<T> {
    var res: MutableList<T>? = null

    for (i in this.indices) {
        val v = this[i]
        val v2 = f(i, v)
        if (res == null && v2 !== v) {
            res = ArrayList(this.size)
            for (j in 0 until i) {
                res.add(this[j])
            }
        }
        res?.add(v2)
    }

    return res?.toImmList() ?: this
}

fun <T> List<T>.countWhile(predicate: (T) -> Boolean): Int {
    val i = this.indexOfFirst { !predicate(it) }
    return if (i >= 0) i else this.size
}

@Suppress("unused")
fun <T> List<T>.countLastWhile(predicate: (T) -> Boolean): Int {
    val i = this.indexOfLast { !predicate(it) }
    return if (i >= 0) (this.size - 1 - i) else this.size
}

@Suppress("unused")
fun <T> List<T>.dropView(n: Int): List<T> = subList(n, size)

fun <T, R> Iterable<T>.partitionMap(f: (T) -> Pair<R, Boolean>): Pair<ImmList<R>, ImmList<R>> {
    val first = mutableListOf<R>()
    val second = mutableListOf<R>()
    for (element in this) {
        val (value, pred) = f(element)
        val dst = if (pred) first else second
        dst.add(value)
    }
    return Pair(first.toImmList(), second.toImmList())
}

fun <T, K, V> Iterable<T>.groupAdjacent(f: (T) -> Pair<K, V>): List<Pair<K, List<V>>> {
    val res = mutableListOf<Pair<K, List<V>>>()
    val group = mutableListOf<V>()
    var groupKey: K? = null

    for (item in this) {
        val (key, value) = f(item)
        if (group.isNotEmpty() && key != groupKey && groupKey != null) {
            res.add(groupKey to group.toImmList())
            group.clear()
        }
        group.add(value)
        groupKey = key
    }

    if (group.isNotEmpty() && groupKey != null) {
        res.add(groupKey to group.toImmList())
    }

    return res.toImmList()
}

fun <T: Any> MutableList<T?>.computeIfAbsent(index: Int, f: () -> T): T {
    var n = this.size
    while (n <= index) {
        add(null)
        n += 1
    }
    return this[index] ?: run {
        val res = f()
        this[index] = res
        res
    }
}

fun <K, V> Map<K, V>.unionNoConflicts(m: Map<K, V>): Map<K, V> {
    val res = this.toMutableMap()
    for (entry in m.entries) {
        check(entry.key !in res) { "Key conflict: $entry" }
        res[entry.key] = entry.value
    }
    return res.toImmMap()
}

typealias ImmList<E> = ImmutableList<E>

fun <T> immListOf(): ImmList<T> = persistentListOf()
fun <T> immListOf(vararg values: T): ImmList<T> = persistentListOf(*values)
fun <T> immListOfNotNull(value: T?): ImmList<T> = if (value == null) immListOf() else immListOf(value)
fun <T> Iterable<T>.toImmList(): ImmList<T> = toPersistentList()
fun <T> Array<T>.toImmList(): ImmList<T> = toPersistentList()

fun <T> ImmList<T>?.orEmpty(): ImmList<T> = this ?: immListOf()

fun <T> ImmList(size: Int, init: (Int) -> T): ImmList<T> = List(size, init).toPersistentList()

@Deprecated("redundant toImmList()", ReplaceWith("this"))
fun <T> ImmList<T>.toImmList(): ImmList<T> = this

inline fun <T, R> Iterable<T>.mapToImmList(transform: (T) -> R): ImmList<R> = persistentListOf<R>().mutate {
    mapTo(it, transform)
}

inline fun <K, V, R> Map<out K, V>.mapToImmList(transform: (Map.Entry<K, V>) -> R): ImmList<R> =
    persistentListOf<R>().mutate {
        mapTo(it, transform)
    }

inline fun <T, R> Array<out T>.mapToImmList(transform: (T) -> R): ImmList<R> = persistentListOf<R>().mutate {
    mapTo(it, transform)
}

inline fun <T, R> Sequence<T>.mapToImmList(transform: (T) -> R): ImmList<R> = persistentListOf<R>().mutate {
    mapTo(it, transform)
}

inline fun <T, R: Any> Iterable<T>.mapNotNullToImmList(transform: (T) -> R?): ImmList<R> =
    persistentListOf<R>().mutate {
        mapNotNullTo(it, transform)
    }

inline fun <K, V, R: Any> Map<out K, V>.mapNotNullToImmList(transform: (Map.Entry<K, V>) -> R?): ImmList<R> =
    persistentListOf<R>().mutate {
        mapNotNullTo(it, transform)
    }

inline fun <T> Iterable<T>.filterToImmList(predicate: (T) -> Boolean): ImmList<T> = persistentListOf<T>().mutate {
    filterTo(it, predicate)
}

inline fun <T, R> Iterable<T>.mapIndexedToImmList(transform: (index: Int, T) -> R): ImmList<R> =
    persistentListOf<R>().mutate {
        mapIndexedTo(it, transform)
    }

inline fun <T, R> Array<out T>.mapIndexedToImmList(transform: (index: Int, T) -> R): ImmList<R> =
    persistentListOf<R>().mutate {
        mapIndexedTo(it, transform)
    }

inline fun <T, R> Iterable<T>.flatMapToImmList(transform: (T) -> Iterable<R>): ImmList<R> =
    persistentListOf<R>().mutate {
        flatMapTo(it, transform)
    }

fun <T: Any> Iterable<T?>.filterNotNullToImmList(): ImmList<T> = persistentListOf<T>().mutate {
    filterNotNullTo(it)
}


typealias ImmSet<E> = ImmutableSet<E>

fun <T> immSetOf(): ImmSet<T> = persistentSetOf()
fun <T> immSetOf(vararg values: T): ImmSet<T> = persistentSetOf(*values)
fun <T> Iterable<T>.toImmSet(): ImmSet<T> = toPersistentSet()
fun <T> Array<T>.toImmSet(): ImmSet<T> = toPersistentSet()

fun <T: Any> immSetOfNotNull(element: T?): ImmSet<T> = if (element != null) immSetOf(element) else immSetOf()


@Deprecated("redundant toImmSet()", ReplaceWith("this"))
fun <T> ImmSet<T>.toImmSet(): ImmSet<T> = this


typealias ImmMap<K, V> = ImmutableMap<K, V>

fun <K, V> immMapOf(vararg entries: Pair<K, V>): ImmMap<K, V> = mapOf(*entries).toImmMap()

fun <K, V> ImmMap<K, V>?.orEmpty(): ImmMap<K, V> = this ?: immMapOf()

@Suppress("unused")
fun <K, V> immMapOfNotNullValues(vararg entries: Pair<K, V?>): ImmMap<K, V> {
    return persistentMapOf<K, V>().mutate {
        for ((k, v) in entries) {
            if (v != null) it[k] = v
        }
    }
}

fun <K, V> Map<K, V>.toImmMap(): ImmMap<K, V> = toPersistentMap()
fun <K, V> Iterable<Pair<K, V>>.toImmMap(): ImmMap<K, V> = toMap().toImmMap()
fun <K, V> Array<out Pair<K, V>>.toImmMap(): ImmMap<K, V> = toMap().toImmMap()

@Deprecated("redundant toImmMap()", ReplaceWith("this"))
fun <K, V> ImmMap<K, V>.toImmMap(): ImmMap<K, V> = this

inline fun <T, K, V> Iterable<T>.associateToImmMap(transform: (T) -> Pair<K, V>): ImmMap<K, V> =
    persistentMapOf<K, V>().mutate {
        associateTo(it, transform)
    }

inline fun <T, K> Iterable<T>.associateByToImmMap(keySelector: (T) -> K): ImmMap<K, T> =
    persistentMapOf<K, T>().mutate {
        associateByTo(it, keySelector)
    }

inline fun <K, V> Iterable<K>.associateWithToImmMap(valueSelector: (K) -> V): ImmMap<K, V> =
    persistentMapOf<K, V>().mutate {
        associateWithTo(it, valueSelector)
    }

inline fun <K, V> Iterable<K>.associateWithToImmList(valueSelector: (K) -> V): ImmMap<K, V> =
    persistentMapOf<K, V>().mutate {
        associateWithTo(it, valueSelector)
    }

inline fun <K, V, R> Map<out K, V>.mapValuesToImmMap(transform: (Map.Entry<K, V>) -> R): ImmMap<K, R> =
    persistentMapOf<K, R>().mutate {
        mapValuesTo(it, transform)
    }

inline fun <K, V, R> Map<out K, V>.mapKeysToImmMap(transform: (Map.Entry<K, V>) -> R): ImmMap<R, V> =
    persistentMapOf<R, V>().mutate {
        mapKeysTo(it, transform)
    }


typealias ImmMultimap<K, V> = ImmutableMultimap<K, V>

fun <K: Any, V: Any> immMultimapOf(): ImmMultimap<K, V> = ImmutableMultimap.of()
fun <K: Any, V: Any> mutableMultimapOf(): Multimap<K, V> = LinkedListMultimap.create()
fun <K: Any, V: Any> Multimap<K, V>.toImmMultimap(): ImmMultimap<K, V> = ImmutableMultimap.copyOf(this)

@Deprecated("redundant toImmMultimap()")
fun <K, V> ImmMultimap<K, V>.toImmMultimap(): ImmMultimap<K, V> = this

fun <K: Any, V: Any> Multimap<K, V>.toMutableMultimap(): Multimap<K, V> = LinkedListMultimap.create(this)

fun <T, K: Any, V: Any> Iterable<T>.toImmMultimap(fn: (T) -> Pair<K, V>): ImmMultimap<K, V> {
    val m = mutableMultimapOf<K, V>()
    for (e in this) {
        val (key, value) = fn(e)
        m.put(key, value)
    }
    return m.toImmMultimap()
}

fun <T: Any, K: Any> Iterable<T>.toImmMultimapKey(fn: (T) -> K): ImmMultimap<K, T> {
    return this.toImmMultimap { fn(it) to it }
}

fun <K: Any, V: Any> Iterable<Pair<K, V>>.toImmMultimap(): ImmMultimap<K, V> {
    val m = mutableMultimapOf<K, V>()
    for ((k, v) in this) {
        m.put(k, v)
    }
    return m.toImmMultimap()
}

fun <K: Any, V: Any> Map<K, Iterable<V>>.toImmMultimap(): ImmMultimap<K, V> {
    val map = mutableMultimapOf<K, V>()
    for ((k, v) in this) {
        map.putAll(k, v)
    }
    return map.toImmMultimap()
}


fun <K, V> MutableMap<K, V>.putAllAbsent(map: Map<K, V>) {
    for ((key, value) in map) {
        if (key !in this) {
            put(key, value)
        }
    }
}

fun <T> Iterable<T>.toPair(): Pair<T, T> {
    return when (this) {
        is List -> {
            checkEquals(size, 2) { "Expected a list of size 2, but $size." }
            this[0] to this[1]
        }

        else -> {
            val iter = this.iterator()
            val first = iter.next()
            val second = iter.next()
            check(!iter.hasNext()) { "Iterable has more than two elements." }
            first to second
        }
    }
}


operator fun <E> ImmList<E>.plus(element: E): ImmList<E> = toPersistentList().add(element)
operator fun <E> ImmList<E>.plus(elements: Iterable<E>): ImmList<E> = toPersistentList().kplus(elements)
operator fun <E> ImmList<E>.plus(elements: Array<out E>): ImmList<E> = toPersistentList().kplus(elements)
operator fun <E> ImmList<E>.plus(elements: Sequence<E>): ImmList<E> = toPersistentList().kplus(elements)

operator fun <E> ImmSet<E>.plus(element: E): ImmSet<E> = toPersistentSet().kplus(element)
operator fun <E> ImmSet<E>.minus(element: E): ImmSet<E> = toPersistentSet().kminus(element)
operator fun <E> ImmSet<E>.plus(elements: Iterable<E>): ImmSet<E> = toPersistentSet().kplus(elements)
operator fun <E> ImmSet<E>.plus(elements: Array<out E>): ImmSet<E> = toPersistentSet().kplus(elements)
operator fun <E> ImmSet<E>.plus(elements: Sequence<E>): ImmSet<E> = toPersistentSet().kplus(elements)
operator fun <E> ImmSet<E>.minus(elements: Iterable<E>): ImmSet<E> = toPersistentSet().kminus(elements)

operator fun <K, V> ImmMap<out K, V>.plus(pair: Pair<K, V>): ImmMap<K, V> = toPersistentMap().kplus(pair)
operator fun <K, V> ImmMap<out K, V>.plus(pairs: Iterable<Pair<K, V>>): ImmMap<K, V> = toPersistentMap().kplus(pairs)
operator fun <K, V> ImmMap<out K, V>.plus(pairs: Array<out Pair<K, V>>): ImmMap<K, V> = toPersistentMap().kplus(pairs)
operator fun <K, V> ImmMap<out K, V>.plus(pairs: Sequence<Pair<K, V>>): ImmMap<K, V> = toPersistentMap().putAll(pairs)
operator fun <K, V> ImmMap<out K, V>.plus(map: Map<out K, V>): ImmMap<K, V> = toPersistentMap().kplus(map)
