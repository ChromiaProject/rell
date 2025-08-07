/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lang.type.DecimalTest
import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.testutils.BaseExprTest
import java.math.BigInteger
import kotlin.test.Test

abstract class LibDecimalExprTest: BaseExprTest() {
    class LibDecimalExprIpTest: LibDecimalExprTest()
    class LibDecimalExprDbTest: LibDecimalExprTest()

    @Test fun testConstructorText() {
        chkConstructor("0", "dec[0]")
        chkConstructor("12345", "dec[12345]")
        chkConstructor("10000", "dec[10000]")
        chkConstructor("1000000000000", "dec[1000000000000]")
        chkConstructor("0000", "dec[0]")
        chkConstructor("0001", "dec[1]")

        chkConstructor("-0", "dec[0]")
        chkConstructor("-1", "dec[-1]")
        chkConstructor("-12345689", "dec[-12345689]")
        chkConstructor("-0001", "dec[-1]")
        chkConstructor("+0", "dec[0]")
        chkConstructor("+1", "dec[1]")
        chkConstructor("+12345689", "dec[12345689]")
        chkConstructor("+0001", "dec[1]")

        chkConstructor("123E0", "dec[123]")
        chkConstructor("123E5", "dec[12300000]")
        chkConstructor("123E+5", "dec[12300000]")
        chkConstructor("123e5", "dec[12300000]")
        chkConstructor("123e+5", "dec[12300000]")
        chkConstructor("123E15", "dec[123000000000000000]")
        chkConstructor("123E-1", "dec[12.3]")
        chkConstructor("123E-2", "dec[1.23]")
        chkConstructor("123E-3", "dec[0.123]")
        chkConstructor("123E-10", "dec[0.0000000123]")
        chkConstructor("123e-10", "dec[0.0000000123]")
        chkConstructor("-123E-10", "dec[-0.0000000123]")
        chkConstructor("+123e-10", "dec[0.0000000123]")

        chkConstructor("123.456", "dec[123.456]")
        chkConstructor("123456E-3", "dec[123.456]")
        chkConstructor(".1", "dec[0.1]")
        chkConstructor(".000000000001", "dec[0.000000000001]")
        chkConstructor("-.1", "dec[-0.1]")
        chkConstructor("-.000000000001", "dec[-0.000000000001]")
        chkConstructor("+.1", "dec[0.1]")
        chkConstructor("+.000000000001", "dec[0.000000000001]")
        chkConstructor("0.0", "dec[0]")
        chkConstructor("0.00000", "dec[0]")
        chkConstructor("1.0", "dec[1]")
        chkConstructor("1.00000", "dec[1]")

        chkExpr("decimal(false)", "ct_err:expr_call_badargs:[decimal]:[boolean]")
    }

    @Test fun testConstructorTextInvalid() {
        chkConstructor("", rtErr("decimal:invalid:"))
        chkConstructor("1 ", rtErr("decimal:invalid:1 "))
        chkConstructor(" 1", rtErr("decimal:invalid: 1"))
        chkConstructor(" 1 ", rtErr("decimal:invalid: 1 "))
        chkConstructor("++1", rtErr("decimal:invalid:++1"))
        chkConstructor("--1", rtErr("decimal:invalid:--1"))
        chkConstructor("-+1", rtErr("decimal:invalid:-+1"))
        chkConstructor("+-1", rtErr("decimal:invalid:+-1"))

        chkConstructor("123+5", rtErr("decimal:invalid:123+5"))
        chkConstructor("123-5", rtErr("decimal:invalid:123-5"))
        chkConstructor("1.E5", rtErr("decimal:invalid:1.E5"))
        chkConstructor("1.e5", rtErr("decimal:invalid:1.e5"))
        chkConstructor("1.E+5", rtErr("decimal:invalid:1.E+5"))
        chkConstructor("1.E-5", rtErr("decimal:invalid:1.E-5"))
        chkConstructor(".E5", rtErr("decimal:invalid:.E5"))
        chkConstructor(".e5", rtErr("decimal:invalid:.e5"))
        chkConstructor(".E+5", rtErr("decimal:invalid:.E+5"))
        chkConstructor(".E-5", rtErr("decimal:invalid:.E-5"))
        chkConstructor(".5E", rtErr("decimal:invalid:.5E"))
        chkConstructor(".5e", rtErr("decimal:invalid:.5e"))
        chkConstructor("1.5E", rtErr("decimal:invalid:1.5E"))
        chkConstructor("1.5e", rtErr("decimal:invalid:1.5e"))

        chkConstructor("Hello", rtErr("decimal:invalid:Hello"))
        chkConstructor("0x1234", rtErr("decimal:invalid:0x1234"))
    }

    @Test fun testConstructorTextRounding() {
        val rep0 = DecimalTest.fracBase("0")
        val rep9 = DecimalTest.fracBase("9")

        chkConstructor("0.${rep9}90", "dec[0.${rep9}9]")
        chkConstructor("0.${rep9}91", "dec[0.${rep9}91]")
        chkConstructor("0.${rep9}99", "dec[0.${rep9}99]")
        chkConstructor("0.${rep9}990", "dec[0.${rep9}99]")
        chkConstructor("0.${rep9}991", "dec[0.${rep9}99]")
        chkConstructor("0.${rep9}992", "dec[0.${rep9}99]")
        chkConstructor("0.${rep9}993", "dec[0.${rep9}99]")
        chkConstructor("0.${rep9}994", "dec[0.${rep9}99]")
        chkConstructor("0.${rep9}995", "dec[1]")
        chkConstructor("0.${rep9}996", "dec[1]")
        chkConstructor("0.${rep9}997", "dec[1]")
        chkConstructor("0.${rep9}998", "dec[1]")
        chkConstructor("0.${rep9}999", "dec[1]")

        chkConstructor("0.${rep0}100", "dec[0.${rep0}1]")
        chkConstructor("0.${rep0}101", "dec[0.${rep0}1]")
        chkConstructor("0.${rep0}104", "dec[0.${rep0}1]")
        chkConstructor("0.${rep0}105", "dec[0.${rep0}11]")
        chkConstructor("0.${rep0}109", "dec[0.${rep0}11]")
        chkConstructor("0.${rep0}194", "dec[0.${rep0}19]")
        chkConstructor("0.${rep0}195", "dec[0.${rep0}2]")

        chkConstructor("0.${rep0}001", "dec[0]")
        chkConstructor("0.${rep0}004", "dec[0]")
        chkConstructor("0.${rep0}005", "dec[0.${rep0}01]")
        chkConstructor("0.${rep0}009", "dec[0.${rep0}01]")

        val fracExp = Lib_DecimalMath.DECIMAL_FRAC_DIGITS - 2
        chkConstructor("0.100e-$fracExp", "dec[0.${rep0}1]")
        chkConstructor("0.101e-$fracExp", "dec[0.${rep0}1]")
        chkConstructor("0.104e-$fracExp", "dec[0.${rep0}1]")
        chkConstructor("0.105e-$fracExp", "dec[0.${rep0}11]")
        chkConstructor("0.109e-$fracExp", "dec[0.${rep0}11]")
        chkConstructor("0.194e-$fracExp", "dec[0.${rep0}19]")
        chkConstructor("0.195e-$fracExp", "dec[0.${rep0}2]")
    }

    @Suppress("RemoveSingleExpressionStringTemplate")
    @Test fun testConstructorTextOverflow() {
        val limitMinusOne = DecimalTest.limitMinus(1)
        val overLimit = "" + (DecimalTest.LIMIT * BigInteger.valueOf(3))
        val fracLimit = BigInteger.TEN.pow(Lib_DecimalMath.DECIMAL_FRAC_DIGITS) - BigInteger.ONE
        val fracLimitMinusOne = fracLimit - BigInteger.ONE

        chkConstructor("$limitMinusOne", "dec[$limitMinusOne]")
        chkConstructor("-$limitMinusOne", "dec[-$limitMinusOne]")
        chkConstructor("${DecimalTest.LIMIT}", rtErr("decimal:overflow"))
        chkConstructor("-${DecimalTest.LIMIT}", rtErr("decimal:overflow"))
        chkConstructor("$overLimit", rtErr("decimal:overflow"))
        chkConstructor("-$overLimit", rtErr("decimal:overflow"))
        chkConstructor("$limitMinusOne.$fracLimit", "dec[$limitMinusOne.$fracLimit]")
        chkConstructor("-$limitMinusOne.$fracLimit", "dec[-$limitMinusOne.$fracLimit]")
        chkConstructor("$limitMinusOne.${fracLimit}9", rtErr("decimal:overflow"))
        chkConstructor("-$limitMinusOne.${fracLimit}9", rtErr("decimal:overflow"))
        chkConstructor("$limitMinusOne.${fracLimitMinusOne}9", "dec[$limitMinusOne.${fracLimit}]")
        chkConstructor("-$limitMinusOne.${fracLimitMinusOne}9", "dec[-$limitMinusOne.${fracLimit}]")
    }

    private fun chkConstructor(s: String, exp: String) {
        chkExpr("decimal(#0)", exp, vText(s))
    }

    @Test fun testAbs() {
        chkExpr("#0.abs()", "dec[0]", vDec("0"))
        chkExpr("#0.abs()", "dec[12345]", vDec("-12345"))
        chkExpr("#0.abs()", "dec[67890]", vDec("67890"))
        chkExpr("#0.abs()", "dec[9223372036854775808]", vDec("-9223372036854775808"))
        chkExpr("#0.abs()", "dec[123000000000000000000000000000000]", vDec("-123E30"))
    }

    @Test fun testMinMax() {
        chkExpr("#0.min(#1)", "dec[123]", vDec(123), vDec(456))
        chkExpr("#0.min(#1)", "dec[123]", vDec(456), vDec(123))
        chkExpr("#0.max(#1)", "dec[456]", vDec(123), vDec(456))
        chkExpr("#0.max(#1)", "dec[456]", vDec(456), vDec(123))

        chkExpr("#0.min(#1)", "dec[123]", vDec(123), vInt(456))
        chkExpr("#0.min(#1)", "dec[123]", vDec(456), vInt(123))
        chkExpr("#0.max(#1)", "dec[456]", vDec(123), vInt(456))
        chkExpr("#0.max(#1)", "dec[456]", vDec(456), vInt(123))
        chkExpr("#0.min(#1)", "dec[456]", vDec("123E31"), vInt(456))

        chkExpr("#0.min(#1)", "dec[123]", vDec(123), vBigInt(456))
        chkExpr("#0.min(#1)", "dec[123]", vDec(456), vBigInt(123))
        chkExpr("#0.max(#1)", "dec[456]", vDec(123), vBigInt(456))
        chkExpr("#0.max(#1)", "dec[456]", vDec(456), vBigInt(123))
        chkExpr("#0.min(#1)", "dec[456]", vDec("123E31"), vBigInt(456))

        chkExpr("#0.min(#1)", "dec[456000000000000000000000000000000]", vDec("123E31"), vDec("456E30"))
        chkExpr("#0.min(#1)", "dec[456000000000000000000000000000000]", vDec("456E30"), vDec("123E31"))
        chkExpr("#0.max(#1)", "dec[1230000000000000000000000000000000]", vDec("123E31"), vDec("456E30"))
        chkExpr("#0.max(#1)", "dec[1230000000000000000000000000000000]", vDec("456E30"), vDec("123E31"))
        chkExpr("#0.max(#1)", "dec[1230000000000000000000000000000000]", vDec("123E31"), vInt(456))
        chkExpr("#0.max(#1)", "dec[1230000000000000000000000000000000]", vDec("123E31"), vBigInt(456))
    }

    @Test fun testConstructorInteger() {
        chkExpr("decimal(#0)", "dec[0]", vInt(0))
        chkExpr("decimal(#0)", "dec[1]", vInt(1))
        chkExpr("decimal(#0)", "dec[-1]", vInt(-1))
        chkExpr("decimal(#0)", "dec[9223372036854775807]", vInt(9223372036854775807))
        chkExpr("decimal(#0)", "dec[-9223372036854775808]", vInt("-9223372036854775808".toLong()))
    }

    @Test fun testConstructorBigInteger() {
        chkExpr("decimal(#0)", "dec[0]", vBigInt(0))
        chkExpr("decimal(#0)", "dec[1]", vBigInt(1))
        chkExpr("decimal(#0)", "dec[-1]", vBigInt(-1))
        chkExpr("decimal(#0)", "dec[9223372036854775808]", vBigInt("9223372036854775808"))
        chkExpr("decimal(#0)", "dec[-9223372036854775808]", vBigInt("-9223372036854775808"))
        chkExpr("decimal(#0)", "dec[340282366920938463463374607431768211455]", vBigInt("340282366920938463463374607431768211455"))
        chkExpr("decimal(#0)", "dec[-340282366920938463463374607431768211455]", vBigInt("-340282366920938463463374607431768211455"))
    }

    @Test fun testCeil() {
        chkExpr("decimal(#0).ceil()", "dec[0]", vText("0"))
        chkExpr("decimal(#0).ceil()", "dec[1]", vText("0.001"))
        chkExpr("decimal(#0).ceil()", "dec[1]", vText("0.999"))
        chkExpr("decimal(#0).ceil()", "dec[123]", vText("123"))
        chkExpr("decimal(#0).ceil()", "dec[124]", vText("123.0000000000001"))
        chkExpr("decimal(#0).ceil()", "dec[124]", vText("123.9999999999999"))
        chkExpr("decimal(#0).ceil()", "dec[124]", vText("124.0000000000000"))
        chkExpr("decimal(#0).ceil()", "dec[0]", vText("-0.001"))
        chkExpr("decimal(#0).ceil()", "dec[0]", vText("-0.999"))
        chkExpr("decimal(#0).ceil()", "dec[-1]", vText("-1.000"))
        chkExpr("decimal(#0).ceil()", "dec[-1]", vText("-1.999"))

        val maxIntPart = "9".repeat(Lib_DecimalMath.DECIMAL_INT_DIGITS)
        chkExpr("#0.ceil()", rtErr("decimal:overflow"), vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("(-#0).ceil()", "dec[-$maxIntPart]", vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("#0.ceil()", "dec[1]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("(-#0).ceil()", "dec[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
    }

    @Test fun testFloor() {
        chkExpr("decimal(#0).floor()", "dec[0]", vText("0"))
        chkExpr("decimal(#0).floor()", "dec[0]", vText("0.001"))
        chkExpr("decimal(#0).floor()", "dec[0]", vText("0.999"))
        chkExpr("decimal(#0).floor()", "dec[1]", vText("1"))
        chkExpr("decimal(#0).floor()", "dec[1]", vText("1.00000"))
        chkExpr("decimal(#0).floor()", "dec[1]", vText("1.99999"))
        chkExpr("decimal(#0).floor()", "dec[2]", vText("2"))
        chkExpr("decimal(#0).floor()", "dec[123]", vText("123"))
        chkExpr("decimal(#0).floor()", "dec[123]", vText("123.0000000000001"))
        chkExpr("decimal(#0).floor()", "dec[123]", vText("123.9999999999999"))
        chkExpr("decimal(#0).floor()", "dec[124]", vText("124.0000000000000"))
        chkExpr("decimal(#0).floor()", "dec[-1]", vText("-0.001"))
        chkExpr("decimal(#0).floor()", "dec[-1]", vText("-0.999"))
        chkExpr("decimal(#0).floor()", "dec[-1]", vText("-1.000"))
        chkExpr("decimal(#0).floor()", "dec[-2]", vText("-1.0000000000001"))

        val maxIntPart = "9".repeat(Lib_DecimalMath.DECIMAL_INT_DIGITS)
        chkExpr("#0.floor()", "dec[$maxIntPart]", vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("(-#0).floor()", rtErr("decimal:overflow"), vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("#0.floor()", "dec[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("(-#0).floor()", "dec[-1]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
    }

    @Test fun testRound() {
        chkExpr("decimal(#0).round()", "dec[0]", vText("0"))
        chkExpr("decimal(#0).round()", "dec[0]", vText("0.1"))
        chkExpr("decimal(#0).round()", "dec[0]", vText("0.4"))
        chkExpr("decimal(#0).round()", "dec[1]", vText("0.5"))
        chkExpr("decimal(#0).round()", "dec[1]", vText("0.9"))

        chkExpr("decimal(#0).round()", "dec[123456]", vText("123456.1"))
        chkExpr("decimal(#0).round()", "dec[123456]", vText("123456.4"))
        chkExpr("decimal(#0).round()", "dec[123457]", vText("123456.5"))
        chkExpr("decimal(#0).round()", "dec[123457]", vText("123456.9"))
        chkExpr("decimal(#0).round()", "dec[-123456]", vText("-123456.1"))
        chkExpr("decimal(#0).round()", "dec[-123456]", vText("-123456.4"))
        chkExpr("decimal(#0).round()", "dec[-123457]", vText("-123456.5"))
        chkExpr("decimal(#0).round()", "dec[-123457]", vText("-123456.9"))

        chkExpr("#0.round()", rtErr("decimal:overflow"), vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("(-#0).round()", rtErr("decimal:overflow"), vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("#0.round()", "dec[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("(-#0).round()", "dec[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
    }

    @Test fun testRoundScalePositive() {
        chkRoundScale("123456.1", 0, "123456")
        chkRoundScale("123456.4", 0, "123456")
        chkRoundScale("123456.5", 0, "123457")
        chkRoundScale("123456.9", 0, "123457")

        chkRoundScale("123456.789", 0, "123457")
        chkRoundScale("123456.789", 1, "123456.8")
        chkRoundScale("123456.789", 2, "123456.79")
        chkRoundScale("123456.789", 3, "123456.789")

        chkRoundScale("123.41", 1, "123.4")
        chkRoundScale("123.44", 1, "123.4")
        chkRoundScale("123.45", 1, "123.5")
        chkRoundScale("123.49", 1, "123.5")

        chkRoundScale("123.451", 2, "123.45")
        chkRoundScale("123.454", 2, "123.45")
        chkRoundScale("123.455", 2, "123.46")
        chkRoundScale("123.459", 2, "123.46")

        chkRoundScale("123.01234567891", 10, "123.0123456789")
        chkRoundScale("123.01234567894", 10, "123.0123456789")
        chkRoundScale("123.01234567895", 10, "123.012345679")
        chkRoundScale("123.01234567899", 10, "123.012345679")

        chkRoundScale("123.456", 100, "123.456")
        chkRoundScale("123.456", 1000000, "123.456")
        chkRoundScale("123.456", 1000000000, "123.456")
        chkRoundScale("123.98765432100123456789", 100, "123.98765432100123456789")
        chkRoundScale("123.98765432100123456789", 1000000, "123.98765432100123456789")
        chkRoundScale("123.98765432100123456789", 1000000000, "123.98765432100123456789")
    }

    @Test fun testRoundScaleNegative() {
        chkRoundScale("123456.789", 0, "123457")
        chkRoundScale("123456.789", -1, "123460")
        chkRoundScale("123456.789", -2, "123500")
        chkRoundScale("123456.789", -3, "123000")
        chkRoundScale("123456.789", -4, "120000")
        chkRoundScale("123456.789", -5, "100000")
        chkRoundScale("123456.789", -6, "0")
        chkRoundScale("123456.789", -7, "0")
        chkRoundScale("123456.789", -100, "0")
        chkRoundScale("123456.789", -1000000, "0")
        chkRoundScale("123456.789", -1000000000, "0")

        chkRoundScale("1231", -1, "1230")
        chkRoundScale("1234", -1, "1230")
        chkRoundScale("1235", -1, "1240")
        chkRoundScale("1239", -1, "1240")

        chkRoundScale("1211", -2, "1200")
        chkRoundScale("1242", -2, "1200")
        chkRoundScale("1253", -2, "1300")
        chkRoundScale("1294", -2, "1300")

        chkRoundScale("2101", -3, "2000")
        chkRoundScale("2402", -3, "2000")
        chkRoundScale("2503", -3, "3000")
        chkRoundScale("2904", -3, "3000")

        chkRoundScale("1234", -4, "0")
        chkRoundScale("4234", -4, "0")
        chkRoundScale("5234", -4, "10000")
        chkRoundScale("9234", -4, "10000")

        chkRoundScaleNegativeZeros(99)
        chkRoundScaleNegativeZeros(999)
        chkRoundScaleNegativeZeros(1999)

        // TODO Rounding in PostgreSQL is wrong for zeros = 2000+, do something about that.
        //chkLibDecimalRoundScaleNegativeZeros(2000)
        //chkLibDecimalRoundScaleNegativeZeros(9999)
    }

    private fun chkRoundScaleNegativeZeros(zeros: Int) {
        val zs = "0".repeat(zeros)
        chkRoundScale("1231$zs", -zeros-1, "1230$zs")
        chkRoundScale("1234$zs", -zeros-1, "1230$zs")
        chkRoundScale("1235$zs", -zeros-1, "1240$zs")
        chkRoundScale("1239$zs", -zeros-1, "1240$zs")
    }

    private fun chkRoundScale(v: String, scale: Int, expected: String) {
        val expectedNeg = if (expected == "0") "0" else "-$expected"
        chkExpr("decimal(#0).round($scale)", "dec[$expected]", vText("$v"))
        chkExpr("decimal(#0).round($scale)", "dec[$expectedNeg]", vText("-$v"))
    }

    @Test fun testSign() {
        chkExpr("#0.sign()", "int[0]", vDec("0"))
        chkExpr("#0.sign()", "int[1]", vDec("12345"))
        chkExpr("#0.sign()", "int[-1]", vDec("-12345"))
        chkExpr("#0.sign()", "int[1]", vDec("123456789101112131415161718192021222324252627282930"))
        chkExpr("#0.sign()", "int[-1]", vDec("-123456789101112131415161718192021222324252627282930"))
    }

    @Test fun testToInteger() {
        chkExpr("decimal(#0).to_integer()", "int[0]", vText("0"))
        chkExpr("decimal(#0).to_integer()", "int[123]", vText("123.456"))
        chkExpr("decimal(#0).to_integer()", "int[123]", vText("123.999"))
        chkExpr("decimal(#0).to_integer()", "int[-123]", vText("-123.456"))
        chkExpr("decimal(#0).to_integer()", "int[-123]", vText("-123.999"))
        chkExpr("decimal(#0).to_integer()", "int[9223372036854775807]", vText("9223372036854775807"))
        chkExpr("decimal(#0).to_integer()", "int[9223372036854775807]", vText("9223372036854775807.999999999"))
        chkExpr("decimal(#0).to_integer()", rtErr("decimal.to_integer:overflow:9223372036854775808"), vText("9223372036854775808"))
        chkExpr("decimal(#0).to_integer()", "int[-9223372036854775808]", vText("-9223372036854775808"))
        chkExpr("decimal(#0).to_integer()", "int[-9223372036854775808]", vText("-9223372036854775808.999999999"))
        chkExpr("decimal(#0).to_integer()", rtErr("decimal.to_integer:overflow:-9223372036854775809"), vText("-9223372036854775809"))
        chkExpr("#0.to_integer()", "int[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("(-#0).to_integer()", "int[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("#0.to_integer()", rtErr("decimal.to_integer:overflow:9.9999999999999999999E+131071"), vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("(-#0).to_integer()", rtErr("decimal.to_integer:overflow:-9.9999999999999999999E+131071"), vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
    }

    @Test fun testToBigInteger() {
        chkExpr("decimal(#0).to_big_integer()", "bigint[0]", vText("0"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[123]", vText("123.456"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[123]", vText("123.999"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[-123]", vText("-123.456"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[-123]", vText("-123.999"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[9223372036854775807]", vText("9223372036854775807"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[9223372036854775807]", vText("9223372036854775807.999999999"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[9223372036854775808]", vText("9223372036854775808"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[-9223372036854775808]", vText("-9223372036854775808"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[-9223372036854775808]", vText("-9223372036854775808.999999999"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[-9223372036854775809]", vText("-9223372036854775809"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[79228162514264337593543950335]", vText("79228162514264337593543950335"))
        chkExpr("decimal(#0).to_big_integer()", "bigint[-79228162514264337593543950335]", vText("-79228162514264337593543950335"))
        chkExpr("#0.to_big_integer()", "bigint[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("(-#0).to_big_integer()", "bigint[0]", vDec(Lib_DecimalMath.DECIMAL_MIN_VALUE))
        chkExpr("#0.to_big_integer()", "bigint[${Lib_BigIntegerMath.MAX_VALUE}]", vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
        chkExpr("(-#0).to_big_integer()", "bigint[-${Lib_BigIntegerMath.MAX_VALUE}]", vDec(Lib_DecimalMath.DECIMAL_MAX_VALUE))
    }

    @Test fun testToText() {
        chkExpr("decimal(#0).to_text()", "text[0]", vText("0"))
        chkExpr("decimal(#0).to_text()", "text[123.456]", vText("123.456"))
        chkExpr("decimal(#0).to_text()", "text[-123.456]", vText("-123.456"))
        chkExpr("decimal(#0).to_text()", "text[1234000000000000000000]", vText("12.34e20"))
        chkExpr("decimal(#0).to_text()", "text[-1234000000000000000000]", vText("-12.34e20"))
        chkExpr("decimal(#0).to_text()", "text[0.00000000000000001234]", vText("12.34e-18"))
        chkExpr("decimal(#0).to_text()", "text[-0.00000000000000001234]", vText("-12.34e-18"))
        chkExpr("'' + decimal(#0)", "text[123.456]", vText("123.456"))
    }

    @Test fun testEquals() {
        chkExpr("#0 * #1 == #2 * #3", "boolean[true]", vDec("123456"), vDec("1e-3"), vDec("0.123456"), vDec("1e3"))
        chkExpr("#0 * 1e-3 == #1 * 1e3", "boolean[true]", vDec("123456"), vDec("0.123456"))
        chkExpr("decimal(#0) * 1e-3 == decimal(#1) * 1e3", "boolean[true]", vText("123456"), vText("0.123456"))
        chkExpr("#0 == #1", "boolean[true]", vDec("123456e-3"), vDec("0.123456e3"))
        chkExpr("decimal(#0) == decimal(#1)", "boolean[true]", vText("123456e-3"), vText("0.123456e3"))
        chkExpr("#0 == #1", "boolean[true]", vDec("123e5"), vDec("12300e3"))
        chkExpr("decimal(#0) == decimal(#1)", "boolean[true]", vText("123e5"), vText("12300e3"))
        chkExpr("#0 * #1 == #2 * #3", "boolean[true]", vDec("123"), vDec("1e5"), vDec("12300"), vDec("1e3"))
        chkExpr("#0 * 1e5 == #1 * 1e3", "boolean[true]", vDec("123"), vDec("12300"))
        chkExpr("#0 * 100000 == #1 * 1000", "boolean[true]", vDec("123"), vDec("12300"))
        chkExpr("#0 * #1 == #2 * #3", "boolean[true]", vDec("123.4"), vDec("1e5"), vDec("12340"), vDec("1e3"))
        chkExpr("#0 * 1e5 == #1 * 1e3", "boolean[true]", vDec("123.4"), vDec("12340"))
        chkExpr("decimal(#0) * 1e5 == decimal(#1) * 1e3", "boolean[true]", vText("123.4"), vText("12340"))
        chkExpr("#0 == #1", "boolean[true]", vDec("123.4e5"), vDec("12340e3"))
        chkExpr("decimal(#0) == decimal(#1)", "boolean[true]", vText("123.4e5"), vText("12340e3"))
        chkExpr("#0 == #1", "boolean[true]", vDec("123.450e6"), vDec("123450e3"))
        chkExpr("decimal(#0) == decimal(#1)", "boolean[true]", vText("123.450e6"), vText("123450e3"))
        chkExpr("#0 == #1", "boolean[true]", vDec("123.456e6"), vDec("123456e3"))
        chkExpr("decimal(#0) == decimal(#1)", "boolean[true]", vText("123.456e6"), vText("123456e3"))
    }
}
