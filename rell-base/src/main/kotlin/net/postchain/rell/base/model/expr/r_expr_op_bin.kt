/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import com.google.common.math.LongMath
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_EntityType
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.base.model.R_ErrorPos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import java.math.BigDecimal
import java.math.BigInteger

sealed class R_CmpOp(val code: String, val str: String, val checker: (Int) -> Boolean) {
    fun check(cmp: Int): Boolean = checker(cmp)
}

object R_CmpOp_Lt: R_CmpOp("<", "less than", { it < 0 })
object R_CmpOp_Gt: R_CmpOp(">", "greater than", { it > 0 })
object R_CmpOp_Le: R_CmpOp("<=", "less than or equal to", { it <= 0 })
object R_CmpOp_Ge: R_CmpOp(">=", "greater than or equal to", { it >= 0 })

sealed class R_CmpType {
    abstract fun compare(left: Rt_Value, right: Rt_Value): Int

    companion object {
        fun forCmpOpType(type: R_Type): R_CmpType? = if (type == R_BooleanType) null else forType(type)
        fun forAtMinMaxType(type: R_Type): R_CmpType? = forType(type)

        private fun forType(type: R_Type): R_CmpType? {
            return when (type) {
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
}

object R_CmpType_Boolean: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asBoolean()
        val r = right.asBoolean()
        return l.compareTo(r)
    }
}

object R_CmpType_Integer: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asInteger()
        val r = right.asInteger()
        return l.compareTo(r)
    }
}

object R_CmpType_BigInteger: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asBigInteger()
        val r = right.asBigInteger()
        return l.compareTo(r)
    }
}

object R_CmpType_Decimal: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asDecimal()
        val r = right.asDecimal()
        return l.compareTo(r)
    }
}

object R_CmpType_Text: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asString()
        val r = right.asString()
        return l.compareTo(r)
    }
}

object R_CmpType_ByteArray: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asByteArray()
        val r = right.asByteArray()

        val ln = l.size
        val rn = r.size
        val n = Math.min(ln, rn)

        var i = 0
        while (i < n) {
            val lb = l[i].toInt()
            val rb = r[i].toInt()
            val d = Integer.compareUnsigned(lb, rb)
            if (d != 0) {
                return d
            }
            ++i
        }

        return Integer.compare(ln, rn)
    }
}

object R_CmpType_Rowid: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asRowid()
        val r = right.asRowid()
        return l.compareTo(r)
    }
}

object R_CmpType_Entity: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asObjectId()
        val r = right.asObjectId()
        return l.compareTo(r)
    }
}

object R_CmpType_Enum: R_CmpType() {
    override fun compare(left: Rt_Value, right: Rt_Value): Int {
        val l = left.asEnum()
        val r = right.asEnum()
        return l.value.compareTo(r.value)
    }
}

internal sealed class R_BinaryOp(val code: String) {
    open fun evaluate(left: Rt_Value): Rt_Value? = null
    abstract fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value

    fun evaluate(frame: Rt_CallFrame, left: R_Expr, right: R_Expr): Rt_Value {
        val leftValue = left.evaluate(frame)
        val scValue = evaluate(leftValue)
        if (scValue != null) {
            return scValue
        }

        val rightValue = right.evaluate(frame)
        val resValue = evaluate(leftValue, rightValue)
        return resValue
    }
}

internal data object R_BinaryOp_Eq: R_BinaryOp("==") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value = Rt_BooleanValue.get(left == right)
}

internal data object R_BinaryOp_Ne: R_BinaryOp("!=") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value = Rt_BooleanValue.get(left != right)
}

internal data object R_BinaryOp_EqRef: R_BinaryOp("===") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value = Rt_BooleanValue.get(left === right)
}

internal data object R_BinaryOp_NeRef: R_BinaryOp("!==") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value = Rt_BooleanValue.get(left !== right)
}

internal class R_BinaryOp_Cmp(val cmpOp: R_CmpOp, val cmpType: R_CmpType): R_BinaryOp(cmpOp.code) {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val cmp = cmpType.compare(left, right)
        val res = cmpOp.check(cmp)
        return Rt_BooleanValue.get(res)
    }
}

internal sealed class R_BinaryOp_Logic(code: String): R_BinaryOp(code) {
    abstract fun evaluate(left: Boolean): Boolean?
    abstract fun evaluate(left: Boolean, right: Boolean): Boolean

    override fun evaluate(left: Rt_Value): Rt_Value? {
        val lb = left.asBoolean()
        val res = evaluate(lb)
        return if (res == null) null else Rt_BooleanValue.get(res)
    }

    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val lb = left.asBoolean()
        val rb = right.asBoolean()
        val res = evaluate(lb, rb)
        return Rt_BooleanValue.get(res)
    }
}

internal data object R_BinaryOp_And: R_BinaryOp_Logic("and") {
    override fun evaluate(left: Boolean): Boolean? {
        return if (!left) false else null
    }

    override fun evaluate(left: Boolean, right: Boolean): Boolean {
        return left && right
    }
}

internal data object R_BinaryOp_Or: R_BinaryOp_Logic("or") {
    override fun evaluate(left: Boolean): Boolean? {
        return if (left) true else null
    }

    override fun evaluate(left: Boolean, right: Boolean): Boolean {
        return left || right
    }
}

internal class R_BinaryExpr(
    type: R_Type,
    private val op: R_BinaryOp,
    private val left: R_Expr,
    private val right: R_Expr,
    private val errPos: R_ErrorPos?,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        return try {
            op.evaluate(frame, left, right)
        } catch (e: Rt_Exception) {
            errPos ?: throw e
            frame.error(errPos, e)
        }
    }
}

internal sealed class R_BinaryOp_Arith_Integer(code: String): R_BinaryOp(code) {
    abstract fun evaluate(left: Long, right: Long): Long

    final override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val leftVal = left.asInteger()
        val rightVal = right.asInteger()

        val resVal = try {
            evaluate(leftVal, rightVal)
        } catch (e: ArithmeticException) {
            throw errIntOverflow(code, leftVal, rightVal)
        }

        return Rt_IntValue.get(resVal)
    }
}

internal sealed class R_BinaryOp_Arith_BigInteger(code: String): R_BinaryOp(code) {
    abstract fun evaluate(left: BigInteger, right: BigInteger): BigInteger

    final override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val leftVal = left.asBigInteger()
        val rightVal = right.asBigInteger()
        val resVal = evaluate(leftVal, rightVal)
        return Rt_BigIntegerValue.getTry(resVal) ?: throw errDecOverflow(code)
    }
}

internal sealed class R_BinaryOp_Arith_Decimal(code: String): R_BinaryOp(code) {
    abstract fun evaluate(left: BigDecimal, right: BigDecimal): BigDecimal

    final override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val leftVal = left.asDecimal()
        val rightVal = right.asDecimal()
        val resVal = evaluate(leftVal, rightVal)
        return Rt_DecimalValue.getTry(resVal) ?: throw errDecOverflow(code)
    }
}

internal data object R_BinaryOp_Add_Integer: R_BinaryOp_Arith_Integer("+") {
    override fun evaluate(left: Long, right: Long) = LongMath.checkedAdd(left, right)
}

internal data object R_BinaryOp_Add_BigInteger: R_BinaryOp_Arith_BigInteger("+") {
    override fun evaluate(left: BigInteger, right: BigInteger) = Lib_BigIntegerMath.add(left, right)
}

internal data object R_BinaryOp_Add_Decimal: R_BinaryOp_Arith_Decimal("+") {
    override fun evaluate(left: BigDecimal, right: BigDecimal) = Lib_DecimalMath.add(left, right)
}

internal data object R_BinaryOp_Sub_Integer: R_BinaryOp_Arith_Integer("-") {
    override fun evaluate(left: Long, right: Long) = LongMath.checkedSubtract(left, right)
}

internal data object R_BinaryOp_Sub_BigInteger: R_BinaryOp_Arith_BigInteger("-") {
    override fun evaluate(left: BigInteger, right: BigInteger) = Lib_BigIntegerMath.subtract(left, right)
}

internal data object R_BinaryOp_Sub_Decimal: R_BinaryOp_Arith_Decimal("-") {
    override fun evaluate(left: BigDecimal, right: BigDecimal) = Lib_DecimalMath.subtract(left, right)
}

internal data object R_BinaryOp_Mul_Integer: R_BinaryOp_Arith_Integer("*") {
    override fun evaluate(left: Long, right: Long) = LongMath.checkedMultiply(left, right)
}

internal data object R_BinaryOp_Mul_BigInteger: R_BinaryOp_Arith_BigInteger("*") {
    override fun evaluate(left: BigInteger, right: BigInteger) = Lib_BigIntegerMath.multiply(left, right)
}

internal data object R_BinaryOp_Mul_Decimal: R_BinaryOp_Arith_Decimal("*") {
    override fun evaluate(left: BigDecimal, right: BigDecimal) = Lib_DecimalMath.multiply(left, right)
}

internal data object R_BinaryOp_Div_Integer: R_BinaryOp_Arith_Integer("/") {
    override fun evaluate(left: Long, right: Long): Long {
        if (right == 0L) {
            throw Rt_Exception.common("expr:/:div0:$left", "Division by zero: $left / $right")
        }
        return left / right
    }
}

internal data object R_BinaryOp_Div_BigInteger: R_BinaryOp_Arith_BigInteger("/") {
    override fun evaluate(left: BigInteger, right: BigInteger): BigInteger {
        if (right.signum() == 0) {
            throw Rt_Exception.common("expr:/:div0:$left", "Division by zero: $left / $right")
        }
        return Lib_BigIntegerMath.divide(left, right)
    }
}

internal data object R_BinaryOp_Div_Decimal: R_BinaryOp_Arith_Decimal("/") {
    override fun evaluate(left: BigDecimal, right: BigDecimal): BigDecimal {
        if (right.signum() == 0) {
            throw Rt_Exception.common("expr:/:div0", "Division by zero: /")
        }
        return Lib_DecimalMath.divide(left, right)
    }
}

internal data object R_BinaryOp_Mod_Integer: R_BinaryOp_Arith_Integer("%") {
    override fun evaluate(left: Long, right: Long): Long {
        if (right == 0L) {
            throw Rt_Exception.common("expr:%:div0:$left", "Division by zero: $left % $right")
        }
        return left % right
    }
}

internal data object R_BinaryOp_Mod_BigInteger: R_BinaryOp_Arith_BigInteger("%") {
    override fun evaluate(left: BigInteger, right: BigInteger): BigInteger {
        if (right.signum() == 0) {
            throw Rt_Exception.common("expr:%:div0", "Division by zero: %")
        }
        return Lib_BigIntegerMath.remainder(left, right)
    }
}

internal data object R_BinaryOp_Mod_Decimal: R_BinaryOp_Arith_Decimal("%") {
    override fun evaluate(left: BigDecimal, right: BigDecimal): BigDecimal {
        if (right.signum() == 0) {
            throw Rt_Exception.common("expr:%:div0", "Division by zero: %")
        }
        return Lib_DecimalMath.remainder(left, right)
    }
}

internal data object R_BinaryOp_Concat_Text: R_BinaryOp("+") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val leftVal = left.asString()
        val rightVal = right.asString()
        val resVal = leftVal + rightVal
        return Rt_TextValue.get(resVal)
    }
}

internal data object R_BinaryOp_Concat_ByteArray: R_BinaryOp("+") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val leftVal = left.asByteArray()
        val rightVal = right.asByteArray()
        val resVal = leftVal + rightVal
        return Rt_ByteArrayValue.get(resVal)
    }
}

internal data object R_BinaryOp_In_Collection: R_BinaryOp("in") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val c = right.asCollection()
        val r = c.contains(left)
        return Rt_BooleanValue.get(r)
    }
}

internal data object R_BinaryOp_In_VirtualList: R_BinaryOp("in") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val index = left.asInteger()
        val list = right.asVirtualList()
        val r = list.contains(index)
        return Rt_BooleanValue.get(r)
    }
}

internal data object R_BinaryOp_In_VirtualSet: R_BinaryOp("in") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val set = right.asVirtualSet()
        val r = set.contains(left)
        return Rt_BooleanValue.get(r)
    }
}

internal data object R_BinaryOp_In_Map: R_BinaryOp("in") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val c = right.asMap()
        val r = c.containsKey(left)
        return Rt_BooleanValue.get(r)
    }
}

internal data object R_BinaryOp_In_Range: R_BinaryOp("in") {
    override fun evaluate(left: Rt_Value, right: Rt_Value): Rt_Value {
        val x = left.asInteger()
        val c = right.asRange()
        val r = c.contains(x)
        return Rt_BooleanValue.get(r)
    }
}

private fun errIntOverflow(op: String, left: Long, right: Long): Rt_Exception {
    return Rt_Exception.common("expr:$op:overflow:$left:$right", "Integer overflow: $left $op $right")
}

private fun errDecOverflow(op: String): Rt_Exception {
    return Rt_DecimalValue.errOverflow("expr:$op:overflow", "Decimal overflow: operator '$op'")
}
