/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_LocalVarRef
import net.postchain.rell.base.compiler.base.expr.C_AssignOp
import net.postchain.rell.base.compiler.base.expr.C_Destination
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_VarFact
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_PosCodeMsg
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_AssignExpr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_NotNullExpr
import net.postchain.rell.base.model.stmt.R_AssignStatement
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmSet

class V_LocalVarExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val varRef: C_LocalVarRef,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo(
        varRef.target.type,
        immListOf(),
        dependsOnAtExprs = listOfNotNull(varRef.target.atExprId).toImmSet(),
    )

    override fun varId() = varRef.target.uid

    override fun isAtExprItem() = varRef.target.atExprId != null
    override fun implicitTargetAttrName() = varRef.target.rName

    override fun toRExpr0(): R_Expr {
        checkInitialized()
        return varRef.toRExpr()
    }

    override fun destination(): C_Destination {
        if (!varRef.target.mutable) {
            if (exprCtx.factsCtx.inited(varRef.target.uid) != C_VarFact.NO) {
                val name = varRef.target.metaName
                throw C_Error.stop(pos, "expr_assign_val:$name", "Value of '$name' cannot be changed")
            }
        }
        return C_Destination_LocalVar()
    }

    private fun checkInitialized() {
        if (exprCtx.factsCtx.inited(varRef.target.uid) != C_VarFact.YES) {
            val name = varRef.target.metaName
            throw C_Error.stop(pos, "expr_var_uninit:$name", "Variable '$name' may be uninitialized")
        }
    }

    private inner class C_Destination_LocalVar: C_Destination() {
        override fun type() = varRef.target.type
        override fun effectiveType() = varRef.target.type

        override fun compileAssignStatement(ctx: C_ExprContext, srcExpr: R_Expr, op: C_AssignOp?): R_Statement {
            if (op != null) {
                checkInitialized()
            }
            val rDstExpr = varRef.toRExpr()
            return R_AssignStatement(rDstExpr, srcExpr, op?.rOp)
        }

        override fun compileAssignExpr(
                ctx: C_ExprContext,
                startPos: S_Pos,
                resType: R_Type,
                srcExpr: R_Expr,
                op: C_AssignOp,
                post: Boolean
        ): R_Expr {
            checkInitialized()
            val rDstExpr = varRef.toRExpr()
            return R_AssignExpr(resType, op.rOp, rDstExpr, srcExpr, post)
        }
    }
}

class V_SmartNullableExpr(
    exprCtx: C_ExprContext,
    private val subExpr: V_Expr,
    private val nulled: Boolean,
    private val smartType: R_Type?,
    private val name: String,
    private val kind: C_CodeMsg,
): V_Expr(exprCtx, subExpr.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(smartType ?: subExpr.type, subExpr)

    override fun toDbExpr0() = subExpr.toDbExpr()
    override fun varId() = subExpr.varId()

    override fun isAtExprItem() = subExpr.isAtExprItem()
    override fun implicitTargetAttrName() = subExpr.implicitTargetAttrName()
    override fun implicitAtWhereAttrName() = subExpr.implicitAtWhereAttrName()

    override fun toRExpr0(): R_Expr {
        val rExpr = subExpr.toRExpr()
        return if (smartType == null) rExpr else R_NotNullExpr(smartType, rExpr)
    }

    override fun asNullable(): V_ExprWrapper {
        return V_ExprWrapper(msgCtx, subExpr) {
            val cm = if (nulled) ("always" toCodeMsg "is always") else ("never" toCodeMsg "cannot be")
            val code = "expr:smartnull:${kind.code}:${cm.code}:$name"
            val msg = "${kind.msg} '$name' ${cm.msg} null at this location"
            C_PosCodeMsg(pos, code, msg)
        }
    }

    override fun destination(): C_Destination {
        val dst = subExpr.destination()
        return if (smartType == null) dst else C_Destination_SmartNullable(dst, smartType)
    }

    private class C_Destination_SmartNullable(
        val destination: C_Destination,
        val effectiveType: R_Type,
    ): C_Destination() {
        override fun type() = destination.type()
        override fun effectiveType() = effectiveType

        override fun compileAssignExpr(
                ctx: C_ExprContext,
                startPos: S_Pos,
                resType: R_Type,
                srcExpr: R_Expr,
                op: C_AssignOp,
                post: Boolean
        ): R_Expr {
            return destination.compileAssignExpr(ctx, startPos, resType, srcExpr, op, post)
        }

        override fun compileAssignStatement(ctx: C_ExprContext, srcExpr: R_Expr, op: C_AssignOp?): R_Statement {
            return destination.compileAssignStatement(ctx, srcExpr, op)
        }
    }
}
