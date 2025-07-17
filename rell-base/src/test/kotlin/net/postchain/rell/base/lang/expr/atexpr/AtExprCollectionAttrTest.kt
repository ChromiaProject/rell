/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.atexpr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.GtvTestUtils
import net.postchain.rell.base.testutils.VirtualTestUtils
import kotlin.test.Test

class AtExprCollectionAttrTest: BaseRellTest() {
    init {
        tst.strictToString = false
    }

    @Test fun testEntityWhatAttr() {
        initEntity("i: integer; t: text;", "i,t", listOf("100,123,'Bob'", "101,456,'Alice'"))
        chk("data() @* {}", "[rec[100], rec[101]]")
        chk("data() @* {} ( .i )", "[123, 456]")
        chk("data() @* {} ( .t )", "[Bob, Alice]")
        chk("data() @* {} ( .i, .t)", "[(i=123,t=Bob), (i=456,t=Alice)]")
    }

    @Test fun testEntityWhere1() {
        initEntity("i: integer; t: text;", "i,t", listOf("100,123,'Bob'", "101,456,'Alice'"))
        chkWhereAttrs2("rec[100]", "rec[101]")
    }

    @Test fun testEntityWhere2() {
        initEntity("i1: integer; t1: text; i2: integer; t2: text;", "i1,t1,i2,t2",
                listOf("100,123,'Bob',321,'Adidas'", "101,456,'Alice',654,'Reebok'"))
        chkWhereAttrs4("rec[100]", "rec[101]", "rec.")
    }

    private fun initEntity(fields: String, columns: String, values: List<String>) {
        tstCtx.useSql = true
        def("entity rec { $fields }")
        def("function data() = list(rec @* {});")
        insert("c0.rec", columns, *values.toTypedArray())
    }

    @Test fun testStructWhatAttr() {
        def("struct rec { i: integer; t: text; }")
        def("function data() = [rec(123,'Bob'), rec(456,'Alice')];")

        chk("data() @* {}", "[rec{i=123,t=Bob}, rec{i=456,t=Alice}]")
        chk("data() @* {} ( .i )", "[123, 456]")
        chk("data() @* {} ( .t )", "[Bob, Alice]")
        chk("data() @* {} ( .i, .t)", "[(i=123,t=Bob), (i=456,t=Alice)]")
    }

    @Test fun testStructWhere1() {
        def("struct rec { i: integer; t: text; }")
        def("function data() = [rec(123,'Bob'), rec(456,'Alice')];")
        chkWhereAttrs2("rec{i=123,t=Bob}", "rec{i=456,t=Alice}")
    }

    @Test fun testStructWhere2() {
        def("struct rec { i1: integer; t1: text; i2: integer; t2: text; }")
        def("function data() = [rec(i1=123,t1='Bob',i2=321,t2='Adidas'), rec(i1=456,t1='Alice',i2=654,t2='Reebok')];")
        chkWhereAttrs4("rec{i1=123,t1=Bob,i2=321,t2=Adidas}", "rec{i1=456,t1=Alice,i2=654,t2=Reebok}", "rec.")
    }

    @Test fun testTupleWhatAttr() {
        def("function data() = [(i=123,t='Bob'),(i=456,t='Alice')];")

        chk("data() @* {}", "[(i=123,t=Bob), (i=456,t=Alice)]")
        chk("data() @* {} ( .i )", "[123, 456]")
        chk("data() @* {} ( .t )", "[Bob, Alice]")
        chk("data() @* {} ( .i, .t)", "[(i=123,t=Bob), (i=456,t=Alice)]")
        chk("data() @* {} ( $[0] )", "[123, 456]")
        chk("data() @* {} ( $[1] )", "[Bob, Alice]")
        chk("data() @* {} ( $[0], $[1] )", "[(123,Bob), (456,Alice)]")
    }

    @Test fun testTupleWhere1() {
        def("function data() = [(123,'Bob'),(456,'Alice')];")

        chk("data() @* { 123 }", "[(123,Bob)]")
        chk("data() @* { 456 }", "[(456,Alice)]")
        chk("data() @* { 'Bob' }", "[(123,Bob)]")
        chk("data() @* { 'Alice' }", "[(456,Alice)]")
        chk("data() @* { x'beef' }", "ct_err:at_where_type:0:byte_array")
    }

    @Test fun testTupleWhere2() {
        def("function data() = [(i=123,t='Bob'),(i=456,t='Alice')];")
        chkWhereAttrs2("(i=123,t=Bob)", "(i=456,t=Alice)")
    }

    @Test fun testTupleWhere3() {
        def("function data() = [(123,'Bob',321,'Adidas'),(456,'Alice',654,'Reebok')];")
        val type = "(integer,text,integer,text)"

        chk("data() @* { 123 }", "ct_err:at_attr_type_ambig:0:integer:[$:$type.0,$:$type.2]")
        chk("data() @* { 'Bob' }", "ct_err:at_attr_type_ambig:0:text:[$:$type.1,$:$type.3]")
        chk("data() @* { x'beef' }", "ct_err:at_where_type:0:byte_array")
        chk("data() @* { $[0] == 123 }", "[(123,Bob,321,Adidas)]")
        chk("data() @* { $[1] == 'Alice' }", "[(456,Alice,654,Reebok)]")
        chk("data() @* { $[2] == 321 }", "[(123,Bob,321,Adidas)]")
        chk("data() @* { $[3] == 'Adidas' }", "[(123,Bob,321,Adidas)]")
    }

    @Test fun testTupleWhere4() {
        def("function data() = [(i1=123,t1='Bob',i2=321,t2='Adidas'),(i1=456,t1='Alice',i2=654,t2='Reebok')];")
        chkWhereAttrs4("(i1=123,t1=Bob,i2=321,t2=Adidas)", "(i1=456,t1=Alice,i2=654,t2=Reebok)", "(i1:integer,t1:text,i2:integer,t2:text).")
    }

    @Test fun testTupleWhere5() {
        def("function data() = [(i=123,t='Bob',321,'Adidas'),(i=456,t='Alice',654,'Reebok')];")
        val type = "(i:integer,t:text,integer,text)"

        chk("data() @* { 123 }", "ct_err:at_attr_type_ambig:0:integer:[$:$type.i,$:$type.2]")
        chk("data() @* { 'Alice' }", "ct_err:at_attr_type_ambig:0:text:[$:$type.t,$:$type.3]")
        chk("data() @* { x'beef' }", "ct_err:at_where_type:0:byte_array")

        chk("data() @* { .i == 123 }", "[(i=123,t=Bob,321,Adidas)]")
        chk("data() @* { .t == 'Alice' }", "[(i=456,t=Alice,654,Reebok)]")
        chk("data() @* { $[0] == 123 }", "[(i=123,t=Bob,321,Adidas)]")
        chk("data() @* { $[1] == 'Alice' }", "[(i=456,t=Alice,654,Reebok)]")
        chk("data() @* { $[2] == 321 }", "[(i=123,t=Bob,321,Adidas)]")
        chk("data() @* { $[3] == 'Reebok' }", "[(i=456,t=Alice,654,Reebok)]")

        chkEx("{ val i = 123; return data() @* { i }; }", "[(i=123,t=Bob,321,Adidas)]")
        chkEx("{ val t = 'Alice'; return data() @* { t }; }", "[(i=456,t=Alice,654,Reebok)]")
        chkEx("{ val i = 321; return data() @* { i }; }", "[]")
        chkEx("{ val t = 'Reebok'; return data() @* { t }; }", "[]")

        chkEx("{ val t = 123; return data() @* { t }; }", "ct_err:at_where:var_noattrs:0:t:integer")
        chkEx("{ val i = 'Alice'; return data() @* { i }; }", "ct_err:at_where:var_noattrs:0:i:text")
        chkEx("{ val i = x'beef'; return data() @* { i }; }", "ct_err:at_where:var_noattrs:0:i:byte_array")

        chkEx("{ val x = 123; return data() @* { x }; }", "ct_err:at_where:var_manyattrs_type:0:x:integer:[$:$type.i,$:$type.2]")
        chkEx("{ val x = 'Alice'; return data() @* { x }; }", "ct_err:at_where:var_manyattrs_type:0:x:text:[$:$type.t,$:$type.3]")
    }

    @Test fun testEnumWhatAttr() {
        def("enum colors { red, green, blue }")
        def("function data() = colors.values();")

        chk("data() @* {}", "[red, green, blue]")
        chk("data() @* {} ( .name )", "[red, green, blue]")
        chk("data() @* {} ( .value )", "[0, 1, 2]")
        chk("data() @* {} ( .name, .value )", "[(name=red,value=0), (name=green,value=1), (name=blue,value=2)]")
    }

    @Test fun testEnumWhere() {
        def("enum colors { red, green, blue }")
        def("function data() = colors.values();")

        chk("data() @* { 'red' }", "[red]")
        chk("data() @* { 'green' }", "[green]")
        chk("data() @* { 'blue' }", "[blue]")
        chk("data() @* { 0 }", "[red]")
        chk("data() @* { 1 }", "[green]")
        chk("data() @* { 2 }", "[blue]")
        chk("data() @* { x'beef' }", "ct_err:at_where_type:0:byte_array")

        chkEx("{ val name = 'red'; return data() @* { name }; }", "[red]")
        chkEx("{ val value = 1; return data() @* { value }; }", "[green]")
        chkEx("{ val name = 1; return data() @* { name }; }", "ct_err:at_where:var_noattrs:0:name:integer")
        chkEx("{ val value = 'red'; return data() @* { value }; }", "ct_err:at_where:var_noattrs:0:value:text")
        chkEx("{ val name = x'beef'; return data() @* { name }; }", "ct_err:at_where:var_noattrs:0:name:byte_array")
    }

    @Test fun testVirtualTupleWhatAttr() {
        initVirtualTuple("i:integer,t:text", "[[0],[1]]", listOf("[123,'Bob']", "[456,'Alice']"))
        chk("data() @* {}", "[virtual(i=123,t=Bob), virtual(i=456,t=Alice)]")
        chk("data() @* {} ( .i )", "[123, 456]")
        chk("data() @* {} ( .t )", "[Bob, Alice]")
        chk("data() @* {} ( .i, .t)", "[(i=123,t=Bob), (i=456,t=Alice)]")
    }

    @Test fun testVirtualTupleWhere1() {
        initVirtualTuple("i:integer,t:text", "[[0],[1]]", listOf("[123,'Bob']", "[456,'Alice']"))
        chkWhereAttrs2("virtual(i=123,t=Bob)", "virtual(i=456,t=Alice)")
    }

    @Test fun testVirtualTupleWhere2() {
        val fields = "i1:integer,t1:text,i2:integer,t2:text"
        initVirtualTuple(fields, "[[0],[1],[2],[3]]", listOf("[123,'Bob',321,'Adidas']", "[456,'Alice',654,'Reebok']"))
        chkWhereAttrs4("virtual(i1=123,t1=Bob,i2=321,t2=Adidas)", "virtual(i1=456,t1=Alice,i2=654,t2=Reebok)", "virtual<($fields)>.")
    }

    private fun initVirtualTuple(fields: String, paths: String, values: List<String>) {
        val data = values.map {
            val json = GtvTestUtils.encodeGtvStr(VirtualTestUtils.argToGtv(it, paths))
            "virtual<($fields)>.from_gtv(gtv.from_json('$json'))"
        }.joinToString()
        def("function data() = [$data];")
    }

    @Test fun testVirtualStructWhatAttr() {
        initVirtualStruct("i: integer; t: text;", "[[0],[1]]", listOf("[123,'Bob']", "[456,'Alice']"))
        chk("data() @* {}", "[virtual<rec>{i=123,t=Bob}, virtual<rec>{i=456,t=Alice}]")
        chk("data() @* {} ( .i )", "[123, 456]")
        chk("data() @* {} ( .t )", "[Bob, Alice]")
        chk("data() @* {} ( .i, .t)", "[(i=123,t=Bob), (i=456,t=Alice)]")
    }

    @Test fun testVirtualStructWhere1() {
        initVirtualStruct("i: integer; t: text;", "[[0],[1]]", listOf("[123,'Bob']", "[456,'Alice']"))
        chkWhereAttrs2("virtual<rec>{i=123,t=Bob}", "virtual<rec>{i=456,t=Alice}")
    }

    @Test fun testVirtualStructWhere2() {
        initVirtualStruct("i1: integer; t1: text; i2: integer; t2: text;", "[[0],[1],[2],[3]]",
                listOf("[123,'Bob',321,'Adidas']", "[456,'Alice',654,'Reebok']"))
        chkWhereAttrs4("virtual<rec>{i1=123,t1=Bob,i2=321,t2=Adidas}", "virtual<rec>{i1=456,t1=Alice,i2=654,t2=Reebok}",
                "virtual<rec>.")
    }

    private fun initVirtualStruct(fields: String, paths: String, values: List<String>) {
        def("struct rec { $fields }")
        val data = values.map {
            val json = GtvTestUtils.encodeGtvStr(VirtualTestUtils.argToGtv(it, paths))
            "virtual<rec>.from_gtv(gtv.from_json('$json'))"
        }.joinToString()
        def("function data() = [$data];")
    }

    @Test fun testNullable() {
        def("struct rec { i: integer; t: text; }")
        def("function data(): list<rec?> = [rec(123,'Bob'), rec(456,'Alice')];")

        chk("data() @* {}", "[rec{i=123,t=Bob}, rec{i=456,t=Alice}]")
        chk("data() @* {} ( .i )", "ct_err:expr_attr_unknown:i")
        chk("data() @* {} ( .t )", "ct_err:expr_attr_unknown:t")
        chk("data() @* {} ( .i, .t)", "ct_err:[expr_attr_unknown:i][expr_attr_unknown:t]")

        chk("data() @* {} ( $.i )", "ct_err:expr_mem_null:rec?:i")
        chk("data() @* {} ( $.t )", "ct_err:expr_mem_null:rec?:t")
        chk("data() @* {} ( $.i, $.t)", "ct_err:[expr_mem_null:rec?:i][expr_mem_null:rec?:t]")
        chk("data() @* {} ( $!!.i )", "[123, 456]")
        chk("data() @* {} ( $!!.t )", "[Bob, Alice]")
        chk("data() @* {} ( $!!.i, $!!.t)", "[(123,Bob), (456,Alice)]")
        chk("data() @* {} ( $?.i )", "[123, 456]")
        chk("data() @* {} ( $?.t )", "[Bob, Alice]")
        chk("data() @* {} ( $?.i, $?.t)", "[(i=123,t=Bob), (i=456,t=Alice)]")

        chk("(a: data()) @* {} ( a.i )", "ct_err:expr_mem_null:rec?:i")
        chk("(a: data()) @* {} ( a.t )", "ct_err:expr_mem_null:rec?:t")
        chk("(a: data()) @* {} ( a.i, a.t)", "ct_err:[expr_mem_null:rec?:i][expr_mem_null:rec?:t]")
        chk("(a: data()) @* {} ( a!!.i )", "[123, 456]")
        chk("(a: data()) @* {} ( a!!.t )", "[Bob, Alice]")
        chk("(a: data()) @* {} ( a!!.i, a!!.t)", "[(123,Bob), (456,Alice)]")
        chk("(a: data()) @* {} ( a?.i )", "[123, 456]")
        chk("(a: data()) @* {} ( a?.t )", "[Bob, Alice]")
        chk("(a: data()) @* {} ( a?.i, a?.t)", "[(i=123,t=Bob), (i=456,t=Alice)]")

        chk("data() @* { 123 }", "ct_err:at_where_type:0:integer")
        chk("data() @* { 'Bob' }", "ct_err:at_where_type:0:text")
        chk("data() @* { .i == 123 }", "ct_err:expr_attr_unknown:i")
        chk("data() @* { .t == 'Bob' }", "ct_err:expr_attr_unknown:t")

        chkEx("{ val i = 123; return data() @* { i }; }", "ct_err:at_where:var_noattrs:0:i:integer")
        chkEx("{ val t = 'Bob'; return data() @* { t }; }", "ct_err:at_where:var_noattrs:0:t:text")
        chkEx("{ val x = 123; return data() @* { x }; }", "ct_err:at_where:var_noattrs:0:x:integer")
        chkEx("{ val x = 'Bob'; return data() @* { x }; }", "ct_err:at_where:var_noattrs:0:x:text")
    }

    private fun chkWhereAttrs2(v1: String, v2: String) {
        chk("data() @* { 123 }", "[$v1]")
        chk("data() @* { 456 }", "[$v2]")
        chk("data() @* { 'Bob' }", "[$v1]")
        chk("data() @* { 'Alice' }", "[$v2]")
        chk("data() @* { x'beef' }", "ct_err:at_where_type:0:byte_array")

        chkEx("{ val i = 123; return data() @* { i }; }", "[$v1]")
        chkEx("{ val t = 'Alice'; return data() @* { t }; }", "[$v2]")
        chkEx("{ val t = 123; return data() @* { t }; }", "ct_err:at_where:var_noattrs:0:t:integer")
        chkEx("{ val i = 'Alice'; return data() @* { i }; }", "ct_err:at_where:var_noattrs:0:i:text")
        chkEx("{ val i = x'beef'; return data() @* { i }; }", "ct_err:at_where:var_noattrs:0:i:byte_array")
        chkEx("{ val x = 123; return data() @* { x }; }", "[$v1]")
        chkEx("{ val x = 'Alice'; return data() @* { x }; }", "[$v2]")
    }

    private fun chkWhereAttrs4(v1: String, v2: String, baseName: String) {
        chk("data() @* { 123 }", "ct_err:at_attr_type_ambig:0:integer:[$:${baseName}i1,$:${baseName}i2]")
        chk("data() @* { 'Alice' }", "ct_err:at_attr_type_ambig:0:text:[$:${baseName}t1,$:${baseName}t2]")
        chk("data() @* { x'beef' }", "ct_err:at_where_type:0:byte_array")

        chkEx("{ val i1 = 123; return data() @* { i1 }; }", "[$v1]")
        chkEx("{ val t1 = 'Alice'; return data() @* { t1 }; }", "[$v2]")
        chkEx("{ val i2 = 123; return data() @* { i2 }; }", "[]")
        chkEx("{ val i2 = 321; return data() @* { i2 }; }", "[$v1]")
        chkEx("{ val t2 = 'Alice'; return data() @* { t2 }; }", "[]")
        chkEx("{ val t2 = 'Reebok'; return data() @* { t2 }; }", "[$v2]")

        chkEx("{ val t1 = 123; return data() @* { t1 }; }", "ct_err:at_where:var_noattrs:0:t1:integer")
        chkEx("{ val i1 = 'Alice'; return data() @* { i1 }; }", "ct_err:at_where:var_noattrs:0:i1:text")
        chkEx("{ val i1 = x'beef'; return data() @* { i1 }; }", "ct_err:at_where:var_noattrs:0:i1:byte_array")

        chkEx("{ val x = 123; return data() @* { x }; }", "ct_err:at_where:var_manyattrs_type:0:x:integer:[$:${baseName}i1,$:${baseName}i2]")
        chkEx("{ val x = 'Alice'; return data() @* { x }; }", "ct_err:at_where:var_manyattrs_type:0:x:text:[$:${baseName}t1,$:${baseName}t2]")
    }

    @Test fun testWhatAttrPathEntity() {
        tstCtx.useSql = true
        def("entity ref { i: integer; t: text; }")
        def("entity data { ref; }")
        insert("c0.ref", "i,t", "100,123,'Bob'")
        insert("c0.data", "ref", "200,100")

        chk("data @* {} ( .ref.i, .ref.t )", "[(123,Bob)]")
        chk("data @* {} ( $.ref.i, $.ref.t )", "[(123,Bob)]")
        chk("data @* {} ( data.ref.i, data.ref.t )", "[(123,Bob)]")
        chk("(d:data) @* {} ( d.ref.i, d.ref.t )", "[(123,Bob)]")
    }

    @Test fun testWhatAttrPathStruct() {
        tstCtx.useSql = true
        def("struct sref { i: integer; t: text; }")
        def("entity eref { i: integer; t: text; }")
        def("struct data { eref; sref; }")
        def("function get_data() = [data(sref(123,'Bob'), eref@{})];")
        insert("c0.eref", "i,t", "100,123,'Bob'")

        chk("get_data() @* {} ( .eref.i, .eref.t )", "[(123,Bob)]")
        chk("get_data() @* {} ( $.eref.i, $.eref.t )", "[(123,Bob)]")
        chk("(d:get_data()) @* {} ( d.eref.i, d.eref.t )", "[(123,Bob)]")
        chk("get_data() @* {} ( .sref.i, .sref.t )", "[(123,Bob)]")
        chk("get_data() @* {} ( $.sref.i, $.sref.t )", "[(123,Bob)]")
        chk("(d:get_data()) @* {} ( d.sref.i, d.sref.t )", "[(123,Bob)]")
    }

    @Test fun testWhatAttrPathTuple() {
        tstCtx.useSql = true
        def("struct sref { i: integer; t: text; }")
        def("entity eref { i: integer; t: text; }")
        def("struct data { eref; sref; }")
        def("function get_data() = [(sref = sref(123,'Bob'), eref = eref@{})];")
        insert("c0.eref", "i,t", "100,123,'Bob'")

        chk("get_data() @* {} ( .eref.i, .eref.t )", "[(123,Bob)]")
        chk("get_data() @* {} ( $.eref.i, $.eref.t )", "[(123,Bob)]")
        chk("(d:get_data()) @* {} ( d.eref.i, d.eref.t )", "[(123,Bob)]")
        chk("get_data() @* {} ( .sref.i, .sref.t )", "[(123,Bob)]")
        chk("get_data() @* {} ( $.sref.i, $.sref.t )", "[(123,Bob)]")
        chk("(d:get_data()) @* {} ( d.sref.i, d.sref.t )", "[(123,Bob)]")
    }

    @Test fun testMemberFunction() {
        def("function get_data() = ['Bob', 'Alice', 'Trudy'];")

        chk("get_data() @* {} (.upper_case())", "[BOB, ALICE, TRUDY]")
        chk("get_data() @* {} (.lower_case())", "[bob, alice, trudy]")
        chk("get_data() @* {} (.size())", "[3, 5, 5]")
        chk("get_data() @* {} (.sub(0, 1))", "[B, A, T]")

        chk("get_data() @* {.upper_case() == 'BOB'}", "[Bob]")
        chk("get_data() @* {.sub(0, 1) == 'B'}", "[Bob]")
    }
}
