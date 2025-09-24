/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.type

import net.postchain.rell.base.lib.LibBlockTransactionTest
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import kotlin.test.Test

class MirrorStructEntityTest: BaseRellTest() {
    @Test fun testValidParameterKinds() {
        def("entity my_entity {}")
        def("object my_object {}")

        chkType("struct<my_entity>", "OK")
        chkType("struct<my_object>", "OK")
    }

    @Test fun testInvalidParameterKinds() {
        def("entity my_entity {}")
        def("struct my_struct {}")
        def("function my_function() {}")
        def("query my_query() = 0;")
        def("enum my_enum { red, green, blue }")

        chkType("struct<boolean>", "ct_err:type:struct:bad_type:boolean")
        chkType("struct<integer>", "ct_err:type:struct:bad_type:integer")

        chkType("struct<my_struct>", "ct_err:type:struct:bad_type:my_struct")
        chkType("struct<my_function>", "ct_err:wrong_name:type:function:my_function")
        chkType("struct<my_query>", "ct_err:wrong_name:type:query:my_query")
        chkType("struct<my_enum>", "ct_err:type:struct:bad_type:my_enum")
        chkType("struct<my_entity?>", "ct_err:type:struct:bad_type:my_entity?")

        chkType("struct<list<integer>>", "ct_err:type:struct:bad_type:list<integer>")
        chkType("struct<set<integer>>", "ct_err:type:struct:bad_type:set<integer>")
        chkType("struct<map<integer, text>>", "ct_err:type:struct:bad_type:map<integer,text>")
    }

    @Test fun testEntityConstructor() {
        def("entity user { name; rating: integer; }")

        chk("struct<user>('Bob', 123)", "struct<user>[name=text[Bob],rating=int[123]]")
        chk("struct<user>(rating = 123, name = 'Bob')", "struct<user>[name=text[Bob],rating=int[123]]")
        chk("struct<user>()", "ct_err:attr_missing:[struct<user>]:name,rating")
        chk("struct<user>(name = 'Bob')", "ct_err:attr_missing:[struct<user>]:rating")
        chk("struct<user>(name = 'Bob', rating = null)", "ct_err:attr_bad_type:1:rating:integer:null")
    }

    @Test fun testEntityConstructorDefaultValues() {
        def("entity data1 { x: integer = 123; y: text = 'abc'; }")
        def("entity data2 { x: integer = 123; y: text; }")

        chk("struct<data1>()", "struct<data1>[x=int[123],y=text[abc]]")
        chk("struct<data2>()", "ct_err:attr_missing:[struct<data2>]:y")
        chk("struct<data2>(x = 456)", "ct_err:attr_missing:[struct<data2>]:y")
        chk("struct<data2>(y = 'hello')", "struct<data2>[x=int[123],y=text[hello]]")
    }

    @Test fun testObjectConstructor() {
        def("object state { x: integer = 123; y: text = 'abc'; }")

        chk("struct<state>()", "struct<state>[x=int[123],y=text[abc]]")
        chk("struct<state>(x = 456)", "struct<state>[x=int[456],y=text[abc]]")
        chk("struct<state>(y = 'xyz')", "struct<state>[x=int[123],y=text[xyz]]")
        chk("struct<state>(456, 'xyz')", "struct<state>[x=int[456],y=text[xyz]]")
    }

    @Test fun testAttributeRead() {
        chkAttributeRead("entity data { x: integer; y: text; }")
        chkAttributeRead("object data { x: integer = 0; y: text = '?'; }")
    }

    private fun chkAttributeRead(def: String) {
        val t = RellCodeTester(tstCtx)
        t.def(def)
        t.chkEx("{ val s = struct<data>(x = 123, y = 'abc'); return s.x; }", "int[123]")
        t.chkEx("{ val s = struct<data>(x = 123, y = 'abc'); return s.y; }", "text[abc]")
    }

    @Test fun testAttributeWrite() {
        chkAttributeWrite("entity data { x: integer; mutable y: text; }")
        chkAttributeWrite("object data { x: integer = 123; mutable y: text = 'abc'; }")
    }

    private fun chkAttributeWrite(def: String) {
        val t = RellCodeTester(tstCtx)
        t.def(def)
        val init = "val s = struct<data>(x = 123, y = 'abc');"
        t.chkEx("{ $init s.x = 456; return 0; }", "ct_err:attr_not_mutable:struct<data>.x")
        t.chkEx("{ $init s.y = 'xyz'; return 0; }", "ct_err:attr_not_mutable:struct<data>.y")
    }

    @Test fun testInstanceMemberFunctions() {
        chkInstanceMemberFunctions("entity data { x: integer; y: text; }")
        chkInstanceMemberFunctions("object data { x: integer = 0; y: text = ''; }")
    }

    private fun chkInstanceMemberFunctions(def: String) {
        val t = RellCodeTester(tstCtx)
        t.def(def)
        val expr = "struct<data>(x = 123, y = 'abc')"
        t.chk("$expr.to_gtv()", """gtv[[123, "abc"]]""")
        t.chk("$expr.to_gtv_pretty()", """gtv[["x": 123, "y": "abc"]]""")
        t.chk("$expr.to_bytes()", "byte_array[a50e300ca30302017ba2050c03616263]")
        t.chk("$expr.bad_name()", "ct_err:unknown_member:[struct<data>]:bad_name")
    }

    @Test fun testStaticMemberFunctions() {
        chkStaticMemberFunctions("entity data { x: integer; y: text; }")
        chkStaticMemberFunctions("object data { x: integer = 0; y: text = ''; }")
    }

    private fun chkStaticMemberFunctions(def: String) {
        val t = RellCodeTester(tstCtx)
        t.def(def)
        t.chk("""struct<data>.from_gtv(gtv.from_json('[123,"abc"]'))""", "struct<data>[x=int[123],y=text[abc]]")
        t.chk("""struct<data>.from_gtv_pretty(gtv.from_json('{"x":123,"y":"abc"}'))""", "struct<data>[x=int[123],y=text[abc]]")
        t.chk("struct<data>.from_bytes(x'a50e300ca30302017ba2050c03616263')", "struct<data>[x=int[123],y=text[abc]]")
        t.chk("struct<data>.bad_name()", "ct_err:unknown_member:[struct<data>]:bad_name")
    }

    @Test fun testSystemEntity() {
        tstCtx.useSql = true
        tst.insert(LibBlockTransactionTest.BLOCK_INSERTS_0)

        chk("struct<block>()", "ct_err:attr_missing:[struct<block>]:block_height,block_rid,timestamp")
        chk("struct<block>(block_height = 123, block_rid = x'beef', timestamp = 456)",
                "struct<block>[block_height=int[123],block_rid=byte_array[beef],timestamp=int[456]]")

        chk("struct<transaction>()", "ct_err:attr_missing:[struct<transaction>]:tx_rid,tx_hash,tx_data,block")
        chk("struct<transaction>(block = block@{.block_height==10}, tx_data = x'dead', tx_hash = x'beef', tx_rid = x'cafe')",
                "struct<transaction>[tx_rid=byte_array[cafe],tx_hash=byte_array[beef],tx_data=byte_array[dead],block=block[710]]")
    }

    @Test fun testMirrorStructAtStructAttribute() {
        chkStructAttr("user", "operation op(d: data) {}", "0", "int[0]")
        chkStructAttr("user", "query w(d: data) = 0;", "0", "int[0]")
        chkStructAttr("user", "query w(): data? = null;", "0", "int[0]")
        chkStructAttr("user", "", "data(a = null)", "data[a=null]")

        chkStructAttr("block", "operation op(d: data) {}", "0", "int[0]")
        chkStructAttr("block", "query w(d: data) = 0;", "0", "int[0]")
        chkStructAttr("block", "query w(): data? = null;", "0", "int[0]")
        chkStructAttr("block", "", "data(a = null)", "data[a=null]")

        chkStructAttr("transaction", "operation op(d: data) {}", "0", "int[0]")
        chkStructAttr("transaction", "query w(d: data) = 0;", "0", "int[0]")
        chkStructAttr("transaction", "query w(): data? = null;", "0", "int[0]")
        chkStructAttr("transaction", "", "data(a = null)", "data[a=null]")
    }

    private fun chkStructAttr(innerType: String, def: String, query: String, exp: String) {
        val t = RellCodeTester(tstCtx)
        t.gtv = true
        t.def("entity user { name; }")
        t.def("struct data { a: struct<$innerType>?; }")
        t.chkFull("$def query q() = $query;", "q", listOf(), exp)
    }

    @Test fun testToStructEntityRtExpr() {
        initToStructEntity()

        chk("_type_of((user@{}).to_struct())", "text[struct<user>]")
        chk("(user@{}).to_struct()", "struct<user>[name=text[Bob],rating=int[123]]")

        chk("_type_of((user@?{}).to_struct())", "ct_err:expr_mem_null:user?:to_struct")
        chk("_type_of((user@?{})?.to_struct())", "text[struct<user>?]")
        chk("(user@?{'Bob'}).to_struct()", "ct_err:expr_mem_null:user?:to_struct")
        chk("(user@?{'Bob'})?.to_struct()", "struct<user>[name=text[Bob],rating=int[123]]")
        chk("(user@?{'Alice'})?.to_struct()", "null")

        chk("(user@{}).to_struct(123)", "ct_err:expr_call_badargs:[rell.entity_ext(user).to_struct]:[integer]")
        chk("(user@{}).to_error()", "ct_err:unknown_member:[user]:to_error")
    }

    @Test fun testToStructEntityDbExpr() {
        initToStructEntity()

        chk("_type_of(user@{} (user.to_struct()))", "text[struct<user>]")

        chk("user@{} (user.to_struct())", "struct<user>[name=text[Bob],rating=int[123]]")
        chk("(a:user)@{} (a.to_struct())", "struct<user>[name=text[Bob],rating=int[123]]")
        chk("user@{} (.to_struct())", "struct<user>[name=text[Bob],rating=int[123]]")

        chk("user@{} (.name, user.to_struct(), .rating)",
                "(name=text[Bob],struct<user>[name=text[Bob],rating=int[123]],rating=int[123])")

        chk("user@{} (s = user.to_struct())", "(s=struct<user>[name=text[Bob],rating=int[123]])")

        chk("user@{} (user.to_struct().name)", "text[Bob]")
        chk("user@{} ('' + user.to_struct())", "ct_err:expr_sqlnotallowed")
        chk("user@{} (user.to_struct() == struct<user>('Bob',123))", "ct_err:expr_sqlnotallowed")

        chk("user @ { user.to_struct() }", "ct_err:at_where:type:0:[boolean]:[struct<user>]")
        chk("user @ { user.to_struct() == struct<user>('Bob',123) }", "ct_err:expr_sqlnotallowed")
        chk("user @ { user.to_struct().name == 'Bob' }", "ct_err:expr_sqlnotallowed")

        chk("user@{} (user.to_struct(123))", "ct_err:expr_call_badargs:[rell.entity_ext(user).to_struct]:[integer]")
        chk("user@{} (user.to_error())", "ct_err:unknown_member:[user]:to_error")
    }

    @Test fun testToStructEntityDbExprMultiple() {
        initToStructEntity()

        val code = """
            (u:user, a:admin, c:company)
            @{}
            ( _ = a.name, u.to_struct(), _ = c.name, a.to_struct(), _ = u.name, c.to_struct(), _ = a.root )
        """

        val exp = listOf(
                "text[Alice]",
                "struct<user>[name=text[Bob],rating=int[123]]",
                "text[ChromaWay]",
                "struct<admin>[name=text[Alice],root=boolean[true]]",
                "text[Bob]",
                "struct<company>[name=text[ChromaWay],city=text[Stockholm]]",
                "boolean[true]"
        ).joinToString(",")

        chk(code, "($exp)")
    }

    @Test fun testToStructEntityDbExprAnnotations() {
        initToStructEntity()

        chk("user@{} ( .name, @omit user.to_struct() )", "text[Bob]")
        chk("user@{} ( @sort user.to_struct() )", "ct_err:expr:at:nodb:sort")
        chk("user@{} ( @sort_desc user.to_struct() )", "ct_err:expr:at:nodb:sort")

        val errAggr = "ct_err:[expr:at:nodb:aggregate]"
        chk("user@{} ( @group user.to_struct() )", "ct_err:expr:at:nodb:group")
        chk("user@{} ( @min user.to_struct() )", "$errAggr[at:what:aggr:bad_type:MIN:struct<user>]")
        chk("user@{} ( @max user.to_struct() )", "$errAggr[at:what:aggr:bad_type:MAX:struct<user>]")
        chk("user@{} ( @sum user.to_struct() )", "$errAggr[at:what:aggr:bad_type:SUM:struct<user>]")
        chk("user@{} ( @group .name, @min user.to_struct() )", "$errAggr[at:what:aggr:bad_type:MIN:struct<user>]")
        chk("user@{} ( @group .name, @max user.to_struct() )", "$errAggr[at:what:aggr:bad_type:MAX:struct<user>]")
        chk("user@{} ( @group .name, @sum user.to_struct() )", "$errAggr[at:what:aggr:bad_type:SUM:struct<user>]")
    }

    @Test fun testToStructEntityCollectionAt() {
        initToStructEntity()
        def("function users() = user @* {};")

        chk("users() @ {} ( $.to_struct() )", "struct<user>[name=text[Bob],rating=int[123]]")
        chk("users() @ {} ( $.to_struct().name )", "text[Bob]")
        chk("users() @ {} ( $.to_struct() == struct<user>('Bob',123) )", "boolean[true]")
        chk("users() @ {} ( $.to_struct() == struct<user>('Alice',456) )", "boolean[false]")
        chk("users() @? { $.to_struct().name == 'Bob' }", "user[33]")
        chk("users() @? { $.to_struct().name == 'Alice' }", "null")
        chk("users() @? { $.to_struct() == struct<user>('Bob',123) }", "user[33]")
        chk("users() @? { $.to_struct() == struct<user>('Alice',456) }", "null")

        chk("users() @* {} ( @omit @sort $.to_struct(), $.name )", "ct_err:at:expr:sort:type:struct<user>")
        chk("users() @* {} ( @omit @sort_desc $.to_struct(), $.name )", "ct_err:at:expr:sort:type:struct<user>")
        chk("users() @* {} ( @min $.to_struct() )", "ct_err:at:what:aggr:bad_type:MIN:struct<user>")
        chk("users() @* {} ( @max $.to_struct() )", "ct_err:at:what:aggr:bad_type:MAX:struct<user>")
        chk("users() @* {} ( @sum $.to_struct() )", "ct_err:at:what:aggr:bad_type:SUM:struct<user>")
    }

    @Test fun testToStructEntityQueryCount() {
        tstCtx.useSql = true
        def("entity company { name; city: text; }")
        def("entity user { name; company; }")
        insert("c0.company", "name,city", "55,'ChromaWay','Stockholm'")
        insert("c0.user", "name,company", "33,'Bob',55")

        chkSqlCtr(0)
        chk("user@{} (user.to_struct())", "struct<user>[name=text[Bob],company=company[55]]")
        chkSqlCtr(1)

        chk("user@{} (user.company.to_struct())", "struct<company>[name=text[ChromaWay],city=text[Stockholm]]")
        chkSqlCtr(1)

        chkEx("{ val u = user@{}; return u.to_struct(); }", "struct<user>[name=text[Bob],company=company[55]]")
        chkSqlCtr(2)

        chkEx("{ val u = user@{}; return u.company.to_struct(); }", "struct<company>[name=text[ChromaWay],city=text[Stockholm]]")
        chkSqlCtr(3) //TODO shall be 2, not 3
    }

    private fun initToStructEntity() {
        tstCtx.useSql = true
        def("entity user { name; rating: integer; }")
        def("entity admin { name; root: boolean; }")
        def("entity company { name; city: text; }")
        insert("c0.user", "name,rating", "33,'Bob',123")
        insert("c0.admin", "name,root", "44,'Alice',true")
        insert("c0.company", "name,city", "55,'ChromaWay','Stockholm'")
    }

    @Test fun testToStructObject() {
        initToStructEntity()
        def("object state { x: integer = 123; y: text = 'abc'; }")

        chk("_type_of(state.to_struct())", "text[struct<state>]")
        chk("state.to_struct()", "struct<state>[x=int[123],y=text[abc]]")

        chk("user @ {} ( state.to_struct() )", "struct<state>[x=int[123],y=text[abc]]")
        chk("((user@*{})) @ {} ( state.to_struct() )", "struct<state>[x=int[123],y=text[abc]]")

        chk("_type_of(state.to_struct(123))", "ct_err:expr_call_badargs:[rell.object_ext(state).to_struct]:[integer]")
        chk("_type_of(state.to_error())", "ct_err:unknown_member:[state]:to_error")
    }

    @Test fun testCreateEntityFromStruct() {
        initToStructEntity()
        tst.strictToString = false

        val expr = "struct<user>('Alice',456)"
        chk("user @* {} ( $, _=.name, _=.rating )", "[(user[33],Bob,123)]")
        chkOp("val s = $expr; print(create user(s));")
        chkOut("user[1]")
        chk("user @* {} ( $, _=.name, _=.rating )", "[(user[1],Alice,456), (user[33],Bob,123)]")

        chkOp("val s: struct<user>? = _nullable($expr); create user(s);",
            "ct_err:[attr_missing:[user]:name,rating][attr_implic_unknown:0:struct<user>?]")
        chkOp("val s = $expr; create user(s, 'Alice');",
            "ct_err:[attr_missing:[user]:rating][attr_implic_unknown:0:struct<user>]")
        chkOp("val s = $expr; create user('Alice', s);",
            "ct_err:[attr_missing:[user]:rating][attr_implic_unknown:1:struct<user>]")
        chkOp("val s = $expr; create user(s, name = 'Alice');",
            "ct_err:[attr_missing:[user]:rating][attr_implic_unknown:0:struct<user>]")
        chkOp("val s = $expr; create user(name = 'Alice', s);",
            "ct_err:[attr_missing:[user]:rating][attr_implic_unknown:1:struct<user>]")
        chkOp("val s = $expr; create user(s = s);", "ct_err:attr_unknown_name:s")
        chkOp("val s = $expr; create admin(s);",
            "ct_err:[attr_missing:[admin]:name,root][attr_implic_unknown:0:struct<user>]")
    }

    @Test fun testMutableBasic() {
        initMutable()
        chkMutableBasic("my_entity")
        chkMutableBasic("my_object")
    }

    private fun initMutable() {
        tstCtx.useSql = true
        def("entity my_entity { x: text = 'abc'; y: integer = 123; }")
        def("object my_object { x: text = 'abc'; y: integer = 123; }")
        insert("c0.my_entity", "x,y", "0,'abc',123")
    }

    private fun chkMutableBasic(type: String) {
        chkType("struct<mutable $type>", "OK")
        chk("_type_of(struct<mutable $type>())", "text[struct<mutable $type>]")

        chk("struct<mutable $type>()", "struct<mutable $type>[x=text[abc],y=int[123]]")
        chk("struct<mutable $type>('xyz',987)", "struct<mutable $type>[x=text[xyz],y=int[987]]")

        chkEx("{ val s = struct<mutable $type>(); return s; }", "struct<mutable $type>[x=text[abc],y=int[123]]")
        chkEx("{ val s = struct<mutable $type>(); s.x = 'xyz'; return s; }", "struct<mutable $type>[x=text[xyz],y=int[123]]")
        chkEx("{ val s = struct<mutable $type>(); s.y = 987; return s; }", "struct<mutable $type>[x=text[abc],y=int[987]]")
    }

    @Test fun testMutableToStruct() {
        initMutable()
        chkMutableToStruct("my_entity", "(my_entity@{})")
        chkMutableToStruct("my_object", "my_object")
    }

    private fun chkMutableToStruct(type: String, expr: String) {
        chk("_type_of($expr.to_struct())", "text[struct<$type>]")
        chk("_type_of($expr.to_mutable_struct())", "text[struct<mutable $type>]")
        chk("_type_of($expr.to_struct().to_immutable())", "ct_err:unknown_member:[struct<$type>]:to_immutable")
        chk("_type_of($expr.to_struct().to_mutable())", "text[struct<mutable $type>]")
        chk("_type_of($expr.to_mutable_struct().to_immutable())", "text[struct<$type>]")
        chk("_type_of($expr.to_mutable_struct().to_mutable())", "ct_err:unknown_member:[struct<mutable $type>]:to_mutable")

        chk("$expr.to_struct()", "struct<$type>[x=text[abc],y=int[123]]")
        chk("$expr.to_mutable_struct()", "struct<mutable $type>[x=text[abc],y=int[123]]")
        chk("$expr.to_struct().to_mutable()", "struct<mutable $type>[x=text[abc],y=int[123]]")
        chk("$expr.to_mutable_struct().to_immutable()", "struct<$type>[x=text[abc],y=int[123]]")
        chk("$expr.to_struct().to_immutable()", "ct_err:unknown_member:[struct<$type>]:to_immutable")
        chk("$expr.to_mutable_struct().to_mutable()", "ct_err:unknown_member:[struct<mutable $type>]:to_mutable")

        chkEx("{ val s = $expr.to_struct(); return s.to_mutable() === s; }",
                "ct_err:binop_operand_type:===:[struct<mutable $type>]:[struct<$type>]")
        chkEx("{ val s = $expr.to_mutable_struct(); return s.to_immutable() === s; }",
                "ct_err:binop_operand_type:===:[struct<$type>]:[struct<mutable $type>]")
    }

    @Test fun testMutableToStructDbExpr() {
        initMutable()

        chk("_type_of((e: my_entity) @ {} ( e.to_struct() ))", "text[struct<my_entity>]")
        chk("_type_of((e: my_entity) @ {} ( e.to_mutable_struct() ))", "text[struct<mutable my_entity>]")
        chk("(e: my_entity) @ {} ( e.to_struct() )", "struct<my_entity>[x=text[abc],y=int[123]]")
        chk("(e: my_entity) @ {} ( e.to_mutable_struct() )", "struct<mutable my_entity>[x=text[abc],y=int[123]]")

        chkEx("{ val s = (e: my_entity) @{} ( e.to_struct() ); s.x = 'xyz'; return s; }",
            "ct_err:attr_not_mutable:struct<my_entity>.x")

        chkEx("{ val s = (e: my_entity) @{} ( e.to_mutable_struct() ); s.x = 'xyz'; return s; }",
                "struct<mutable my_entity>[x=text[xyz],y=int[123]]")
    }

    @Test fun testMutableCreate() {
        initMutable()
        tst.strictToString = false
        chk("my_entity @* {} ( $, _=.x, _=.y )", "[(my_entity[0],abc,123)]")
        chkOp("val s = struct<mutable my_entity>('xyz',987); print(create my_entity(s));")
        chkOut("my_entity[1]")
        chk("my_entity @* {} ( $, _=.x, _=.y )", "[(my_entity[0],abc,123), (my_entity[1],xyz,987)]")
    }

    @Test fun testLogEntity() {
        tstCtx.useSql = true
        def("@log entity user { name; value: integer = 123; }")

        chk("struct<user>('Bob')", "ct_err:attr_missing:[struct<user>]:transaction")
        chk("struct<user>('Bob', transaction @ {})", "rt_err:at:wrong_count:0")
        chk("struct<user>(transaction @ {})", "ct_err:attr_missing:[struct<user>]:name")
    }

    @Test fun testLogEntityGtvCompatibility() {
        tst.gtv = true
        def("@log entity user { name; value: integer = 123; }")
        chkCompile("query qq(): struct<user> = user @ {} (user.to_struct());", "OK")
    }

    private fun chkType(typeCode: String, expected: String) {
        val code = "function f(x: $typeCode) {}"
        chkCompile(code, expected)
    }

    @Test fun testCopyMirrorStructEntity() {
        def("entity user { name; rating: integer; }")

        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(); return a === b; }", "boolean[false]")
        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(); return b; }",
            "struct<user>[name=text[Alice],rating=int[100]]")

        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(name = 'Bob'); return b; }",
            "struct<user>[name=text[Bob],rating=int[100]]")
        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(rating = 200); return b; }",
            "struct<user>[name=text[Alice],rating=int[200]]")
        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(name = 'Bob', rating = 200); return b; }",
            "struct<user>[name=text[Bob],rating=int[200]]")
    }

    @Test fun testCopyMirrorStructObject() {
        def("object state { x: integer = 123; y: text = 'abc'; }")

        chkEx("{ val a = struct<state>(); val b = a.copy(); return b; }",
            "struct<state>[x=int[123],y=text[abc]]")
        chkEx("{ val a = struct<state>(x = 456); val b = a.copy(y = 'xyz'); return b; }",
            "struct<state>[x=int[456],y=text[xyz]]")
        chkEx("{ val a = struct<state>(y = 'hello'); val b = a.copy(x = 999); return b; }",
            "struct<state>[x=int[999],y=text[hello]]")
    }

    @Test fun testCopyMirrorStructUnnamedArgs() {
        def("entity user { name; }")
        chkEx("{ val a = struct<user>('Alice'); val b = a.copy('Bob'); return b; }",
            "ct_err:copy:unnamed_arg")
    }

    @Test fun testCopyMirrorStructWrongType() {
        def("entity user { name; rating: integer; }")

        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(name = 123); return b; }",
            """ct_err:[expr_call_argtype:[rell.struct_ext(struct<user>).copy]:0:name:text:integer]
               [expr_call_argtype:[copy]:name:text:integer]""")

        chkEx("{ val a = struct<user>('Alice', 100); val b = a.copy(rating = 'wrong'); return b; }",
            """ct_err:[expr_call_argtype:[rell.struct_ext(struct<user>).copy]:1:rating:integer:text]
               [expr_call_argtype:[copy]:rating:integer:text]""")
    }

    @Test fun testCopyMirrorStructUnknownArg() {
        def("entity user { name; }")
        chkEx("{ val a = struct<user>('Alice'); val b = a.copy(age = 25); return b; }",
            """ct_err:[expr:call:unknown_named_arg:[rell.struct_ext(struct<user>).copy]:age]
               [expr:call:unknown_named_arg:age]""")
    }

    @Test fun testCopyMirrorStructDefaultValues() {
        def("entity data { x: integer = 100; y: text = 'default'; }")
        chkEx("{ val a = struct<data>(); val b = a.copy(x = 200); return b; }",
            "struct<data>[x=int[200],y=text[default]]")
        chkEx("{ val a = struct<data>(y = 'custom'); val b = a.copy(); return b; }",
            "struct<data>[x=int[100],y=text[custom]]")
    }
}
