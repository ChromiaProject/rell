/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_AssignOp
import net.postchain.rell.base.compiler.base.expr.C_Destination
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprVarFacts
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_Value

sealed class V_UnaryOp(val resType: R_Type) {
    open fun canBeDbExpr(): Boolean = true
    abstract fun compileR(pos: S_Pos, expr: R_Expr): R_Expr
    abstract fun compileDb(pos: S_Pos, expr: Db_Expr): Db_Expr
    open fun evaluate(value: Rt_Value): Rt_Value? = null
}

class V_UnaryOp_Plus(resType: R_Type): V_UnaryOp(resType) {
    override fun compileR(pos: S_Pos, expr: R_Expr) = expr
    override fun compileDb(pos: S_Pos, expr: Db_Expr) = expr
    override fun evaluate(value: Rt_Value) = value
}

class V_UnaryOp_Minus(resType: R_Type, val rOp: R_UnaryOp, val dbOp: Db_UnaryOp): V_UnaryOp(resType) {
    override fun compileR(pos: S_Pos, expr: R_Expr) = R_UnaryExpr(resType, rOp, expr)
    override fun compileDb(pos: S_Pos, expr: Db_Expr) = Db_UnaryExpr(resType, dbOp, expr)
    override fun evaluate(value: Rt_Value) = rOp.evaluate(value)
}

class V_UnaryOp_Not: V_UnaryOp(R_BooleanType) {
    override fun compileR(pos: S_Pos, expr: R_Expr) = R_UnaryExpr(R_BooleanType, R_UnaryOp_Not, expr)
    override fun compileDb(pos: S_Pos, expr: Db_Expr) = Db_UnaryExpr(R_BooleanType, Db_UnaryOp_Not, expr)
    override fun evaluate(value: Rt_Value) = R_UnaryOp_Not.evaluate(value)
}

class V_UnaryOp_NotNull(resType: R_Type): V_UnaryOp(resType) {
    override fun canBeDbExpr() = false
    override fun compileR(pos: S_Pos, expr: R_Expr) = R_NotNullExpr(resType, expr)
    override fun compileDb(pos: S_Pos, expr: Db_Expr) = throw C_Error.stop(pos, "expr:is_null:nodb", "Not supported for SQL")
}

class V_UnaryOp_IsNull: V_UnaryOp(R_BooleanType) {
    override fun canBeDbExpr() = false
    override fun compileR(pos: S_Pos, expr: R_Expr) = R_BinaryExpr(R_BooleanType, R_BinaryOp_Ne, expr, R_ConstantValueExpr.makeNull())
    override fun compileDb(pos: S_Pos, expr: Db_Expr) = throw C_Error.stop(pos, "expr:is_null:nodb", "Not supported for SQL")
}

class V_UnaryExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val op: V_UnaryOp,
        private val expr: V_Expr,
        private val resVarFacts: C_ExprVarFacts
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(op.resType, expr, canBeDbExpr = op.canBeDbExpr())
    override fun varFacts0() = resVarFacts

    override fun toRExpr0(): R_Expr {
        val rExpr = expr.toRExpr()
        return op.compileR(pos, rExpr)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbExpr = expr.toDbExpr()
        return op.compileDb(pos, dbExpr)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val v = expr.constantValue(ctx)
        if (v == null) return null
        val res = op.evaluate(v)
        return res
    }
}

class V_IncDecExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val resType: R_Type,
        private val destination: C_Destination,
        private val dstExpr: V_Expr,
        private val srcExpr: R_Expr,
        private val op: C_AssignOp,
        private val post: Boolean
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, dstExpr)

    override fun toRExpr0() = destination.compileAssignExpr(exprCtx, pos, resType, srcExpr, op, post)
}
