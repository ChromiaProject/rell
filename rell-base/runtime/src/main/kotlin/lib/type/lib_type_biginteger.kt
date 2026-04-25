/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.google.common.math.LongMath
import mu.KLogging
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.GtvCompatibility
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immSetOf
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.reflect.full.createType

object Lib_Type_BigInteger {
    val FromInteger_Db = Db_SysFunction.cast("big_integer(integer)", Lib_BigIntegerMath.SQL_TYPE_STR)

    val FromInteger = C_SysFunctionBody.simple(FromInteger_Db, pure = true) { a ->
        calcFromInteger(a)
    }

    private val FromText_1 = C_SysFunctionBody.simple(
        Db_SysFunction.simple("big_integer(text)", SqlConstants.FN_BIGINTEGER_FROM_TEXT),
        pure = true
    ) { a ->
        val s = a.asString()
        Rt_BigIntegerValue.get(s)
    }

    private const val SINCE0 = "0.12.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("big_integer", since = SINCE0) {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER))
            comment("""
                An immutable signed integer type, supporting extremely large values (upwards of 100,000 decimal digits).

                Literals of `big_integer` type can be written like integers, but with the suffix `L`, e.g. `123L` or
                `0x123L`. `big_integer`s support the operators `+`, `-`, `*`, `/` and `%` with typical behavior.
            """)

            constant("PRECISION", Lib_BigIntegerMath.PRECISION.toLong(), since = SINCE0) {
                comment("The maximum number of digits a `big_integer` can have, `131072`, or `2^17`.")
            }

            constant("MIN_VALUE", Lib_BigIntegerMath.MIN_VALUE, since = SINCE0) {
                comment("The minimum value a `big_integer` can have, `-(10^131072)+1`.")
            }

            constant("MAX_VALUE", Lib_BigIntegerMath.MAX_VALUE, since = SINCE0) {
                comment("The maximum value a `big_integer` can have, `(10^131072)-1`.")
            }

            constructor(since = SINCE0) {
                comment("""
                    Construct a `big_integer` by parsing a signed base-10 text representation of an integer.
                    @throws exception if the text representation is ill-formed
                """)
                param("s", type = "text", comment = "the text to be parsed")
                bodyRaw(FromText_1)
            }

            constructor(since = SINCE0) {
                comment("Construct a big_integer from an integer.")
                param("value", type = "integer", comment = "the integer value")
                bodyRaw(FromInteger)
            }

            staticFunction("from_bytes", result = "big_integer", pure = true, since = SINCE0) {
                comment("""
                    Create a `big_integer` from a byte array.

                    The byte array is interpreted with the first bit representing the sign (two's complement), and for
                    subsequent bits, more significant bits are on the left and less significant bits are on the left
                    (big-endian).

                    Inverse of `big_integer.to_bytes()`.

                    @throws exception if the byte array is empty
                """)
                param("value", type = "byte_array", comment = "the byte array to convert")
                body { a ->
                    val bytes = a.asByteArray()
                    val bigInt = BigInteger(bytes)
                    Rt_BigIntegerValue.get(bigInt)
                }
            }

            staticFunction("from_bytes_unsigned", result = "big_integer", pure = true, since = SINCE0) {
                comment("""
                    Create a `big_integer` from a byte array.

                    The byte array is interpreted with more significant bits on the left and less significant bits on
                    the right (big-endian). An empty byte array is interpreted as `0`.

                    Inverse of `big_integer.to_bytes_unsigned()`.
                """)
                param("value", type = "byte_array", comment = "the byte array to convert")
                body { a ->
                    val bytes = a.asByteArray()
                    val bigInt = BigInteger(1, bytes)
                    Rt_BigIntegerValue.get(bigInt)
                }
            }

            staticFunction("from_text", result = "big_integer", pure = true, since = SINCE0) {
                comment("""
                    Parse a signed base-10 text representation of an integer.
                    @throws exception if the text is ill-formed
                """)
                param("value", type = "text", comment = "the text to parse")
                bodyRaw(FromText_1)
            }

            staticFunction("from_text", result = "big_integer", pure = true, since = SINCE0) {
                comment("""
                    Parse a signed text representation of an integer. The integer is interpreted in the specified radix
                    (from ${Character.MIN_RADIX} to ${Character.MAX_RADIX} inclusive).
                    @throws exception when
                    - the text is ill-formed
                    - the radix is outside the supported range
                """)
                param("value", type = "text", comment = "the text to parse")
                param("radix", type = "integer") {
                    comment("the radix with which to interpret `value`")
                }
                body { a, b ->
                    val s = a.asString()
                    val r = b.asInteger()
                    if (r < Character.MIN_RADIX || r > Character.MAX_RADIX) {
                        throw Rt_Exception.common("fn:big_integer.from_text:radix:$r", "Invalid radix: $r")
                    }
                    calcFromText(s, r.toInt(), "from_text")
                }
            }

            staticFunction("from_hex", result = "big_integer", pure = true, since = SINCE0) {
                comment("""
                    Parses an unsigned hexadecimal text representation of a `big_integer`.

                    Base prefixes are not supported, so one must write e.g. `integer.from_hex('CAFE')` rather than
                    `integer.from_hex('0xCAFE')`.

                    Case is ignored, i.e. `integer.from_hex('CAFE')` and `integer.from_hex('cafe')` are equivalent.
                    @throws exception if the text representation is ill-formed
                """)
                param("value", type = "text", comment = "the hexadecimal text to be parsed")
                body { a ->
                    val s = a.asString()
                    calcFromText(s, 16, "from_hex")
                }
            }

            function("abs", "big_integer", since = SINCE0) {
                comment("""
                    Returns the absolute value of this `big_integer`; i.e. the `big_integer` itself if it's positive
                    or its negation if it's negative.
                """)
                bodyRaw(Lib_Math.Abs_BigInteger)
            }

            function("min", "big_integer", since = SINCE0) {
                comment("""
                    Returns the lesser of this and another `big_integer` value; i.e. `value` if `value` is less than
                    this, or this `big_integer` otherwise.
                    @return the lesser of `value` and this `big_integer`
                """)
                param("value", "big_integer", comment = "the value to compare against")
                bodyRaw(Lib_Math.Min_BigInteger)
            }

            function("min", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Returns the numerically lesser of this `big_integer` a `decimal` value; i.e. `value` if `value` is
                    less than this `big_integer`, or this `big_integer` otherwise.
                    @return the lesser of `value` and this `big_integer`
                """)
                param("value", "decimal", comment = "the decimal value to compare against")
                dbFunctionSimple("big_integer.min", "LEAST")
                body { a, b ->
                    val v1 = a.asBigInteger()
                    val v2 = b.asDecimal()
                    val r = v1.toBigDecimal().min(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("max", "big_integer", since = SINCE0) {
                comment("""
                    Returns the greater of this and another `big_integer` value; i.e. `value` if `value` is greater than
                    this, or this `big_integer` otherwise.
                    @return the greater of `value` and this `big_integer`
                """)
                param("value", "big_integer", comment = "the value to compare against")
                bodyRaw(Lib_Math.Max_BigInteger)
            }

            function("max", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Returns the numerically greater of this `big_integer` a `decimal` value; i.e. `value` if `value` is
                    greater than this `big_integer`, or this `big_integer` otherwise.
                    @return the greater of `value` and this `big_integer`
                """)
                param("value", "decimal", comment = "the decimal value to compare against")
                dbFunctionSimple("big_integer.max", "GREATEST")
                body { a, b ->
                    val v1 = a.asBigInteger()
                    val v2 = b.asDecimal()
                    val r = v1.toBigDecimal().max(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("pow", result = "big_integer", pure = true, since = "0.13.6") {
                comment("""
                    Raise this `big_integer` to the power of the given exponent. SQL compatible.

                    Note that:
                    - the exponent cannot be negative
                    - if the exponent is 0, the result is 1
                    - if the exponent is 1, the result is the original value
                    @throws exception on overflow, i.e. if the result is out of `big_integer` range
                """)
                param(name = "exponent", type = "integer", comment = "the exponent")
                dbFunctionSimple(fnSimpleName, SqlConstants.FN_BIGINTEGER_POWER)
                body { a, b ->
                    val base = a.asBigInteger()
                    val exp = b.asInteger()

                    val res = Lib_BigIntegerMath.genericPower(
                        fnSimpleName,
                        base,
                        exp,
                        Lib_BigIntegerMath.NumericType_BigInteger
                    )

                    Rt_BigIntegerValue.get(res)
                }
            }

            // Function: sign
            function("sign", "integer", pure = true, since = SINCE0) {
                comment("""
                    Returns the sign of this `big_integer`: `-1` if negative, `0` if zero, and `1` if positive.

                    It holds that for all `x`, `x == x.sign() * x.abs()`.
                """)
                dbFunctionSimple("big_integer.sign", "SIGN")
                body { a ->
                    val v = a.asBigInteger()
                    val r = v.signum()
                    Rt_IntValue.get(r.toLong())
                }
            }

            // Function: to_bytes
            function("to_bytes", "byte_array", pure = true, since = SINCE0) {
                comment("""
                    Convert this `big_integer` to a byte array.

                    The first bit of the generated byte array represents the sign (two's complement), and for subsequent
                    bits, more significant bits are on the left and less significant bits are on the left (big-endian).

                    Inverse of `big_integer.from_bytes()`.

                    Examples:
                    - `0L.to_bytes()` returns `x'00'`
                    - `(-1L).to_bytes()` returns `x'FF'`
                    - `1L.to_bytes()` returns `x'01'`
                    - `2L.pow(100).to_bytes()` returns `x'10000000000000000000000000'`
                """)
                body { a ->
                    val bigInt = a.asBigInteger()
                    val bytes = bigInt.toByteArray()
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("to_bytes_unsigned", "byte_array", pure = true, since = SINCE0) {
                comment("""
                    Convert this non-negative `big_integer` to a byte array, with no sign bit.

                    The generated byte array has more significant bits on the left and less significant bits on the
                    right (big-endian).

                    Inverse of `big_integer.from_bytes_unsigned()`.

                    When this `big_integer` is equal to `0`, the empty byte array `x''` is returned (this is consistent
                    with the inverse function `big_integer.from_bytes_unsigned()`, which interprets `x''` as `0`).

                    Examples:
                    - `0L.to_bytes_unsigned()` returns `x''`
                    - `(-1L).to_bytes_unsigned()` throws an exception
                    - `1L.to_bytes_unsigned()` returns `x'01'`
                    - `2L.pow(100).to_bytes_unsigned()` returns `x'10000000000000000000000000'`
                    @throws exception if this `big_integer` is negative
                """)
                body { a ->
                    val bigInt = a.asBigInteger()
                    Rt_Utils.check(bigInt.signum() >= 0) {
                        "fn:big_integer.to_bytes_unsigned:negative" to "Value is negative"
                    }
                    var bytes = bigInt.toByteArray()
                    val n = (bigInt.bitLength() + 7) / 8
                    if (n != bytes.size) {
                        checkEquals(n, bytes.size - 1)
                        bytes = bytes.copyOfRange(1, bytes.size)
                    }
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            // Function: to_decimal
            function("to_decimal", "decimal", since = SINCE0) {
                comment("Convert this `big_integer` to a decimal.")
                bodyRaw(Lib_Type_Decimal.FromBigInteger)
            }

            // Function: to_hex
            function("to_hex", "text", pure = true, since = SINCE0) {
                comment("""
                    Convert this `big_integer` to hexadecimal text.

                    Does not include a base prefix in the output, i.e. `big_integer(25).to_hex()` returns `19` rather
                    than `0x19`.
                """)
                body { a ->
                    val v = a.asBigInteger()
                    val s = v.toString(16)
                    Rt_TextValue.get(s)
                }
            }

            // Function: to_integer
            function("to_integer", "integer", pure = true, since = SINCE0) {
                comment("""
                    Convert this big_integer to an integer.
                    @throws exception if this `big_integer` is outside `integer` range
                """)
                dbFunctionTemplate("big_integer.to_integer", 1, "(#0)::BIGINT")
                body { a ->
                    val v = a.asBigInteger()
                    if (v < Rt_IntValue.MIN_VALUE_AS_BIGINT || v > Rt_IntValue.MAX_VALUE_AS_BIGINT) {
                        val s = v.toBigDecimal().round(MathContext(20, RoundingMode.DOWN))
                        throw Rt_Exception.common("big_integer.to_integer:overflow:$s", "Value out of range: $s")
                    }
                    val r = v.toLong()
                    Rt_IntValue.get(r)
                }
            }

            // Function: to_text
            function("to_text", "text", pure = true, since = SINCE0) {
                comment("Convert this `big_integer` to a base 10 text representation.")
                dbFunctionTemplate("decimal.to_text", 1, "(#0)::TEXT")
                body { a ->
                    val v = a.asBigInteger()
                    val r = v.toString()
                    Rt_TextValue.get(r)
                }
            }

            // Function: to_text with radix
            function("to_text", "text", pure = true, since = SINCE0) {
                comment("""
                    Convert this `big_integer` to a text representation with the specified radix.

                    Does not include a base prefix in the output, i.e. `integer(25).to_text(16)` returns `19` rather
                    than `0x19`.

                    Supported radixes are from ${Character.MIN_RADIX} to ${Character.MAX_RADIX} (inclusive).
                    @throws exception if the radix is outside the supported range
                """)
                param("radix", "integer") {
                    comment("the radix (base) to use for the text representation")
                }
                body { a, b ->
                    val v = a.asBigInteger()
                    val r = b.asInteger()
                    if (r < Character.MIN_RADIX || r > Character.MAX_RADIX) {
                        throw Rt_Exception.common("fn:big_integer.to_text:radix:$r", "Invalid radix: $r")
                    }
                    val s = v.toString(r.toInt())
                    Rt_TextValue.get(s)
                }
            }
        }
    }

    private fun calcFromText(s: String, radix: Int, fnName: String): Rt_Value {
        val r = try {
            BigInteger(s, radix)
        } catch (e: NumberFormatException) {
            throw Rt_Exception.common("fn:big_integer.$fnName:$s", "Invalid number: '$s'")
        }
        return Rt_BigIntegerValue.get(r)
    }

    fun calcFromInteger(a: Rt_Value): Rt_Value {
        val i = a.asInteger()
        return Rt_BigIntegerValue.get(i)
    }
}

object Lib_BigIntegerMath {
    const val PRECISION = 131072

    val MAX_VALUE: BigInteger = BigInteger.TEN.pow(PRECISION).subtract(BigInteger.ONE)
    val MIN_VALUE: BigInteger = -MAX_VALUE

    val SQL_TYPE: DataType<*> = SQLDataType.DECIMAL

    const val SQL_TYPE_STR = "NUMERIC"

    fun add(a: BigInteger, b: BigInteger): BigInteger {
        return a.add(b)
    }

    fun subtract(a: BigInteger, b: BigInteger): BigInteger {
        return a.subtract(b)
    }

    fun multiply(a: BigInteger, b: BigInteger): BigInteger {
        return a.multiply(b)
    }

    fun divide(a: BigInteger, b: BigInteger): BigInteger {
        val r = a.divide(b)
        return r
    }

    fun remainder(a: BigInteger, b: BigInteger): BigInteger {
        return a.remainder(b)
    }

    fun <T> genericPower(fnName: String, base: T, exp: Long, type: NumericType<T>): T {
        Rt_Utils.check(exp >= 0) {
            "$fnName:exp_negative:$exp" to "Negative exponent: $exp"
        }

        val res = when {
            exp == 0L -> type.one
            exp == 1L -> base
            base == type.zero -> type.zero
            base == type.one -> type.one
            base == type.minusOne -> if ((exp and 1L) == 0L) type.one else type.minusOne
            else -> {
                try {
                    val exp0 = Math.toIntExact(exp)
                    type.pow(base, exp0)
                } catch (e: ArithmeticException) {
                    val baseStr = type.errStr(base)
                    val msg = "Power overflow: $baseStr ^ $exp"
                    throw Rt_Exception.common("$fnName:overflow:$baseStr:$exp", msg)
                }
            }
        }

        return res
    }

    abstract class NumericType<T>(val zero: T, val one: T, val minusOne: T) {
        abstract fun pow(base: T, exp: Int): T
        abstract fun errStr(value: T): String
    }

    object NumericType_Long: NumericType<Long>(zero = 0, one = 1, minusOne = -1) {
        override fun pow(base: Long, exp: Int): Long {
            return LongMath.checkedPow(base, exp)
        }

        override fun errStr(value: Long): String {
            return value.toString()
        }
    }

    object NumericType_BigInteger: NumericType<BigInteger>(
        zero = BigInteger.ZERO,
        one = BigInteger.ONE,
        minusOne = BigInteger.ONE.negate(),
    ) {
        override fun pow(base: BigInteger, exp: Int): BigInteger {
            // Check overflow by examining the bit length. Without this check, some combinations of base and exp
            // will produce a very big number by performing heavy and slow computations - checking the overflow after
            // the computation is too inefficient in such cases (example: 1E+1000 ^ 250000 = 1E+250000000).
            val baseExp = (base.abs().bitLength() - 1).coerceAtLeast(0)
            val resExp = LongMath.checkedMultiply(baseExp.toLong(), exp.toLong())
            if (resExp + 1 > MAX_VALUE.bitLength()) {
                throw ArithmeticException("Big integer power result out of range")
            }

            val res = base.pow(exp)
            if (res !in MIN_VALUE..MAX_VALUE) {
                throw ArithmeticException("Big integer power result out of range")
            }

            return res
        }

        override fun errStr(value: BigInteger): String {
            val s = value.abs().toString()
            val s0 = if (s.length <= 100) s else {
                val head = s.substring(0, 1)
                val tail = s.substring(1, 16)
                val exp = s.length - 1
                "$head.$tail(...)E+$exp"
            }
            return if (value.signum() >= 0) s0 else "-$s0"
        }
    }
}

object Rt_NativeConversion_BigInteger: Rt_TypeNativeConversion {
    override val nativeTypes = immSetOf(BigInteger::class.createType())
    override fun rtToNative(value: Rt_Value) = value.asBigInteger()
    override fun nativeToRt(value: Any?) = Rt_BigIntegerValue.get(value as BigInteger)
}

object Rt_ValueSqlAdapter_BigInteger: Rt_ValueSqlAdapter_Primitive("big_integer", Lib_BigIntegerMath.SQL_TYPE) {
    override fun toSqlValue(value: Rt_Value) = value.asBigInteger()

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
        val v = value.asBigInteger()
        params.setBigDecimal(idx, BigDecimal(v))
    }

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
        val v = row.getBigDecimal(idx)
        return if (v != null) Rt_BigIntegerValue.get(v) else checkSqlNull(name, nullable)
    }
}

class Rt_BigIntegerValue private constructor(val value: BigInteger): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.BIG_INTEGER.type()

    override fun type() = Rt_PrimitiveTypes.BIG_INTEGER
    override fun asBigInteger() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "bigint[${str()}]"
    override fun str(format: StrFormat): String = value.toString()
    override fun equals(other: Any?) = other === this || (other is Rt_BigIntegerValue && value == other.value)
    override fun hashCode() = value.hashCode()

    companion object : KLogging() {
        val ZERO = Rt_BigIntegerValue(BigInteger.ZERO)

        fun get(v: BigInteger): Rt_Value {
            if (v.signum() == 0) {
                return ZERO
            }

            val res = getTry(v)
            if (res != null) {
                return res
            }

            val p = Lib_BigIntegerMath.PRECISION
            val msg = "Big integer value out of range (allowed range is -10^$p..10^$p, exclusive)"
            throw Rt_Exception.common("bigint:overflow", msg)
        }

        fun getTry(v: BigInteger): Rt_Value? {
            return if (v < Lib_BigIntegerMath.MIN_VALUE || v > Lib_BigIntegerMath.MAX_VALUE) null else Rt_BigIntegerValue(v)
        }

        fun get(v: BigDecimal): Rt_Value {
            val bigInt = try {
                v.toBigIntegerExact()
            } catch (e: ArithmeticException) {
                throw Rt_Exception.common("bigint:nonint:$v", "Value is not an integer: '$v'")
            }
            return get(bigInt)
        }

        fun get(s: String): Rt_Value {
            val v = try {
                BigInteger(s)
            } catch (e: NumberFormatException) {
                throw Rt_Exception.common("bigint:invalid:$s", "Invalid big integer value: '$s'")
            }
            return get(v)
        }

        fun get(v: Long): Rt_Value {
            val bi = BigInteger.valueOf(v)
            return get(bi)
        }
    }
}

object GtvRtConversion_BigInteger: GtvRtConversion {
    override val directCompatibility = GtvCompatibility(fromGtv = true, toGtv = true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvFactory.gtv(rt.asBigInteger())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToBigInteger(ctx, gtv, "big_integer")
        return Rt_BigIntegerValue.get(v)
    }
}
