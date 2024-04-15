/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*

sealed class S_UnaryOp(val code: String) {
    abstract fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr

    fun errTypeMismatch(ctx: C_ExprContext, pos: S_Pos, type: R_Type) {
        if (type.isNotError()) {
            ctx.msgCtx.error(pos, "unop_operand_type:$code:[${type.strCode()}]",
                    "Wrong operand type for '$code': ${type.str()}")
        }
    }
}

object S_UnaryOp_Plus: S_UnaryOp("+") {
    override fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr {
        val type = expr.type
        if (!C_BinOp_Common.isNumericType(type)) {
            errTypeMismatch(ctx, opPos, type)
        }

        // Cannot simply return "expr", because then expressions like "(+x)++" or "(+x) = 123" will be allowed.
        val vOp = V_UnaryOp_Plus(type)
        val varFacts = C_ExprVarFacts.forSubExpressions(expr)
        return V_UnaryExpr(ctx, startPos, vOp, expr, varFacts)
    }
}

object S_UnaryOp_Minus: S_UnaryOp("-") {
    override fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr {
        val type = expr.type

        val vOp = when (type) {
            R_IntegerType -> V_UnaryOp_Minus(type, R_UnaryOp_Minus_Integer, Db_UnaryOp_Minus_Integer)
            R_BigIntegerType -> V_UnaryOp_Minus(type, R_UnaryOp_Minus_BigInteger, Db_UnaryOp_Minus_BigInteger)
            R_DecimalType -> V_UnaryOp_Minus(type, R_UnaryOp_Minus_Decimal, Db_UnaryOp_Minus_Decimal)
            else -> {
                errTypeMismatch(ctx, opPos, type)
                V_UnaryOp_Minus(type, R_UnaryOp_Minus_Integer, Db_UnaryOp_Minus_Integer) // Fake op for error recovery.
            }
        }

        val varFacts = C_ExprVarFacts.forSubExpressions(expr)
        return V_UnaryExpr(ctx, startPos, vOp, expr, varFacts)
    }
}

object S_UnaryOp_Not: S_UnaryOp("not") {
    override fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr {
        val type = expr.type
        if (type != R_BooleanType) {
            errTypeMismatch(ctx, opPos, type)
        }

        val varFacts = expr.varFacts
        val resVarFacts = C_ExprVarFacts.of(
                trueFacts = varFacts.falseFacts,
                falseFacts = varFacts.trueFacts,
                postFacts = varFacts.postFacts
        )

        return V_UnaryExpr(ctx, startPos, V_UnaryOp_Not(), expr, resVarFacts)
    }
}

class S_UnaryOp_IncDec(val inc: Boolean, val post: Boolean): S_UnaryOp(if (inc) "++" else "--") {
    override fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr {
        val dst = expr.destination()
        val dstType = dst.effectiveType()

        val ops = if (R_IntegerType.isAssignableFrom(dstType)) {
            Pair(INTEGER_INCREMENT, INTEGER_DECREMENT)
        } else if (R_BigIntegerType.isAssignableFrom(dstType)) {
            Pair(BIG_INTEGER_INCREMENT, BIG_INTEGER_DECREMENT)
        } else if (R_DecimalType.isAssignableFrom(dstType)) {
            Pair(DECIMAL_INCREMENT, DECIMAL_DECREMENT)
        } else {
            val opCode = if (inc) "++" else "--"
            ctx.msgCtx.error(opPos, "expr_incdec_type:$opCode:${dstType.strCode()}",
                    "Bad operand type for '$opCode': ${dstType.str()}")
            Pair(INTEGER_INCREMENT, INTEGER_DECREMENT) // Fake ops for recovery.
        }

        val incDecOp = if (inc) ops.first else ops.second
        val op = C_AssignOp(opPos, incDecOp.op, incDecOp.rOp, incDecOp.dbOp)

        val resType = dst.resultType(expr.type)
        return V_IncDecExpr(ctx, startPos, resType, dst, expr, incDecOp.srcExpr, op, post)
    }

    companion object {
        private val INTEGER_ONE = R_ConstantValueExpr.makeInt(1)
        private val BIG_INTEGER_ONE = R_ConstantValueExpr(Rt_BigIntegerValue.get(1))
        private val DECIMAL_ONE = R_ConstantValueExpr(Rt_DecimalValue.get(1))

        private val INTEGER_INCREMENT = C_IncDecOp("++", R_BinaryOp_Add_Integer, Db_BinaryOp_Add_Integer, INTEGER_ONE)
        private val INTEGER_DECREMENT = C_IncDecOp("--", R_BinaryOp_Sub_Integer, Db_BinaryOp_Sub_Integer, INTEGER_ONE)
        private val BIG_INTEGER_INCREMENT = C_IncDecOp("++", R_BinaryOp_Add_BigInteger, Db_BinaryOp_Add_BigInteger, BIG_INTEGER_ONE)
        private val BIG_INTEGER_DECREMENT = C_IncDecOp("--", R_BinaryOp_Sub_BigInteger, Db_BinaryOp_Sub_BigInteger, BIG_INTEGER_ONE)
        private val DECIMAL_INCREMENT = C_IncDecOp("++", R_BinaryOp_Add_Decimal, Db_BinaryOp_Add_Decimal, DECIMAL_ONE)
        private val DECIMAL_DECREMENT = C_IncDecOp("--", R_BinaryOp_Sub_Decimal, Db_BinaryOp_Sub_Decimal, DECIMAL_ONE)
    }

    private class C_IncDecOp(val op: String, val rOp: R_BinaryOp, val dbOp: Db_BinaryOp, val srcExpr: R_Expr)
}

object S_UnaryOp_NotNull: S_UnaryOp("!!") {
    override fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr {
        val exprN = expr.asNullable().unwrap()
        val type = exprN.type
        val valueType = if (type is R_NullableType) type.valueType else {
            errTypeMismatch(ctx, opPos, type)
            type
        }

        val preFacts = exprN.varFacts.postFacts
        val varFacts = C_ExprVarFacts.forNullCast(preFacts, exprN)
        return V_UnaryExpr(ctx, startPos, V_UnaryOp_NotNull(valueType), exprN, varFacts)
    }
}

object S_UnaryOp_IsNull: S_UnaryOp("??") {
    override fun compile(ctx: C_ExprContext, startPos: S_Pos, opPos: S_Pos, expr: V_Expr): V_Expr {
        val exprN = expr.asNullable().unwrap()
        val type = exprN.type
        if (type !is R_NullableType) {
            errTypeMismatch(ctx, opPos, type)
        }

        val preFacts = exprN.varFacts
        val varFacts = C_ExprVarFacts.forNullCheck(exprN, false).update(postFacts = preFacts.postFacts)
        return V_UnaryExpr(ctx, startPos, V_UnaryOp_IsNull(true), exprN, varFacts)
    }
}

class S_UnaryExpr(startPos: S_Pos, val op: S_PosValue<S_UnaryOp>, val expr: S_Expr): S_Expr(startPos) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val cExpr = expr.compile(ctx)
        val vExpr = cExpr.value()
        checkUnitType(vExpr.type)
        val vResExpr = op.value.compile(ctx, startPos, op.pos, vExpr)
        return C_ValueExpr(vResExpr)
    }

    private fun checkUnitType(type: R_Type) = C_Utils.checkUnitType(op.pos, type) {
        "expr_operand_unit" toCodeMsg "Operand of '${op.value.code}' returns nothing"
    }
}
