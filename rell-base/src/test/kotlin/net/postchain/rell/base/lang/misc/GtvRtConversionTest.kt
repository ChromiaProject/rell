/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.misc

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvString
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.CommonUtils
import org.junit.Test
import kotlin.test.assertEquals

class GtvRtConversionTest: BaseRellTest(gtv = true) {
    init {
        tst.gtvResultRaw = true
    }

    @Test fun testQueryResult() {
        chkQueryRes("= true;", "1")
        chkQueryRes("= 123;", "123")
        chkQueryRes("= 'Hello';", """"Hello"""")
        chkQueryRes("= x'12EF';", """"12EF"""")
        chkQueryRes("""= json('{"x":123,"y":"Hello"}');""", """"{\"x\":123,\"y\":\"Hello\"}"""")
        chkQueryRes("= null;", "null")
        chkQueryRes("= 123.456;", "\"123.456\"")
        chkQueryRes("= 123.0;", "\"123\"")
        chkQueryRes("= (123,[4,5,6]);", """[123,[4,5,6]]""")
        chkQueryRes("= (x=123,y=[4,5,6]);", """{"x":123,"y":[4,5,6]}""")
        chkQueryRes("= (x=123,[4,5,6]);", """[123,[4,5,6]]""")
        chkQueryRes("= (123,y=[4,5,6]);", """[123,[4,5,6]]""")
    }

    @Test fun testQueryResultTuple() {
        chkQueryRes("= (x = 123, y = 'Hello');", """{"x":123,"y":"Hello"}""")
        chkQueryRes("= (123, 'Hello');", """[123,"Hello"]""")
        chkQueryRes("= (x = 123, 'Hello');", """[123,"Hello"]""")
        chkQueryRes("= (123, y = 'Hello');", """[123,"Hello"]""")
    }

    @Test fun testQueryResultNullable() {
        chkQueryRes("{ val x: integer? = 123; return x; }", "123")
        chkQueryRes("{ val x: integer? = null; return x; }", "null")
    }

    @Test fun testQueryResultCollection() {
        chkQueryRes("= [1,2,3];", "[1,2,3]")
        chkQueryRes("= set([1,2,3]);", "[1,2,3]")
        chkQueryRes("= ['A':1,'B':2,'C':3];", """{"A":1,"B":2,"C":3}""")
        chkQueryRes("= [1:'A',2:'B',3:'C'];", """[[1,"A"],[2,"B"],[3,"C"]]""")
    }

    @Test fun testQueryResultStruct() {
        def("struct foo { x: integer; b: bar; }")
        def("struct bar { p: boolean; q: text; }")
        chkQueryRes(" = foo(123, bar(true, 'Hello'));", """{"b":{"p":1,"q":"Hello"},"x":123}""")
    }

    @Test fun testQueryResultCyclicStruct() {
        def("struct node { v: integer; left: node? = null; right: node? = null; }")
        chkQueryRes("= node(456, left = node(123), right = node(789));",
                """{"left":{"left":null,"right":null,"v":123},"right":{"left":null,"right":null,"v":789},"v":456}""")
    }

    @Test fun testArgSimple() {
        tst.gtvResult = false

        chkArg("boolean", "0", "boolean[false]")
        chkArg("boolean", "1", "boolean[true]")
        chkArg("boolean", "2", "gtv_err:type:[boolean]:bad_value:2")
        chkArg("boolean", """"Hello"""", "gtv_err:type:[boolean]:INTEGER:STRING")

        chkArg("integer", "123", "int[123]")
        chkArg("integer", "-123", "int[-123]")
        chkArg("integer", """"Hello"""", "gtv_err:type:[integer]:INTEGER:STRING")
        chkArg("integer", "null", "gtv_err:type:[integer]:INTEGER:NULL")

        chkArg("text", """"Hello"""", "text[Hello]")
        chkArg("text", "123", "gtv_err:type:[text]:STRING:INTEGER")

        chkArg("byte_array", "12EF", "byte_array[12ef]")
        chkArg("byte_array", "12ef", "byte_array[12ef]")
        chkArg("byte_array", "12eF", "byte_array[12ef]")
        chkArg("byte_array", "12EG", "gtv_err:type:[byte_array]:bad_value:STRING")
        chkArg("byte_array", "12E", "gtv_err:type:[byte_array]:bad_value:STRING")
        chkArg("byte_array", """"Hello"""", "gtv_err:type:[byte_array]:bad_value:STRING")

        chkArg("json", """"{\"x\":123,\"y\":\"Hello\"}"""", """json[{"x":123,"y":"Hello"}]""")
        chkArg("json", """{"x":123,"y":"Hello"}""", "gtv_err:type:[json]:STRING:DICT")
        chkArg("json", """"{"""", "gtv_err:type:[json]:bad_value")

        chkArg("decimal", "123", "dec[123]")
        chkArg("decimal", "'123.456'", "dec[123.456]")
    }

    @Test fun testArgByteArray() {
        chkOpArg("byte_array", gtvBytes("12EF"), "byte_array[12ef]")
        chkOpArg("byte_array", gtvBytes("12ef"), "byte_array[12ef]")
        chkOpArg("byte_array", gtvBytes("12eF"), "byte_array[12ef]")
        chkOpArg("byte_array", gtvStr("12EF"), "byte_array[12ef]")
        chkOpArg("byte_array", gtvStr("12ef"), "byte_array[12ef]")
        chkOpArg("byte_array", gtvStr("12eF"), "byte_array[12ef]")
        chkOpArg("byte_array", gtvStr("12EG"), "gtv_err:type:[byte_array]:bad_value:STRING")
        chkOpArg("byte_array", gtvStr("12E"), "gtv_err:type:[byte_array]:bad_value:STRING")
        chkOpArg("byte_array", gtvStr("Hello"), "gtv_err:type:[byte_array]:bad_value:STRING")
    }

    @Test fun testArgTupleQuery() {
        tst.gtvResult = false
        chkQueryArg("(x:integer,y:text)", """{"x":123,"y":"Hello"}""", "(x=int[123],y=text[Hello])")
        chkQueryArg("(x:integer,y:text)", """{"p":123,"q":"Hello"}""", "gtv_err:tuple_nokey:x")
        chkQueryArg("(x:integer,y:text)", """[123,"Hello"]""", "(x=int[123],y=text[Hello])")
        chkQueryArg("(x:integer,y:text)", """{"x":123}""", "gtv_err:tuple_count:2:1")
        chkQueryArg("(x:integer,y:text)", """{"y":"Hello"}""", "gtv_err:tuple_count:2:1")
        chkQueryArg("(x:integer,y:text)", """{"x":123,"y":"Hello","z":456}""", "gtv_err:tuple_count:2:3")
        chkQueryArg("(integer,text)", """[123,"Hello"]""", "(int[123],text[Hello])")
        chkQueryArg("(integer,text)", """{"x":123,"y":"Hello"}""", "gtv_err:type:[(integer,text)]:ARRAY:DICT")
        chkQueryArg("(x:integer,text)", """[123,"Hello"]""", "(x=int[123],text[Hello])")
        chkQueryArg("(integer,y:text)", """[123,"Hello"]""", "(int[123],y=text[Hello])")
    }

    @Test fun testArgTupleOp() {
        tst.gtvResult = false
        chkOpArg("(x:integer,y:text)", """[123,"Hello"]""", "(x=int[123],y=text[Hello])")
        chkOpArg("(x:integer,y:text)", """{"x":123,"y":"Hello"}""", "gtv_err:type:[(x:integer,y:text)]:ARRAY:DICT")
        chkOpArg("(x:integer,y:text)", """{"p":123,"q":"Hello"}""", "gtv_err:type:[(x:integer,y:text)]:ARRAY:DICT")
        chkOpArg("(x:integer,y:text)", """[123]""", "gtv_err:tuple_count:2:1")
        chkOpArg("(x:integer,y:text)", """["Hello"]""", "gtv_err:tuple_count:2:1")
        chkOpArg("(x:integer,y:text)", """{"x":123}""", "gtv_err:type:[(x:integer,y:text)]:ARRAY:DICT")
        chkOpArg("(x:integer,y:text)", """{"y":"Hello"}""", "gtv_err:type:[(x:integer,y:text)]:ARRAY:DICT")
        chkOpArg("(x:integer,y:text)", """[123,"Hello",456]""", "gtv_err:tuple_count:2:3")
        chkOpArg("(x:integer,y:text)", """{"x":123,"y":"Hello","z":456}""", "gtv_err:type:[(x:integer,y:text)]:ARRAY:DICT")
        chkOpArg("(integer,text)", """[123,"Hello"]""", "(int[123],text[Hello])")
        chkOpArg("(integer,text)", """{"x":123,"y":"Hello"}""", "gtv_err:type:[(integer,text)]:ARRAY:DICT")
        chkOpArg("(x:integer,text)", """[123,"Hello"]""", "(x=int[123],text[Hello])")
        chkOpArg("(integer,y:text)", """[123,"Hello"]""", "(int[123],y=text[Hello])")
    }

    @Test fun testArgNullable() {
        tst.gtvResult = false
        chkArg("integer?", "123", "int[123]")
        chkArg("integer?", "null", "null")
        chkArg("integer?", """"Hello"""", "gtv_err:type:[integer]:INTEGER:STRING")
    }

    @Test fun testArgCollection() {
        tst.gtvResult = false

        chkArg("list<integer>", "[1,2,3]", "list<integer>[int[1],int[2],int[3]]")
        chkArg("list<integer>", "[]", "list<integer>[]")
        chkArg("list<integer>", """["Hello"]""", "gtv_err:type:[integer]:INTEGER:STRING")
        chkArg("list<integer>", "{}", "gtv_err:type:[list<integer>]:ARRAY:DICT")

        chkArg("set<integer>", "[1,2,3]", "set<integer>[int[1],int[2],int[3]]")
        chkArg("set<integer>", "[]", "set<integer>[]")
        chkArg("set<integer>", "[1,2,1]", "gtv_err:set_dup:int[1]")
        chkArg("set<integer>", """["Hello"]""", "gtv_err:type:[integer]:INTEGER:STRING")
        chkArg("set<integer>", "{}", "gtv_err:type:[set<integer>]:ARRAY:DICT")

        chkArg("map<text,integer>", """{"A":1,"B":2,"C":3}""",
                "map<text,integer>[text[A]=int[1],text[B]=int[2],text[C]=int[3]]")
        chkArg("map<text,integer>", """{"A":1,"B":2,"A":1}""", "map<text,integer>[text[A]=int[1],text[B]=int[2]]")
        chkArg("map<text,integer>", """{"A":1,"B":2,"A":3}""", "map<text,integer>[text[A]=int[3],text[B]=int[2]]")
        chkArg("map<text,integer>", """{"A":"B"}""", "gtv_err:type:[integer]:INTEGER:STRING")
        chkArg("map<text,integer>", """{1:2}""", "map<text,integer>[text[1]=int[2]]")
        chkArg("map<text,integer>", """{1:"A"}""", "gtv_err:type:[integer]:INTEGER:STRING")
        chkArg("map<integer,text>", """[[1,"A"]]""", "map<integer,text>[int[1]=text[A]]")
    }

    @Test fun testArgGtv() {
        tst.gtvResult = false
        chkArg("gtv", """{"x":123,"y":[4,5,6]}""", """gtv[["x": 123, "y": [4, 5, 6]]]""")
        chkArg("gtv", """{ "x" : 123, "y" : [4,5,6] }""", """gtv[["x": 123, "y": [4, 5, 6]]]""")
    }

    @Test fun testArgStructQuery() {
        def("struct foo { x: integer; b: bar; }")
        def("struct bar { p: boolean; q: text; }")
        def("struct qaz { b: bar?; }")
        tst.gtvResult = false

        chkQueryArg("foo", """{"x":123,"b":{"p":1,"q":"Hello"}}""", "foo[x=int[123],b=bar[p=boolean[true],q=text[Hello]]]")
        chkQueryArg("foo", """{"x":123,"b":{"p":2,"q":"Hello"}}""", "gtv_err:type:[boolean]:bad_value:2:attr:[bar]:p")
        chkQueryArg("foo", """{"x":123,"b":{"p":1,"q":456}}""", "gtv_err:type:[text]:STRING:INTEGER:attr:[bar]:q")
        chkQueryArg("foo", """{"b":{"p":1,"q":"Hello"}}""", "gtv_err:struct_nokey:foo:x")
        chkQueryArg("foo", """{"x":123,"b":null}""", "gtv_err:type:[bar]:ARRAY:NULL:attr:[foo]:b")
        chkQueryArg("foo", """{"x":123}""", "gtv_err:struct_nokey:foo:b")
        chkQueryArg("foo", """{"x":123,"b":{"p":1,"q":"Hello","r":456}}""", "gtv_err:struct_badkey:bar:r:attr:[foo]:b")

        chkQueryArg("qaz", """{"b":{"p":2,"q":"Hello","r":456}}""", "gtv_err:type:[boolean]:bad_value:2:attr:[bar]:p")
        chkQueryArg("qaz", """{"b":null}""", "qaz[b=null]")
        chkQueryArg("qaz", """{}""", "gtv_err:struct_nokey:qaz:b")
    }

    @Test fun testArgStructQueryCyclic() {
        def("struct node { v: integer; left: node? = null; right: node? = null; }")
        tst.gtvResult = false

        chkQueryArg("node", """{"v":456,"left":{"v":123,"left":null,"right":null},"right":{"v":789,"left":null,"right":null}}""",
                "node[v=int[456],left=node[v=int[123],left=null,right=null],right=node[v=int[789],left=null,right=null]]")
    }

    @Test fun testArgStructOp() {
        def("struct foo { x: integer; b: bar; }")
        def("struct bar { p: boolean; q: text; }")
        def("struct qaz { b: bar?; }")

        chkOpArg("foo", """[123,[1,"Hello"]]""", "foo[x=int[123],b=bar[p=boolean[true],q=text[Hello]]]")
        chkOpArg("foo", """{"x":123,"b":{"p":1,"q":"Hello"}}""", "gtv_err:type:[foo]:ARRAY:DICT")
        chkOpArg("foo", """[123,[2,"Hello"]]""", "gtv_err:type:[boolean]:bad_value:2:attr:[bar]:p")
        chkOpArg("foo", """[123,[1,456]]""", "gtv_err:type:[text]:STRING:INTEGER:attr:[bar]:q")
        chkOpArg("foo", """[[1,"Hello"]]""", "gtv_err:struct_size:foo:2:1")
        chkOpArg("foo", """[123,null]""", "gtv_err:type:[bar]:ARRAY:NULL:attr:[foo]:b")
        chkOpArg("foo", """[123]""", "gtv_err:struct_size:foo:2:1")
        chkOpArg("foo", """[123,[1,"Hello",456]]""", "gtv_err:struct_size:bar:2:3:attr:[foo]:b")

        chkOpArg("qaz", """[[2,"Hello",456]]""", "gtv_err:struct_size:bar:2:3:attr:[qaz]:b")
        chkOpArg("qaz", """[null]""", "qaz[b=null]")
        chkOpArg("qaz", """[]""", "gtv_err:struct_size:qaz:1:0")
    }

    @Test fun testArgStructOpCyclic() {
        def("struct node { v: integer; left: node? = null; right: node? = null; }")

        chkOpArg("node", """[456,[123,null,null],[789,null,null]]""",
                "node[v=int[456],left=node[v=int[123],left=null,right=null],right=node[v=int[789],left=null,right=null]]")
    }

    @Test fun testEnum() {
        def("enum foo { A, B, C }")

        chkQueryRes("= foo.A;", "\"A\"")
        chkQueryArg("foo", "\"A\"", "\"A\"")
        chkQueryArg("foo", "\"B\"", "\"B\"")
        chkQueryArg("foo", "\"C\"", "\"C\"")
        chkQueryArg("foo", "\"D\"", "gtv_err:type:[foo]:enum:bad_value:D")
        chkQueryArg("foo", "0", "\"A\"")
        chkQueryArg("foo", "\"0\"", "gtv_err:type:[foo]:enum:bad_value:0")

        chkOpArg("foo", "0", "foo[A]")
        chkOpArg("foo", "1", "foo[B]")
        chkOpArg("foo", "2", "foo[C]")
        chkOpArg("foo", "3", "gtv_err:type:[foo]:enum:bad_value:3")
        chkOpArg("foo", "\"0\"", "gtv_err:type:[foo]:INTEGER:STRING")
        chkOpArg("foo", "\"A\"", "gtv_err:type:[foo]:INTEGER:STRING")
    }

    private fun chkArg(type: String, arg: String, expected: String) {
        chkQueryArg(type, arg, expected)
        chkOpArg(type, arg, expected)
    }

    private fun chkQueryRes(code: String, expected: String) {
        tst.chkQueryGtv(code, expected)
    }

    private fun chkQueryArg(type: String, arg: String, expected: String) {
        val code = "query q(a: $type) = a;"
        tst.chkQueryGtvEx(code, listOf(arg), expected)
    }

    private fun chkOpArg(type: String, arg: String, expected: String) {
        val code = "operation o(a: $type) { print(_strict_str(a)); }"
        val actual = tst.callOpGtvStr(code, listOf(arg))
        chkRes(expected, actual)
    }

    private fun chkOpArg(type: String, arg: Gtv, expected: String) {
        val code = "operation o(a: $type) { print(_strict_str(a)); }"
        val actual = tst.callOpGtv(code, listOf(arg))
        chkRes(expected, actual)
    }

    private fun chkRes(expected: String, actual: String) {
        if (actual == "OK") {
            chkOut(expected)
        } else {
            assertEquals(expected, actual)
        }
    }

    private fun gtvBytes(s: String): Gtv {
        val bytes = CommonUtils.hexToBytes(s)
        return GtvByteArray(bytes)
    }

    private fun gtvStr(s: String): Gtv {
        return GtvString(s)
    }
}
