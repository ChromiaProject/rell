/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import com.google.common.collect.*
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.runtime.utils.toGtv
import org.apache.commons.collections4.IterableUtils
import java.util.*

// Values are restricted to Any, because Guava immutable collections don't allow null values.

class ListVsMap<K: Any> private constructor(private val entries: List<Map.Entry<K, *>>) {
    fun <W: Any> listToMap(list: List<W>): Map<K, W> {
        val copy = list.toImmList()
        checkEquals(copy.size, entries.size)
        return entries.mapIndexed { i, e -> e.key to copy[i] }.toImmMap()
    }

    companion object {
        fun <K: Any, V: Any> mapToList(map: Map<K, V>): Pair<List<V>, ListVsMap<K>> {
            val entries = map.entries.toImmList()
            val list = entries.map { it.value }.toImmList()
            val listVsMap = ListVsMap(entries)
            return Pair(list, listVsMap)
        }
    }
}

fun <T> chainToIterable(head: T?, nextGetter: (T) -> T?): Iterable<T> {
    return if (head == null) IterableUtils.emptyIterable() else ChainIterable(head, nextGetter)
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

fun <T: Any> Array<out T?>.filterNotNullAllOrNull(): List<T>? {
    val res: MutableList<T> = ArrayList(this.size)
    for (value in this) {
        value ?: return null
        res.add(value)
    }
    return res.toImmList()
}

fun <T: Any> Iterable<T?>.filterNotNullAllOrNull(): List<T>? {
    val res: MutableList<T> = ArrayList()
    for (value in this) {
        value ?: return null
        res.add(value)
    }
    return res.toImmList()
}

fun <T, R: Any> Iterable<T>.mapNotNullAllOrNull(f: (T) -> R?): List<R>? {
    val res: MutableList<R> = ArrayList()
    for (value in this) {
        val resValue = f(value)
        resValue ?: return null
        res.add(resValue)
    }
    return res.toImmList()
}

fun <T, R: Any> Iterable<T>.mapIndexedNotNullAllOrNull(f: (Int, T) -> R?): List<R>? {
    val res: MutableList<R> = ArrayList()
    for (entry in this.withIndex()) {
        val index = res.size
        val resValue = f(index, entry.value)
        resValue ?: return null
        res.add(resValue)
    }
    return res.toImmList()
}

fun <T, K: Any, V: Any> Iterable<T>.associateNotNullValues(f: (T) -> Pair<K, V?>): Map<K, V> {
    return mapNotNull {
        val (k, v) = f(it)
        if (v == null) null else (k to v)
    }.toImmMap()
}

fun <T: Any> List<T>.mapOrSame(f: (T) -> T): List<T> {
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

    return if (res == null) this else res.toImmList()
}

fun <T: Any> List<T>.mapIndexedOrSame(f: (Int, T) -> T): List<T> {
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

    return if (res == null) this else res.toImmList()
}

fun <T> List<T>.countWhile(predicate: (T) -> Boolean): Int {
    val i = this.indexOfFirst { !predicate(it) }
    return if (i >= 0) i else this.size
}

fun <T> List<T>.countLastWhile(predicate: (T) -> Boolean): Int {
    val i = this.indexOfLast { !predicate(it) }
    return if (i >= 0) (this.size - 1 - i) else this.size
}

fun <T, R: Any> Iterable<T>.partitionMap(f: (T) -> Pair<R, Boolean>): Pair<List<R>, List<R>> {
    val first = mutableListOf<R>()
    val second = mutableListOf<R>()
    for (element in this) {
        val (value, pred) = f(element)
        val dst = if (pred) first else second
        dst.add(value)
    }
    return Pair(first.toImmList(), second.toImmList())
}

fun <T: Any> MutableList<T?>.computeIfAbsent(index: Int, f: () -> T): T {
    var n = this.size
    while (n <= index) {
        add(null)
        n += 1
    }

    var res = this[index]
    if (res == null) {
        res = f()
        this[index] = res
    }

    return res
}

fun <K: Any, V: Any> Map<K, V>.unionNoConflicts(m: Map<K, V>): Map<K, V> {
    val res = this.toMutableMap()
    for (entry in m.entries) {
        check(entry.key !in res) { "Key conflict: $entry" }
        res[entry.key] = entry.value
    }
    return res.toImmMap()
}

fun <T: Any> immListOf(vararg values: T): List<T> = ImmutableList.copyOf(values)
fun <T: Any> immListOfNotNull(value: T?): List<T> = if (value == null) immListOf() else immListOf(value)
fun <T: Any> Iterable<T>.toImmList(): List<T> = ImmutableList.copyOf(this)
fun <T: Any> Array<T>.toImmList(): List<T> = ImmutableList.copyOf(this)

fun <T: Any> immSetOf(): Set<T> = ImmutableSet.of()
fun <T: Any> immSetOf(vararg values: T): Set<T> = ImmutableSet.copyOf(values)
fun <T: Any> Iterable<T>.toImmSet(): Set<T> = ImmutableSet.copyOf(this)
fun <T: Any> Array<T>.toImmSet(): Set<T> = ImmutableSet.copyOf(this)

fun <K: Any, V: Any> immMapOf(vararg entries: Pair<K, V>): Map<K, V> = mapOf(*entries).toImmMap()
fun <K: Any, V: Any> Map<K, V>.toImmMap(): Map<K, V> = ImmutableMap.copyOf(this)
fun <K: Any, V: Any> Iterable<Pair<K, V>>.toImmMap(): Map<K, V> = toMap().toImmMap()

fun <K: Any, V: Any> immMultimapOf(): Multimap<K, V> = ImmutableMultimap.of()
fun <K: Any, V: Any> mutableMultimapOf(): Multimap<K, V> = LinkedListMultimap.create()
fun <K: Any, V: Any> Multimap<K, V>.toImmMultimap(): Multimap<K, V> = ImmutableMultimap.copyOf(this)
fun <K: Any, V: Any> Multimap<K, V>.toMutableMultimap(): Multimap<K, V> = LinkedListMultimap.create(this)

fun <T, K: Any, V: Any> Iterable<T>.toImmMultimap(fn: (T) -> Pair<K, V>): Multimap<K, V> {
    val m = mutableMultimapOf<K, V>()
    for (e in this) {
        val (key, value) = fn(e)
        m.put(key, value)
    }
    return m.toImmMultimap()
}

fun <T: Any, K: Any> Iterable<T>.toImmMultimapKey(fn: (T) -> K): Multimap<K, T> {
    return this.toImmMultimap { fn(it) to it }
}

fun <K: Any, V: Any> Iterable<Pair<K, V>>.toImmMultimap(): Multimap<K, V> {
    val m = mutableMultimapOf<K, V>()
    for ((k, v) in this) {
        m.put(k, v)
    }
    return m.toImmMultimap()
}

fun <K: Any, V: Any> Map<K, Iterable<V>>.toImmMultimap(): Multimap<K, V> {
    val map = mutableMultimapOf<K, V>()
    for ((k, v) in this) {
        map.putAll(k, v)
    }
    return map.toImmMultimap()
}

fun <K: Any, V: Any> Multimap<K, V>.toImmMapOfList(): Map<K, List<V>> {
    return this.asMap()
        .mapValues { it.value.toImmList() }
        .toImmMap()
}

fun <T: Any> mutableMultisetOf(): Multiset<T> = LinkedHashMultiset.create()
fun <T: Any> Multiset<T>.toImmMultiset(): Multiset<T> = ImmutableMultiset.copyOf(this)

fun <K, V> MutableMap<K, V>.putAllAbsent(map: Map<K, V>) {
    for ((key, value) in map) {
        if (key !in this) {
            put(key, value)
        }
    }
}

fun <T> queueOf(vararg values: T): Queue<T> {
    val res = ArrayDeque<T>()
    res.addAll(values)
    return res
}

fun <T> Iterable<T>.toPair(): Pair<T, T> {
    val iter = this.iterator()
    val first = iter.next()
    val second = iter.next()
    check(!iter.hasNext())
    return first to second
}

// Needs to be in a different file than List<Gtv>.toGtv() because of a name conflict...
fun List<String>.toGtv(): Gtv = GtvFactory.gtv(this.map { it.toGtv() })
