/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlConstants
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.sign

object Lib_Type_Integer {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("timestamp", "integer", since = SINCE0)

        type(Rt_IntValue, "integer", since = SINCE0) {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.INTEGER))

            """
                A 64-bit signed integer type, ranging from `-2^63` to `(2^63)-1`, supporting a standard
                complement of numerical operations.
            """.comment()

            constant("MIN_VALUE", Rt_IntValue.MIN_VALUE, since = SINCE0) {
                comment("The minimum value an integer can have, `-2^63`, or `-9223372036854775808`.")
            }
            constant("MAX_VALUE", Rt_IntValue.MAX_VALUE, since = SINCE0) {
                comment("The maximum value an integer can have, `(2^63)-1`, or `9223372036854775807`.")
            }

            constructor(pure = true, since = SINCE0) {
                """
                    Construct an integer object by parsing a signed text representation of an integer.

                    Base prefixes are not supported, so one must write e.g. `integer('CAFE', 16)` rather than `integer('0xCAFE')`.

                    Case is ignored, i.e. `integer('CAFE', 16)` and `integer('cafe', 16)` are equivalent.

                    Supported radixes are from ${Character.MIN_RADIX} to ${Character.MAX_RADIX} (inclusive).

                    @throws exception when:

                    - the textual representation is ill-formed
                    - `radix` is outside the supported range
                """.comment()

                val value by param(Rt_TextValue, comment = "the text to be parsed")
                val radix by paramOpt(
                    Rt_IntValue,
                    comment = "the radix to be used in parsing `value`; defaults to 10 if not provided",
                )

                body(Rt_IntValue) {
                    calcFromText(value, radix?.value ?: 10)
                }
            }

            constructor(since = "0.9.1") {
                """
                    Construct an integer from a decimal, with any fractional part truncated (i.e. rounding toward 0).
                """.comment()

                param("value", "decimal", comment = "the decimal value to convert to an integer")
                bodyRaw(Lib_Type_Decimal.ToInteger)
            }

            staticFunction("from_text", pure = true, since = "0.9.0") {
                """
                    Parse a signed text representation of an integer.
                    @throws exception if the text is ill-formed
                """.comment()

                val value by param(Rt_TextValue, comment = "the text to parse")
                val radix by paramOpt(
                    Rt_IntValue,
                    comment = "the radix in which to interpret `value`, defaults to 10",
                )

                body(Rt_IntValue) {
                    calcFromText(value, radix?.value ?: 10)
                }
            }

            staticFunction("from_hex", pure = true, since = "0.9.0") {
                """
                    Parse an unsigned hexadecimal text representation of an integer.

                    Base prefixes are not supported, so one must write e.g. `integer.from_hex('CAFE')` rather than
                    `integer.from_hex('0xCAFE')`.

                    Case is ignored, i.e. `integer.from_hex('CAFE')` and `integer.from_hex('cafe')` are equivalent.
                    @throws exception if the text representation is ill-formed
                """.comment()
                val value by param(Rt_TextValue, comment = "the hexadecimal text to be parsed")
                alias("parseHex", C_MessageType.ERROR, since = "0.6.0")
                body(Rt_IntValue) {
                    val s = value.value
                    try {
                        java.lang.Long.parseUnsignedLong(s, 16)
                    } catch (_: NumberFormatException) {
                        throw Rt_Exception.common("fn:integer.from_hex:$s", "Invalid hex number: '$s'")
                    }
                }
            }

            function("abs", "integer", since = SINCE0) {
                """
                    Returns the absolute value of this integer; i.e. the integer itself if it's positive or its negation
                    if it's negative.
                """.comment()

                bodyRaw(Lib_Math.Abs_Integer)
            }

            function("min", "integer", since = SINCE0) {
                """
                    Returns the lesser of this integer and another integer value; i.e. `value` if `value` is less than
                    this integer, or this integer otherwise.
                    @return the lesser of `value` and this integer.
                """.comment()

                param("value", "integer", comment = "the value to compare against this integer")
                bodyRaw(Lib_Math.Min_Integer)
            }

            function("min", pure = true, since = "0.12.0") {
                """
                    Returns the lesser of this integer and a `big_integer` value; i.e. `value` if `value` is less than
                    this integer, or this integer otherwise.
                    @return the lesser of `value` and this integer.
                """.comment()

                val self by self()
                val value by param(Rt_BigIntegerValue, comment = "the value to compare against this integer")
                dbFunctionSimple("min", "LEAST")
                body(Rt_BigIntegerValue) {
                    BigInteger.valueOf(self.value).min(value.value)
                }
            }

            function("min", pure = true, since = SINCE0) {
                """
                    Returns the lesser of this integer and a `decimal` value; i.e. `value` if `value` is less than this
                    integer, or this integer otherwise.
                    @return the lesser of `value` and this integer.
                """.comment()

                val self by self()
                val value by param(Rt_DecimalValue, comment = "the value to compare against this integer")

                dbFunctionSimple("min", "LEAST")

                body(Rt_DecimalValue) {
                    BigDecimal.valueOf(self.value).min(value.value)
                }
            }

            function("max", "integer", since = SINCE0) {
                """
                    Returns the greater of this integer and another integer value; i.e. `value` if `value` is greater
                    than this integer, or this integer otherwise.
                    @return the greater of `value` and this integer.
                """.comment()

                param("value", "integer", comment = "the value to compare against this integer")
                bodyRaw(Lib_Math.Max_Integer)
            }

            function("max", pure = true, since = "0.12.0") {
                """
                    Returns the greater of this integer and a `big_integer` value; i.e. `value` if `value` is greater
                    than this integer, or this integer otherwise.
                    @return the greater of `value` and this integer.
                """.comment()

                val self by self()
                val value by param(Rt_BigIntegerValue, comment = "the value to compare against this integer")

                dbFunctionSimple("max", "GREATEST")

                body(Rt_BigIntegerValue) {
                    BigInteger.valueOf(self.value).max(value.value)
                }
            }

            function("max", pure = true, since = SINCE0) {
                """
                    Returns the greater of this integer and a `decimal` value; i.e. `value` if `value` is greater than
                    this integer, or this integer otherwise.
                    @return the greater of `value` and this integer.
                """.comment()

                val self by self()
                val value by param(Rt_DecimalValue, comment = "the value to compare against this integer")

                dbFunctionSimple("max", "GREATEST")

                body(Rt_DecimalValue) {
                    BigDecimal.valueOf(self.value).max(value.value)
                }
            }

            function("pow", pure = true, since = "0.13.6") {
                """
                    Raise this integer to the power of the given exponent. SQL compatible.

                    Fails on integer overflow; so if results outside integer range are expected, first convert to
                    `big_integer` (e.g. with `integer.to_big_integer()` or `big_integer(integer)`) and then use
                    `big_integer.pow()`.

                    Note that:
                    - the exponent cannot be negative
                    - if the exponent is 0, the result is 1
                    - if the exponent is 1, the result is the original value
                    @throws exception if the result is out of integer range
                """.comment()

                val self by self()
                val exponent by param(Rt_IntValue, comment = "the exponent")

                dbFunctionSimple(fnSimpleName, SqlConstants.FN_INTEGER_POWER)

                body(Rt_IntValue) {
                    Lib_BigIntegerMath.genericPower(
                        fnName = fnSimpleName,
                        base = self.value,
                        exp = exponent.value,
                        type = Lib_BigIntegerMath.NumericType_Long,
                    )
                }
            }

            function("sign", pure = true, since = SINCE0) {
                """
                    Returns the sign of the integer: -1 if negative, 0 if zero, and 1 if positive.

                    It holds that for all `x`, `x == x.sign() * x.abs()`.
                """.comment()

                val self by self()
                alias("signum", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("sign", "SIGN")

                body(Rt_IntValue) {
                    self.value.sign.toLong()
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

            function("to_text", pure = true, since = "0.9.0") {
                comment("Convert this integer to a base-10 text representation.")
                val self by self()
                alias("str", since = SINCE0)
                dbFunctionCast("int.to_text", "TEXT")

                body(Rt_TextValue) {
                    self.value.toString()
                }
            }

            function("to_text", pure = true, since = "0.9.0") {
                """
                    Convert this integer to a text representation with the specified radix.

                    Does not include a base prefix in the output, i.e. `integer(25).to_text(16)` returns `19` rather
                    than `0x19`.

                    Supported radixes are from ${Character.MIN_RADIX} to ${Character.MAX_RADIX} (inclusive).
                    @throws exception if the radix is outside the supported range
                """.comment()

                val self by self()

                val radix by param(
                    Rt_IntValue,
                    comment = "The radix (base) to use for the text representation.",
                )

                alias("str", since = SINCE0)

                body(Rt_TextValue) {
                    if (radix.value < Character.MIN_RADIX || radix.value > Character.MAX_RADIX) {
                        throw Rt_Exception.common("fn_int_str_radix:${radix.value}", "Invalid radix: ${radix.value}")
                    }

                    self.value.toString(radix.value.toInt())
                }
            }

            function("to_hex", pure = true, since = SINCE0) {
                """
                    Convert this integer to hexadecimal text.

                    Does not include a base prefix in the output, i.e. `integer(25).to_hex()` returns `19` rather than
                    `0x19`.
                """.comment()

                val self by self()
                alias("hex", C_MessageType.ERROR, since = SINCE0)

                body(Rt_TextValue) {
                    java.lang.Long.toHexString(self.value)
                }
            }
        }
    }

    private fun calcFromText(a: Rt_TextValue, radix: Long): Long {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw Rt_Exception.common("fn:integer.from_text:radix:$radix", "Invalid radix: $radix")
        }

        val s = a.value
        return try {
            s.toLong(radix.toInt())
        } catch (_: NumberFormatException) {
            throw Rt_Exception.common("fn:integer.from_text:$s", "Invalid number: '$s'")
        }
    }
}
