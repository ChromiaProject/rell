/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.lib.C_SysProperty
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.doc.DocSymbol

class L_NamespaceProperty(
    internal val rType: R_Type,
    internal val prop: C_SysProperty,
    internal val pure: Boolean,
) {
    val type: M_Type get() = rType.mType
}

class L_NamespaceMember_Property(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val property: L_NamespaceProperty,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "property $qualifiedName: ${property.rType.strCode()}"
}

class L_NamespaceMember_SpecialProperty(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val property: C_NamespaceProperty,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "property $qualifiedName"
}

class L_TypeProperty(
    val simpleName: R_Name,
    internal val rType: R_Type,
    internal val prop: C_SysProperty,
    internal val pure: Boolean,
) {
    val type: M_Type get() = rType.mType
    fun strCode() = "property $simpleName: ${rType.strCode()}"
}

class L_TypeDefMember_Property(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val property: L_TypeProperty,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = property.strCode()
}
