/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.common.hexStringToByteArray
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_Name
import java.util.*

fun String.hexStringToBytes(): Bytes = this.hexStringToByteArray().toBytes()

fun String.formatSafe(vararg args: Any?): String {
    return try {
        format(Locale.US, *args)
    } catch (e: IllegalFormatException) {
        this
    }
}

/** Non-deprecated version of [kotlin.String.capitalize]. */
fun String.capitalizeEx(): String {
    val c = first()
    return if (!c.isLowerCase()) this else replaceFirstChar { it.titlecase(Locale.US) }
}

/** Non-deprecated version of [kotlin.String.toUpperCase]. */
fun String.toUpperCaseEx(): String {
    return uppercase(Locale.getDefault())
}

/** Non-deprecated version of [kotlin.String.toLowerCase]. */
fun String.toLowerCaseEx(): String {
    return lowercase(Locale.getDefault())
}

fun String.nounWithArticle(): String {
    val c = this.getOrNull(0)
    if (c == null || !c.isLetter()) return this
    val article = if (c.uppercaseChar() in "AEIO") "an" else "a"
    return "$article $this"
}

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

class LazyPosString(val pos: S_Pos, val lazyStr: LazyString) {
    val str: String get() = lazyStr.value

    override fun toString() = lazyStr.toString()

    companion object {
        fun of(pos: S_Pos, value: String) = LazyPosString(pos, LazyString.of(value))
        fun of(pos: S_Pos, fn: () -> String) = LazyPosString(pos, LazyString.of(fn))
        fun of(cName: C_Name) = of(cName.pos, cName.str)
    }
}

class MsgString(s: String) {
    val normal = s.toLowerCaseEx()
    val upper = s.toUpperCaseEx()
    val capital = s.capitalizeEx()

    override fun equals(other: Any?) = other is MsgString && normal == other.normal
    override fun hashCode() = normal.hashCode()
    override fun toString() = normal
}
