/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_TypeParam
import net.postchain.rell.base.mtype.M_TypeSet
import net.postchain.rell.base.utils.doc.DocSymbol

class L_NamespaceProperty(
    val type: M_Type,
    val fn: C_SysFunction,
    val pure: Boolean,
)

class L_NamespaceMember_Property(
    fullName: R_FullName,
    doc: DocSymbol,
    val property: L_NamespaceProperty,
): L_NamespaceMember(fullName, doc) {
    override fun strCode() = "property $qualifiedName: ${property.type.strCode()}"
}

class L_NamespaceMember_SpecialProperty(
    fullName: R_FullName,
    doc: DocSymbol,
    val property: C_NamespaceProperty,
): L_NamespaceMember(fullName, doc) {
    override fun strCode() = "property $qualifiedName"
}

class L_TypeProperty(val simpleName: R_Name, val type: M_Type, val body: C_SysFunctionBody) {
    val pure: Boolean = body.pure

    fun strCode() = "property $simpleName: ${type.strCode()}"

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_TypeProperty {
        val type2 = type.replaceParamsOut(map)
        return if (type2 === type) this else L_TypeProperty(simpleName, type2, body = body)
    }
}

class L_TypeDefMember_Property(
    val simpleName: R_Name,
    doc: DocSymbol,
    val property: L_TypeProperty,
) : L_TypeDefMember(simpleName.str, doc) {
    override fun strCode() = property.strCode()

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): L_TypeDefMember_Property {
        val property2 = property.replaceTypeParams(map)
        return if (property2 === property) this else L_TypeDefMember_Property(simpleName, docSymbol, property2)
    }
}
