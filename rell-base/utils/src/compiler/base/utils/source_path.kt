/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.utils.*

class C_CodeMsg(val code: String, val msg: String) {
    override fun toString() = code
}

infix fun String.toCodeMsg(that: String): C_CodeMsg = C_CodeMsg(this, that)

class C_CommonError(val code: String, val msg: String): RuntimeException(msg) {
    constructor(codeMsg: C_CodeMsg): this(codeMsg.code, codeMsg.msg)
}

class C_SourcePath private constructor(val parts: ImmList<String>): Comparable<C_SourcePath> {
    private val str = parts.joinToString("/")

    fun add(path: C_SourcePath) = C_SourcePath(parts + path.parts)

    fun add(part: String): C_SourcePath {
        if (!validate(part)) {
            throw errBadPath(part)
        }
        return C_SourcePath(parts + part)
    }

    fun parent() = C_SourcePath(parts.subList(0, parts.size - 1))
    fun str() = str

    override fun compareTo(other: C_SourcePath) = CommonUtils.compareLists(parts, other.parts)
    override fun equals(other: Any?) = other === this || (other is C_SourcePath && parts == other.parts)
    override fun hashCode() = parts.hashCode()
    override fun toString() = str()

    companion object {
        val EMPTY = C_SourcePath(immListOf())

        fun ofParts(parts: List<String>): C_SourcePath {
            if (!validate(parts)) {
                val str = parts.joinToString("/")
                throw errBadPath(str)
            }
            return C_SourcePath(parts.toImmList())
        }

        fun ofPartsOrNull(parts: List<String>): C_SourcePath? {
            if (!validate(parts)) {
                return null
            }
            return C_SourcePath(parts.toImmList())
        }

        fun parse(path: String): C_SourcePath = parseOrNull(path) ?: throw errBadPath(path)

        fun parseOrNull(path: String): C_SourcePath? {
            val parts = path.split('/', '\\').toImmList()

            return when {
                parts.isEmpty() -> null
                !validate(parts) -> null
                else -> C_SourcePath(parts)
            }
        }

        private fun validate(parts: List<String>): Boolean = parts.all { validate(it) }
        private fun validate(part: String): Boolean = part != "" && part != "." && part != ".."
        private fun errBadPath(str: String): C_CommonError = C_CommonError("invalid_path:$str", "Invalid path: '$str'")
    }
}
