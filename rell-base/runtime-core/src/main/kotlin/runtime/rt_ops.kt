/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.model.rr.RR_CmpBinaryOp
import net.postchain.rell.base.model.rr.RR_ConstantValue
import kotlin.math.min

fun evaluateBinaryOp(key: String, left: Rt_Value, right: Rt_Value): Rt_Value = when (key) {
    "R_BinaryOp_Eq" -> Rt_BooleanValue.get(left == right)
    "R_BinaryOp_Ne" -> Rt_BooleanValue.get(left != right)
    "R_BinaryOp_EqRef" -> Rt_BooleanValue.get(left === right)
    "R_BinaryOp_NeRef" -> Rt_BooleanValue.get(left !== right)

    // Logical (full two-arg evaluation; short-circuit is handled separately)
    "R_BinaryOp_And" -> Rt_BooleanValue.get((left as Rt_BooleanValue).value && (right as Rt_BooleanValue).value)
    "R_BinaryOp_Or" -> Rt_BooleanValue.get((left as Rt_BooleanValue).value || (right as Rt_BooleanValue).value)

    // Integer arithmetic
    "R_BinaryOp_Add_Integer" -> evalIntArith("+", left, right) { a, b -> Math.addExact(a, b) }
    "R_BinaryOp_Sub_Integer" -> evalIntArith("-", left, right) { a, b -> Math.subtractExact(a, b) }
    "R_BinaryOp_Mul_Integer" -> evalIntArith("*", left, right) { a, b -> Math.multiplyExact(a, b) }
    "R_BinaryOp_Div_Integer" -> evalIntArith("/", left, right) { a, b ->
        if (b == 0L) throw Rt_Exception.common("expr:/:div0:$a", "Division by zero: $a / 0")
        a / b
    }

    "R_BinaryOp_Mod_Integer" -> evalIntArith("%", left, right) { a, b ->
        if (b == 0L) throw Rt_Exception.common("expr:%:div0:$a", "Division by zero: $a % 0")
        a % b
    }

    // BigInteger arithmetic
    "R_BinaryOp_Add_BigInteger" -> evalBigIntArith("+", left, right) { a, b -> Lib_BigIntegerMath.add(a, b) }
    "R_BinaryOp_Sub_BigInteger" -> evalBigIntArith("-", left, right) { a, b -> Lib_BigIntegerMath.subtract(a, b) }
    "R_BinaryOp_Mul_BigInteger" -> evalBigIntArith("*", left, right) { a, b -> Lib_BigIntegerMath.multiply(a, b) }
    "R_BinaryOp_Div_BigInteger" -> evalBigIntArith("/", left, right) { a, b ->
        if (b.signum() == 0) throw Rt_Exception.common("expr:/:div0:$a", "Division by zero: $a / $b")
        Lib_BigIntegerMath.divide(a, b)
    }

    "R_BinaryOp_Mod_BigInteger" -> evalBigIntArith("%", left, right) { a, b ->
        if (b.signum() == 0) throw Rt_Exception.common("expr:%:div0", "Division by zero: %")
        Lib_BigIntegerMath.remainder(a, b)
    }

    // Decimal arithmetic
    "R_BinaryOp_Add_Decimal" -> evalDecArith("+", left, right) { a, b -> Lib_DecimalMath.add(a, b) }
    "R_BinaryOp_Sub_Decimal" -> evalDecArith("-", left, right) { a, b -> Lib_DecimalMath.subtract(a, b) }
    "R_BinaryOp_Mul_Decimal" -> evalDecArith("*", left, right) { a, b -> Lib_DecimalMath.multiply(a, b) }
    "R_BinaryOp_Div_Decimal" -> evalDecArith("/", left, right) { a, b ->
        if (b.signum() == 0) throw Rt_Exception.common("expr:/:div0", "Division by zero: /")
        Lib_DecimalMath.divide(a, b)
    }

    "R_BinaryOp_Mod_Decimal" -> evalDecArith("%", left, right) { a, b ->
        if (b.signum() == 0) throw Rt_Exception.common("expr:%:div0", "Division by zero: %")
        Lib_DecimalMath.remainder(a, b)
    }

    // Concatenation / collection ops
    "R_BinaryOp_Concat_Text" -> Rt_TextValue.get((left as Rt_TextValue).value + (right as Rt_TextValue).value)
    "R_BinaryOp_Concat_ByteArray" -> Rt_ByteArrayValue.get((left as Rt_ByteArrayValue).value + (right as Rt_ByteArrayValue).value)
    "R_BinaryOp_Concat_List" -> evalConcatList(left, right)
    "R_BinaryOp_Union_Set" -> evalUnionSet(left, right)
    "R_BinaryOp_Sub_List" -> evalSubList(left, right)
    "R_BinaryOp_Sub_Set" -> evalSubSet(left, right)
    "R_BinaryOp_Intersect_List" -> evalIntersectList(left, right)
    "R_BinaryOp_Intersect_Set" -> evalIntersectSet(left, right)
    "R_BinaryOp_Merge_Map" -> evalMergeMap(left, right)

    // In / contains
    "R_BinaryOp_In_Collection" -> Rt_BooleanValue.get((right as Rt_CollectionValue).collection.contains(left))
    "R_BinaryOp_In_VirtualList" -> Rt_BooleanValue.get((right as Rt_VirtualListValue).contains((left as Rt_IntValue).value))
    "R_BinaryOp_In_VirtualSet" -> Rt_BooleanValue.get((right as Rt_VirtualSetValue).contains(left))
    "R_BinaryOp_In_Map" -> Rt_BooleanValue.get((right as Rt_MapBackedValue).mapView.containsKey(left))
    "R_BinaryOp_In_Range" -> Rt_BooleanValue.get((right as Rt_RangeValue).contains((left as Rt_IntValue).value))

    else -> {
        // Comparison operators: key = "Cmp_{CmpOpClass}_{CmpTypeClass}"
        check(!key.startsWith("Cmp_")) { "Comparison binary op must be dispatched via evaluateCmpBinaryOp: $key" }
        error("Unknown binary op key: $key")
    }
}

/**
 * Short-circuit evaluation for logical binary operators.
 * Returns a non-null value if the result is determined by the left operand alone.
 */
fun shortCircuitBinaryOp(key: String, left: Rt_Value): Rt_Value? = when (key) {
    "R_BinaryOp_And" -> if (!(left as Rt_BooleanValue).value) Rt_BooleanValue.get(false) else null
    "R_BinaryOp_Or" -> if ((left as Rt_BooleanValue).value) Rt_BooleanValue.get(true) else null
    else -> null
}

/**
 * Evaluate a comparison binary operator using the decomposed cmpOp/cmpType strings from [RR_CmpBinaryOp].
 */
fun evaluateCmpBinaryOp(cmpInfo: RR_CmpBinaryOp, left: Rt_Value, right: Rt_Value): Rt_Value {
    val cmp = compareByCmpType(cmpInfo.cmpType, left, right)
    val result = checkCmpOp(cmpInfo.cmpOp, cmp)
    return Rt_BooleanValue.get(result)
}

private fun compareByCmpType(cmpType: String, left: Rt_Value, right: Rt_Value): Int = when (cmpType) {
    "R_CmpType_Boolean" -> (left as Rt_BooleanValue).value.compareTo((right as Rt_BooleanValue).value)
    "R_CmpType_Integer" -> (left as Rt_IntValue).value.compareTo((right as Rt_IntValue).value)
    "R_CmpType_BigInteger" -> (left as Rt_BigIntegerValue).value.compareTo((right as Rt_BigIntegerValue).value)
    "R_CmpType_Decimal" -> (left as Rt_DecimalValue).value.compareTo((right as Rt_DecimalValue).value)
    "R_CmpType_Text" -> (left as Rt_TextValue).value.compareTo((right as Rt_TextValue).value)
    "R_CmpType_ByteArray" -> compareByteArrays((left as Rt_ByteArrayValue).value, (right as Rt_ByteArrayValue).value)
    "R_CmpType_Rowid" -> (left as Rt_RowidValue).value.compareTo((right as Rt_RowidValue).value)
    "R_CmpType_Entity" -> (left as Rt_EntityValue).rowid.compareTo((right as Rt_EntityValue).rowid)
    "R_CmpType_Enum" -> (left as Rt_RR_EnumValue).rrAttr.value.compareTo((right as Rt_RR_EnumValue).rrAttr.value)
    else -> error("Unknown cmp type: $cmpType")
}

private fun checkCmpOp(cmpOp: String, cmp: Int): Boolean = when (cmpOp) {
    "R_CmpOp_Lt" -> cmp < 0
    "R_CmpOp_Gt" -> cmp > 0
    "R_CmpOp_Le" -> cmp <= 0
    "R_CmpOp_Ge" -> cmp >= 0
    else -> error("Unknown cmp op: $cmpOp")
}

private fun compareByteArrays(l: ByteArray, r: ByteArray): Int {
    val n = min(l.size, r.size)
    var i = 0
    while (i < n) {
        val d = Integer.compareUnsigned(l[i].toInt(), r[i].toInt())
        if (d != 0) return d
        ++i
    }
    return l.size.compareTo(r.size)
}

// --- Integer arithmetic helpers ---

private inline fun evalIntArith(
    code: String,
    left: Rt_Value,
    right: Rt_Value,
    op: (Long, Long) -> Long,
): Rt_Value {
    val a = (left as Rt_IntValue).value
    val b = (right as Rt_IntValue).value
    val res = try {
        op(a, b)
    } catch (_: ArithmeticException) {
        throw Rt_Exception.common("expr:$code:overflow:$a:$b", "Integer overflow: $a $code $b")
    }
    return Rt_IntValue.get(res)
}

private inline fun evalBigIntArith(
    code: String,
    left: Rt_Value,
    right: Rt_Value,
    op: (java.math.BigInteger, java.math.BigInteger) -> java.math.BigInteger,
): Rt_Value {
    val a = (left as Rt_BigIntegerValue).value
    val b = (right as Rt_BigIntegerValue).value
    val res = op(a, b)
    return Rt_BigIntegerValue.getOrNull(res) ?: throw Rt_DecimalValue.errOverflow(
        "expr:$code:overflow",
        "Decimal overflow: operator '$code'",
    )
}

private inline fun evalDecArith(
    code: String,
    left: Rt_Value,
    right: Rt_Value,
    op: (java.math.BigDecimal, java.math.BigDecimal) -> java.math.BigDecimal,
): Rt_Value {
    val a = (left as Rt_DecimalValue).value
    val b = (right as Rt_DecimalValue).value
    val res = op(a, b)
    return Rt_DecimalValue.getOrNull(res) ?: throw Rt_DecimalValue.errOverflow(
        "expr:$code:overflow",
        "Decimal overflow: operator '$code'",
    )
}

// --- Collection operation helpers ---

fun evalConcatList(left: Rt_Value, right: Rt_Value): Rt_Value {
    val leftList = (left as Rt_ListValue).elements
    val rightCollection = (right as Rt_CollectionValue).collection
    val out = ArrayList<Rt_Value>(leftList.size + rightCollection.size)
    out += leftList
    out += rightCollection
    return Rt_ListValue(left.type, out)
}

fun evalUnionSet(left: Rt_Value, right: Rt_Value): Rt_Value {
    val out: MutableSet<Rt_Value> = (left as Rt_SetValue).elements.toMutableSet()
    out += (right as Rt_CollectionValue).collection
    return Rt_SetValue(left.type, out)
}

fun evalSubList(left: Rt_Value, right: Rt_Value): Rt_Value {
    val out = (left as Rt_ListValue).elements.toMutableList()
    out.removeAll((right as Rt_CollectionValue).collection.toSet())
    return Rt_ListValue(left.type, out)
}

fun evalSubSet(left: Rt_Value, right: Rt_Value): Rt_Value {
    val out: MutableSet<Rt_Value> = (left as Rt_SetValue).elements.toMutableSet()
    out.removeAll((right as Rt_SetValue).elements)
    return Rt_SetValue(left.type, out)
}

fun evalIntersectList(left: Rt_Value, right: Rt_Value): Rt_Value {
    val leftList = (left as Rt_ListValue).elements
    val out: MutableList<Rt_Value> = ArrayList(leftList.size)
    for (it in leftList.filter { (right as Rt_CollectionValue).collection.contains(it) }) {
        out += it
    }
    return Rt_ListValue(left.type, out)
}

fun evalIntersectSet(left: Rt_Value, right: Rt_Value): Rt_Value {
    val out: MutableSet<Rt_Value> = mutableSetOf()
    for (it in (left as Rt_SetValue).elements.filter { (right as Rt_CollectionValue).collection.contains(it) }) {
        out += it
    }
    return Rt_SetValue(left.type, out)
}

fun evalMergeMap(left: Rt_Value, right: Rt_Value): Rt_Value {
    require(left is Rt_MapValue)
    val out: MutableMap<Rt_Value, Rt_Value> = (left as Rt_MapBackedValue).mapView.toMutableMap()
    out.putAll((right as Rt_MapBackedValue).mapView)
    return Rt_MapValue(left.type, out)
}

// =============================================================================
// Unary operators — moved from R_UnaryOp sealed interface
// =============================================================================

/**
 * Standalone unary operator evaluation — moved from R_UnaryOp sealed interface hierarchy.
 *
 * Dispatches on the string key generated at resolve-time (the simple class name of the R_UnaryOp subtype).
 */
fun evaluateUnaryOp(key: String, operand: Rt_Value): Rt_Value = when (key) {
    "Minus_Integer" -> {
        val v = (operand as Rt_IntValue).value
        val res = try {
            Math.subtractExact(0, v)
        } catch (_: ArithmeticException) {
            throw Rt_Exception.common("expr:-:overflow:$v", "Integer overflow: -($v)")
        }
        Rt_IntValue.get(res)
    }

    "Minus_BigInteger" -> Rt_BigIntegerValue.get((operand as Rt_BigIntegerValue).value.negate())
    "Minus_Decimal" -> Rt_DecimalValue.get((operand as Rt_DecimalValue).value.negate())
    "Not" -> Rt_BooleanValue.get(!(operand as Rt_BooleanValue).value)
    else -> error("Unknown unary op key: $key")
}

/**
 * Apply a runtime type adapter to a value. Pure data transformation — no interpreter state required,
 * so it lives at the runtime-core level where any backend can call it directly without going through
 * the tree-walker.
 */
fun applyTypeAdapter(adapter: net.postchain.rell.base.model.rr.RR_TypeAdapter, value: Rt_Value): Rt_Value =
    when (adapter) {
        is net.postchain.rell.base.model.rr.RR_TypeAdapter.Direct -> value
        is net.postchain.rell.base.model.rr.RR_TypeAdapter.IntegerToBigInteger ->
            Rt_BigIntegerValue.get((value as Rt_IntValue).value.toBigInteger())
        is net.postchain.rell.base.model.rr.RR_TypeAdapter.IntegerToDecimal ->
            Rt_DecimalValue.get((value as Rt_IntValue).value.toBigDecimal())
        is net.postchain.rell.base.model.rr.RR_TypeAdapter.BigIntegerToDecimal ->
            Rt_DecimalValue.get((value as Rt_BigIntegerValue).value.toBigDecimal())
        is net.postchain.rell.base.model.rr.RR_TypeAdapter.Nullable ->
            if (value == Rt_NullValue) Rt_NullValue else applyTypeAdapter(adapter.inner, value)
    }

/** Convert an RR_ConstantValue to an Rt_Value. Returns null for complex types (struct, collection, etc.). */
fun rrConstantToRtValue(cv: RR_ConstantValue): Rt_Value? = when (cv) {
    is RR_ConstantValue.Null -> Rt_NullValue
    is RR_ConstantValue.Unit -> Rt_UnitValue
    is RR_ConstantValue.Bool -> Rt_BooleanValue.get(cv.value)
    is RR_ConstantValue.Int -> Rt_IntValue.get(cv.value)
    is RR_ConstantValue.Text -> Rt_TextValue.get(cv.value)
    is RR_ConstantValue.ByteArray -> Rt_ByteArrayValue.get(cv.value)
    is RR_ConstantValue.Decimal -> Rt_DecimalValue.get(cv.value.toBigDecimal())
    is RR_ConstantValue.BigInteger -> Rt_BigIntegerValue.get(cv.value.toBigInteger())
    is RR_ConstantValue.Rowid -> Rt_RowidValue.get(cv.value)
    else -> null
}
