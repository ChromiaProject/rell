/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.utils.RellVersions
import org.junit.Test
import kotlin.test.assertEquals

class StructTest: BaseRellTest() {
    @Test fun testGeneral() {
        def("struct foo { x: integer; s: text; }")
        chkEx("{ val r = foo(x = 123, s = 'Hello'); return r.x; }", "int[123]")
        chkEx("{ val r = foo(x = 123, s = 'Hello'); return r.s; }", "text[Hello]")
        chkEx("{ return foo(x = 123, s = 'Hello'); }", "foo[x=int[123],s=text[Hello]]")
    }

    @Test fun testConstruct() {
        def("struct foo { i: integer; s: text; q: text = 'Unknown'; t: (integer, text); }")

        chkEx("{ return foo(i = 123, s = 'Hello', q = 'Foo', t = (456, 'Bye')); }",
                "foo[i=int[123],s=text[Hello],q=text[Foo],t=(int[456],text[Bye])]")

        chkEx("{ return foo(i = 123, s = 'Hello', t = (456, 'Bye')); }",
                "foo[i=int[123],s=text[Hello],q=text[Unknown],t=(int[456],text[Bye])]")

        chkEx("{ return foo(i = 123, s = 'Hello'); }", "ct_err:attr_missing:[foo]:t")

        chkEx("{ return foo(i = 123, s = 'Hello', t = 'WrongType'); }",
                "ct_err:attr_bad_type:2:t:(integer,text):text")
    }

    @Test fun testConstructResolveAttrByName() {
        def("struct foo { i: integer; s: text; q: text = 'Unknown'; }")

        chkEx("{ val s = 'Hello'; val q = 'Bye'; return foo(i = 123, q, s); }",
                "foo[i=int[123],s=text[Hello],q=text[Bye]]")

        chkEx("{ val s = 'Hello'; return foo(i = 123, s); }", "foo[i=int[123],s=text[Hello],q=text[Unknown]]")
        chkEx("{ val q = 'Bye'; return foo(i = 123, q); }", "ct_err:attr_missing:[foo]:s")

        chkEx("{ val s = 123; return foo(i = 123, s); }", "ct_err:attr_bad_type:1:s:text:integer")
    }

    @Test fun testConstructResolveAttrByType() {
        def("struct foo { i: integer; s: text; q: text = 'Unknown'; }")
        chkEx("{ val v = 123; return foo(v, s = 'Hello'); }", "foo[i=int[123],s=text[Hello],q=text[Unknown]]")
        chkEx("{ val v = 'Hello'; return foo(i = 123, v); }", "ct_err:attr_implic_multi:1:s,q")
    }

    @Test fun testConstructNoArgs() {
        def("struct foo { x: integer = 123; } struct bar { f: foo; }")
        chk("foo()", "foo[x=int[123]]")
        chk("bar(foo())", "bar[f=foo[x=int[123]]]")
    }

    @Test fun testStructVsFunctionNameConflict() {
        chkCompile("function foo(x: integer){} struct foo {s:text;}", """ct_err:
            [name_conflict:user:foo:STRUCT:main.rell(1:35)]
            [name_conflict:user:foo:FUNCTION:main.rell(1:10)]
        """)

        chkCompile("struct foo {s:text;} function foo(x: integer){}", """ct_err:
            [name_conflict:user:foo:FUNCTION:main.rell(1:31)]
            [name_conflict:user:foo:STRUCT:main.rell(1:8)]
        """)
    }

    @Test fun testStructVsLocalNameConflict() {
        def("struct data {}")
        chkFull("function f(data: integer) = data; query q() = f(123);", "int[123]")
        chkFull("function f(data: integer) = data(); query q() = f(123);", "data[]")
        chkFull("function f(data: () -> integer) = data(); query q() = f(integer.from_hex('7b', *));", "int[123]")
        chkFull("namespace ns { val data = 123; function f() = data; } query q() = ns.f();", "int[123]")
        chkFull("namespace ns { val data = 123; function f() = data(); } query q() = ns.f();", "data[]")
    }

    @Test fun testMutableAttributes() {
        def("struct foo { mutable a: integer; b: integer; }")
        chkEx("{ val r = foo(a = 123, b = 456); return r; }", "foo[a=int[123],b=int[456]]")
        chkEx("{ val r = foo(a = 123, b = 456); r.a = 789; return r; }", "foo[a=int[789],b=int[456]]")
        chkEx("{ val r = foo(a = 123, b = 456); r.b = 789; return r; }", "ct_err:attr_not_mutable:foo.b")
        chkEx("{ val r = foo(a = 123, b = 456); r.b += 789; return r; }", "ct_err:attr_not_mutable:foo.b")
    }

    @Test fun testAttributeTypeNullable() {
        def("struct foo { a: integer?; b: text?; }")
        chk("foo(a = 123, b = 'Hello')", "foo[a=int[123],b=text[Hello]]")
        chk("foo(a = null, b = 'Hello')", "foo[a=null,b=text[Hello]]")
        chk("foo(a = 123, b = null)", "foo[a=int[123],b=null]")
        chk("foo(a = null, b = null)", "foo[a=null,b=null]")
    }

    @Test fun testAttributeTypeTuple() {
        def("struct foo { a: (integer, text); }")
        chk("foo((123, 'Hello'))", "foo[a=(int[123],text[Hello])]")
    }

    @Test fun testAttributeTypeCollection() {
        def("struct foo { l: list<integer>; s: set<text>; m: map<integer, text>; }")
        chk("foo([123], set(['Hello']), [123:'Hello'])",
                "foo[l=list<integer>[int[123]],s=set<text>[text[Hello]],m=map<integer,text>[int[123]=text[Hello]]]")
    }

    @Test fun testTypeCompatibility() {
        def("struct foo { x: integer; } struct bar { x: integer; }")
        chkEx("{ val r: foo = foo(123); return r; }", "foo[x=int[123]]")
        chkEx("{ val r: bar = bar(123); return r; }", "bar[x=int[123]]")
        chkEx("{ val r: foo = bar(123); return 0; }", "ct_err:stmt_var_type:r:[foo]:[bar]")
        chkEx("{ val r: bar = foo(123); return 0; }", "ct_err:stmt_var_type:r:[bar]:[foo]")
    }

    @Test fun testStructAsEntityAttributeType() {
        chkCompile("struct foo { x: integer; } entity bar { foo; }", "ct_err:entity_attr_type:foo:foo")
    }

    @Test fun testAttributeOfNullableStruct() {
        def("struct foo { mutable x: integer; }")
        def("function nop(x: foo?): foo? = x;")

        chkEx("{ val r: foo? = nop(foo(123)); return r.x; }", "ct_err:expr_mem_null:foo?:x")
        chkEx("{ val r: foo? = nop(foo(123)); return r!!.x; }", "int[123]")
        chkEx("{ val r: foo? = nop(null); return r!!.x; }", "rt_err:null_value")
        chkEx("{ val r: foo? = nop(foo(123)); return r?.x; }", "int[123]")
        chkEx("{ val r: foo? = nop(null); return r?.x; }", "null")

        chkEx("{ val r: foo? = nop(foo(123)); r.x = 456; return r; }", "ct_err:expr_mem_null:foo?:x")

        chkEx("{ val r: foo? = nop(foo(123)); r!!.x = 456; return r; }", "foo[x=int[456]]")
        chkEx("{ val r: foo? = nop(null); r!!.x = 456; return r; }", "rt_err:null_value")
        chkEx("{ val r: foo? = nop(foo(123)); r!!.x += 456; return r; }", "foo[x=int[579]]")
        chkEx("{ val r: foo? = nop(null); r!!.x += 456; return r; }", "rt_err:null_value")

        chkEx("{ val r: foo? = nop(foo(123)); r?.x = 456; return r; }", "foo[x=int[456]]")
        chkEx("{ val r: foo? = nop(null); r?.x = 456; return r; }", "null")
        chkEx("{ val r: foo? = nop(foo(123)); r?.x += 456; return r; }", "foo[x=int[579]]")
        chkEx("{ val r: foo? = nop(null); r?.x += 456; return r; }", "null")

        chkEx("{ val r: foo? = nop(foo(123)); r!!.x = 456; return r; }", "foo[x=int[456]]")
        chkEx("{ val r: foo? = nop(null); r!!.x = 456; return r; }", "rt_err:null_value")
    }

    @Test fun testConstructUnderAt() {
        tstCtx.useSql = true
        def("entity user { name; value: integer; } struct foo { x: integer; }")
        chkOp("create user('Bob', 123); create user('Alice', 456);")
        chk("user @ { .value == foo(x = 123).x }(.name)", "text[Bob]")
        chk("user @ { .value == foo(x = 456).x }(.name)", "text[Alice]")
    }

    @Test fun testConstructEvaluationOrder() {
        def("function f(v: text) { print(v); return v; }")
        def("struct s { a: text = f('a0'); b: text; c: text = f('c0'); d: text; e: text = f('e0'); }")

        chk("s(b = f('b'), d = f('d'))", "s[a=text[a0],b=text[b],c=text[c0],d=text[d],e=text[e0]]")
        chkOut("b", "d", "a0", "c0", "e0")

        chk("s(d = f('d'), b = f('b'))", "s[a=text[a0],b=text[b],c=text[c0],d=text[d],e=text[e0]]")
        chkOut("d", "b", "a0", "c0", "e0")

        chk("s(e = f('e'), b = f('b'), a = f('a'), d = f('d'))", "s[a=text[a],b=text[b],c=text[c0],d=text[d],e=text[e]]")
        chkOut("e", "b", "a", "d", "c0")
    }

    @Test fun testConstructEvaluationOrderUnderAt() {
        tstCtx.useSql = true
        def("function f(v: text) { print(v); return v; }")
        def("struct s { a: text = f('a0'); b: text; c: text = f('c0'); d: text; e: text = f('e0'); }")
        def("entity data { a: text; b: text; c: text; d: text; e: text;  }")
        insert("c0.data", "a,b,c,d,e", "100,'A','B','C','D','E'")

        chk("data @ {} ( s(a = f(.a), b = f(.b), c = f(.c), d = f(.d), e = f(.e)) )",
                "s[a=text[A],b=text[B],c=text[C],d=text[D],e=text[E]]")
        chkOut("A", "B", "C", "D", "E")

        chk("data @ {} ( s(b = f(.b), d = f(.d)) )", "s[a=text[a0],b=text[B],c=text[c0],d=text[D],e=text[e0]]")
        chkOut("B", "D", "a0", "c0", "e0")

        chk("data @ {} ( s(d = f(.d), b = f(.b)) )", "s[a=text[a0],b=text[B],c=text[c0],d=text[D],e=text[e0]]")
        chkOut("D", "B", "a0", "c0", "e0")

        chk("data @ {} ( s(e = f(.e), b = f(.b), a = f(.a), d = f(.d)) )",
                "s[a=text[A],b=text[B],c=text[c0],d=text[D],e=text[E]]")
        chkOut("E", "B", "A", "D", "c0")
    }

    @Test fun testAttributeOfNullableStruct2() {
        def("struct foo { b: bar?; } struct bar { mutable x: integer; }")
        chkEx("{ val r: foo? = _nullable(foo(bar(123))); return r?.b?.x; }", "int[123]")
        chkEx("{ val r: foo? = _nullable(foo(null)); return r?.b?.x; }", "null")
        chkEx("{ val r: foo? = null; return r?.b?.x; }", "null")

        chkEx("{ val r: foo? = _nullable(foo(bar(123))); r?.b?.x = 456; return r; }", "foo[b=bar[x=int[456]]]")
        chkEx("{ val r: foo? = _nullable(foo(null)); r?.b?.x = 456; return r; }", "foo[b=null]")
        chkEx("{ val r: foo? = null; r?.b?.x = 456; return r; }", "null")

        chkEx("{ val r: foo? = _nullable(foo(bar(123))); r?.b?.x += 456; return r; }", "foo[b=bar[x=int[579]]]")
        chkEx("{ val r: foo? = _nullable(foo(null)); r?.b?.x += 456; return r; }", "foo[b=null]")
        chkEx("{ val r: foo? = null; r?.b?.x += 456; return r; }", "null")
    }

    @Test fun testAccessUnderAt() {
        tstCtx.useSql = true
        def("entity user { name; value: integer; } struct foo { x: integer; }")
        chkOp("create user('Bob', 123); create user('Alice', 456);")
        chkEx("{ var r = foo(123); return user @ { .value == r.x }(.name); }", "text[Bob]")
        chkEx("{ var r = foo(456); return user @ { .value == r.x }(.name); }", "text[Alice]")
    }

    @Test fun testStructFlags() {
        chkFlags("struct foo { x: integer; }", "foo[gtv]")
        chkFlags("struct foo { mutable x: integer; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: integer; next: foo?; }", "foo[gtv,cyc,inf]")
        chkFlags("struct foo { mutable x: integer; next: foo?; }", "foo[mut,gtv,cyc,inf]")
        chkFlags("struct foo { x: virtual<list<integer>>; }", "foo[from_gtv]")

        chkFlags("struct foo { x: integer; }", "foo[gtv]")
        chkFlags("struct foo { x: integer?; }", "foo[gtv]")
        chkFlags("struct foo { x: text; }", "foo[gtv]")
        chkFlags("struct foo { x: byte_array; }", "foo[gtv]")
        chkFlags("struct foo { x: boolean; }", "foo[gtv]")
        chkFlags("struct foo { x: (x: integer, text); }", "foo[gtv]")
        chkFlags("struct foo { x: range; }", "foo[]")
        chkFlags("entity user { name; } struct foo { x: user; }", "foo[gtv]")
        chkFlags("struct foo { x: list<integer>; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: set<integer>; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: map<integer,text>; }", "foo[mut,gtv]")

        chkFlags("struct bar { x: integer; } struct foo { y: bar; }", "bar[gtv],foo[gtv]")
        chkFlags("struct bar { x: integer; } struct foo { mutable y: bar; }", "bar[gtv],foo[mut,gtv]")
        chkFlags("struct bar { mutable x: integer; } struct foo { y: bar; }", "bar[mut,gtv],foo[mut,gtv]")
        chkFlags("struct bar { mutable x: integer?; } struct foo { y: bar; }", "bar[mut,gtv],foo[mut,gtv]")

        chkFlags("struct bar { x: foo?; } struct foo { y: bar?; }", "bar[gtv,cyc,inf],foo[gtv,cyc,inf]")
        chkFlags("struct bar { mutable x: foo?; } struct foo { y: bar?; }", "bar[mut,gtv,cyc,inf],foo[mut,gtv,cyc,inf]")
        chkFlags("struct bar { x: foo?; } struct foo { mutable y: bar?; }", "bar[mut,gtv,cyc,inf],foo[mut,gtv,cyc,inf]")

        chkFlags("struct bar { x: bar?; } struct foo { y: bar?; }", "bar[gtv,cyc,inf],foo[gtv,inf]")
        chkFlags("struct bar { mutable x: bar?; } struct foo { y: bar?; }", "bar[mut,gtv,cyc,inf],foo[mut,gtv,inf]")
        chkFlags("struct bar { x: bar?; } struct foo { mutable y: bar?; }", "bar[gtv,cyc,inf],foo[mut,gtv,inf]")

        chkFlags("struct bar { x: foo?; } struct foo { y: (q: boolean, list<map<text,bar>>); }",
                "bar[mut,gtv,cyc,inf],foo[mut,gtv,cyc,inf]")
        chkFlags("struct bar { x: integer; } struct foo { y: (q: boolean, list<map<text,bar>>); }",
                "bar[gtv],foo[mut,gtv]")
    }

    @Test fun testStructFlagsGtv() {
        chkFlags("struct foo { x: integer; }", "foo[gtv]")
        chkFlags("struct foo { x: (a: text, b: integer); }", "foo[gtv]")
        chkFlags("struct foo { x: (a: integer, b: text); }", "foo[gtv]")
        chkFlags("struct foo { x: (x: text, integer); }", "foo[gtv]")

        chkFlags("struct foo { x: map<text,integer>; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: map<integer,text>; }", "foo[mut,gtv]")

        chkFlags("struct foo { x: list<set<(a: text, b: integer)>>; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: list<set<(q: text, integer)>>; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: list<map<text,integer>>; }", "foo[mut,gtv]")
        chkFlags("struct foo { x: list<map<integer,text>>; }", "foo[mut,gtv]")

        chkFlags("struct foo { x: (a:text,b:integer); } struct bar { mutable y: range; }", "bar[mut],foo[gtv]")
        chkFlags("struct foo { x: (a:text,b:integer); p: bar; } struct bar { mutable y: range; }", "bar[mut],foo[mut]")
        chkFlags("struct foo { x: (a:text,b:integer); } struct bar { mutable y: range; q: foo; }", "bar[mut],foo[gtv]")
        chkFlags("struct foo { x: (a:text,b:integer); p: bar; } struct bar { mutable y: range; q: foo; }",
                "bar[mut,cyc,inf],foo[mut,cyc,inf]")

        chkFlags("struct foo { x: (t:text,integer); } struct bar { mutable y: range; }", "bar[mut],foo[gtv]")
        chkFlags("struct foo { x: (t:text,integer); p: bar; } struct bar { mutable y: range; }", "bar[mut],foo[mut]")
        chkFlags("struct foo { x: (t:text,integer); } struct bar { mutable y: range; q: foo; }", "bar[mut],foo[gtv]")
        chkFlags("struct foo { x: (t:text,integer); p: bar; } struct bar { mutable y: range; q: foo; }",
                "bar[mut,cyc,inf],foo[mut,cyc,inf]")
    }

    private fun chkFlags(code: String, expected: String) {
        val actual = tst.processApp(code) { app ->
            val lst = mutableListOf<String>()
            val structDefs = app.rApp.modules.flatMap { it.structs.values }
            for (structDef in structDefs.sortedBy { it.simpleName }) {
                val struct = structDef.struct

                val flags = mutableListOf<String>()
                if (struct.flags.typeFlags.mutable) flags.add("mut")

                val gtv = struct.flags.typeFlags.gtv
                if (gtv.fromGtv && gtv.toGtv) flags.add("gtv") else {
                    if (gtv.fromGtv) flags.add("from_gtv")
                    if (gtv.toGtv) flags.add("to_gtv")
                }

                if (struct.flags.cyclic) flags.add("cyc")
                if (struct.flags.infinite) flags.add("inf")
                lst.add("${structDef.simpleName}[${flags.joinToString(",")}]")
            }
            lst.joinToString(",")
        }
        assertEquals(expected, actual)
    }

    @Test fun testMutableStructAsMapSetKey() {
        def("struct foo { mutable x: integer; }")
        chk("set<foo>()", "ct_err:[param_bounds:set:T:-immutable:foo][param_bounds:set:T:-immutable:foo]")
        chk("map<foo,text>()", "ct_err:[param_bounds:map:K:-immutable:foo][param_bounds:map:K:-immutable:foo]")
        chk("map<text,foo>()", "map<text,foo>[]")
        chk("[ foo(123) : 'Hello' ]", "ct_err:expr_map_keytype:foo")
        chk("[ 'Hello' : foo(123) ]", "map<text,foo>[text[Hello]=foo[x=int[123]]]")
        chk("set([foo(123)])", "ct_err:expr_call_badargs:[set]:[list<foo>]")
        chk("[foo(123)]", "list<foo>[foo[x=int[123]]]")
    }

    @Test fun testStructAsMapSetKeySimple() {
        def("struct foo { x: integer; }")

        chkEx("{ var s = set([foo(123)]); return s; }", "set<foo>[foo[x=int[123]]]")
        chkEx("{ var s = set([foo(123)]); return s.contains(foo(123)); }", "boolean[true]")
        chkEx("{ var s = set([foo(123)]); return s.contains(foo(456)); }", "boolean[false]")

        chkEx("{ var m = [foo(123) : 'Hello']; return m; }", "map<foo,text>[foo[x=int[123]]=text[Hello]]")
        chkEx("{ var m = [foo(123) : 'Hello']; return m[foo(123)]; }", "text[Hello]")
        chkEx("{ var m = [foo(123) : 'Hello']; return m[foo(456)]; }", "rt_err:fn_map_get_novalue:foo[x=int[456]]")
    }

    @Test fun testStructAsMapSetKeyComplex() {
        def("struct foo { x: text; b: bar; } struct bar { p: integer; q: integer; }")

        chkEx("{ var s = set([foo('ABC', bar(p=123,q=456))]); return s; }",
                "set<foo>[foo[x=text[ABC],b=bar[p=int[123],q=int[456]]]]")
        chkEx("{ var s = set([foo('ABC', bar(p=123,q=456))]); return s.contains(foo('ABC',bar(p=123,q=456))); }",
                "boolean[true]")
        chkEx("{ var s = set([foo('ABC', bar(p=123,q=456))]); return s.contains(foo('ABC',bar(p=123,q=789))); }",
                "boolean[false]")
        chkEx("{ var s = set([foo('ABC', bar(p=123,q=456))]); return s.contains(foo('DEF',bar(p=123,q=456))); }",
                "boolean[false]")

        chkEx("{ var m = [foo('ABC', bar(p=123,q=456)) : 'X']; return m; }",
                "map<foo,text>[foo[x=text[ABC],b=bar[p=int[123],q=int[456]]]=text[X]]")
        chkEx("{ var m = [foo('ABC', bar(p=123,q=456)) : 'X']; return m[foo('ABC', bar(p=123,q=456))]; }",
                "text[X]")
        chkEx("{ var m = [foo('ABC', bar(p=123,q=456)) : 'X']; return m[foo('ABC', bar(p=123,q=789))]; }",
                "rt_err:fn_map_get_novalue:foo[x=text[ABC],b=bar[p=int[123],q=int[789]]]")
        chkEx("{ var m = [foo('ABC', bar(p=123,q=456)) : 'X']; return m[foo('DEF', bar(p=123,q=456))]; }",
                "rt_err:fn_map_get_novalue:foo[x=text[DEF],b=bar[p=int[123],q=int[456]]]")
    }

    @Test fun testStructAsMapSetKeyRecursive() {
        chkCompile("""
            struct foo { a: set<bar>; }
            struct bar { b: set<foo>; }
        """, "ct_err:[param_bounds:set:T:-immutable:bar][param_bounds:set:T:-immutable:foo]")
    }

    @Test fun testEqSimple() {
        def("struct foo { x: integer; y: text; } struct bar { x: integer; y: text; }")

        chkEx("{ val f = foo(123, 'Hello'); return f == foo(123, 'Hello'); }", "boolean[true]")
        chkEx("{ val f = foo(123, 'Hello'); return f != foo(123, 'Hello'); }", "boolean[false]")
        chkEx("{ val f = foo(123, 'Hello'); return f == foo(456, 'Hello'); }", "boolean[false]")
        chkEx("{ val f = foo(123, 'Hello'); return f != foo(456, 'Hello'); }", "boolean[true]")
        chkEx("{ val f = foo(123, 'Hello'); return f == foo(123, 'Bye'); }", "boolean[false]")
        chkEx("{ val f = foo(123, 'Hello'); return f != foo(123, 'Bye'); }", "boolean[true]")

        chkEx("{ val f = foo(123, 'Hello'); return f == bar(123, 'Hello'); }", "ct_err:binop_operand_type:==:[foo]:[bar]")
        chkEx("{ val f = foo(123, 'Hello'); return f != bar(123, 'Hello'); }", "ct_err:binop_operand_type:!=:[foo]:[bar]")
    }

    @Test fun testEqComplex() {
        def("struct foo { x: integer; b: list<bar>; } struct bar { s: list<text>; q: boolean; }")

        chkEx("{ val f = foo(123, [bar(['Hello'], true)]); return f == foo(123, [bar(['Hello'], true)]); }",
                "boolean[true]")

        chkEx("""{ val l = ['Hello']; val f = foo(123, [bar(l, true)]);
                l.add('Bye');
                return f == foo(123, [bar(['Hello'], true)]); }""",
                "boolean[false]")

        chkEx("""{ val l = ['Hello']; val f = foo(123, [bar(l, true)]);
                l.add('Bye');
                return f == foo(123, [bar(['Hello', 'Bye'], true)]); }""",
                "boolean[true]")
    }

    @Test fun testEqTree() {
        def("struct node { left: node?; right: node?; value: integer; }")

        chkEx("""{
            val p = node(
                left = node(left = null, right = null, 123),
                456,
                right = node(left = null, right = null, 789)
            );
            val q = node(
                right = node(left = null, right = null, 789),
                456,
                left = node(left = null, right = null, 123)
            );
            return p == q;
        }""", "boolean[true]")

        chkEx("""{
            val p = node(
                left = node(left = null, right = null, 123),
                456,
                right = node(left = null, right = null, 789)
            );
            val q = node(
                right = node(left = null, right = null, 123),
                456,
                left = node(left = null, right = null, 789)
            );
            return p == q;
        }""", "boolean[false]")
    }

    @Test fun testRefEq() {
        def("struct foo { a: integer; b: text; }")

        chkEx("{ val x = foo(123, 'Hello'); val y = foo(123, 'Hello'); return x == y; }", "boolean[true]")
        chkEx("{ val x = foo(123, 'Hello'); val y = foo(123, 'Hello'); return x != y; }", "boolean[false]")
        chkEx("{ val x = foo(123, 'Hello'); val y = foo(123, 'Hello'); return x === y; }", "boolean[false]")
        chkEx("{ val x = foo(123, 'Hello'); val y = foo(123, 'Hello'); return x !== y; }", "boolean[true]")
        chkEx("{ val x = foo(123, 'Hello'); val y = x; return x === y; }", "boolean[true]")
        chkEx("{ val x = foo(123, 'Hello'); val y = x; return x !== y; }", "boolean[false]")
    }

    @Test fun testToString() {
        def("struct foo { a: integer; b: bar; } struct bar { s: text; }")
        chk("'' + foo(123, bar('Hello'))", "text[foo{a=123,b=bar{s=Hello}}]")
    }

    @Test fun testFunctionNamedArgument() {
        def("function foo(x: integer): integer = x * x;")
        chk("foo(x = 123)", "int[15129]")
    }

    @Test fun testCircularReference() {
        val code = """
            struct node { value: integer; mutable next: node?; }

            function list_to_chain(values: list<integer>): node? {
                var first: node? = null;
                var last: node? = null;
                for (value in values) {
                    val node = node(value, next = null);
                    if (last == null) {
                        first = node;
                        last = node;
                    } else {
                        last.next = node;
                        last = node;
                    }
                }
                return first;
            }

            function chain_to_list(chain: node?): list<integer> {
                val res = list<integer>();
                var ptr = chain;
                while (ptr != null) {
                    res.add(ptr.value);
                    ptr = ptr.next;
                }
                return res;
            }

            query q() {
                val chain = list_to_chain([123, 456, 789]);
                return chain_to_list(chain);
            }
        """

        chkFull(code, "list<integer>[int[123],int[456],int[789]]")
    }

    @Test fun testAttributeDefaultValueTypePromotion() {
        def("struct s { x: decimal = 123; }")
        chk("s()", "s[x=dec[123]]")
        chk("s(456)", "ct_err:attr_implic_unknown:0:integer") // maybe not right
        chk("s(x = 456)", "s[x=dec[456]]")
        chk("s(456.0)", "s[x=dec[456]]")
        chk("s(x = 456.0)", "s[x=dec[456]]")
    }

    @Test fun testRecursiveToString() {
        def("struct s { a: integer; mutable next: s?; }")
        def("""
            function f(): s {
                val s1 = s(a = 123, next = null);
                val s2 = s(a = 456, next = s1);
                s1.next = s2;
                return s1;
            }
        """)

        chk("'' + f()", "text[s{a=123,next=s{a=456,next=s{...}}}]")
        chk("_strict_str(f())", "text[s[a=int[123],next=s[a=int[456],next=s[...]]]]")
    }

    @Test fun testBugConstructorAttrResolution() {
        def("struct s { interface: name; name; }")
        chkEx("{ val interface = 'foo'; return s(interface, name = 'bar'); }", "s[interface=text[foo],name=text[bar]]")
        chkFull("val interface = 'foo'; query q() = s(interface, name = 'bar');", "s[interface=text[foo],name=text[bar]]")
    }

    @Test fun testFromGtvDefaultValuesArray() {
        def("struct s { x: integer; y: text = 'hi'; z: boolean = true; }")
        def("struct s2 { a: text; b: integer = 123; c: decimal; }")

        chkFromGtv("s.from_gtv", """[123,"hello",false]""", "s[x=int[123],y=text[hello],z=boolean[false]]")
        chkFromGtv("s.from_gtv", """[123,"hello"]""", "s[x=int[123],y=text[hello],z=boolean[true]]")
        chkFromGtv("s.from_gtv", """[123]""", "s[x=int[123],y=text[hi],z=boolean[true]]")
        chkFromGtv("s.from_gtv", """[]""", "gtv_err:struct_size:s:1:3:0")

        chkFromGtv("s2.from_gtv", """["hello",456,"12.34"]""", "s2[a=text[hello],b=int[456],c=dec[12.34]]")
        chkFromGtv("s2.from_gtv", """["hello",456]""", "gtv_err:struct_size:s2:3:3:2")
        chkFromGtv("s2.from_gtv", """["hello"]""", "gtv_err:struct_size:s2:3:3:1")
        chkFromGtv("s2.from_gtv", """[]""", "gtv_err:struct_size:s2:3:3:0")
    }

    @Test fun testFromGtvDefaultValuesDict() {
        def("struct s { x: integer; y: text = 'hi'; z: boolean = true; }")
        def("struct s2 { a: text; b: integer = 123; c: decimal; }")

        val f = "from_gtv_pretty"
        chkFromGtv("s.$f", """{"x":123,"y":"hello","z":false}""", "s[x=int[123],y=text[hello],z=boolean[false]]")
        chkFromGtv("s.$f", """{"x":123,"y":"hello"}""", "s[x=int[123],y=text[hello],z=boolean[true]]")
        chkFromGtv("s.$f", """{"x":123,"z":false}""", "s[x=int[123],y=text[hi],z=boolean[false]]")
        chkFromGtv("s.$f", """{"x":123}""", "s[x=int[123],y=text[hi],z=boolean[true]]")
        chkFromGtv("s.$f", """{"y":"hello","z":false}""", "gtv_err:struct_noattr:s:x")
        chkFromGtv("s.$f", """{"z":false}""", "gtv_err:struct_noattr:s:x")
        chkFromGtv("s.$f", """{"y":"hello"}""", "gtv_err:struct_noattr:s:x")
        chkFromGtv("s.$f", """{}""", "gtv_err:struct_noattr:s:x")

        chkFromGtv("s2.$f", """{"a":"hello","b":456,"c":"12.34"}""", "s2[a=text[hello],b=int[456],c=dec[12.34]]")
        chkFromGtv("s2.$f", """{"a":"hello","c":"12.34"}""", "s2[a=text[hello],b=int[123],c=dec[12.34]]")
        chkFromGtv("s2.$f", """{"a":"hello","b":456}""", "gtv_err:struct_noattr:s2:c")
        chkFromGtv("s2.$f", """{"b":456,"c":"12.34"}""", "gtv_err:struct_noattr:s2:a")
        chkFromGtv("s2.$f", """{"b":456}""", "gtv_err:struct_noattr:s2:a")
        chkFromGtv("s2.$f", """{}""", "gtv_err:struct_noattr:s2:a")
    }

    @Test fun testFromGtvDefaultValuesVersionControl() {
        def("struct s { x: integer; y: text = 'hi'; z: boolean = true; }")
        chkFromGtv("s.from_gtv", """[123]""", "s[x=int[123],y=text[hi],z=boolean[true]]")
        chkFromGtv("s.from_gtv_pretty", """{"x":123}""", "s[x=int[123],y=text[hi],z=boolean[true]]")

        tst.compatibilityVer("0.14.12")
        chkFromGtv("s.from_gtv", """[123]""", "s[x=int[123],y=text[hi],z=boolean[true]]")
        chkFromGtv("s.from_gtv_pretty", """{"x":123}""", "s[x=int[123],y=text[hi],z=boolean[true]]")

        tst.compatibilityVer("0.14.11")
        chkFromGtv("s.from_gtv", """[123]""", "gtv_err:struct_noattr:s:y")
        chkFromGtv("s.from_gtv_pretty", """{"x":123}""", "gtv_err:struct_noattr:s:y")
    }

    private fun chkFromGtv(f: String, json: String, exp: String) {
        chk("""$f(gtv.from_json('$json'))""", exp)
    }
}
