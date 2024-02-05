/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel

import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Struct
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.utils.doc.DocDefinition
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.base.utils.futures.FcFuture

class L_StructAttribute(
    val name: R_Name,
    val type: M_Type,
    val mutable: Boolean,
    override val docSymbol: DocSymbol,
): DocDefinition

class L_Struct(
    val simpleName: R_Name,
    val rStruct: R_Struct,
    private val attributesFuture: FcFuture<Map<String, L_StructAttribute>>,
) {
    val attributesMap: Map<String, L_StructAttribute> get() = attributesFuture.getResult()
}

class L_NamespaceMember_Struct(
    fullName: R_FullName,
    doc: DocSymbol,
    val struct: L_Struct,
): L_NamespaceMember(fullName, doc) {
    override fun strCode() = "struct $qualifiedName"

    override fun getAbstractTypeDefOrNull(): L_AbstractTypeDef = L_MTypeDef(struct.rStruct.type.mType)
    override fun getStructOrNull() = struct

    override fun getDocMember(name: String): DocDefinition? {
        return struct.attributesMap[name]
    }
}
