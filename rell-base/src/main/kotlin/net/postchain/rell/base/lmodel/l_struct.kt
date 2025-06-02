/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Struct
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.futures.FcFuture

class L_StructAttribute(
    fullName: R_FullName,
    val type: M_Type,
    val mutable: Boolean,
    header: L_MemberHeader,
    docSymbol: DocSymbol,
): L_AbstractMember(fullName, header, docSymbol)

class L_Struct(
    val simpleName: R_Name,
    val rStruct: R_Struct,
    private val attributesFuture: FcFuture<ImmMap<String, L_StructAttribute>>,
) {
    val docMembers: ImmMap<String, DocDefinition> get() = attributesFuture.getResult()
}

class L_NamespaceMember_Struct(
    fullName: R_FullName,
    header: L_MemberHeader,
    doc: DocSymbol,
    val struct: L_Struct,
): L_NamespaceMember(fullName, header, doc) {
    override fun strCode() = "struct $qualifiedName"

    override fun getAbstractTypeDefOrNull(): L_AbstractTypeDef = L_MTypeDef(struct.rStruct.type.mType)
    override fun getStructOrNull() = struct
    override fun getDocMembers0() = struct.docMembers
}
