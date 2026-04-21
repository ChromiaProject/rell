/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

data class One<T>(val value: T)

/** **Project extension** - functionality provided by outer (dependent) projects, e.g. rell-postchain project may
 * provide an extension needed by a component of the rell-base project. This common parent class for all kinds of
 * [ProjExt]s is for the documentation purpose only. */
interface ProjExt

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
    operator fun <V> get(key: TypedKey<V>): V {
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

    fun get(): T = checkNotNull(local.get())
    fun getOpt(): T? = local.get()
}
