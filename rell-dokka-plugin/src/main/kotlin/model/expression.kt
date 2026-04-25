/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.model

import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_ConstantDocSource
import net.postchain.rell.base.runtime.Rt_Value
import org.jetbrains.dokka.model.*
import java.math.BigDecimal
import java.math.BigInteger

fun L_ConstantDocSource.toExpression(): Expression = when (this) {
    L_ConstantDocSource.Null -> ComplexExpression("null")
    L_ConstantDocSource.Unit -> ComplexExpression("unit")
    is L_ConstantDocSource.Bool -> BooleanConstant(value)
    is L_ConstantDocSource.Int -> IntegerConstant(value)
    is L_ConstantDocSource.BigInt -> value.toExpression()
    is L_ConstantDocSource.Decimal -> value.toExpression()
    is L_ConstantDocSource.Text -> StringConstant(value)
    is L_ConstantDocSource.Bytes -> ComplexExpression(bytesToHex(value))
    is L_ConstantDocSource.Rowid -> ComplexExpression("rowid(${value})")
    is L_ConstantDocSource.Complex -> ComplexExpression(fallbackStr)
}

fun Rt_Value.toExpression(): Expression = when (this) {
    is Rt_BigIntegerValue -> value.toExpression()
    is Rt_IntValue -> IntegerConstant(value)
    is Rt_BooleanValue -> BooleanConstant(value)
    is Rt_DecimalValue -> value.toExpression()
    is Rt_TextValue -> StringConstant(value)
    else -> ComplexExpression(str(Rt_Value.StrFormat.V2))
}

private fun BigInteger.toExpression() =
        if (this > Long.MAX_VALUE.toBigInteger() || this < Long.MIN_VALUE.toBigInteger()) {
            ComplexExpression(String.format("%10.1g", toBigDecimal()))
        } else {
            IntegerConstant(longValueExact())
        }

private fun BigDecimal.toExpression() =
        if (this > Double.MAX_VALUE.toBigDecimal() || this < Double.MIN_VALUE.toBigDecimal()) {
            ComplexExpression(String.format("%10.1g", this))
        } else {
            DoubleConstant(toDouble())
        }

private fun bytesToHex(bytes: ByteArray): String {
    val buf = StringBuilder("x\"")
    for (b in bytes) buf.append("%02X".format(b))
    buf.append("\"")
    return buf.toString()
}
