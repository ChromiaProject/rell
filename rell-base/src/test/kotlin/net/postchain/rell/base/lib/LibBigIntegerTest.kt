/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.testutils.BaseRellTest
import java.math.BigInteger
import kotlin.test.Test

class LibBigIntegerTest: BaseRellTest() {
    @Test fun testConstants() {
        val expMax = "9".repeat(Lib_BigIntegerMath.PRECISION)
        chk("big_integer.PRECISION", "int[131072]")
        chk("big_integer.MIN_VALUE", "bigint[-$expMax]")
        chk("big_integer.MAX_VALUE", "bigint[$expMax]")
    }

    @Test fun testConstructorText() {
        // More tests in OperatorsBaseTest.
        chk("big_integer('1234')", "bigint[1234]")
        chk("big_integer(1234)", "bigint[1234]")
        chk("big_integer(12.34)", "ct_err:expr_call_badargs:[big_integer]:[decimal]")
        chk("big_integer(false)", "ct_err:expr_call_badargs:[big_integer]:[boolean]")
        chk("big_integer(123L)", "ct_err:expr_call_badargs:[big_integer]:[big_integer]")
    }

    @Test fun testFromText() {
        // More tests in OperatorsBaseTest.
        chk("big_integer.from_text('1234')", "bigint[1234]")
        chk("big_integer.from_text(false)", "ct_err:expr_call_badargs:[big_integer.from_text]:[boolean]")
    }

    @Test fun testFromTextBase() {
        chk("big_integer.from_text('123', 10)", "bigint[123]")
        chk("big_integer.from_text('101', 2)", "bigint[5]")

        chk("big_integer.from_text('123', 0)", "rt_err:fn:big_integer.from_text:radix:0")
        chk("big_integer.from_text('123', 1)", "rt_err:fn:big_integer.from_text:radix:1")
        chk("big_integer.from_text('123', 40)", "rt_err:fn:big_integer.from_text:radix:40")
        chk("big_integer.from_text('123', -1)", "rt_err:fn:big_integer.from_text:radix:-1")
    }

    @Test fun testFromTextBase2() {
        chk("big_integer.from_text('0', 2)", "bigint[0]")
        chk("big_integer.from_text('1111011', 2)", "bigint[123]")
        chk("big_integer.from_text('11111111', 2)", "bigint[255]")
        chk("big_integer.from_text('-1', 2)", "bigint[-1]")
        chk("big_integer.from_text('111111111111111111111111111111111111111111111111111111111111111', 2)", "bigint[9223372036854775807]")
        chk("big_integer.from_text('-111111111111111111111111111111111111111111111111111111111111111', 2)", "bigint[-9223372036854775807]")
        chk("big_integer.from_text('-1000000000000000000000000000000000000000000000000000000000000000', 2)", "bigint[-9223372036854775808]")
        chk("big_integer.from_text('1000000000000000000000000000000000000000000000000000000000000000', 2)", "bigint[9223372036854775808]")
        chk("big_integer.from_text('-1000000000000000000000000000000000000000000000000000000000000001', 2)", "bigint[-9223372036854775809]")
        chk("big_integer.from_text('2', 2)", "rt_err:fn:big_integer.from_text:2")
    }

    @Test fun testFromTextBase8() {
        chk("big_integer.from_text('0', 8)", "bigint[0]")
        chk("big_integer.from_text('173', 8)", "bigint[123]")
        chk("big_integer.from_text('377', 8)", "bigint[255]")
        chk("big_integer.from_text('777777777777777777777', 8)", "bigint[9223372036854775807]")
        chk("big_integer.from_text('-1', 8)", "bigint[-1]")
        chk("big_integer.from_text('-1000000000000000000000', 8)", "bigint[-9223372036854775808]")
        chk("big_integer.from_text('1000000000000000000000', 8)", "bigint[9223372036854775808]")
        chk("big_integer.from_text('-1000000000000000000001', 8)", "bigint[-9223372036854775809]")
        chk("big_integer.from_text('8', 8)", "rt_err:fn:big_integer.from_text:8")
    }

    @Test fun testFromTextBase10() {
        chk("big_integer.from_text('0', 10)", "bigint[0]")
        chk("big_integer.from_text('123456789', 10)", "bigint[123456789]")
        chk("big_integer.from_text('9223372036854775807', 10)", "bigint[9223372036854775807]")
        chk("big_integer.from_text('9223372036854775808', 10)", "bigint[9223372036854775808]")
        chk("big_integer.from_text('-123456789', 10)", "bigint[-123456789]")
        chk("big_integer.from_text('-9223372036854775808', 10)", "bigint[-9223372036854775808]")
        chk("big_integer.from_text('-9223372036854775809', 10)", "bigint[-9223372036854775809]")
        chk("big_integer.from_text('a', 10)", "rt_err:fn:big_integer.from_text:a")
    }

    @Test fun testFromTextBase16() {
        chk("big_integer.from_text('0', 16)", "bigint[0]")
        chk("big_integer.from_text('7b', 16)", "bigint[123]")
        chk("big_integer.from_text('ff', 16)", "bigint[255]")
        chk("big_integer.from_text('7fffffffffffffff', 16)", "bigint[9223372036854775807]")
        chk("big_integer.from_text('-1', 16)", "bigint[-1]")
        chk("big_integer.from_text('-8000000000000000', 16)", "bigint[-9223372036854775808]")
        chk("big_integer.from_text('8000000000000000', 16)", "bigint[9223372036854775808]")
        chk("big_integer.from_text('-8000000000000001', 16)", "bigint[-9223372036854775809]")
        chk("big_integer.from_text('ffffffffffffffff', 16)", "bigint[18446744073709551615]")
        chk("big_integer.from_text('-ffffffffffffffff', 16)", "bigint[-18446744073709551615]")
        chk("big_integer.from_text('ffffffffffffffffffffffffffffffff', 16)", "bigint[340282366920938463463374607431768211455]")
        chk("big_integer.from_text('+ffffffffffffffffffffffffffffffff', 16)", "bigint[340282366920938463463374607431768211455]")
        chk("big_integer.from_text('-ffffffffffffffffffffffffffffffff', 16)", "bigint[-340282366920938463463374607431768211455]")
        chk("big_integer.from_text('g', 16)", "rt_err:fn:big_integer.from_text:g")
        chk("big_integer.from_text('0x7f', 16)", "rt_err:fn:big_integer.from_text:0x7f")
    }

    @Test fun testFromHex() {
        chk("big_integer.from_hex('0')", "bigint[0]")
        chk("big_integer.from_hex('7b')", "bigint[123]")
        chk("big_integer.from_hex('ff')", "bigint[255]")
        chk("big_integer.from_hex('7fffffffffffffff')", "bigint[9223372036854775807]")
        chk("big_integer.from_hex('8000000000000000')", "bigint[9223372036854775808]")
        chk("big_integer.from_hex('ffffffffffffffff')", "bigint[18446744073709551615]")
        chk("big_integer.from_hex('10000000000000000')", "bigint[18446744073709551616]")

        chk("big_integer.from_hex('1')", "bigint[1]")
        chk("big_integer.from_hex('+1')", "bigint[1]")
        chk("big_integer.from_hex('-1')", "bigint[-1]")
        chk("big_integer.from_hex('-7fffffffffffffff')", "bigint[-9223372036854775807]")
        chk("big_integer.from_hex('-ffffffffffffffff')", "bigint[-18446744073709551615]")
        chk("big_integer.from_hex('+7fffffffffffffff')", "bigint[9223372036854775807]")
        chk("big_integer.from_hex('+ffffffffffffffff')", "bigint[18446744073709551615]")

        chk("big_integer.from_hex()", "ct_err:expr:call:missing_args:[big_integer.from_hex]:[0:value]")
        chk("big_integer.from_hex(123)", "ct_err:expr_call_badargs:[big_integer.from_hex]:[integer]")
        chk("big_integer.from_hex('')", "rt_err:fn:big_integer.from_hex:")
        chk("big_integer.from_hex('ghi')", "rt_err:fn:big_integer.from_hex:ghi")
        chk("big_integer.from_hex('0x7b')", "rt_err:fn:big_integer.from_hex:0x7b")
    }

    @Test fun testToFromBytes() {
        chkToFromBytes("0", "00")
        chkToFromBytes("1", "01")
        chkToFromBytes("-1", "ff")
        chkToFromBytes("0x7f", "7f")
        chkToFromBytes("0x80", "0080")
        chkToFromBytes("-0x80", "80")
        chkToFromBytes("-0x81", "ff7f")
        chkToFromBytes("0x7fff", "7fff")
        chkToFromBytes("0x8000", "008000")
        chkToFromBytes("-0x8000", "8000")
        chkToFromBytes("-0x8001", "ff7fff")
        chkToFromBytes("0x7fffffffffffff", "7fffffffffffff")
        chkToFromBytes("0x80000000000000", "0080000000000000")
        chkToFromBytes("-0x80000000000000", "80000000000000")
        chkToFromBytes("-0x80000000000001", "ff7fffffffffffff")
        chkToFromBytes("123456789", "075bcd15")
        chkToFromBytes("-123456789", "f8a432eb")
    }

    private fun chkToFromBytes(v: String, b: String) {
        val bigInt = parseBigInt(v)
        chk("(${bigInt}L).to_bytes()", "byte_array[$b]")
        chk("big_integer.from_bytes(x'$b')", "bigint[$bigInt]")
    }

    @Test fun testToFromBytesUnsigned() {
        chkToFromBytesUns("0x0", "")
        chkToFromBytesUns("0x1", "01")
        chkToFromBytesUns("0x80", "80")
        chkToFromBytesUns("0xff", "ff")
        chkToFromBytesUns("0x100", "0100")
        chkToFromBytesUns("0x1000", "1000")
        chkToFromBytesUns("0x8000", "8000")
        chkToFromBytesUns("0xffff", "ffff")
        chkToFromBytesUns("0x10000", "010000")
        chkToFromBytesUns("0xffffffffff", "ffffffffff")
        chkToFromBytesUns("0x10000000000", "010000000000")
        chkToFromBytesUns("123456789", "075bcd15")

        chk("(-1L).to_bytes_unsigned()", "rt_err:fn:big_integer.to_bytes_unsigned:negative")
        chk("(-0xffL).to_bytes_unsigned()", "rt_err:fn:big_integer.to_bytes_unsigned:negative")
        chk("(-0x1000L).to_bytes_unsigned()", "rt_err:fn:big_integer.to_bytes_unsigned:negative")
        chk("(-0xfffffffffffL).to_bytes_unsigned()", "rt_err:fn:big_integer.to_bytes_unsigned:negative")
        chk("(-123456789L).to_bytes_unsigned()", "rt_err:fn:big_integer.to_bytes_unsigned:negative")
    }

    private fun chkToFromBytesUns(v: String, b: String) {
        val bigInt = parseBigInt(v)
        chk("(${bigInt}L).to_bytes_unsigned()", "byte_array[$b]")
        chk("big_integer.from_bytes_unsigned(x'$b')", "bigint[$bigInt]")
    }

    private fun parseBigInt(s: String): BigInteger {
        var p = s
        var radix = 10
        if (p.contains("0x")) {
            p = p.replace("0x", "")
            radix = 16
        }
        return BigInteger(p, radix)
    }

    @Test fun testToDecimal() {
        chk("big_integer(0).to_decimal()", "dec[0]")
        chk("big_integer(123456).to_decimal()", "dec[123456]")
        chk("big_integer(9223372036854775807).to_decimal()", "dec[9223372036854775807]")
        chk("big_integer(-9223372036854775807-1).to_decimal()", "dec[-9223372036854775808]")
        chk("big_integer('18446744073709551615').to_decimal()", "dec[18446744073709551615]")
        chk("big_integer('-18446744073709551615').to_decimal()", "dec[-18446744073709551615]")
    }

    @Test fun testToHex() {
        chk("big_integer(0).to_hex()", "text[0]")
        chk("big_integer(123).to_hex()", "text[7b]")
        chk("big_integer(255).to_hex()", "text[ff]")
        chk("big_integer('9223372036854775807').to_hex()", "text[7fffffffffffffff]")
        chk("big_integer('9223372036854775808').to_hex()", "text[8000000000000000]")
        chk("big_integer('18446744073709551615').to_hex()", "text[ffffffffffffffff]")
        chk("big_integer(-1).to_hex()", "text[-1]")
        chk("big_integer('-9223372036854775808').to_hex()", "text[-8000000000000000]")
        chk("big_integer('-18446744073709551615').to_hex()", "text[-ffffffffffffffff]")
    }

    @Test fun testToTextBase() {
        chk("big_integer(0).to_text(-1)", "rt_err:fn:big_integer.to_text:radix:-1")
        chk("big_integer(0).to_text(0)", "rt_err:fn:big_integer.to_text:radix:0")
        chk("big_integer(0).to_text(1)", "rt_err:fn:big_integer.to_text:radix:1")
        chk("big_integer(0).to_text(37)", "rt_err:fn:big_integer.to_text:radix:37")
        chk("big_integer(0).to_text(100)", "rt_err:fn:big_integer.to_text:radix:100")
    }

    @Test fun testToTextBase2() {
        chk("big_integer(0).to_text(2)", "text[0]")
        chk("big_integer(123).to_text(2)", "text[1111011]")
        chk("big_integer(255).to_text(2)", "text[11111111]")
        chk("big_integer(9223372036854775807).to_text(2)", "text[111111111111111111111111111111111111111111111111111111111111111]")
        chk("big_integer(-1).to_text(2)", "text[-1]")
        chk("big_integer(-9223372036854775807-1).to_text(2)", "text[-1000000000000000000000000000000000000000000000000000000000000000]")
    }

    @Test fun testToTextBase8() {
        chk("big_integer(0).to_text(8)", "text[0]")
        chk("big_integer(123).to_text(8)", "text[173]")
        chk("big_integer(255).to_text(8)", "text[377]")
        chk("big_integer(9223372036854775807).to_text(8)", "text[777777777777777777777]")
        chk("big_integer(-1).to_text(8)", "text[-1]")
        chk("big_integer(-9223372036854775807-1).to_text(8)", "text[-1000000000000000000000]")
    }

    @Test fun testToText10() {
        chk("big_integer(0).to_text(10)", "text[0]")
        chk("big_integer(123).to_text(10)", "text[123]")
        chk("big_integer(255).to_text(10)", "text[255]")
        chk("big_integer(9223372036854775807).to_text(10)", "text[9223372036854775807]")
        chk("big_integer(-1).to_text(10)", "text[-1]")
        chk("big_integer(-9223372036854775807-1).to_text(10)", "text[-9223372036854775808]")
    }

    @Test fun testToTextBase16() {
        chk("big_integer(0).to_text(16)", "text[0]")
        chk("big_integer(123).to_text(16)", "text[7b]")
        chk("big_integer(255).to_text(16)", "text[ff]")
        chk("big_integer(9223372036854775807).to_text(16)", "text[7fffffffffffffff]")
        chk("big_integer(-1).to_text(16)", "text[-1]")
        chk("big_integer(-9223372036854775807-1).to_text(16)", "text[-8000000000000000]")
    }

    @Test fun testSign() {
        chk("big_integer(0).sign()", "int[0]")
        chk("big_integer(1).sign()", "int[1]")
        chk("big_integer(-1).sign()", "int[-1]")
        chk("big_integer(-123456).sign()", "int[-1]")
        chk("big_integer(123456).sign()", "int[1]")
    }
}
