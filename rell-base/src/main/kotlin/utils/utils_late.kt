/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import java.util.function.Supplier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LateInit<T: Any>(private var fallback: T? = null) : ReadWriteProperty<Any?, T> {
    private var value: T? = null

    fun get(): T {
        var res = value
        if (res == null) {
            res = fallback
            checkNotNull(res) { "Value not initialized." }
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

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}
