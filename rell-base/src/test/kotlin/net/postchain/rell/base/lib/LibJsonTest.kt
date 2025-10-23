package net.postchain.rell.base.lib

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BigIntegerNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.ShortNode
import net.postchain.rell.base.lib.type.JsonUtils.canBeRellInteger
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.testutils.BaseRellTest
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

internal object LibJsonTest: BaseRellTest() {
    @Test fun testJsonConstructor() {
        chk("""json('[]')""", "json[[]]")
        chk("""json('{}')""", "json[{}]")
        chk("""json('0')""", "json[0]")
        chk("""json('"A"')""", """json["A"]""")
        chk("""json('[1,2,3]')""", "json[[1,2,3]]")
    }

    @Test fun testJsonStr() {
        chkEx("""{ val s = json('{  "x":5, "y" : 10  }'); return s.str(); }""", """text[{"x":5,"y":10}]""")
    }

    @Test fun testArraySubscript() {
        chk("""json('["a", "b", "c"]')[0]""", "json[\"a\"]")
        chk("""json('["a", "b", "c"]')[1]""", "json[\"b\"]")
        chk("""json('["a", "b", "c"]')[2]""", "json[\"c\"]")
        chk("""json('["a", "b", "c"]')[3]""", "rt_err:expr_json_array_subscript_index:3:3")
        chk("""json('{"a": 1, "b": 2, "c": 3}')[0]""", "rt_err:expr_json_array_subscript_nodetype:OBJECT")
    }

    @Test fun testArraySubscriptDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('["a", "b", "c"]')""")
        chk("data @ {} ( .data[0] )", "json[\"a\"]")
        chk("data @ {} ( .data[1] )", "json[\"b\"]")
        chk("data @ {} ( .data[2] )", "json[\"c\"]")
        chk("data @ {} ( .data[3] )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data[-1] )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data['a'] )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data[-99999999999] )", "rt_err:sqlerr:0") // valid postgres BIGINT, but fails downcast to INT
        chk("data @ {} ( .data[99999999999] )", "rt_err:sqlerr:0") // valid postgres BIGINT, but fails downcast to INT
    }

    @Test fun testArrayGet() {
        chk("""json('["a", "b", "c"]').get(0)""", "json[\"a\"]")
        chk("""json('["a", "b", "c"]').get(1)""", "json[\"b\"]")
        chk("""json('["a", "b", "c"]').get(2)""", "json[\"c\"]")
        chk("""json('["a", "b", "c"]').get(3)""", "rt_err:expr_json_array_get_index:3:3")
        chk("""json('["a", "b", "c"]').get("a")""", "rt_err:expr_json_object_get_nodetype:ARRAY")
    }

    @Test fun testArrayGetDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('["a", "b", "c"]')""")
        chk("data @ {} ( .data.get(0) )", "json[\"a\"]")
        chk("data @ {} ( .data.get(1) )", "json[\"b\"]")
        chk("data @ {} ( .data.get(2) )", "json[\"c\"]")
        chk("data @ {} ( .data.get(3) )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data.get(-1) )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data.get('a') )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data.get(-99999999999) )", "rt_err:sqlerr:0") // valid postgres BIGINT, but fails downcast to INT
        chk("data @ {} ( .data.get(99999999999) )", "rt_err:sqlerr:0") // valid postgres BIGINT, but fails downcast to INT
    }

    @Test fun testArrayGetOrNull() {
        chk("""json('["a", "b", "c"]').get_or_null(0)""", "json[\"a\"]")
        chk("""json('["a", "b", "c"]').get_or_null(1)""", "json[\"b\"]")
        chk("""json('["a", "b", "c"]').get_or_null(2)""", "json[\"c\"]")
        chk("""json('["a", "b", "c"]').get_or_null(3)""", "null")
        chk("""json('["a", "b", "c"]').get_or_null("a")""", "null")
    }

    @Test fun testArrayGetOrNullDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('["a", "b", "c"]')""")
        chk("data @ {} ( .data.get_or_null(0) )", "json[\"a\"]")
        chk("data @ {} ( .data.get_or_null(1) )", "json[\"b\"]")
        chk("data @ {} ( .data.get_or_null(2) )", "json[\"c\"]")
        chk("data @ {} ( .data.get_or_null(3) )", "null")
        chk("data @ {} ( .data.get_or_null(-1) )", "null")
        chk("data @ {} ( .data.get_or_null('a') )", "null")
        chk("data @ {} ( .data.get_or_null(-99999999999) )", "null") // valid postgres BIGINT, but fails downcast to INT
        chk("data @ {} ( .data.get_or_null(99999999999) )", "null") // valid postgres BIGINT, but fails downcast to INT
    }

    @Test fun testObjectSubscript() {
        chk("""json('{"a": 1, "b": 2, "c": 3}')["a"]""", "json[1]")
        chk("""json('{"a": 1, "b": 2, "c": 3}')["b"]""", "json[2]")
        chk("""json('{"a": 1, "b": 2, "c": 3}')["c"]""", "json[3]")
        chk("""json('{"a": 1, "b": 2, "c": 3}')["d"]""", "rt_err:expr_json_object_subscript_key:novalue:d")
        chk("""json('["a", "b", "c"]')["a"]""", "rt_err:expr_json_object_subscript_nodetype:ARRAY")
    }

    @Test fun testObjectSubscriptDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('{"a": 1, "b": 2, "c": 3}')""")
        chk("data @ {} ( .data['a'] )", "json[1]")
        chk("data @ {} ( .data['b'] )", "json[2]")
        chk("data @ {} ( .data['c'] )", "json[3]")
        chk("data @ {} ( .data['d'] )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data[0] )", "rt_err:sqlerr:0")
    }

    @Test fun testObjectGet() {
        chk("""json('{"a": 1, "b": 2, "c": 3}').get("a")""", "json[1]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').get("b")""", "json[2]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').get("c")""", "json[3]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').get("d")""", "rt_err:expr_json_object_get_key:novalue:d")
        chk("""json('["a", "b", "c"]').get("a")""", "rt_err:expr_json_object_get_nodetype:ARRAY")
    }

    @Test fun testObjectGetDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('{"a": 1, "b": 2, "c": 3}')""")
        chk("data @ {} ( .data.get('a') )", "json[1]")
        chk("data @ {} ( .data.get('b') )", "json[2]")
        chk("data @ {} ( .data.get('c') )", "json[3]")
        chk("data @ {} ( .data.get('d') )", "rt_err:sqlerr:0")
        chk("data @ {} ( .data.get(0) )", "rt_err:sqlerr:0")
    }

    @Test fun testObjectGetOrNull() {
        chk("""json('{"a": 1, "b": 2, "c": 3}').get_or_null("a")""", "json[1]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').get_or_null("b")""", "json[2]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').get_or_null("c")""", "json[3]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').get_or_null("d")""", "null")
        chk("""json('["a", "b", "c"]').get_or_null("a")""", "null")
    }

    @Test fun testObjectGetOrNullDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('{"a": 1, "b": 2, "c": 3}')""")
        chk("data @ {} ( .data.get_or_null('a') )", "json[1]")
        chk("data @ {} ( .data.get_or_null('b') )", "json[2]")
        chk("data @ {} ( .data.get_or_null('c') )", "json[3]")
        chk("data @ {} ( .data.get_or_null('d') )", "null")
        chk("data @ {} ( .data.get_or_null(0) )", "null")
    }

    @Test fun testAsInteger() {
        chk("""json('null').as_integer()""", "rt_err:json.as_integer:type_error:NULL")
        chk("""json('{"a": 1, "b": 2, "c": 3}').as_integer()""", "rt_err:json.as_integer:type_error:OBJECT")
        chk("""json('["a", "b", "c"]').as_integer()""", "rt_err:json.as_integer:type_error:ARRAY")
        chk("""json('7.0').as_integer()""", "rt_err:json.as_integer:type_error:NUMBER")
        chk("""json('-7.01').as_integer()""", "rt_err:json.as_integer:type_error:NUMBER")
        chk("""json('true').as_integer()""", "rt_err:json.as_integer:type_error:BOOLEAN")
        chk("""json('7').as_integer()""", "int[7]")
        chk("""json('-7').as_integer()""", "int[-7]")
        chk("""json('0').as_integer()""", "int[0]")
    }

    @Test fun testAsIntegerDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[{"an": "object"}, ["an", "array"], null, "a", 7.0, -7.01, true, 7, -7, 0]')""")
        chk("""data @ {} ( .data[0].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[1].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[2].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[3].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[4].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[5].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[6].as_integer() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[7].as_integer() )""", "int[7]")
        chk("""data @ {} ( .data[8].as_integer() )""", "int[-7]")
        chk("""data @ {} ( .data[9].as_integer() )""", "int[0]")
    }

    @Test fun testAsBoolean() {
        chk("""json('null').as_boolean()""", "rt_err:json.as_boolean:type_error:NULL")
        chk("""json('{"a": 1, "b": 2, "c": 3}').as_boolean()""", "rt_err:json.as_boolean:type_error:OBJECT")
        chk("""json('["a", "b", "c"]').as_boolean()""", "rt_err:json.as_boolean:type_error:ARRAY")
        chk("""json('7.0').as_boolean()""", "rt_err:json.as_boolean:type_error:NUMBER")
        chk("""json('-7.01').as_boolean()""", "rt_err:json.as_boolean:type_error:NUMBER")
        chk("""json('7').as_boolean()""", "rt_err:json.as_boolean:type_error:NUMBER")
        chk("""json('true').as_boolean()""", "boolean[true]")
        chk("""json('false').as_boolean()""", "boolean[false]")
    }

    @Test fun testAsBooleanDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('[{"an": "object"}, ["an", "array"], null, "a", 7.0, -7.01, 7, -7, """ +
            """true, false, 0, 1, "t", "f", "yes", "no"]')""")
        chk("""data @ {} ( .data[0].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[1].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[2].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[3].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[4].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[5].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[6].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[7].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[8].as_boolean() )""", "boolean[true]")
        chk("""data @ {} ( .data[9].as_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[10].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[11].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[12].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[13].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[14].as_boolean() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[15].as_boolean() )""", "rt_err:sqlerr:0")
    }

    @Test fun testAsText() {
        chk("""json('null').as_text()""", "rt_err:json.as_text:type_error:NULL")
        chk("""json('{"a": 1, "b": 2, "c": 3}').as_text()""", "rt_err:json.as_text:type_error:OBJECT")
        chk("""json('["a", "b", "c"]').as_text()""", "rt_err:json.as_text:type_error:ARRAY")
        chk("""json('7.0').as_text()""", "rt_err:json.as_text:type_error:NUMBER")
        chk("""json('-7.01').as_text()""", "rt_err:json.as_text:type_error:NUMBER")
        chk("""json('7').as_text()""", "rt_err:json.as_text:type_error:NUMBER")
        chk("""json('true').as_text()""", "rt_err:json.as_text:type_error:BOOLEAN")
        chk("""json('"true"').as_text()""", "text[true]")
        chk("""json('"hello"').as_text()""", "text[hello]")
    }

    @Test fun testAsTextDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('[null, {"a": 1, "b": 2, "c": 3}, ["a", "b", "c"], 7.0, -7.01, 7, """ +
            """true, "true", "hello", "", "hel\"lo"]')""")
        chk("""data @ {} ( .data[0].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[1].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[2].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[3].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[4].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[5].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[6].as_text() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[7].as_text() )""", "text[true]")
        chk("""data @ {} ( .data[8].as_text() )""", "text[hello]")
        chk("""data @ {} ( .data[9].as_text() )""", "text[]")
        chk("""data @ {} ( .data[10].as_text() )""", """text[hel"lo]""")
        chk("""data @ {} ( .data[10] )""", """json["hel\"lo"]""")
    }

    @Test fun testAsIntegerOrNull() {
        chk("""json('null').as_integer_or_null()""", "null")
        chk("""json('{"a": 1, "b": 2, "c": 3}').as_integer_or_null()""", "null")
        chk("""json('["a", "b", "c"]').as_integer_or_null()""", "null")
        chk("""json('7.0').as_integer_or_null()""", "null")
        chk("""json('-7.01').as_integer_or_null()""", "null")
        chk("""json('true').as_integer_or_null()""", "null")
        chk("""json('7').as_integer_or_null()""", "int[7]")
        chk("""json('-7').as_integer_or_null()""", "int[-7]")
        chk("""json('0').as_integer_or_null()""", "int[0]")
    }

    @Test fun testAsIntegerOrNullDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[{"an": "object"}, ["an", "array"], null, "a", 7.0, -7.01, true, 7, -7, 0]')""")
        chk("""data @ {} ( .data[0].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[1].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[2].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[3].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[4].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[5].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[6].as_integer_or_null() )""", "null")
        chk("""data @ {} ( .data[7].as_integer_or_null() )""", "int[7]")
        chk("""data @ {} ( .data[8].as_integer_or_null() )""", "int[-7]")
        chk("""data @ {} ( .data[9].as_integer_or_null() )""", "int[0]")
    }

    @Test fun testAsBooleanOrNull() {
        chk("""json('null').as_boolean_or_null()""", "null")
        chk("""json('{"a": 1, "b": 2, "c": 3}').as_boolean_or_null()""", "null")
        chk("""json('["a", "b", "c"]').as_boolean_or_null()""", "null")
        chk("""json('7.0').as_boolean_or_null()""", "null")
        chk("""json('-7.01').as_boolean_or_null()""", "null")
        chk("""json('7').as_boolean_or_null()""", "null")
        chk("""json('true').as_boolean_or_null()""", "boolean[true]")
        chk("""json('false').as_boolean_or_null()""", "boolean[false]")
    }

    @Test fun testAsBooleanOrNullDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('[{"an": "object"}, ["an", "array"], null, "a", 7.0, -7.01, 7, -7, """ +
                """true, false, 0, 1, "t", "f", "yes", "no"]')""")
        chk("""data @ {} ( .data[0].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[1].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[2].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[3].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[4].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[5].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[6].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[7].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[8].as_boolean_or_null() )""", "boolean[true]")
        chk("""data @ {} ( .data[9].as_boolean_or_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[10].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[11].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[12].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[13].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[14].as_boolean_or_null() )""", "null")
        chk("""data @ {} ( .data[15].as_boolean_or_null() )""", "null")
    }

    @Test fun testAsTextOrNull() {
        chk("""json('null').as_text_or_null()""", "null")
        chk("""json('{"a": 1, "b": 2, "c": 3}').as_text_or_null()""", "null")
        chk("""json('["a", "b", "c"]').as_text_or_null()""", "null")
        chk("""json('7.0').as_text_or_null()""", "null")
        chk("""json('-7.01').as_text_or_null()""", "null")
        chk("""json('7').as_text_or_null()""", "null")
        chk("""json('true').as_text_or_null()""", "null")
        chk("""json('"true"').as_text_or_null()""", "text[true]")
        chk("""json('"hello"').as_text_or_null()""", "text[hello]")
    }

    @Test fun testAsTextOrNullDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('[null, {"a": 1, "b": 2, "c": 3}, ["a", "b", "c"], 7.0, -7.01, 7, """ +
            """true, "true", "hello", "", "hel\"lo"]')""")
        chk("""data @ {} ( .data[0].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[1].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[2].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[3].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[4].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[5].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[6].as_text_or_null() )""", "null")
        chk("""data @ {} ( .data[7].as_text_or_null() )""", "text[true]")
        chk("""data @ {} ( .data[8].as_text_or_null() )""", "text[hello]")
        chk("""data @ {} ( .data[9].as_text_or_null() )""", "text[]")
        chk("""data @ {} ( .data[10].as_text() )""", """text[hel"lo]""")
        chk("""data @ {} ( .data[10] )""", """json["hel\"lo"]""")
    }

    @Test fun testIsInteger() {
        chk("""json('null').is_integer()""", "boolean[false]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').is_integer()""", "boolean[false]")
        chk("""json('["a", "b", "c"]').is_integer()""", "boolean[false]")
        chk("""json('7.0').is_integer()""", "boolean[false]")
        chk("""json('-7.01').is_integer()""", "boolean[false]")
        chk("""json('true').is_integer()""", "boolean[false]")
        chk("""json('7').is_integer()""", "boolean[true]")
        chk("""json('-7').is_integer()""", "boolean[true]")
        chk("""json('0').is_integer()""", "boolean[true]")
    }

    @Test fun testIsIntegerDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('[null, {"a": 1, "b": 2, "c": 3}, ["a", "b", "c"], [], {}, "hello", """ +
            """true, 7.0, -7.01, 7, 0, -1]')""")
        chk("""data @ {} ( .data[0].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[1].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[2].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[3].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[4].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[5].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[6].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[7].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[8].is_integer() )""", "boolean[false]")
        chk("""data @ {} ( .data[9].is_integer() )""", "boolean[true]")
        chk("""data @ {} ( .data[10].is_integer() )""", "boolean[true]")
        chk("""data @ {} ( .data[11].is_integer() )""", "boolean[true]")
    }

    @Test fun testIsBoolean() {
        chk("""json('null').is_boolean()""", "boolean[false]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').is_boolean()""", "boolean[false]")
        chk("""json('["a", "b", "c"]').is_boolean()""", "boolean[false]")
        chk("""json('7.0').is_boolean()""", "boolean[false]")
        chk("""json('-7.01').is_boolean()""", "boolean[false]")
        chk("""json('7').is_boolean()""", "boolean[false]")
        chk("""json('true').is_boolean()""", "boolean[true]")
        chk("""json('false').is_boolean()""", "boolean[true]")
    }

    @Test fun testIsBooleanDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[null, {"a": 1, "b": 2, "c": 3}, 7.0, 7, ["a", "b", "c"], [], "true", "false", false, true]')""")
        chk("""data @ {} ( .data[0].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[1].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[2].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[3].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[4].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[5].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[6].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[7].is_boolean() )""", "boolean[false]")
        chk("""data @ {} ( .data[8].is_boolean() )""", "boolean[true]")
        chk("""data @ {} ( .data[9].is_boolean() )""", "boolean[true]")
    }

    @Test fun testIsText() {
        chk("""json('null').is_text()""", "boolean[false]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').is_text()""", "boolean[false]")
        chk("""json('["a", "b", "c"]').is_text()""", "boolean[false]")
        chk("""json('7.0').is_text()""", "boolean[false]")
        chk("""json('-7.01').is_text()""", "boolean[false]")
        chk("""json('7').is_text()""", "boolean[false]")
        chk("""json('true').is_text()""", "boolean[false]")
        chk("""json('"true"').is_text()""", "boolean[true]")
        chk("""json('"hello"').is_text()""", "boolean[true]")
    }

    @Test fun testIsTextDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[null, {"a": 1, "b": 2, "c": 3}, 7.0, -7.01, 7, true, ["a", "b", "c"], [], "true", "hello"]')""")
        chk("""data @ {} ( .data[0].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[1].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[2].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[3].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[4].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[5].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[6].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[7].is_text() )""", "boolean[false]")
        chk("""data @ {} ( .data[8].is_text() )""", "boolean[true]")
        chk("""data @ {} ( .data[9].is_text() )""", "boolean[true]")
    }

    @Test fun testIsArray() {
        chk("""json('null').is_array()""", "boolean[false]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').is_array()""", "boolean[false]")
        chk("""json('7.0').is_array()""", "boolean[false]")
        chk("""json('-7.01').is_array()""", "boolean[false]")
        chk("""json('7').is_array()""", "boolean[false]")
        chk("""json('true').is_array()""", "boolean[false]")
        chk("""json('"true"').is_array()""", "boolean[false]")
        chk("""json('"hello"').is_array()""", "boolean[false]")
        chk("""json('["a", "b", "c"]').is_array()""", "boolean[true]")
        chk("""json('[]').is_array()""", "boolean[true]")
    }

    @Test fun testIsArrayDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[null, {"a": 1, "b": 2, "c": 3}, 7.0, -7.01, 7, true, "true", "hello", ["a", "b", "c"], []]')""")
        chk("""data @ {} ( .data[0].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[1].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[2].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[3].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[4].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[5].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[6].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[7].is_array() )""", "boolean[false]")
        chk("""data @ {} ( .data[8].is_array() )""", "boolean[true]")
        chk("""data @ {} ( .data[9].is_array() )""", "boolean[true]")
    }

    @Test fun testIsObject() {
        chk("""json('null').is_object()""", "boolean[false]")
        chk("""json('["a", "b", "c"]').is_object()""", "boolean[false]")
        chk("""json('7.0').is_object()""", "boolean[false]")
        chk("""json('-7.01').is_object()""", "boolean[false]")
        chk("""json('7').is_object()""", "boolean[false]")
        chk("""json('true').is_object()""", "boolean[false]")
        chk("""json('"true"').is_object()""", "boolean[false]")
        chk("""json('"hello"').is_object()""", "boolean[false]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').is_object()""", "boolean[true]")
        chk("""json('{}').is_object()""", "boolean[true]")
    }

    @Test fun testIsObjectDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[null, ["a", "b", "c"], 7.0, -7.01, 7, true, "true", "hello", {"a": 1, "b": 2, "c": 3}, {}]')""")
        chk("""data @ {} ( .data[0].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[1].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[2].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[3].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[4].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[5].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[6].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[7].is_object() )""", "boolean[false]")
        chk("""data @ {} ( .data[8].is_object() )""", "boolean[true]")
        chk("""data @ {} ( .data[9].is_object() )""", "boolean[true]")
    }

    @Test fun testIsNull() {
        chk("""json('["a", "b", "c"]').is_null()""", "boolean[false]")
        chk("""json('7.0').is_null()""", "boolean[false]")
        chk("""json('-7.01').is_null()""", "boolean[false]")
        chk("""json('7').is_null()""", "boolean[false]")
        chk("""json('true').is_null()""", "boolean[false]")
        chk("""json('"true"').is_null()""", "boolean[false]")
        chk("""json('"hello"').is_null()""", "boolean[false]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').is_null()""", "boolean[false]")
        chk("""json('null').is_null()""", "boolean[true]")
    }

    @Test fun testIsNullDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data",
            """1,json('[["a", "b", "c"], [], -7.01, 7, true, "hello", {"a": 1, "b": 2, "c": 3}, {}, "null", null]')""")
        chk("""data @ {} ( .data[0].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[1].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[2].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[3].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[4].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[5].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[6].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[7].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[8].is_null() )""", "boolean[false]")
        chk("""data @ {} ( .data[9].is_null() )""", "boolean[true]")
    }

    @Test fun testSize() {
        chk("""json('7.0').size()""", "rt_err:json.size:type_error:NUMBER")
        chk("""json('-7.01').size()""", "rt_err:json.size:type_error:NUMBER")
        chk("""json('7').size()""", "rt_err:json.size:type_error:NUMBER")
        chk("""json('true').size()""", "rt_err:json.size:type_error:BOOLEAN")
        chk("""json('"true"').size()""", "rt_err:json.size:type_error:STRING")
        chk("""json('"hello"').size()""", "rt_err:json.size:type_error:STRING")
        chk("""json('null').size()""", "rt_err:json.size:type_error:NULL")
        chk("""json('["a", "b", "c"]').size()""", "int[3]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').size()""", "int[3]")
        chk("""json('[]').size()""", "int[0]")
        chk("""json('{}').size()""", "int[0]")
        chk("""json('{"a": {"b": {"c": "d"}}}').size()""", "int[1]")
    }

    @Test fun testSizeDb() {
        tstCtx.useSql = true
        def("entity data { data: json; }")
        insert("c0.data", "data", """1,json('[-7.01, 7, true, "hello", "null", null, ["a", "b", "c"], [], """ +
            """{"a": 1, "b": 2, "c": 3}, {}, {"a": {"b": {"c": "d"}}}]')""")
        chk("""data @ {} ( .data[0].size() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[1].size() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[2].size() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[3].size() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[4].size() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[5].size() )""", "rt_err:sqlerr:0")
        chk("""data @ {} ( .data[6].size() )""", "int[3]")
        chk("""data @ {} ( .data[7].size() )""", "int[0]")
        chk("""data @ {} ( .data[8].size() )""", "int[3]")
        chk("""data @ {} ( .data[9].size() )""", "int[0]")
        chk("""data @ {} ( .data[10].size() )""", "int[1]")
    }

    @Test fun testKeys() {
        chk("""json('7.0').keys()""", "rt_err:json.keys:type_error:NUMBER")
        chk("""json('-7.01').keys()""", "rt_err:json.keys:type_error:NUMBER")
        chk("""json('7').keys()""", "rt_err:json.keys:type_error:NUMBER")
        chk("""json('true').keys()""", "rt_err:json.keys:type_error:BOOLEAN")
        chk("""json('"true"').keys()""", "rt_err:json.keys:type_error:STRING")
        chk("""json('"hello"').keys()""", "rt_err:json.keys:type_error:STRING")
        chk("""json('null').keys()""", "rt_err:json.keys:type_error:NULL")
        chk("""json('[]').keys()""", "rt_err:json.keys:type_error:ARRAY")
        chk("""json('["a", "b", "c"]').keys()""", "rt_err:json.keys:type_error:ARRAY")
        chk("""json('{}').keys()""", "set<text>[]")
        chk("""json('{"a": 1, "b": 2, "c": 3}').keys()""", "set<text>[text[a],text[b],text[c]]")
    }

    @Test fun testToRellIntGood() {
        chkToRellIntOk(BigIntegerNode(BigInteger.valueOf(0)), 0)
        chkToRellIntOk(BigIntegerNode(BigInteger.valueOf(-1)), -1)
        chkToRellIntOk(BigIntegerNode(BigInteger.valueOf(1)), 1)
        chkToRellIntOk(BigIntegerNode(BigInteger.valueOf(Rt_IntValue.MIN_VALUE)), Rt_IntValue.MIN_VALUE)
        chkToRellIntOk(BigIntegerNode(BigInteger.valueOf(Rt_IntValue.MAX_VALUE)), Rt_IntValue.MAX_VALUE)
        chkToRellIntOk(LongNode(0), 0)
        chkToRellIntOk(LongNode(-1), -1)
        chkToRellIntOk(LongNode(1), 1)
        chkToRellIntOk(LongNode(java.lang.Long.MIN_VALUE), java.lang.Long.MIN_VALUE)
        chkToRellIntOk(LongNode(java.lang.Long.MAX_VALUE), java.lang.Long.MAX_VALUE)
        chkToRellIntOk(ShortNode(0), 0)
        chkToRellIntOk(ShortNode(-1), -1)
        chkToRellIntOk(ShortNode(1), 1)
        chkToRellIntOk(ShortNode(java.lang.Short.MIN_VALUE), java.lang.Short.MIN_VALUE.toLong())
        chkToRellIntOk(ShortNode(java.lang.Short.MAX_VALUE), java.lang.Short.MAX_VALUE.toLong())
        chkToRellIntOk(IntNode(0), 0)
        chkToRellIntOk(IntNode(-1), -1)
        chkToRellIntOk(IntNode(1), 1)
        chkToRellIntOk(IntNode(Integer.MIN_VALUE), Integer.MIN_VALUE.toLong())
        chkToRellIntOk(IntNode(Integer.MAX_VALUE), Integer.MAX_VALUE.toLong())
    }

    private fun chkToRellIntOk(node: JsonNode, expected: Long) {
        assert(canBeRellInteger(node))
        assertEquals(Rt_IntValue.get(node.asLong()), Rt_IntValue.get(expected))
    }

    @Test fun testToRellIntBad() {
        assert(!canBeRellInteger(BigIntegerNode(BigInteger.valueOf(Rt_IntValue.MIN_VALUE).minus(BigInteger.ONE))))
        assert(!canBeRellInteger(BigIntegerNode(BigInteger.valueOf(Rt_IntValue.MAX_VALUE).plus(BigInteger.ONE))))
    }
}