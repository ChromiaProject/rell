/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_LocalVarRef
import net.postchain.rell.base.compiler.base.expr.C_AssignOp
import net.postchain.rell.base.compiler.base.expr.C_Destination
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_VarStateKey
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_PosCodeMsg
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_AssignExpr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_NotNullExpr
import net.postchain.rell.base.model.stmt.R_AssignStatement
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.capitalizeEx
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmSet

internal class V_LocalVarExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val varRef: C_LocalVarRef,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo(
        varRef.target.type,
        immListOf(),
        dependsOnAtExprs = listOfNotNull(varRef.target.atExprId).toImmSet(),
    )

    override fun varKey() = varRef.target.varKey

    override fun isAtExprItem() = varRef.target.atExprId != null
    override fun implicitTargetAttrName() = varRef.target.rName

    override fun toRExpr(): R_Expr {
        checkInitialized()
        return varRef.toRExpr()
    }

    override fun destination(): C_Destination {
        if (!varRef.target.mutable) {
            if (exprCtx.varStates.getInited(varRef.target.varKey) != false) {
                val name = varRef.target.metaName
                throw C_Error.stop(pos, "expr_assign_val:$name", "Value of '$name' cannot be changed")
            }
        }
        return C_Destination_LocalVar()
    }

    private fun checkInitialized() {
        if (exprCtx.varStates.getInited(varRef.target.varKey) != true) {
            val name = varRef.target.metaName
            msgCtx.error(pos, "expr_var_uninit:$name", "Variable '$name' may be uninitialized")
        }
    }

    private inner class C_Destination_LocalVar: C_Destination() {
        override fun type() = varRef.target.type
        override fun effectiveType() = varRef.target.type

        override fun compileAssignStatement(
            ctx: C_ExprContext,
            pos: S_Pos,
            srcExpr: R_Expr,
            op: C_AssignOp?,
        ): R_Statement {
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

internal class V_SmartNullableExpr private constructor(
    exprCtx: C_ExprContext,
    private val subExpr: V_Expr,
    private val nulled: Boolean,
    private val smartType: R_Type?,
    private val targetVarKey: C_VarStateKey?,
    private val kind: C_CodeMsg,
): V_Expr(exprCtx, subExpr.pos) {
    override fun exprInfo0() = V_ExprInfo.simple(smartType ?: subExpr.type, subExpr)

    override fun toDbExpr0() = subExpr.toDbExpr()
    override fun varKey() = subExpr.varKey()

    override fun isAtExprItem() = subExpr.isAtExprItem()
    override fun implicitTargetAttrName() = subExpr.implicitTargetAttrName()
    override fun implicitAtWhereAttrName() = subExpr.implicitAtWhereAttrName()
    override fun implicitAtWhatAttrName() = subExpr.implicitAtWhatAttrName()

    override fun toRExpr(): R_Expr {
        val rExpr = subExpr.toRExpr()
        return if (smartType == null) rExpr else R_NotNullExpr(smartType, rExpr, pos.toErrorPos())
    }

    override fun asNullable(): V_ExprWrapper {
        return V_ExprWrapper(msgCtx, subExpr) {
            val cm = if (nulled) ("always" toCodeMsg "is always") else ("never" toCodeMsg "cannot be")
            val name = if (targetVarKey != null && targetVarKey.isFull) targetVarKey.nameMsg() else null
            val baseCode = "expr:smartnull:${kind.code}:${cm.code}"
            val code = if (name != null) "$baseCode:[$name]" else baseCode
            val kindMsg = kind.msg.capitalizeEx()
            val baseMsg = if (name != null) "$kindMsg '$name'" else kindMsg
            val msg = "$baseMsg ${cm.msg} null at this location"
            C_PosCodeMsg(pos, code, msg)
        }
    }

    override fun destination(): C_Destination {
        val dst = subExpr.destination()
        return if (smartType == null) dst else C_Destination_ImplicitCast(dst, smartType)
    }

    companion object {
        fun wrap(ctx: C_ExprContext, vExpr: V_Expr, varKind: C_CodeMsg, forceNotNull: Boolean = false): V_Expr {
            val varKey = vExpr.varKey()

            val nulled = when {
                forceNotNull -> false
                varKey != null -> ctx.varStates.getNulled(varKey)
                else -> null
            }
            nulled ?: return vExpr

            val type = vExpr.type
            val smartType = if (type is R_NullableType && nulled == false) type.valueType else null
            return V_SmartNullableExpr(ctx, vExpr, nulled == true, smartType, varKey, varKind)
        }
    }
}

private class C_Destination_ImplicitCast(
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
        post: Boolean,
    ): R_Expr {
        return destination.compileAssignExpr(ctx, startPos, resType, srcExpr, op, post)
    }

    override fun compileAssignStatement(ctx: C_ExprContext, pos: S_Pos, srcExpr: R_Expr, op: C_AssignOp?): R_Statement {
        return destination.compileAssignStatement(ctx, pos, srcExpr, op)
    }
}
