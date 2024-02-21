package net.postchain.rell.gtx

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType
import net.postchain.gtv.parse.GtvParser
import net.postchain.rell.base.lang.type.VirtualTest.Companion.argToGtv
import net.postchain.rell.gtx.testutils.BaseGtxTest
import org.junit.Ignore
import org.junit.Test

class GtxStrictModeTest: BaseGtxTest() {

    private var isVirtualTypeTest = false

    @Test fun testByteArray() {
        chkStrictMode("byte_array", "'ABCD'", "OK", "gtv_err:type:[byte_array]:bad_value:STRING:param:x")
        chkStrictMode("byte_array", "x'ABCD'", "OK", "OK")
    }

    @Test fun testBigInteger() {
        chkStrictMode("big_integer", "123",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")
        chkStrictMode("big_integer", "123L", "OK", "OK")
    }

    @Test fun testDecimal() {
        chkStrictMode("decimal", "'25.7'", "OK", "OK")
        chkStrictMode("decimal", "25", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
        chkStrictMode("decimal", "25L", "OK", "gtv_err:type:[decimal]:STRING:BIGINTEGER:param:x")
    }

    @Test fun testInteger() {
        chkStrictMode("integer", "25", "OK", "OK")
        chkStrictMode("integer", "25L", "OK", "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")
    }

    @Test fun testRowId() {
        chkStrictMode("rowid", "25", "OK", "OK")
        chkStrictMode("rowid", "25L", "OK", "gtv_err:type:[rowid]:INTEGER:BIGINTEGER:param:x")
    }

    @Test fun testBoolean() {
        chkStrictMode("boolean", "1", "OK", "OK")
        chkStrictMode("boolean", "1L",
            "gtv_err:type:[boolean]:INTEGER:BIGINTEGER:param:x",
            "gtv_err:type:[boolean]:INTEGER:BIGINTEGER:param:x")
        chkStrictMode("boolean", "10",
            "gtv_err:type:[boolean]:bad_value:10:param:x",
            "gtv_err:type:[boolean]:bad_value:10:param:x")
    }

    // TODO: This test should be enabled when postchain-gtv version 3.14.22 is released
    @Ignore @Test fun testJson() {
        chkStrictMode("json", "'{\"a\": 2}'", "OK", "OK")
    }

    @Test fun testList() {
        chkStrictMode("list<big_integer>", "[1L, 2L]", "OK", "OK")
        chkStrictMode("list<big_integer>", "[1, 2]",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")

        chkStrictMode("list<byte_array>", "[x'ABCD', x'DEFF']", "OK", "OK")
        chkStrictMode("list<byte_array>", "['ABCD', 'DEFF']",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")

        chkStrictMode("list<decimal>", "['25.7']", "OK", "OK")
        chkStrictMode("list<decimal>", "[25]", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
        chkStrictMode("list<decimal>", "[25L]", "OK", "gtv_err:type:[decimal]:STRING:BIGINTEGER:param:x")

        chkStrictMode("list<integer>", "[25]", "OK", "OK")
        chkStrictMode("list<integer>", "[25L]", "OK", "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("list<rowid>", "[25]", "OK", "OK")
        chkStrictMode("list<rowid>", "[25L]", "OK", "gtv_err:type:[rowid]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("list<boolean>", "[1]", "OK", "OK")
        chkStrictMode("list<boolean>", "[1L]",
            "gtv_err:type:[boolean]:INTEGER:BIGINTEGER:param:x",
            "gtv_err:type:[boolean]:INTEGER:BIGINTEGER:param:x")
        chkStrictMode("list<boolean>", "[10]",
            "gtv_err:type:[boolean]:bad_value:10:param:x",
            "gtv_err:type:[boolean]:bad_value:10:param:x")
    }

    @Test fun testStruct() {
        def("""
            struct person {
                photo: byte_array;
                age: integer;
                zip_code: big_integer;
                height: decimal;
            }
        """)
        chkStrictMode("person", "[x'ABCD', 2, 3L, '1.1']", "OK", "OK")
        chkStrictMode("person", "['ABCD', 2, 3L, '1.1']",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")
        chkStrictMode("person", "[x'ABCD', 2L, 3L, '1.1']",
            "OK",
            "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")
        chkStrictMode("person", "[x'ABCD', 2, 3, '1.1']",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")
        chkStrictMode("person", "[x'ABCD', 2, 3L, 1]", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
    }

    @Test fun testSet() {
        chkStrictMode("set<big_integer>", "[1L, 2L]", "OK", "OK")
        chkStrictMode("set<big_integer>", "[1, 2]",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")

        chkStrictMode("set<byte_array>", "[x'ABCD', x'DEFF']", "OK", "OK")
        chkStrictMode("set<byte_array>", "['ABCD', 'DEFF']",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")

        chkStrictMode("set<decimal>", "['25.7']", "OK", "OK")
        chkStrictMode("set<decimal>", "[25]", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
        chkStrictMode("set<decimal>", "[25L]", "OK", "gtv_err:type:[decimal]:STRING:BIGINTEGER:param:x")

        chkStrictMode("set<integer>", "[25]", "OK", "OK")
        chkStrictMode("set<integer>", "[25L]", "OK", "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("set<rowid>", "[25]", "OK", "OK")
        chkStrictMode("set<rowid>", "[25L]", "OK", "gtv_err:type:[rowid]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("set<boolean>", "[1]", "OK", "OK")
        chkStrictMode("set<boolean>", "[1L]",
            "gtv_err:type:[boolean]:INTEGER:BIGINTEGER:param:x",
            "gtv_err:type:[boolean]:INTEGER:BIGINTEGER:param:x")
        chkStrictMode("set<boolean>", "[10]",
            "gtv_err:type:[boolean]:bad_value:10:param:x",
            "gtv_err:type:[boolean]:bad_value:10:param:x")
    }

    @Test fun testTuple() {
        chkStrictMode("(byte_array, integer, big_integer, decimal)", "[x'ABCD', 2, 3L, '1.1']", "OK", "OK")
        chkStrictMode("(byte_array, integer, big_integer, decimal)", "['ABCD', 2, 3L, '1.1']",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")
        chkStrictMode("(byte_array, integer, big_integer, decimal)", "[x'ABCD', 2L, 3L, '1.1']",
            "OK",
            "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")
        chkStrictMode("(byte_array, integer, big_integer, decimal)", "[x'ABCD', 2, 3, '1.1']",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")
        chkStrictMode("(byte_array, integer, big_integer, decimal)", "[x'ABCD', 2, 3L, 1]",
            "OK",
            "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
    }

    @Test fun testMapTextKeys() {
        chkStrictMode("map<text, byte_array>", "[\"x\" : x'ABCD']", "OK", "OK")
        chkStrictMode("map<text, byte_array>", "[\"x\" : 'ABCD']",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")

        chkStrictMode("map<text, integer>", "[\"x\" : 2]", "OK", "OK")
        chkStrictMode("map<text, integer>", "[\"x\" : 2L]",
            "OK",
            "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("map<text, big_integer>", "[\"x\" : 2L]", "OK", "OK")
        chkStrictMode("map<text, big_integer>", "[\"x\" : 2]",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")

        chkStrictMode("map<text, decimal>", "[\"x\" : '1.1']", "OK", "OK")
        chkStrictMode("map<text, decimal>", "[\"x\" : 1]", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
    }

    @Test fun testMap() {
        chkStrictMode("map<integer, byte_array>", "[[1, x'ABCD']]", "OK", "OK")
        chkStrictMode("map<integer, byte_array>", "[[1, 'ABCD']]",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")

        chkStrictMode("map<integer, integer>", "[[1, 2]]", "OK", "OK")
        chkStrictMode("map<integer, integer>", "[[1, 2L]]",
            "OK",
            "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("map<integer, big_integer>", "[[1, 2L]]", "OK", "OK")
        chkStrictMode("map<integer, big_integer>", "[[1, 2]]",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")

        chkStrictMode("map<integer, decimal>", "[[1, '1.1']]", "OK", "OK")
        chkStrictMode("map<integer, decimal>", "[[1, 1]]", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
    }

    @Test fun testMapNonTextKeys() {
        chkStrictMode("map<byte_array, integer>", "[[x'ABCD', 1]]", "OK", "OK")
        chkStrictMode("map<byte_array, integer>", "[['ABCD', 1]]",
            "OK",
            "gtv_err:type:[byte_array]:bad_value:STRING:param:x")

        chkStrictMode("map<integer, integer>", "[[2, 1]]", "OK", "OK")
        chkStrictMode("map<integer, integer>", "[[2L, 1]]",
            "OK",
            "gtv_err:type:[integer]:INTEGER:BIGINTEGER:param:x")

        chkStrictMode("map<big_integer, integer>", "[[2L, 1]]", "OK", "OK")
        chkStrictMode("map<big_integer, integer>", "[[2, 1]]",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x",
            "gtv_err:type:[big_integer]:BIGINTEGER:INTEGER:param:x")

        chkStrictMode("map<decimal, integer>", "[['1.1', 1]]", "OK", "OK")
        chkStrictMode("map<decimal, integer>", "[[1, 1]]", "OK", "gtv_err:type:[decimal]:STRING:INTEGER:param:x")
    }

    @Test fun testVirtualTypes() {
        isVirtualTypeTest = true
        testStruct()
        testList()
        testSet()
        testMapTextKeys()
        testTuple()
    }

    private fun chkStrictMode(
        type: String,
        arg: String,
        expectedNonStrict: String = "OK",
        expectedStrict: String = "OK"
    ) {
        tst.gtv = true
        tst.wrapRtErrors = false
        val (gtvArg, argType) = gtvArgWithType(arg, type)

        tst.strictGtvConversion = false
        tst.chkOpEx("operation o(x: $argType) {}", listOf(gtvArg), expectedNonStrict)

        tst.strictGtvConversion = true
        tst.chkOpEx("operation o(x: $argType) {}", listOf(gtvArg), expectedStrict)
    }

    private fun gtvArgWithType(arg: String, type: String): Pair<Gtv, String> {
        var gtvArg = GtvParser.parse(tst.normalizeQuotes(arg))
        var argType = type
        if (isVirtualTypeTest) {
            argType = "virtual<$type>"
            gtvArg = argToGtv(gtvArg, argToPath(gtvArg))
        }
        return Pair(gtvArg, argType)
    }

    private fun argToPath(arg: Gtv): String {
        return when (arg.type) {
            GtvType.ARRAY -> (0..arg.asArray().size).joinToString(",", "[", "]") { "[$it]" }
            GtvType.DICT -> arg.asDict().keys.joinToString(",", "[", "]") { "['$it']" }
            else -> throw IllegalArgumentException("Invalid argument type: ${arg.type}")
        }
    }
}
