/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.GtvCompatibility
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.immSetOf
import net.postchain.rell.base.utils.mapToImmList
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.sign
import kotlin.reflect.full.createType

object Lib_Type_Integer {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("timestamp", "integer", since = SINCE0)

        type("integer", since = SINCE0) {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.INTEGER))
            comment("""
                A 64-bit signed integer type, ranging from `-2^63` to `(2^63)-1`, supporting a standard
                complement of numerical operations.
            """)

            constant("MIN_VALUE", Rt_IntValue.MIN_VALUE, since = SINCE0) {
                comment("The minimum value an integer can have, `-2^63`, or `-9223372036854775808`.")
            }
            constant("MAX_VALUE", Rt_IntValue.MAX_VALUE, since = SINCE0) {
                comment("The maximum value an integer can have, `(2^63)-1`, or `9223372036854775807`.")
            }

            constructor(pure = true, since = SINCE0) {
                comment("""
                    Construct an integer object by parsing a signed text representation of an integer.

                    Base prefixes are not supported, so one must write e.g. `integer('CAFE', 16)` rather than `integer('0xCAFE')`.

                    Case is ignored, i.e. `integer('CAFE', 16)` and `integer('cafe', 16)` are equivalent.

                    Supported radixes are from ${Character.MIN_RADIX} to ${Character.MAX_RADIX} (inclusive).

                    @throws exception when:

                    - the textual representation is ill-formed
                    - `radix` is outside the supported range
                """)
                param("value", "text", comment = "the text to be parsed")
                param("radix", "integer", arity = L_ParamArity.ZERO_ONE) {
                    comment("the radix to be used in parsing `value`; defaults to 10 if not provided")
                }
                bodyOpt1 { value, radix ->
                    val r = radix?.asInteger() ?: 10
                    calcFromText(value, r)
                }
            }

            constructor(since = "0.9.1") {
                comment("""
                    Construct an integer from a decimal, with any fractional part truncated (i.e. rounding toward 0).
                """)
                param("value", "decimal", comment = "the decimal value to convert to an integer")
                bodyRaw(Lib_Type_Decimal.ToInteger)
            }

            staticFunction("from_text", "integer", pure = true, since = "0.9.0") {
                comment("""
                    Parse a signed text representation of an integer.
                    @throws exception if the text is ill-formed
                """)
                param("value", "text", comment = "the text to parse")
                param("radix", "integer", arity = L_ParamArity.ZERO_ONE) {
                    comment("the radix in which to interpret `value`, defaults to 10")
                }
                bodyOpt1 { value, radix ->
                    val r = radix?.asInteger() ?: 10
                    calcFromText(value, r)
                }
            }

            staticFunction("from_hex", "integer", pure = true, since = "0.9.0") {
                comment("""
                    Parse an unsigned hexadecimal text representation of an integer.

                    Base prefixes are not supported, so one must write e.g. `integer.from_hex('CAFE')` rather than
                    `integer.from_hex('0xCAFE')`.

                    Case is ignored, i.e. `integer.from_hex('CAFE')` and `integer.from_hex('cafe')` are equivalent.
                    @throws exception if the text representation is ill-formed
                """)
                alias("parseHex", C_MessageType.ERROR, since = "0.6.0")
                param("value", "text", comment = "the hexadecimal text to be parsed")
                body { value ->
                    val s = value.asString()
                    val r = try {
                        java.lang.Long.parseUnsignedLong(s, 16)
                    } catch (_: NumberFormatException) {
                        throw Rt_Exception.common("fn:integer.from_hex:$s", "Invalid hex number: '$s'")
                    }
                    Rt_IntValue.get(r)
                }
            }

            function("abs", "integer", since = SINCE0) {
                comment("""
                    Returns the absolute value of this integer; i.e. the integer itself if it's positive or its negation
                    if it's negative.
                """)
                bodyRaw(Lib_Math.Abs_Integer)
            }

            function("min", "integer", since = SINCE0) {
                comment("""
                    Returns the lesser of this integer and another integer value; i.e. `value` if `value` is less than
                    this integer, or this integer otherwise.
                    @return the lesser of `value` and this integer.
                """)
                param("value", "integer", comment = "the value to compare against this integer")
                bodyRaw(Lib_Math.Min_Integer)
            }

            function("min", "big_integer", pure = true, since = "0.12.0") {
                comment("""
                    Returns the lesser of this integer and a `big_integer` value; i.e. `value` if `value` is less than
                    this integer, or this integer otherwise.
                    @return the lesser of `value` and this integer.
                """)
                param("value", "big_integer", comment = "the value to compare against this integer")
                dbFunctionSimple("min", "LEAST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asBigInteger()
                    val r = BigInteger.valueOf(v1).min(v2)
                    Rt_BigIntegerValue.get(r)
                }
            }

            function("min", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Returns the lesser of this integer and a `decimal` value; i.e. `value` if `value` is less than this
                    integer, or this integer otherwise.
                    @return the lesser of `value` and this integer.
                """)
                param("value", "decimal", comment = "the value to compare against this integer")
                dbFunctionSimple("min", "LEAST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asDecimal()
                    val r = BigDecimal(v1).min(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("max", "integer", since = SINCE0) {
                comment("""
                    Returns the greater of this integer and another integer value; i.e. `value` if `value` is greater
                    than this integer, or this integer otherwise.
                    @return the greater of `value` and this integer.
                """)
                param("value", "integer", comment = "the value to compare against this integer")
                bodyRaw(Lib_Math.Max_Integer)
            }

            function("max", "big_integer", pure = true, since = "0.12.0") {
                comment("""
                    Returns the greater of this integer and a `big_integer` value; i.e. `value` if `value` is greater
                    than this integer, or this integer otherwise.
                    @return the greater of `value` and this integer.
                """)
                param("value", "big_integer", comment = "the value to compare against this integer")
                dbFunctionSimple("max", "GREATEST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asBigInteger()
                    val r = BigInteger.valueOf(v1).max(v2)
                    Rt_BigIntegerValue.get(r)
                }
            }

            function("max", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Returns the greater of this integer and a `decimal` value; i.e. `value` if `value` is greater than
                    this integer, or this integer otherwise.
                    @return the greater of `value` and this integer.
                """)
                param("value", "decimal", comment = "the value to compare against this integer")
                dbFunctionSimple("max", "GREATEST")
                body { self, value ->
                    val v1 = self.asInteger()
                    val v2 = value.asDecimal()
                    val r = BigDecimal(v1).max(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("pow", result = "integer", pure = true, since = "0.13.6") {
                comment("""
                    Raise this integer to the power of the given exponent. SQL compatible.

                    Fails on integer overflow; so if results outside integer range are expected, first convert to
                    `big_integer` (e.g. with `integer.to_big_integer()` or `big_integer(integer)`) and then use
                    `big_integer.pow()`.

                    Note that:
                    - the exponent cannot be negative
                    - if the exponent is 0, the result is 1
                    - if the exponent is 1, the result is the original value
                    @throws exception if the result is out of integer range
                """)
                param(name = "exponent", type = "integer", comment = "the exponent")
                dbFunctionSimple(fnSimpleName, SqlConstants.FN_INTEGER_POWER)
                body { self, exponent ->
                    val baseValue = self.asInteger()
                    val expValue = exponent.asInteger()
                    val resultValue = Lib_BigIntegerMath.genericPower(fnSimpleName, baseValue, expValue, Lib_BigIntegerMath.NumericType_Long)
                    Rt_IntValue.get(resultValue)
                }
            }

            function("sign", "integer", pure = true, since = SINCE0) {
                comment("""
                    Returns the sign of the integer: -1 if negative, 0 if zero, and 1 if positive.

                    It holds that for all `x`, `x == x.sign() * x.abs()`.
                """)
                alias("signum", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("sign", "SIGN")
                body { self ->
                    val intValue = self.asInteger()
                    val result = intValue.sign.toLong()
                    Rt_IntValue.get(result)
                }
            }

            function("to_big_integer", "big_integer", since = "0.12.0") {
                comment("Convert this integer to a big integer.")
                bodyRaw(Lib_Type_BigInteger.FromInteger)
            }

            function("to_decimal", "decimal", since = "0.9.1") {
                comment("Convert this integer to a decimal.")
                bodyRaw(Lib_Type_Decimal.FromInteger)
            }

            function("to_text", "text", pure = true, since = "0.9.0") {
                comment("Convert this integer to a base-10 text representation.")
                alias("str", since = SINCE0)
                dbFunctionCast("int.to_text", "TEXT")
                body { self ->
                    val intValue = self.asInteger()
                    Rt_TextValue.get(intValue.toString())
                }
            }

            function("to_text", "text", pure = true, since = "0.9.0") {
                comment("""
                    Convert this integer to a text representation with the specified radix.

                    Does not include a base prefix in the output, i.e. `integer(25).to_text(16)` returns `19` rather
                    than `0x19`.

                    Supported radixes are from ${Character.MIN_RADIX} to ${Character.MAX_RADIX} (inclusive).
                    @throws exception if the radix is outside the supported range
                """)
                alias("str", since = SINCE0)
                param("radix", "integer", comment = "The radix (base) to use for the text representation.")
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

            function("to_hex", "text", pure = true, since = SINCE0) {
                alias("hex", C_MessageType.ERROR, since = SINCE0)
                comment("""
                    Convert this integer to hexadecimal text.

                    Does not include a base prefix in the output, i.e. `integer(25).to_hex()` returns `19` rather than
                    `0x19`.
                """)
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
            s.toLong(radix.toInt())
        } catch (_: NumberFormatException) {
            throw Rt_Exception.common("fn:integer.from_text:$s", "Invalid number: '$s'")
        }

        return Rt_IntValue.get(r)
    }
}

object Rt_NativeConversion_Integer: Rt_TypeNativeConversion {
    override val nativeTypes = immSetOf(Long::class.createType())
    override fun rtToNative(value: Rt_Value) = value.asInteger()
    override fun nativeToRt(value: Any?) = Rt_IntValue.get(value as Long)
}

object R_TypeSqlAdapter_Integer: R_TypeSqlAdapter_Primitive("integer", SQLDataType.BIGINT) {
    override fun toSqlValue(value: Rt_Value) = value.asInteger()

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        params.setLong(idx, value.asInteger())
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getLong(idx)
        return checkSqlNull(v == 0L, row, name, nullable) ?: Rt_IntValue.get(v)
    }
}

class Rt_IntValue private constructor(val value: Long): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.INTEGER.type()

    override fun type() = Rt_PrimitiveTypes.INTEGER
    override fun asInteger() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "int[$value]"
    override fun str(format: StrFormat) = "" + value
    override fun equals(other: Any?) = other is Rt_IntValue && value == other.value
    override fun hashCode() = value.hashCode()

    companion object {
        const val MAX_VALUE = Long.MAX_VALUE
        const val MIN_VALUE = Long.MIN_VALUE
        val MAX_VALUE_AS_BIGINT: BigInteger = BigInteger.valueOf(MAX_VALUE)
        val MIN_VALUE_AS_BIGINT: BigInteger = BigInteger.valueOf(MIN_VALUE)
        private const val NVALUES = 1000

        private val VALUES = (-NVALUES .. NVALUES).mapToImmList { Rt_IntValue(it.toLong()) }

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

object GtvRtConversion_Integer: GtvRtConversion {
    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asInteger())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToInteger(ctx, gtv, "integer")
        return Rt_IntValue.get(v)
    }
}
