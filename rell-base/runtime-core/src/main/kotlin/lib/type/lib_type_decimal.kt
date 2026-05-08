/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Math
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlConstants
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min

object Lib_Type_Decimal {
    val ToInteger = DecFns.ToInteger
    val FromInteger = DecFns.FromInteger
    val FromInteger_Db = DecFns.FromInteger_Db
    val FromBigInteger = DecFns.FromBigInteger
    val FromBigInteger_Db = DecFns.FromBigInteger_Db

    // Using regexp (in a stored procedure) to remove trailing zeros.
    val ToText_Db: Db_SysFunction = Db_SysFunction.simple("decimal.to_text", SqlConstants.FN_DECIMAL_TO_TEXT)

    private const val SINCE0 = "0.9.1"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("decimal", since = SINCE0) {
            rrType(RR_Type.Primitive(RR_PrimitiveKind.DECIMAL))
            comment("""
                A real number data type with high precision.

                Not a complete equivalent of floating-point types, as there are a fixed maximum number of digits before
                the decimal point (`131072`, or `2^17` digits) and after the decimal point (`20` digits).

                Examples:
                - `123.456`
                - `.789`
                - `7e+33`
                - `decimal('123456789.98765')`
            """)

            constant("PRECISION", Lib_DecimalMath.DECIMAL_PRECISION.toLong(), since = SINCE0) {
                comment("""
                    The maximum number of digits a `decimal` can have (`131072` before the decimal point and `20` after,
                    i.e. `131092`).
                """)
            }
            constant("SCALE", Lib_DecimalMath.DECIMAL_FRAC_DIGITS.toLong(), since = SINCE0) {
                comment("The maximum number of decimal digits after the decimal point (`20`).")
            }
            constant("INT_DIGITS", Lib_DecimalMath.DECIMAL_INT_DIGITS.toLong(), since = SINCE0) {
                comment("The maximum number of digits before the decimal point (`131072`).")
            }
            constant("MIN_VALUE", Lib_DecimalMath.DECIMAL_MIN_VALUE, since = SINCE0) {
                comment("""
                    The smallest nonzero absolute value that a decimal can store (`0.00000000000000000001`).
                """)
            }
            constant("MAX_VALUE", Lib_DecimalMath.DECIMAL_MAX_VALUE, since = SINCE0) {
                comment("The largest value that a decimal can store (`1E+131072 - 1`).")
            }

            constructor(since = SINCE0) {
                comment("Construct a decimal from a text representation.")
                param("value", "text", comment = "The text representation of the number.")
                bodyRaw(DecFns.FromText)
            }

            constructor(since = SINCE0) {
                comment("Construct a decimal from an integer.")
                param("value", "integer", comment = "the integer value")
                bodyRaw(DecFns.FromInteger)
            }

            constructor(since = "0.12.0") {
                comment("Construct a decimal from a `big_integer`.")
                param("value", "big_integer", comment = "the `big_integer` value")
                bodyRaw(DecFns.FromBigInteger)
            }

            staticFunction("from_text", "decimal", since = SINCE0) {
                comment("""
                    Parse a signed base-10 text representation of a real number.

                    If the encoded value has more decimal places than are supported, it is rounded to the nearest
                    supported value.
                    @throws exception when
                    - the text representation is ill-formed
                    - the magnitude of the encoded value is larger than `decimal.MAX_VALUE`
                """)

                param("value", "text", comment = "the text to parse")
                bodyRaw(DecFns.FromText)
            }

            function("abs", "decimal", since = SINCE0) {
                comment("""
                    Returns the absolute value of this decimal; i.e. the decimal itself if it's positive or its negation
                    if it's negative.
                """)
                bodyRaw(Lib_Math.Abs_Decimal)
            }

            function("ceil", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Round this decimal away from zero, to the next whole number.

                    Examples:
                    - `(-0.4).ceil()` returns `-1`
                    - `(-1.99999).ceil()` returns `-2`
                    - `(1.99999).ceil()` returns `2`
                    - `(1.00000000000000000001).ceil()` returns `2`
                """)
                dbFunctionSimple("decimal.ceil", "CEIL")
                body { a ->
                    val v = a.asDecimal()
                    val r = v.setScale(0, RoundingMode.CEILING)
                    Rt_DecimalValue.get(r)
                }
            }

            function("floor", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Round this decimal towards zero, to the next whole number.

                    Examples:
                    - `(-0.4).floor()` returns `0`
                    - `(-1.6).floor()` returns `-1`
                    - `(1.99999999999999999999).floor()` returns `1`
                """)
                dbFunctionSimple("decimal.floor", "FLOOR")
                body { a ->
                    // Route through the virtual hook so the long-mantissa Truffle leaf can do
                    // floor in plain Long arithmetic without materialising a BigDecimal.
                    (a as Rt_DecimalValue).fastFloor()
                }
            }

            function("min", "decimal", since = SINCE0) {
                comment("""
                    Returns the lesser of this and another decimal value; i.e. `value` if `value` is less than this, or
                    this decimal otherwise.
                    @return the lesser of `value` and this decimal
                """)
                param("value", "decimal", comment = "the value to compare against")
                bodyRaw(Lib_Math.Min_Decimal)
            }

            function("max", "decimal", since = SINCE0) {
                comment("""
                    Returns the greater of this and another decimal value; i.e. `value` if `value` is greater than this,
                    or this decimal otherwise.
                    @return the greater of `value` and this decimal
                """)
                param("value", "decimal", comment = "the value to compare against")
                bodyRaw(Lib_Math.Max_Decimal)
            }

            function("round", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Round this decimal to the nearest whole number.

                    Examples:
                    - `(-0.4).round()` returns `0`
                    - `(0.4).round()` returns `0`
                    - `(1.49999999999999999999).round()` returns `1`
                    - `(1.5).round()` returns `2`
                """)
                dbFunctionTemplate("decimal.round", 1, "ROUND(#0)")
                body { a ->
                    val v = a.asDecimal()
                    val r = v.setScale(0, RoundingMode.HALF_UP)
                    Rt_DecimalValue.get(r)
                }
            }

            function("round", "decimal", pure = true, since = SINCE0) {
                comment("""
                    Round this decimal to a specific number of decimal places.

                    `decimal.round(0)` is equivalent to `decimal.round()`, i.e. rounding will be to the nearest whole
                    number. Positive arguments round to an increasing number of decimal places, e.g. `1` rounds to the
                    neartest tenth, `2` to the nearest hundredth, `3` to the nearest thousandth. Negative arguments
                    round in the opposite way, i.e. `-1` rounds to the nearest ten, `-2` to the nearest hundred, `-3` to
                    the nearest thousand.

                    Examples:
                    - `(123.456).round(0)` returns `123`
                    - `(123.456).round(-1)` returns `120`
                    - `(123.456).round(1)` returns `123.4`
                    - `(123.456).round(-2)` returns `100`
                    - `(123.456).round(2)` returns `123.45`
                    - `(123.456).round(-3)` returns `0`
                    - `(123.456).round(3)` returns `123.456`
                """)
                param("digits", "integer", comment = "the number of decimal places to round")
                // Argument #2 has to be cast to INT, as PostgreSQL doesn't allow BIGINT.
                dbFunctionTemplate("decimal.round", 2, "ROUND(#0,(#1)::INT)")
                body { a, b ->
                    val v = a.asDecimal()
                    var scale = b.asInteger()
                    scale = max(scale, -Lib_DecimalMath.DECIMAL_INT_DIGITS.toLong())
                    scale = min(scale, Lib_DecimalMath.DECIMAL_FRAC_DIGITS.toLong())
                    val r = v.setScale(scale.toInt(), RoundingMode.HALF_UP)
                    Rt_DecimalValue.get(r)
                }
            }

            //function("pow", "decimal", listOf("integer"), R_SysFn_Decimal.Pow)

            // Function: sign
            function("sign", "integer", pure = true, since = SINCE0) {
                comment("""
                    Returns the sign of this decimal: `-1` if negative, `0` if zero, and `1` if positive.

                    It holds that for all `x`, `x == x.sign() * x.abs()`.
                """)
                alias("signum", C_MessageType.ERROR, since = SINCE0)
                dbFunctionSimple("decimal.sign", "SIGN")
                body { a ->
                    val v = a.asDecimal()
                    val r = v.signum()
                    Rt_IntValue.get(r.toLong())
                }
            }

            //function("sqrt", "decimal", listOf(), R_SysFn_Decimal.Sqrt)

            function("to_big_integer", "big_integer", pure = true, since = "0.12.0") {
                comment("""
                    Convert this decimal to a `big_integer`, truncating the fractional part.

                    Numerically equivalent to `decimal.floor()`, but with a type conversion.
                """)
                dbFunctionTemplate("decimal.to_big_integer", 1, "TRUNC(#0)")
                body { a ->
                    val v = a.asDecimal()
                    val bi = v.toBigInteger()
                    Rt_BigIntegerValue.get(bi)
                }
            }

            function("to_integer", "integer", since = SINCE0) {
                comment("""
                    Convert this decimal to an integer, truncating the fractional part.

                    Numerically equivalent to `decimal.floor()`, but with a type conversion.
                    @throws exception if this decimal is greater than `integer.MAX_VALUE` or less than
                    `integer.MIN_VALUE`.
                """)
                bodyRaw(DecFns.ToInteger)
            }

            function("to_text", "text", pure = true, since = SINCE0) {
                comment("Convert this decimal to a base 10 text representation.")
                dbFunction(ToText_Db)
                body { a ->
                    val v = a.asDecimal()
                    val r = Lib_DecimalMath.toString(v)
                    Rt_TextValue.get(r)
                }
            }

            function("to_text", "text", pure = true, since = SINCE0) {
                comment("""
                    Convert this decimal to a base 10 text representation, optionally using scientific notation (also
                    called standard form), e.g. `6.0221E+23`.

                    The general output format when scientific notation is used is `mEsn`, where `m` represents a
                    non-zero coefficient, `E` is the literal character, `s` represents the sign (either `+` or `-`),
                    and `n` represents the exponent.

                    Note that the maximum number of decimal places for the coefficient is `20`, and therefore any
                    precision beyond this in the original `decimal` value is lost.
                """)
                param("scientific", "boolean") {
                    comment("""
                        If `true`, the output will use scientific notation. If `false`, decimal representation will be
                        used.
                    """)
                }
                body { a, b ->
                    val v = a.asDecimal()
                    val sci = b.asBoolean()
                    val r = if (sci) {
                        Lib_DecimalMath.toSciString(v)
                    } else {
                        Lib_DecimalMath.toString(v)
                    }
                    Rt_TextValue.get(r)
                }
            }
        }
    }

    fun calcFromInteger(a: Rt_Value): Rt_Value = DecFns.calcFromInteger(a)
}

object Lib_DecimalMath {
    const val DECIMAL_INT_DIGITS = 131072
    const val DECIMAL_FRAC_DIGITS = 20
    const val DECIMAL_SQL_TYPE_STR = "NUMERIC"

    val DECIMAL_SQL_TYPE: DataType<BigDecimal> = SQLDataType.DECIMAL

    const val DECIMAL_PRECISION = DECIMAL_INT_DIGITS + DECIMAL_FRAC_DIGITS

    val DECIMAL_MIN_VALUE: BigDecimal = BigDecimal.ONE.divide(BigDecimal.TEN.pow(DECIMAL_FRAC_DIGITS))
    val DECIMAL_MAX_VALUE: BigDecimal = BigDecimal.TEN.pow(DECIMAL_PRECISION).subtract(BigDecimal.ONE)
        .divide(BigDecimal.TEN.pow(DECIMAL_FRAC_DIGITS))

    fun parse(s: String): BigDecimal {
        var t = if (s.startsWith(".")) {
            "0$s"
        } else if (s.startsWith("+.")) {
            "0${s.substring(1)}"
        } else if (s.startsWith("-.")) {
            "-0${s.substring(1)}"
        } else {
            s
        }
        t = removeTrailingZeros(t)
        return BigDecimal(t)
    }

    fun scale(v: BigDecimal): BigDecimal? {
        if (v.signum() == 0) {
            return BigDecimal.ZERO
        }

        val scale = v.scale()
        val intDigits = v.precision() - scale
        if (intDigits > DECIMAL_INT_DIGITS) {
            return null
        } else if (intDigits < -DECIMAL_FRAC_DIGITS) {
            return BigDecimal.ZERO
        }

        return if (scale <= DECIMAL_FRAC_DIGITS) {
            v.setScale(DECIMAL_FRAC_DIGITS)
        } else {
            // The number of integer digits may grow (by one) because of rounding - need to check again.
            val v2 = v.setScale(DECIMAL_FRAC_DIGITS, RoundingMode.HALF_UP)
            val intDigits2 = v2.precision() - v2.scale()
            if (intDigits2 > DECIMAL_INT_DIGITS) null else v2
        }
    }

    fun stripTrailingZeros(v: BigDecimal, all: Boolean = true): BigDecimal {
        val scale = v.scale()
        var s = scale

        var q = v.unscaledValue()
        while ((all || s > 0) && q.signum() != 0) {
            val arr = q.divideAndRemainder(BigInteger.TEN)
            val div = arr[0]
            val mod = arr[1]
            if (mod != BigInteger.ZERO) break
            --s
            q = div
        }

        if (s == scale) {
            return v
        }

        return BigDecimal(q, s)
    }

    fun add(a: BigDecimal, b: BigDecimal): BigDecimal {
        return a.add(b)
    }

    fun subtract(a: BigDecimal, b: BigDecimal): BigDecimal {
        return a.subtract(b)
    }

    fun multiply(a: BigDecimal, b: BigDecimal): BigDecimal {
        return a.multiply(b)
    }

    fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        val r = a.divide(b, DECIMAL_FRAC_DIGITS, RoundingMode.HALF_UP)
        return r
    }

    fun remainder(a: BigDecimal, b: BigDecimal): BigDecimal {
        return a.remainder(b)
    }

    fun power(a: BigDecimal, b: Int): BigDecimal {
        TODO() // Need to handle rounding and precision carefully.
    }

    fun toString(v: BigDecimal): String {
        val s = v.toPlainString()
        val r = removeTrailingZeros(s)
        return r
    }

    fun toSciString(v: BigDecimal): String {
        if (v.signum() == 0) {
            return "0"
        }

        var t = v.round(MathContext(DECIMAL_FRAC_DIGITS + 1, RoundingMode.HALF_UP))
        t = stripTrailingZeros(t)

        val unscaledStr = t.unscaledValue().abs().toString()
        val precision = unscaledStr.length
        val scale = t.scale()

        val e = when {
            scale == 0 -> precision - 1
            scale < 0 -> precision - 1 - scale
            else -> precision - 1 - scale
        }

        return buildString {
            if (v.signum() < 0) {
                append('-')
            }

            append(unscaledStr[0])
            append('.')

            if (precision >= 2) {
                append(unscaledStr.substring(1))
            } else {
                append('0')
            }

            if (e != 0) {
                append('E')
                if (e > 0) {
                    append('+')
                }
                append(e)
            }
        }
    }

    private fun removeTrailingZeros(s: String): String {
        // Verify that the string is a valid number and find the fractional part.
        val (fracStart, fracEnd) = parseString(s)

        var i = fracEnd
        while (i > fracStart && s[i - 1] == '0') --i
        if (i > fracStart && s[i - 1] == '.') --i

        return if (i == fracEnd) {
            s
        } else if (fracEnd == s.length) {
            s.substring(0, i)
        } else {
            s.substring(0, i) + s.substring(fracEnd)
        }
    }

    private fun parseString(s: String): Pair<Int, Int> {
        val n = s.length

        var fracStart = n
        var fracEnd = n
        var i = 0

        if (i < n && (s[i] == '-' || s[i] == '+')) ++i
        verifyDigit(s, i++)
        while (i < n && isDigit(s, i)) ++i

        if (i < n && s[i] == '.') {
            fracStart = i
            ++i
            verifyDigit(s, i++)
            while (i < n && isDigit(s, i)) ++i
        }

        if (i < n && (s[i] == 'E' || s[i] == 'e')) {
            if (fracStart == n) fracStart = i
            fracEnd = i
            ++i
            if (i < n && (s[i] == '+' || s[i] == '-')) ++i
            verifyDigit(s, i++)
            while (i < n && isDigit(s, i)) ++i
        }

        if (i != n) {
            throw NumberFormatException()
        }

        return Pair(fracStart, fracEnd)
    }

    private fun verifyDigit(s: String, i: Int) {
        if (i >= s.length || !isDigit(s, i)) {
            throw NumberFormatException()
        }
    }

    private fun isDigit(s: String, i: Int): Boolean = s[i] in '0'..'9'
}

private object DecFns {
    val Pow = C_SysFunctionBody.simple(Db_SysFunction.simple("decimal.pow", "POW"), pure = true) { a, b ->
        val v = a.asDecimal()
        val power = b.asInteger()
        if (power < 0) {
            throw Rt_Exception.common("decimal.pow:negative_power:$power", "Negative power: $power")
        }

        val r = Lib_DecimalMath.power(v, power.toInt())
        Rt_DecimalValue.get(r)
    }

    val Sqrt = C_SysFunctionBody.simple(Db_SysFunction.simple("decimal.sqrt", "SQRT"), pure = true) { a ->
        val v = a.asDecimal()
        if (v < BigDecimal.ZERO) {
            throw Rt_Exception.common("decimal.sqrt:negative:$v", "Negative value")
        }
        TODO()
    }

    val ToInteger = C_SysFunctionBody.simple(
        Db_SysFunction.template("decimal.to_integer", 1, "TRUNC(#0)::BIGINT"),
        pure = true,
    ) { a ->
        // Route through the virtual hook so the long-mantissa Truffle leaf can return the
        // truncated mantissa directly without `BigDecimal.toBigInteger()` + range check.
        Rt_IntValue.get((a as Rt_DecimalValue).fastToInteger())
    }

    fun calcFromInteger(a: Rt_Value): Rt_Value {
        val i = a.asInteger()
        return Rt_DecimalValue.get(i)
    }

    val FromInteger_Db = Db_SysFunction.cast("decimal(integer)", Lib_DecimalMath.DECIMAL_SQL_TYPE_STR)

    val FromInteger = C_SysFunctionBody.simple(FromInteger_Db, pure = true) { a ->
        calcFromInteger(a)
    }

    val FromBigInteger_Db = Db_SysFunction.template("decimal(big_integer)", 1, "#0")

    val FromBigInteger = C_SysFunctionBody.simple(FromBigInteger_Db, pure = true) { a ->
        val bigInt = a.asBigInteger()
        val bigDec = bigInt.toBigDecimal()
        Rt_DecimalValue.get(bigDec)
    }

    val FromText = C_SysFunctionBody.simple(
        Db_SysFunction.simple("decimal(text)", SqlConstants.FN_DECIMAL_FROM_TEXT),
        pure = true
    ) { a ->
        val s = a.asString()
        Rt_DecimalValue.get(s)
    }
}
