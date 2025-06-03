/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.model.R_TupleField
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_MemberCalculator_TupleAttr
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapIndexedToImmList

object Lib_Type_Tuple {
    fun getValueMembers(type: R_TupleType): ImmList<C_TypeValueMember> {
        return type.fields.mapIndexedToImmList { idx, field ->
            val mem = C_MemberAttr_RegularTupleAttr(field.type, idx, field)
            C_TypeValueMember_BasicAttr(mem)
        }
    }

    private class C_MemberAttr_RegularTupleAttr(
        type: R_Type,
        fieldIndex: Int,
        field: R_TupleField,
    ): C_MemberAttr_TupleAttr(type, fieldIndex, field) {
        override fun vAttr(exprCtx: C_ExprContext, pos: S_Pos): V_MemberAttr {
            return V_MemberAttr_RegularTupleAttr(type, fieldIndex)
        }

        private inner class V_MemberAttr_RegularTupleAttr(
            type: R_Type,
            fieldIndex: Int,
        ): V_MemberAttr_TupleAttr(type, fieldIndex) {
            override fun calculator() = R_MemberCalculator_TupleAttr(type, fieldIndex)
        }
    }
}
