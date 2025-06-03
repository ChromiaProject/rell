/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import java.util.function.Supplier

class LateInit<T: Any>(private var fallback: T? = null) {
    val getter = LateGetter(this)
    val setter = LateSetter(this)

    private var value: T? = null

    fun isSet(): Boolean = value != null

    fun get(): T {
        var res = value
        if (res == null) {
            res = fallback
            checkNotNull(res) { "value not initialized" }
            value = res
            fallback = null
        }
        return res
    }

    fun set(v: T) {
        check(value == null) { "value already initialized with: <$value>" }
        value = v
        fallback = null
    }
}

class LateGetter<T: Any>(private val init: LateInit<T>): Supplier<T> {
    override fun get(): T = init.get()

    companion object {
        fun <T: Any> of(value: T): LateGetter<T> {
            val init = LateInit<T>()
            init.set(value)
            return init.getter
        }
    }
}

class LateSetter<T: Any>(private val init: LateInit<T>) {
    fun set(value: T) = init.set(value)
}
