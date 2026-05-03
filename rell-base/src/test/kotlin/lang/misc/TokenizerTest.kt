/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.immListOf
import kotlin.test.Test

class TokenizerTest: BaseRellTest() {
    @Test fun testName() {
        tst.errMsgPos = true
        chk("\n$", "ct_err:main.rell(2:1):expr:placeholder:none")
        chk("\nbob$", "ct_err:main.rell(2:4):syntax")
        chk("\nSehenswürdigkeit", "ct_err:main.rell(2:8):syntax")
        chk("\nтест", "ct_err:main.rell(2:1):syntax")
    }

    @Test fun testIntegerLiteral() {
        tst.errMsgPos = true

        chk("0", "int[0]")
        chk("123456789", "int[123456789]")
        chk("9223372036854775807", "int[9223372036854775807]")
        chk("01234567", "int[1234567]")
        chk("\n9223372036854775808", "ct_err:main.rell(2:1):lex:int:range:9223372036854775808")
        // ANTLR tokenizes `1a` as two tokens (NUMBER `1`, ID `a`); the parser surfaces the trailing
        // identifier as a syntax error rather than a single bad numeric token.
        chk("\n1a", "ct_err:main.rell(2:2):syntax")
        chk("\n1f", "ct_err:main.rell(2:2):syntax")
        chk("\n1z", "ct_err:main.rell(2:2):syntax")

        chk("0x0", "int[0]")
        chk("0xA", "int[10]")
        chk("0xa", "int[10]")
        chk("0xF", "int[15]")
        chk("0xf", "int[15]")
        chk("0xABCD", "int[43981]")
        chk("0xabcd", "int[43981]")
        chk("0x1234ABCD", "int[305441741]")
        chk("0x1234abcd", "int[305441741]")
        chk("0x1234AbCd", "int[305441741]")
        chk("0x7FFFFFFFFFFFFFFF", "int[9223372036854775807]")
        chk("0x8000000000000000", "ct_err:main.rell(1:13):lex:int:range:0x8000000000000000")
        chk("0xFED", "int[4077]")
        chk("0xFed", "int[4077]")
        chk("0xfed", "int[4077]")
        chk("\n0x", "ct_err:main.rell(2:2):syntax")
        chk("\n0X0", "ct_err:main.rell(2:2):syntax")
        chk("\n0xg", "ct_err:main.rell(2:2):syntax")
        chk("\n0x0g", "ct_err:main.rell(2:4):syntax")
        chk("\n0xz", "ct_err:main.rell(2:2):syntax")
    }

    @Test fun testDecimalLiteral() {
        tst.errMsgPos = true

        chk("0.0", "dec[0]")
        chk("12345.6789", "dec[12345.6789]")
        chk("922337203685477580.7", "dec[922337203685477580.7]")
        chk("01234567.8", "dec[1234567.8]")
        chk("9223372036854775808.0", "dec[9223372036854775808]")

        chk("12.34E0", "dec[12.34]")
        chk("12.34E+0", "dec[12.34]")
        chk("12.34E-0", "dec[12.34]")
        chk("12.34E5", "dec[1234000]")
        chk("12.34E+5", "dec[1234000]")
        chk("12.34E-5", "dec[0.0001234]")

        chk(".12345", "dec[0.12345]")
        chk(".0", "dec[0]")
        chk(".1E3", "dec[100]")

        // ANTLR's RULE_DECIMAL is a single, more permissive shape; malformed numerics are split
        // into multiple tokens by the lexer and reported as parse-level syntax errors.
        chk("\n123.", "ct_err:main.rell(2:4):syntax")
        chk("\n123. 4", "ct_err:main.rell(2:4):syntax")
        chk("\n0.", "ct_err:main.rell(2:2):syntax")
        chk("\n123y.456", "ct_err:main.rell(2:4):syntax")
        chk("\n0.123z", "ct_err:main.rell(2:6):syntax")
        chk("\n0.a", "ct_err:main.rell(2:1):syntax")
        chk("\n1E", "ct_err:main.rell(2:2):syntax")
        chk("\n1e", "ct_err:main.rell(2:2):syntax")
        chk("\n1E+", "ct_err:main.rell(2:2):syntax")
        chk("\n1E-", "ct_err:main.rell(2:2):syntax")
        chk("\n1E 5", "ct_err:main.rell(2:2):syntax")
        chk("\n1E+ 5", "ct_err:main.rell(2:2):syntax")
        chk("\n1E- 6", "ct_err:main.rell(2:2):syntax")
        chk("\n1E +5", "ct_err:main.rell(2:2):syntax")
        chk("\n1E -6", "ct_err:main.rell(2:2):syntax")
        chk("\n1E+A", "ct_err:main.rell(2:2):syntax")
        chk("\n1E-B", "ct_err:main.rell(2:2):syntax")
        chk("\n1E+10F", "ct_err:main.rell(2:6):syntax")
        chk("\n1E-10g", "ct_err:main.rell(2:6):syntax")

        chk("\n0x123.0", "ct_err:main.rell(2:6):syntax")
        chk("\n0x123.456", "ct_err:main.rell(2:6):syntax")
        chk("\n0x.123", "ct_err:main.rell(2:2):syntax")
    }

    @Test fun testStringLiteral() {
        tst.errMsgPos = true
        val nl = "\n"

        chk("""  ''  """, "text[]")
        chk("""  ""  """, "text[]")
        chk("""  "Hello"  """, "text[Hello]")
        chk("""  'Hello'  """, "text[Hello]")
        chk("""  "'"  """, "text[']")
        chk("""  '"'  """, "text[\"]")
        chk("""  " "  """, "text[ ]")
        chk("""  ' '  """, "text[ ]")

        chk("""  "\b"  """, """text[\b]""")
        chk("""  '\b'  """, """text[\b]""")
        chk("""  "\t"  """, """text[\t]""")
        chk("""  '\t'  """, """text[\t]""")
        chk("""  "\r"  """, """text[\r]""")
        chk("""  '\r'  """, """text[\r]""")
        chk("""  "\n"  """, """text[\n]""")
        chk("""  '\n'  """, """text[\n]""")
        chk("""  "\""  """, """text["]""")
        chk("""  '\"'  """, """text["]""")
        chk("""  "\'"  """, """text[']""")
        chk("""  '\''  """, """text[']""")
        chk("""  "\\"  """, """text[\\]""")
        chk("""  '\\'  """, """text[\\]""")
        // ANTLR's RULE_STRING accepts only the documented escapes; malformed escapes break the
        // RULE_STRING token, so the parser sees a stray quote and reports a generic syntax error.
        chk("""$nl "\q"  """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl '\q'  """, "ct_err:main.rell(2:2):syntax")

        chk(""" "\u0031\u0032\u0033" """, "text[123]")
        chk(""" '\u0031\u0032\u0033' """, "text[123]")
        chk(""" "\u003A\u003B\u003C" """, "text[:;<]")
        chk(""" '\u003A\u003B\u003C' """, "text[:;<]")
        chk(""" "\u003a\u003b\u003c" """, "text[:;<]")
        chk(""" '\u003a\u003b\u003c' """, "text[:;<]")
        chk("""$nl "\u003g" """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl '\u003g' """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl "\u003" """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl '\u003' """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl "\u003!" """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl '\u003!' """, "ct_err:main.rell(2:2):syntax")

        chk("""$nl "Hello${nl}World"  """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl 'Hello${nl}World'  """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl "Hello\${nl}World"  """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl 'Hello\${nl}World'  """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl "Hello  """, "ct_err:main.rell(2:2):syntax")
        chk("""$nl 'Hello  """, "ct_err:main.rell(2:2):syntax")
    }

    @Test fun testByteArrayLiteral() {
        tst.errMsgPos = true
        val nl = "\n"

        chk("""  x""  """, "byte_array[]")
        chk("""  x''  """, "byte_array[]")
        chk("""  x"1234ABCD"  """, "byte_array[1234abcd]")
        chk("""  x'1234ABCD'  """, "byte_array[1234abcd]")
        chk("""  x"1234abcd"  """, "byte_array[1234abcd]")
        chk("""  x'1234abcd'  """, "byte_array[1234abcd]")

        // ANTLR's RULE_BYTES requires hex-digit pairs; malformed byte literals fall back to
        // RULE_ID + RULE_STRING tokenization and surface as parse-level syntax errors.
        chk("""$nl x"1"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x'1'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x"123"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x'123'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x"ge"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x'ge'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x"${'\n'}"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x'${'\n'}'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x"\u0030\u0031"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x'\u0030\u0031'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x" 1234"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x' 1234'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x"1234 "  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x'1234 '  """, "ct_err:main.rell(2:3):syntax")

        chk("""$nl X"1234"  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl X'1234'  """, "ct_err:main.rell(2:3):syntax")
        chk("""$nl x "1234"  """, "ct_err:main.rell(2:4):syntax")
        chk("""$nl x '1234'  """, "ct_err:main.rell(2:4):syntax")
    }

    @Test fun testComments() {
        tst.errMsgPos = true

        chk("123//456\n", "int[123]")
        chk("//456\n123", "int[123]")
        chk("//456\n\n123", "int[123]")
        chk("123//789\n+456", "int[579]")
        chk("123+//789\n456", "int[579]")
        chk("// /*\n 123 // */ 456\n", "int[123]")
        chk("'Hello//' + '//World'", "text[Hello////World]")

        chk("/*123*/456", "int[456]")
        chk("/*/123*/456", "int[456]")
        chk("123/*456*/", "int[123]")
        chk("/*456\n789*/123", "int[123]")
        chk("123/*777\n888*/+456", "int[579]")
        chk("/*123//*/456", "int[456]")
        chk("'Hello/*'+'*/World'", "text[Hello/**/World]")

        chkEx("{\n 123 /* 456", "ct_err:main.rell(2:6):syntax")
    }

    @Test fun testErrPos() {
        tst.errMsgPos = true
        chkEx("{ val x = 5;\nval x = 10; return 0; }", "ct_err:main.rell(2:5):block:name_conflict:x")
        chkEx("{ val x = 5;\nreturn; }", "ct_err:main.rell(2:1):stmt_return_query_novalue")
    }

    @Test fun testCompilationErrorPos() {
        tst.errMsgPos = true
        // ANTLR counts a tab as a single column, unlike better-parse which expanded tabs to 4 columns.
        chkEx("{\n    val x = 5;\n    val x = 6;\n    return 0;\n}\n", "ct_err:main.rell(3:9):block:name_conflict:x")
        chkEx("{\n\tval x = 5;\n\tval x = 6;\n\treturn 0;\n}\n", "ct_err:main.rell(3:6):block:name_conflict:x")
        chkEx("{\n\tval x = 5;\n \tval x = 6;\n\treturn 0;\n}\n", "ct_err:main.rell(3:7):block:name_conflict:x")
        chkEx("{\n\tval x = 5;\n  \tval x = 6;\n\treturn 0;\n}\n", "ct_err:main.rell(3:8):block:name_conflict:x")
        chkEx("{\n\tval x = 5;\n   \tval x = 6;\n\treturn 0;\n}\n", "ct_err:main.rell(3:9):block:name_conflict:x")
        chkEx("{\n\tval x = 5;\n    \tval x = 6;\n\treturn 0;\n}\n", "ct_err:main.rell(3:10):block:name_conflict:x")
        chkEx("{\n\tval x = 5;\n\t val x = 6;\n\treturn 0;\n}\n", "ct_err:main.rell(3:7):block:name_conflict:x")
    }

    @Test fun testSyntaxErrorBeforeLexicalError() {
        // ANTLR lexer rejects `'\x'` as a syntax error rather than emitting a specific
        // lex:string_esc code; the better-parse tokenizer used to distinguish the two.
        chkFull("'\\x'", "ct_err:syntax")
        chkFull("val x = 123; '\\x'", "ct_err:syntax")
        chkFull("val x = ; '\\x'", "ct_err:syntax")
        chkFull("val x = '\\x'; val y = ;", "ct_err:syntax")
    }


    @Test fun testTypesValidIdentifiers() {
        val types = immListOf("big_integer", "boolean", "byte_array", "decimal", "gtv", "integer", "json", "list",
            "map", "set", "text")
        for (type in types) {
            chk("($type = true)", "($type=boolean[true])")
        }
    }

    @Test fun testKeywordsNotValidIdentifiers() {
        val keywords = immListOf("abstract", "and", "break", "class", "continue", "create", "delete", "else", "entity",
            "enum", "false", "for", "function", "if", "import", "in", "index", "key", "limit", "module", "mutable",
            "namespace", "not", "null", "object", "offset", "operation", "or", "override", "query", "record", "return",
            "struct", "true", "update", "val", "var", "virtual", "when", "while")
        for (keyword in keywords) {
            chk("($keyword = true)", "ct_err:syntax")
        }
    }

    @Test fun testDeprecatedKeywordsValidIdentifiers() {
        chk("(sort = true)", "(sort=boolean[true])")
    }
}
