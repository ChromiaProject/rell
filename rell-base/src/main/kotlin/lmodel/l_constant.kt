/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.doc.DocSymbol

class L_Constant(
    val simpleName: R_Name,
    internal val rType: R_Type,
    val value: Rt_Value,
) {
    val type: M_Type get() = rType.mType

    fun strCode(): String {
        val valueStr = value.strCode()
        return "constant $simpleName: ${rType.strCode()} = $valueStr"
    }
}

class L_NamespaceMember_Constant(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constant: L_Constant,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = constant.strCode()
}

class L_TypeDefMember_Constant(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constant: L_Constant,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = constant.strCode()
}
