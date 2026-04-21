/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

/** Non-deprecated version of [capitalize][String.capitalize]. */
fun String.capitalizeEx(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

class MsgString(s: String) {
    val normal = s.lowercase()
    val capital = s.capitalizeEx()

    override fun equals(other: Any?) = other is MsgString && normal == other.normal
    override fun hashCode() = normal.hashCode()
    override fun toString() = normal
}
