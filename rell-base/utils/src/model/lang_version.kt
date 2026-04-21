/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.VersionNumber

class R_LangVersion(private val ver: VersionNumber): Comparable<R_LangVersion> {
    init {
        require(ver.items.size == 3) { "invalid version: $ver" }
    }

    fun parts(): ImmList<Int> = ver.items
    fun str(): String = ver.str()

    override fun compareTo(other: R_LangVersion) = ver.compareTo(other.ver)
    override fun equals(other: Any?) = other === this || (other is R_LangVersion && ver == other.ver)
    override fun hashCode() = ver.hashCode()
    override fun toString() = ver.toString()

    companion object {
        fun of(s: String): R_LangVersion {
            val ver = VersionNumber.of(s)
            return R_LangVersion(ver)
        }

        fun of(parts: ImmList<Int>): R_LangVersion = R_LangVersion(VersionNumber(parts))
    }
}
