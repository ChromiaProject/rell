/*
 * Copyright (C) 2021 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.C_BinOp_EqNe
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*

class V_InCollectionExpr(
        exprCtx: C_ExprContext,
        private val elemType: R_Type,
        private val left: V_Expr,
        private val right: V_Expr,
        private val not: Boolean
): V_Expr(exprCtx, left.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(R_BooleanType, left, right)

    override fun toRExpr0(): R_Expr {
        val rLeft = left.toRExpr()
        val rRight = right.toRExpr()
        var rExpr: R_Expr = R_BinaryExpr(R_BooleanType, R_BinaryOp_In_Collection, rLeft, rRight)
        if (not) {
            rExpr = R_UnaryExpr(R_BooleanType, R_UnaryOp_Not, rExpr)
        }
        return rExpr
    }

    override fun toDbExpr0(): Db_Expr {
        val dbLeft = left.toDbExpr()

        if (!C_BinOp_EqNe.checkTypesDb(elemType, elemType) || !elemType.sqlAdapter.isSqlCompatible()) {
            C_Errors.errExprNoDb(msgCtx, pos, elemType)
            return C_ExprUtils.errorDbExpr(R_BooleanType)
        }

        return if (right.info.dependsOnDbAtEntity) {
            if (right is V_ListLiteralExpr) {
                val dbRights = right.elems.map { it.toDbExpr() }
                Db_InExpr(dbLeft, dbRights, not)
            } else {
                val dbRight = right.toDbExpr()
                val op = if (not) Db_BinaryOp_NotIn else Db_BinaryOp_In
                Db_BinaryExpr(R_BooleanType, op, dbLeft, dbRight)
            }
        } else {
            val rRight = right.toRExpr()
            Db_InCollectionExpr(dbLeft, rRight, not)
        }
    }
}
