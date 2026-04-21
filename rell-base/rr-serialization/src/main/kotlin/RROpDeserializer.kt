/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_BinaryOp
import net.postchain.rell.base.model.rr.RR_DbBinaryOp
import net.postchain.rell.base.model.rr.RR_DbUnaryOp
import net.postchain.rell.base.model.rr.RR_UnaryOp
import rell.ir.*

private val FB_TO_BINARY_OP: Map<UByte, RR_BinaryOp> = mapOf(
    BinaryOp.EQ to "R_BinaryOp_Eq",
    BinaryOp.NE to "R_BinaryOp_Ne",
    BinaryOp.EQ_REF to "R_BinaryOp_EqRef",
    BinaryOp.NE_REF to "R_BinaryOp_NeRef",
    BinaryOp.AND to "R_BinaryOp_And",
    BinaryOp.OR to "R_BinaryOp_Or",
    BinaryOp.ADD_INTEGER to "R_BinaryOp_Add_Integer",
    BinaryOp.SUB_INTEGER to "R_BinaryOp_Sub_Integer",
    BinaryOp.MUL_INTEGER to "R_BinaryOp_Mul_Integer",
    BinaryOp.DIV_INTEGER to "R_BinaryOp_Div_Integer",
    BinaryOp.MOD_INTEGER to "R_BinaryOp_Mod_Integer",
    BinaryOp.ADD_BIG_INTEGER to "R_BinaryOp_Add_BigInteger",
    BinaryOp.SUB_BIG_INTEGER to "R_BinaryOp_Sub_BigInteger",
    BinaryOp.MUL_BIG_INTEGER to "R_BinaryOp_Mul_BigInteger",
    BinaryOp.DIV_BIG_INTEGER to "R_BinaryOp_Div_BigInteger",
    BinaryOp.MOD_BIG_INTEGER to "R_BinaryOp_Mod_BigInteger",
    BinaryOp.ADD_DECIMAL to "R_BinaryOp_Add_Decimal",
    BinaryOp.SUB_DECIMAL to "R_BinaryOp_Sub_Decimal",
    BinaryOp.MUL_DECIMAL to "R_BinaryOp_Mul_Decimal",
    BinaryOp.DIV_DECIMAL to "R_BinaryOp_Div_Decimal",
    BinaryOp.MOD_DECIMAL to "R_BinaryOp_Mod_Decimal",
    BinaryOp.CONCAT_TEXT to "R_BinaryOp_Concat_Text",
    BinaryOp.CONCAT_BYTE_ARRAY to "R_BinaryOp_Concat_ByteArray",
    BinaryOp.CONCAT_LIST to "R_BinaryOp_Concat_List",
    BinaryOp.IN_COLLECTION to "R_BinaryOp_In_Collection",
    BinaryOp.IN_VIRTUAL_LIST to "R_BinaryOp_In_VirtualList",
    BinaryOp.IN_VIRTUAL_SET to "R_BinaryOp_In_VirtualSet",
    BinaryOp.IN_MAP to "R_BinaryOp_In_Map",
    BinaryOp.IN_RANGE to "R_BinaryOp_In_Range",
    BinaryOp.SUB_LIST to "R_BinaryOp_Sub_List",
    BinaryOp.SUB_SET to "R_BinaryOp_Sub_Set",
    BinaryOp.UNION_SET to "R_BinaryOp_Union_Set",
    BinaryOp.INTERSECT_LIST to "R_BinaryOp_Intersect_List",
    BinaryOp.INTERSECT_SET to "R_BinaryOp_Intersect_Set",
    BinaryOp.MERGE_MAP to "R_BinaryOp_Merge_Map",
)

private val FB_TO_UNARY_OP: Map<UByte, RR_UnaryOp> = mapOf(
    UnaryOp.MINUS_INTEGER to "Minus_Integer",
    UnaryOp.MINUS_BIG_INTEGER to "Minus_BigInteger",
    UnaryOp.MINUS_DECIMAL to "Minus_Decimal",
    UnaryOp.NOT to "Not",
)

fun deserializeRRBinaryOp(op: UByte): RR_BinaryOp =
    checkNotNull(FB_TO_BINARY_OP[op]) { "Unknown FlatBuffer binary op: $op" }

fun deserializeRRUnaryOp(op: UByte): RR_UnaryOp =
    checkNotNull(FB_TO_UNARY_OP[op]) { "Unknown FlatBuffer unary op: $op" }

private val FB_TO_CMP_OP: Map<UByte, String> = mapOf(
    CmpOp.LT to "R_CmpOp_Lt",
    CmpOp.GT to "R_CmpOp_Gt",
    CmpOp.LE to "R_CmpOp_Le",
    CmpOp.GE to "R_CmpOp_Ge",
)

private val FB_TO_CMP_TYPE: Map<UByte, String> = mapOf(
    CmpType.BOOLEAN to "R_CmpType_Boolean",
    CmpType.INTEGER to "R_CmpType_Integer",
    CmpType.BIG_INTEGER to "R_CmpType_BigInteger",
    CmpType.DECIMAL to "R_CmpType_Decimal",
    CmpType.TEXT to "R_CmpType_Text",
    CmpType.BYTE_ARRAY to "R_CmpType_ByteArray",
    CmpType.ROWID to "R_CmpType_Rowid",
    CmpType.ENTITY to "R_CmpType_Entity",
    CmpType.ENUM to "R_CmpType_Enum",
)

fun deserializeCmpOp(op: UByte): String =
    checkNotNull(FB_TO_CMP_OP[op]) { "Unknown FlatBuffer cmp op: $op" }

fun deserializeCmpType(type: UByte): String =
    checkNotNull(FB_TO_CMP_TYPE[type]) { "Unknown FlatBuffer cmp type: $type" }

// --- Database-level operators ---

private val FB_TO_DB_BINARY_OP: Map<UByte, RR_DbBinaryOp> = mapOf(
    DbBinaryOp.LT to "<",
    DbBinaryOp.GT to ">",
    DbBinaryOp.LE to "<=",
    DbBinaryOp.GE to ">=",
    DbBinaryOp.AND to "AND",
    DbBinaryOp.OR to "OR",
    DbBinaryOp.ADD_INTEGER to "+",
    DbBinaryOp.ADD_BIG_INTEGER to "+",
    DbBinaryOp.ADD_DECIMAL to "+",
    DbBinaryOp.SUB_INTEGER to "-",
    DbBinaryOp.SUB_BIG_INTEGER to "-",
    DbBinaryOp.SUB_DECIMAL to "-",
    DbBinaryOp.MUL_INTEGER to "*",
    DbBinaryOp.MUL_BIG_INTEGER to "*",
    DbBinaryOp.MUL_DECIMAL to "*",
    DbBinaryOp.DIV_INTEGER to "/",
    DbBinaryOp.DIV_BIG_INTEGER to "/",
    DbBinaryOp.DIV_DECIMAL to "/",
    DbBinaryOp.MOD_INTEGER to "%",
    DbBinaryOp.MOD_BIG_INTEGER to "%",
    DbBinaryOp.MOD_DECIMAL to "%",
    DbBinaryOp.CONCAT to "||",
    DbBinaryOp.IN to "IN",
    DbBinaryOp.NOT_IN to "NOT IN",
    DbBinaryOp.EQ to "=",
    DbBinaryOp.NE to "<>",
    DbBinaryOp.EQ_NULL to "IS NOT DISTINCT FROM",
    DbBinaryOp.NE_NULL to "IS DISTINCT FROM",
)

private val FB_TO_DB_UNARY_OP: Map<UByte, RR_DbUnaryOp> = mapOf(
    DbUnaryOp.MINUS_INTEGER to "-",
    DbUnaryOp.MINUS_BIG_INTEGER to "-",
    DbUnaryOp.MINUS_DECIMAL to "-",
    DbUnaryOp.NOT to "NOT",
)

fun deserializeDbBinaryOp(op: UByte): RR_DbBinaryOp =
    checkNotNull(FB_TO_DB_BINARY_OP[op]) { "Unknown FlatBuffer DB binary op: $op" }

fun deserializeDbUnaryOp(op: UByte): RR_DbUnaryOp =
    checkNotNull(FB_TO_DB_UNARY_OP[op]) { "Unknown FlatBuffer DB unary op: $op" }
