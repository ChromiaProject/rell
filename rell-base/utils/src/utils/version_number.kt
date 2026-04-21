/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

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
