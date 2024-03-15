package com.chromia.rell.dokka.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lib.type.Rt_TextValue
import net.postchain.rell.base.runtime.Rt_Value
import org.jetbrains.dokka.model.BooleanConstant
import org.jetbrains.dokka.model.ComplexExpression
import org.jetbrains.dokka.model.DoubleConstant
import org.jetbrains.dokka.model.Expression
import org.jetbrains.dokka.model.IntegerConstant
import org.jetbrains.dokka.model.StringConstant
import java.math.BigDecimal
import java.math.BigInteger

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