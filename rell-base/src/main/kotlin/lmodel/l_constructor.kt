/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.lib.C_SpecialLibGlobalFunctionBody
import net.postchain.rell.base.compiler.base.namespace.C_Deprecated
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.mtype.M_TypeParam
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.associateByToImmMap
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol

class L_ConstructorHeader(
    val typeParams: ImmList<M_TypeParam>,
    params: ImmList<L_FunctionParam>,
): L_CommonFunctionHeader(params) {
    fun strCode(): String {
        val parts = mutableListOf<String>()
        if (typeParams.isNotEmpty()) parts.add(typeParams.joinToString(",", "<", ">") { it.strCode() })
        parts.add(params.joinToString(", ", "(", ")") { it.strCode() })
        return parts.joinToString(" ")
    }
}

class L_Constructor(
    val header: L_ConstructorHeader,
    val deprecated: C_Deprecated?,
    val body: L_FunctionBody,
    val pure: Boolean,
) {
    val docMembers: ImmMap<String, DocDefinition> by lazy {
        header.params.associateByToImmMap { it.name.str }
    }

    fun strCode(): String {
        val parts = mutableListOf<String>()
        if (deprecated != null) parts.add("@deprecated")
        if (pure) parts.add("pure")
        parts.add("constructor")
        parts.add(header.strCode())
        return parts.joinToString(" ")
    }
}

class L_TypeDefMember_Constructor(
    typeName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constructor: L_Constructor,
): L_TypeDefMember(typeName, header, doc, "!init") {
    override fun strCode() = constructor.strCode()
    override fun getDocMembers0() = constructor.docMembers
}

class L_TypeDefMember_SpecialConstructor(
    typeName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val fn: C_SpecialLibGlobalFunctionBody,
): L_TypeDefMember(typeName, header, doc, "!init") {
    override fun strCode() = "special constructor (...)"
}
