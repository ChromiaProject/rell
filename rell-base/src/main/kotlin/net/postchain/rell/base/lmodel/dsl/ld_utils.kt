/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

@DslMarker
annotation class RellLibDsl

class Ld_FullName(val moduleName: R_ModuleName?, val qualifiedName: R_QualifiedName) {
    override fun toString(): String {
        return if (moduleName == null) qualifiedName.str() else "${moduleName.str()}${MODULE_SEP}${qualifiedName.str()}"
    }

    companion object {
        private const val MODULE_SEP = "::"

        fun parse(s: String): Ld_FullName {
            val i = s.indexOf(MODULE_SEP)
            return if (i < 0) {
                val qName = R_QualifiedName.of(s)
                Ld_FullName(null, qName)
            } else {
                val moduleName = R_ModuleName.of(s.substring(0, i))
                val qName = R_QualifiedName.of(s.substring(i + MODULE_SEP.length))
                Ld_FullName(moduleName, qName)
            }
        }
    }
}

class Ld_Exception(val code: String, val msg: String, cause: Throwable? = null): RuntimeException(msg, cause) {
    companion object {
        fun check(b: Boolean, cause: Exception? = null, lazyMessage: () -> Pair<String, String>) {
            if (!b) {
                val (code, msg) = lazyMessage()
                throw Ld_Exception(code, msg, cause)
            }
        }
    }
}

class Ld_Alias(val memberHeader: Ld_MemberHeader, val simpleName: R_Name, val deprecated: C_Deprecated?) {
    companion object {
        fun make(
            memberHeader: Ld_MemberHeader,
            simpleName: R_Name,
            primaryName: R_Name,
            deprecatedType: C_MessageType?,
        ): Ld_Alias {
            val deprecated = C_Deprecated.makeOrNull(deprecatedType, useInstead = primaryName.str)
            return Ld_Alias(memberHeader, simpleName, deprecated)
        }
    }
}

class Ld_AliasesBuilder(private val primaryName: R_Name) {
    private val aliases = mutableMapOf<R_Name, Ld_Alias>()

    fun alias(name: String, deprecated: C_MessageType?, memberHeader: Ld_MemberHeader) {
        val rName = R_Name.of(name)

        Ld_Exception.check(rName != primaryName && rName !in aliases) {
            "alias_conflict:$rName" to "Alias name conflict: $rName"
        }

        aliases[rName] = Ld_Alias.make(memberHeader, rName, primaryName, deprecated)
    }

    fun build(): ImmList<Ld_Alias> {
        return aliases.values.toImmList()
    }
}

enum class Ld_ConflictMemberKind {
    NAMESPACE,
    FUNCTION,
    OTHER,
}

class Ld_MemberConflictChecker(initialNames: Map<R_Name, Ld_ConflictMemberKind>) {
    private val names = initialNames.toMutableMap()

    fun addMember(name: R_Name, kind: Ld_ConflictMemberKind) {
        val oldKind = names[name]
        if (oldKind == null) {
            names[name] = kind
        } else {
            Ld_Exception.check(kind == oldKind && kind != Ld_ConflictMemberKind.OTHER) {
                "name_conflict:$name" to "Name conflict: $name"
            }
        }
    }

    fun finish(): ImmMap<R_Name, Ld_ConflictMemberKind> {
        return names.toImmMap()
    }
}

class Ld_MemberDef<T: Any>(
    val header: Ld_MemberHeader,
    val def: T,
)
