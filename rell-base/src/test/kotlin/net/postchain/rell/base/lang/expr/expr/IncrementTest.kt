/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class IncrementTest: BaseRellTest() {
    @Test fun testStatement() {
        chkEx("{ var x = 123; x++; return x; }", "int[124]")
        chkEx("{ var x = 123; x--; return x; }", "int[122]")
        chkEx("{ var x = 123; ++x; return x; }", "int[124]")
        chkEx("{ var x = 123; --x; return x; }", "int[122]")
    }

    @Test fun testExpression() {
        chkEx("{ var x = 123; val y = x++; return (x, y); }", "(int[124],int[123])")
        chkEx("{ var x = 123; val y = x--; return (x, y); }", "(int[122],int[123])")
        chkEx("{ var x = 123; val y = ++x; return (x, y); }", "(int[124],int[124])")
        chkEx("{ var x = 123; val y = --x; return (x, y); }", "(int[122],int[122])")
    }

    @Test fun testOperandEntity() {
        tstCtx.useSql = true
        def("entity foo { mutable x: integer; y: integer; }")
        insert("c0.foo", "x,y", "1,123,456")

        val init = "val f = foo@{}"

        chkOp("$init; f.x++;")
        chkData("foo(1,124,456)")

        chkOp("$init; f.x--;")
        chkData("foo(1,123,456)")

        chkOp("$init; ++f.x;")
        chkData("foo(1,124,456)")

        chkOp("$init; --f.x;")
        chkData("foo(1,123,456)")

        chkOp("$init; print(f.x++);", "ct_err:expr_arg_unit")
        chkOp("$init; print(f.x--);", "ct_err:expr_arg_unit")
        chkOp("$init; print(++f.x);", "ct_err:expr_arg_unit")
        chkOp("$init; print(--f.x);", "ct_err:expr_arg_unit")
        chkData("foo(1,123,456)")

        chkOp("$init; f.y++;", "ct_err:attr_not_mutable:foo.y")
        chkOp("$init; f.y--;", "ct_err:attr_not_mutable:foo.y")
        chkOp("$init; ++f.y;", "ct_err:attr_not_mutable:foo.y")
        chkOp("$init; --f.y;", "ct_err:attr_not_mutable:foo.y")

        chkEx("{ $init; f.x++; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ $init; f.x--; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ $init; ++f.x; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ $init; --f.x; return 0; }", "ct_err:no_db_update:query")
    }

    @Test fun testOperandObject() {
        tstCtx.useSql = true
        def("object foo { mutable x: integer = 123; y: integer = 456; }")

        chkOp("foo.x++;")
        chkData("foo(0,124,456)")

        chkOp("foo.x--;")
        chkData("foo(0,123,456)")

        chkOp("++foo.x;")
        chkData("foo(0,124,456)")

        chkOp("--foo.x;")
        chkData("foo(0,123,456)")

        chkOp("print(foo.x++);", "ct_err:expr_arg_unit")
        chkOp("print(foo.x--);", "ct_err:expr_arg_unit")
        chkOp("print(++foo.x);", "ct_err:expr_arg_unit")
        chkOp("print(--foo.x);", "ct_err:expr_arg_unit")

        chkOp("foo.y++;", "ct_err:attr_not_mutable:foo.y")
        chkOp("foo.y--;", "ct_err:attr_not_mutable:foo.y")
        chkOp("++foo.y;", "ct_err:attr_not_mutable:foo.y")
        chkOp("--foo.y;", "ct_err:attr_not_mutable:foo.y")

        chkEx("{ foo.x++; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ foo.x--; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ ++foo.x; return 0; }", "ct_err:no_db_update:query")
        chkEx("{ --foo.x; return 0; }", "ct_err:no_db_update:query")
    }

    @Test fun testOperandStruct() {
        def("struct foo { mutable x: integer = 123; y: integer = 456; }")

        chkEx("{ val f = foo(); f.x++; return f; }", "foo[x=int[124],y=int[456]]")
        chkEx("{ val f = foo(); f.x--; return f; }", "foo[x=int[122],y=int[456]]")
        chkEx("{ val f = foo(); ++f.x; return f; }", "foo[x=int[124],y=int[456]]")
        chkEx("{ val f = foo(); --f.x; return f; }", "foo[x=int[122],y=int[456]]")

        chkEx("{ val f = foo(); val y = f.x++; return (f.x, y); }", "(int[124],int[123])")
        chkEx("{ val f = foo(); val y = f.x--; return (f.x, y); }", "(int[122],int[123])")
        chkEx("{ val f = foo(); val y = ++f.x; return (f.x, y); }", "(int[124],int[124])")
        chkEx("{ val f = foo(); val y = --f.x; return (f.x, y); }", "(int[122],int[122])")

        chkEx("{ val f = foo(); f.y++; return f; }", "ct_err:attr_not_mutable:foo.y")
        chkEx("{ val f = foo(); f.y--; return f; }", "ct_err:attr_not_mutable:foo.y")
        chkEx("{ val f = foo(); ++f.y; return f; }", "ct_err:attr_not_mutable:foo.y")
        chkEx("{ val f = foo(); --f.y; return f; }", "ct_err:attr_not_mutable:foo.y")
    }

    @Test fun testOperandList() {
        val init = "val t = [ 123 ]"

        chkEx("{ $init; t[0]++; return t; }", "list<integer>[int[124]]")
        chkEx("{ $init; t[0]--; return t; }", "list<integer>[int[122]]")
        chkEx("{ $init; ++t[0]; return t; }", "list<integer>[int[124]]")
        chkEx("{ $init; --t[0]; return t; }", "list<integer>[int[122]]")

        chkEx("{ $init; val y = t[0]++; return (t, y); }", "(list<integer>[int[124]],int[123])")
        chkEx("{ $init; val y = t[0]--; return (t, y); }", "(list<integer>[int[122]],int[123])")
        chkEx("{ $init; val y = ++t[0]; return (t, y); }", "(list<integer>[int[124]],int[124])")
        chkEx("{ $init; val y = --t[0]; return (t, y); }", "(list<integer>[int[122]],int[122])")
    }

    @Test fun testOperandMap() {
        val init = "val t = [ 'A' : 123 ]"

        chkEx("{ $init; t['A']++; return t; }", "map<text,integer>[text[A]=int[124]]")
        chkEx("{ $init; t['A']--; return t; }", "map<text,integer>[text[A]=int[122]]")
        chkEx("{ $init; ++t['A']; return t; }", "map<text,integer>[text[A]=int[124]]")
        chkEx("{ $init; --t['A']; return t; }", "map<text,integer>[text[A]=int[122]]")

        chkEx("{ $init; val y = t['A']++; return (t, y); }", "(map<text,integer>[text[A]=int[124]],int[123])")
        chkEx("{ $init; val y = t['A']--; return (t, y); }", "(map<text,integer>[text[A]=int[122]],int[123])")
        chkEx("{ $init; val y = ++t['A']; return (t, y); }", "(map<text,integer>[text[A]=int[124]],int[124])")
        chkEx("{ $init; val y = --t['A']; return (t, y); }", "(map<text,integer>[text[A]=int[122]],int[122])")
    }

    @Test fun testOperandNullable() {
        val init = "var x: integer? = if(1>0) 123 else null;"

        chkEx("{ $init; x++; return x; }", "ct_err:expr_incdec_type:++:integer?")
        chkEx("{ $init; x--; return x; }", "ct_err:expr_incdec_type:--:integer?")
        chkEx("{ $init; ++x; return x; }", "ct_err:expr_incdec_type:++:integer?")
        chkEx("{ $init; --x; return x; }", "ct_err:expr_incdec_type:--:integer?")

        chkEx("{ $init; x!!++; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; x!!--; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; ++x!!; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; --x!!; return x; }", "ct_err:expr_bad_dst")
    }

    @Test fun testOperandComplex() {
        def("struct rec { mutable x: integer; }")
        def("function f(r: rec): list<map<text,rec>> = [['X' : rec(-1)], ['A' : r]];")

        chkEx("{ val r = rec(123); f(r)[1]['A'].x++; return r; }", "rec[x=int[124]]")
        chkEx("{ val r = rec(123); f(r)[1]['A'].x--; return r; }", "rec[x=int[122]]")
        chkEx("{ val r = rec(123); ++f(r)[1]['A'].x; return r; }", "rec[x=int[124]]")
        chkEx("{ val r = rec(123); --f(r)[1]['A'].x; return r; }", "rec[x=int[122]]")

        chkEx("{ val r = rec(123); val y = f(r)[1]['A'].x++; return (r, y); }", "(rec[x=int[124]],int[123])")
        chkEx("{ val r = rec(123); val y = f(r)[1]['A'].x--; return (r, y); }", "(rec[x=int[122]],int[123])")
        chkEx("{ val r = rec(123); val y = ++f(r)[1]['A'].x; return (r, y); }", "(rec[x=int[124]],int[124])")
        chkEx("{ val r = rec(123); val y = --f(r)[1]['A'].x; return (r, y); }", "(rec[x=int[122]],int[122])")
    }

    @Test fun testOperandParameter() {
        chkFull("function f(x: integer): integer { x++; return x; }", "ct_err:expr_assign_val:x")
        chkFull("function f(x: integer): integer { x--; return x; }", "ct_err:expr_assign_val:x")
        chkFull("function f(x: integer): integer { ++x; return x; }", "ct_err:expr_assign_val:x")
        chkFull("function f(x: integer): integer { --x; return x; }", "ct_err:expr_assign_val:x")
    }

    @Test fun testSafeAccess() {
        def("struct r { mutable x: integer; }")
        val init = "val r: r? = _nullable(r(123))"

        chkEx("{ $init; r.x++; return r; }", "ct_err:expr_mem_null:r?:x")
        chkEx("{ $init; r.x--; return r; }", "ct_err:expr_mem_null:r?:x")
        chkEx("{ $init; ++r.x; return r; }", "ct_err:expr_mem_null:r?:x")
        chkEx("{ $init; --r.x; return r; }", "ct_err:expr_mem_null:r?:x")

        chkEx("{ $init; r?.x++; return r; }", "r[x=int[124]]")
        chkEx("{ $init; r?.x--; return r; }", "r[x=int[122]]")
        chkEx("{ $init; ++r?.x; return r; }", "r[x=int[124]]")
        chkEx("{ $init; --r?.x; return r; }", "r[x=int[122]]")

        chkEx("{ $init; return r?.x++; }", "int[123]")
        chkEx("{ $init; return r?.x--; }", "int[123]")
        chkEx("{ $init; return ++r?.x; }", "int[124]")
        chkEx("{ $init; return --r?.x; }", "int[122]")

        chkEx("{ val r: r? = null; r?.x++; return r; }", "null")
        chkEx("{ val r: r? = null; r?.x--; return r; }", "null")
        chkEx("{ val r: r? = null; ++r?.x; return r; }", "null")
        chkEx("{ val r: r? = null; --r?.x; return r; }", "null")

        chkEx("{ val r: r? = null; return r?.x++; }", "null")
        chkEx("{ val r: r? = null; return r?.x--; }", "null")
        chkEx("{ val r: r? = null; return ++r?.x; }", "null")
        chkEx("{ val r: r? = null; return --r?.x; }", "null")

        chkEx("{ val r: r? = null; return _type_of(r?.x++); }", "text[integer?]")
        chkEx("{ val r: r? = null; return _type_of(r?.x--); }", "text[integer?]")
        chkEx("{ val r: r? = null; return _type_of(++r?.x); }", "text[integer?]")
        chkEx("{ val r: r? = null; return _type_of(--r?.x); }", "text[integer?]")
    }

    @Test fun testSafeAccessEntity() {
        tstCtx.useSql = true
        def("entity user { mutable x: integer; }")
        insert("c0.user", "x", "1,123")
        val init = "val u: user? = user@?{}"

        chkOp("$init; val y = u.x++;", "ct_err:[stmt_var_unit:y][expr_mem_null:user?:x]")
        chkOp("$init; val y = u.x--;", "ct_err:[stmt_var_unit:y][expr_mem_null:user?:x]")
        chkOp("$init; val y = ++u.x;", "ct_err:[stmt_var_unit:y][expr_mem_null:user?:x]")
        chkOp("$init; val y = --u.x;", "ct_err:[stmt_var_unit:y][expr_mem_null:user?:x]")

        chkOp("$init; print(u?.x++);", "ct_err:expr_arg_unit")
        chkOp("$init; print(u?.x--);", "ct_err:expr_arg_unit")
        chkOp("$init; print(++u?.x);", "ct_err:expr_arg_unit")
        chkOp("$init; print(--u?.x);", "ct_err:expr_arg_unit")

        chkOp("val u: user? = null; u?.x++;")
        chkOp("val u: user? = null; u?.x--;")
        chkOp("val u: user? = null; ++u?.x;")
        chkOp("val u: user? = null; --u?.x;")

        chkOp("val u: user? = null; print(u?.x++);", "ct_err:expr_arg_unit")
        chkOp("val u: user? = null; print(u?.x--);", "ct_err:expr_arg_unit")
        chkOp("val u: user? = null; print(++u?.x);", "ct_err:expr_arg_unit")
        chkOp("val u: user? = null; print(--u?.x);", "ct_err:expr_arg_unit")
    }

    @Test fun testTrickySyntax() {
        val init = "var x = 123"

        chkEx("{ $init; ++x++; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; ++x--; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; --x++; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; --x--; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; ++++x; return x; }", "ct_err:syntax")
        chkEx("{ $init; ++--x; return x; }", "ct_err:syntax")
        chkEx("{ $init; --++x; return x; }", "ct_err:syntax")
        chkEx("{ $init; ----x; return x; }", "ct_err:syntax")
        chkEx("{ $init; x++++; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; x++--; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; x--++; return x; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; x----; return x; }", "ct_err:expr_bad_dst")

        chkEx("{ $init; val y = -x++; return (x, y); }", "(int[124],int[-123])")
        chkEx("{ $init; val y = -x--; return (x, y); }", "(int[122],int[-123])")
        chkEx("{ $init; val y = -++x; return (x, y); }", "(int[124],int[-124])")
        chkEx("{ $init; val y = ---x; return 0; }", "ct_err:expr_bad_dst")

        chkEx("{ $init; val y = +x++; return (x, y); }", "(int[124],int[123])")
        chkEx("{ $init; val y = +x--; return (x, y); }", "(int[122],int[123])")
        chkEx("{ $init; val y = +++x; return 0; }", "ct_err:expr_bad_dst")
        chkEx("{ $init; val y = +--x; return (x, y); }", "(int[122],int[122])")

        chkEx("{ $init; -x++; return x; }", "ct_err:syntax")
        chkEx("{ $init; -x--; return x; }", "ct_err:syntax")
        chkEx("{ $init; -++x; return x; }", "ct_err:syntax")
        chkEx("{ $init; ---x; return x; }", "ct_err:syntax")
        chkEx("{ $init; +x++; return x; }", "ct_err:syntax")
        chkEx("{ $init; +x--; return x; }", "ct_err:syntax")
        chkEx("{ $init; +++x; return x; }", "ct_err:syntax")
        chkEx("{ $init; +--x; return x; }", "ct_err:syntax")
    }

    @Test fun testWrongType() {
        def("entity user { name; }")
        def("struct rec { mutable v: integer; }")
        chkWrongType("boolean")
        chkWrongType("text")
        chkWrongType("byte_array")
        chkWrongType("range")
        chkWrongType("list<integer>")
        chkWrongType("map<integer,integer>")
        chkWrongType("user")
        chkWrongType("rec")
    }

    private fun chkWrongType(type: String) {
        chkCompile("function f(x: $type) { var y = x; y++; }", "ct_err:expr_incdec_type:++:$type")
        chkCompile("function f(x: $type) { var y = x; y--; }", "ct_err:expr_incdec_type:--:$type")
        chkCompile("function f(x: $type) { var y = x; ++y; }", "ct_err:expr_incdec_type:++:$type")
        chkCompile("function f(x: $type) { var y = x; --y; }", "ct_err:expr_incdec_type:--:$type")
    }
}
