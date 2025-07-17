/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lang.type.BigIntegerTest
import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.testutils.BaseExprTest
import kotlin.test.Test
import java.math.BigInteger
import kotlin.test.assertEquals

abstract class LibBigIntegerExprTest: BaseExprTest() {
    class LibBigIntegerExprIpTest: LibBigIntegerExprTest() {
        @Test fun testPowOverflowError() {
            val k = "1234567890" + "0".repeat(90)
            val err = "rt_err:pow:overflow"
            chkExpr("(#0).pow(#1)", "$err:${k}:2000", vBigInt(k), vInt(2000))
            chkExpr("(#0).pow(#1)", "$err:-${k}:2000", vBigInt("-$k"), vInt(2000))
            chkExpr("(#0).pow(#1)", "$err:1.234567890000000(...)E+100:2000", vBigInt("${k}0"), vInt(2000))
            chkExpr("(#0).pow(#1)", "$err:-1.234567890000000(...)E+100:2000", vBigInt("-${k}0"), vInt(2000))
            chkExpr("(#0).pow(#1)", "$err:1.234567890000000(...)E+105:2000", vBigInt("${k}000000"), vInt(2000))
            chkExpr("(#0).pow(#1)", "$err:-1.234567890000000(...)E+105:2000", vBigInt("-${k}000000"), vInt(2000))
        }

        @Test fun testPowOverflowOutOfMemory() {
            val k = "1" + "0".repeat(1000)
            val err = "rt_err:pow:overflow"
            chkExpr("(#0).pow(#1)", "$err:1.000000000000000(...)E+1000:1000", vBigInt(k), vInt(1000))
            chkExpr("(#0).pow(#1)", "$err:1.000000000000000(...)E+1000:250000", vBigInt(k), vInt(250000))
            chkExpr("(#0).pow(#1)", "$err:1.000000000000000(...)E+1000:1000000", vBigInt(k), vInt(1000000))
            chkExpr("(#0).pow(#1)", "$err:1.000000000000000(...)E+1000:1000000000", vBigInt(k), vInt(1000000000))
        }

        @Test fun testBigIntegerBitLength() {
            // Checking Java BigInteger details.
            chkBitLength(0, 0)
            chkBitLength(1, 1)
            chkBitLength(2, 2)
            chkBitLength(3, 2)
            chkBitLength(4, 3)
            chkBitLength(5, 3)
            chkBitLength(6, 3)
            chkBitLength(7, 3)
            chkBitLength(8, 4)
            chkBitLength(-1, 0)
            chkBitLength(-2, 1)
            chkBitLength(-3, 2)
            chkBitLength(-4, 2)
            chkBitLength(-5, 3)
            chkBitLength(-6, 3)
            chkBitLength(-7, 3)
            chkBitLength(-8, 3)
            chkBitLength(-9, 4)
        }

        private fun chkBitLength(v: Int, exp: Int) {
            assertEquals(exp, BigInteger.valueOf(v.toLong()).bitLength())
        }
    }

    class LibBigIntegerExprDbTest: LibBigIntegerExprTest()

    @Test fun testConstructorText() {
        chkConstructor("0", "bigint[0]")
        chkConstructor("+0", "bigint[0]")
        chkConstructor("-0", "bigint[0]")
        chkConstructor("12345", "bigint[12345]")
        chkConstructor("+12345", "bigint[12345]")
        chkConstructor("-12345", "bigint[-12345]")
        chkConstructor("10000", "bigint[10000]")
        chkConstructor("1000000000000", "bigint[1000000000000]")
        chkConstructor("0000", "bigint[0]")
        chkConstructor("0001", "bigint[1]")

        chkConstructor("-0", "bigint[0]")
        chkConstructor("-1", "bigint[-1]")
        chkConstructor("-12345689", "bigint[-12345689]")
        chkConstructor("-0001", "bigint[-1]")
        chkConstructor("+0", "bigint[0]")
        chkConstructor("+1", "bigint[1]")
        chkConstructor("+12345689", "bigint[12345689]")
        chkConstructor("+0001", "bigint[1]")
    }

    @Test fun testConstructorTextInvalid() {
        chkConstructor("0L", rtErr("bigint:invalid:0L"))
        chkConstructor("1L", rtErr("bigint:invalid:1L"))
        chkConstructor("123L", rtErr("bigint:invalid:123L"))

        chkConstructor("123E0", rtErr("bigint:invalid:123E0"))
        chkConstructor("123E+5", rtErr("bigint:invalid:123E+5"))
        chkConstructor("123e5", rtErr("bigint:invalid:123e5"))
        chkConstructor("123e+5", rtErr("bigint:invalid:123e+5"))
        chkConstructor("123E-1", rtErr("bigint:invalid:123E-1"))
        chkConstructor("-123E-10", rtErr("bigint:invalid:-123E-10"))
        chkConstructor("+123e-10", rtErr("bigint:invalid:+123e-10"))

        chkConstructor("123.456", rtErr("bigint:invalid:123.456"))
        chkConstructor("+123.456", rtErr("bigint:invalid:+123.456"))
        chkConstructor("-123.456", rtErr("bigint:invalid:-123.456"))
        chkConstructor(".1", rtErr("bigint:invalid:.1"))
        chkConstructor("-.1", rtErr("bigint:invalid:-.1"))
        chkConstructor("+.1", rtErr("bigint:invalid:+.1"))
        chkConstructor("0.0", rtErr("bigint:invalid:0.0"))
        chkConstructor("1.0", rtErr("bigint:invalid:1.0"))

        chkConstructor("", rtErr("bigint:invalid:"))
        chkConstructor("1 ", rtErr("bigint:invalid:1 "))
        chkConstructor(" 1", rtErr("bigint:invalid: 1"))
        chkConstructor(" 1 ", rtErr("bigint:invalid: 1 "))
        chkConstructor("++1", rtErr("bigint:invalid:++1"))
        chkConstructor("--1", rtErr("bigint:invalid:--1"))
        chkConstructor("-+1", rtErr("bigint:invalid:-+1"))
        chkConstructor("+-1", rtErr("bigint:invalid:+-1"))
        chkConstructor("Hello", rtErr("bigint:invalid:Hello"))
        chkConstructor("0x1234", rtErr("bigint:invalid:0x1234"))
    }

    @Test fun testConstructorTextOverflow() {
        val maxVal = BigIntegerTest.limitMinus(1)
        val maxValP1 = BigIntegerTest.limitMinus(0)
        val overMaxVal = "" + (BigIntegerTest.LIMIT * BigInteger.valueOf(3))

        chkConstructor("$maxVal", "bigint[$maxVal]")
        chkConstructor("-$maxVal", "bigint[-$maxVal]")
        chkConstructor("+$maxVal", "bigint[$maxVal]")
        chkConstructor("$maxValP1", rtErr("bigint:overflow"))
        chkConstructor("-$maxValP1", rtErr("bigint:overflow"))
        chkConstructor("+$maxValP1", rtErr("bigint:overflow"))
        chkConstructor("$overMaxVal", rtErr("bigint:overflow"))
        chkConstructor("-$overMaxVal", rtErr("bigint:overflow"))
        chkConstructor("+$overMaxVal", rtErr("bigint:overflow"))
    }

    @Test fun testConstructorInteger() {
        chkExpr("big_integer(#0)", "bigint[0]", vInt(0))
        chkExpr("big_integer(#0)", "bigint[1]", vInt(1))
        chkExpr("big_integer(#0)", "bigint[-1]", vInt(-1))
        chkExpr("big_integer(#0)", "bigint[9223372036854775807]", vInt(9223372036854775807))
        chkExpr("big_integer(#0)", "bigint[-9223372036854775808]", vInt("-9223372036854775808".toLong()))
    }

    private fun chkConstructor(s: String, exp: String) {
        chkExpr("big_integer(#0)", exp, vText(s))
        chkExpr("big_integer.from_text(#0)", exp, vText(s))
    }

    @Test fun testAbs() {
        chkExpr("#0.abs()", "bigint[0]", vBigInt("0"))
        chkExpr("#0.abs()", "bigint[12345]", vBigInt("-12345"))
        chkExpr("#0.abs()", "bigint[67890]", vBigInt("67890"))
        chkExpr("#0.abs()", "bigint[9223372036854775808]", vBigInt("-9223372036854775808"))
        chkExpr("#0.abs()", "bigint[123000000000000000000000000000000]", vBigInt("-123E30"))
    }

    @Test fun testMinMax() {
        chkExpr("#0.min(#1)", "bigint[123]", vBigInt(123), vBigInt(456))
        chkExpr("#0.min(#1)", "bigint[123]", vBigInt(456), vBigInt(123))
        chkExpr("#0.max(#1)", "bigint[456]", vBigInt(123), vBigInt(456))
        chkExpr("#0.max(#1)", "bigint[456]", vBigInt(456), vBigInt(123))
        chkExpr("#0.min(#1)", "bigint[123]", vBigInt(123), vInt(456))
        chkExpr("#0.min(#1)", "bigint[123]", vBigInt(456), vInt(123))
        chkExpr("#0.max(#1)", "bigint[456]", vBigInt(123), vInt(456))
        chkExpr("#0.max(#1)", "bigint[456]", vBigInt(456), vInt(123))
        chkExpr("#0.min(#1)", "bigint[456000000000000000000000000000000]", vBigInt("123E31"), vBigInt("456E30"))
        chkExpr("#0.min(#1)", "bigint[456000000000000000000000000000000]", vBigInt("456E30"), vBigInt("123E31"))
        chkExpr("#0.max(#1)", "bigint[1230000000000000000000000000000000]", vBigInt("123E31"), vBigInt("456E30"))
        chkExpr("#0.max(#1)", "bigint[1230000000000000000000000000000000]", vBigInt("456E30"), vBigInt("123E31"))
        chkExpr("#0.min(#1)", "bigint[456]", vBigInt("123E31"), vInt(456))
        chkExpr("#0.max(#1)", "bigint[1230000000000000000000000000000000]", vBigInt("123E31"), vInt(456))

        chkExpr("#0.min(#1)", "dec[123]", vBigInt(123), vDec(456))
        chkExpr("#0.max(#1)", "dec[456]", vBigInt(123), vDec(456))
    }

    private fun getIntTest(): LibIntegerExprTest {
        val tst = when (mode) {
            Mode.INTERPRETED -> LibIntegerExprTest.LibIntegerExprIpTest()
            Mode.DATABASE -> LibIntegerExprTest.LibIntegerExprDbTest()
        }
        resource(tst)
        return tst
    }

    @Test fun testPow() {
        chkExpr("_type_of((0L).pow(1))", "text[big_integer]")
        chkExpr("(0L).pow(1L)", "ct_err:expr_call_badargs:[big_integer.pow]:[big_integer]")
        chkExpr("(0L).pow(1.0)", "ct_err:expr_call_badargs:[big_integer.pow]:[decimal]")

        getIntTest().chkPowCommon("bigint", ::vBigInt)

        chkExpr("(#0).pow(#1)", "bigint[1427247692705959881058285969449495136382746624]", vBigInt(2), vInt(150))
        chkExpr("(#0).pow(#1)", "bigint[100000000000000000000000000000000000000000000000000]", vBigInt(10), vInt(50))
        chkExpr("(#0).pow(#1)", "bigint[784637716923335095224261902710254454442933591094742482943]",
            vBigInt(9223372036854775807), vInt(3))
        chkExpr("(#0).pow(#1)", "bigint[119813656335857110859093644639397060831164251810449900756925720268614126151801]",
            vBigInt("346141093104902976440432355966090329099"), vInt(2))
    }

    @Test fun testPowSpecialBase() {
        getIntTest().chkPowSpecialBaseCommon("bigint", ::vBigInt)
    }

    @Test fun testPowSpecialExponent() {
        getIntTest().chkPowSpecialExponentCommon("bigint", ::vBigInt)

        chkExpr("(#0).pow(#1)", "bigint[1]", vBigInt("346141093104902976440432355966090329099"), vInt(0))
        chkExpr("(#0).pow(#1)", "bigint[346141093104902976440432355966090329099]",
            vBigInt("346141093104902976440432355966090329099"), vInt(1))
    }

    @Test fun testPowNegativeBase() {
        getIntTest().chkPowNegativeBaseCommon("bigint", ::vBigInt)

        chkExpr("(#0).pow(#1)", "bigint[1]", vBigInt("-1009448127870199401855463807815"), vInt(0))
        chkExpr("(#0).pow(#1)", "bigint[-1009448127870199401855463807815]",
            vBigInt("-1009448127870199401855463807815"), vInt(1))
        chkExpr("(#0).pow(#1)", "bigint[1018985522860650442396283530019886413017402564111339255074225]",
            vBigInt("-1009448127870199401855463807815"), vInt(2))
    }

    @Test fun testPowOverflow() {
        // generated by big_integer_pow_testcases.py
        chkPowOverflow("2", 435411, 131072, "590802126", "711234048", true)
        chkPowOverflow("3", 274714, 131072, "773337477", "551828969", true)

        // Database tests are very slow.
        if (mode != Mode.DATABASE) {
            chkPowOverflow("5", 187521, 131072, "358247439", "064453125", true)
            chkPowOverflow("7", 155096, 131072, "211647942", "258817601", true)
            chkPowOverflow("11", 125862, 131072, "583632387", "773687721", true)
            chkPowOverflow("15", 111447, 131072, "695901829", "630859375", true)
            chkPowOverflow("31", 87887, 131072, "201923103", "842348511", true)
            chkPowOverflow("123", 62716, 131071, "308296811", "463382161", true)
            chkPowOverflow("123456", 25743, 131071, "628936071", "306262016", false)
            chkPowOverflow("123469", 25743, 131072, "945824525", "036973509", true)
            chkPowOverflow("123456789", 16198, 131067, "228875964", "398683881", false)
            chkPowOverflow("123555815", 16198, 131072, "999879136", "244140625", true)
            chkPowOverflow("12345678987654321", 8145, 131066, "245196439", "001812401", false)
            chkPowOverflow("12368771906168482", 8145, 131072, "999999999", "386002432", true)
            chkPowOverflow("18446744073709551616", 6803, 131067, "112686562", "888144896", false)
            chkPowOverflow("18483919202271110888", 6803, 131072, "999999999", "664771072", true)
            chkPowOverflow("340282366920938463463374607431768211456", 3401, 131047, "610875080", "281363456", false)
            chkPowOverflow("346141093104902976440432355966090329099", 3401, 131072, "999999999", "932269099", true)
        }
    }

    private fun chkPowOverflow(base: String, exp: Long, len: Int, head: String, tail: String, max: Boolean) {
        val b = BigInteger(base)
        chkPowOverflow0(b, exp, len, head, tail, max)

        val nb = b.negate()
        if (exp % 2 == 0L) {
            chkPowOverflow0(nb, exp, len, head, tail, max)
        } else {
            chkPowOverflow0(nb, exp, len + 1, "-" + head.take(8), tail, max)
        }
    }

    private fun chkPowOverflow0(b: BigInteger, exp: Long, len: Int, head: String, tail: String, max: Boolean) {
        chkExpr("(#0).pow(#1).to_text().size()", "int[$len]", vBigInt(b), vInt(exp))
        chkExpr("(#0).pow(#1).to_text().sub(0, 9)", "text[$head]", vBigInt(b), vInt(exp))
        chkExpr("(#0).pow(#1).to_text().sub(${len-9}, $len)", "text[$tail]", vBigInt(b), vInt(exp))
        chkExpr("(#0).pow(#1)", "rt_err:pow:overflow:$b:${exp+1}", vBigInt(b), vInt(exp + 1))

        if (max) {
            val bPlus1 = if (b.signum() > 0) b.add(BigInteger.ONE) else b.subtract(BigInteger.ONE)
            chkExpr("(#0).pow(#1)", "rt_err:pow:overflow:$bPlus1:$exp", vBigInt(bPlus1), vInt(exp))
        }
    }

    @Test fun testSign() {
        chkExpr("#0.sign()", "int[0]", vBigInt("0"))
        chkExpr("#0.sign()", "int[1]", vBigInt("12345"))
        chkExpr("#0.sign()", "int[-1]", vBigInt("-12345"))
        chkExpr("#0.sign()", "int[1]", vBigInt("123456789101112131415161718192021222324252627282930"))
        chkExpr("#0.sign()", "int[-1]", vBigInt("-123456789101112131415161718192021222324252627282930"))
    }

    @Test fun testToDecimal() {
        chkExpr("#0.to_decimal()", "dec[0]", vBigInt("0"))
        chkExpr("#0.to_decimal()", "dec[123456]", vBigInt("123456"))
        chkExpr("#0.to_decimal()", "dec[9223372036854775807]", vBigInt("9223372036854775807"))
        chkExpr("#0.to_decimal()", "dec[-9223372036854775808]", vBigInt("-9223372036854775808"))
        chkExpr("#0.to_decimal()", "dec[18446744073709551615]", vBigInt("18446744073709551615"))
        chkExpr("#0.to_decimal()", "dec[-18446744073709551615]", vBigInt("-18446744073709551615"))
    }

    @Test fun testToInteger() {
        chkExpr("#0.to_integer()", "int[0]", vBigInt("0"))
        chkExpr("#0.to_integer()", "int[123]", vBigInt("123"))
        chkExpr("#0.to_integer()", "int[-123]", vBigInt("-123"))
        chkExpr("#0.to_integer()", "int[9223372036854775807]", vBigInt("9223372036854775807"))
        chkExpr("#0.to_integer()", rtErr("big_integer.to_integer:overflow:9223372036854775808"), vBigInt("9223372036854775808"))
        chkExpr("#0.to_integer()", "int[-9223372036854775808]", vBigInt("-9223372036854775808"))
        chkExpr("#0.to_integer()", rtErr("big_integer.to_integer:overflow:-9223372036854775809"), vBigInt("-9223372036854775809"))
        chkExpr("#0.to_integer()", rtErr("big_integer.to_integer:overflow:9.9999999999999999999E+131071"),
            vBigInt(Lib_BigIntegerMath.MAX_VALUE))
        chkExpr("#0.to_integer()", rtErr("big_integer.to_integer:overflow:-9.9999999999999999999E+131071"),
            vBigInt(Lib_BigIntegerMath.MIN_VALUE))
    }

    @Test fun testToText() {
        chkExpr("#0.to_text()", "text[0]", vBigInt("0"))
        chkExpr("#0.to_text()", "text[123456]", vBigInt("123456"))
        chkExpr("#0.to_text()", "text[-123456]", vBigInt("-123456"))
        chkExpr("#0.to_text()", "text[1234000000000000000000]", vBigInt("12.34e20"))
        chkExpr("#0.to_text()", "text[-1234000000000000000000]", vBigInt("-12.34e20"))
        chkExpr("'' + #0", "text[123456]", vBigInt("123456"))
    }
}
