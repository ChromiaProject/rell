/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VirtualStructType
import net.postchain.rell.base.model.expr.R_DestinationExpr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_MemberCalculator_VirtualStructAttr
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList

object Lib_Type_VirtualStruct {
    fun getValueMembers(type: R_VirtualStructType): ImmList<C_TypeValueMember> {
        return type.innerType.struct.attributesList.mapToImmList { attr ->
            val virtualType = S_VirtualType.virtualMemberType(attr.type)
            val mem = C_MemberAttr_VirtualStructAttr(virtualType, attr, type)
            C_TypeValueMember_BasicAttr(mem)
        }
    }

    private class C_MemberAttr_VirtualStructAttr(
        type: R_Type,
        attr: R_Attribute,
        private val outerType: R_Type,
    ): C_MemberAttr_StructAttr(type, attr) {
        override fun vAttr(exprCtx: C_ExprContext, selfType: R_Type, pos: S_Pos): V_MemberAttr {
            return V_MemberAttr_VirtualStructAttr(type, attr, outerType)
        }

        private inner class V_MemberAttr_VirtualStructAttr(
            type: R_Type,
            attr: R_Attribute,
            private val outerType: R_Type,
        ): V_MemberAttr_StructAttr(type, attr) {
            override fun calculator() = R_MemberCalculator_VirtualStructAttr(type, attr)

            override fun destination(pos: S_Pos, base: R_Expr): R_DestinationExpr {
                throw C_Errors.errAttrNotMutable(pos, attr.name, "${outerType.name}.${attr.name}")
            }
        }
    }
}
