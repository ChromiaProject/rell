/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.expr.C_EntityAttrRef
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_DecimalType
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.immListOf

sealed class Db_BinaryOp(val code: String) {
    open fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value? = null

    abstract fun toSql(ctx: SqlGenContext, bld: SqlBuilder, left: RedDb_Expr, right: RedDb_Expr)

    open fun toRedExpr(frame: Rt_CallFrame, type: R_Type, redLeft: RedDb_Expr, right: Db_Expr): RedDb_Expr {
        val redRight = right.toRedExpr(frame)

        val leftValue = redLeft.constantValue()
        val rightValue = if (leftValue == null) null else redRight.constantValue()
        if (leftValue != null && rightValue != null) {
            val resValue = evaluate(leftValue, rightValue)
            if (resValue != null) {
                return RedDb_ConstantExpr(resValue)
            }
        }

        val redExpr = RedDb_BinaryExpr(this, redLeft, redRight)
        return RedDb_Utils.wrapDecimalExpr(type, redExpr)
    }
}

sealed class Db_BinaryOp_Basic(code: String, private val sql: String): Db_BinaryOp(code) {
    final override fun toSql(ctx: SqlGenContext, bld: SqlBuilder, left: RedDb_Expr, right: RedDb_Expr) {
        left.toSql(ctx, bld, true)
        bld.append(" ")
        bld.append(sql)
        bld.append(" ")
        right.toSql(ctx, bld, true)
    }
}

class Db_BinaryOp_EqNe private constructor(
    code: String,
    private val sql: String,
    private val equal: Boolean,
    private val nullable: Boolean,
): Db_BinaryOp(code) {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val eq = left == right
        val res = if (equal) eq else !eq
        return Rt_BooleanValue.get(res)
    }

    override fun toSql(ctx: SqlGenContext, bld: SqlBuilder, left: RedDb_Expr, right: RedDb_Expr) {
        if (!nullable) {
            toSql0(ctx, bld, left, right)
            return
        }

        val leftValue = left.constantValue()
        if (leftValue != null) {
            toSqlPart(ctx, bld, right, leftValue)
            return
        }

        val rightValue = right.constantValue()
        if (rightValue != null) {
            toSqlPart(ctx, bld, left, rightValue)
            return
        }

        toSql0(ctx, bld, left, right)
    }

    private fun toSqlPart(ctx: SqlGenContext, bld: SqlBuilder, left: RedDb_Expr, rightValue: Rt_Value) {
        left.toSql(ctx, bld, true)
        bld.append(" ")

        if (rightValue == Rt_NullValue) {
            val sql0 = if (equal) "IS NULL" else "IS NOT NULL"
            bld.append(sql0)
        } else {
            bld.append(sql)
            bld.append(" ")
            bld.append(rightValue)
        }
    }

    private fun toSql0(ctx: SqlGenContext, bld: SqlBuilder, left: RedDb_Expr, right: RedDb_Expr) {
        left.toSql(ctx, bld, true)
        bld.append(" ")
        bld.append(sql)
        bld.append(" ")
        right.toSql(ctx, bld, true)
    }

    companion object {
        private val EQ: Db_BinaryOp = Db_BinaryOp_EqNe("==", "=", true, nullable = false)
        private val NE: Db_BinaryOp = Db_BinaryOp_EqNe("!=", "<>", false, nullable = false)
        private val EQ_NULLABLE: Db_BinaryOp = Db_BinaryOp_EqNe("==?", "IS NOT DISTINCT FROM", true, nullable = true)
        private val NE_NULLABLE: Db_BinaryOp = Db_BinaryOp_EqNe("!=?", "IS DISTINCT FROM", false, nullable = true)

        private val PAIR = EQ to NE
        private val PAIR_NULLABLE = EQ_NULLABLE to NE_NULLABLE

        fun get(equal: Boolean, nullable: Boolean): Db_BinaryOp {
            val pair = if (nullable) PAIR_NULLABLE else PAIR
            return if (equal) pair.first else pair.second
        }
    }
}

object Db_BinaryOp_Lt: Db_BinaryOp_Basic("<", "<")
object Db_BinaryOp_Gt: Db_BinaryOp_Basic(">", ">")
object Db_BinaryOp_Le: Db_BinaryOp_Basic("<=", "<=")
object Db_BinaryOp_Ge: Db_BinaryOp_Basic(">=", ">=")
object Db_BinaryOp_Add_Integer: Db_BinaryOp_Basic("+", "+")
object Db_BinaryOp_Add_BigInteger: Db_BinaryOp_Basic("+", "+")
object Db_BinaryOp_Add_Decimal: Db_BinaryOp_Basic("+", "+")
object Db_BinaryOp_Sub_Integer: Db_BinaryOp_Basic("-", "-")
object Db_BinaryOp_Sub_BigInteger: Db_BinaryOp_Basic("-", "-")
object Db_BinaryOp_Sub_Decimal: Db_BinaryOp_Basic("-", "-")
object Db_BinaryOp_Mul_Integer: Db_BinaryOp_Basic("*", "*")
object Db_BinaryOp_Mul_BigInteger: Db_BinaryOp_Basic("*", "*")
object Db_BinaryOp_Mul_Decimal: Db_BinaryOp_Basic("*", "*")
object Db_BinaryOp_Div_Integer: Db_BinaryOp_Basic("/", "/")

object Db_BinaryOp_Div_BigInteger: Db_BinaryOp("/") {
    override fun toSql(ctx: SqlGenContext, bld: SqlBuilder, left: RedDb_Expr, right: RedDb_Expr) {
        bld.append("DIV(")
        left.toSql(ctx, bld, false)
        bld.append(", ")
        right.toSql(ctx, bld, false)
        bld.append(")")
    }
}

object Db_BinaryOp_Div_Decimal: Db_BinaryOp_Basic("/", "/")
object Db_BinaryOp_Mod_Integer: Db_BinaryOp_Basic("%", "%")
object Db_BinaryOp_Mod_BigInteger: Db_BinaryOp_Basic("%", "%")
object Db_BinaryOp_Mod_Decimal: Db_BinaryOp_Basic("%", "%")
object Db_BinaryOp_Concat: Db_BinaryOp_Basic("+", "||")
object Db_BinaryOp_In: Db_BinaryOp_Basic("in", "IN")
object Db_BinaryOp_NotIn: Db_BinaryOp_Basic("not_in", "NOT IN")

sealed class Db_BinaryOp_AndOr(
    code: String,
    sql: String,
    private val shortCircuitValue: Boolean,
): Db_BinaryOp_Basic(code, sql) {
    final override fun toRedExpr(frame: Rt_CallFrame, type: R_Type, redLeft: RedDb_Expr, right: Db_Expr): RedDb_Expr {
        val leftValue = redLeft.constantValue()
        if (leftValue != null) {
            val v = leftValue.asBoolean()
            return if (v == shortCircuitValue) RedDb_ConstantExpr(leftValue) else right.toRedExpr(frame)
        }

        val redRight = right.toRedExpr(frame)
        val rightValue = redRight.constantValue()
        if (rightValue != null) {
            val v = rightValue.asBoolean()
            return if (v == shortCircuitValue) RedDb_ConstantExpr(rightValue) else redLeft
        }

        val redExpr = RedDb_BinaryExpr(this, redLeft, redRight)
        return RedDb_Utils.wrapDecimalExpr(type, redExpr)
    }
}

object Db_BinaryOp_And: Db_BinaryOp_AndOr("and", "AND", false)
object Db_BinaryOp_Or: Db_BinaryOp_AndOr("or", "OR", true)

sealed class Db_UnaryOp(val code: String, val sql: String, val postfix: Boolean = false)
object Db_UnaryOp_Minus_Integer: Db_UnaryOp("-", "-")
object Db_UnaryOp_Minus_BigInteger: Db_UnaryOp("-", "-")
object Db_UnaryOp_Minus_Decimal: Db_UnaryOp("-", "-")
object Db_UnaryOp_Not: Db_UnaryOp("not", "NOT")

abstract class Db_Expr(val type: R_Type) {
    abstract fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr
}

abstract class RedDb_Expr {
    protected open fun needsEnclosing() = true

    open fun constantValue(): Rt_Value? = null

    protected abstract fun toSql0(ctx: SqlGenContext, bld: SqlBuilder)

    fun toSql(ctx: SqlGenContext, bld: SqlBuilder, enclose: Boolean) {
        val reallyEnclose = enclose && needsEnclosing()
        if (reallyEnclose) bld.append("(")
        toSql0(ctx, bld)
        if (reallyEnclose) bld.append(")")
    }
}

class Db_InterpretedExpr(val expr: R_Expr): Db_Expr(expr.type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val value = expr.evaluate(frame)
        return RedDb_ConstantExpr(value)
    }
}

private class RedDb_ConstantExpr(val value: Rt_Value): RedDb_Expr() {
    override fun needsEnclosing() = false
    override fun constantValue() = value

    override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
        bld.append(value)
    }
}

class Db_BinaryExpr(type: R_Type, val op: Db_BinaryOp, val left: Db_Expr, val right: Db_Expr): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redLeft = left.toRedExpr(frame)
        return op.toRedExpr(frame, type, redLeft, right)
    }
}

private class RedDb_BinaryExpr(val op: Db_BinaryOp, val left: RedDb_Expr, val right: RedDb_Expr): RedDb_Expr() {
    override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
        op.toSql(ctx, bld, left, right)
    }
}

class Db_UnaryExpr(type: R_Type, val op: Db_UnaryOp, val expr: Db_Expr): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redExpr = expr.toRedExpr(frame)
        return RedDb_UnaryExpr(op, redExpr)
    }

    private class RedDb_UnaryExpr(val op: Db_UnaryOp, val expr: RedDb_Expr): RedDb_Expr() {
        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            if (op.postfix) {
                expr.toSql(ctx, bld, true)
                bld.append(" ")
                bld.append(op.sql)
            } else {
                bld.append(op.sql)
                bld.append(" ")
                expr.toSql(ctx, bld, true)
            }
        }
    }
}

sealed class Db_TableExpr(val rEntity: R_EntityDefinition, type: R_Type): Db_Expr(type) {
    abstract fun alias(ctx: SqlGenContext): SqlTableAlias
}

class Db_EntityExpr(val entity: R_DbAtEntity, type: R_Type = entity.rEntity.type): Db_TableExpr(entity.rEntity, type) {
    override fun alias(ctx: SqlGenContext) = ctx.getEntityAlias(entity)

    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        return RedDb_EntityExpr(this)
    }

    private class RedDb_EntityExpr(val entityExpr: Db_EntityExpr): RedDb_Expr() {
        override fun needsEnclosing() = false

        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            val alias = entityExpr.alias(ctx)
            val rowidCol = entityExpr.rEntity.sqlMapping.rowidColumn()
            bld.appendColumn(alias, rowidCol)
        }
    }
}

class Db_RelExpr(
    val base: Db_TableExpr,
    val attr: R_Attribute,
    targetEntity: R_EntityDefinition,
    type: R_Type = targetEntity.type,
): Db_TableExpr(targetEntity, type) {
    override fun alias(ctx: SqlGenContext): SqlTableAlias {
        val baseAlias = base.alias(ctx)
        return ctx.getRelAlias(baseAlias, attr, rEntity)
    }

    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        return RedDb_RelExpr(base, attr)
    }

    private class RedDb_RelExpr(val base: Db_TableExpr, val attr: R_Attribute): RedDb_Expr() {
        override fun needsEnclosing() = false

        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            val alias = base.alias(ctx)
            bld.appendColumn(alias, attr.sqlMapping)
        }
    }
}

class Db_AttrExpr(val base: Db_TableExpr, val attr: R_Attribute, type: R_Type = attr.type): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redExpr = RedDb_AttrExpr(base, attr)
        return RedDb_Utils.wrapDecimalExpr(type, redExpr)
    }

    private class RedDb_AttrExpr(val base: Db_TableExpr, val attr: R_Attribute): RedDb_Expr() {
        override fun needsEnclosing() = false

        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            val alias = base.alias(ctx)
            bld.appendColumn(alias, attr.sqlMapping)
        }
    }
}

class Db_RowidExpr(val base: Db_TableExpr): Db_Expr(C_EntityAttrRef.ROWID_TYPE) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        return base.toRedExpr(frame)
    }
}

class Db_CollectionInterpretedExpr(val expr: R_Expr): Db_Expr(expr.type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val value = expr.evaluate(frame)
        val collection = value.asCollection()
        return RedDb_CollectionConstantExpr(collection)
    }

    private class RedDb_CollectionConstantExpr(val value: Collection<Rt_Value>): RedDb_Expr() {
        override fun needsEnclosing() = false

        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            bld.append("(")
            bld.append(value, ",") {
                bld.append(it)
            }
            bld.append(")")
        }
    }
}

class Db_InExpr(val keyExpr: Db_Expr, val exprs: List<Db_Expr>, val not: Boolean): Db_Expr(R_BooleanType) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redKeyExpr = keyExpr.toRedExpr(frame)
        val redExprTypes = toRedExprs(frame, redKeyExpr, exprs)
        return if (redExprTypes != null) {
            val redExprs = redExprTypes.map { it.first }
            RedDb_Utils.makeRedDbInExpr(redKeyExpr, redExprs, not)
        } else {
            RedDb_ConstantExpr(Rt_BooleanValue.get(!not))
        }
    }

    companion object {
        fun toRedExprs(
            frame: Rt_CallFrame,
            redKeyExpr: RedDb_Expr,
            exprs: List<Db_Expr>,
        ): List<Pair<RedDb_Expr, R_Type>>? {
            val keyValue = redKeyExpr.constantValue()

            val redExprs = mutableListOf<Pair<RedDb_Expr, R_Type>>()
            for (expr in exprs) {
                val redExpr = expr.toRedExpr(frame)
                val exprValue = if (keyValue == null) null else redExpr.constantValue()
                if (keyValue != null && exprValue != null) {
                    if (exprValue == keyValue) {
                        return null
                    }
                } else {
                    redExprs.add(redExpr to expr.type)
                }
            }

            return redExprs
        }
    }
}

private class RedDb_InExpr(val keyExpr: RedDb_Expr, val exprs: List<RedDb_Expr>, val not: Boolean): RedDb_Expr() {
    override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
        keyExpr.toSql(ctx, bld, true)
        if (not) bld.append(" NOT")
        bld.append(" IN (")
        bld.append(exprs, ",") { expr ->
            expr.toSql(ctx, bld, false)
        }
        bld.append(")")
    }
}

class Db_ElvisExpr(type: R_Type, private val left: Db_Expr, private val right: Db_Expr): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redLeft = left.toRedExpr(frame)

        val leftValue = redLeft.constantValue()
        if (leftValue != null && leftValue != Rt_NullValue) {
            return redLeft
        }

        val redRight = right.toRedExpr(frame)
        return if (leftValue != null && leftValue == Rt_NullValue) redRight else {
            RedDb_CallExpr(Db_SysFunction.COALESCE, immListOf(redLeft, redRight))
        }
    }
}

class Db_CallExpr(type: R_Type, val fn: Db_SysFunction, val args: List<Db_Expr>): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redArgs = args.map { it.toRedExpr(frame) }
        val redExpr = RedDb_CallExpr(fn, redArgs)
        return RedDb_Utils.wrapDecimalExpr(type, redExpr)
    }
}

private class RedDb_CallExpr(val fn: Db_SysFunction, val args: List<RedDb_Expr>): RedDb_Expr() {
    override fun needsEnclosing() = false
    override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) = fn.toSql(ctx, bld, args)
}

class Db_ExistsExpr(val subExpr: Db_Expr, val not: Boolean): Db_Expr(R_BooleanType) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redSubExpr = subExpr.toRedExpr(frame)
        return RedDb_ExistsExpr(not, redSubExpr)
    }

    private class RedDb_ExistsExpr(val not: Boolean, val subExpr: RedDb_Expr): RedDb_Expr() {
        override fun needsEnclosing() = false

        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            if (not) bld.append("NOT ")
            bld.append("EXISTS(")
            subExpr.toSql(ctx, bld, false)
            bld.append(")")
        }
    }
}

class Db_InCollectionExpr(val left: Db_Expr, val right: R_Expr, val not: Boolean): Db_Expr(R_BooleanType) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redLeft = left.toRedExpr(frame)

        val rightValue = right.evaluate(frame).asCollection()
        if (rightValue.isEmpty()) {
            return RedDb_ConstantExpr(Rt_BooleanValue.get(not))
        }

        val leftValue = redLeft.constantValue()
        if (leftValue != null && leftValue in rightValue) {
            return RedDb_ConstantExpr(Rt_BooleanValue.get(!not))
        }

        return RedDb_InCollectionExpr(redLeft, rightValue, not)
    }

    private class RedDb_InCollectionExpr(
            val left: RedDb_Expr,
            val rightValue: Collection<Rt_Value>,
            val not: Boolean
    ): RedDb_Expr() {
        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            left.toSql(ctx, bld, true)
            if (not) bld.append(" NOT")
            bld.append(" IN (")
            bld.append(rightValue, ",") {
                bld.append(it)
            }
            bld.append(")")
        }
    }
}

object RedDb_Utils {
    fun makeRedDbBinaryExprChain(op: Db_BinaryOp, exprs: List<RedDb_Expr>): RedDb_Expr {
        return CommonUtils.foldSimple(exprs) { left, right -> RedDb_BinaryExpr(op, left, right) }
    }

    fun makeRedDbEqExpr(left: RedDb_Expr, right: RedDb_Expr, equal: Boolean, nullable: Boolean): RedDb_Expr {
        val op = Db_BinaryOp_EqNe.get(equal, nullable = nullable)
        return RedDb_BinaryExpr(op, left, right)
    }

    fun makeRedDbInExpr(left: RedDb_Expr, right: List<RedDb_Expr>, not: Boolean): RedDb_Expr {
        return if (right.isEmpty()) {
            RedDb_ConstantExpr(Rt_BooleanValue.get(not))
        } else if (right.size == 1) {
            makeRedDbEqExpr(left, right[0], equal = !not, nullable = false)
        } else {
            RedDb_InExpr(left, right, not)
        }
    }

    fun wrapDecimalExpr(type: R_Type, redExpr: RedDb_Expr): RedDb_Expr {
        return if (type != R_DecimalType || redExpr is RedDb_DecimalRoundExpr) {
            redExpr
        } else {
            val value = redExpr.constantValue()
            if (value != null) redExpr else RedDb_DecimalRoundExpr(redExpr)
        }
    }

    private class RedDb_DecimalRoundExpr(private val expr: RedDb_Expr): RedDb_Expr() {
        override fun needsEnclosing() = false

        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            bld.append("ROUND(")
            expr.toSql(ctx, bld, false)
            bld.append(", ${Lib_DecimalMath.DECIMAL_FRAC_DIGITS})")
        }
    }
}
