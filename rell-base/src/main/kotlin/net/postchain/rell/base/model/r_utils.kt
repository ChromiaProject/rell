/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.VersionNumber
import net.postchain.rell.base.utils.toImmList
import org.apache.commons.lang3.StringUtils
import java.util.*

class R_DefinitionId(val module: String, val definition: String) {
    override fun toString() = str(module, definition)

    companion object {
        val ERROR = R_DefinitionId("<error>", "<error>")

        fun str(module: String, definition: String) = "$module:$definition"
    }
}

class R_FilePos(val file: String, val line: Int) {
    override fun toString() = "$file:$line"
}

class R_StackPos(val def: R_DefinitionId, val file: R_FilePos) {
    override fun toString() = "$def($file)"
}

sealed class R_GenericQualifiedName<T: R_GenericQualifiedName<T>>(parts: List<R_Name>): Comparable<T> {
    val parts = parts.toImmList()

    private val str = parts.joinToString(".")

    fun str() = str
    fun displayStr() = if (str.isEmpty()) "''" else str
    fun isEmpty() = parts.isEmpty()
    fun size() = parts.size

    fun startsWith(qName: T): Boolean {
        val prefParts = qName.parts
        if (prefParts.size > parts.size) return false
        for (i in prefParts.indices) {
            if (parts[i] != prefParts[i]) return false
        }
        return true
    }

    fun append(name: R_Name): T {
        return create(parts + name)
    }

    fun append(name: String): T {
        val rName = R_Name.of(name)
        return append(rName)
    }

    protected abstract fun self(): T
    protected abstract fun create(parts: List<R_Name>): T

    final override fun compareTo(other: T): Int {
        return CommonUtils.compareLists(parts, other.parts)
    }

    final override fun toString() = str()

    final override fun equals(other: Any?) = other === this
            || (other is R_GenericQualifiedName<*> && javaClass == other.javaClass && parts == other.parts)
    final override fun hashCode() = Objects.hash(javaClass, parts)
}

private fun <T: R_GenericQualifiedName<T>> qNameOf0(s: String, empty: T, create: (List<R_Name>) -> T): T {
    val res = qNameOfOpt0(s, empty, create)
    return requireNotNull(res) { s }
}

private fun <T: R_GenericQualifiedName<T>> qNameOfOpt0(s: String, empty: T?, create: (List<R_Name>) -> T): T? {
    if (s == "") return empty
    val parts = R_Name.listOfOpt(s)
    return if (parts == null) null else create(parts)
}

class R_QualifiedName(parts: List<R_Name>): R_GenericQualifiedName<R_QualifiedName>(parts) {
    init {
        check(parts.isNotEmpty())
    }

    val first: R_Name = this.parts.first()
    val last: R_Name = this.parts.last()

    override fun self() = this
    override fun create(parts: List<R_Name>) = R_QualifiedName(parts)

    fun replaceLast(name: R_Name): R_QualifiedName {
        if (name == last) return this
        val resParts = parts.subList(0, parts.size - 1) + listOf(name)
        return R_QualifiedName(resParts)
    }

    companion object {
        fun of(s: String): R_QualifiedName = requireNotNull(ofOpt(s)) { s }
        fun ofOpt(s: String): R_QualifiedName? = qNameOfOpt0(s, null) { R_QualifiedName(it) }
        fun of(vararg parts: R_Name): R_QualifiedName = R_QualifiedName(parts.toImmList())
    }
}

class R_ModuleName private constructor(parts: List<R_Name>): R_GenericQualifiedName<R_ModuleName>(parts) {
    override fun self() = this
    override fun create(parts: List<R_Name>) = of(parts)

    fun parent(): R_ModuleName {
        val res = parentOrNull()
        return checkNotNull(res) { "Trying to get a parent name of an empty name" }
    }

    fun parentOrNull(): R_ModuleName? {
        return if (parts.isEmpty()) null else of(parts.subList(0, parts.size - 1))
    }

    companion object {
        val EMPTY = R_ModuleName(listOf())
        fun of(parts: List<R_Name>) = if (parts.isEmpty()) EMPTY else R_ModuleName(parts)
        fun of(s: String) = qNameOf0(s, EMPTY) { R_ModuleName(it) }
        fun ofOpt(s: String) = qNameOfOpt0(s, EMPTY) { R_ModuleName(it) }
    }
}

class R_MountName(parts: List<R_Name>): R_GenericQualifiedName<R_MountName>(parts) {
    override fun self() = this
    override fun create(parts: List<R_Name>) = R_MountName(parts)

    companion object {
        val EMPTY = R_MountName(listOf())
        fun of(s: String) = qNameOf0(s, EMPTY) { R_MountName(it) }
        fun ofOpt(s: String) = qNameOfOpt0(s, EMPTY) { R_MountName(it) }
    }
}

data class R_FullName(
    val moduleName: R_ModuleName,
    val qualifiedName: R_QualifiedName,
) {
    val last: R_Name get() = qualifiedName.last

    fun str(): String = "$moduleName:$qualifiedName"
    override fun toString() = str()

    fun append(name: R_Name): R_FullName {
        val qName2 = qualifiedName.append(name)
        return R_FullName(moduleName, qName2)
    }

    fun replaceLast(name: R_Name): R_FullName {
        val qName2 = qualifiedName.replaceLast(name)
        return R_FullName(moduleName, qName2)
    }
}

class R_Name private constructor(val str: String): Comparable<R_Name> {
    override fun compareTo(other: R_Name) = str.compareTo(other.str)
    override fun toString() = str
    override fun equals(other: Any?) = other === this || (other is R_Name && str == other.str)
    override fun hashCode() = str.hashCode()

    companion object {
        fun isNameStart(c: Char) = c == '_' || c in 'A'..'Z' || c in 'a'..'z'
        fun isNamePart(c: Char) = isNameStart(c) || c in '0'..'9'

        fun isValid(s: String): Boolean {
            if (s.isEmpty()) return false
            if (!isNameStart(s[0])) return false
            for (c in s) {
                if (!isNamePart(c)) return false
            }
            return true
        }

        fun of(s: String): R_Name {
            val res = ofOpt(s)
            return requireNotNull(res) { s }
        }

        fun ofOpt(s: String): R_Name? {
            if (!isValid(s)) {
                return null
            }
            return R_Name(s)
        }

        fun listOfOpt(s: String): List<R_Name>? {
            val parts = StringUtils.splitPreserveAllTokens(s, '.')
            val names = parts.map { ofOpt(it) }
            val names2 = names.filterNotNull()
            if (names2.size != names.size) return null
            return names2.toImmList()
        }
    }
}

class R_IdeName(val rName: R_Name, val ideInfo: C_IdeSymbolInfo) {
    val str = rName.str

    override fun equals(other: Any?) = other is R_IdeName && rName == other.rName
    override fun hashCode() = Objects.hash(javaClass, rName)
    override fun toString() = rName.toString()
}

class R_LangVersion(private val ver: VersionNumber): Comparable<R_LangVersion> {
    init {
        require(ver.items.size == 3) { "invalid version: $ver" }
    }

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
    }
}

object R_Utils {
    private val ERROR_APP_UID = R_AppUid(-1)
    private val ERROR_CONTAINER_UID = R_ContainerUid(-1, "<error>", ERROR_APP_UID)
    private val ERROR_FN_UID = R_FnUid(-1, "<error>", ERROR_CONTAINER_UID)
    val ERROR_BLOCK_UID = R_FrameBlockUid(-1, "<error>", ERROR_FN_UID)
}
