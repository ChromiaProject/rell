/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseExprTest
import net.postchain.rell.base.testutils.RellExprTester
import org.junit.Test

abstract class LibIntegerExprTest: BaseExprTest() {
    class LibIntegerExprIpTest: LibIntegerExprTest()
    class LibIntegerExprDbTest: LibIntegerExprTest()

    @Test fun testConstructorDecimal() {
        chkExpr("integer(#0)", "int[0]", vDec(0))
        chkExpr("integer(#0)", "int[123456]", vDec(123456))
        chkExpr("integer(#0)", "int[-123456]", vDec(-123456))
        chkExpr("integer(#0)", "int[123]", vDec("123.456"))
        chkExpr("integer(#0)", "int[-123]", vDec("-123.456"))
        chkExpr("integer(#0)", "int[123]", vDec("123.789"))
        chkExpr("integer(#0)", "int[-123]", vDec("-123.789"))
        chkExpr("integer(#0)", "int[9223372036854775807]", vDec("9223372036854775807"))
        chkExpr("integer(#0)", rtErr("decimal.to_integer:overflow:9223372036854775808"), vDec("9223372036854775808"))
        chkExpr("integer(#0)", "int[-9223372036854775808]", vDec("-9223372036854775808"))
        chkExpr("integer(#0)", rtErr("decimal.to_integer:overflow:-9223372036854775809"), vDec("-9223372036854775809"))
    }

    @Test fun testAbs() {
        chkExpr("#0.abs()", "int[0]", vInt(0))
        chkExpr("#0.abs()", "int[12345]", vInt(-12345))
        chkExpr("#0.abs()", "int[67890]", vInt(67890))
        chkExpr("#0.abs()", rtErr("abs:integer:overflow:-9223372036854775808"), vInt(Long.MIN_VALUE))
    }

    @Test fun testMinMax() {
        chkExpr("#0.min(#1)", "int[123]", vInt(123), vInt(456))
        chkExpr("#0.min(#1)", "int[123]", vInt(456), vInt(123))
        chkExpr("#0.max(#1)", "int[456]", vInt(123), vInt(456))
        chkExpr("#0.max(#1)", "int[456]", vInt(456), vInt(123))

        chkExpr("#0.min(#1)", "bigint[123]", vInt(123), vBigInt(456))
        chkExpr("#0.max(#1)", "bigint[456]", vInt(123), vBigInt(456))

        chkExpr("#0.min(#1)", "dec[123]", vInt(123), vDec("456"))
        chkExpr("#0.min(#1)", "dec[123]", vInt(456), vDec("123"))
        chkExpr("#0.max(#1)", "dec[456]", vInt(123), vDec("456"))
        chkExpr("#0.max(#1)", "dec[456]", vInt(456), vDec("123"))
    }

    @Test fun testSign() {
        chkExpr("#0.sign()", "int[0]", vInt(0))
        chkExpr("#0.sign()", "int[1]", vInt(12345))
        chkExpr("#0.sign()", "int[-1]", vInt(-12345))
    }

    @Test fun testToBigInteger() {
        chkExpr("#0.to_big_integer()", "bigint[0]", vInt(0))
        chkExpr("#0.to_big_integer()", "bigint[123456]", vInt(123456))
        chkExpr("#0.to_big_integer()", "bigint[-123456]", vInt(-123456))
    }

    @Test fun testToDecimal() {
        chkExpr("#0.to_decimal()", "dec[0]", vInt(0))
        chkExpr("#0.to_decimal()", "dec[123456]", vInt(123456))
        chkExpr("#0.to_decimal()", "dec[-123456]", vInt(-123456))
    }

    @Test fun testPow() {
        chkExpr("_type_of((0).pow(1))", "text[integer]")
        chkExpr("(0).pow(1L)", "ct_err:expr_call_badargs:[integer.pow]:[big_integer]")
        chkExpr("(0).pow(1.0)", "ct_err:expr_call_badargs:[integer.pow]:[decimal]")

        chkPowCommon("int", ::vInt)
    }

    fun chkPowCommon(type: String, vBase: (Long) -> RellExprTester.TstVal) {
        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(2), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(2), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[2]", vBase(2), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[4]", vBase(2), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[65536]", vBase(2), vInt(16))
        chkExpr("(#0).pow(#1)", "$type[4294967296]", vBase(2), vInt(32))
        chkExpr("(#0).pow(#1)", "$type[4611686018427387904]", vBase(2), vInt(62))

        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(10), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(10), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[10]", vBase(10), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[100]", vBase(10), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[1000000000000000000]", vBase(10), vInt(18))

        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(1000000), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1000000]", vBase(1000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000000000]", vBase(1000000), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[1000000000000000000]", vBase(1000000), vInt(3))

        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(1000000000), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1000000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1000000000]", vBase(1000000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000000000000000]", vBase(1000000000), vInt(2))

        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(9223372036854775807), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(9223372036854775807), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[9223372036854775807]", vBase(9223372036854775807), vInt(1))
    }

    @Test fun testPowSpecialBase() {
        chkPowSpecialBaseCommon("int", ::vInt)
    }

    fun chkPowSpecialBaseCommon(type: String, vBase: (Long) -> RellExprTester.TstVal) {
        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(0), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(0), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[0]", vBase(0), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[0]", vBase(0), vInt(100))
        chkExpr("(#0).pow(#1)", "$type[0]", vBase(0), vInt(1000000000))
        chkExpr("(#0).pow(#1)", "$type[0]", vBase(0), vInt(9223372036854775807))

        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(1), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(100))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(1000000000))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(9223372036854775807))

        chkExpr("(#0).pow(#1)", "rt_err:pow:exp_negative:-1", vBase(-1), vInt(-1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(3))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(100))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(101))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(1000000000))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(1000000001))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(1000000000000))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(1000000000001))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(1000000000000000))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(1000000000000001))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(1234567890123456))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(1234567890123457))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(9223372036854775806))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(9223372036854775807))
    }

    @Test fun testPowSpecialExponent() {
        chkPowSpecialExponentCommon("int", ::vInt)
    }

    fun chkPowSpecialExponentCommon(type: String, vBase: (Long) -> RellExprTester.TstVal) {
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(0), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(2), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(3), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(10), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1000000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1000000000000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(9223372036854775807), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-2), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-3), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-10), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1000000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1000000000000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-9223372036854775807), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-9223372036854775807-1), vInt(0))

        chkExpr("(#0).pow(#1)", "$type[0]", vBase(0), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(1), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[2]", vBase(2), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[3]", vBase(3), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[10]", vBase(10), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000]", vBase(1000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000000]", vBase(1000000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000000000000]", vBase(1000000000000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[9223372036854775807]", vBase(9223372036854775807), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-1]", vBase(-1), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-2]", vBase(-2), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-3]", vBase(-3), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-10]", vBase(-10), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-1000000]", vBase(-1000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-1000000000]", vBase(-1000000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-1000000000000000]", vBase(-1000000000000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-9223372036854775807]", vBase(-9223372036854775807), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[-9223372036854775808]", vBase(-9223372036854775807-1), vInt(1))
    }

    @Test fun testPowNegativeBase() {
        chkPowNegativeBaseCommon("int", ::vInt)
    }

    fun chkPowNegativeBaseCommon(type: String, vBase: (Long) -> RellExprTester.TstVal) {
        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-2), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[-2]", vBase(-2), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[4]", vBase(-2), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[-8]", vBase(-2), vInt(3))
        chkExpr("(#0).pow(#1)", "$type[16]", vBase(-2), vInt(4))
        chkExpr("(#0).pow(#1)", "$type[-32]", vBase(-2), vInt(5))
        chkExpr("(#0).pow(#1)", "$type[-2147483648]", vBase(-2), vInt(31))
        chkExpr("(#0).pow(#1)", "$type[4294967296]", vBase(-2), vInt(32))
        chkExpr("(#0).pow(#1)", "$type[-2305843009213693952]", vBase(-2), vInt(61))
        chkExpr("(#0).pow(#1)", "$type[4611686018427387904]", vBase(-2), vInt(62))
        chkExpr("(#0).pow(#1)", "$type[-9223372036854775808]", vBase(-2), vInt(63))

        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-5), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[-5]", vBase(-5), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[25]", vBase(-5), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[-125]", vBase(-5), vInt(3))
        chkExpr("(#0).pow(#1)", "$type[390625]", vBase(-5), vInt(8))
        chkExpr("(#0).pow(#1)", "$type[1490116119384765625]", vBase(-5), vInt(26))
        chkExpr("(#0).pow(#1)", "$type[-7450580596923828125]", vBase(-5), vInt(27))

        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[-1000]", vBase(-1000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000]", vBase(-1000), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[-1000000000]", vBase(-1000), vInt(3))
        chkExpr("(#0).pow(#1)", "$type[1000000000000]", vBase(-1000), vInt(4))
        chkExpr("(#0).pow(#1)", "$type[-1000000000000000]", vBase(-1000), vInt(5))
        chkExpr("(#0).pow(#1)", "$type[1000000000000000000]", vBase(-1000), vInt(6))

        chkExpr("(#0).pow(#1)", "$type[1]", vBase(-1000000), vInt(0))
        chkExpr("(#0).pow(#1)", "$type[-1000000]", vBase(-1000000), vInt(1))
        chkExpr("(#0).pow(#1)", "$type[1000000000000]", vBase(-1000000), vInt(2))
        chkExpr("(#0).pow(#1)", "$type[-1000000000000000000]", vBase(-1000000), vInt(3))
    }

    @Test fun testPowOverflow() {
        chkPowOverflow(2, 63)
        chkPowOverflow(2, 9223372036854775807)
        chkPowOverflow(10, 19)
        chkPowOverflow(10, 9223372036854775807)
        chkPowOverflow(1000000, 4)
        chkPowOverflow(1000000, 9223372036854775807)
        chkPowOverflow(1000000000, 3)
        chkPowOverflow(1000000000, 9223372036854775807)
        chkPowOverflow(9223372036854775807, 2)
        chkPowOverflow(9223372036854775807, 9223372036854775807)

        chkPowOverflow(-2, 64)
        chkPowOverflow(-5, 28)
        chkPowOverflow(-1000, 7)
        chkPowOverflow(-1000000, 4)

        chkPowOverflow(3037000499, 2, 9223372030926249001)
        chkPowOverflow(2097151, 3, 9223358842721533951)
        chkPowOverflow(55108, 4, 9222710978872688896)
        chkPowOverflow(6208, 5, 9220586390859808768)
        chkPowOverflow(1448, 6, 9217462324974321664)
        chkPowOverflow(511, 7, 9098007718612700671)
        chkPowOverflow(234, 8, 8989320386052055296)
        chkPowOverflow(127, 9, 8594754748609397887)
        chkPowOverflow(78, 10, 8335775831236199424)
        chkPowOverflow(52, 11, 7516865509350965248)
        chkPowOverflow(38, 12, 9065737908494995456)
        chkPowOverflow(28, 13, 6502111422497947648)
        chkPowOverflow(22, 14, 6221821273427820544)
        chkPowOverflow(18, 15, 6746640616477458432)
        chkPowOverflow(15, 16, 6568408355712890625)
        chkPowOverflow(13, 17, 8650415919381337933)
        chkPowOverflow(11, 18, 5559917313492231481)
        chkPowOverflow(9, 19, 1350851717672992089)
        chkPowOverflow(8, 20, 1152921504606846976)
        chkPowOverflow(7, 22, 3909821048582988049)
        chkPowOverflow(6, 24, 4738381338321616896)
        chkPowOverflow(5, 27, 7450580596923828125)
        chkPowOverflow(4, 31, 4611686018427387904)
        chkPowOverflow(3, 39, 4052555153018976267)
        chkPowOverflow(2, 62, 4611686018427387904)
    }

    private fun chkPowOverflow(base: Long, exp: Int, res: Long) {
        val lexp = exp.toLong()
        chkExpr("(#0).pow(#1)", "int[$res]", vInt(base), vInt(lexp))
        chkPowOverflow(base + 1, lexp)
        chkPowOverflow(base, lexp + 1)
    }

    private fun chkPowOverflow(base: Long, exp: Long) {
        chkExpr("(#0).pow(#1)", "rt_err:pow:overflow:$base:$exp", vInt(base), vInt(exp))
    }
}
