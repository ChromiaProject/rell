/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.*

/**
 * `kindName` is the deterministic serialized key; `code` is the display string.
 * These are separate because multiple ops can share a display code but never a kindName.
 */
sealed class R_CmpOp(val code: String, val kindName: String, val str: String, val checker: (Int) -> Boolean) {
    fun check(cmp: Int): Boolean = checker(cmp)
}

object R_CmpOp_Lt: R_CmpOp("<", "R_CmpOp_Lt", "less than", { it < 0 })
object R_CmpOp_Gt: R_CmpOp(">", "R_CmpOp_Gt", "greater than", { it > 0 })
object R_CmpOp_Le: R_CmpOp("<=", "R_CmpOp_Le", "less than or equal to", { it <= 0 })
object R_CmpOp_Ge: R_CmpOp(">=", "R_CmpOp_Ge", "greater than or equal to", { it >= 0 })

sealed class R_CmpType(val kindName: String) {
    companion object {
        fun forCmpOpType(type: R_Type): R_CmpType? = if (type == R_BooleanType) null else forType(type)
        fun forAtMinMaxType(type: R_Type): R_CmpType? = forType(type)

        private fun forType(type: R_Type): R_CmpType? = when (type) {
            R_BooleanType -> R_CmpType_Boolean
            R_IntegerType -> R_CmpType_Integer
            R_BigIntegerType -> R_CmpType_BigInteger
            R_DecimalType -> R_CmpType_Decimal
            R_TextType -> R_CmpType_Text
            R_ByteArrayType -> R_CmpType_ByteArray
            R_RowidType -> R_CmpType_Rowid
            is R_EntityType -> R_CmpType_Entity
            is R_EnumType -> R_CmpType_Enum
            else -> null
        }
    }
}

object R_CmpType_Boolean: R_CmpType("R_CmpType_Boolean")
object R_CmpType_Integer: R_CmpType("R_CmpType_Integer")
object R_CmpType_BigInteger: R_CmpType("R_CmpType_BigInteger")
object R_CmpType_Decimal: R_CmpType("R_CmpType_Decimal")
object R_CmpType_Text: R_CmpType("R_CmpType_Text")
object R_CmpType_ByteArray: R_CmpType("R_CmpType_ByteArray")
object R_CmpType_Rowid: R_CmpType("R_CmpType_Rowid")
object R_CmpType_Entity: R_CmpType("R_CmpType_Entity")
object R_CmpType_Enum: R_CmpType("R_CmpType_Enum")

/**
 * Compile-time identity for a binary operator. No evaluate() — all evaluation logic lives
 * in [net.postchain.rell.base.runtime.evaluateBinaryOp] and related functions in runtime/rt_ops.kt.
 *
 * `kindName` is the deterministic serialized dispatch key; it must match the branches of
 * `evaluateBinaryOp`. Declared explicitly (rather than deriving from `javaClass.simpleName`)
 * so the serialization contract survives class-name refactors. `code` is the display form.
 */
sealed class R_BinaryOp(val code: String, val kindName: String)

data object R_BinaryOp_Eq: R_BinaryOp("==", "R_BinaryOp_Eq")
data object R_BinaryOp_Ne: R_BinaryOp("!=", "R_BinaryOp_Ne")
data object R_BinaryOp_EqRef: R_BinaryOp("===", "R_BinaryOp_EqRef")
data object R_BinaryOp_NeRef: R_BinaryOp("!==", "R_BinaryOp_NeRef")

class R_BinaryOp_Cmp(val cmpOp: R_CmpOp, val cmpType: R_CmpType):
    R_BinaryOp(cmpOp.code, "Cmp_${cmpOp.kindName}_${cmpType.kindName}")

sealed class R_BinaryOp_Logic(code: String, kindName: String): R_BinaryOp(code, kindName)
data object R_BinaryOp_And: R_BinaryOp_Logic("and", "R_BinaryOp_And")
data object R_BinaryOp_Or: R_BinaryOp_Logic("or", "R_BinaryOp_Or")

class R_BinaryExpr(
        type: R_Type,
        val op: R_BinaryOp,
        val left: R_Expr,
        val right: R_Expr,
        val errPos: ErrorPos?,
): R_BaseExpr(type)

sealed class R_BinaryOp_Arith_Integer(code: String, kindName: String): R_BinaryOp(code, kindName)
sealed class R_BinaryOp_Arith_BigInteger(code: String, kindName: String): R_BinaryOp(code, kindName)
sealed class R_BinaryOp_Arith_Decimal(code: String, kindName: String): R_BinaryOp(code, kindName)

data object R_BinaryOp_Add_Integer: R_BinaryOp_Arith_Integer("+", "R_BinaryOp_Add_Integer")
data object R_BinaryOp_Add_BigInteger: R_BinaryOp_Arith_BigInteger("+", "R_BinaryOp_Add_BigInteger")
data object R_BinaryOp_Add_Decimal: R_BinaryOp_Arith_Decimal("+", "R_BinaryOp_Add_Decimal")
data object R_BinaryOp_Sub_Integer: R_BinaryOp_Arith_Integer("-", "R_BinaryOp_Sub_Integer")
data object R_BinaryOp_Sub_BigInteger: R_BinaryOp_Arith_BigInteger("-", "R_BinaryOp_Sub_BigInteger")
data object R_BinaryOp_Sub_Decimal: R_BinaryOp_Arith_Decimal("-", "R_BinaryOp_Sub_Decimal")
data object R_BinaryOp_Mul_Integer: R_BinaryOp_Arith_Integer("*", "R_BinaryOp_Mul_Integer")
data object R_BinaryOp_Mul_BigInteger: R_BinaryOp_Arith_BigInteger("*", "R_BinaryOp_Mul_BigInteger")
data object R_BinaryOp_Mul_Decimal: R_BinaryOp_Arith_Decimal("*", "R_BinaryOp_Mul_Decimal")
data object R_BinaryOp_Div_Integer: R_BinaryOp_Arith_Integer("/", "R_BinaryOp_Div_Integer")
data object R_BinaryOp_Div_BigInteger: R_BinaryOp_Arith_BigInteger("/", "R_BinaryOp_Div_BigInteger")
data object R_BinaryOp_Div_Decimal: R_BinaryOp_Arith_Decimal("/", "R_BinaryOp_Div_Decimal")
data object R_BinaryOp_Mod_Integer: R_BinaryOp_Arith_Integer("%", "R_BinaryOp_Mod_Integer")
data object R_BinaryOp_Mod_BigInteger: R_BinaryOp_Arith_BigInteger("%", "R_BinaryOp_Mod_BigInteger")
data object R_BinaryOp_Mod_Decimal: R_BinaryOp_Arith_Decimal("%", "R_BinaryOp_Mod_Decimal")

data object R_BinaryOp_Concat_Text: R_BinaryOp("+", "R_BinaryOp_Concat_Text")
data object R_BinaryOp_Concat_ByteArray: R_BinaryOp("+", "R_BinaryOp_Concat_ByteArray")
data object R_BinaryOp_Concat_List: R_BinaryOp("+", "R_BinaryOp_Concat_List")
data object R_BinaryOp_Union_Set: R_BinaryOp("+", "R_BinaryOp_Union_Set")
data object R_BinaryOp_Sub_List: R_BinaryOp("-", "R_BinaryOp_Sub_List")
data object R_BinaryOp_Sub_Set: R_BinaryOp("-", "R_BinaryOp_Sub_Set")
data object R_BinaryOp_Intersect_List: R_BinaryOp("&", "R_BinaryOp_Intersect_List")
data object R_BinaryOp_Intersect_Set: R_BinaryOp("&", "R_BinaryOp_Intersect_Set")
data object R_BinaryOp_Merge_Map: R_BinaryOp("+", "R_BinaryOp_Merge_Map")

data object R_BinaryOp_In_Collection: R_BinaryOp("in", "R_BinaryOp_In_Collection")
data object R_BinaryOp_In_VirtualList: R_BinaryOp("in", "R_BinaryOp_In_VirtualList")
data object R_BinaryOp_In_VirtualSet: R_BinaryOp("in", "R_BinaryOp_In_VirtualSet")
data object R_BinaryOp_In_Map: R_BinaryOp("in", "R_BinaryOp_In_Map")
data object R_BinaryOp_In_Range: R_BinaryOp("in", "R_BinaryOp_In_Range")
