/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_QualifiedName
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParam
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.doc.DocSymbol

class L_TypeExtension(
    val qualifiedName: R_QualifiedName,
    val typeParams: ImmList<M_TypeParam>,
    val selfType: M_Type,
    val members: L_TypeDefMembers,
    val docSymbol: DocSymbol,
) {
    fun strCode(): String {
        val parts = mutableListOf<String>()

        parts.add("extension ")
        parts.add(qualifiedName.str())

        if (typeParams.isNotEmpty()) {
            val s = typeParams.joinToString(",", "<", ">") { it.strCode() }
            parts.add(s)
        }

        parts.add(": ")
        parts.add(selfType.strCode())

        return parts.joinToString("")
    }
}

class L_NamespaceMember_TypeExtension(
    fullName: R_FullName,
    header: L_MemberHeader,
    val typeExt: L_TypeExtension,
): L_NamespaceMember(fullName, header, typeExt.docSymbol) {
    override fun strCode(): String {
        return typeExt.strCode()
    }

    override fun getTypeExtensionOrNull() = typeExt
    override fun getDocMembers0() = typeExt.members.docMembers
}
