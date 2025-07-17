/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.testutils.BaseExprTest
import kotlin.test.Test

abstract class LibTextExprTest: BaseExprTest() {
    class LibTextExprIpTest: LibTextExprTest()
    class LibTextExprDbTest: LibTextExprTest()

    @Test fun testCharAt() {
        chkExpr("#0.char_at(#1)", "int[72]", vText("Hello"), vInt(0))
        chkExpr("#0.char_at(#1)", "int[101]", vText("Hello"), vInt(1))
        chkExpr("#0.char_at(#1)", "int[108]", vText("Hello"), vInt(2))
        chkExpr("#0.char_at(#1)", "int[108]", vText("Hello"), vInt(3))
        chkExpr("#0.char_at(#1)", "int[111]", vText("Hello"), vInt(4))
        chkExpr("#0.char_at(#1)", rtErr("fn:text.char_at:index:5:5"), vText("Hello"), vInt(5))
        chkExpr("#0.char_at(#1)", rtErr("fn:text.char_at:index:5:-1"), vText("Hello"), vInt(-1))
        chkExpr("#0.char_at(#1)", "int[32]", vText("Hello World"), vInt(5))

        chkExpr("#0.char_at(0)", "int[72]", vText("Hello"))
        chkExpr("#0.char_at(1)", "int[101]", vText("Hello"))
        chkExpr("#0.char_at(2)", "int[108]", vText("Hello"))
        chkExpr("#0.char_at(3)", "int[108]", vText("Hello"))
        chkExpr("#0.char_at(4)", "int[111]", vText("Hello"))
        chkExpr("#0.char_at(5)", rtErr("fn:text.char_at:index:5:5"), vText("Hello"))
        chkExpr("#0.char_at(-1)", rtErr("fn:text.char_at:index:5:-1"), vText("Hello"))
    }

    @Test fun testContains() {
        chkExpr("#0.contains('Hello')", "boolean[true]", vText("Hello"), vText("Hello"))
        chkExpr("#0.contains('ello')", "boolean[true]", vText("Hello"), vText("ello"))
        chkExpr("#0.contains('ll')", "boolean[true]", vText("Hello"), vText("ll"))
        chkExpr("#0.contains('lo')", "boolean[true]", vText("Hello"), vText("lo"))
        chkExpr("#0.contains('hello')", "boolean[false]", vText("Hello"), vText("hello"))
        chkExpr("#0.contains('L')", "boolean[false]", vText("Hello"), vText("L"))
        chkExpr("#0.contains('Hello1')", "boolean[false]", vText("Hello"), vText("Hello1"))
    }

    @Test fun testEmpty() {
        chkExpr("#0.empty()", "boolean[true]", vText(""))
        chkExpr("#0.empty()", "boolean[false]", vText(" "))
        chkExpr("#0.empty()", "boolean[false]", vText("X"))
        chkExpr("#0.empty()", "boolean[false]", vText("Hello"))
    }

    @Test fun testEndsWith() {
        chkExpr("#0.ends_with(#1)", "boolean[true]", vText("Hello"), vText("Hello"))
        chkExpr("#0.ends_with(#1)", "boolean[true]", vText("Hello"), vText("ello"))
        chkExpr("#0.ends_with(#1)", "boolean[true]", vText("Hello"), vText("o"))
        chkExpr("#0.ends_with(#1)", "boolean[true]", vText("Hello"), vText(""))
        chkExpr("#0.ends_with(#1)", "boolean[false]", vText("Hello"), vText("hello"))
        chkExpr("#0.ends_with(#1)", "boolean[false]", vText("Hello"), vText("XHello"))
    }

    @Test fun testIndexOf() {
        chkExpr("#0.index_of(#1)", "int[0]", vText("Hello"), vText("Hello"))
        chkExpr("#0.index_of(#1)", "int[1]", vText("Hello"), vText("ello"))
        chkExpr("#0.index_of(#1)", "int[2]", vText("Hello"), vText("ll"))
        chkExpr("#0.index_of(#1)", "int[2]", vText("Hello"), vText("l"))
        chkExpr("#0.index_of(#1)", "int[3]", vText("Hello"), vText("lo"))
        chkExpr("#0.index_of(#1)", "int[-1]", vText("Hello"), vText("hello"))
        chkExpr("#0.index_of(#1)", "int[-1]", vText("Hello"), vText("L"))
    }

    @Test fun testLike() {
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("Hello"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("hello"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("HELLO"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("Hell"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("ello"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText(""))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("Hello!"))

        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("H%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("%o"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("%o%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("%ll%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("H%o"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("h%"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("%O"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("o%"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("%H"))

        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("H_l_o"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("_e_l_"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("_____"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("H____"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("____o"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello"), vText("Hell_"))

        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("Hello_"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("_Hello"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("H_l__o"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("______"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("____"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("H_____"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("_____o"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("H___"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("h____"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("____O"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("Hel_"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello"), vText("_Hell"))

        chkExpr("#0.like(#1)", "boolean[true]", vText(""), vText("%"))
        chkExpr("#0.like(#1)", "boolean[false]", vText(""), vText("_"))
        chkExpr("#0.like(#1)", "boolean[false]", vText(""), vText("%_"))
        chkExpr("#0.like(#1)", "boolean[false]", vText(""), vText("_%"))

        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("Hello%World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("Hello%%World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("Hello_World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("H% W%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("%Hello%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("%World%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("%H___o%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("%W___d%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("H%o W%d"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("H%o_W%d"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("%o_W%"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("_%_ _%_"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("___%_ _%___"))

        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("Hello%world"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("Hello__World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("h% w%"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("H_ W_"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("%Hello"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("%Hello_"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("World%"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("_World%"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("% ___%___"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("___%___ %"))

        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("Hello_World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("Hello\\_World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello_World"), vText("Hello\\_World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello%World"), vText("Hello\\_World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello World"), vText("Hello%World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello World"), vText("Hello\\%World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello%World"), vText("Hello\\%World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello_World"), vText("Hello\\%World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello\\World"), vText("Hello\\World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello\\World"), vText("Hello\\\\World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("HelloWorld"), vText("Hello\\World"))

        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello\nWorld"), vText("Hello\nWorld"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello\nWorld"), vText("Hello_World"))
        chkExpr("#0.like(#1)", "boolean[true]", vText("Hello\nWorld"), vText("Hello%World"))
        chkExpr("#0.like(#1)", "boolean[false]", vText("Hello\nWorld"), vText("Hello World"))
    }

    @Test fun testRepeat() {
        chkExpr("#0.repeat(#1)", "text[]", vText(""), vInt(0))
        chkExpr("#0.repeat(#1)", "text[]", vText("abc"), vInt(0))
        chkExpr("#0.repeat(#1)", "text[abc]", vText("abc"), vInt(1))
        chkExpr("#0.repeat(#1)", "text[abcabc]", vText("abc"), vInt(2))
        chkExpr("#0.repeat(#1)", "text[abcabcabc]", vText("abc"), vInt(3))
        chkExpr("#0.repeat(#1)", rtErr("fn:text.repeat:n_negative:-1"), vText("abc"), vInt(-1))
        chkExpr("#0.repeat(#1)", rtErr("fn:text.repeat:n_out_of_range:2147483648"), vText("abc"), vInt(0x80000000))
    }

    @Test fun testReplace() {
        chkExpr("#0.replace(#1, #2)", "text[Bye World]", vText("Hello World"), vText("Hello"), vText("Bye"))
        chkExpr("#0.replace(#1, #2)", "text[Hell0 W0rld]", vText("Hello World"), vText("o"), vText("0"))
        chkExpr("#0.replace(#1, #2)", "text[Hello World]", vText("Hello World"), vText("Bye"), vText("Tschus"))
    }

    @Test fun testReversed() {
        chkExpr("#0.reversed()", "text[]", vText(""))
        chkExpr("#0.reversed()", "text[a]", vText("a"))
        chkExpr("#0.reversed()", "text[ba]", vText("ab"))
        chkExpr("#0.reversed()", "text[cba]", vText("abc"))
        chkExpr("#0.reversed()", "text[dcba]", vText("abcd"))
        chkExpr("#0.reversed()", "text[edcba]", vText("abcde"))
    }

    @Test fun testSize() {
        chkExpr("#0.size()", "int[0]", vText(""))
        chkExpr("#0.size()", "int[1]", vText(" "))
        chkExpr("#0.size()", "int[1]", vText("X"))
        chkExpr("#0.size()", "int[5]", vText("Hello"))
    }

    @Test fun testStartsWith() {
        chkExpr("#0.starts_with(#1)", "boolean[true]", vText("Hello"), vText("Hello"))
        chkExpr("#0.starts_with(#1)", "boolean[true]", vText("Hello"), vText("Hell"))
        chkExpr("#0.starts_with(#1)", "boolean[true]", vText("Hello"), vText("H"))
        chkExpr("#0.starts_with(#1)", "boolean[true]", vText("Hello"), vText(""))
        chkExpr("#0.starts_with(#1)", "boolean[false]", vText("Hello"), vText("hello"))
        chkExpr("#0.starts_with(#1)", "boolean[false]", vText("Hello"), vText("Hellou"))
    }

    @Test fun testSub() {
        chkExpr("#0.sub(#1)", "text[World]", vText("Hello World"), vInt(6))
        chkExpr("#0.sub(#1)", "text[]", vText("Hello World"), vInt(11))
        chkExpr("#0.sub(#1)", "text[Hello World]", vText("Hello World"), vInt(0))
        chkExpr("#0.sub(#1)", rtErr("fn:text.sub:range:11:12:11"), vText("Hello World"), vInt(12))
        chkExpr("#0.sub(#1, #2)", "text[Wor]", vText("Hello World"), vInt(6), vInt(9))
        chkExpr("#0.sub(#1, #2)", "text[]", vText("Hello World"), vInt(6), vInt(6))
        chkExpr("#0.sub(#1, #2)", "text[World]", vText("Hello World"), vInt(6), vInt(11))
        chkExpr("#0.sub(#1, #2)", rtErr("fn:text.sub:range:11:6:12"), vText("Hello World"), vInt(6), vInt(12))
        chkExpr("#0.sub(#1, #2)", rtErr("fn:text.sub:range:11:6:5"), vText("Hello World"), vInt(6), vInt(5))
        chkExpr("#0.sub(#1, #2)", "text[Hello World]", vText("Hello World"), vInt(0), vInt(11))
    }

    @Test fun testUpperCase() {
        chkExpr("#0.upper_case()", "text[\\u0407]", vText("ї"))
        chkExpr("#0.lower_case()", "text[\\u0457]", vText("Ї"))
    }

//    @Test fun testTrim() {
//        chkExpr("#0.trim()", "text[Hello]", vText("Hello"))
//        chkExpr("#0.trim()", "text[Hello]", vText("  Hello   "))
//        chkExpr("#0.trim()", "text[Hello]", vText("  \t\t   Hello   \t  "))
//        chkExpr("#0.trim()", "text[Hello]", vText(" \n\t\r\n Hello \n\r\t "))
//    }
}
