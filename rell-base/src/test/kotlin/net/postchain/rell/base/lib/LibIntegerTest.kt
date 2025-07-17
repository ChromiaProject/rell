/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class LibIntegerTest: BaseRellTest() {
    @Test fun testConstants() {
        chk("integer.MIN_VALUE", "int[-9223372036854775808]")
        chk("integer.MAX_VALUE", "int[9223372036854775807]")
    }

    @Test fun testConstructorText() {
        // Only few cases, as it's the same as integer.from_text().
        chk("integer('0')", "int[0]")
        chk("integer('123456789')", "int[123456789]")
        chk("integer('123456789', 20)", "int[28365650969]")
        chk("integer('123', 0)", "rt_err:fn:integer.from_text:radix:0")
        chk("integer('123', 1)", "rt_err:fn:integer.from_text:radix:1")
        chk("integer('123', 40)", "rt_err:fn:integer.from_text:radix:40")
        chk("integer('123', -1)", "rt_err:fn:integer.from_text:radix:-1")
    }

    @Test fun testFromText() {
        chk("integer.from_text('0')", "int[0]")
        chk("integer.from_text('123456789')", "int[123456789]")
        chk("integer.from_text('9223372036854775807')", "int[9223372036854775807]")
        chk("integer.from_text('9223372036854775808')", "rt_err:fn:integer.from_text:9223372036854775808")
        chk("integer.from_text('-123456789')", "int[-123456789]")
        chk("integer.from_text('-9223372036854775808')", "int[-9223372036854775808]")
        chk("integer.from_text('-9223372036854775809')", "rt_err:fn:integer.from_text:-9223372036854775809")
        chk("integer.from_text('')", "rt_err:fn:integer.from_text:")
        chk("integer.from_text(' 123')", "rt_err:fn:integer.from_text: 123")
        chk("integer.from_text('123 ')", "rt_err:fn:integer.from_text:123 ")
        chk("integer.from_text('0123')", "int[123]")
        chk("integer.from_text('0x1234')", "rt_err:fn:integer.from_text:0x1234")
        chk("integer.from_text(123)", "ct_err:expr_call_badargs:[integer.from_text]:[integer]")
        chk("integer.from_text('aaa')", "rt_err:fn:integer.from_text:aaa")

        chk("integer.from_text('123', 0)", "rt_err:fn:integer.from_text:radix:0")
        chk("integer.from_text('123', 1)", "rt_err:fn:integer.from_text:radix:1")
        chk("integer.from_text('123', 40)", "rt_err:fn:integer.from_text:radix:40")
        chk("integer.from_text('123', -1)", "rt_err:fn:integer.from_text:radix:-1")
    }

    @Test fun testFromTextBase2() {
        chk("integer.from_text('0', 2)", "int[0]")
        chk("integer.from_text('1111011', 2)", "int[123]")
        chk("integer.from_text('11111111', 2)", "int[255]")
        chk("integer.from_text('111111111111111111111111111111111111111111111111111111111111111', 2)", "int[9223372036854775807]")
        chk("integer.from_text('-1', 2)", "int[-1]")
        chk("integer.from_text('-1000000000000000000000000000000000000000000000000000000000000000', 2)", "int[-9223372036854775808]")
        chk("integer.from_text('1000000000000000000000000000000000000000000000000000000000000000', 2)",
                "rt_err:fn:integer.from_text:1000000000000000000000000000000000000000000000000000000000000000")
        chk("integer.from_text('-1000000000000000000000000000000000000000000000000000000000000001', 2)",
                "rt_err:fn:integer.from_text:-1000000000000000000000000000000000000000000000000000000000000001")
    }

    @Test fun testFromTextBase8() {
        chk("integer.from_text('0', 8)", "int[0]")
        chk("integer.from_text('173', 8)", "int[123]")
        chk("integer.from_text('377', 8)", "int[255]")
        chk("integer.from_text('777777777777777777777', 8)", "int[9223372036854775807]")
        chk("integer.from_text('-1', 8)", "int[-1]")
        chk("integer.from_text('-1000000000000000000000', 8)", "int[-9223372036854775808]")
        chk("integer.from_text('1000000000000000000000', 8)", "rt_err:fn:integer.from_text:1000000000000000000000")
        chk("integer.from_text('-1000000000000000000001', 8)", "rt_err:fn:integer.from_text:-1000000000000000000001")
    }

    @Test fun testFromTextBase10() {
        chk("integer.from_text('0', 10)", "int[0]")
        chk("integer.from_text('123456789', 10)", "int[123456789]")
        chk("integer.from_text('9223372036854775807', 10)", "int[9223372036854775807]")
        chk("integer.from_text('9223372036854775808', 10)", "rt_err:fn:integer.from_text:9223372036854775808")
        chk("integer.from_text('-123456789', 10)", "int[-123456789]")
        chk("integer.from_text('-9223372036854775808', 10)", "int[-9223372036854775808]")
        chk("integer.from_text('-9223372036854775809', 10)", "rt_err:fn:integer.from_text:-9223372036854775809")
    }

    @Test fun testFromTextBase16() {
        chk("integer.from_text('0', 16)", "int[0]")
        chk("integer.from_text('7b', 16)", "int[123]")
        chk("integer.from_text('ff', 16)", "int[255]")
        chk("integer.from_text('7fffffffffffffff', 16)", "int[9223372036854775807]")
        chk("integer.from_text('-1', 16)", "int[-1]")
        chk("integer.from_text('-8000000000000000', 16)", "int[-9223372036854775808]")
        chk("integer.from_text('8000000000000000', 16)", "rt_err:fn:integer.from_text:8000000000000000")
        chk("integer.from_text('-8000000000000001', 16)", "rt_err:fn:integer.from_text:-8000000000000001")
    }

    @Test fun testFromHex() {
        chk("integer.from_hex('0')", "int[0]")
        chk("integer.from_hex('7b')", "int[123]")
        chk("integer.from_hex('ff')", "int[255]")
        chk("integer.from_hex('7fffffffffffffff')", "int[9223372036854775807]")
        chk("integer.from_hex('ffffffffffffffff')", "int[-1]")
        chk("integer.from_hex('8000000000000000')", "int[-9223372036854775808]")
        chk("integer.from_hex('-1')", "rt_err:fn:integer.from_hex:-1")
        chk("integer.from_hex('10000000000000000')", "rt_err:fn:integer.from_hex:10000000000000000")
        chk("integer.from_hex()", "ct_err:expr:call:missing_args:[integer.from_hex]:[0:value]")
        chk("integer.from_hex(123)", "ct_err:expr_call_badargs:[integer.from_hex]:[integer]")
        chk("integer.from_hex('')", "rt_err:fn:integer.from_hex:")
        chk("integer.from_hex('ghi')", "rt_err:fn:integer.from_hex:ghi")
        chk("integer.from_hex('0x7b')", "rt_err:fn:integer.from_hex:0x7b")
    }

    @Test fun testToDecimal() {
        chk("(0).to_decimal()", "dec[0]")
        chk("(123456).to_decimal()", "dec[123456]")
        chk("(9223372036854775807).to_decimal()", "dec[9223372036854775807]")
        chk("(-9223372036854775807-1).to_decimal()", "dec[-9223372036854775808]")
    }

    @Test fun testToHex() {
        chk("(0).to_hex()", "text[0]")
        chk("(123).to_hex()", "text[7b]")
        chk("(255).to_hex()", "text[ff]")
        chk("(9223372036854775807).to_hex()", "text[7fffffffffffffff]")
        chk("(-1).to_hex()", "text[ffffffffffffffff]")
        chk("(-9223372036854775807-1).to_hex()", "text[8000000000000000]")
    }

    @Test fun testToText() {
        chk("(0).to_text()", "text[0]")
        chk("(123).to_text()", "text[123]")
        chk("(255).to_text()", "text[255]")
        chk("(9223372036854775807).to_text()", "text[9223372036854775807]")
        chk("(-1).to_text()", "text[-1]")
        chk("(-9223372036854775807-1).to_text()", "text[-9223372036854775808]")
    }

    @Test fun testToTextBase2() {
        chk("(0).to_text(2)", "text[0]")
        chk("(123).to_text(2)", "text[1111011]")
        chk("(255).to_text(2)", "text[11111111]")
        chk("(9223372036854775807).to_text(2)", "text[111111111111111111111111111111111111111111111111111111111111111]")
        chk("(-1).to_text(2)", "text[-1]")
        chk("(-9223372036854775807-1).to_text(2)", "text[-1000000000000000000000000000000000000000000000000000000000000000]")
    }

    @Test fun testToTextBase8() {
        chk("(0).to_text(8)", "text[0]")
        chk("(123).to_text(8)", "text[173]")
        chk("(255).to_text(8)", "text[377]")
        chk("(9223372036854775807).to_text(8)", "text[777777777777777777777]")
        chk("(-1).to_text(8)", "text[-1]")
        chk("(-9223372036854775807-1).to_text(8)", "text[-1000000000000000000000]")
    }

    @Test fun testToTextBase10() {
        chk("(0).to_text(10)", "text[0]")
        chk("(123).to_text(10)", "text[123]")
        chk("(255).to_text(10)", "text[255]")
        chk("(9223372036854775807).to_text(10)", "text[9223372036854775807]")
        chk("(-1).to_text(10)", "text[-1]")
        chk("(-9223372036854775807-1).to_text(10)", "text[-9223372036854775808]")
    }

    @Test fun testToTextBase16() {
        chk("(0).to_text(16)", "text[0]")
        chk("(123).to_text(16)", "text[7b]")
        chk("(255).to_text(16)", "text[ff]")
        chk("(9223372036854775807).to_text(16)", "text[7fffffffffffffff]")
        chk("(-1).to_text(16)", "text[-1]")
        chk("(-9223372036854775807-1).to_text(16)", "text[-8000000000000000]")
    }

    @Test fun testSign() {
        chk("(0).sign()", "int[0]")
        chk("(1).sign()", "int[1]")
        chk("(-1).sign()", "int[-1]")
        chk("(-123456).sign()", "int[-1]")
        chk("(123456).sign()", "int[1]")
    }
}
