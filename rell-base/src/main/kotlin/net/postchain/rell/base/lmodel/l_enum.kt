/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.associateToImmMap
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol

class L_Enum(
    val simpleName: R_Name,
    val rEnum: R_EnumDefinition,
) {
    val docMembers: ImmMap<String, DocDefinition> = rEnum.attrs.associateToImmMap { it.name to it }
}

class L_NamespaceMember_Enum(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val enum: L_Enum,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "enum $qualifiedName"

    override fun getAbstractTypeDefOrNull(): L_AbstractTypeDef = L_MTypeDef(enum.rEnum.type.mType)
    override fun getEnumOrNull() = enum
    override fun getDocMembers0() = enum.docMembers
}
