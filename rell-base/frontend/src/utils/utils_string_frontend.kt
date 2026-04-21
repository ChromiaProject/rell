/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_Name
import java.util.*

internal fun String.formatOrOriginal(vararg args: Any?): String {
    return try {
        formatEx(*args)
    } catch (_: IllegalFormatException) {
        this
    }
}

internal fun String.nounWithArticle(): String {
    val c = this.getOrNull(0)
    if (c == null || !c.isLetter()) return this
    val article = if (c.uppercaseChar() in "AEIO") "an" else "a"
    return "$article $this"
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
