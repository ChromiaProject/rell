/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.expr.C_EntityAttrRef
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_BooleanType
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.ImmList

sealed interface Db_BinaryOp {
    val code: String
    val sql: String
}

sealed class Db_BinaryOp_Basic(override val code: String, override val sql: String): Db_BinaryOp

class Db_BinaryOp_EqNe private constructor(
    override val code: String,
    override val sql: String,
): Db_BinaryOp {
    companion object {
        private val EQ: Db_BinaryOp = Db_BinaryOp_EqNe("==", "=")
        private val NE: Db_BinaryOp = Db_BinaryOp_EqNe("!=", "<>")
        private val EQ_NULLABLE: Db_BinaryOp = Db_BinaryOp_EqNe("==?", "IS NOT DISTINCT FROM")
        private val NE_NULLABLE: Db_BinaryOp = Db_BinaryOp_EqNe("!=?", "IS DISTINCT FROM")

        private val PAIR = EQ to NE
        private val PAIR_NULLABLE = EQ_NULLABLE to NE_NULLABLE

        fun get(equal: Boolean, nullable: Boolean): Db_BinaryOp {
            val pair = if (nullable) PAIR_NULLABLE else PAIR
            return if (equal) pair.first else pair.second
        }
    }
}

data object Db_BinaryOp_Lt: Db_BinaryOp_Basic("<", "<")
data object Db_BinaryOp_Gt: Db_BinaryOp_Basic(">", ">")
data object Db_BinaryOp_Le: Db_BinaryOp_Basic("<=", "<=")
data object Db_BinaryOp_Ge: Db_BinaryOp_Basic(">=", ">=")
data object Db_BinaryOp_Add_Integer: Db_BinaryOp_Basic("+", "+")
data object Db_BinaryOp_Add_BigInteger: Db_BinaryOp_Basic("+", "+")
data object Db_BinaryOp_Add_Decimal: Db_BinaryOp_Basic("+", "+")
data object Db_BinaryOp_Sub_Integer: Db_BinaryOp_Basic("-", "-")
data object Db_BinaryOp_Sub_BigInteger: Db_BinaryOp_Basic("-", "-")
data object Db_BinaryOp_Sub_Decimal: Db_BinaryOp_Basic("-", "-")
data object Db_BinaryOp_Mul_Integer: Db_BinaryOp_Basic("*", "*")
data object Db_BinaryOp_Mul_BigInteger: Db_BinaryOp_Basic("*", "*")
data object Db_BinaryOp_Mul_Decimal: Db_BinaryOp_Basic("*", "*")
data object Db_BinaryOp_Div_Integer: Db_BinaryOp_Basic("/", "/")
data object Db_BinaryOp_Div_BigInteger: Db_BinaryOp {
    override val code: String
        get() = "/"
    override val sql: String
        get() = "FN:DIV"
}

data object Db_BinaryOp_Div_Decimal: Db_BinaryOp_Basic("/", "/")
data object Db_BinaryOp_Mod_Integer: Db_BinaryOp_Basic("%", "%")
data object Db_BinaryOp_Mod_BigInteger: Db_BinaryOp_Basic("%", "%")
data object Db_BinaryOp_Mod_Decimal: Db_BinaryOp_Basic("%", "%")
data object Db_BinaryOp_Concat: Db_BinaryOp_Basic("+", "||")
data object Db_BinaryOp_In: Db_BinaryOp_Basic("in", "IN")
data object Db_BinaryOp_NotIn: Db_BinaryOp_Basic("not_in", "NOT IN")

sealed class Db_BinaryOp_AndOr(
    code: String,
    sql: String,
): Db_BinaryOp_Basic(code, sql)

object Db_BinaryOp_And: Db_BinaryOp_AndOr("and", "AND")
object Db_BinaryOp_Or: Db_BinaryOp_AndOr("or", "OR")

sealed class Db_UnaryOp(val code: String, val sql: String, val postfix: Boolean = false)
data object Db_UnaryOp_Minus_Integer: Db_UnaryOp("-", "-")
data object Db_UnaryOp_Minus_BigInteger: Db_UnaryOp("-", "-")
data object Db_UnaryOp_Minus_Decimal: Db_UnaryOp("-", "-")
data object Db_UnaryOp_Not: Db_UnaryOp("not", "NOT")

abstract class Db_Expr(val type: R_Type)

class Db_InterpretedExpr(val expr: R_Expr): Db_Expr(expr.type)

class Db_BinaryExpr(
    type: R_Type,
    val op: Db_BinaryOp,
    val left: Db_Expr,
    val right: Db_Expr,
): Db_Expr(type)

class Db_UnaryExpr(
    type: R_Type,
    val op: Db_UnaryOp,
    val expr: Db_Expr,
): Db_Expr(type)

sealed class Db_TableExpr(val rEntity: R_EntityDefinition, type: R_Type): Db_Expr(type)

class Db_EntityExpr(
    val entity: R_DbAtEntity,
    type: R_Type = entity.rEntity.type,
): Db_TableExpr(entity.rEntity, type)

class Db_RelExpr(
    val base: Db_TableExpr,
    val attr: R_Attribute,
    targetEntity: R_EntityDefinition,
    type: R_Type = targetEntity.type,
): Db_TableExpr(targetEntity, type)

class Db_AttrExpr(
    val base: Db_TableExpr,
    val attr: R_Attribute,
    type: R_Type = attr.type,
): Db_Expr(type)

class Db_RowidExpr(val base: Db_TableExpr): Db_Expr(C_EntityAttrRef.ROWID_TYPE)

class Db_CollectionInterpretedExpr(val expr: R_Expr): Db_Expr(expr.type)

class Db_InExpr(
    val keyExpr: Db_Expr,
    val exprs: ImmList<Db_Expr>,
    val not: Boolean,
): Db_Expr(R_BooleanType)

class Db_ElvisExpr(
    type: R_Type,
    val left: Db_Expr,
    val right: Db_Expr,
): Db_Expr(type)

class Db_CallExpr(
    type: R_Type,
    val fn: Db_SysFunction,
    val args: ImmList<Db_Expr>,
): Db_Expr(type)

class Db_ExistsExpr(
    val subExpr: Db_Expr,
    val not: Boolean,
): Db_Expr(R_BooleanType)

class Db_InCollectionExpr(
    val left: Db_Expr,
    val right: R_Expr,
    val not: Boolean,
): Db_Expr(R_BooleanType)
