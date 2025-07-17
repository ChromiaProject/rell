/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseExprTest
import kotlin.test.Test

abstract class LibMathExprTest: BaseExprTest() {
    class LibMathExprIpTest: LibMathExprTest()
    class LibMathExprDbTest: LibMathExprTest()

    @Test fun testInteger() {
        chkExpr("_type_of(abs(#0))", "text[integer]", vInt(12345))
        chkExpr("abs(#0)", "int[12345]", vInt(12345))
        chkExpr("abs(#0)", "int[12345]", vInt(-12345))

        chkExpr("_type_of(min(#0, #1))", "text[integer]", vInt(12345), vInt(67890))
        chkExpr("min(#0, #1)", "int[12345]", vInt(12345), vInt(67890))
        chkExpr("min(#0, #1)", "int[12345]", vInt(67890), vInt(12345))

        chkExpr("_type_of(max(#0, #1))", "text[integer]", vInt(12345), vInt(67890))
        chkExpr("max(#0, #1)", "int[67890]", vInt(12345), vInt(67890))
        chkExpr("max(#0, #1)", "int[67890]", vInt(67890), vInt(12345))
    }

    @Test fun testBigInteger() {
        chkExpr("_type_of(abs(#0))", "text[big_integer]", vBigInt("123456"))
        chkExpr("abs(#0)", "bigint[123456]", vBigInt("123456"))
        chkExpr("abs(#0)", "bigint[123456]", vBigInt("-123456"))

        chkBigInt("min(#0, #1)", 123, 456, "bigint[123]")
        chkBigInt("max(#0, #1)", 123, 456, "bigint[456]")
    }

    private fun chkBigInt(expr: String, a: Long, b: Long, exp: String) {
        chkExpr("_type_of($expr)", "text[integer]", vInt(a), vInt(b))
        chkExpr("_type_of($expr)", "text[big_integer]", vInt(a), vBigInt(b))
        chkExpr("_type_of($expr)", "text[big_integer]", vBigInt(a), vInt(b))
        chkExpr("_type_of($expr)", "text[big_integer]", vBigInt(a), vBigInt(b))

        chkExpr(expr, exp, vBigInt(a), vInt(b))
        chkExpr(expr, exp, vBigInt(b), vInt(a))
        chkExpr(expr, exp, vInt(a), vBigInt(b))
        chkExpr(expr, exp, vInt(b), vBigInt(a))
        chkExpr(expr, exp, vBigInt(a), vBigInt(b))
        chkExpr(expr, exp, vBigInt(b), vBigInt(a))
    }

    @Test fun testDecimal() {
        chkExpr("_type_of(abs(#0))", "text[decimal]", vDec("123.456"))
        chkExpr("_type_of(abs(#0))", "text[decimal]", vDec("123456"))
        chkExpr("abs(#0)", "dec[123.456]", vDec("123.456"))
        chkExpr("abs(#0)", "dec[123.456]", vDec("-123.456"))

        chkExpr("_type_of(min(#0, #1))", "text[decimal]", vDec("12.34"), vDec("56.78"))
        chkExpr("_type_of(min(#0, #1))", "text[decimal]", vDec("1234"), vDec("5678"))
        chkExpr("min(#0, #1)", "dec[12.34]", vDec("12.34"), vDec("56.78"))
        chkExpr("min(#0, #1)", "dec[12.34]", vDec("56.78"), vDec("12.34"))

        chkExpr("_type_of(max(#0, #1))", "text[decimal]", vDec("12.34"), vDec("56.78"))
        chkExpr("_type_of(max(#0, #1))", "text[decimal]", vDec("1234"), vDec("5678"))
        chkExpr("max(#0, #1)", "dec[56.78]", vDec("12.34"), vDec("56.78"))
        chkExpr("max(#0, #1)", "dec[56.78]", vDec("56.78"), vDec("12.34"))
    }

    @Test fun testDecimalMixed() {
        chkDec("min(#0, #1)", 123, 456, "dec[123]")
        chkDec("max(#0, #1)", 123, 456, "dec[456]")
    }

    private fun chkDec(expr: String, a: Long, b: Long, exp: String) {
        chkExpr("_type_of($expr)", "text[integer]", vInt(a), vInt(b))
        chkExpr("_type_of($expr)", "text[decimal]", vDec(a), vBigInt(b))
        chkExpr("_type_of($expr)", "text[decimal]", vBigInt(a), vDec(b))
        chkExpr("_type_of($expr)", "text[decimal]", vDec(a), vInt(b))
        chkExpr("_type_of($expr)", "text[decimal]", vInt(a), vDec(b))

        chkExpr(expr, exp, vDec(a), vBigInt(b))
        chkExpr(expr, exp, vDec(b), vBigInt(a))
        chkExpr(expr, exp, vBigInt(a), vDec(b))
        chkExpr(expr, exp, vBigInt(b), vDec(a))
        chkExpr(expr, exp, vDec(a), vInt(b))
        chkExpr(expr, exp, vDec(b), vInt(a))
        chkExpr(expr, exp, vInt(a), vDec(b))
        chkExpr(expr, exp, vInt(b), vDec(a))
        chkExpr(expr, exp, vDec(a), vDec(b))
        chkExpr(expr, exp, vDec(b), vDec(a))
    }
}
