/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.utils

import net.postchain.rell.base.compiler.base.core.C_DefinitionPath
import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.QualifiedName
import net.postchain.rell.base.utils.*
import java.util.*

abstract class C_GenericQualifiedName<NameT: Any, FullNameT: C_GenericQualifiedName<NameT, FullNameT>>
protected constructor(parts: List<NameT>) {
    val parts = parts.let {
        check(it.isNotEmpty())
        it.toImmList()
    }

    val last = parts.last()

    fun add(other: FullNameT): FullNameT = create(parts + other.parts)

    fun add(name: NameT): FullNameT {
        checkName(name)
        return create(parts + name)
    }

    fun str() = parts.joinToString(".")

    protected abstract fun create(names: List<NameT>): FullNameT
    protected abstract fun checkName(name: NameT)

    final override fun equals(other: Any?): Boolean {
        return this === other || (other is C_GenericQualifiedName<*, *> && other.javaClass == javaClass && parts == other.parts)
    }

    final override fun hashCode() = parts.hashCode()

    final override fun toString() = str()

    protected companion object {
        fun <NameT: Any, FullNameT: C_GenericQualifiedName<NameT, FullNameT>> ofNames0(
                parts: List<NameT>,
                ctor: (List<NameT>) -> FullNameT
        ): FullNameT {
            val copy = parts.toImmList()
            require(copy.isNotEmpty())
            val res = ctor(copy)
            for (part in parts) {
                res.checkName(part)
            }
            return res
        }

        fun <NameT: Any, FullNameT: C_GenericQualifiedName<NameT, FullNameT>> ofName0(
                name: NameT,
                ctor: (List<NameT>) -> FullNameT
        ): FullNameT {
            val parts = immListOf(name)
            val res = ctor(parts)
            res.checkName(name)
            return res
        }
    }
}

class C_StringQualifiedName private constructor(
    parts: List<String>,
): C_GenericQualifiedName<String, C_StringQualifiedName>(parts) {
    override fun create(names: List<String>) = C_StringQualifiedName(names)

    override fun checkName(name: String) {
        require(name.isNotBlank())
    }

    companion object {
        fun of(parts: List<String>): C_StringQualifiedName = ofNames0(parts) { C_StringQualifiedName(it) }
        fun of(name: String): C_StringQualifiedName = ofName0(name) { C_StringQualifiedName(it) }
        fun of(cName: C_QualifiedName): C_StringQualifiedName = of(cName.parts.map { it.str })
        fun of(parent: List<Name>, name: String): C_StringQualifiedName = of(parent.map { it.str } + name)
        fun of(parent: QualifiedName, name: Name): C_StringQualifiedName = of(parent.parts, name.str)
        fun of(name: QualifiedName): C_StringQualifiedName = ofRNames(name.parts)

        fun ofRNames(parts: List<Name>): C_StringQualifiedName = of(parts.map { it.str })
    }
}

class C_RNamePath private constructor(val parts: ImmList<Name>) {
    fun append(name: Name) = C_RNamePath(parts + name)
    fun append(names: List<Name>) = of(parts + names)

    fun qualifiedName(name: Name): QualifiedName = QualifiedName(parts + name)
    fun fullName(moduleName: ModuleName, name: Name): FullName = FullName(moduleName, qualifiedName(name))

    override fun equals(other: Any?) = other is C_RNamePath && parts == other.parts
    override fun hashCode() = parts.hashCode()
    override fun toString() = parts.joinToString(".")

    companion object {
        val EMPTY = C_RNamePath(immListOf())
        fun of(qualifiedName: QualifiedName): C_RNamePath = of(qualifiedName.parts)
        fun of(parts: ImmList<Name>): C_RNamePath = if (parts.isEmpty()) EMPTY else C_RNamePath(parts)
        fun of(parts: List<Name>): C_RNamePath = of(parts.toImmList())
    }
}

class C_RFullNamePath private constructor(
    val moduleName: ModuleName,
    val parts: ImmList<Name>,
) {
    fun append(name: Name) = C_RFullNamePath(moduleName, parts + name)
    fun append(names: List<Name>) = C_RFullNamePath(moduleName, parts + names)

    fun qualifiedName(name: Name): QualifiedName = QualifiedName(parts + name)
    fun fullName(name: Name): FullName = FullName(moduleName, qualifiedName(name))

    fun toDefPath(): C_DefinitionPath = C_DefinitionPath(moduleName.str(), parts.mapToImmList { it.str })

    override fun equals(other: Any?) = other is C_RFullNamePath && moduleName == other.moduleName && parts == other.parts
    override fun hashCode() = Objects.hash(moduleName, parts)
    override fun toString() = "$moduleName:${parts.joinToString(".")}"

    companion object {
        fun of(moduleName: ModuleName, parts: List<Name> = immListOf()): C_RFullNamePath {
            return C_RFullNamePath(moduleName, parts.toImmList())
        }
    }
}
