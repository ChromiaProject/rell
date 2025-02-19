/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.google.common.math.LongMath
import mu.KLogging
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_IntegerToBigInteger
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.checkEquals
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.*

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

    private val BIGINT_MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE)
    private val BIGINT_MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE)

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("big_integer", rType = R_BigIntegerType, since = SINCE0) {
            comment("""
                A data type that represents large integers with high precision, capable of handling very large numbers.
                Uses `java.math.BigInteger` internally.
            """)

            constant("PRECISION", Lib_BigIntegerMath.PRECISION.toLong(), since = SINCE0) {
                comment("The maximum number of digits (131072)")
            }

            constant("MIN_VALUE", Lib_BigIntegerMath.MIN_VALUE, since = SINCE0) {
                comment("Minimum value (-(10^131072)+1)")
            }

            constant("MAX_VALUE", Lib_BigIntegerMath.MAX_VALUE, since = SINCE0) {
                comment("Maximum value ((10^131072)-1)")
            }

            constructor(since = SINCE0) {
                comment("""
                    Creates a big_integer from a decimal string representation, possibly with a sign.
                    Fails if the string is not valid.
                """)
                param("s", type = "text", comment = "The decimal string representation.")
                bodyRaw(FromText_1)
            }

            constructor(since = SINCE0) {
                comment("Creates a big_integer from an integer.")
                param("value", type = "integer", comment = "The integer value.")
                bodyRaw(FromInteger)
            }

            staticFunction("from_bytes", result = "big_integer", pure = true, since = SINCE0) {
                comment("Creates a big_integer from a byte_array.")
                param("value", type = "byte_array", comment = "The byte array to convert.")
                body { a ->
                    val bytes = a.asByteArray()
                    val bigInt = BigInteger(bytes)
                    Rt_BigIntegerValue.get(bigInt)
                }
            }

            staticFunction("from_bytes_unsigned", result = "big_integer", pure = true, since = SINCE0) {
                comment("Creates a big_integer from an unsigned byte_array.")
                param("value", type = "byte_array", comment = "The byte array to convert.")
                body { a ->
                    val bytes = a.asByteArray()
                    val bigInt = BigInteger(1, bytes)
                    Rt_BigIntegerValue.get(bigInt)
                }
            }

            staticFunction("from_text", result = "big_integer", pure = true, since = SINCE0) {
                comment("Creates a big_integer from a text representation")
                param("value", type = "text", comment = "The decimal string representation.")
                bodyRaw(FromText_1)
            }

            staticFunction("from_text", result = "big_integer", pure = true, since = SINCE0) {
                comment("""
                    Creates a big_integer from a string representation with a specific base (radix, from 2 to 36).
                """)
                param("value", type = "text", comment = "The string representation.")
                param("radix", type = "integer") {
                    comment("The radix to be used in interpreting `value`. Must be between 2 and 36.")
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
                comment("Creates a big_integer from a hexadecimal string representation")
                param("value", type = "text", comment = "Hexadecimal string.")
                body { a ->
                    val s = a.asString()
                    calcFromText(s, 16, "from_hex")
                }
            }

            function("abs", "big_integer", since = SINCE0) {
                comment("Absolute value of the big_integer.")
                bodyRaw(Lib_Math.Abs_BigInteger)
            }

            function("min", "big_integer", since = SINCE0) {
                comment("Minimum of two big_integer values.")
                param("value", "big_integer", comment = "The value to compare against.")
                bodyRaw(Lib_Math.Min_BigInteger)
            }

            function("min", "decimal", pure = true, since = SINCE0) {
                comment("Minimum of a big_integer and a decimal value.")
                param("value", "decimal", comment = "The decimal value to compare against.")
                dbFunctionSimple("big_integer.min", "LEAST")
                body { a, b ->
                    val v1 = a.asBigInteger()
                    val v2 = b.asDecimal()
                    val r = v1.toBigDecimal().min(v2)
                    Rt_DecimalValue.get(r)
                }
            }

            function("max", "big_integer", since = SINCE0) {
                comment("Maximum of two big_integer values.")
                param("value", "big_integer", comment = "The value to compare against.")
                bodyRaw(Lib_Math.Max_BigInteger)
            }

            function("max", "decimal", pure = true, since = SINCE0) {
                comment("Maximum of a big_integer and a decimal value.")
                param("value", "decimal", comment = "The decimal value to compare against.")
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
                    Raises this big_integer to the power of the given exponent.
                    Can be used in a database at-expression.

                    1. The exponent cannot be negative.
                    2. Error on overflow, if the result is out of integer or big_integer range.
                    3. If the exponent is 0, the result is always 1; if the exponent is 1,
                    the result is the original value.
                """)
                param(name = "exponent", type = "integer", comment = "The exponent.")
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
                comment("Returns -1, 0, or 1 depending on the sign.")
                dbFunctionSimple("big_integer.sign", "SIGN")
                body { a ->
                    val v = a.asBigInteger()
                    val r = v.signum()
                    Rt_IntValue.get(r.toLong())
                }
            }

            // Function: to_bytes
            function("to_bytes", "byte_array", pure = true, since = SINCE0) {
                comment("Converts this big_integer to a byte_array.")
                body { a ->
                    val bigInt = a.asBigInteger()
                    val bytes = bigInt.toByteArray()
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("to_bytes_unsigned", "byte_array", pure = true, since = SINCE0) {
                comment("Converts this big_integer to an unsigned byte_array.")
                body { a ->
                    val bigInt = a.asBigInteger()
                    Rt_Utils.check(bigInt.signum() >= 0) {
                        "fn:big_integer.to_bytes_unsigned:negative" toCodeMsg "Value is negative"
                    }
                    var bytes = bigInt.toByteArray()
                    val n = (bigInt.bitLength() + 7) / 8
                    if (n != bytes.size) {
                        checkEquals(n, bytes.size - 1)
                        bytes = Arrays.copyOfRange(bytes, 1, bytes.size)
                    }
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            // Function: to_decimal
            function("to_decimal", "decimal", since = SINCE0) {
                comment("Converts this big_integer to a decimal value.")
                bodyRaw(Lib_Type_Decimal.FromBigInteger)
            }

            // Function: to_hex
            function("to_hex", "text", pure = true, since = SINCE0) {
                comment("Converts this big_integer to an unsigned hexadecimal representation.")
                body { a ->
                    val v = a.asBigInteger()
                    val s = v.toString(16)
                    Rt_TextValue.get(s)
                }
            }

            // Function: to_integer
            function("to_integer", "integer", pure = true, since = SINCE0) {
                comment("Converts this big_integer to an integer value.")
                dbFunctionTemplate("big_integer.to_integer", 1, "(#0)::BIGINT")
                body { a ->
                    val v = a.asBigInteger()
                    if (v < BIGINT_MIN_LONG || v > BIGINT_MAX_LONG) {
                        val s = v.toBigDecimal().round(MathContext(20, RoundingMode.DOWN))
                        throw Rt_Exception.common("big_integer.to_integer:overflow:$s", "Value out of range: $s")
                    }
                    val r = v.toLong()
                    Rt_IntValue.get(r)
                }
            }

            // Function: to_text
            function("to_text", "text", pure = true, since = SINCE0) {
                comment("Converts this big_integer to a decimal string representation.")
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
                    Converts this big_integer to a string representation with a specific base (radix, from 2 to 36).
                """)
                param("radix", "integer") {
                    comment("The radix to be used in the conversion. Must be between 2 and 36.")
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
            "$fnName:exp_negative:$exp" toCodeMsg "Negative exponent: $exp"
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
            if (res < MIN_VALUE || res > MAX_VALUE) {
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

object R_BigIntegerType: R_PrimitiveType("big_integer") {
    override fun defaultValue() = Rt_BigIntegerValue.ZERO
    override fun comparator() = Rt_Comparator.create { it.asBigInteger() }
    override fun fromCli(s: String): Rt_Value = Rt_BigIntegerValue.get(s)

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_BigInteger
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_BigInteger

    override fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        return when (sourceType) {
            R_IntegerType -> C_TypeAdapter_IntegerToBigInteger
            else -> super.getTypeAdapter(sourceType)
        }
    }

    override fun getLibTypeDef() = Lib_Rell.BIG_INTEGER_TYPE

    private object R_TypeSqlAdapter_BigInteger: R_TypeSqlAdapter_Primitive("big_integer", Lib_BigIntegerMath.SQL_TYPE) {
        override fun toSqlValue(value: Rt_Value) = value.asBigInteger()

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            val v = value.asBigInteger()
            params.setBigDecimal(idx, BigDecimal(v))
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getBigDecimal(idx)
            return if (v != null) Rt_BigIntegerValue.get(v) else checkSqlNull(R_BigIntegerType, nullable)
        }
    }
}

class Rt_BigIntegerValue private constructor(val value: BigInteger): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.BIG_INTEGER.type()

    override fun type() = R_BigIntegerType
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

private object GtvRtConversion_BigInteger: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvFactory.gtv(rt.asBigInteger())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToBigInteger(ctx, gtv, R_BigIntegerType)
        return ctx.rtValue {
            Rt_BigIntegerValue.get(v)
        }
    }
}
