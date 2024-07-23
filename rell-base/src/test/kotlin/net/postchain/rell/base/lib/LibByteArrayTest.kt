/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class LibByteArrayTest: BaseRellTest() {
    @Test fun testConstructorText() {
        chk("byte_array('0123abcd')", "byte_array[0123abcd]")
        chk("byte_array('0123ABCD')", "byte_array[0123abcd]")
        chk("byte_array('')", "byte_array[]")
        chk("byte_array('0')", "rt_err:fn:byte_array.from_hex")
        chk("byte_array('0g')", "rt_err:fn:byte_array.from_hex")
        chk("byte_array(123)", "ct_err:expr_call_badargs:[byte_array]:[integer]")
    }

    @Test fun testSha256() {
        chk("x''.sha256()", "byte_array[e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855]")
        chk("'foo'.to_bytes().sha256()", "byte_array[2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae]")
        chk("'bar'.to_bytes().sha256()", "byte_array[fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9]")
    }

    @Test fun testFromList() {
        chk("byte_array.from_list(list<integer>())", "byte_array[]")
        chk("byte_array.from_list([123])", "byte_array[7b]")
        chk("byte_array.from_list([18, 52, 171, 205])", "byte_array[1234abcd]")
        chk("byte_array.from_list([0, 255])", "byte_array[00ff]")

        chk("byte_array.from_list()", "ct_err:expr:call:missing_args:[byte_array.from_list]:[0:list]")
        chk("byte_array.from_list(list<text>())", "ct_err:expr_call_badargs:[byte_array.from_list]:[list<text>]")
        chk("byte_array.from_list(['Hello'])", "ct_err:expr_call_badargs:[byte_array.from_list]:[list<text>]")
        chk("byte_array.from_list(set<integer>())", "ct_err:expr_call_badargs:[byte_array.from_list]:[set<integer>]")
        chk("byte_array.from_list([-1])", "rt_err:fn:byte_array.from_list:-1")
        chk("byte_array.from_list([256])", "rt_err:fn:byte_array.from_list:256")
    }

    @Test fun testEmpty() {
        chk("x''.empty()", "boolean[true]")
        chk("x'01'.empty()", "boolean[false]")
        chk("x'01234567'.empty()", "boolean[false]")
    }

    @Test fun testSize() {
        chk("x''.size()", "int[0]")
        chk("x'01'.size()", "int[1]")
        chk("x'ABCD'.size()", "int[2]")
        chk("x'0123ABCD'.size()", "int[4]")
    }

    @Test fun testConcat() {
        chk("x'0123' + x'ABCD'", "byte_array[0123abcd]")
    }

    @Test fun testSubscript() {
        chk("x'0123ABCD'[0]", "int[1]")
        chk("x'0123ABCD'[1]", "int[35]")
        chk("x'0123ABCD'[2]", "int[171]")
        chk("x'0123ABCD'[3]", "int[205]")
        chk("x'0123ABCD'[4]", "rt_err:expr_bytearray_subscript_index:4:4")
        chk("x'0123ABCD'[-1]", "rt_err:expr_bytearray_subscript_index:4:-1")
        chkEx("{ val x = x'0123ABCD'; x[1] = 123; return x; }", "ct_err:expr_immutable:byte_array")
    }

    @Test fun testSubscriptValues() {
        chk("x'007F80FF'[0]", "int[0]")
        chk("x'007F80FF'[1]", "int[127]")
        chk("x'007F80FF'[2]", "int[128]")
        chk("x'007F80FF'[3]", "int[255]")

        for (x in 0 .. 255) {
            chk("byte_array.from_list([$x])[0]", "int[$x]")
        }
    }

    @Test fun testSub() {
        chk("x'0123ABCD'.sub(0)", "byte_array[0123abcd]")
        chk("x'0123ABCD'.sub(2)", "byte_array[abcd]")
        chk("x'0123ABCD'.sub(3)", "byte_array[cd]")
        chk("x'0123ABCD'.sub(4)", "byte_array[]")
        chk("x'0123ABCD'.sub(5)", "rt_err:fn:byte_array.sub:range:4:5:4")
        chk("x'0123ABCD'.sub(-1)", "rt_err:fn:byte_array.sub:range:4:-1:4")
        chk("x'0123ABCD'.sub(1, 3)", "byte_array[23ab]")
        chk("x'0123ABCD'.sub(0, 4)", "byte_array[0123abcd]")
        chk("x'0123ABCD'.sub(1, 0)", "rt_err:fn:byte_array.sub:range:4:1:0")
        chk("x'0123ABCD'.sub(1, 5)", "rt_err:fn:byte_array.sub:range:4:1:5")
    }

    @Test fun testToList() {
        chk("x''.to_list()", "list<integer>[]")
        chk("x'1234abcd'.to_list()", "list<integer>[int[18],int[52],int[171],int[205]]")
    }

    @Test fun testToFromHex() {
        chk("x''.to_hex()", "text[]")
        chk("x'deadbeef'.to_hex()", "text[deadbeef]")

        chk("byte_array.from_hex('')", "byte_array[]")
        chk("byte_array.from_hex('deadbeef')", "byte_array[deadbeef]")
        chk("byte_array.from_hex('DEADBEEF')", "byte_array[deadbeef]")
        chk("byte_array.from_hex('123456')", "byte_array[123456]")

        chk("byte_array.from_hex()", "ct_err:expr:call:missing_args:[byte_array.from_hex]:[0:value]")
        chk("byte_array.from_hex(1234)", "ct_err:expr_call_badargs:[byte_array.from_hex]:[integer]")
        chk("byte_array.from_hex(true)", "ct_err:expr_call_badargs:[byte_array.from_hex]:[boolean]")

        chk("byte_array.from_hex('0')", "rt_err:fn:byte_array.from_hex")
        chk("byte_array.from_hex('0g')", "rt_err:fn:byte_array.from_hex")
        chk("byte_array.from_hex('-1')", "rt_err:fn:byte_array.from_hex")
        chk("byte_array.from_hex('+1')", "rt_err:fn:byte_array.from_hex")
    }

    @Test fun testToFromBase64() {
        chk("x''.to_base64()", "text[]")
        chk("x'00'.to_base64()", "text[AA==]")
        chk("x'0000'.to_base64()", "text[AAA=]")
        chk("x'000000'.to_base64()", "text[AAAA]")
        chk("x'00000000'.to_base64()", "text[AAAAAA==]")
        chk("x'FFFFFF'.to_base64()", "text[////]")
        chk("x'12345678'.to_base64()", "text[EjRWeA==]")
        chk("x'deadbeef'.to_base64()", "text[3q2+7w==]")
        chk("x'cafebabedeadbeef'.to_base64()", "text[yv66vt6tvu8=]")

        chk("byte_array.from_base64('')", "byte_array[]")
        chk("byte_array.from_base64('AA==')", "byte_array[00]")
        chk("byte_array.from_base64('AA')", "byte_array[00]")
        chk("byte_array.from_base64('AA=')", "rt_err:fn:byte_array.from_base64")
        chk("byte_array.from_base64('AAA=')", "byte_array[0000]")
        chk("byte_array.from_base64('AAA')", "byte_array[0000]")
        chk("byte_array.from_base64('AAAA')", "byte_array[000000]")
        chk("byte_array.from_base64('AAAAAA==')", "byte_array[00000000]")
        chk("byte_array.from_base64('////')", "byte_array[ffffff]")
        chk("byte_array.from_base64('EjRWeA==')", "byte_array[12345678]")
        chk("byte_array.from_base64('EjRWeA')", "byte_array[12345678]")
        chk("byte_array.from_base64('EjRWeA=')", "rt_err:fn:byte_array.from_base64")
        chk("byte_array.from_base64('3q2+7w==')", "byte_array[deadbeef]")
        chk("byte_array.from_base64('yv66vt6tvu8=')", "byte_array[cafebabedeadbeef]")
        chk("byte_array.from_base64('!@#%^')", "rt_err:fn:byte_array.from_base64")

        chk("byte_array.from_base64()", "ct_err:expr:call:missing_args:[byte_array.from_base64]:[0:value]")
        chk("byte_array.from_base64(1234)", "ct_err:expr_call_badargs:[byte_array.from_base64]:[integer]")
        chk("byte_array.from_base64(true)", "ct_err:expr_call_badargs:[byte_array.from_base64]:[boolean]")
    }

    @Test fun testIterable() {
        chk("_type_of(x'05000F80' @* {})", "text[list<integer>]")
        chk("x'05000F80' @* {}", "list<integer>[int[5],int[0],int[15],int[128]]")

        chkEx("{ for (x in x'05000F80') return _type_of(x); return '?'; }", "text[integer]")
        chkEx("{ val l = list<integer>(); for (x in x'05000F80') l.add(x); return l; }",
                "list<integer>[int[5],int[0],int[15],int[128]]")
    }

    @Test fun testRepeat() {
        chk("_type_of(x'123456'.repeat(3))", "text[byte_array]")

        chk("x'123456'.repeat(0)", "byte_array[]")
        chk("x'123456'.repeat(1)", "byte_array[123456]")
        chk("x'123456'.repeat(2)", "byte_array[123456123456]")
        chk("x'123456'.repeat(3)", "byte_array[123456123456123456]")
        chk("x'123456'.repeat(4)", "byte_array[123456123456123456123456]")
        chk("x'123456'.repeat(5)", "byte_array[123456123456123456123456123456]")

        chk("x''.repeat(3)", "byte_array[]")
        chk("x'12'.repeat(3)", "byte_array[121212]")
        chk("x'1234'.repeat(3)", "byte_array[123412341234]")

        chk("x'123456'.repeat(-1)", "rt_err:fn:byte_array.repeat:n_negative:-1")
        chk("x'123456'.repeat(-1234567890123456)", "rt_err:fn:byte_array.repeat:n_negative:-1234567890123456")
        chk("x'123456'.repeat(0x80000000)", "rt_err:fn:byte_array.repeat:n_out_of_range:2147483648")
        chk("x'123456'.repeat(0x7FFFFFFF)", "rt_err:fn:byte_array.repeat:too_big:6442450941")
    }

    @Test fun testReversed() {
        chk("_type_of(x'123456'.reversed())", "text[byte_array]")
        chk("x''.reversed()", "byte_array[]")
        chk("x'12'.reversed()", "byte_array[12]")
        chk("x'1234'.reversed()", "byte_array[3412]")
        chk("x'123456'.reversed()", "byte_array[563412]")
        chk("x'12345678'.reversed()", "byte_array[78563412]")
        chk("x'123456789a'.reversed()", "byte_array[9a78563412]")
    }
}
