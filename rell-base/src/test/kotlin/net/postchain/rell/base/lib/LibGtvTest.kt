/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.parse.GtvParser
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.utils.PostchainGtvUtils
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class LibGtvTest: BaseRellTest() {
    @Test fun testFromBytes() {
        chkToGtv("gtv.from_bytes(x'a30302017b')", gtv(123))
        chkToGtv("gtv.from_bytes(x'a60302017b')", gtv(BigInteger.valueOf(123)))
        chkToGtv("gtv.from_bytes(x'a2070c0548656c6c6f')", gtv("Hello"))
        chkToGtv("gtv.from_bytes(x'a511300fa303020101a303020102a303020103')", gtv(gtv(1), gtv(2), gtv(3)))
        chkToGtv("gtv.from_bytes(x'a410300e300c0c0548656c6c6fa30302017b')", gtv("Hello" to gtv(123)))
    }

    @Test fun testFromBytesOrNull() {
        chkToGtv("gtv.from_bytes_or_null(x'a30302017b')", gtv(123))
        chkToGtv("gtv.from_bytes_or_null(x'a60302017b')", gtv(BigInteger.valueOf(123)))
        chkToGtv("gtv.from_bytes_or_null(x'a2070c0548656c6c6f')", gtv("Hello"))
        chkToGtv("gtv.from_bytes_or_null(x'a511300fa303020101a303020102a303020103')", gtv(gtv(1), gtv(2), gtv(3)))
        chkToGtv("gtv.from_bytes_or_null(x'a410300e300c0c0548656c6c6fa30302017b')", gtv("Hello" to gtv(123)))

        chk("gtv.from_bytes_or_null(x'')", "null")
        chk("gtv.from_bytes_or_null(x'00')", "null")
        chk("gtv.from_bytes_or_null(x'1234')", "null")
        chk("gtv.from_bytes_or_null(x'ffff')", "null")

        chk("gtv.from_bytes(x'')", "rt_err:fn:gtv.from_bytes")
        chk("gtv.from_bytes(x'00')", "rt_err:fn:gtv.from_bytes")
        chk("gtv.from_bytes(x'1234')", "rt_err:fn:gtv.from_bytes")
        chk("gtv.from_bytes(x'ffff')", "rt_err:fn:gtv.from_bytes")
    }

    @Test fun testToFromJson() {
        chk("""gtv.from_json('{"x":123,"y":[4,5,6]}')""", """gtv[["x": 123, "y": [4, 5, 6]]]""")
        chk("""gtv.from_json(json('{"x":123,"y":[4,5,6]}'))""", """gtv[["x": 123, "y": [4, 5, 6]]]""")
        chk("""gtv.from_json('{"x":123,"y":[4,5,6]}').to_bytes()""",
                "byte_array[a424302230080c0178a30302017b30160c0179a511300fa303020104a303020105a303020106]")

        chk("gtv.from_bytes(x'a424302230080c0178a30302017b30160c0179a511300fa303020104a303020105a303020106')",
                """gtv[["x": 123, "y": [4, 5, 6]]]""")
        chk("gtv.from_bytes(x'a424302230080c0178a30302017b30160c0179a511300fa303020104a303020105a303020106').to_json()",
                """json[{"x":123,"y":[4,5,6]}]""")
        chk("''+gtv.from_bytes(x'a424302230080c0178a30302017b30160c0179a511300fa303020104a303020105a303020106').to_json()",
                """text[{"x":123,"y":[4,5,6]}]""")
    }

    @Test fun testToFromGtvBigInteger() {
        chkToGtv("(0L).to_gtv()", gtv(BigInteger.valueOf(0)))
        chkToGtv("(123L).to_gtv()", gtv(BigInteger.valueOf(123)))
        chkToGtv("(-456L).to_gtv()", gtv(BigInteger.valueOf(-456)))
        chkToGtv("(79228162514264337593543950335L).to_gtv()", gtv(BigInteger("79228162514264337593543950335")))

        chkToGtv("(0L).to_gtv_pretty()", gtv(BigInteger.valueOf(0)))
        chkToGtv("(123L).to_gtv_pretty()", gtv(BigInteger.valueOf(123)))
        chkToGtv("(-456L).to_gtv_pretty()", gtv(BigInteger.valueOf(-456)))

        chkFromGtv(gtv(BigInteger.valueOf(0)), "big_integer.from_gtv(g)", "bigint[0]")
        chkFromGtv(gtv(BigInteger.valueOf(123)), "big_integer.from_gtv(g)", "bigint[123]")
        chkFromGtv(gtv(BigInteger.valueOf(-456)), "big_integer.from_gtv(g)", "bigint[-456]")
        chkFromGtv(gtv(123), "big_integer.from_gtv(g)", "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER")
        chkFromGtv(gtv("123"), "big_integer.from_gtv(g)", "gtv_err:type:[big_integer]:BIGINTEGER:STRING")

        chkFromGtv(gtv(BigInteger.valueOf(123)), "big_integer.from_gtv_pretty(g)", "bigint[123]")
        chkFromGtv(gtv(BigInteger.valueOf(-456)), "big_integer.from_gtv_pretty(g)", "bigint[-456]")
        chkFromGtv(gtv(123), "big_integer.from_gtv_pretty(g)", "bigint[123]")
        chkFromGtv(gtv("123"), "big_integer.from_gtv_pretty(g)", "gtv_err:type:[big_integer]:BIGINTEGER:STRING")

        chkFromGtv("'Hello'", "big_integer.from_gtv(g)", "gtv_err:type:[big_integer]:BIGINTEGER:STRING")
        chkFromGtv("'Hello'", "big_integer.from_gtv_pretty(g)", "gtv_err:type:[big_integer]:BIGINTEGER:STRING")
        chkFromGtv("[]", "big_integer.from_gtv(g)", "gtv_err:type:[big_integer]:BIGINTEGER:ARRAY")
        chkFromGtv("[]", "big_integer.from_gtv_pretty(g)", "gtv_err:type:[big_integer]:BIGINTEGER:ARRAY")
        chkFromGtv("[123]", "big_integer.from_gtv(g)", "gtv_err:type:[big_integer]:BIGINTEGER:ARRAY")
        chkFromGtv("[123]", "big_integer.from_gtv_pretty(g)", "gtv_err:type:[big_integer]:BIGINTEGER:ARRAY")
    }

    @Test fun testToFromGtvBoolean() {
        chk("false.to_gtv()", "gtv[0]")
        chk("true.to_gtv()", "gtv[1]")
        chk("false.to_gtv_pretty()", "gtv[0]")

        chkFromGtv("0", "boolean.from_gtv(g)", "boolean[false]")
        chkFromGtv("1", "boolean.from_gtv(g)", "boolean[true]")
        chkFromGtv("-1", "boolean.from_gtv(g)", "gtv_err:type:[boolean]:bad_value:-1")
        chkFromGtv("2", "boolean.from_gtv(g)", "gtv_err:type:[boolean]:bad_value:2")

        chkFromGtv("0", "boolean.from_gtv_pretty(g)", "boolean[false]")
        chkFromGtv("1", "boolean.from_gtv_pretty(g)", "boolean[true]")
        chkFromGtv("-1", "boolean.from_gtv_pretty(g)", "gtv_err:type:[boolean]:bad_value:-1")
        chkFromGtv("2", "boolean.from_gtv_pretty(g)", "gtv_err:type:[boolean]:bad_value:2")

        chk("boolean.from_gtv((0L).to_gtv())", "gtv_err:type:[boolean]:INTEGER:BIGINTEGER")
        chk("boolean.from_gtv((1L).to_gtv())", "gtv_err:type:[boolean]:INTEGER:BIGINTEGER")
        chk("boolean.from_gtv((0).to_gtv())", "boolean[false]")
        chk("boolean.from_gtv((1).to_gtv())", "boolean[true]")
    }

    @Test fun testToFromGtvInteger() {
        chk("(0).to_gtv()", "gtv[0]")
        chk("(123).to_gtv()", "gtv[123]")
        chk("(-456).to_gtv()", "gtv[-456]")
        chk("(9223372036854775807).to_gtv()", "gtv[9223372036854775807]")
        chk("(0).to_gtv_pretty()", "gtv[0]")

        chkFromGtv("0", "integer.from_gtv(g)", "int[0]")
        chkFromGtv("123", "integer.from_gtv(g)", "int[123]")
        chkFromGtv("-456", "integer.from_gtv(g)", "int[-456]")
        chkFromGtv("9223372036854775807", "integer.from_gtv(g)", "int[9223372036854775807]")
        chkFromGtv("123", "integer.from_gtv_pretty(g)", "int[123]")
        chkFromGtv("-456", "integer.from_gtv_pretty(g)", "int[-456]")

        chkFromGtv("'Hello'", "integer.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("'Hello'", "integer.from_gtv_pretty(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("[]", "integer.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:ARRAY")
        chkFromGtv("[]", "integer.from_gtv_pretty(g)", "gtv_err:type:[integer]:INTEGER:ARRAY")
        chkFromGtv("[123]", "integer.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:ARRAY")
        chkFromGtv("[123]", "integer.from_gtv_pretty(g)", "gtv_err:type:[integer]:INTEGER:ARRAY")
    }

    @Test fun testToFromGtvDecimal() {
        chk("(0.0).to_gtv()", """gtv["0"]""")
        chk("(123.456).to_gtv()", """gtv["123.456"]""")
        chk("(-456.789).to_gtv()", """gtv["-456.789"]""")
        chk("(0.0).to_gtv_pretty()", """gtv["0"]""")
        chk("(123.456e+53).to_gtv_pretty()", """gtv["12345600000000000000000000000000000000000000000000000000"]""")
        chk("(123.456e-17).to_gtv_pretty()", """gtv["0.00000000000000123456"]""")

        chkFromGtv("'0'", "decimal.from_gtv(g)", "dec[0]")
        chkFromGtv("'0.0000'", "decimal.from_gtv(g)", "dec[0]")
        chkFromGtv("'123.456'", "decimal.from_gtv(g)", "dec[123.456]")
        chkFromGtv("'-456.789'", "decimal.from_gtv(g)", "dec[-456.789]")
        chkFromGtv("'123.456'", "decimal.from_gtv_pretty(g)", "dec[123.456]")
        chkFromGtv("'-456.789'", "decimal.from_gtv_pretty(g)", "dec[-456.789]")

        chkFromGtv("'123.456E10'", "decimal.from_gtv(g)", "dec[1234560000000]")
        chkFromGtv("'123.456E+10'", "decimal.from_gtv(g)", "dec[1234560000000]")
        chkFromGtv("'123.456E-10'", "decimal.from_gtv(g)", "dec[0.0000000123456]")
        chkFromGtv("'123.456e10'", "decimal.from_gtv(g)", "dec[1234560000000]")
        chkFromGtv("'123.456e+10'", "decimal.from_gtv(g)", "dec[1234560000000]")
        chkFromGtv("'123.456e-10'", "decimal.from_gtv(g)", "dec[0.0000000123456]")
        chkFromGtv("'-123.456E10'", "decimal.from_gtv(g)", "dec[-1234560000000]")
        chkFromGtv("'-123.456E+10'", "decimal.from_gtv(g)", "dec[-1234560000000]")
        chkFromGtv("'-123.456E-10'", "decimal.from_gtv(g)", "dec[-0.0000000123456]")
        chkFromGtv("'123E10'", "decimal.from_gtv(g)", "dec[1230000000000]")
        chkFromGtv("'123E+10'", "decimal.from_gtv(g)", "dec[1230000000000]")
        chkFromGtv("'123E-10'", "decimal.from_gtv(g)", "dec[0.0000000123]")
        chkFromGtv("'-123E10'", "decimal.from_gtv(g)", "dec[-1230000000000]")
        chkFromGtv("'-123E+10'", "decimal.from_gtv(g)", "dec[-1230000000000]")
        chkFromGtv("'-123E-10'", "decimal.from_gtv(g)", "dec[-0.0000000123]")

        chkFromGtv("0", "decimal.from_gtv(g)", "dec[0]")
        chkFromGtv("123", "decimal.from_gtv(g)", "dec[123]")
        chkFromGtv("-456", "decimal.from_gtv(g)", "dec[-456]")

        chkFromGtv("'Hello'", "decimal.from_gtv(g)", "rt_err:decimal:invalid:Hello")
        chkFromGtv("'Hello'", "decimal.from_gtv_pretty(g)", "rt_err:decimal:invalid:Hello")
        chkFromGtv("[]", "decimal.from_gtv(g)", "gtv_err:type:[decimal]:STRING:ARRAY")
        chkFromGtv("[]", "decimal.from_gtv_pretty(g)", "gtv_err:type:[decimal]:STRING:ARRAY")
        chkFromGtv("[123]", "decimal.from_gtv(g)", "gtv_err:type:[decimal]:STRING:ARRAY")
        chkFromGtv("[123]", "decimal.from_gtv_pretty(g)", "gtv_err:type:[decimal]:STRING:ARRAY")
    }

    @Test fun testToFromGtvText() {
        chk("''.to_gtv()", """gtv[""]""")
        chk("'Hello'.to_gtv()", """gtv["Hello"]""")
        chk("''.to_gtv_pretty()", """gtv[""]""")
        chk("'Hello'.to_gtv_pretty()", """gtv["Hello"]""")

        chkFromGtv("''", "text.from_gtv(g)", "text[]")
        chkFromGtv("'Hello'", "text.from_gtv(g)", "text[Hello]")
        chkFromGtv("''", "text.from_gtv_pretty(g)", "text[]")
        chkFromGtv("'Hello'", "text.from_gtv_pretty(g)", "text[Hello]")

        chkFromGtv("123", "text.from_gtv(g)", "gtv_err:type:[text]:STRING:INTEGER")
        chkFromGtv("123", "text.from_gtv_pretty(g)", "gtv_err:type:[text]:STRING:INTEGER")
    }

    @Test fun testToFromGtvByteArray() {
        chk("x''.to_gtv()", """gtv[x""]""")
        chk("x'0123abcd'.to_gtv()", """gtv[x"0123ABCD"]""")
        chk("x''.to_gtv_pretty()", """gtv[x""]""")
        chk("x'0123abcd'.to_gtv_pretty()", """gtv[x"0123ABCD"]""")

        chkFromGtv("''", "byte_array.from_gtv(g)", "byte_array[]")
        chkFromGtv("'0123abcd'", "byte_array.from_gtv(g)", "byte_array[0123abcd]")
        chkFromGtv("'0123ABCD'", "byte_array.from_gtv(g)", "byte_array[0123abcd]")
        chkFromGtv("''", "byte_array.from_gtv_pretty(g)", "byte_array[]")
        chkFromGtv("'0123abcd'", "byte_array.from_gtv_pretty(g)", "byte_array[0123abcd]")

        chkFromGtv("'hello'", "byte_array.from_gtv(g)", "gtv_err:type:[byte_array]:bad_value:STRING")
        chkFromGtv("123", "byte_array.from_gtv(g)", "gtv_err:type:[byte_array]:BYTEARRAY,STRING:INTEGER")
        chkFromGtv("123", "byte_array.from_gtv_pretty(g)", "gtv_err:type:[byte_array]:BYTEARRAY,STRING:INTEGER")
    }

    @Test fun testToFromGtvRowid() {
        tstCtx.useSql = true
        def("entity user { name; }")
        insert("c0.user", "name", "0,'Bob'")
        insert("c0.user", "name", "123,'Alice'")

        chk("(user@{'Bob'}).to_gtv()", "gtv[0]")
        chk("(user@{'Alice'}).to_gtv()", "gtv[123]")
        chk("(user@{'Bob'}).to_gtv_pretty()", "gtv[0]")

        chkFromGtv("0", "rowid.from_gtv(g)", "rowid[0]")
        chkFromGtv("123", "rowid.from_gtv(g)", "rowid[123]")
        chkFromGtv("-456", "rowid.from_gtv(g)", "rt_err:fn:[rowid]:from_gtv:false")
        chkFromGtv("123", "rowid.from_gtv_pretty(g)", "rowid[123]")
        chkFromGtv("-456", "rowid.from_gtv_pretty(g)", "rt_err:fn:[rowid]:from_gtv:true")

        chkFromGtv("'Hello'", "rowid.from_gtv(g)", "gtv_err:type:[rowid]:INTEGER:STRING")
        chkFromGtv("'Hello'", "rowid.from_gtv_pretty(g)", "gtv_err:type:[rowid]:INTEGER:STRING")
        chkFromGtv("[]", "rowid.from_gtv(g)", "gtv_err:type:[rowid]:INTEGER:ARRAY")
        chkFromGtv("[]", "rowid.from_gtv_pretty(g)", "gtv_err:type:[rowid]:INTEGER:ARRAY")
        chkFromGtv("[123]", "rowid.from_gtv(g)", "gtv_err:type:[rowid]:INTEGER:ARRAY")
        chkFromGtv("[123]", "rowid.from_gtv_pretty(g)", "gtv_err:type:[rowid]:INTEGER:ARRAY")
    }

    @Test fun testToFromGtvJson() {
        chk("json('123').to_gtv()", """gtv["123"]""")
        chk("json('{}').to_gtv()", """gtv["{}"]""")
        chk("json('[]').to_gtv()", """gtv["[]"]""")
        chk("json('[123]').to_gtv()", """gtv["[123]"]""")
        chk("json('[123]').to_gtv_pretty()", """gtv["[123]"]""")

        chkFromGtv("'123'", "json.from_gtv(g)", "json[123]")
        chkFromGtv("'{}'", "json.from_gtv(g)", "json[{}]")
        chkFromGtv("'[]'", "json.from_gtv(g)", "json[[]]")
        chkFromGtv("'[123]'", "json.from_gtv(g)", "json[[123]]")
        chkFromGtv("'123'", "json.from_gtv_pretty(g)", "json[123]")
        chkFromGtv("'{}'", "json.from_gtv_pretty(g)", "json[{}]")
        chkFromGtv("'[]'", "json.from_gtv_pretty(g)", "json[[]]")
        chkFromGtv("'[123]'", "json.from_gtv_pretty(g)", "json[[123]]")

        chkFromGtv("{}", "json.from_gtv(g)", "gtv_err:type:[json]:STRING:DICT")
        chkFromGtv("[]", "json.from_gtv(g)", "gtv_err:type:[json]:STRING:ARRAY")
        chkFromGtv("[123]", "json.from_gtv(g)", "gtv_err:type:[json]:STRING:ARRAY")
        chkFromGtv("'Hello'", "json.from_gtv(g)", "gtv_err:type:[json]:bad_value")
        chkFromGtv("{}", "json.from_gtv_pretty(g)", "gtv_err:type:[json]:STRING:DICT")
        chkFromGtv("[]", "json.from_gtv_pretty(g)", "gtv_err:type:[json]:STRING:ARRAY")
    }

    @Test fun testToFromGtvEnum() {
        def("enum E {A,B,C}")

        chk("E.A.to_gtv()", "gtv[0]")
        chk("E.B.to_gtv()", "gtv[1]")
        chk("E.C.to_gtv()", "gtv[2]")

        chk("E.A.to_gtv_pretty()", """gtv["A"]""")
        chk("E.B.to_gtv_pretty()", """gtv["B"]""")
        chk("E.C.to_gtv_pretty()", """gtv["C"]""")

        chkFromGtv("0", "E.from_gtv(g)", "E[A]")
        chkFromGtv("1", "E.from_gtv(g)", "E[B]")
        chkFromGtv("2", "E.from_gtv(g)", "E[C]")
        chkFromGtv("-1", "E.from_gtv(g)", "gtv_err:type:[E]:enum:bad_value:-1")
        chkFromGtv("3", "E.from_gtv(g)", "gtv_err:type:[E]:enum:bad_value:3")
        chkFromGtv("'A'", "E.from_gtv(g)", "gtv_err:type:[E]:INTEGER:STRING")

        chkFromGtv("'A'", "E.from_gtv_pretty(g)", "E[A]")
        chkFromGtv("'B'", "E.from_gtv_pretty(g)", "E[B]")
        chkFromGtv("'C'", "E.from_gtv_pretty(g)", "E[C]")
        chkFromGtv("'D'", "E.from_gtv_pretty(g)", "gtv_err:type:[E]:enum:bad_value:D")
        chkFromGtv("0", "E.from_gtv_pretty(g)", "E[A]")
    }

    @Test fun testToFromGtvOther() {
        chk("gtv.from_json('{}').to_gtv()", "gtv[[:]]")
        chk("gtv.from_json('[]').to_gtv()", "gtv[[]]")
        chk("gtv.from_json('[123]').to_gtv()", "gtv[[123]]")
        chk("gtv.from_json('[123]').to_gtv_pretty()", "gtv[[123]]")

        chk("range(10).to_gtv()", "ct_err:fn:invalid:range:to_gtv")
        chk("range(10).to_gtv_pretty()", "ct_err:fn:invalid:range:to_gtv_pretty")
        chkFromGtv("''", "range.from_gtv(g)", "ct_err:fn:invalid:range:from_gtv")
        chkFromGtv("''", "range.from_gtv_pretty(g)", "ct_err:fn:invalid:range:from_gtv_pretty")
    }

    @Test fun testToFromGtvList() {
        chk("list<integer>().to_gtv()", "gtv[[]]")
        chk("[123].to_gtv()", "gtv[[123]]")
        chk("[123, 456].to_gtv()", "gtv[[123, 456]]")
        chk("[123].to_gtv_pretty()", "gtv[[123]]")

        chkFromGtv("[]", "list<integer>.from_gtv(g)", "list<integer>[]")
        chkFromGtv("[123]", "list<integer>.from_gtv(g)", "list<integer>[int[123]]")
        chkFromGtv("['Hello']", "list<integer>.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("123", "list<integer>.from_gtv(g)", "gtv_err:type:[list<integer>]:ARRAY:INTEGER")
        chkFromGtv("[]", "list<range>.from_gtv(g)", "ct_err:fn:invalid:list<range>:from_gtv")

        chkFromGtv("[]", "list<integer>.from_gtv_pretty(g)", "list<integer>[]")
        chkFromGtv("[123]", "list<integer>.from_gtv_pretty(g)", "list<integer>[int[123]]")
        chkFromGtv("['Hello']", "list<integer>.from_gtv_pretty(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("123", "list<integer>.from_gtv_pretty(g)", "gtv_err:type:[list<integer>]:ARRAY:INTEGER")
        chkFromGtv("[]", "list<range>.from_gtv_pretty(g)", "ct_err:fn:invalid:list<range>:from_gtv_pretty")
    }

    @Test fun testToFromGtvSet() {
        chk("set<integer>().to_gtv()", "gtv[[]]")
        chk("set([123]).to_gtv()", "gtv[[123]]")
        chk("set([123, 456]).to_gtv()", "gtv[[123, 456]]")
        chk("set([123]).to_gtv_pretty()", "gtv[[123]]")

        chkFromGtv("[]", "set<integer>.from_gtv(g)", "set<integer>[]")
        chkFromGtv("[123]", "set<integer>.from_gtv(g)", "set<integer>[int[123]]")
        chkFromGtv("[123,'Hello']", "set<integer>.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("['Hello']", "set<integer>.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("123", "set<integer>.from_gtv(g)", "gtv_err:type:[set<integer>]:ARRAY:INTEGER")

        chkFromGtv("[]", "set<integer>.from_gtv_pretty(g)", "set<integer>[]")
        chkFromGtv("[123,456]", "set<integer>.from_gtv_pretty(g)", "set<integer>[int[123],int[456]]")
        chkFromGtv("['Hello']", "set<integer>.from_gtv_pretty(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("123", "set<integer>.from_gtv_pretty(g)", "gtv_err:type:[set<integer>]:ARRAY:INTEGER")
    }

    @Test fun testToFromGtvMap() {
        chk("map<text,integer>().to_gtv()", "gtv[[:]]")
        chk("['Hello':123].to_gtv()", """gtv[["Hello": 123]]""")
        chk("['Hello':123,'Bye':456].to_gtv()", """gtv[["Bye": 456, "Hello": 123]]""")
        chk("['Hello':123].to_gtv_pretty()", """gtv[["Hello": 123]]""")

        chk("map<integer,text>().to_gtv()", "gtv[[]]")
        chk("[123:'Hello'].to_gtv()", """gtv[[[123, "Hello"]]]""")
        chk("[123:'Hello',456:'Bye'].to_gtv()", """gtv[[[123, "Hello"], [456, "Bye"]]]""")
        chk("[123:'Hello',456:'Bye'].to_gtv_pretty()", """gtv[[[123, "Hello"], [456, "Bye"]]]""")

        chkFromGtv("{}", "map<text,integer>.from_gtv(g)", "map<text,integer>[]")
        chkFromGtv("[]", "map<text,integer>.from_gtv(g)", "map<text,integer>[]")
        chkFromGtv("{'Hello':123}", "map<text,integer>.from_gtv(g)", "map<text,integer>[text[Hello]=int[123]]")
        chkFromGtv("[['Hello',123]]", "map<text,integer>.from_gtv(g)", "map<text,integer>[text[Hello]=int[123]]")
        chkFromGtv("123", "map<text,integer>.from_gtv(g)", "gtv_err:type:[map<text,integer>]:ARRAY:INTEGER")
        chkFromGtv("'Hello'", "map<text,integer>.from_gtv(g)", "gtv_err:type:[map<text,integer>]:ARRAY:STRING")
        chkFromGtv("{}", "map<text,integer>.from_gtv_pretty(g)", "map<text,integer>[]")
        chkFromGtv("[]", "map<text,integer>.from_gtv_pretty(g)", "map<text,integer>[]")
        chkFromGtv("{'Hello':123}", "map<text,integer>.from_gtv_pretty(g)", "map<text,integer>[text[Hello]=int[123]]")

        chkFromGtv("[]", "map<integer,text>.from_gtv(g)", "map<integer,text>[]")
        chkFromGtv("[[123,'Hello']]", "map<integer,text>.from_gtv(g)", "map<integer,text>[int[123]=text[Hello]]")
        chkFromGtv("[[123,'Hello'],[456,'Bye']]", "map<integer,text>.from_gtv(g)",
                "map<integer,text>[int[123]=text[Hello],int[456]=text[Bye]]")
        chkFromGtv("{}", "map<integer,text>.from_gtv(g)", "gtv_err:type:[map<integer,text>]:ARRAY:DICT")
        chkFromGtv("[123]", "map<integer,text>.from_gtv(g)", "gtv_err:type:[map<integer,text>]:ARRAY:INTEGER")
        chkFromGtv("[[]]", "map<integer,text>.from_gtv(g)", "gtv_err:type:[map<integer,text>]:map_entry_size:2:0")
        chkFromGtv("[[123]]", "map<integer,text>.from_gtv(g)", "gtv_err:type:[map<integer,text>]:map_entry_size:2:1")
        chkFromGtv("[['Hello',123]]", "map<integer,text>.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:STRING")
        chkFromGtv("[[123,'Hello','Bye']]", "map<integer,text>.from_gtv(g)",
                "gtv_err:type:[map<integer,text>]:map_entry_size:2:3")
        chkFromGtv("[[123,'Hello'],[123,'Bye']]", "map<integer,text>.from_gtv(g)", "gtv_err:map_dup_key:int[123]")
        chkFromGtv("[['Hello',123],['Bye',456]]", "map<text,integer>.from_gtv(g)",
                "map<text,integer>[text[Hello]=int[123],text[Bye]=int[456]]")
        chkFromGtv("[['Hello',123],['Bye',456]]", "map<text,integer>.from_gtv_pretty(g)",
                "map<text,integer>[text[Hello]=int[123],text[Bye]=int[456]]")
        chkFromGtv("{'Hello':123,'Bye':456}", "map<text,integer>.from_gtv(g)",
                "map<text,integer>[text[Bye]=int[456],text[Hello]=int[123]]")
        chkFromGtv("{'Hello':123,'Bye':456}", "map<text,integer>.from_gtv_pretty(g)",
                "map<text,integer>[text[Bye]=int[456],text[Hello]=int[123]]")

        chkFromGtv("[]", "map<integer,text>.from_gtv_pretty(g)", "map<integer,text>[]")
        chkFromGtv("[[123,'Hello']]", "map<integer,text>.from_gtv_pretty(g)", "map<integer,text>[int[123]=text[Hello]]")
    }

    @Test fun testToGtvTuple() {
        def("struct A { t: (x: integer, y: text); }")
        def("struct B { t: (x: integer, text); }")
        def("struct C { t: (s: (x: boolean, y: text), k: integer); }")

        chk("(123,).to_gtv()", "gtv[[123]]")
        chk("(123,'Hello').to_gtv()", """gtv[[123, "Hello"]]""")
        chk("(x=123,y='Hello').to_gtv()", """gtv[[123, "Hello"]]""")
        chk("(x=123,'Hello').to_gtv()", """gtv[[123, "Hello"]]""")
        chk("(123,y='Hello').to_gtv()", """gtv[[123, "Hello"]]""")

        chk("(123,'Hello').to_gtv_pretty()", """gtv[[123, "Hello"]]""")
        chk("(x=123,y='Hello').to_gtv_pretty()", """gtv[["x": 123, "y": "Hello"]]""")
        chk("(x=123,'Hello').to_gtv_pretty()", """gtv[[123, "Hello"]]""")
        chk("(123,y='Hello').to_gtv_pretty()", """gtv[[123, "Hello"]]""")

        chkFromGtv("[[123,'Hello']]", "A.from_gtv(g)", "A[t=(x=int[123],y=text[Hello])]")
        chkFromGtv("[{'x':123,'y':'Hello'}]", "A.from_gtv(g)", "gtv_err:type:[(x:integer,y:text)]:ARRAY:DICT:attr:[A]:t")
        chkFromGtv("{'t':[123,'Hello']}", "A.from_gtv(g)", "gtv_err:type:[A]:ARRAY:DICT")
        chkFromGtv("[[[1,'A'],123]]", "C.from_gtv(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("{'t':[[1,'A'],123]}", "C.from_gtv(g)", "gtv_err:type:[C]:ARRAY:DICT")
        chkFromGtv("{'t':{'s':[1,'A'],'k':123}}", "C.from_gtv(g)", "gtv_err:type:[C]:ARRAY:DICT")
        chkFromGtv("{'t':{'s':{'x':1,'y':'A'},'k':123}}", "C.from_gtv(g)", "gtv_err:type:[C]:ARRAY:DICT")
        chkFromGtv("[{'s':[1,'A'],'k':123}]", "C.from_gtv(g)", "gtv_err:type:[(s:(x:boolean,y:text),k:integer)]:ARRAY:DICT:attr:[C]:t")
        chkFromGtv("[{'s':{'x':1,'y':'A'},'k':123}]", "C.from_gtv(g)", "gtv_err:type:[(s:(x:boolean,y:text),k:integer)]:ARRAY:DICT:attr:[C]:t")
        chkFromGtv("[[{'x':1,'y':'A'},123]]", "C.from_gtv(g)", "gtv_err:type:[(x:boolean,y:text)]:ARRAY:DICT:attr:[C]:t")

        chkFromGtv("[[123,'Hello']]", "A.from_gtv_pretty(g)", "A[t=(x=int[123],y=text[Hello])]")
        chkFromGtv("[{'x':123,'y':'Hello'}]", "A.from_gtv_pretty(g)", "A[t=(x=int[123],y=text[Hello])]")
        chkFromGtv("{'t':[123,'Hello']}", "A.from_gtv_pretty(g)", "A[t=(x=int[123],y=text[Hello])]")
        chkFromGtv("{'t':{'x':123,'y':'Hello'}}", "A.from_gtv_pretty(g)", "A[t=(x=int[123],y=text[Hello])]")
        chkFromGtv("[[[1,'A'],123]]", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("{'t':[[1,'A'],123]}", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("{'t':{'s':[1,'A'],'k':123}}", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("{'t':{'s':{'x':1,'y':'A'},'k':123}}", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("[{'s':[1,'A'],'k':123}]", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("[{'s':{'x':1,'y':'A'},'k':123}]", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
        chkFromGtv("[[{'x':1,'y':'A'},123]]", "C.from_gtv_pretty(g)", "C[t=(s=(x=boolean[true],y=text[A]),k=int[123])]")
    }

    @Test fun testToFromGtvStruct() {
        def("struct rec { x: integer; y: text; }")
        def("struct no_gtv { r: range; }")

        chk("rec(123,'Hello').to_gtv()", """gtv[[123, "Hello"]]""")
        chk("no_gtv(range(10)).to_gtv()", "ct_err:fn:invalid:no_gtv:to_gtv")
        chk("rec(123,'Hello').to_gtv_pretty()", """gtv[["x": 123, "y": "Hello"]]""")
        chk("no_gtv(range(10)).to_gtv_pretty()", "ct_err:fn:invalid:no_gtv:to_gtv_pretty")

        chkFromGtv("[123,'Hello']", "rec.from_gtv(g)", "rec[x=int[123],y=text[Hello]]")
        chkFromGtv("[]", "rec.from_gtv(g)", "gtv_err:struct_size:rec:2:0")
        chkFromGtv("[123]", "rec.from_gtv(g)", "gtv_err:struct_size:rec:2:1")
        chkFromGtv("['Hello',123]", "rec.from_gtv(g)", "gtv_err:type:[integer]:INTEGER:STRING:attr:[rec]:x")
        chkFromGtv("{'x':123,'y':'Hello'}", "rec.from_gtv(g)", "gtv_err:type:[rec]:ARRAY:DICT")

        chkFromGtv("{'x':123,'y':'Hello'}", "rec.from_gtv_pretty(g)", "rec[x=int[123],y=text[Hello]]")
        chkFromGtv("{'y':'Hello','x':123}", "rec.from_gtv_pretty(g)", "rec[x=int[123],y=text[Hello]]")
        chkFromGtv("{}", "rec.from_gtv_pretty(g)", "gtv_err:struct_nokey:rec:x")
        chkFromGtv("{'x':123}", "rec.from_gtv_pretty(g)", "gtv_err:struct_nokey:rec:y")
        chkFromGtv("{'y':'Hello'}", "rec.from_gtv_pretty(g)", "gtv_err:struct_nokey:rec:x")
        chkFromGtv("{'y':123,'x':'Hello'}", "rec.from_gtv_pretty(g)", "gtv_err:type:[integer]:INTEGER:STRING:attr:[rec]:x")
        chkFromGtv("[]", "rec.from_gtv(g)", "gtv_err:struct_size:rec:2:0")
        chkFromGtv("[123,'Hello']", "rec.from_gtv_pretty(g)", "rec[x=int[123],y=text[Hello]]")
        chkFromGtv("[]", "rec.from_gtv_pretty(g)", "gtv_err:struct_size:rec:2:0")
        chkFromGtv("[]", "no_gtv.from_gtv(g)", "ct_err:fn:invalid:no_gtv:from_gtv")
        chkFromGtv("[]", "no_gtv.from_gtv_pretty(g)", "ct_err:fn:invalid:no_gtv:from_gtv_pretty")
    }

    @Test fun testToFromGtvEntity() {
        tstCtx.useSql = true
        def("entity user { name; }")
        def("object state { mutable value: integer = 0; }")
        insert("c0.user", "name", "5,'Bob'")

        chk("(user@{}).to_gtv()", "gtv[5]")
        chk("(user@{}).to_gtv_pretty()", "gtv[5]")

        chkFromGtv("5", "user.from_gtv(g)", "user[5]")
        chkFromGtv("'X'", "user.from_gtv(g)", "gtv_err:type:[user]:INTEGER:STRING")
        chkFromGtv("4", "user.from_gtv(g)", "gtv_err:obj_missing:[user]:4")
        chkFromGtv("5", "user.from_gtv_pretty(g)", "user[5]")
        chkFromGtv("'X'", "user.from_gtv_pretty(g)", "gtv_err:type:[user]:INTEGER:STRING")

        chk("state.to_gtv()", "ct_err:fn:invalid:state:to_gtv")
        chk("state.to_gtv_pretty()", "ct_err:fn:invalid:state:to_gtv_pretty")
    }

    @Test fun testToGtvNull() {
        chk("null.to_gtv()", "gtv[null]")
        chk("null.to_gtv_prety()", "ct_err:unknown_member:[null]:to_gtv_prety")
        chk("integer.from_gtv(null.to_gtv())", "gtv_err:type:[integer]:INTEGER:NULL")
        chk("null?.to_gtv()", "ct_err:[expr_safemem_type:[null]:to_gtv][unknown_member:[null]:to_gtv]")
        chk("(null).to_gtv()", "ct_err:unknown_member:[null]:to_gtv")
        chk("[null][0].to_gtv()", "ct_err:unknown_member:[null]:to_gtv")
        chk("_nullable_int(123).to_gtv()", "ct_err:expr_mem_null:integer?:to_gtv")
    }

    @Test fun testJsonNumberTruncation() {
        chkFromGtv("9223372036854775807", "integer.from_gtv(g)", "int[9223372036854775807]")
        chkFromGtv("9223372036854775808", "integer.from_gtv(g)", "rt_err:fn:gtv.from_json(text)")
        chkFromGtv("-9223372036854775808", "integer.from_gtv(g)", "int[-9223372036854775808]")
        chkFromGtv("-9223372036854775809", "integer.from_gtv(g)", "rt_err:fn:gtv.from_json(text)")
        chkFromGtv("123.456", "integer.from_gtv(g)", "rt_err:fn:gtv.from_json(text)")
        chkFromGtv("123.0", "integer.from_gtv(g)", "int[123]")
        chkFromGtv("123.0000000001", "integer.from_gtv(g)", "rt_err:fn:gtv.from_json(text)")

        chkFromGtv("9223372036854775807", "decimal.from_gtv(g)", "dec[9223372036854775807]")
        chkFromGtv("-9223372036854775808", "decimal.from_gtv(g)", "dec[-9223372036854775808]")
        chkFromGtv("9223372036854775808", "decimal.from_gtv(g)", "rt_err:fn:gtv.from_json(text)") // Probably should work
        chkFromGtv("-9223372036854775809", "decimal.from_gtv(g)", "rt_err:fn:gtv.from_json(text)") // Probably should work
        chkFromGtv("123.456", "decimal.from_gtv(g)", "rt_err:fn:gtv.from_json(text)")
    }

    @Test fun testToText() {
        chk("'' + (true).to_gtv()", "text[1]")
        chk("'' + (123).to_gtv()", "text[123]")
        chk("'' + (123L).to_gtv()", "text[123L]")
        chk("'' + (123.456).to_gtv()", "text[\"123.456\"]")
        chk("'' + ('Hello').to_gtv()", "text[\"Hello\"]")
        chk("'' + (x'1234').to_gtv()", "text[\"1234\"]")
    }

    private fun chkFromGtv(gtv: String, expr: String, expected: String) = chkFromGtv(tst, gtv, expr, expected)

    private fun chkFromGtv(gtv: Gtv, expr: String, expected: String) {
        val bytes = PostchainGtvUtils.gtvToBytes(gtv)
        val hex = bytes.toHex()
        val code = """{ val g = gtv.from_bytes(x'$hex'); return $expr; }"""
        tst.chkEx(code, expected)
    }

    private fun chkToGtv(expr: String, expected: Gtv) {
        val actual = tst.callQueryGtv("query q() = $expr;", "q", listOf())
        assertEquals(expected, actual)
    }

    companion object {
        fun chkFromGtv(tst: RellCodeTester, gtv: String, expr: String, expected: String) {
            val gtv2 = gtv.replace('\'', '"')
            val code = """{ val g = gtv.from_json('$gtv2'); return $expr; }"""
            tst.chkEx(code, expected)
        }
    }
}
