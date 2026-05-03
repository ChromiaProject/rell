/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.QualifiedName
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol

class L_MemberHeader(val since: R_LangVersion?, val docComment: DocComment?)

abstract class L_AbstractMember(
    val fullName: FullName,
    val header: L_MemberHeader,
    override val docSymbol: DocSymbol,
): DocDefinition() {
    val qualifiedName: QualifiedName = fullName.qualifiedName
    val simpleName: Name = qualifiedName.last

    final override val docSourcePos = null
}

sealed class L_NamespaceMember(
    fullName: FullName,
    header: L_MemberHeader,
    docSymbol: DocSymbol,
): L_AbstractMember(fullName, header, docSymbol) {
    abstract fun strCode(): String

    open fun getTypeDefOrNull(): L_TypeDef? = null
    open fun getAbstractTypeDefOrNull(): L_AbstractTypeDef? = null
    open fun getTypeExtensionOrNull(): L_TypeExtension? = null
    open fun getStructOrNull(): L_Struct? = null
    open fun getEnumOrNull(): L_Enum? = null
}

class L_Namespace(members: List<L_NamespaceMember>) {
    val members: ImmList<L_NamespaceMember> = members.toImmList()

    private val namespaces: ImmMap<Name, L_Namespace> = let {
        val map = mutableMapOf<Name, L_Namespace>()
        for (member in this.members) {
            if (member is L_NamespaceMember_Namespace) {
                check(member.simpleName !in map) { "Name conflict: ${member.qualifiedName}" }
                map[member.simpleName] = member.namespace
            }
        }
        map.toImmMap()
    }

    // Ignoring name conflicts, assuming clients ask for unique entries (e.g. a type, not a function).
    private val membersMap: ImmMap<Name, L_NamespaceMember> = let {
        val map = mutableMapOf<Name, L_NamespaceMember>()
        for (member in this.members) {
            map.putIfAbsent(member.simpleName, member)
        }
        map.toImmMap()
    }

    private val typeExtensions = this.members
        .mapNotNullToImmList { (it as? L_NamespaceMember_TypeExtension)?.typeExt }

    val docMembers: ImmMap<String, DocDefinition> by lazy {
        membersMap.entries.associateToImmMap { it.key.str to it.value }
    }

    fun getDef(qName: QualifiedName): L_NamespaceMember {
        val def = getDefOrNull(qName)
        checkNotNull(def) { "Definition not found: $qName" }
        return def
    }

    fun getDefOrNull(qName: QualifiedName): L_NamespaceMember? {
        var ns = this
        for (rName in qName.parts.dropLast(1)) {
            val nextNs = ns.namespaces[rName]
            nextNs ?: return null
            ns = nextNs
        }
        return ns.membersMap[qName.last]
    }

    fun getAllDefs(): List<L_NamespaceMember> {
        val res = mutableListOf<L_NamespaceMember>()
        getAllDefs0(res)
        return res.toImmList()
    }

    private fun getAllDefs0(res: MutableList<L_NamespaceMember>) {
        for (def in members) {
            res.add(def)
            if (def is L_NamespaceMember_Namespace) {
                def.namespace.getAllDefs0(res)
            }
        }
    }

    fun allTypeExtensions(): List<L_TypeExtension> {
        return allTypeExtensions0().toImmList()
    }

    private fun allTypeExtensions0(): Iterable<L_TypeExtension> {
        val nested = namespaces.values.flatMap { it.allTypeExtensions0().asIterable() }
        return typeExtensions + nested
    }

    companion object {
        val EMPTY = L_Namespace(immListOf())
    }
}

class L_NamespaceMember_Namespace(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val namespace: L_Namespace,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "namespace $qualifiedName"
    override fun getDocMembers0() = namespace.docMembers
}

class L_NamespaceMember_Alias(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val targetMember: L_NamespaceMember,
    val finalTargetMember: L_NamespaceMember,
    val deprecated: C_Deprecated?,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode(): String {
        val parts = listOfNotNull(
            L_InternalUtils.deprecatedStrCodeOrNull(deprecated),
            "alias $qualifiedName = ${targetMember.qualifiedName}",
        )
        return parts.joinToString(" ")
    }

    override fun getTypeDefOrNull(): L_TypeDef? = finalTargetMember.getTypeDefOrNull()
    override fun getAbstractTypeDefOrNull(): L_AbstractTypeDef? = finalTargetMember.getAbstractTypeDefOrNull()
    override fun getTypeExtensionOrNull(): L_TypeExtension? = finalTargetMember.getTypeExtensionOrNull()
    override fun getStructOrNull(): L_Struct? = finalTargetMember.getStructOrNull()
    override fun getEnumOrNull(): L_Enum? = finalTargetMember.getEnumOrNull()
}
