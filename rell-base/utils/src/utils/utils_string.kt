/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.common.hexStringToByteArray
import java.util.*

fun String.hexStringToBytes(): Bytes = this.hexStringToByteArray().toBytes()

fun String.formatEx(vararg args: Any?): String = format(Locale.ROOT, *args)

sealed class LazyString {
    abstract val value: String
    final override fun toString() = value

    companion object {
        fun of(value: String): LazyString = ValueLazyString(value)
        fun of(fn: () -> String): LazyString = FnLazyString(fn)
    }
}

private class ValueLazyString(override val value: String): LazyString()

private class FnLazyString(private val fn: () -> String): LazyString() {
    override val value by lazy {
        val res = fn()
        res
    }
}
