/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

class Nullable<T: Any> private constructor(val value: T?) {
    override fun equals(other: Any?) = other === this || (other is Nullable<*> && value == other.value)
    override fun hashCode() = value?.hashCode() ?: 0
    override fun toString(): String = java.lang.String.valueOf(value)

    companion object {
        private val NULL: Nullable<Any> = Nullable(null)

        @Suppress("UNCHECKED_CAST")
        fun <T: Any> of(value: T? = null): Nullable<T> {
            return if (value != null) Nullable(value) else (NULL as Nullable<T>)
        }
    }
}

data class One<T>(val value: T)

/** **Project extension** - functionality provided by outer (dependent) projects, e.g. rell-postchain project may
 * provide an extension needed by a component of the rell-base project. This common parent class for all kinds of
 * [ProjExt]s is for the documentation purpose only. */
abstract class ProjExt

class MutableTypedKeyMap {
    private val map = mutableMapOf<TypedKey<Any>, Any?>()

    fun <V> put(key: TypedKey<V>, value: V) {
        @Suppress("UNCHECKED_CAST")
        key as TypedKey<Any>
        check(key !in map) { "Key $key is already present" }
        map[key] = value
    }

    fun toImmTypedKeyMap(): ImmTypedKeyMap = ImmTypedKeyMap(map.toImmMap())
}

class ImmTypedKeyMap(private val map: ImmMap<TypedKey<Any>, Any?> = immMapOf()) {
    @Suppress("UNCHECKED_CAST")
    fun <V> get(key: TypedKey<V>): V {
        key as TypedKey<Any>
        return map.getValue(key) as V
    }
}

class TypedKey<V>

class ThreadLocalContext<T>(private val defaultValue: T? = null) {
    private val local = ThreadLocal.withInitial<T> { defaultValue }

    fun <R> set(value: T, code: () -> R): R {
        val old = local.get()
        local.set(value)
        try {
            val res = code()
            return res
        } finally {
            local.set(old)
        }
    }

    fun get(): T {
        val res = local.get()
        check(res != null)
        return res
    }

    fun getOpt(): T? = local.get()
}

class VersionNumber(val items: ImmList<Int>): Comparable<VersionNumber> {
    init {
        require(items.isNotEmpty()) { "items is empty" }
        for (v in this.items) require(v >= 0) { "wrong version: ${this.items}" }
    }

    fun str(): String = items.joinToString(".")

    override fun compareTo(other: VersionNumber) = CommonUtils.compareLists(items, other.items)
    override fun equals(other: Any?) = other === this || (other is VersionNumber && items == other.items)
    override fun hashCode() = items.hashCode()
    override fun toString() = str()

    companion object {
        fun of(s: String): VersionNumber {
            require(s.matches(Regex("(0|[1-9][0-9]*)([.](0|[1-9][0-9]*))*"))) { "Invalid version format: '$s'" }
            val parts = s.split(".")
            val items = parts.mapToImmList { it.toInt() }
            return VersionNumber(items)
        }
    }
}

fun <T> checkEquals(actual: T, expected: T) {
    check(expected == actual) { "expected <$expected> actual <$actual>" }
}

fun <T> checkEquals(actual: T, expected: T, lazyMsg: () -> Any) {
    check(expected == actual) { "expected <$expected> actual <$actual>: ${lazyMsg()}" }
}
