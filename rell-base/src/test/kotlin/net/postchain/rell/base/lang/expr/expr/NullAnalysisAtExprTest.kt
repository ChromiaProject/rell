/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.iff
import org.junit.Test

class NullAnalysisAtExprTest: BaseRellTest() {
    @Test fun testDbWhere() {
        initEntity()

        val from = "(d: data, @outer r: ref @* { .data == d })"
        chk("$from @* { r.x > 100 } ( d )", "ct_err:expr_mem_null:ref?:x")
        chk("$from @* { r == null or r.x > 100 } ( d )", "[data[10]]")
        chk("$from @* { r != null and r.x > 100 } ( d )", "[data[10]]")
        chk("$from @* { r != null, r.x > 100 } ( d )", "[data[10]]")
        chk("$from @* { r.x > 100, r != null } ( d )", "ct_err:expr_mem_null:ref?:x")

        chk("$from @* {} ( r.x )", "ct_err:expr_mem_null:ref?:x")
        chk("$from @* { r != null } ( r.x )", "[123]")
        chk("$from @* { r?.x != null } ( r.x )", "[123]")
        chk("$from @* { r != null or d.v < 0 } ( r.x )", "ct_err:expr_mem_null:ref?:x")
        chk("$from @* { .x != null } ( r.x )", "[123]")

        chk("$from @* {} ( _=.x, _type_of(.x) )", "[(123,integer?)]")
        chk("$from @* { r != null } ( _=.x, _type_of(.x) )", "[(123,integer)]")
        chk("$from @* { .x != null } ( _=.x, _type_of(.x) )", "[(123,integer)]")
        chk("$from @* { r != null or d.v < 0 } ( _=.x, _type_of(.x) )", "[(123,integer?)]")
    }

    @Test fun testDbWhereMore() {
        initEntity()

        val from = "(d: data, @outer r1: ref @* {}, @outer r2: ref @* {}, @outer r3: ref @* {})"
        val err = "expr_mem_null:ref?:x"

        chk("$from @* {} ( _=r1.x, _=r2.x, _=r3.x )", "ct_err:[$err][$err][$err]")
        chk("$from @* { r1 != null } ( _=r1.x, _=r2.x, _=r3.x )", "ct_err:[$err][$err]")
        chk("$from @* { r3 != null } ( _=r1.x, _=r2.x, _=r3.x )", "ct_err:[$err][$err]")
        chk("$from @* { r1 != null, r2 != null } ( _=r1.x, _=r2.x, _=r3.x )", "ct_err:$err")
        chk("$from @* { r1 != null, r2 != null, r3 != null } ( _=r1.x, _=r2.x, _=r3.x )", "[(123,123,123)]")
    }

    @Test fun testDbWhat() {
        initEntity()

        val from = "(d: data, @outer r: ref @* { .data == d })"
        chk("$from @{} ( d, r )", "(data[10],ref[20])")
        chk("$from @{} ( r.x )", "ct_err:expr_mem_null:ref?:x")
        chk("$from @{} ( r?.x )", "123")

        chk("$from @{} ( _=.x, _=.y )", "(123,456)")
        chk("$from @{} ( _type_of(.x), _type_of(.y) )", "(integer?,integer?)")
        chk("$from @{} ( if (.x == null) '' else _type_of(.x) )", "integer")
        chk("$from @{} ( if (.y == null) '' else _type_of(.y) )", "integer")
        chk("$from @{} ( if (.x == null) -1 else (.x + 7) )", "130")
        chk("$from @{} ( if (.y == null) -1 else (.y + 4) )", "460")

        chk("$from @{} ( if (r == null) '' else _type_of(.x) )", "integer")
        chk("$from @{} ( if (r == null) '' else _type_of(.y) )", "integer")
        chk("$from @{} ( if (r == null) '' else _type_of(r.x) )", "integer")
        chk("$from @{} ( if (r == null) '' else _type_of(r.y) )", "integer")
        chk("$from @{} ( if (r == null) -1 else r.x )", "123")
        chk("$from @{} ( if (r == null) -1 else r.y )", "456")
        chk("$from @{} ( if (r?.x == null) -1 else r.x )", "123")
        chk("$from @{} ( if (r?.x == null) -1 else r.y )", "456")
        chk("$from @{} ( if (r?.y == null) -1 else r.x )", "123")
        chk("$from @{} ( if (r?.y == null) -1 else r.y )", "456")

        chk("$from @{} ( _type_of(r?.rowid) )", "rowid?")
        chk("$from @{} ( if (r == null) '' else _type_of(r?.rowid) )", "rowid")
    }

    private fun initEntity() {
        tstCtx.useSql = true
        tst.strictToString = false
        def("entity data { v: integer; }")
        def("entity ref { key data; x: integer; mutable y: integer; }")
        insert("c0.data", "v", "10,999")
        insert("c0.ref", "data,x,y", "20,10,123,456")
    }

    @Test fun testColWhere() {
        tst.strictToString = false
        def("struct ref { p: integer?; mutable q: integer?; }")
        def("struct data { x: integer?; mutable y: integer?; ref?; }")
        def("function from(): list<data?> = [data(x = 123, y = 456, ref = ref(p = 321, q = 654))];")

        chk("from() @ {} ( $.x )", "ct_err:expr_mem_null:data?:x")
        chk("from() @ {} ( _type_of($) )", "data?")
        chk("from() @ { $ != null } ( $.x, $.y )", "(x=123,y=456)")

        val what = "_type_of($.x), _type_of($.y), _type_of($.ref)"
        chk("from() @ { $ != null } ( $what )", "(integer?,integer?,ref?)")
        chk("from() @ { $ != null, $.x != null } ( $what )", "(integer,integer?,ref?)")
        chk("from() @ { $ != null, $.x != null, $.y != null } ( $what )", "(integer,integer?,ref?)")
        chk("from() @ { $ != null, $.x != null, $.y != null, $.ref != null } ( $what )", "(integer,integer?,ref)")

        chk("from() @ { $?.ref?.p != null } ( _type_of($) )", "data")
        chk("from() @ { $?.ref?.p != null } ( _type_of($.ref) )", "ref")
        chk("from() @ { $?.ref?.p != null } ( _type_of($.ref.p), _type_of($.ref.q) )", "(integer,integer?)")

        chk("from() @ { $?.ref?.q != null } ( _type_of($) )", "data")
        chk("from() @ { $?.ref?.q != null } ( _type_of($.ref) )", "ref")
        chk("from() @ { $?.ref?.q != null } ( _type_of($.ref.p), _type_of($.ref.q) )", "(integer?,integer?)")
    }

    @Test fun testColAttrName() {
        initFooBar()

        chk("[foo_nz()] @ {} ( _type_of($.x), _type_of($.y) )", "(integer?,integer?)")
        chk("[foo_nz()] @ { $.x != null } ( _type_of($.x), _type_of($.y) )", "(integer,integer?)")
        chk("[foo_nz()] @ { $.y != null } ( _type_of($.x), _type_of($.y) )", "(integer?,integer?)")
        chk("[foo_nz()] @ { $.x != null } ( $.x, $.y )", "(x=123,y=456)")
        chk("[foo_nz()] @ { $.y != null } ( $.x, $.y )", "(x=123,y=456)")
        chk("[foo_nz()] @ { $.x != null, $.y != null } ( $.x, $.y )", "(x=123,y=456)")

        chk("[foo_z()] @ { $ != null } ( _type_of($.x), _type_of($.y) )", "(integer?,integer?)")
        chk("[foo_z()] @ { $ != null } ( $.x, $.y )", "(x=123,y=456)")
        chk("[foo_z()] @ { $?.x != null } ( $.x, $.y )", "(x=123,y=456)")
        chk("[foo_z()] @ { $?.y != null } ( $.x, $.y )", "(x=123,y=456)")
    }

    @Test fun testColWhatType() {
        initFooBar()

        chk("_type_of([foo_z()] @* {})", "list<foo?>")
        chk("_type_of([foo_z()] @* {} ($))", "list<foo?>")
        chk("_type_of([foo_z()] @* { $?? })", "list<foo>")
        chk("_type_of([foo_z()] @* { $?? } ($))", "list<foo>")

        chk("_type_of([foo_z()] @* { $?? } ($.x))", "list<integer?>")
        chk("_type_of([foo_z()] @* { $??, $.x?? } ($.x))", "list<integer>")
        chk("_type_of([foo_z()] @* { $?? }.x)", "list<integer?>")
        chk("[foo_z()] @* { $?? }.x", "[123]")
        chk("_type_of([foo_z()] @* { $??, $.x?? }.x)", "list<integer>")
        chk("[foo_z()] @* { $??, $.x?? }.x", "[123]")
        chk("[foo_z()] @* {}.x", "ct_err:expr_attr_unknown:x")

        chk("_type_of([foo_nz()] @* {} ($.x))", "list<integer?>")
        chk("_type_of([foo_nz()] @* { $.x?? } ($.x))", "list<integer>")
        chk("_type_of([foo_nz()] @* {}.x)", "list<integer?>")
        chk("_type_of([foo_nz()] @* { $.x?? }.x)", "list<integer>")
    }

    @Test fun testVersionControlWhatDefault() {
        initFooBar()
        chkVer("0.14.0") {
            chk("_type_of([foo_z()] @* {})", "list<foo?>")
            chk("_type_of([foo_z()] @* { $ != null })", it.iff("list<foo?>", "list<foo>"))
            chk("_type_of([foo_z()] @* {} ($))", "list<foo?>")
            chk("_type_of([foo_z()] @* { $ != null } ($))", it.iff("list<foo?>", "list<foo>"))
        }
    }

    @Test fun testVersionControlWhatComplex() {
        initFooBar()

        chkVer("0.14.0") {
            val what = "_type_of($), _type_of($?.x), _type_of($?.y)"
            chk("[foo_z()] @ {} ( $what )", "(foo?,integer?,integer?)")
            chk("[foo_z()] @ { $ != null } ( $what )", it.iff("(foo?,integer?,integer?)", "(foo,integer?,integer?)"))
            chk("[foo_z()] @ { $?.x != null } ( $what )", it.iff("(foo?,integer?,integer?)", "(foo,integer,integer?)"))
            chk("[foo_z()] @ { $?.y != null } ( $what )", it.iff("(foo?,integer?,integer?)", "(foo,integer?,integer?)"))
            chk("[foo_z()] @ { $ != null, $.x != null, $.y != null } ( $what )",
                it.iff("ct_err:[expr_mem_null:foo?:x][expr_mem_null:foo?:y]", "(foo,integer,integer?)"))
        }
    }

    @Test fun testVersionControlWhatContextAttr() {
        initFooBar()

        chkVer("0.14.0") {
            chk("_type_of([foo_nz()] @* {}.x)", "list<integer?>")
            chk("_type_of([foo_nz()] @* { .x != null }.x)", it.iff("list<integer?>", "list<integer>"))
            chk("_type_of([foo_nz()] @* { $.x != null }.x)", it.iff("list<integer?>", "list<integer>"))

            chk("_type_of([foo_nz()] @* {} (.x))", "list<integer?>")
            chk("_type_of([foo_nz()] @* { .x != null } (.x))", it.iff("list<integer?>", "list<integer>"))
            chk("_type_of([foo_nz()] @* { $.x != null } (.x))", it.iff("list<integer?>", "list<integer>"))

            chk("_type_of([foo_nz()] @* {} ($.x))", "list<integer?>")
            chk("_type_of([foo_nz()] @* { .x != null } ($.x))", it.iff("list<integer?>", "list<integer>"))
            chk("_type_of([foo_nz()] @* { $.x != null } ($.x))", it.iff("list<integer?>", "list<integer>"))
        }
    }

    @Test fun testVersionControlWhereContextAttr() {
        initFooBar()
        chkVer("0.14.0") {
            chk("[foo_nz()] @* { .x > 0 } ( @sum 1 )", "ct_err:binop_operand_type:>:[integer?]:[integer]")
            chk("[foo_nz()] @* { .x != null, .x > 0 } ( @sum 1 )",
                it.iff("ct_err:binop_operand_type:>:[integer?]:[integer]", "[1]"))
        }
    }

    @Test fun testVersionControlWhereImplicitAttr() {
        initCol()
        chkVer("0.14.0") {
            chkEx("{ val x = 123; return f() @* { x }; }", "ct_err:at_where:var_noattrs:0:x:integer")
            chkEx("{ val x = 123; return f() @* { $ != null, x }; }",
                it.iff("ct_err:at_where:var_noattrs:1:x:integer", "[data{x=123,y=abc}]"))
        }
    }

    private fun initFooBar() {
        tst.strictToString = false
        def("struct foo { x: integer?; mutable y: integer?; bar?; mutable mbar: bar?; }")
        def("struct bar { r: integer?; mutable s: integer?; }")
        def("function get_bar() = bar(r = 321, s = 654);")
        def("function foo_nz(): foo = foo(x = 123, y = 456, bar = get_bar(), mbar = get_bar());")
        def("function foo_z(): foo? = foo_nz();")
        def("function foo_z0(): foo? = null;")
    }

    @Test fun testDefaultWhatCol() {
        initCol()
        chk("_type_of(f() @* {})", "list<data?>")
        chk("f() @* {} (_type_of($))", "[data?]")
        chk("_type_of(f() @* { $?? })", "list<data>")
        chk("f() @* { $?? } (_type_of($))", "[data]")
    }

    @Test fun testDefaultWhatDb() {
        initDb()
        chk("_type_of((a:tada, @outer b:data) @* {})", "list<(a:tada,b:data?)>")
        chk("(a:data, @outer b:data) @* {} (_type_of(b))", "[data?]")
        chk("_type_of((a:tada, @outer b:data) @* { b?? })", "list<(a:tada,b:data)>")
        chk("(a:data, @outer b:data) @* { b?? } (_type_of(b))", "[data]")
    }

    @Test fun testWhatSimpleCol() {
        initCol()
        chk("f() @* {}.x", "ct_err:expr_attr_unknown:x")
        chk("f() @* { $?? }.x", "[123]")
        chk("_type_of(f() @* { $?? }.x)", "list<integer>")
    }

    @Test fun testWhatSimpleDb() {
        initDb()
        chk("(tada, @outer data) @* {}.x", "[123]")
        chk("_type_of((tada, @outer data) @* {}.x)", "list<integer?>")
        chk("(tada, @outer data) @* { data?? }.x", "[123]")
        chk("_type_of((tada, @outer data) @* { data?? }.x)", "list<integer>")
    }

    @Test fun testWhatComplexItemAttrCol() {
        initCol()

        chk("f() @* {} ( $.x )", "ct_err:expr_mem_null:data?:x")
        chk("f() @* {} ( $?.x )", "[123]")
        chk("f() @* {} ( $?.y )", "[abc]")
        chk("_type_of(f() @* {} ( $?.x ))", "list<integer?>")
        chk("_type_of(f() @* {} ( $?.y ))", "list<text?>")

        chk("f() @* { $?? } ( $.x )", "[123]")
        chk("f() @* { $?? } ( $.y )", "[abc]")
        chk("_type_of(f() @* { $?? } ( $.x ))", "list<integer>")
        chk("_type_of(f() @* { $?? } ( $.y ))", "list<text?>")
        chk("f() @* { $?? } ( $?.x )", "[123]")
        chk("_type_of(f() @* { $?? } ( $?.x ))", "list<integer>")
        chk("_type_of(f() @* { $??, $.y?? } ( $.y ))", "list<text>")
        chk("_type_of(f() @* { $?.y?? } ( $.y ))", "list<text>")
    }

    @Test fun testWhatComplexItemAttrDb() {
        initDb()

        chk("(tada, @outer data) @* {} ( data.x )", "ct_err:expr_mem_null:data?:x")
        chk("(tada, @outer data) @* {} ( data?.x )", "[123]")
        chk("_type_of((tada, @outer data) @* {} ( data?.x ))", "list<integer?>")

        chk("(tada, @outer data) @* { data?? } ( data.x )", "[123]")
        chk("_type_of((tada, @outer data) @* { data?? } ( data.x ))", "list<integer>")
        chk("(tada, @outer data) @* { data?? } ( data?.x )", "[123]")
        chk("_type_of((tada, @outer data) @* { data?? } ( data?.x ))", "list<integer>")
        chk("_type_of((tada, @outer data) @* { data?.x?? } ( data.x ))", "list<integer>")
    }

    @Test fun testWhatComplexContextAttrCol() {
        initCol()

        chk("f() @* {} ( .x )", "ct_err:expr_attr_unknown:x")
        chk("f() @* {} ( .y )", "ct_err:expr_attr_unknown:y")

        chk("f() @* { $?? } ( .x )", "[123]")
        chk("f() @* { $?? } ( .y )", "[abc]")
        chk("_type_of(f() @* { $?? } ( .x ))", "list<integer>")
        chk("_type_of(f() @* { $?? } ( .y ))", "list<text?>")
        chk("_type_of(f() @* { $??, $.y?? } ( .y ))", "list<text>")
        chk("_type_of(f() @* { $?.y?? } ( .y ))", "list<text>")

        chk("_type_of(g() @* {} ( .x ))", "list<integer>")
        chk("_type_of(g() @* {} ( .y ))", "list<text?>")
        chk("_type_of(g() @* { .y != null } ( .y ))", "list<text>")
    }

    @Test fun testWhatComplexContextAttrDb() {
        initDb()

        chk("(tada, @outer data) @* {} ( .x )", "[123]")
        chk("(tada, @outer data) @* {} ( .y )", "[abc]")
        chk("_type_of((tada, @outer data) @* {} ( .x ))", "list<integer?>")
        chk("_type_of((tada, @outer data) @* {} ( .y ))", "list<text>")

        chk("(tada, @outer data) @* { data?? } ( .x )", "[123]")
        chk("_type_of((tada, @outer data) @* { data?? } ( .x ))", "list<integer>")
        chk("_type_of((tada, @outer data) @* { data?.x?? } ( .x ))", "list<integer>")
    }

    @Test fun testWhereContextAttrCol() {
        initCol()
        chk("f() @* { .x == 123 }", "ct_err:expr_attr_unknown:x")
        chk("f() @* { $??, .x == 123 }", "[data{x=123,y=abc}]")
        chk("f() @* { .y == 'abc' }", "ct_err:expr_attr_unknown:y")
        chk("f() @* { $??, .y == 'abc' }", "[data{x=123,y=abc}]")
    }

    @Test fun testWhereContextAttrDb() {
        initDb()
        chk("(tada, @outer data) @* { .x == 123 }", "[(tada=tada[2],data=data[1])]")
        chk("(tada, @outer data) @* { .y == 'abc' }", "[(tada=tada[2],data=data[1])]")
    }

    @Test fun testWhereMatchAttrTypeCol() {
        initCol()
        chk("f() @* { 123 }", "ct_err:at_where_type:0:integer")
        chk("f() @* { $??, 123 }", "[data{x=123,y=abc}]")
        chk("f() @* { 'abc' }", "ct_err:at_where_type:0:text")
        chk("f() @* { $??, 'abc' }", "ct_err:at_where_type:1:text")
        chk("f() @* { $??, $.y??, 'abc' }", "ct_err:at_where_type:2:text")
        chk("f() @* { $?.y??, 'abc' }", "ct_err:at_where_type:1:text")
    }

    @Test fun testWhereMatchAttrTypeDb() {
        initDb()
        chk("(tada, @outer data) @* { 123 }", "[(tada=tada[2],data=data[1])]")
        chk("(tada, @outer data) @* { 'abc' }", "[(tada=tada[2],data=data[1])]")
    }

    @Test fun testWhereMatchAttrNameCol() {
        initCol()
        chkEx("{ val x = 123; return f() @* { x }; }", "ct_err:at_where:var_noattrs:0:x:integer")
        chkEx("{ val x = 123; return f() @* { $??, x }; }", "[data{x=123,y=abc}]")
        chkEx("{ val y = 'abc'; return f() @* { y }; }", "ct_err:at_where:var_noattrs:0:y:text")
        chkEx("{ val y = 'abc'; return f() @* { $??, y }; }", "ct_err:at_where:var_noattrs:1:y:text")
        chkEx("{ val y = _nullable('abc'); return f() @* { $??, y }; }", "[data{x=123,y=abc}]")
        chkEx("{ val y = 'abc'; return f() @* { $??, $.y??, y }; }", "ct_err:at_where:var_noattrs:2:y:text")
        chkEx("{ val y = 'abc'; return f() @* { $?.y??, y }; }", "ct_err:at_where:var_noattrs:1:y:text")
    }

    @Test fun testWhereMatchAttrNameDb() {
        initDb()
        chkEx("{ val x = 123; return (tada, @outer data) @* { x }; }", "[(tada=tada[2],data=data[1])]")
        chkEx("{ val y = 'abc'; return (tada, @outer data) @* { y }; }", "[(tada=tada[2],data=data[1])]")
    }

    private fun initCol() {
        tst.strictToString = false
        def("struct data { x: integer; y: text?; }")
        def("function f(): list<data?> = list<data?>(g());")
        def("function g(): list<data> = [data(x = 123, y = 'abc')];")
    }

    private fun initDb() {
        tstCtx.useSql = true
        tst.strictToString = false
        def("entity data { x: integer; }")
        def("entity tada { y: text; }")
        insert("c0.data", "x", "1,123")
        insert("c0.tada", "y", "2,'abc'")
    }
}
