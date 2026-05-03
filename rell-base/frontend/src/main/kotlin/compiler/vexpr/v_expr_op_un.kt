/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.model.R_BooleanType
import net.postchain.rell.base.model.R_NullType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.rr.RR_ConstantValue

sealed class V_UnaryOp(val resType: R_Type) {
    open fun canBeDbExpr(): Boolean = true
    open fun preserveVarKey(): Boolean = false
    open fun foldConstant(value: RR_ConstantValue): RR_ConstantValue? = null
    abstract fun compileR(pos: S_Pos, expr: R_Expr): R_Expr
    abstract fun compileDb(pos: S_Pos, expr: Db_Expr): Db_Expr
}

class V_UnaryOp_Plus(resType: R_Type): V_UnaryOp(resType) {
    override fun foldConstant(value: RR_ConstantValue) = value
    override fun compileR(pos: S_Pos, expr: R_Expr) = expr
    override fun compileDb(pos: S_Pos, expr: Db_Expr) = expr
}

class V_UnaryOp_Minus(resType: R_Type, val rOp: R_UnaryOp, val dbOp: Db_UnaryOp): V_UnaryOp(resType) {
    override fun foldConstant(value: RR_ConstantValue): RR_ConstantValue? = when (value) {
        is RR_ConstantValue.Int -> RR_ConstantValue.Int(-value.value)
        is RR_ConstantValue.BigInteger -> RR_ConstantValue.BigInteger(java.math.BigInteger(value.value).negate().toString())
        is RR_ConstantValue.Decimal -> RR_ConstantValue.Decimal(java.math.BigDecimal(value.value).negate().toPlainString())
        else -> null
    }
    override fun compileR(pos: S_Pos, expr: R_Expr): R_Expr {
        val errPos = pos.toErrorPos()
        return R_UnaryExpr(resType, rOp, expr, errPos)
    }

    override fun compileDb(pos: S_Pos, expr: Db_Expr) = Db_UnaryExpr(resType, dbOp, expr)
}

class V_UnaryOp_Not: V_UnaryOp(R_BooleanType) {
    override fun foldConstant(value: RR_ConstantValue): RR_ConstantValue? = when (value) {
        is RR_ConstantValue.Bool -> RR_ConstantValue.Bool(!value.value)
        else -> null
    }
    override fun compileR(pos: S_Pos, expr: R_Expr): R_Expr {
        val errPos = pos.toErrorPos()
        return R_UnaryExpr(R_BooleanType, R_UnaryOp.Not, expr, errPos)
    }

    override fun compileDb(pos: S_Pos, expr: Db_Expr) = Db_UnaryExpr(R_BooleanType, Db_UnaryOp_Not, expr)
}

class V_UnaryOp_NotNull(resType: R_Type): V_UnaryOp(resType) {
    override fun canBeDbExpr() = false
    override fun preserveVarKey() = true

    override fun compileR(pos: S_Pos, expr: R_Expr): R_Expr {
        val errPos = pos.toErrorPos()
        return R_NotNullExpr(resType, expr, errPos)
    }

    override fun compileDb(pos: S_Pos, expr: Db_Expr): Db_Expr =
        throw C_Error.stop(pos, "expr:is_null:nodb", "Not supported for SQL")
}

class V_UnaryOp_IsNull(private val not: Boolean): V_UnaryOp(R_BooleanType) {
    override fun canBeDbExpr() = false

    override fun compileR(pos: S_Pos, expr: R_Expr): R_Expr {
        val rOp = if (not) R_BinaryOp_Ne else R_BinaryOp_Eq
        val errPos = pos.toErrorPos()
        return R_BinaryExpr(R_BooleanType, rOp, expr, R_RRConstantValueExpr(R_NullType, RR_ConstantValue.Null), errPos)
    }

    override fun compileDb(pos: S_Pos, expr: Db_Expr): Db_Expr {
        val dbOp = Db_BinaryOp_EqNe.get(!not, true)
        val right = Db_InterpretedExpr(R_RRConstantValueExpr(R_NullType, RR_ConstantValue.Null))
        return Db_BinaryExpr(resType, dbOp, expr, right)
    }
}

class V_UnaryExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val op: V_UnaryOp,
    private val expr: V_Expr,
    private val resVarStates: C_ExprVarStatesDelta,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(op.resType, expr, canBeDbExpr = op.canBeDbExpr())
    override fun varStatesDelta0() = resVarStates

    override fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? {
        val inner = expr.constantValue(ctx) ?: return null
        return op.foldConstant(inner)
    }

    override fun toRExpr(): R_Expr {
        val rExpr = expr.toRExpr()
        return op.compileR(pos, rExpr)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbExpr = expr.toDbExpr()
        return op.compileDb(pos, dbExpr)
    }

    override fun varKey(): C_VarStateKey? = if (op.preserveVarKey()) expr.varKey() else null
}

class V_IncDecExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val resType: R_Type,
    private val destination: C_Destination,
    private val dstExpr: V_Expr,
    private val srcExpr: R_Expr,
    private val op: C_AssignOp,
    private val post: Boolean,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, dstExpr)
    override fun toRExpr() = destination.compileAssignExpr(exprCtx, pos, resType, srcExpr, op, post)
}
