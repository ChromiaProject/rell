/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.toImmList
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.PreparedStatement
import java.sql.ResultSet

object Lib_Type_Integer {
    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("timestamp", "integer")

        type("integer", rType = R_IntegerType) {
            comment("Represents a 64-bit signed integer")
            constant("MIN_VALUE", Long.MIN_VALUE) {
                comment("A constant representing the minimum value an integer can have (-9223372036854775808)")
            }
            constant("MAX_VALUE", Long.MAX_VALUE) {
                comment("A constant representing the maximum value an integer can have (9223372036854775807)")
            }

            constructor(pure = true) {
                comment("Parses a signed string representation of an integer.")
                param("value", "text", comment = "The string to be parsed.")
                param("radix", "integer", arity = L_ParamArity.ZERO_ONE) {
                    comment("The radix to be used in interpreting `value`. Defaults to 10 if not provided.")
                }
                bodyOpt1 { value, radix ->
                    val r = radix?.asInteger() ?: 10
                    calcFromText(value, r)
                }
            }

            constructor {
                comment("Converts a decimal to an integer, rounding towards 0.")
                param("value", "decimal", comment = "The decimal value to be converted to an integer.")
                bodyRaw(Lib_Type_Decimal.ToInteger)
            }

            staticFunction("from_text", "integer", pure = true) {
                comment("Parses a signed string representation of an integer.")
                param("value", "text", comment = "The string to be parsed.")
                param("radix", "integer", arity = L_ParamArity.ZERO_ONE) {
                    comment("The radix to be used in interpreting `value`. Defaults to 10 if not provided.")
                }
                bodyOpt1 { value, radix ->
                    val r = radix?.asInteger() ?: 10
                    calcFromText(value, r)
                }
            }

            staticFunction("from_hex", "integer", pure = true) {
                comment("Parses an unsigned hexadecimal representation of an integer.")
                alias("parseHex", C_MessageType.ERROR)
                param("value", "text", comment = "The hexadecimal string to be parsed.")
                body { value ->
                    val s = value.asString()
                    val r = try {
                        java.lang.Long.parseUnsignedLong(s, 16)
                    } catch (e: NumberFormatException) {
                        throw Rt_Exception.common("fn:integer.from_hex:$s", "Invalid hex number: '$s'")
                    }
                    Rt_IntValue.get(r)
                }
            }

            function("abs", "integer") {
                comment("Calculates the absolute value of the integer.")
                bodyRaw(Lib_Math.Abs_Integer)
            }

            function("min", "integer") {
                comment("Finds the minimum of this integer and the given value.")
                param("value", "integer", comment = "The integer value to compare.")
                bodyRaw(Lib_Math.Min_Integer)
            }

            function("min", "big_integer", pure = true) {
                comment("Finds the minimum of this integer and the given big integer value.")
                param("value", "big_integer", comment = "The big integer value to compare.")
                dbFunctionSimple("min", "LEAST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asBigInteger()
                    val r = BigInteger.valueOf(v1).min(v2)
                    Rt_BigIntegerValue.get(r)
                }
            }

            function("min", "decimal", pure = true) {
                comment("Finds the minimum of this integer and the given decimal value.")
                param("value", "decimal", comment = "The decimal value to compare.")
                dbFunctionSimple("min", "LEAST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asDecimal()
                    val r = BigDecimal(v1).min(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("max", "integer") {
                comment("Finds the maximum of this integer and the given value.")
                param("value", "integer", comment = "The integer value to compare.")
                bodyRaw(Lib_Math.Max_Integer)
            }

            function("max", "big_integer", pure = true) {
                comment("Finds the maximum of this integer and the given big integer value.")
                param("value", "big_integer", comment = "The big integer value to compare.")
                dbFunctionSimple("max", "GREATEST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asBigInteger()
                    val r = BigInteger.valueOf(v1).max(v2)
                    Rt_BigIntegerValue.get(r)
                }
            }

            function("max", "decimal", pure = true) {
                comment("Finds the maximum of this integer and the given decimal value.")
                param("value", "decimal", comment = "The decimal value to compare.")
                dbFunctionSimple("max", "GREATEST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asDecimal()
                    val r = BigDecimal(v1).max(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("pow", result = "integer", pure = true) {
                comment("""
                    Raises this integer to the power of the given exponent.
                    Can be used in a database at-expression.

                    1. The exponent cannot be negative.
                    2. Error on overflow, if the result is out of integer or integer range.
                    3. Beware that the result of integer.pow() is limited to the 64-bit signed integer range,
                    so the operation like (2).pow(64) will overflow - use big_integer.pow() to
                    get a big_integer result, e.g. (2).to_big_integer().pow(64).
                    4. If the exponent is 0, the result is always 1; if the exponent is 1,
                    the result is the original value.
                """)
                param(name = "exponent", type = "integer", comment = "The exponent.")
                dbFunctionSimple(fnSimpleName, SqlConstants.FN_INTEGER_POWER)
                body { self, exponent ->
                    val baseValue = self.asInteger()
                    val expValue = exponent.asInteger()
                    val resultValue = Lib_BigIntegerMath.genericPower(fnSimpleName, baseValue, expValue, Lib_BigIntegerMath.NumericType_Long)
                    Rt_IntValue.get(resultValue)
                }
            }

            function("sign", "integer", pure = true) {
                comment("Returns the sign of the integer: -1 if negative, 0 if zero, and 1 if positive.")
                alias("signum", C_MessageType.ERROR)
                dbFunctionSimple("sign", "SIGN")
                body { self ->
                    val intValue = self.asInteger()
                    val result = java.lang.Long.signum(intValue).toLong()
                    Rt_IntValue.get(result)
                }
            }

            function("to_big_integer", "big_integer") {
                comment("Converts this integer to a big integer.")
                bodyRaw(Lib_Type_BigInteger.FromInteger)
            }

            function("to_decimal", "decimal") {
                comment("Converts this integer to a decimal.")
                bodyRaw(Lib_Type_Decimal.FromInteger)
            }

            function("to_text", "text", pure = true) {
                comment("Converts this integer to a text string.")
                alias("str")
                dbFunctionCast("int.to_text", "TEXT")
                body { self ->
                    val intValue = self.asInteger()
                    Rt_TextValue.get(intValue.toString())
                }
            }

            function("to_text", "text", pure = true) {
                comment("Converts this integer to a text string with the specified radix.")
                alias("str")
                param("radix", "integer", comment = "The radix (base) to use for the string representation.")
                body { self, radix ->
                    val intValue = self.asInteger()
                    val radixValue = radix.asInteger()
                    if (radixValue < Character.MIN_RADIX || radixValue > Character.MAX_RADIX) {
                        throw Rt_Exception.common("fn_int_str_radix:$radixValue", "Invalid radix: $radixValue")
                    }
                    val stringValue = intValue.toString(radixValue.toInt())
                    Rt_TextValue.get(stringValue)
                }
            }

            function("to_hex", "text", pure = true) {
                alias("hex", C_MessageType.ERROR)
                comment("Converts this integer to a hexadecimal string.")
                body { self ->
                    val v = self.asInteger()
                    Rt_TextValue.get(java.lang.Long.toHexString(v))
                }
            }
        }
    }

    private fun calcFromText(a: Rt_Value, radix: Long): Rt_Value {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw Rt_Exception.common("fn:integer.from_text:radix:$radix", "Invalid radix: $radix")
        }

        val s = a.asString()
        val r = try {
            java.lang.Long.parseLong(s, radix.toInt())
        } catch (e: NumberFormatException) {
            throw Rt_Exception.common("fn:integer.from_text:$s", "Invalid number: '$s'")
        }

        return Rt_IntValue.get(r)
    }
}

object R_IntegerType: R_PrimitiveType("integer") {
    override fun defaultValue() = Rt_IntValue.ZERO

    override fun comparator() = Rt_Comparator.create { it.asInteger() }
    override fun fromCli(s: String): Rt_Value = Rt_IntValue.get(s.toLong())

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Integer
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Integer

    override fun getLibTypeDef() = Lib_Rell.INTEGER_TYPE

    private object R_TypeSqlAdapter_Integer: R_TypeSqlAdapter_Primitive("integer", SQLDataType.BIGINT) {
        override fun toSqlValue(value: Rt_Value) = value.asInteger()

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            stmt.setLong(idx, value.asInteger())
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getLong(idx)
            return checkSqlNull(v == 0L, rs, R_IntegerType, nullable) ?: Rt_IntValue.get(v)
        }
    }
}

class Rt_IntValue private constructor(val value: Long): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.INTEGER.type()

    override fun type() = R_IntegerType
    override fun asInteger() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "int[$value]"
    override fun str(format: StrFormat) = "" + value
    override fun equals(other: Any?) = other is Rt_IntValue && value == other.value
    override fun hashCode() = java.lang.Long.hashCode(value)

    companion object {
        private const val NVALUES = 1000

        private val VALUES: List<Rt_Value> = (-NVALUES .. NVALUES).map { Rt_IntValue(it.toLong()) }.toImmList()

        val ZERO: Rt_Value = get(0)

        fun get(v: Long): Rt_Value {
            return if (v >= -NVALUES && v <= NVALUES) {
                VALUES[(v + NVALUES).toInt()]
            } else {
                Rt_IntValue(v)
            }
        }
    }
}

private object GtvRtConversion_Integer: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asInteger())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToInteger(ctx, gtv, R_IntegerType)
        return ctx.rtValue {
            Rt_IntValue.get(v)
        }
    }
}
