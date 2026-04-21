/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.FullName
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.doc.DocSymbol

class L_Constant(
        val simpleName: Name,
        val rType: R_Type,
        val value: Any, // Opaque — at runtime this is Rt_Value
) {
    val type: M_Type get() = rType.mType

    fun strCode(): String {
        val valueStr = L_ConstantStrCode.format(value)
        return "constant $simpleName: ${rType.strCode()} = $valueStr"
    }
}

/** Bridge to format runtime values; set from runtime initialization. */
object L_ConstantStrCode {
    /** Default fallback uses toString — runtime overrides this with Rt_Value.strCode(). */
    var format: (Any) -> String = { it.toString() }
}

class L_NamespaceMember_Constant(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constant: L_Constant,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = constant.strCode()
}

class L_TypeDefMember_Constant(
    fullName: FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val constant: L_Constant,
): L_TypeDefMember(fullName, header, doc) {
    override fun strCode() = constant.strCode()
}
