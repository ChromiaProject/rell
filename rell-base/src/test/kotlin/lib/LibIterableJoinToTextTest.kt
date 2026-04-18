/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.gtv.Gtv
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.VirtualTestUtils
import kotlin.test.Test

class LibIterableJoinToTextTest: BaseRellTest() {
    @Test fun testAllCollectionElementTypes() {
        chkElementType("integer", "[1, 2, 3, 4]", "text[<foo1*foo2*foo3*&&>]")
        chkElementType("rowid", "[rowid(1), rowid(2), rowid(3), rowid(4)]", "text[<foo1*foo2*foo3*&&>]")
        chkElementType(
            "byte_array",
            "[byte_array('11'), byte_array('22'), byte_array('33'), byte_array('44')]",
            "text[<foo0x11*foo0x22*foo0x33*&&>]"
        )
        chkElementType("big_integer", "[1L, 20L, 333L, 4L]", "text[<foo1*foo20*foo333*&&>]")
        chkElementType("decimal", "[1e-5, 2.222e2, 3.2, 4.0]", "text[<foo0.00001*foo222.2*foo3.2*&&>]")
        chkElementType("boolean", "[true, false]", "text[<footrue*foofalse>]")
        chkElementType("text", "['1', '2', '3', '4']", "text[<foo1*foo2*foo3*&&>]")
        chkElementType("text?", "[null, 'foo', 'bar']", "text[<foonull*foofoo*foobar>]")
        chkElementType(
            "json",
            """[json('{ "a": "1" }'), json('{ "b": "2" }'), json('{ "c": "3" }'), json('{ "d": "4" }')]""",
            "text[<foo{\"a\":\"1\"}*foo{\"b\":\"2\"}*foo{\"c\":\"3\"}*&&>]"
        )
    }

    private fun chkElementType(type: String, input: String, output: String) {
        chkElementType("list", type, input, output)
        chkElementType("set", type, input, output)
    }

    private fun chkElementType(collectionType: String, type: String, input: String, output: String) {
        val code = """
            function transform(element: $type) { return 'foo' + element; }
            query q() = $collectionType<${type}>(${input}).join_to_text('*', '<', '>', 3, '&&', transform(*));
        """
        chkFull(code, output)
    }

    @Test fun testMapElementTypes() {
        chkMapElementType(
            "text, integer",
            "['a':1, 'b':2, 'c':3, 'd':4]",
            "text[<foo(a,1)*foo(b,2)*foo(c,3)*&&>]"
        )
        chkMapElementType(
            "text, rowid",
            "['a':rowid(1), 'b':rowid(2), 'c':rowid(3), 'd':rowid(4)]",
            "text[<foo(a,1)*foo(b,2)*foo(c,3)*&&>]"
        )
        chkMapElementType(
            "integer, byte_array",
            "[1:byte_array('11'), 2:byte_array('22'), 3:byte_array('33'), 4:byte_array('44')]",
            "text[<foo(1,0x11)*foo(2,0x22)*foo(3,0x33)*&&>]"
        )
        chkMapElementType(
            "decimal, big_integer",
            "[2.5:1L, 2.3:20L, 2.2:333L, 2.1:4L]",
            "text[<foo(2.5,1)*foo(2.3,20)*foo(2.2,333)*&&>]"
        )
        chkMapElementType(
            "text, decimal",
            "['a':1e-5, 'b':2.222e2, 'c':3.2, 'd':4.0]",
            "text[<foo(a,0.00001)*foo(b,222.2)*foo(c,3.2)*&&>]"
        )
        chkMapElementType("integer, boolean", "[5:true, 10:false]", "text[<foo(5,true)*foo(10,false)>]")
        chkMapElementType(
            "integer, text",
            "[10:'1', 20:'2', 30:'3', 40:'4']",
            "text[<foo(10,1)*foo(20,2)*foo(30,3)*&&>]"
        )
        chkMapElementType(
            "text, text?",
            "['a':null, 'b':'foo', 'c':'bar']",
            "text[<foo(a,null)*foo(b,foo)*foo(c,bar)>]"
        )
        chkMapElementType(
            "text?, text?",
            "[null:null, 'b':'foo', 'c':'bar']",
            "text[<foo(null,null)*foo(b,foo)*foo(c,bar)>]"
        )
        chkMapElementType(
            "integer, json",
            """[1:json('{ "a": "1" }'), 2:json('{ "b": "2" }'), 3:json('{ "c": "3" }'), 4:json('{ "d": "4" }')]""",
            """text[<foo(1,{"a":"1"})*foo(2,{"b":"2"})*foo(3,{"c":"3"})*&&>]"""
        )
        chkMapElementType(
            "person, integer",
            "[person(first_name='a', last_name='b'):20, person(first_name='c', last_name='d'):40]",
            "text[<foo(person{first_name=a,last_name=b},20)*foo(person{first_name=c,last_name=d},40)>]"
        )

    }

    private fun chkMapElementType(type: String, input: String, output: String) {
        val code = """
            struct person { first_name: text; last_name: text; }
            function transform(element: ($type)) { return 'foo' + element; }
            query q() = map<${type}>(${input}).join_to_text('*', '<', '>', 3, '&&', transform(*));
        """
        chkFull(code, output)
    }

     @Test fun testLimit() {
         chk(
             "list<integer>([1,2,3,4,5]).join_to_text(', ', '', '', ${Long.MAX_VALUE})",
             "rt_err:fn:join_to_text:incorrect_limit:9223372036854775807"
         )
         chk(
             "list<integer>([1,2,3,4,5]).join_to_text(', ', '', '', -1)",
             "rt_err:fn:join_to_text:incorrect_limit:-1"
         )
         chk("list<integer>([1,2,3,4,5]).join_to_text(', ', '', '', null)", "text[1, 2, 3, 4, 5]")
    }

    @Test fun testNotFullVirtualIterable() {
        def("function transform(element: integer) { return 'foo' + element; }")
        def("function transform_map(element: (text, integer)) { return 'foo' + element; }")

        val mapArgs = VirtualTestUtils.argToGtv("{'a':1, 'b':2, 'c':3, 'd':4, 'e':5}", "[['a'],['b'],['c'],['d']]")
        chkVirtual(
            "virtual<map<text, integer>>", "x.join_to_text('*', '<', '>', 3, '&&', transform_map(*))", mapArgs,
            "text[<foo(a,1)*foo(b,2)*foo(c,3)*&&>]"
        )

        val collectionArgs = VirtualTestUtils.argToGtv("[1, 2, 3, 4, 5]", "[[0],[1], [2], [3]]")
        chkVirtual(
            "virtual<list<integer>>", "x.join_to_text('*', '<', '>', 3, '&&', transform(*))", collectionArgs,
            "text[<foo1*foo2*foo3*&&>]"
        )
        chkVirtual(
            "virtual<set<integer>>", "x.join_to_text('*', '<', '>', 3, '&&', transform(*))", collectionArgs,
            "text[<foo1*foo2*foo3*&&>]"
        )
    }

    @Test fun testLimitExceedsIterableLength() {
        chk("list<integer>([1,2,3,4,5]).join_to_text('*', '<', '>', 6, '...')", "text[<1*2*3*4*5>]")
    }

    @Test fun testFailingTransform() {
        def("function transform(element: integer) { require(false); return 'foo' + element; }")
        chk("list<integer>([1,2,3,4,5]).join_to_text('*', '<', '>', 5, '...', transform(*))", "req_err:null")
    }

    private val collectionLikeExpectations = listOf(
        "text[1, 2, 3, 4, 5]",
        "text[1*2*3*4*5]",
        "text[<1*2*3*4*5]",
        "text[<1*2*3*4*5>]",
        "text[<1*2*3*...>]",
        "text[<1*2*3*&&>]",
        "text[<foo1*foo2*foo3*&&>]"
    )

    private val mapExpectations = listOf(
        "text[(a,1), (b,2), (c,3), (d,4), (e,5)]",
        "text[(a,1)*(b,2)*(c,3)*(d,4)*(e,5)]",
        "text[<(a,1)*(b,2)*(c,3)*(d,4)*(e,5)]",
        "text[<(a,1)*(b,2)*(c,3)*(d,4)*(e,5)>]",
        "text[<(a,1)*(b,2)*(c,3)*...>]",
        "text[<(a,1)*(b,2)*(c,3)*&&>]",
        "text[<foo(a,1)*foo(b,2)*foo(c,3)*&&>]",
    )

    @Test fun testIterableDefaultParams() {
        def("function transform(element: integer) { return 'foo' + element; }")
        def("function transform_map(element: (text, integer)) { return 'foo' + element; }")
        chkIterableDefaultParams("list<integer>([1, 2, 3, 4, 5])", collectionLikeExpectations)
        chkIterableDefaultParams("set<integer>([1, 2, 3, 4, 5])", collectionLikeExpectations)
        chkIterableDefaultParams("byte_array.from_list([1, 2, 3, 4, 5])", collectionLikeExpectations)
        chkIterableDefaultParams("range(1, 6, 1)", collectionLikeExpectations)
        chkIterableDefaultParams(
            "map<text, integer>(['a':1, 'b':2, 'c':3, 'd':4, 'e':5])",
            mapExpectations,
            "transform_map(*)"
        )
    }

    private fun chkIterableDefaultParams(
        constructorCall: String,
        expectations: List<String>,
        transformFnName: String = "transform(*)"
    ) {
        chk("$constructorCall.join_to_text()", expectations[0])
        chk("$constructorCall.join_to_text('*')", expectations[1])
        chk("$constructorCall.join_to_text('*', '<')", expectations[2])
        chk("$constructorCall.join_to_text('*', '<', '>')", expectations[3])
        chk("$constructorCall.join_to_text('*', '<', '>', 3)", expectations[4])
        chk("$constructorCall.join_to_text('*', '<', '>', 3, '&&')", expectations[5])
        chk("$constructorCall.join_to_text('*', '<', '>', 3, '&&', $transformFnName)", expectations[6])
    }

    @Test fun testVirtualIterablesDefaultParams() {
        def("function transform(element: integer) { return 'foo' + element; }")
        def("function transform_map(element: (text, integer)) { return 'foo' + element; }")

        val args = VirtualTestUtils.argToGtv("[1, 2, 3, 4, 5]", "[[0],[1], [2], [3], [4]]")
        chkVirtualIterableDefaultParams("virtual<list<integer>>", args, collectionLikeExpectations)
        chkVirtualIterableDefaultParams("virtual<set<integer>>", args, collectionLikeExpectations)

        val mapArgs =
            VirtualTestUtils.argToGtv("{'a':1, 'b':2, 'c':3, 'd':4, 'e':5}", "[['a'],['b'],['c'],['d'],['e']]")
        chkVirtualIterableDefaultParams("virtual<map<text, integer>>", mapArgs, mapExpectations, "transform_map(*)")
    }

    private fun chkVirtualIterableDefaultParams(
        constructorCall: String,
        args: Gtv, expectations: List<String>,
        transformFnName: String = "transform(*)"
    ) {
        chkVirtual(constructorCall, "x.join_to_text('*')", args, expectations[1])
        chkVirtual(constructorCall, "x.join_to_text('*', '<')", args, expectations[2])
        chkVirtual(constructorCall, "x.join_to_text('*', '<', '>')", args, expectations[3])
        chkVirtual(constructorCall, "x.join_to_text('*', '<', '>', 3)", args, expectations[4])
        chkVirtual(constructorCall, "x.join_to_text('*', '<', '>', 3, '&&')", args, expectations[5])
        chkVirtual(constructorCall, "x.join_to_text('*', '<', '>', 3, '&&', $transformFnName)", args, expectations[6])
    }

    @Test fun testTransformers() {
        chkTransformer(
            "a: integer",
            "text?",
            "ct_err:expr_call_badargs:[list<integer>.join_to_text]:[text,text,text,integer,text,(integer)->text?]"
        )
        chkTransformer(
            "a: integer",
            "unit",
            "ct_err:[fn_rettype:[unit]:[text]][expr_call_badargs:[list<integer>.join_to_text]:[text,text,text,integer,text,(integer)->unit]]"
        )
        chkTransformer(
            "a: integer",
            "decimal",
            "ct_err:[fn_rettype:[decimal]:[text]][expr_call_badargs:[list<integer>.join_to_text]:[text,text,text,integer,text,(integer)->decimal]]"
        )
        chkTransformer(
            "a: decimal",
            "text",
            "ct_err:expr_call_badargs:[list<integer>.join_to_text]:[text,text,text,integer,text,(decimal)->text]"
        )
        chkTransformer(
            "",
            "text",
            "ct_err:expr_call_badargs:[list<integer>.join_to_text]:[text,text,text,integer,text,()->text]"
        )
        chkTransformer(
            "a: integer, b: integer",
            "text",
            "ct_err:expr_call_badargs:[list<integer>.join_to_text]:[text,text,text,integer,text,(integer,integer)->text]"
        )
        chkTransformer("a: integer, b: integer = 500", "text", "text[<foo*foo*foo*...>]")
        chkTransformer("a: integer, b: integer", "text", "text[<foo*foo*foo*...>]", "transform(*, b = 10)")
        chkTransformer("a: integer?", "text", "text[<foo*foo*foo*...>]")
    }

    private fun chkTransformer(params: String, returnType: String, output: String, transform: String = "transform(*)") {
        chkFull(
            """
            function transform($params): $returnType { return 'foo'; }
            query q() = list<integer>([1, 2, 3, 4, 5]).join_to_text('*', '<', '>', 3, '...', $transform);
        """, output
        )
    }
}
