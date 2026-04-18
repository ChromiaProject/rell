/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.lang.type.BigIntegerTest
import net.postchain.rell.base.lang.type.DecimalTest
import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.testutils.BaseExprTest
import net.postchain.rell.base.testutils.RellExprTester.TstVal
import java.math.BigInteger
import kotlin.test.Test

abstract class OperatorsBaseTest: BaseExprTest() {
    @Test fun testCmpBoolean() {
        chkOpBool("==", vBool(false), vBool(false), true)
        chkOpBool("==", vBool(false), vBool(true), false)
        chkOpBool("==", vBool(true), vBool(false), false)
        chkOpBool("==", vBool(true), vBool(true), true)

        chkOpBool("!=", vBool(false), vBool(false), false)
        chkOpBool("!=", vBool(false), vBool(true), true)
        chkOpBool("!=", vBool(true), vBool(false), true)
        chkOpBool("!=", vBool(true), vBool(true), false)

        chkOpErr("boolean < boolean")
        chkOpErr("boolean > boolean")
        chkOpErr("boolean <= boolean")
        chkOpErr("boolean >= boolean")
    }

    @Test fun testCmpInteger() {
        chkCmpCommon(vInt(55), vInt(122), vInt(123), vInt(124), vInt(456))
    }

    @Test fun testCmpBigInteger() {
        chkCmpCommon(vBigInt("55"), vBigInt("122"), vBigInt("123"), vBigInt("124"), vBigInt("456"))
        chkCmpCommon(vBigInt("55e500"), vBigInt("122e500"), vBigInt("123e500"), vBigInt("124e500"), vBigInt("456e500"))
        chkCmpCommon(vBigInt("1e500+55"), vBigInt("1e500+122"), vBigInt("1e500+123"), vBigInt("1e500+124"), vBigInt("1e500+456"))

        // Extreme values.
        val maxp = Lib_BigIntegerMath.PRECISION - 1
        chkCmpCommon( vDec("1e$maxp+55"), vDec("1e$maxp+122"), vDec("1e$maxp+123"), vDec("1e$maxp+124"), vDec("1e$maxp+456"))
        chkCmpCommon( vDec("1e$maxp+125"), vDec("2e$maxp+124"), vDec("3e$maxp+123"), vDec("4e$maxp+122"), vDec("5e$maxp+55"))

        // Mixed integer and big_integer.
        chkOpEq(vInt(123), vBigInt("123"), true)
        chkOpEq(vInt(123), vBigInt("321"), false)
        chkCmpCommon(vBigInt("55"), vInt(122), vBigInt("123"), vInt(124), vBigInt("456"))
        chkCmpCommon(vInt(55), vBigInt("122"), vInt(123), vBigInt("124"), vInt(456))
    }

    @Test fun testCmpDecimal() {
        chkCmpCommon(vDec("55"), vDec("122"), vDec("123"), vDec("124"), vDec("456"))
        chkCmpCommon(vDec("55e500"), vDec("122e500"), vDec("123e500"), vDec("124e500"), vDec("456e500"))
        chkCmpCommon(vDec("1e500+55"), vDec("1e500+122"), vDec("1e500+123"), vDec("1e500+124"), vDec("1e500+456"))

        // Extreme values.
        val maxp = Lib_DecimalMath.DECIMAL_INT_DIGITS - 1
        val minp = -Lib_DecimalMath.DECIMAL_FRAC_DIGITS
        chkCmpCommon(
                vDec("1e$maxp+55e$minp"),
                vDec("1e$maxp+122e$minp"),
                vDec("1e$maxp+123e$minp"),
                vDec("1e$maxp+124e$minp"),
                vDec("1e$maxp+456e$minp")
        )

        // Mixed integer, big_integer and decimal.
        chkOpEq(vInt(123), vDec("123"), true)
        chkOpEq(vInt(123), vDec("321"), false)
        chkOpEq(vBigInt(123), vDec("123"), true)
        chkOpEq(vBigInt(123), vDec("321"), false)
        chkCmpCommon(vDec("55"), vInt(122), vDec("123"), vInt(124), vDec("456"))
        chkCmpCommon(vInt(55), vDec("122"), vInt(123), vDec("124"), vInt(456))
        chkCmpCommon(vDec("55"), vBigInt(122), vDec("123"), vInt(124), vDec("456"))
        chkCmpCommon(vBigInt(55), vDec("122"), vInt(123), vDec("124"), vBigInt(456))
    }

    @Test fun testCmpRowid() {
        chkCmpCommon(vRowid(55), vRowid(122), vRowid(123), vRowid(124), vRowid(456))
    }

    @Test fun testCmpText() {
        chkCmpCommon(vText("Apple"), vText("Helln"), vText("Hello"), vText("Hellp"), vText("World"))
        chkCmpCommon(vText("HELLO"), vText("Hello"))
        chkCmpCommon(vText("Hello"), vText("hello"))
    }

    @Test fun testCmpByteArray() {
        chkCmpCommon(vBytes("0000"), vBytes("0122"), vBytes("0123"), vBytes("0124"), vBytes("abcd"))
        chkCmpCommon(vBytes("beef"), vBytes("cafd"), vBytes("cafe"), vBytes("caff"), vBytes("dead"))
        chkCmpCommon(vBytes("0123"), vBytes("abcd"))
        chkCmpCommon(vBytes("0123"), vBytes("0123abcd"))
        chkCmpCommon(vBytes("0123"), vBytes("02"))
        chkCmpCommon(vBytes("01"), vBytes("0123"))
        chkCmpCommon(vBytes("00"), vBytes("ff"))
        chkCmpCommon(vBytes(""), vBytes("0123abcd"))
    }

    @Test fun testCmpObject() {
        chkCmpCommon(vObj("user", 1000), vObj("user", 2000))
    }

    private fun chkCmpCommon(vLow: TstVal, vMinusOne: TstVal, v: TstVal, vPlusOne: TstVal, vHigh: TstVal) {
        chkOpBool("==", v, v, true)
        chkOpBool("==", v, vLow, false)
        chkOpBool("==", v, vHigh, false)
        chkOpBool("==", v, vMinusOne, false)
        chkOpBool("==", v, vPlusOne, false)

        chkOpBool("!=", v, v, false)
        chkOpBool("!=", v, vLow, true)
        chkOpBool("!=", v, vHigh, true)
        chkOpBool("!=", v, vMinusOne, true)
        chkOpBool("!=", v, vPlusOne, true)

        chkOpBool("<", v, vLow, false)
        chkOpBool("<", v, vMinusOne, false)
        chkOpBool("<", v, v, false)
        chkOpBool("<", v, vPlusOne, true)
        chkOpBool("<", v, vHigh, true)

        chkOpBool("<=", v, vLow, false)
        chkOpBool("<=", v, vMinusOne, false)
        chkOpBool("<=", v, v, true)
        chkOpBool("<=", v, vPlusOne, true)
        chkOpBool("<=", v, vHigh, true)

        chkOpBool(">", v, vLow, true)
        chkOpBool(">", v, vMinusOne, true)
        chkOpBool(">", v, v, false)
        chkOpBool(">", v, vPlusOne, false)
        chkOpBool(">", v, vHigh, false)

        chkOpBool(">=", v, vLow, true)
        chkOpBool(">=", v, vMinusOne, true)
        chkOpBool(">=", v, v, true)
        chkOpBool(">=", v, vPlusOne, false)
        chkOpBool(">=", v, vHigh, false)
    }

    private fun chkCmpCommon(v1: TstVal, v2: TstVal) {
        chkOpEq(v1, v2, false)

        chkOpBool("<", v1, v1, false)
        chkOpBool("<", v2, v2, false)
        chkOpBool("<", v1, v2, true)
        chkOpBool("<", v2, v1, false)

        chkOpBool("<=", v1, v1, true)
        chkOpBool("<=", v2, v2, true)
        chkOpBool("<=", v1, v2, true)
        chkOpBool("<=", v2, v1, false)

        chkOpBool(">", v1, v1, false)
        chkOpBool(">", v2, v2, false)
        chkOpBool(">", v1, v2, false)
        chkOpBool(">", v2, v1, true)

        chkOpBool(">=", v1, v1, true)
        chkOpBool(">=", v2, v2, true)
        chkOpBool(">=", v1, v2, false)
        chkOpBool(">=", v2, v1, true)
    }

    private fun chkOpEq(v1: TstVal, v2: TstVal, eq: Boolean) {
        chkOpBool("==", v1, v1, true)
        chkOpBool("==", v2, v2, true)
        chkOpBool("==", v1, v2, eq)
        chkOpBool("==", v2, v1, eq)

        chkOpBool("!=", v1, v1, false)
        chkOpBool("!=", v2, v2, false)
        chkOpBool("!=", v1, v2, !eq)
        chkOpBool("!=", v2, v1, !eq)
    }

    @Test fun testCmpErr() {
        chkCmpErr("==")
        chkCmpErr("!=")
        chkCmpErr("<")
        chkCmpErr(">")
        chkCmpErr("<=")
        chkCmpErr(">=")
    }

    private fun chkCmpErr(op: String) {
        chkOpErr("boolean $op integer")
        chkOpErr("boolean $op decimal")
        chkOpErr("boolean $op text")
        chkOpErr("boolean $op rowid")
        chkOpErr("boolean $op user")
        chkOpErr("integer $op boolean")
        chkOpErr("integer $op text")
        chkOpErr("integer $op rowid")
        chkOpErr("integer $op user")
        chkOpErr("big_integer $op boolean")
        chkOpErr("big_integer $op text")
        chkOpErr("decimal $op boolean")
        chkOpErr("decimal $op text")
        chkOpErr("text $op boolean")
        chkOpErr("text $op integer")
        chkOpErr("text $op decimal")
        chkOpErr("text $op rowid")
        chkOpErr("text $op user")
        chkOpErr("user $op boolean")
        chkOpErr("user $op integer")
        chkOpErr("user $op decimal")
        chkOpErr("user $op text")
        chkOpErr("user $op rowid")
        chkOpErr("user $op company")
    }

    @Test fun testAnd() {
        chkOpBool("and", vBool(false), vBool(false), false)
        chkOpBool("and", vBool(false), vBool(true), false)
        chkOpBool("and", vBool(true), vBool(false), false)
        chkOpBool("and", vBool(true), vBool(true), true)
    }

    @Test fun testOr() {
        chkOpBool("or", vBool(false), vBool(false), false)
        chkOpBool("or", vBool(false), vBool(true), true)
        chkOpBool("or", vBool(true), vBool(false), true)
        chkOpBool("or", vBool(true), vBool(true), true)
    }

    @Test fun testNot() {
        chkOpBool("not", vBool(false), true)
        chkOpBool("not", vBool(true), false)
    }

    @Test fun testPlusInteger() {
        chkSymOp("+", vInt(123), vInt(456), "int[579]")
        chkSymOp("+", vInt(12345), vInt(67890), "int[80235]")
        chkSymOp("+", vInt(9223372036854775806), vInt(1), "int[9223372036854775807]")
        chkIntOverflow("+", 9223372036854775807, 1)
    }

    @Test fun testPlusBigInteger() {
        chkBigIntegerCases(BigIntegerTest.ADD_TEST_CASES)
    }

    @Test fun testPlusBigIntegerMixed() {
        chkBigIntSymOp("+", 456, "123", "bigint[579]")
        chkBigIntSymOp("+", 123, "456", "bigint[579]")
        chkBigIntSymOp("+", 67890, "12345", "bigint[80235]")
        chkBigIntSymOp("+", 12345, "67890", "bigint[80235]")
    }

    @Test fun testPlusBigIntegerExtreme() {
        val v = BIV
        chkBigIntSymOp("+", 1, "${v.lim2}", "bigint[${v.lim1}]")
        chkBigIntSymOp("+", -1, "-${v.lim2}", "bigint[-${v.lim1}]")
        chkBigIntOverflow("+", vBigInt("${v.lim2}"), vInt(2))
        chkBigIntOverflow("+", vBigInt("-${v.lim2}"), vInt(-2))
    }

    private fun chkBigIntegerCases(cases: List<DecimalTest.DecAddCase>) {
        for (case in cases) {
            if (case.expected != null) {
                chkOp(case.op, vBigInt(case.a), vBigInt(case.b), "bigint[${case.expected}]")
            } else {
                chkBigIntOverflow(case.op, vBigInt(case.a), vBigInt(case.b))
            }
        }
    }

    @Test fun testPlusDecimal() {
        chkDecimalCases(DecimalTest.ADD_TEST_CASES)
    }

    @Test fun testPlusDecimalMixed() {
        chkDecSymOp("+", 456, "123", "dec[579]")
        chkDecSymOp("+", 123, "456", "dec[579]")
        chkDecSymOp("+", 67890, "12345", "dec[80235]")
        chkDecSymOp("+", 12345, "67890", "dec[80235]")
    }

    @Test fun testPlusDecimalExtreme() {
        val d = DV
        chkDecSymOp("+", 1, "${d.lim2}", "dec[${d.lim1}]")
        chkDecSymOp("+", -1, "-${d.lim2}", "dec[-${d.lim1}]")
        chkDecOverflow("+", vDec("${d.lim2}"), vInt(2))
        chkDecOverflow("+", vDec("-${d.lim2}"), vInt(-2))
    }

    private fun chkDecimalCases(cases: List<DecimalTest.DecAddCase>) {
        for (case in cases) {
            if (case.expected != null) {
                chkOp(case.op, vDec(case.a), vDec(case.b), "dec[${case.expected}]")
            } else {
                chkDecOverflow(case.op, vDec(case.a), vDec(case.b))
            }
        }
    }

    @Test fun testPlusSpecial() {
        chkOp("+", vText("Hello"), vText("World"), "text[HelloWorld]")
        chkOp("+", vBytes("0123456789"), vBytes("abcdef"), "byte_array[0123456789abcdef]")

        chkPlusText("Hello", vBool(true), "true")
        chkPlusText("Hello", vInt(123), "123")
        chkPlusText("Hello", vBigInt(123), "123")
        chkPlusText("Hello", vDec("123.456"), "123.456")
        chkPlusText("Hello", vJson("[{}]"), "[{}]")
        chkPlusText("Hello", vRowid(123), "123")
        //chkPlusText("Hello", vObj("user", 1000), "user[1000]")
    }

    private fun chkPlusText(a: String, b: TstVal, bs: String) {
        chkOp("+", vText(a), b, "text[$a$bs]")
        chkOp("+", b, vText(a), "text[$bs$a]")
    }

    @Test fun testPlusUnary() {
        chkOp("+", vInt(123), "int[123]")
        chkOp("+", vInt(-123), "int[-123]")

        chkOp("+", vBigInt(123), "bigint[123]")
        chkOp("+", vBigInt(-123), "bigint[-123]")

        chkOp("+", vDec("123.456"), "dec[123.456]")
        chkOp("+", vDec("-123.456"), "dec[-123.456]")
    }

    @Test fun testMinusInteger() {
        chkOp("-", vInt(123), vInt(456), "int[-333]")
        chkOp("-", vInt(456), vInt(123), "int[333]")
        chkOp("-", vInt(12345), vInt(67890), "int[-55545]")
        chkOp("-", vInt(67890), vInt(12345), "int[55545]")
        chkOp("-", vInt(-9223372036854775807), vInt(1), "int[-9223372036854775808]")
        chkIntOverflow("-", -9223372036854775807-1, 1)
    }

    @Test fun testMinusBigInteger() {
        chkBigIntegerCases(BigIntegerTest.SUB_TEST_CASES)
    }

    @Test fun testMinusBigIntegerMixed() {
        chkBigIntOp("-", 12345, 67890, "bigint[-55545]")
        chkBigIntOp("-", 12345, 67890, "bigint[-55545]")
        chkBigIntOp("-", 67890, 12345, "bigint[55545]")
        chkBigIntOp("-", 67890, 12345, "bigint[55545]")
    }

    @Test fun testMinusBigIntegerExtreme() {
        val v = BIV
        chkBigIntOp("-", "-${v.lim2}", 1, "bigint[-${v.lim1}]")
        chkBigIntOverflow("-", vBigInt("-${v.lim2}"), vInt(2))
        chkBigIntOp("-", "${v.lim2}", -1, "bigint[${v.lim1}]")
        chkBigIntOverflow("-", vBigInt("${v.lim2}"), vInt(-2))
    }

    @Test fun testMinusDecimal() {
        chkDecimalCases(DecimalTest.SUB_TEST_CASES)
    }

    @Test fun testMinusDecimalMixed() {
        chkDecOp("-", 12345, 67890, "dec[-55545]")
        chkDecOp("-", 12345, 67890, "dec[-55545]")
        chkDecOp("-", 67890, 12345, "dec[55545]")
        chkDecOp("-", 67890, 12345, "dec[55545]")
    }

    @Test fun testMinusDecimalExtreme() {
        val d = DV
        chkDecOp("-", "-${d.lim2}", 1, "dec[-${d.lim1}]")
        chkDecOverflow("-", vDec("-${d.lim2}"), vInt(2))
        chkDecOp("-", "${d.lim2}", -1, "dec[${d.lim1}]")
        chkDecOverflow("-", vDec("${d.lim2}"), vInt(-2))
    }

    @Test fun testMinusUnary() {
        chkOp("-", vInt(123), "int[-123]")
        chkOp("-", vInt(-123), "int[123]")
        chkOp("-", vInt(Long.MIN_VALUE), rtErr("expr:-:overflow:-9223372036854775808"))

        chkOp("-", vBigInt(123), "bigint[-123]")
        chkOp("-", vBigInt(-123), "bigint[123]")
        chkOp("-", vBigInt(Long.MIN_VALUE), "bigint[9223372036854775808]")

        chkOp("-", vDec("123"), "dec[-123]")
        chkOp("-", vDec("-123"), "dec[123]")
        chkOp("-", vDec("123.456"), "dec[-123.456]")
        chkOp("-", vDec("-123.456"), "dec[123.456]")
    }

    @Test fun testMulInteger() {
        chkSymOp("*", vInt(123), vInt(456), "int[56088]")
        chkSymOp("*", vInt(123), vInt(0), "int[0]")
        chkSymOp("*", vInt(123), vInt(1), "int[123]")
        chkSymOp("*", vInt(1), vInt(456), "int[456]")
        chkSymOp("*", vInt(-1), vInt(456), "int[-456]")
        chkSymOp("*", vInt(4294967296-1), vInt(2147483648), "int[9223372034707292160]")
        chkIntOverflow("*", 4294967296, 2147483648)
    }

    @Test fun testMulBigInteger() {
        chkBigIntSymOp("*", 123, "456", "bigint[56088]")
        chkBigIntSymOp("*", 0, "456", "bigint[0]")
        chkBigIntSymOp("*", 1, "456", "bigint[456]")
        chkBigIntSymOp("*", -1, "456", "bigint[-456]")
        chkBigIntSymOp("*", 4294967295, "2147483648", "bigint[9223372034707292160]")
        chkBigIntSymOp("*", 4294967296, "2147483648", "bigint[9223372036854775808]")
        chkBigIntSymOp("*", 9223372036854775807, "9223372036854775808", "bigint[85070591730234615856620279821087277056]")
    }

    @Test fun testMulBigIntegerMixed() {
        chkBigIntSymOp("*", 456, "123", "bigint[56088]")
        chkBigIntSymOp("*", 123, "456", "bigint[56088]")
        chkBigIntSymOp("*", 0, "123", "bigint[0]")
        chkBigIntSymOp("*", 123, "0", "bigint[0]")
        chkBigIntSymOp("*", 1, "123", "bigint[123]")
        chkBigIntSymOp("*", 123, "1", "bigint[123]")
        chkBigIntSymOp("*", 456, "-1", "bigint[-456]")
        chkBigIntSymOp("*", -1, "456", "bigint[-456]")
        chkBigIntSymOp("*", 2147483648, "4294967296", "bigint[9223372036854775808]")
        chkBigIntSymOp("*", 4294967296, "2147483648", "bigint[9223372036854775808]")
        chkBigIntSymOp("*", 9223372036854775807, "9223372036854775808", "bigint[85070591730234615856620279821087277056]")
    }

    @Test fun testMulBigIntegerExtreme() {
        val v = BIV
        chkSymOp("*", vBigInt("${v.limDiv10}"), vBigInt("2"), "bigint[2${"0".repeat(v.digs-1)}]")
        chkSymOp("*", vBigInt("${v.limDiv10}"), vBigInt("5"), "bigint[5${"0".repeat(v.digs-1)}]")
        chkSymOp("*", vBigInt("${v.limDiv10}"), vBigInt("9"), "bigint[9${"0".repeat(v.digs-1)}]")
        chkBigIntOverflow("*", vBigInt("${v.limDiv10}"), vBigInt("10"))
    }

    @Test fun testMulDecimal() {
        chkDecSymOp("*", 123, "456", "dec[56088]")
        chkDecSymOp("*", 123, "0", "dec[0]")
        chkDecSymOp("*", 0, "456", "dec[0]")
        chkDecSymOp("*", 123, "1", "dec[123]")
        chkDecSymOp("*", 1, "456", "dec[456]")
        chkDecSymOp("*", -1, "456", "dec[-456]")
        chkDecSymOp("*", 4294967295, "2147483648", "dec[9223372034707292160]")
        chkDecSymOp("*", 4294967296, "2147483648", "dec[9223372036854775808]")
        chkDecSymOp("*", 9223372036854775807, "9223372036854775808", "dec[85070591730234615856620279821087277056]")
    }

    @Test fun testMulDecimalMixed() {
        chkDecSymOp("*", 123, "456", "dec[56088]")
        chkDecSymOp("*", 0, "123", "dec[0]")
        chkDecSymOp("*", 123, "0", "dec[0]")
        chkDecSymOp("*", 1, "123", "dec[123]")
        chkDecSymOp("*", 123, "1", "dec[123]")
        chkDecSymOp("*", 456, "-1", "dec[-456]")
        chkDecSymOp("*", -1, "456", "dec[-456]")
        chkDecSymOp("*", 2147483648, "4294967296", "dec[9223372036854775808]")
        chkDecSymOp("*", 4294967296, "2147483648", "dec[9223372036854775808]")
        chkDecSymOp("*", 9223372036854775807, "9223372036854775808", "dec[85070591730234615856620279821087277056]")
    }

    @Test fun testMulDecimalExtreme() {
        val d = DV
        chkDecOverflow("*", vDec("${d.limDiv10}"), vInt(10))
        chkDecOverflow("*", vDec("${d.limDiv10}"), vBigInt(10))

        val exp = "99999" + "0".repeat(d.intDigs - 5)
        chkOp("*", vDec("${d.limDiv10}"), vDec("9.9999"), "dec[$exp]")
        chkDecOverflow("*", vDec("${d.limDiv10}"), vDec("10"))
    }

    @Test fun testMulDecimalRounding() {
        val d = DV
        chkOp("*", vDec("0.${d.frac0}50"), vDec("0.1"), "dec[0.${d.frac0}05]")
        chkOp("*", vDec("0.${d.frac0}54"), vDec("0.1"), "dec[0.${d.frac0}05]")
        chkOp("*", vDec("0.${d.frac0}55"), vDec("0.1"), "dec[0.${d.frac0}06]")
        chkOp("*", vDec("0.${d.frac0}59"), vDec("0.1"), "dec[0.${d.frac0}06]")
    }

    @Test fun testDivInteger() {
        chkOp("/", vInt(123), vInt(456), "int[0]")
        chkOp("/", vInt(456), vInt(123), "int[3]")
        chkOp("/", vInt(1000000), vInt(1), "int[1000000]")
        chkOp("/", vInt(1000000), vInt(2), "int[500000]")
        chkOp("/", vInt(1000000), vInt(9), "int[111111]")
        chkOp("/", vInt(1000000), vInt(10), "int[100000]")
        chkOp("/", vInt(1000000), vInt(11), "int[90909]")
        chkOp("/", vInt(1000000), vInt(333333), "int[3]")
        chkOp("/", vInt(1000000), vInt(333334), "int[2]")
        chkOp("/", vInt(1000000), vInt(499999), "int[2]")
        chkOp("/", vInt(1000000), vInt(500000), "int[2]")
        chkOp("/", vInt(1000000), vInt(500001), "int[1]")
        chkOp("/", vInt(1), vInt(0), rtErr("expr:/:div0:1"))
        chkOp("/", vInt(123456789), vInt(0), rtErr("expr:/:div0:123456789"))

        chkOp("/", vInt(-1000000), vInt(11), "int[-90909]")
        chkOp("/", vInt(1000000), vInt(-11), "int[-90909]")
        chkOp("/", vInt(-1000000), vInt(-11), "int[90909]")
    }

    @Test fun testDivBigInteger() {
        chkBigIntOp("/", 123, 456, "bigint[0]")
        chkBigIntOp("/", 456, 123, "bigint[3]")
        chkBigIntOp("/", 1000000, 1, "bigint[1000000]")
        chkBigIntOp("/", 1000000, 2, "bigint[500000]")
        chkBigIntOp("/", 1000000, 9, "bigint[111111]")
        chkBigIntOp("/", 1000000, 10, "bigint[100000]")
        chkBigIntOp("/", 1000000, 11, "bigint[90909]")
        chkBigIntOp("/", 1000000, 333333, "bigint[3]")
        chkBigIntOp("/", 1000000, 333334, "bigint[2]")
        chkBigIntOp("/", 1000000, 499999, "bigint[2]")
        chkBigIntOp("/", 1000000, 500000, "bigint[2]")
        chkBigIntOp("/", 1000000, 500001, "bigint[1]")
        chkBigIntOp("/", 1, 0, rtErr("expr:/:div0:1"))
        chkBigIntOp("/", 123456789, 0, rtErr("expr:/:div0:123456789"))

        chkBigIntOp("/", -1000000, 11, "bigint[-90909]")
        chkBigIntOp("/", 1000000, -11, "bigint[-90909]")
        chkBigIntOp("/", -1000000, -11, "bigint[90909]")
        chkBigIntOp("/", -1000000, 333333, "bigint[-3]")
        chkBigIntOp("/", 1000000, -333333, "bigint[-3]")
        chkBigIntOp("/", -1000000, -333333, "bigint[3]")
        chkBigIntOp("/", -1000000, 333334, "bigint[-2]")
        chkBigIntOp("/", 1000000, -333334, "bigint[-2]")
        chkBigIntOp("/", -1000000, -333334, "bigint[2]")
    }

    @Test fun testDivBigIntegerExtreme() {
        val v = BIV
        val max = BigInteger(v.lim1)
        chkOp("/", vBigInt(max), vBigInt(1), "bigint[$max]")
        chkOp("/", vBigInt(max), vBigInt(2), "bigint[${max / BigInteger.valueOf(2)}]")
        chkOp("/", vBigInt(max), vBigInt(3), "bigint[${max / BigInteger.valueOf(3)}]")
    }

    @Test fun testDivDecimal() {
        chkDecOp("/", 42883369, 7717, "dec[5557]")
        chkDecOp("/", 42883369, 5557, "dec[7717]")
        chkDecOp("/", 123456789, 0, rtErr("expr:/:div0"))

        chkDecOp("/", 123, 456, "dec[0.26973684210526315789]")
        chkDecOp("/", 456, 123, "dec[3.70731707317073170732]")
        chkDecOp("/", 1, 7, "dec[0.14285714285714285714]")
        chkOp("/", vDec("12.34"), vDec("56.78"), "dec[0.21733004579077139838]")
        chkOp("/", vDec("56.78"), vDec("12.34"), "dec[4.60129659643435980551]")

        chkDecOp("/", -456, 123, "dec[-3.70731707317073170732]")
        chkDecOp("/", 456, -123, "dec[-3.70731707317073170732]")
        chkDecOp("/", -456, -123, "dec[3.70731707317073170732]")
    }

    @Test fun testDivDecimalMixed() {
        chkDecOp("/", 42883369, 7717, "dec[5557]")
        chkDecOp("/", 42883369, 5557, "dec[7717]")
        chkDecOp("/", 123456789, 0, rtErr("expr:/:div0"))
        chkDecOp("/", 123, 456, "dec[0.26973684210526315789]")
        chkDecOp("/", 456, 123, "dec[3.70731707317073170732]")
    }

    @Test fun testDivDecimalExtreme() {
        val d = DV
        chkOp("/", vDec("1e${d.intDigs-2}"), vDec("0.1"), "dec[${d.limDiv10}]")
        chkOp("/", vDec("1e${d.intDigs-2}"), vDec("0.01"), rtErr("expr:/:overflow"))
        chkOp("/", vDec("1e${d.intDigs-1}"), vDec("0.1"), rtErr("expr:/:overflow"))
    }

    @Test fun testDivDecimalRounding() {
        val d = DV
        chkOp("/", vDec("0.${d.frac0}77"), vDec("2"), "dec[0.${d.frac0}39]")
        chkOp("/", vDec("0.${d.frac0}77"), vDec("3"), "dec[0.${d.frac0}26]")
        chkOp("/", vDec("0.${d.frac0}77"), vDec("4"), "dec[0.${d.frac0}19]")
        chkOp("/", vDec("0.${d.frac0}77"), vDec("5"), "dec[0.${d.frac0}15]")
        chkOp("/", vDec("0.${d.frac0}77"), vDec("6"), "dec[0.${d.frac0}13]")
        chkOp("/", vDec("0.${d.frac0}77"), vDec("7"), "dec[0.${d.frac0}11]")
        chkOp("/", vDec("0.${d.frac0}50"), vDec("10"), "dec[0.${d.frac0}05]")
        chkOp("/", vDec("0.${d.frac0}54"), vDec("10"), "dec[0.${d.frac0}05]")
        chkOp("/", vDec("0.${d.frac0}55"), vDec("10"), "dec[0.${d.frac0}06]")
        chkOp("/", vDec("0.${d.frac0}59"), vDec("10"), "dec[0.${d.frac0}06]")
    }

    @Test fun testModInteger() {
        chkOp("%", vInt(123), vInt(456), "int[123]")
        chkOp("%", vInt(456), vInt(123), "int[87]")
        chkOp("%", vInt(1000000), vInt(2), "int[0]")
        chkOp("%", vInt(1000000), vInt(3), "int[1]")
        chkOp("%", vInt(1000000), vInt(9999), "int[100]")

        chkOp("%", vInt(111), vInt(77), "int[34]")
        chkOp("%", vInt(-111), vInt(77), "int[-34]")
        chkOp("%", vInt(111), vInt(-77), "int[34]")
        chkOp("%", vInt(-111), vInt(-77), "int[-34]")

        chkOp("%", vInt(123), vInt(0), rtErr("expr:%:div0:123"))
    }

    @Test fun testModBigInteger() {
        chkBigIntOp("%", 123, 456, "bigint[123]")
        chkBigIntOp("%", 456, 123, "bigint[87]")
        chkBigIntOp("%", 1000000, 2, "bigint[0]")
        chkBigIntOp("%", 1000000, 3, "bigint[1]")
        chkBigIntOp("%", 1000000, 9999, "bigint[100]")
        chkBigIntOp("%", "79228162514264337593543950335", 1234567, "bigint[801782]")

        chkBigIntOp("%", 111, 77, "bigint[34]")
        chkBigIntOp("%", -111, 77, "bigint[-34]")
        chkBigIntOp("%", 111, -77, "bigint[34]")
        chkBigIntOp("%", -111, -77, "bigint[-34]")

        chkBigIntOp("%", 123, 0, rtErr("expr:%:div0"))
    }

    @Test fun testModDecimal() {
        chkDecOp("%", 123, 456, "dec[123]")
        chkDecOp("%", 456, 123, "dec[87]")
        chkDecOp("%", 123, 123, "dec[0]")
        chkDecOp("%", 123456789, 123456789, "dec[0]")
        chkDecOp("%", 123456789, 123456788, "dec[1]")
        chkDecOp("%", "79228162514264337593543950335", 1234567, "dec[801782]")

        chkDecOp("%", 111, 77, "dec[34]")
        chkDecOp("%", -111, 77, "dec[-34]")
        chkDecOp("%", 111, -77, "dec[34]")
        chkDecOp("%", -111, -77, "dec[-34]")

        chkOp("%", vDec("12.34"), vDec("56.78"), "dec[12.34]")
        chkOp("%", vDec("56.78"), vDec("12.34"), "dec[7.42]")
        chkOp("%", vDec("0.123456789"), vDec("0.123456789"), "dec[0]")
        chkOp("%", vDec("0.123456789"), vDec("0.123456788"), "dec[0.000000001]")

        chkDecOp("%", "12.34", 0, rtErr("expr:%:div0"))
    }

    private fun chkBigIntOp(op: String, a: Long, b: Long, exp: String) {
        chkOp(op, vInt(a), vBigInt(b), exp)
        chkOp(op, vBigInt(a), vInt(b), exp)
        chkOp(op, vBigInt(a), vBigInt(b), exp)
    }

    private fun chkBigIntOp(op: String, a: Long, b: String, exp: String) {
        chkOp(op, vInt(a), vBigInt(b), exp)
        chkOp(op, vBigInt(a), vBigInt(b), exp)
    }

    private fun chkBigIntOp(op: String, a: String, b: Long, exp: String) {
        chkOp(op, vBigInt(a), vInt(b), exp)
        chkOp(op, vBigInt(a), vBigInt(b), exp)
    }

    private fun chkBigIntSymOp(op: String, a: Long, b: String, exp: String) {
        chkOp(op, vInt(a), vBigInt(b), exp)
        chkOp(op, vBigInt(b), vInt(a), exp)
        chkOp(op, vBigInt(a), vBigInt(b), exp)
        chkOp(op, vBigInt(b), vBigInt(a), exp)
    }

    private fun chkIntOverflow(op: String, left: Long, right: Long) {
        chkOp(op, vInt(left), vInt(right), rtErr("expr:$op:overflow:$left:$right"))
        chkOp(op, vInt(right), vInt(left), rtErr("expr:$op:overflow:$right:$left"))
    }

    private fun chkBigIntOverflow(op: String, left: TstVal, right: TstVal) {
        chkOp(op, left, right, rtErr("expr:$op:overflow"))
        chkOp(op, right, left, rtErr("expr:$op:overflow"))
    }

    private fun chkDecOp(op: String, a: Long, b: Long, exp: String) {
        chkOp(op, vInt(a), vDec(b), exp)
        chkOp(op, vDec(a), vInt(b), exp)
        chkOp(op, vBigInt(a), vDec(b), exp)
        chkOp(op, vDec(a), vBigInt(b), exp)
        chkOp(op, vDec(a), vDec(b), exp)
    }

    private fun chkDecOp(op: String, a: String, b: Long, exp: String) {
        chkOp(op, vDec(a), vInt(b), exp)
        chkOp(op, vDec(a), vBigInt(b), exp)
        chkOp(op, vDec(a), vDec(b), exp)
    }

    private fun chkDecSymOp(op: String, a: Long, b: String, exp: String) {
        chkOp(op, vInt(a), vDec(b), exp)
        chkOp(op, vDec(b), vInt(a), exp)
        chkOp(op, vBigInt(a), vDec(b), exp)
        chkOp(op, vDec(b), vBigInt(a), exp)
        chkOp(op, vDec(a), vDec(b), exp)
        chkOp(op, vDec(b), vDec(a), exp)
    }

    private fun chkDecOverflow(op: String, left: TstVal, right: TstVal) {
        chkOp(op, left, right, rtErr("expr:$op:overflow"))
        chkOp(op, right, left, rtErr("expr:$op:overflow"))
    }

    @Test fun testErr() {
        chkOpErr("boolean + integer")
        chkOpErr("boolean + decimal")
        chkOpErr("boolean + user")
        chkOpErr("integer + boolean")
        chkOpErr("integer + user")
        chkOpErr("big_integer + boolean")
        chkOpErr("big_integer + user")
        chkOpErr("decimal + boolean")
        chkOpErr("decimal + user")
        chkOpErr("user + boolean")
        chkOpErr("user + integer")
        chkOpErr("user + company")

        chkErrSub("-")
        chkErrSub("*")
        chkErrSub("/")
        chkErrSub("%")
        chkErrSub("and")
        chkErrSub("or")

        chkErrSubBin("+", "integer", "boolean", "user")
        chkErrSubBin("-", "integer", "boolean", "text", "user")
        chkErrSubBin("*", "integer", "boolean", "text", "user")
        chkErrSubBin("/", "integer", "boolean", "text", "user")
        chkErrSubBin("%", "integer", "boolean", "text", "user")

        chkErrSubBin("and", "boolean", "integer", "decimal", "text", "user")
        chkErrSubBin("or", "boolean", "integer", "decimal", "text", "user")

        chkErrSubUn("+", "boolean", "text", "user", "rowid")
        chkErrSubUn("-", "boolean", "text", "user", "rowid")
        chkErrSubUn("not", "integer", "decimal", "text", "user", "rowid")
    }

    private fun chkErrSub(op: String) {
        chkOpErr("boolean $op integer")
        chkOpErr("boolean $op decimal")
        chkOpErr("boolean $op text")
        chkOpErr("boolean $op rowid")
        chkOpErr("boolean $op user")

        chkOpErr("integer $op boolean")
        chkOpErr("integer $op text")
        chkOpErr("integer $op rowid")
        chkOpErr("integer $op user")

        chkOpErr("text $op boolean")
        chkOpErr("text $op integer")
        chkOpErr("text $op decimal")
        chkOpErr("text $op rowid")
        chkOpErr("text $op user")

        chkOpErr("user $op boolean")
        chkOpErr("user $op integer")
        chkOpErr("user $op decimal")
        chkOpErr("user $op rowid")
        chkOpErr("user $op text")
        chkOpErr("user $op company")

        chkOpErr("rowid $op boolean")
        chkOpErr("rowid $op integer")
        chkOpErr("rowid $op decimal")
        chkOpErr("rowid $op text")
        chkOpErr("rowid $op user")
    }

    private fun chkErrSubBin(op: String, goodType: String, vararg badTypes: String) {
        for (badType in badTypes) {
            chkOpErr(op, goodType, badType)
            chkOpErr(op, badType, goodType)
            chkOpErr(op, badType, badType)
        }
    }

    private fun chkErrSubUn(op: String, vararg types: String) {
        for (type in types) {
            chkOpErr(op, type)
        }
    }

    @Test fun testFnJson() {
        chkExpr("json(#0)", """json[{"a":10,"b":[1,2,3],"c":{"x":999}}]""",
                vText("""{ "a" : 10, "b" : [1, 2, 3], "c" : { "x" : 999 } }"""))

        chkExpr("#0.str()", "text[{}]", vJson("{}"))
        chkExpr("#0.str()", "text[[]]", vJson("[]"))
        chkExpr("#0.str()", "text[[12345]]", vJson("[12345]"))
        chkExpr("#0.str()", """text[["Hello"]]""", vJson("""["Hello"]"""))
    }

    @Test fun testIf() {
        chkExpr("if (#0) 1 else 2", "int[1]", vBool(true))
        chkExpr("if (#0) 1 else 2", "int[2]", vBool(false))
        chkExpr("if (#0) 'Yes' else 'No'", "text[Yes]", vBool(true))
        chkExpr("if (#0) 'Yes' else 'No'", "text[No]", vBool(false))

        chkExpr("if (#0) #1 else #2", "int[123]", vBool(true), vInt(123), vInt(456))
        chkExpr("if (#0) #1 else #2", "int[456]", vBool(false), vInt(123), vInt(456))
        chkExpr("if (#0) #1 else #2", "text[Yes]", vBool(true), vText("Yes"), vText("No"))
        chkExpr("if (#0) #1 else #2", "text[No]", vBool(false), vText("Yes"), vText("No"))

        chkExpr("if (true) 'Hello' else 123", "ct_err:expr_if_restype:[text]:[integer]")
        chkExpr("if (true) 'Hello' else decimal(123)", "ct_err:expr_if_restype:[text]:[decimal]")
        chkExpr("if (123) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[integer]")
        chkExpr("if (decimal(123)) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[decimal]")
        chkExpr("if ('Hello') 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[text]")
        chkExpr("if (null) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[null]")
        chkExpr("if (unit()) 'A' else 'B'", "ct_err:expr_if_cond_type:[boolean]:[unit]")
    }

    @Test fun testIfBigInteger() {
        chkExpr("if (#0) #1 else #2", "bigint[123]", vBool(true), vBigInt("123"), vInt(456))
        chkExpr("if (#0) #1 else #2", "bigint[123]", vBool(true), vInt(123), vBigInt("456"))
        chkExpr("if (#0) #1 else #2", "bigint[456]", vBool(false), vBigInt("123"), vInt(456))
        chkExpr("if (#0) #1 else #2", "bigint[456]", vBool(false), vInt(123), vBigInt("456"))
    }

    @Test fun testIfDecimal() {
        chkExpr("if (#0) #1 else #2", "dec[123]", vBool(true), vDec("123"), vInt(456))
        chkExpr("if (#0) #1 else #2", "dec[123]", vBool(true), vInt(123), vDec("456"))
        chkExpr("if (#0) #1 else #2", "dec[456]", vBool(false), vDec("123"), vInt(456))
        chkExpr("if (#0) #1 else #2", "dec[456]", vBool(false), vInt(123), vDec("456"))

        chkExpr("if (#0) #1 else #2", "dec[123]", vBool(true), vDec("123"), vBigInt(456))
        chkExpr("if (#0) #1 else #2", "dec[123]", vBool(true), vBigInt(123), vDec("456"))
        chkExpr("if (#0) #1 else #2", "dec[456]", vBool(false), vDec("123"), vBigInt(456))
        chkExpr("if (#0) #1 else #2", "dec[456]", vBool(false), vBigInt(123), vDec("456"))
    }

    @Test fun testWhenBigInteger() {
        chkWhenBigInteger(vBigInt("123"), vBigInt("456"), vBigInt("789"))

        chkWhenBigInteger(vInt(123), vBigInt("456"), vBigInt("789"))
        chkWhenBigInteger(vBigInt("123"), vInt(456), vBigInt("789"))
        chkWhenBigInteger(vBigInt("123"), vBigInt("456"), vInt(789))

        chkWhenBigInteger(vBigInt("123"), vInt(456), vInt(789))
        chkWhenBigInteger(vInt(123), vBigInt("456"), vInt(789))
        chkWhenBigInteger(vInt(123), vInt(456), vBigInt("789"))
    }

    private fun chkWhenBigInteger(v1: TstVal, v2: TstVal, v3: TstVal) {
        val expr = "when (#0) { 'A' -> #1; 'B' -> #2; else -> #3; }"
        chkExpr("_type_of($expr)", "text[big_integer]", vText(""), v1, v2, v3)
        chkExpr(expr, "bigint[123]", vText("A"), v1, v2, v3)
        chkExpr(expr, "bigint[456]", vText("B"), v1, v2, v3)
        chkExpr(expr, "bigint[789]", vText("C"), v1, v2, v3)
    }

    @Test fun testWhenDecimal() {
        chkWhenDecimal(vDec("123"), vDec("456"), vDec("789"))

        chkWhenDecimal(vBigInt(123), vDec("456"), vDec("789"))
        chkWhenDecimal(vDec("123"), vBigInt(456), vDec("789"))
        chkWhenDecimal(vDec("123"), vDec("456"), vBigInt(789))

        chkWhenDecimal(vDec("123"), vBigInt(456), vBigInt(789))
        chkWhenDecimal(vBigInt(123), vDec("456"), vBigInt(789))
        chkWhenDecimal(vBigInt(123), vBigInt(456), vDec("789"))

        chkWhenDecimal(vDec("123"), vBigInt(456), vInt(789))
        chkWhenDecimal(vDec("123"), vInt(456), vBigInt(789))
        chkWhenDecimal(vBigInt(123), vDec("456"), vInt(789))
        chkWhenDecimal(vInt(123), vDec("456"), vBigInt(789))
        chkWhenDecimal(vBigInt(123), vInt(456), vDec("789"))
        chkWhenDecimal(vInt(123), vBigInt(456), vDec("789"))

        chkWhenDecimal(vInt(123), vDec("456"), vDec("789"))
        chkWhenDecimal(vDec("123"), vInt(456), vDec("789"))
        chkWhenDecimal(vDec("123"), vDec("456"), vInt(789))

        chkWhenDecimal(vDec("123"), vInt(456), vInt(789))
        chkWhenDecimal(vInt(123), vDec("456"), vInt(789))
        chkWhenDecimal(vInt(123), vInt(456), vDec("789"))
    }

    private fun chkWhenDecimal(v1: TstVal, v2: TstVal, v3: TstVal) {
        val expr = "when (#0) { 'A' -> #1; 'B' -> #2; else -> #3; }"
        chkExpr("_type_of($expr)", "text[decimal]", vText(""), v1, v2, v3)
        chkExpr(expr, "dec[123]", vText("A"), v1, v2, v3)
        chkExpr(expr, "dec[456]", vText("B"), v1, v2, v3)
        chkExpr(expr, "dec[789]", vText("C"), v1, v2, v3)
    }

    @Test fun testSubscriptByteArray() {
        chkExpr("#0[#1]", "int[1]", vBytes("0123ABCD"), vInt(0))
        chkExpr("#0[#1]", "int[35]", vBytes("0123ABCD"), vInt(1))
        chkExpr("#0[#1]", "int[171]", vBytes("0123ABCD"), vInt(2))
        chkExpr("#0[#1]", "int[205]", vBytes("0123ABCD"), vInt(3))
        chkExpr("#0[#1]", rtErr("expr_bytearray_subscript_index:4:4"), vBytes("0123ABCD"), vInt(4))
        chkExpr("#0[#1]", rtErr("expr_bytearray_subscript_index:4:-1"), vBytes("0123ABCD"), vInt(-1))
    }

    @Test fun testSubscriptText() {
        chkExpr("#0[#1]", "text[H]", vText("Hello"), vInt(0))
        chkExpr("#0[#1]", "text[e]", vText("Hello"), vInt(1))
        chkExpr("#0[#1]", "text[l]", vText("Hello"), vInt(2))
        chkExpr("#0[#1]", "text[l]", vText("Hello"), vInt(3))
        chkExpr("#0[#1]", "text[o]", vText("Hello"), vInt(4))
        chkExpr("#0[#1]", rtErr("expr_text_subscript_index:5:-1"), vText("Hello"), vInt(-1))
        chkExpr("#0[#1]", rtErr("expr_text_subscript_index:5:5"), vText("Hello"), vInt(5))
    }

    private fun chkOpErr(expr: String) {
        val (left, op, right) = expr.split(" ")
        chkOpErr(op, left, right)
    }

    private fun chkOpErr(op: String, left: String, right: String) {
        chkExprErr("#0 $op #1", listOf(left, right), "ct_err:binop_operand_type:$op:[$left]:[$right]")
    }

    private fun chkOpErr(op: String, right: String) {
        chkExprErr("$op #0", listOf(right), "ct_err:unop_operand_type:$op:[$right]")
    }

    private fun chkOpBool(op: String, left: TstVal, right: TstVal, expected: Boolean) {
        chkExpr("#0 $op #1", "boolean[$expected]", left, right)
    }

    private fun chkOpBool(op: String, right: TstVal, expected: Boolean) {
        chkExpr("$op #0", "boolean[$expected]", right)
    }

    fun chkOp(op: String, left: TstVal, right: TstVal, expected: String) {
        chkExpr("#0 $op #1", expected, left, right)
    }

    fun chkSymOp(op: String, left: TstVal, right: TstVal, expected: String) {
        chkOp(op, left, right, expected)
        chkOp(op, right, left, expected)
    }

    fun chkOp(op: String, right: TstVal, expected: String) {
        chkExpr("$op #0", expected, right)
    }

    companion object {
        private val BIV = BigIntegerTest.BigIntVals()
        private val DV = DecimalTest.DecVals()
    }
}
