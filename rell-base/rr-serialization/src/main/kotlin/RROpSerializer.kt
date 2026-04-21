/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.rell.base.model.rr.RR_BinaryOp
import net.postchain.rell.base.model.rr.RR_DbBinaryOp
import net.postchain.rell.base.model.rr.RR_DbUnaryOp
import net.postchain.rell.base.model.rr.RR_UnaryOp
import rell.ir.*

private val BINARY_OP_MAP = mapOf(
    // Equality & identity
    "R_BinaryOp_Eq" to BinaryOp.EQ,
    "R_BinaryOp_Ne" to BinaryOp.NE,
    "R_BinaryOp_EqRef" to BinaryOp.EQ_REF,
    "R_BinaryOp_NeRef" to BinaryOp.NE_REF,
    // Logical
    "R_BinaryOp_And" to BinaryOp.AND,
    "R_BinaryOp_Or" to BinaryOp.OR,
    // Integer arithmetic
    "R_BinaryOp_Add_Integer" to BinaryOp.ADD_INTEGER,
    "R_BinaryOp_Sub_Integer" to BinaryOp.SUB_INTEGER,
    "R_BinaryOp_Mul_Integer" to BinaryOp.MUL_INTEGER,
    "R_BinaryOp_Div_Integer" to BinaryOp.DIV_INTEGER,
    "R_BinaryOp_Mod_Integer" to BinaryOp.MOD_INTEGER,
    // BigInteger arithmetic
    "R_BinaryOp_Add_BigInteger" to BinaryOp.ADD_BIG_INTEGER,
    "R_BinaryOp_Sub_BigInteger" to BinaryOp.SUB_BIG_INTEGER,
    "R_BinaryOp_Mul_BigInteger" to BinaryOp.MUL_BIG_INTEGER,
    "R_BinaryOp_Div_BigInteger" to BinaryOp.DIV_BIG_INTEGER,
    "R_BinaryOp_Mod_BigInteger" to BinaryOp.MOD_BIG_INTEGER,
    // Decimal arithmetic
    "R_BinaryOp_Add_Decimal" to BinaryOp.ADD_DECIMAL,
    "R_BinaryOp_Sub_Decimal" to BinaryOp.SUB_DECIMAL,
    "R_BinaryOp_Mul_Decimal" to BinaryOp.MUL_DECIMAL,
    "R_BinaryOp_Div_Decimal" to BinaryOp.DIV_DECIMAL,
    "R_BinaryOp_Mod_Decimal" to BinaryOp.MOD_DECIMAL,
    // Concatenation
    "R_BinaryOp_Concat_Text" to BinaryOp.CONCAT_TEXT,
    "R_BinaryOp_Concat_ByteArray" to BinaryOp.CONCAT_BYTE_ARRAY,
    "R_BinaryOp_Concat_List" to BinaryOp.CONCAT_LIST,
    // Membership
    "R_BinaryOp_In_Collection" to BinaryOp.IN_COLLECTION,
    "R_BinaryOp_In_VirtualList" to BinaryOp.IN_VIRTUAL_LIST,
    "R_BinaryOp_In_VirtualSet" to BinaryOp.IN_VIRTUAL_SET,
    "R_BinaryOp_In_Map" to BinaryOp.IN_MAP,
    "R_BinaryOp_In_Range" to BinaryOp.IN_RANGE,
    // Set/list/map operations
    "R_BinaryOp_Sub_List" to BinaryOp.SUB_LIST,
    "R_BinaryOp_Sub_Set" to BinaryOp.SUB_SET,
    "R_BinaryOp_Union_Set" to BinaryOp.UNION_SET,
    "R_BinaryOp_Intersect_List" to BinaryOp.INTERSECT_LIST,
    "R_BinaryOp_Intersect_Set" to BinaryOp.INTERSECT_SET,
    "R_BinaryOp_Merge_Map" to BinaryOp.MERGE_MAP,
)

private val UNARY_OP_MAP = mapOf(
    "Minus_Integer" to UnaryOp.MINUS_INTEGER,
    "Minus_BigInteger" to UnaryOp.MINUS_BIG_INTEGER,
    "Minus_Decimal" to UnaryOp.MINUS_DECIMAL,
    "Not" to UnaryOp.NOT,
)

fun serializeRRBinaryOp(op: RR_BinaryOp): UByte = BINARY_OP_MAP[op]
    ?: if (op.startsWith("Cmp_")) BinaryOp.EQ  // Comparison ops are dispatched via cmpInfo, not op.
    else error("Unknown binary op: $op")

fun serializeRRUnaryOp(op: RR_UnaryOp): UByte = checkNotNull(UNARY_OP_MAP[op]) { "Unknown unary op: $op" }

private val CMP_OP_MAP = mapOf(
    "R_CmpOp_Lt" to CmpOp.LT,
    "R_CmpOp_Gt" to CmpOp.GT,
    "R_CmpOp_Le" to CmpOp.LE,
    "R_CmpOp_Ge" to CmpOp.GE,
)

private val CMP_TYPE_MAP = mapOf(
    "R_CmpType_Boolean" to CmpType.BOOLEAN,
    "R_CmpType_Integer" to CmpType.INTEGER,
    "R_CmpType_BigInteger" to CmpType.BIG_INTEGER,
    "R_CmpType_Decimal" to CmpType.DECIMAL,
    "R_CmpType_Text" to CmpType.TEXT,
    "R_CmpType_ByteArray" to CmpType.BYTE_ARRAY,
    "R_CmpType_Rowid" to CmpType.ROWID,
    "R_CmpType_Entity" to CmpType.ENTITY,
    "R_CmpType_Enum" to CmpType.ENUM,
)

fun serializeCmpOp(op: String): UByte = checkNotNull(CMP_OP_MAP[op]) { "Unknown cmp op: $op" }

fun serializeCmpType(type: String): UByte = checkNotNull(CMP_TYPE_MAP[type]) { "Unknown cmp type: $type" }

// --- Database-level operators ---

private val DB_BINARY_OP_MAP = mapOf(
    "<" to DbBinaryOp.LT,
    ">" to DbBinaryOp.GT,
    "<=" to DbBinaryOp.LE,
    ">=" to DbBinaryOp.GE,
    "AND" to DbBinaryOp.AND,
    "and" to DbBinaryOp.AND,
    "OR" to DbBinaryOp.OR,
    "or" to DbBinaryOp.OR,
    "+" to DbBinaryOp.ADD_INTEGER,
    "-" to DbBinaryOp.SUB_INTEGER,
    "*" to DbBinaryOp.MUL_INTEGER,
    "/" to DbBinaryOp.DIV_INTEGER,
    "%" to DbBinaryOp.MOD_INTEGER,
    "||" to DbBinaryOp.CONCAT,
    "IN" to DbBinaryOp.IN,
    "NOT IN" to DbBinaryOp.NOT_IN,
    "=" to DbBinaryOp.EQ,
    "<>" to DbBinaryOp.NE,
    "IS NOT DISTINCT FROM" to DbBinaryOp.EQ_NULL,
    "IS DISTINCT FROM" to DbBinaryOp.NE_NULL,
)

private val DB_UNARY_OP_MAP = mapOf(
    "-" to DbUnaryOp.MINUS_INTEGER,
    "NOT" to DbUnaryOp.NOT,
)

fun serializeDbBinaryOp(op: RR_DbBinaryOp): UByte = checkNotNull(DB_BINARY_OP_MAP[op]) { "Unknown DB binary op: $op" }

fun serializeDbUnaryOp(op: RR_DbUnaryOp): UByte = checkNotNull(DB_UNARY_OP_MAP[op]) { "Unknown DB unary op: $op" }
