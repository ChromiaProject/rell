/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class ObjectTest: BaseRellTest(useSql = true) {
    @Test fun testInitialization() {
        def("object foo { n: integer = 123; s: text = 'Hello'; }")
        chkData("foo(0,123,Hello)")
    }

    @Test fun testRead() {
        def("object foo { n: integer = 123; s: text = 'Hello'; }")
        chk("foo.n", "int[123]")
        chk("foo.s", "text[Hello]")
    }

    @Test fun testReadUnderAt() {
        def("object foo { n: integer = 123; s: text = 'Hello'; }")
        def("entity user {name;}")
        chkOp("create user('Bob');")
        chk("user @{} ( foo.n )", "int[123]")
        chk("user @{} ( foo.s )", "text[Hello]")
    }

    @Test fun testAttributes() {
        chkCompile("object foo { x: integer; }", "ct_err:object_attr_novalue:foo:x")
        chkCompile("object foo { x: integer = 123; s: text; }", "ct_err:object_attr_novalue:foo:s")
        chkCompile("object foo { n = 123; }", "ct_err:unknown_name:n")

        def("object foo{}")
        chk("'' + foo", "text[foo]")
    }

    @Test fun testBadType() {
        chkCompile("object foo { x: list<integer> = list<integer>(); }", "ct_err:entity_attr_type:x:list<integer>")
        chkCompile("object foo { x: set<integer> = set<integer>(); }", "ct_err:entity_attr_type:x:set<integer>")
        chkCompile("object foo { x: map<integer,text> = map<integer, text>(); }", "ct_err:entity_attr_type:x:map<integer,text>")
        chkCompile("object foo { x: (integer,text) = (123,'Hello'); }", "ct_err:entity_attr_type:x:(integer,text)")
        chkCompile("object foo { x: integer? = 123; }", "ct_err:entity_attr_type:x:integer?")
    }

    @Test fun testKeyIndex() {
        chkCompile("object foo { x: integer = 123; key x; }", "ct_err:syntax")
        chkCompile("object foo { x: integer = 123; index x; }", "ct_err:syntax")
    }

    @Test fun testUseAsType() {
        def("object foo { x: integer = 123; }")
        chkCompile("function g(f: foo){}", "ct_err:wrong_name:type:object:foo")
        chkCompile("function g(): foo {}", "ct_err:[fun_noreturn:g][wrong_name:type:object:foo]")
        chkCompile("function g() { var f: foo; }", "ct_err:wrong_name:type:object:foo")
        chkCompile("entity bar { f: foo; }", "ct_err:wrong_name:type:object:foo")
        chkCompile("function g() { var l: list<foo>; }", "ct_err:wrong_name:type:object:foo")
        chkCompile("function g() { var l: set<foo>; }", "ct_err:wrong_name:type:object:foo")
        chkCompile("function g() { var l: map<integer, foo>; }", "ct_err:wrong_name:type:object:foo")
        chkCompile("function g() { var l: map<foo, integer>; }", "ct_err:wrong_name:type:object:foo")
        chkCompile("struct bar { foo; }", "ct_err:wrong_name:type:object:foo")
    }

    @Test fun testCreateDelete() {
        def("object foo { x: integer = 123; }")
        chkOp("create foo(x=123);", "ct_err:wrong_name:entity:object:foo")
        chkOp("delete foo @* {};", "ct_err:wrong_name:entity:object:foo")
        chkOp("delete foo;", "ct_err:stmt_delete_obj:foo")
    }

    @Test fun testComplexExpressions() {
        def("object foo { p: integer = sq(5); }")
        def("object bar { q: integer = sq(foo.p); }")
        def("function sq(x: integer): integer = x * x;")
        chk("foo.p", "int[25]")
        chk("bar.q", "int[625]")
    }

    @Test fun testObjectValue() {
        def("object foo { x: integer = 123; }")

        chk("'' + foo", "text[foo]")
        chk("_type_of(foo)", "text[foo]")
        chk("abs(foo)", "ct_err:expr_call_badargs:[abs]:[foo]")
        chk("foo", "foo")

        chk("foo == foo", "ct_err:binop_operand_type:==:[foo]:[foo]")
        chk("foo != foo", "ct_err:binop_operand_type:!=:[foo]:[foo]")
        chk("foo === foo", "ct_err:binop_operand_type:===:[foo]:[foo]")
        chk("foo !== foo", "ct_err:binop_operand_type:!==:[foo]:[foo]")
        chk("foo < foo", "ct_err:binop_operand_type:<:[foo]:[foo]")
        chk("foo <= foo", "ct_err:binop_operand_type:<=:[foo]:[foo]")
        chk("foo + foo", "ct_err:binop_operand_type:+:[foo]:[foo]")
        chk("not foo", "ct_err:unop_operand_type:not:[foo]")

        chkEx("{ foo = 123; return 456; }", "ct_err:expr_bad_dst")
        chkEx("{ foo += 123; return 456; }", "ct_err:expr_bad_dst")

        chk("foo?.x", "ct_err:expr_safemem_type:[foo]:x")
    }

    @Test fun testGtv() {
        def("object foo { x: integer = 123; }")
        tst.gtv = true
        chk("foo", "ct_err:result_nogtv:q:foo")
        chkFull("query q() { return foo; }", "ct_err:result_nogtv:q:foo")
    }

    @Test fun testForwardReference() {
        def("function f(): integer = foo.x;")
        def("query q() = foo.x;")
        def("object foo { x: integer = 123; }")
        chkFull("", "int[123]")
        chkFnFull("", "int[123]")
    }

    @Test fun testForwardReferenceErr() {
        chkCompile("object foo { x: integer = 123; y: integer = x; }", "ct_err:unknown_name:x")
        chkCompile("object foo { x: integer = bar.y; } object bar { y: integer = 123; }", "OK")

        def("object foo { x: integer = 123; y: integer = foo.x; }")
        tst.chkInit("rt_err:obj:init_cycle:foo,foo")
    }

    @Test fun testForwardReferenceRt() {
        def("object foo { x: integer = g(); }")
        def("object bar { y: integer = 123; }")
        def("function g(): integer = bar.y;")
        chk("foo.x", "int[123]")
        chk("bar.y", "int[123]")
    }

    @Test fun testCycle() {
        def("""
            object o1 { x: integer = f3(); }
            object o2 { x: integer = f4(); }
            object o3 { x: integer = f2(); }
            object o4 { x: integer = f1(); }
            function f1(): integer = o1.x;
            function f2(): integer = o2.x;
            function f3(): integer = o3.x;
            function f4(): integer = o4.x;
        """)
        tst.chkInit("rt_err:obj:init_cycle:o1,o3,o2,o4,o1")
    }

    @Test fun testInterModuleDependencies() {
        file("a1.rell", "module; import b; object o1 { x: integer = b.f3() + 1; }")
        file("a2.rell", "module; import b; object o2 { x: integer = b.f4() + 10; }")
        file("a3.rell", "module; import b; object o3 { x: integer = b.f2() + 100; }")
        file("a4.rell", "module; import b; object o4 { x: integer = 123; }")
        file("b.rell", """
            module;
            import a1; import a2; import a3; import a4;
            function f1(): integer = a1.o1.x;
            function f2(): integer = a2.o2.x;
            function f3(): integer = a3.o3.x;
            function f4(): integer = a4.o4.x;
        """)
        def("import a1; import a2; import a3; import a4;")

        chk("a1.o1.x", "int[234]")
        chk("a2.o2.x", "int[133]")
        chk("a3.o3.x", "int[233]")
        chk("a4.o4.x", "int[123]")
    }

    @Test fun testInterModuleCycle() {
        file("a1.rell", "module; import b; object o1 { x: integer = b.f3(); }")
        file("a2.rell", "module; import b; object o2 { x: integer = b.f4(); }")
        file("a3.rell", "module; import b; object o3 { x: integer = b.f2(); }")
        file("a4.rell", "module; import b; object o4 { x: integer = b.f1(); }")
        file("b.rell", """
            module;
            import a1; import a2; import a3; import a4;
            function f1(): integer = a1.o1.x;
            function f2(): integer = a2.o2.x;
            function f3(): integer = a3.o3.x;
            function f4(): integer = a4.o4.x;
        """)
        def("import a1; import a2; import a3; import a4;")
        tst.chkInit("rt_err:obj:init_cycle:a1:o1,a3:o3,a2:o2,a4:o4,a1:o1")
    }

    @Test fun testUpdate() {
        def("object foo { mutable x: integer = 123; mutable s: text = 'Hello'; c: integer = 999; }")

        chkData("foo(0,123,Hello,999)")
        chk("(foo.x,foo.s,foo.c)", "(int[123],text[Hello],int[999])")

        chkOp("update foo (x += 456, s += 'World'); print((foo.x,foo.s,foo.c));")
        chkData("foo(0,579,HelloWorld,999)")
        chk("(foo.x,foo.s,foo.c)", "(int[579],text[HelloWorld],int[999])")

        chkOp("update foo (c = 111);", "ct_err:attr_not_mutable:foo.c")
        chkData("foo(0,579,HelloWorld,999)")
        chk("(foo.x,foo.s,foo.c)", "(int[579],text[HelloWorld],int[999])")

        chkOp("update foo ( 555, 'Bye' );")
        chkData("foo(0,555,Bye,999)")
        chk("(foo.x,foo.s,foo.c)", "(int[555],text[Bye],int[999])")

        chkOp("update foo ( 33 );")
        chkData("foo(0,33,Bye,999)")
        chk("(foo.x,foo.s,foo.c)", "(int[33],text[Bye],int[999])")

        chkOp("update foo ( 'Tschuss' );")
        chkData("foo(0,33,Tschuss,999)")
        chk("(foo.x,foo.s,foo.c)", "(int[33],text[Tschuss],int[999])")

        chkEx("{ update foo ( 'Tschuss' ); return 0; }", "ct_err:no_db_update:query")
    }

    @Test fun testUpdateMemory() {
        def("object foo { mutable x: integer = 123; }")

        chkData("foo(0,123)")

        chkOp("""
            print(foo.x);
            update foo (x = 456);
            print(foo.x);
        """)

        chkData("foo(0,456)")
        chk("foo.x", "int[456]")
        chkOut("123", "456")
    }

    @Test fun testInitSideEffectsDirectCreateAllowed() {
        def("entity data { v: integer; }")
        def("object state { mutable d: data = create data(123); }")
        chk("state.d", "data[1]")
        chk("state.d.v", "int[123]")
    }

    @Test fun testInitSideEffectsDirectCreateUnallowed() {
        tst.allowDbModificationsInObjectExprs = false
        def("entity data { v: integer; }")
        chkCompile("object state { mutable d: data = create data(123); }", "ct_err:no_db_update:object:expr")
    }

    @Test fun testInitSideEffectsIndirectCreateAllowed() {
        def("entity data { mutable v: integer; }")
        def("function f(v: integer): integer { create data(v); return v*v; }")
        def("object state { mutable d: integer = f(123); }")
        chk("state.d", "int[15129]")
        chk("data @* {}", "list<data>[data[1]]")
    }

    @Test fun testInitSideEffectsIndirectCreateUnallowed() {
        tst.allowDbModificationsInObjectExprs = false
        def("entity data { mutable v: integer; }")
        def("function f(v: integer): integer { create data(v); return v*v; }")
        def("object state { mutable d: integer = f(123); }")
        tst.chkInit("rt_err:no_db_update:def")
    }

    @Test fun testInitSideEffectsIndirectUpdateAllowed() {
        def("object helper { mutable v: integer = 123; }")
        def("function f(v: integer): integer { update helper ( .v += v ); return v*v; }")
        def("object state { mutable d: integer = f(456); }")
        chk("helper.v", "int[579]")
        chk("state.d", "int[207936]")
    }

    @Test fun testInitSideEffectsIndirectUpdateUnallowed() {
        tst.allowDbModificationsInObjectExprs = false
        def("entity data { mutable v: integer; }")
        def("function f(v: integer): integer { update data @*{} ( .v += v ); return v*v; }")
        def("object state { mutable d: integer = f(123); }")
        tst.chkInit("rt_err:no_db_update:def")
    }

    @Test fun testInitSideEffectsIndirectDeleteAllowed() {
        def("entity data { mutable v: integer; }")
        def("function f(v: integer): integer { delete data @*{}; return v*v; }")
        def("object state { mutable d: integer = f(123); }")
        insert("c0.data", "v", "1,456")
        chk("data @* {}", "list<data>[data[1]]")
    }

    @Test fun testInitSideEffectsIndirectDeleteUnallowed() {
        tst.allowDbModificationsInObjectExprs = false
        def("entity data { mutable v: integer; }")
        def("function f(v: integer): integer { delete data @*{}; return v*v; }")
        def("object state { mutable d: integer = f(123); }")
        tst.chkInit("rt_err:no_db_update:def")
    }

    @Test fun testNameResolution() {
        def("object foo { x: integer = 123; }")
        def("entity user { name; }")
        insert("c0.user", "name", "1,'Bob'")

        // Object vs. local: error.
        chkEx("{ val foo = (s = 'Hello'); return foo.s; }", "text[Hello]")
        chkEx("{ val foo = (s = 'Hello'); return foo.x; }", "ct_err:unknown_member:[(s:text)]:x")
        chkEx("{ val foo = (x = 456); return foo.x; }", "int[456]")

        // Object vs. alias: error.
        chk("(foo: user) @* {}", "list<user>[user[1]]")
        chk("(foo: user) @* { foo.name == 'Bob' }", "list<user>[user[1]]")
    }

    @Test fun testMultipleRecords() {
        def("object foo { x: integer = 123; }")
        insert("c0.foo", "x", "1,123")
        insert("c0.foo", "x", "2,456")
        insert("c0.foo", "x", "3,789")
        chk("foo.x", "rt_err:obj_multirec:foo:4")
    }

    @Test fun testUpdateShortSyntax() {
        def("object foo { mutable x: integer = 100; y: integer = 250; }")
        chkData("foo(0,100,250)")

        chkOp("foo.x = 50;")
        chkData("foo(0,50,250)")

        chkOp("foo.x += 33;")
        chkData("foo(0,83,250)")

        chkOp("foo.x *= 55;")
        chkData("foo(0,4565,250)")

        chkOp("foo.y = 10;", "ct_err:attr_not_mutable:foo.y")
        chkData("foo(0,4565,250)")

        chkOp("foo.y += 10;", "ct_err:attr_not_mutable:foo.y")
        chkData("foo(0,4565,250)")

        chkEx("{ foo.x = 50; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ foo.x += 50; return 0; }", "ct_err:no_db_update:query")
    }

    @Test fun testAttributeValueTypePromotion() {
        def("object state { mutable x: decimal = 123; }")
        chk("state.x", "dec[123]")
    }

    @Test fun testAttributeReadSql() {
        tstCtx.useSql = true
        def("object state { mutable value: integer = 123; }")
        chkSql()
        chk("state.value", "int[123]")
        chkSql("""SELECT A00."value" FROM "c0.state" A00""")
    }

    @Test fun testToStructSql() {
        tstCtx.useSql = true
        def("object state { mutable value: integer = 123; }")
        chkSql()
        chk("state.to_struct()", "struct<state>[value=int[123]]")
        chkSql("""SELECT A00."value" FROM "c0.state" A00""")
    }
}
