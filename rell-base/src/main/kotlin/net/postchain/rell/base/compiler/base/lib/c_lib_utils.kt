/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.core.C_VarId
import net.postchain.rell.base.lmodel.L_Constant
import net.postchain.rell.base.lmodel.L_NamespaceProperty
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.model.R_FullName
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.mtype.M_Type
import net.postchain.rell.base.mtype.M_Type_Tuple

object C_LibUtils {
    val DEFAULT_MODULE = R_ModuleName.EMPTY
    val DEFAULT_MODULE_STR = DEFAULT_MODULE.str()

    fun defName(name: String) = C_DefinitionName(DEFAULT_MODULE_STR, name)

    fun isImmutableType(mType: M_Type): Boolean {
        val rType = L_TypeUtils.getRType(mType)
        return rType != null && !rType.completeFlags().mutable
    }

    fun asMapEntryOrNull(mType: M_Type): Pair<M_Type, M_Type>? {
        return if (mType is M_Type_Tuple && mType.fieldTypes.size == 2) {
            mType.fieldTypes[0] to mType.fieldTypes[1]
        } else null
    }
}

data class C_LibConstantVarId(private val fullName: R_FullName, private val constant: L_Constant): C_VarId() {
    override fun nameMsg() = fullName.str()
}

data class C_LibNamespacePropertyVarId(
    private val fullName: R_FullName,
    private val property: L_NamespaceProperty,
): C_VarId() {
    override fun nameMsg() = fullName.str()
}
