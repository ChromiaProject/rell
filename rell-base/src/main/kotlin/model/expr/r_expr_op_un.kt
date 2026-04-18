/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import com.google.common.math.LongMath
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.model.R_ErrorPos
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value

sealed class R_UnaryOp {
    internal abstract fun evaluate(operand: Rt_Value): Rt_Value
}

internal data object R_UnaryOp_Minus_Integer: R_UnaryOp() {
    override fun evaluate(operand: Rt_Value): Rt_Value {
        val v = operand.asInteger()

        val res = try {
            LongMath.checkedSubtract(0, v)
        } catch (e: ArithmeticException) {
            throw Rt_Exception.common("expr:-:overflow:$v", "Integer overflow: -($v)")
        }

        return Rt_IntValue.get(res)
    }
}

internal data object R_UnaryOp_Minus_BigInteger: R_UnaryOp() {
    override fun evaluate(operand: Rt_Value): Rt_Value {
        val v = operand.asBigInteger()
        return Rt_BigIntegerValue.get(v.negate())
    }
}

internal data object R_UnaryOp_Minus_Decimal: R_UnaryOp() {
    override fun evaluate(operand: Rt_Value): Rt_Value {
        val v = operand.asDecimal()
        return Rt_DecimalValue.get(v.negate())
    }
}

internal data object R_UnaryOp_Not: R_UnaryOp() {
    override fun evaluate(operand: Rt_Value): Rt_Value {
        val v = operand.asBoolean()
        return Rt_BooleanValue.get(!v)
    }
}

internal class R_UnaryExpr(
    type: R_Type,
    private val op: R_UnaryOp,
    private val expr: R_Expr,
    private val errPos: R_ErrorPos,
): R_BaseExpr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val operand = expr.evaluate(frame)
        val resValue = try {
            op.evaluate(operand)
        } catch (e: Rt_Exception) {
            frame.error(errPos, e)
        }
        return resValue
    }
}
