/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.expr.expr

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceBodyDsl
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.LibModuleTester
import net.postchain.rell.base.testutils.iff
import net.postchain.rell.base.testutils.iffArray
import kotlin.test.Test

class NullAnalysisPathTest: BaseRellTest() {
    @Test fun testBasic() {
        initFooBar()

        chkEx("{ var x: integer?; x = 123; return _type_of(x); }", "integer")
        chkEx("{ var x: integer?; x = null; x = 123; return _type_of(x); }", "integer")

        chkEx("{ val f = foo_nz(); return _type_of(f.x); }", "integer?")
        chkEx("{ val f = foo_nz(); return _type_of(f.y); }", "integer?")
        chkEx("{ val f = foo_nz(); f.x!!; return _type_of(f.x); }", "integer")
        chkEx("{ val f = foo_nz(); f.x!!; return _type_of(f.y); }", "integer?")
        chkEx("{ val f = foo_nz(); f.y!!; return _type_of(f.x); }", "integer?")
        chkEx("{ val f = foo_nz(); f.y!!; return _type_of(f.y); }", "integer?")
        chkEx("{ val f = foo_nz(); return if (f.x != null) _type_of(f.x) else ''; }", "integer")
        chkEx("{ val f = foo_nz(); return if (f.x != null) _type_of(f.y) else ''; }", "integer?")
        chkEx("{ val f = foo_nz(); return if (f.y != null) _type_of(f.x) else ''; }", "integer?")
        chkEx("{ val f = foo_nz(); return if (f.y != null) _type_of(f.y) else ''; }", "integer?")
        chkEx("{ val f = foo_nz(); return if (f.x != null) f.x + 1 else -1; }", "124")
        chkEx("{ val f = foo_nz(); return if (f.y != null) f.y + 1 else -1; }",
            "ct_err:binop_operand_type:+:[integer?]:[integer]")
    }

    @Test fun testVar() {
        def("struct foo { x: integer?; mutable y: integer?; }")
        def("function get_foo() = foo(x = 123, y = 456);")

        chkEx("{ var f = get_foo(); return _type_of(f.x); }", "text[integer?]")
        chkEx("{ var f = get_foo(); f.x!!; return _type_of(f.x); }", "text[integer]")
        chkEx("{ var f = get_foo(); f.x!!; f = get_foo(); return _type_of(f.x); }", "text[integer?]")
        chkEx("{ var f = get_foo(); if (f.x == null) return ''; return _type_of(f.x); }", "text[integer]")
        chkEx("{ var f = get_foo(); if (f.x == null) return ''; f = get_foo(); return _type_of(f.x); }",
            "text[integer?]")

        chkEx("""{
            var f = get_foo();
            print(_type_of(f.x));
            if (f.x == null) return 0;
            print(_type_of(f.x));
            f = get_foo();
            print(_type_of(f.x));
            if (f.x == null) return 0;
            print(_type_of(f.x));
            return 0;
        }
        """, "int[0]")
        chkOut("integer?", "integer", "integer?", "integer")
    }

    @Test fun testSafeAccess() {
        initFooBar()

        chkEx("{ var f = foo_z(); return _type_of(f); }", "foo?")

        chkEx("{ var f = foo_z(); f?.bar?.r!!; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); f?.bar?.r!!; return _type_of(f.bar); }", "bar")
        chkEx("{ var f = foo_z(); f?.bar?.r!!; return _type_of(f?.bar); }", "bar")
        chkEx("{ var f = foo_z(); f?.bar?.r!!; return _type_of(f.bar.r); }", "integer")
        chkEx("{ var f = foo_z(); f?.bar?.r!!; return _type_of(f?.bar.r); }", "integer")
        chkEx("{ var f = foo_z(); f?.bar?.r!!; return _type_of(f?.bar?.r); }", "integer")
        chkEx("{ var f = foo_z(); f?.bar?.s!!; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); f?.bar?.s!!; return _type_of(f.bar); }", "bar")
        chkEx("{ var f = foo_z(); f?.bar?.s!!; return _type_of(f.bar.s); }", "integer?")
        chkEx("{ var f = foo_z(); return if (f?.bar?.r != null) _type_of(f) else ''; }", "foo")
        chkEx("{ var f = foo_z(); return if (f?.bar?.r != null) _type_of(f.bar) else ''; }", "bar")
        chkEx("{ var f = foo_z(); return if (f?.bar?.r != null) _type_of(f.bar.r) else ''; }", "integer")
        chkEx("{ var f = foo_z(); return if (f?.bar?.r != null) _type_of(f) else ''; }", "foo")
        chkEx("{ var f = foo_z(); return if (f?.bar?.r != null) _type_of(f.bar) else ''; }", "bar")
        chkEx("{ var f = foo_z(); return if (f?.bar?.r != null) _type_of(f.bar.s) else ''; }", "integer?")
        chkEx("{ var f = foo_z(); if (f?.bar?.r == null) return ''; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); if (f?.bar?.r == null) return ''; return _type_of(f.bar); }", "bar")
        chkEx("{ var f = foo_z(); if (f?.bar?.r == null) return ''; return _type_of(f.bar.r); }", "integer")

        chkEx("{ var f = foo_z(); f?.mbar?.r!!; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); f?.mbar?.r!!; return _type_of(f.mbar); }", "bar?")
        chkEx("{ var f = foo_z(); f?.mbar?.r!!; return _type_of(f?.mbar); }", "bar?")
        chkEx("{ var f = foo_z(); f?.mbar?.r!!; return _type_of(f.mbar?.r); }", "integer?")
        chkEx("{ var f = foo_z(); f?.mbar?.r!!; return _type_of(f?.mbar?.r); }", "integer?")
        chkEx("{ var f = foo_z(); f?.mbar?.s!!; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); f?.mbar?.s!!; return _type_of(f.mbar); }", "bar?")
        chkEx("{ var f = foo_z(); f?.mbar?.s!!; return _type_of(f?.mbar); }", "bar?")
        chkEx("{ var f = foo_z(); f?.mbar?.s!!; return _type_of(f.mbar?.s); }", "integer?")
        chkEx("{ var f = foo_z(); return if (f?.mbar?.r != null) _type_of(f) else ''; }", "foo")
        chkEx("{ var f = foo_z(); return if (f?.mbar?.r != null) _type_of(f.mbar) else ''; }", "bar?")
        chkEx("{ var f = foo_z(); return if (f?.mbar?.s != null) _type_of(f) else ''; }", "foo")
        chkEx("{ var f = foo_z(); return if (f?.mbar?.s != null) _type_of(f.mbar) else ''; }", "bar?")
        chkEx("{ var f = foo_z(); if (f?.mbar?.r == null) return ''; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); if (f?.mbar?.r == null) return ''; return _type_of(f.mbar); }", "bar?")

        chkEx("{ var f = foo_z(); f?.bar?.r!!; f = foo_z(); return _type_of(f); }", "foo?")
        chkEx("{ var f = foo_z(); f?.bar?.s!!; f = foo_z(); return _type_of(f); }", "foo?")
        chkEx("{ var f = foo_z(); if (f?.bar?.r == null) return ''; f = foo_z(); return _type_of(f); }", "foo?")
    }

    @Test fun testSafeAccessNull() {
        initFooBar()
        def("function foo_custom(): foo? = foo(x = null, y = null, bar = null, mbar = null);")

        chkEx("{ var f = foo_custom(); if (f?.x == null) return _type_of(f); return ''; }", "foo?")
        chkEx("{ var f = foo_custom(); if (f?.x == null) return _type_of(f?.x); return ''; }", "integer?")
        chkEx("{ var f = foo_custom(); if (f?.x == null) return f?.x == null; return null; }", "true")
        chkWarn("expr:smartnull:expr:always:[f.x]")
        chkEx("{ var f = foo_custom(); if (f?.x == null) return f?.x != null; return null; }", "false")
        chkWarn("expr:smartnull:expr:always:[f.x]")

        chkEx("{ var f = foo_custom(); if (f?.x == null) { f!!; return _type_of(f); } return ''; }", "foo")
        chkEx("{ var f = foo_custom(); if (f?.x == null) { f!!; return _type_of(f.x); } return ''; }", "integer?")
        chkEx("{ var f = foo_custom(); if (f?.x == null) { f!!; return f.x == null; } return null; }", "true")
        chkWarn("expr:smartnull:expr:always:[f.x]")
        chkEx("{ var f = foo_custom(); if (f?.x == null) { f!!; return f.x != null; } return null; }", "false")
        chkWarn("expr:smartnull:expr:always:[f.x]")
    }

    @Test fun testSafeAccessNotNullableAttr() {
        tst.strictToString = false
        def("struct fer { s: integer; }")
        def("struct ref { q: integer; fer; }")
        def("struct data { x: integer; mutable y: integer; ref; }")
        def("function f(): data? = data(x = 123, y = 456, ref(q = 789, fer(s = 987)));")

        chkEx("{ val d = f(); return _type_of(d); }", "data?")
        chkEx("{ val d = f(); return _type_of(d?.x); }", "integer?")
        chkEx("{ val d = f(); return _type_of(d?.y); }", "integer?")

        chkEx("{ val d = f(); if (d == null) return ''; return _type_of(d); }", "data")
        chkEx("{ val d = f(); if (d == null) return ''; return _type_of(d.x); }", "integer")
        chkEx("{ val d = f(); if (d == null) return ''; return _type_of(d.y); }", "integer")
        chkEx("{ val d = f(); if (d == null) return ''; return _type_of(d?.x); }", "integer")
        chkEx("{ val d = f(); if (d == null) return ''; return _type_of(d?.y); }", "integer")

        chkEx("{ val d = f(); if (d?.x == null) return ''; return _type_of(d); }", "data")
        chkEx("{ val d = f(); if (d?.x == null) return ''; return _type_of(d.x); }", "integer")
        chkEx("{ val d = f(); if (d?.x == null) return ''; return _type_of(d.y); }", "integer")
        chkEx("{ val d = f(); if (d?.x == null) return ''; return _type_of(d?.x); }", "integer")
        chkEx("{ val d = f(); if (d?.x == null) return ''; return _type_of(d?.y); }", "integer")

        chkEx("{ val d = f(); if (d?.y == null) return ''; return _type_of(d); }", "data")
        chkEx("{ val d = f(); if (d?.y == null) return ''; return _type_of(d.x); }", "integer")
        chkEx("{ val d = f(); if (d?.y == null) return ''; return _type_of(d.y); }", "integer")
        chkEx("{ val d = f(); if (d?.y == null) return ''; return _type_of(d?.x); }", "integer")
        chkEx("{ val d = f(); if (d?.y == null) return ''; return _type_of(d?.y); }", "integer")

        chkEx("{ val d = f(); d!!; return d?.ref?.q; }", "789")
        chkWarn("expr:smartnull:expr:never:[d.ref]", "expr:smartnull:var:never:[d]")

        chkEx("{ val d = f(); d!!; return d?.ref.fer?.s; }", "ct_err:expr_safemem_type:[fer]:s")
        chkWarn("expr:smartnull:var:never:[d]")
    }

    @Test fun testLongerPath() {
        def("struct s1 { a: s2; }")
        def("struct s2 { b: s3; }")
        def("struct s3 { c: s4; }")
        def("struct s4 { x: integer?; }")
        def("function get_s1() = s1(s2(s3(s4(x = 123))));")

        chkEx("{ var s = get_s1(); return _type_of(s.a.b.c.x); }", "text[integer?]")
        chkEx("{ var s = get_s1(); s.a.b.c.x!!; return _type_of(s.a.b.c.x); }", "text[integer]")
        chkEx("{ var s = get_s1(); if (s.a.b.c.x == null) return ''; return _type_of(s.a.b.c.x); }", "text[integer]")
        chkEx("{ var s = get_s1(); return if (s.a.b.c.x == null) '' else  _type_of(s.a.b.c.x); }", "text[integer]")
        chkEx("{ var s = get_s1(); return if (s.a.b.c.x != null) _type_of(s.a.b.c.x) else ''; }", "text[integer]")
    }

    @Test fun testWarnings() {
        initFooBar()

        chkEx("{ val f = foo_z(); return f?.x; }", "123")
        chkWarn()
        chkEx("{ val f = foo_z(); f!!; return f?.x; }", "123")
        chkWarn("expr:smartnull:var:never:[f]")

        chkEx("{ val f = foo_z0(); return f?.bar == null; }", "true")
        chkWarn()
        chkEx("{ val f = foo_z0(); return f?.mbar == null; }", "true")
        chkWarn()
        chkEx("{ val f = foo_z0(); require(f == null); return f?.bar == null; }", "true")
        chkWarn("expr:smartnull:var:always:[f]")
        chkEx("{ val f = foo_z0(); require(f == null); return f?.mbar == null; }", "true")
        chkWarn("expr:smartnull:var:always:[f]")

        chkEx("{ val f = foo_z(); f?.bar?.r!!; return f?.x; }", "123")
        chkWarn("expr:smartnull:var:never:[f]")
        chkEx("{ val f = foo_z(); f?.mbar?.s!!; return f?.x; }", "123")
        chkWarn("expr:smartnull:var:never:[f]")
        chkEx("{ val f = foo_z(); f?.bar?.r!!; return f.bar?.s; }", "654")
        chkWarn("expr:smartnull:expr:never:[f.bar]")
        chkEx("{ val f = foo_z(); f?.bar?.r!!; return f?.bar?.s; }", "654")
        chkWarn("expr:smartnull:expr:never:[f.bar]", "expr:smartnull:var:never:[f]")
    }

    @Test fun testRequire() {
        initFooBar()
        chkEx("{ var f = foo_z(); return _type_of(f); }", "foo?")
        chkEx("{ var f = foo_z(); require(f); return _type_of(f); }", "foo")
        chkEx("{ var f = foo_nz(); return _type_of(f.x); }", "integer?")
        chkEx("{ var f = foo_nz(); require(f.x); return _type_of(f.x); }", "integer")
        chkEx("{ var f = foo_nz(); return _type_of(f.y); }", "integer?")
        chkEx("{ var f = foo_nz(); require(f.y); return _type_of(f.y); }", "integer?")
    }

    @Test fun testNotInited() {
        initFooBar()
        chkEx("{ var f: foo; return f.x; }", "ct_err:expr_var_uninit:f")
        chkEx("{ var f: foo; return f.bar?.r; }", "ct_err:expr_var_uninit:f")
    }

    @Test fun testMemberFunction() {
        initFooBar()
        chkEx("{ val f = foo_z(); require(f?.x?.to_hex()); return _type_of(f); }", "foo")
        chkEx("{ val f = foo_z(); require(f?.x?.to_hex()); return _type_of(f.x); }", "integer")
        chkEx("{ val f = foo_z(); require(f?.x?.to_hex()); return _type_of(f.y); }", "integer?")
        chkEx("{ val f = foo_z(); require(f?.y?.to_hex()); return _type_of(f); }", "foo")
        chkEx("{ val f = foo_z(); require(f?.y?.to_hex()); return _type_of(f.x); }", "integer?")
        chkEx("{ val f = foo_z(); require(f?.y?.to_hex()); return _type_of(f.y); }", "integer?")
    }

    @Test fun testSpecialCases() {
        initFooBar()
        chkEx("{ var f = foo_z(); f!!.x!!; return _type_of(f); }", "foo")
        chkEx("{ var f = foo_z(); f!!.x!!; return _type_of(f.x); }", "integer")
        chkEx("{ var f = foo_z(); require(f!!.x); return _type_of(f.x); }", "integer")
        chkEx("{ var f = foo_z(); f!!.y!!; return _type_of(f.y); }", "integer?")
        chkEx("{ var f = foo_z(); require(f!!.y); return _type_of(f.y); }", "integer?")
    }

    @Test fun testLongPath() {
        tst.strictToString = false
        def("struct A { b: B?; }")
        def("struct B { c: C?; }")
        def("struct C { d: D?; }")
        def("struct D { e: E?; }")
        def("struct E { f: integer?; }")
        def("function get_a(): A? = A(B(C(D(E(123)))));")

        chkEx("{ val a = get_a(); return _type_of(a); }", "A?")
        chkEx("{ val a = get_a(); return _type_of(a?.b); }", "B?")
        chkEx("{ val a = get_a(); return _type_of(a?.b?.c); }", "C?")
        chkEx("{ val a = get_a(); return _type_of(a?.b?.c?.d); }", "D?")
        chkEx("{ val a = get_a(); return _type_of(a?.b?.c?.d?.e); }", "E?")
        chkEx("{ val a = get_a(); return _type_of(a?.b?.c?.d?.e?.f); }", "integer?")

        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a); }", "A")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a.b); }", "B")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a?.b); }", "B")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a.b.c); }", "C")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a?.b?.c); }", "C")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a.b?.c); }", "C")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a.b.c.d); }", "D?")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a.b.c.d?.e); }", "E?")
        chkEx("{ val a = get_a(); if (a?.b?.c == null) return ''; return _type_of(a.b.c.d?.e?.f); }", "integer?")

        val ifs = "if (a?.b?.c == null) return ''; if (a.b.c.d?.e?.f == null) return '';"
        chkEx("{ val a = get_a(); $ifs return _type_of(a); }", "A")
        chkEx("{ val a = get_a(); $ifs return _type_of(a.b); }", "B")
        chkEx("{ val a = get_a(); $ifs return _type_of(a.b.c); }", "C")
        chkEx("{ val a = get_a(); $ifs return _type_of(a.b.c.d); }", "D")
        chkEx("{ val a = get_a(); $ifs return _type_of(a.b.c.d.e); }", "E")
        chkEx("{ val a = get_a(); $ifs return _type_of(a.b.c.d.e.f); }", "integer")
        chkEx("{ val a = get_a(); $ifs return _type_of(a?.b?.c?.d?.e?.f); }", "integer")
    }

    @Test fun testModuleArgs() {
        tst.strictToString = false
        def("struct ref { r: integer?; }")
        def("struct module_args { x: integer?; ref?; }")
        tst.moduleArgs("" to "{'x':123,'ref':{'r':456}}")

        val args = "chain_context.args"
        chkEx("{ return _type_of($args.x); }", "integer?")
        chkEx("{ return _type_of($args.ref); }", "ref?")
        chkEx("{ return _type_of($args.ref?.r); }", "integer?")

        chkEx("{ if ($args.x == null) return ''; return _type_of($args.x); }", "integer")
        chkEx("{ if ($args.ref == null) return ''; return _type_of($args.ref); }", "ref")
        chkEx("{ if ($args.ref == null) return ''; return _type_of($args.ref.r); }", "integer?")
        chkEx("{ if ($args.ref?.r == null) return ''; return _type_of($args.ref); }", "ref")
        chkEx("{ if ($args.ref?.r == null) return ''; return _type_of($args.ref.r); }", "integer")
        chkEx("{ if ($args.ref?.r == null) return ''; return _type_of($args.ref?.r); }", "integer")

        chkEx("{ if ($args.x == null) return 0; $args.x!!; return 1; }", "1")
        chkWarn("expr:smartnull:expr:never:[:chain_context.args.x]")
        chkEx("{ if ($args.x != null) return 1; $args.x!!; return 0; }", "1")
        chkWarn("expr:smartnull:expr:always:[:chain_context.args.x]")
    }

    @Test fun testTupleAttrs() {
        tst.strictToString = false
        def("function f(): (a: integer?, b: (x: integer?, boolean?)?, text?)? = (a=123, b=(x=456, true), 'A');")

        chkEx("{ val v = f(); return _type_of(v); }", "(a:integer?,b:(x:integer?,boolean?)?,text?)?")

        chkEx("{ val v = f(); v!!; return _type_of(v.a); }", "integer?")
        chkEx("{ val v = f(); v!!; return _type_of(v[0]); }", "integer?")
        chkEx("{ val v = f(); v!!; v.a!!; return _type_of(v.a); }", "integer")
        chkEx("{ val v = f(); v!!; v.a!!; return _type_of(v[0]); }", "integer")
        chkEx("{ val v = f(); v!!; v[0]!!; return _type_of(v.a); }", "integer")
        chkEx("{ val v = f(); v!!; v[0]!!; return _type_of(v[0]); }", "integer")

        chkEx("{ val v = f(); v!!; return _type_of(v.b); }", "(x:integer?,boolean?)?")
        chkEx("{ val v = f(); v!!; return _type_of(v[1]); }", "(x:integer?,boolean?)?")
        chkEx("{ val v = f(); v!!; v.b!!; return _type_of(v.b); }", "(x:integer?,boolean?)")
        chkEx("{ val v = f(); v!!; v.b!!; return _type_of(v[1]); }", "(x:integer?,boolean?)")
        chkEx("{ val v = f(); v!!; v[1]!!; return _type_of(v.b); }", "(x:integer?,boolean?)")
        chkEx("{ val v = f(); v!!; v[1]!!; return _type_of(v[1]); }", "(x:integer?,boolean?)")

        chkEx("{ val v = f(); v!!; v.b!!; return _type_of(v.b.x); }", "integer?")
        chkEx("{ val v = f(); v!!; v.b!!; return _type_of(v.b[0]); }", "integer?")
        chkEx("{ val v = f(); v!!; v.b!!; return _type_of(v.b[1]); }", "boolean?")
        chkEx("{ val v = f(); v!!; v.b!!; v.b.x!!; return _type_of(v.b.x); }", "integer")
        chkEx("{ val v = f(); v!!; v.b!!; v.b.x!!; return _type_of(v.b[0]); }", "integer")
        chkEx("{ val v = f(); v!!; v.b!!; v.b.x!!; return _type_of(v.b[1]); }", "boolean?")
        chkEx("{ val v = f(); v!!; v.b!!; v.b[0]!!; return _type_of(v.b.x); }", "integer")
        chkEx("{ val v = f(); v!!; v.b!!; v.b[0]!!; return _type_of(v.b[0]); }", "integer")
        chkEx("{ val v = f(); v!!; v.b!!; v.b[0]!!; return _type_of(v.b[1]); }", "boolean?")
        chkEx("{ val v = f(); v!!; v.b!!; v.b[1]!!; return _type_of(v.b.x); }", "integer?")
        chkEx("{ val v = f(); v!!; v.b!!; v.b[1]!!; return _type_of(v.b[0]); }", "integer?")
        chkEx("{ val v = f(); v!!; v.b!!; v.b[1]!!; return _type_of(v.b[1]); }", "boolean")

        chkEx("{ val v = f(); v!!; return _type_of(v[2]); }", "text?")
        chkEx("{ val v = f(); v!!; v[2]!!; return _type_of(v[2]); }", "text")
        chkEx("{ val v = f(); v!!; v[2]!!; return _type_of(v.a); }", "integer?")
        chkEx("{ val v = f(); v!!; v[2]!!; return _type_of(v[0]); }", "integer?")
        chkEx("{ val v = f(); v!!; v[2]!!; return _type_of(v.b); }", "(x:integer?,boolean?)?")
        chkEx("{ val v = f(); v!!; v[2]!!; return _type_of(v[1]); }", "(x:integer?,boolean?)?")
    }

    @Test fun testFunctionCall() {
        tst.strictToString = false
        def("function f(x: integer?): integer? = x;")

        val xyz = "val x = f(123); val y = f(456); val z = f(789);"
        chkEx("{ $xyz val t = z?.min(x!!)?.max(y!!); return t + ':' + _type_of(x);  }", "456:integer?")
        chkEx("{ $xyz val t = z?.min(x!!)?.max(y!!); return t + ':' + _type_of(y);  }", "456:integer?")
        chkEx("{ $xyz z?.abs()!!; return _type_of(z); }", "integer")
        chkEx("{ $xyz z?.abs()!!; return _type_of(z.abs()); }", "integer")
        chkEx("{ $xyz z?.abs()!!; return _type_of(z?.abs()); }", "integer")

        val m = "val m = _nullable([1:[2:'A']]);"
        chkEx("{ $m return _type_of(m); }", "map<integer,map<integer,text>>?")
        chkEx("{ $m m!!; return _type_of(m); }", "map<integer,map<integer,text>>")
        chkEx("{ $xyz $m m?.get_or_null(x!!); return _type_of(x); }", "integer?")
        chkEx("{ $xyz $m m!!; m.get_or_null(x!!); return _type_of(x); }", "integer")
        chkEx("{ $xyz $m m!!; m?.get_or_null(x!!); return _type_of(x); }", "integer")
        chkEx("{ $xyz $m m!!; m.get_or_null(x!!)?.get_or_null(y!!); return _type_of(y); }", "integer?")
        chkEx("{ $xyz $m m!!; m?.get_or_null(x!!)?.get_or_null(y!!); return _type_of(y); }", "integer?")
    }

    @Test fun testFunctionCallChain() {
        tst.strictToString = false
        def("function f(x: text?): text? = x;")

        chkEx("{ val s = f('A'); s!!; return s?.lower_case()?.upper_case()?.sub(0); }", "A")
        chkWarn("expr:smartnull:expr:never", "expr:smartnull:expr:never", "expr:smartnull:var:never:[s]")
    }

    //TODO make this test work - needs safe subscript support
    /*@Test*/ fun testSubscriptAssignment() {
        tst.strictToString = false
        def("struct rec { l: list<integer> = [10,11,12,13,14]; }")

        val r = "val r = _nullable(rec());"
        chkEx("{ $r return _type_of(r); }", "rec?")
        chkEx("{ $r val x = _nullable_int(3); r?.l[x!!] = 123; return _type_of(x); }", "integer?")
        chkEx("{ $r val x = _nullable_int(3); r!!; r.l[x!!] = 123; return _type_of(x); }", "integer")
        chkEx("{ $r val x = _nullable_int(3); r!!; r?.l[x!!] = 123; return _type_of(x); }", "integer")
    }

    @Test fun testMirrorStruct() {
        tst.strictToString = false
        def("operation op(x: integer?) {}")
        def("function f(): struct<op>? = struct<op>(x = 123);")
        def("function g(): struct<mutable op>? = struct<mutable op>(x = 123);")

        chkEx("{ val s = f(); return _type_of(s); }", "struct<op>?")
        chkEx("{ val s = g(); return _type_of(s); }", "struct<mutable op>?")
        chkEx("{ val s = f(); s!!; return _type_of(s.x); }", "integer?")
        chkEx("{ val s = g(); s!!; return _type_of(s.x); }", "integer?")
        chkEx("{ val s = f(); s!!; s.x!!; return _type_of(s.x); }", "integer")
        chkEx("{ val s = g(); s!!; s.x!!; return _type_of(s.x); }", "integer?")
    }

    @Test fun testEntityVariable() {
        initEntity()

        chkEx("{ val r = ref @?{}; return _type_of(r); }", "ref?")
        chkEx("{ val r = ref @?{}; return _type_of(r?.x); }", "integer?")
        chkEx("{ val r = ref @?{}; return _type_of(r?.y); }", "integer?")

        chkEx("{ val r = ref @?{}; r!!; return _type_of(r); }", "ref")
        chkEx("{ val r = ref @?{}; r!!; return _type_of(r?.x); }", "integer")
        chkEx("{ val r = ref @?{}; r!!; return _type_of(r?.y); }", "integer")

        chkEx("{ val r = ref @?{}; r?.x!!; return _type_of(r); }", "ref")
        chkEx("{ val r = ref @?{}; r?.x!!; return _type_of(r?.x); }", "integer")
        chkEx("{ val r = ref @?{}; r?.x!!; return _type_of(r?.y); }", "integer")
        chkEx("{ val r = ref @?{}; r?.y!!; return _type_of(r); }", "ref")
        chkEx("{ val r = ref @?{}; r?.y!!; return _type_of(r?.x); }", "integer")
        chkEx("{ val r = ref @?{}; r?.y!!; return _type_of(r?.y); }", "integer")
    }

    private fun initEntity() {
        tstCtx.useSql = true
        tst.strictToString = false
        def("entity data { v: integer; }")
        def("entity ref { key data; x: integer; mutable y: integer; }")
        insert("c0.data", "v", "10,999")
        insert("c0.ref", "data,x,y", "20,10,123,456")
    }

    @Test fun testLibConstant() {
        tst.strictToString = false
        val modTst = LibModuleTester(tst, Lib_Rell.MODULE)
        modTst.libModule {
            val structGetter = initLibStruct(this)
            constant("V", "rec?") {
                value(structGetter)
            }
            type("data") {
                modTst.setRTypeFactory(this)
                constant("W", "rec?") {
                    value(structGetter)
                }
            }
        }

        chkLibConstant("V")
        chkLibConstant("data.W")
    }

    private fun chkLibConstant(name: String) {
        chkEx("{ return _type_of($name); }", "rec?")
        chkEx("{ $name!!; return _type_of($name); }", "rec")
        chkEx("{ $name!!; return _type_of($name.x); }", "integer?")
        chkEx("{ $name!!; return _type_of($name.y); }", "integer?")
        chkEx("{ $name!!; $name.x!!; return _type_of($name.x); }", "integer")
        chkEx("{ $name!!; $name.y!!; return _type_of($name.y); }", "integer?")

        chkEx("{ $name!!; $name!!; return 0; }", "0")
        chkWarn("expr:smartnull:const:never:[mod:$name]")
    }

    @Test fun testLibPropertyNs() {
        tst.strictToString = false
        val modTst = LibModuleTester(tst, Lib_Rell.MODULE)
        modTst.libModule {
            val structGetter = initLibStruct(this)
            property("pure", "rec?", pure = true) {
                value { _, type -> structGetter(type) }
            }
            property("non_pure", "rec?", pure = false) {
                value { _, type -> structGetter(type) }
            }
        }

        chkEx("{ return _type_of(pure); }", "rec?")
        chkEx("{ pure!!; return _type_of(pure); }", "rec")
        chkEx("{ pure!!; return _type_of(pure.x); }", "integer?")
        chkEx("{ pure!!; return _type_of(pure.y); }", "integer?")
        chkEx("{ pure!!; pure.x!!; return _type_of(pure.x); }", "integer")
        chkEx("{ pure!!; pure.y!!; return _type_of(pure.y); }", "integer?")
        chkEx("{ pure?.x!!; return _type_of(pure); }", "rec")
        chkEx("{ pure?.y!!; return _type_of(pure); }", "rec")
        chkEx("{ pure?.x!!; return _type_of(pure.x); }", "integer")
        chkEx("{ pure?.y!!; return _type_of(pure.y); }", "integer?")

        chkEx("{ return _type_of(non_pure); }", "rec?")
        chkEx("{ non_pure!!; return _type_of(non_pure); }", "rec?")
        chkEx("{ non_pure?.x!!; return _type_of(non_pure); }", "rec?")
        chkEx("{ non_pure?.y!!; return _type_of(non_pure); }", "rec?")
        chkEx("{ non_pure?.x!!; return _type_of(non_pure?.x); }", "integer?")
        chkEx("{ non_pure?.y!!; return _type_of(non_pure?.y); }", "integer?")

        chkEx("{ pure!!; pure!!; return 0; }", "0")
        chkWarn("expr:smartnull:prop:never:[mod:pure]")
        chkEx("{ non_pure!!; non_pure!!; return 0; }", "0")
        chkWarn()
    }

    @Test fun testLibPropertyType() {
        tst.strictToString = false
        def("function f(): integer? = 123;")

        val modTst = LibModuleTester(tst, Lib_Rell.MODULE)
        modTst.libModule {
            val structGetter = initLibStruct(this)
            extension("int_ext", "integer") {
                property("pure", "rec?", pure = true) {
                    value { _, type -> structGetter(type) }
                }
                property("non_pure", "rec?", pure = false) {
                    value { _, type -> structGetter(type) }
                }
            }
        }

        chkEx("{ val v = f(); return _type_of(v); }", "integer?")
        chkEx("{ val v = f(); v!!; return _type_of(v); }", "integer")

        chkEx("{ val v = f(); v!!; return _type_of(v.pure); }", "rec?")
        chkEx("{ val v = f(); v!!; v.pure!!; return _type_of(v.pure); }", "rec")
        chkEx("{ val v = f(); v!!; v.pure!!; return _type_of(v.pure.x); }", "integer?")
        chkEx("{ val v = f(); v!!; v.pure!!; return _type_of(v.pure.y); }", "integer?")
        chkEx("{ val v = f(); v!!; v.pure!!; v.pure.x!!; return _type_of(v.pure.x); }", "integer")
        chkEx("{ val v = f(); v!!; v.pure!!; v.pure.y!!; return _type_of(v.pure.y); }", "integer?")
        chkEx("{ val v = f(); v?.pure?.x!!; return _type_of(v); }", "integer")
        chkEx("{ val v = f(); v?.pure?.x!!; return _type_of(v.pure); }", "rec")
        chkEx("{ val v = f(); v?.pure?.x!!; return _type_of(v.pure.x); }", "integer")
        chkEx("{ val v = f(); v?.pure?.y!!; return _type_of(v); }", "integer")
        chkEx("{ val v = f(); v?.pure?.y!!; return _type_of(v.pure); }", "rec")
        chkEx("{ val v = f(); v?.pure?.y!!; return _type_of(v.pure.y); }", "integer?")

        chkEx("{ val v = f(); v!!; return _type_of(v.non_pure); }", "rec?")
        chkEx("{ val v = f(); v!!; v.non_pure!!; return _type_of(v.non_pure); }", "rec?")
        chkEx("{ val v = f(); v!!; v.non_pure?.x!!; return _type_of(v.non_pure); }", "rec?")
        chkEx("{ val v = f(); v!!; v.non_pure?.x!!; return _type_of(v.non_pure?.x); }", "integer?")
        chkEx("{ val v = f(); v!!; v.non_pure?.y!!; return _type_of(v.non_pure); }", "rec?")
        chkEx("{ val v = f(); v!!; v.non_pure?.y!!; return _type_of(v.non_pure?.y); }", "integer?")
        chkEx("{ val v = f(); v?.non_pure?.x!!; return _type_of(v); }", "integer")
        chkEx("{ val v = f(); v?.non_pure?.x!!; return _type_of(v.non_pure); }", "rec?")

        chkEx("{ val v = f(); v?.pure!!; v.pure!!; return 0; }", "0")
        chkWarn("expr:smartnull:expr:never:[v.pure]")
    }

    private fun initLibStruct(dsl: Ld_NamespaceBodyDsl): (R_Type) -> Rt_Value {
        dsl.struct("rec") {
            attribute("x", "integer?")
            attribute("y", "integer?", mutable = true)
        }

        return { rType ->
            val rStructType = (rType as R_NullableType).valueType as R_StructType
            Rt_StructValue(rStructType, mutableListOf(Rt_IntValue.get(123), Rt_IntValue.get(456)))
        }
    }

    @Test fun testVersionControlStructAttr() {
        initFooBar()

        chkVer("0.14.0") {
            chkEx("{ val f = foo_z(); return _type_of(f); }", "foo?")
            chkEx("{ val f = foo_z(); f!!; return _type_of(f); }", "foo")

            chkEx("{ val f = foo_nz(); return _type_of(f.x); }", "integer?")
            chkEx("{ val f = foo_nz(); f.x!!; return _type_of(f.x); }", it.iff("integer?", "integer"))
            chkEx("{ val f = foo_nz(); f.x!!; return abs(f.x); }",
                it.iff("ct_err:expr_call_badargs:[abs]:[integer?]", "123"))

            chkEx("{ val f = foo_nz(); return _type_of(f.y); }", "integer?")
            chkEx("{ val f = foo_nz(); f.y!!; return _type_of(f.y); }", "integer?")
            chkEx("{ val f = foo_nz(); f.y!!; return abs(f.y); }", "ct_err:expr_call_badargs:[abs]:[integer?]")

            chkEx("{ val f = foo_nz(); f.x!!; return f.x!!; }", "123")
            chkWarn(*it.iffArray("expr:smartnull:expr:never:[f.x]"))
        }
    }

    @Test fun testVersionControlSafeAccess() {
        initFooBar()

        chkVer("0.14.0") {
            chkEx("{ val f = foo_z(); return _type_of(f); }", "foo?")
            chkEx("{ val f = foo_z(); f?.x!!; return _type_of(f); }", it.iff("foo?", "foo"))

            chkEx("{ val f = foo_z(); f?.x!!; return f!!; }",
                "foo{x=123,y=456,bar=bar{r=321,s=654},mbar=bar{r=321,s=654}}")
            chkWarn(*it.iffArray("expr:smartnull:var:never:[f]"))
        }
    }

    @Test fun testVersionControlTupleAttr() {
        initFooBar()
        chkVer("0.14.0") {
            chkEx("{ val t = (foo_z(), 0); return _type_of(t[0]); }", "foo?")
            chkEx("{ val t = (foo_z(), 0); t[0]!!; return _type_of(t[0]); }", it.iff("foo?", "foo"))
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
}
