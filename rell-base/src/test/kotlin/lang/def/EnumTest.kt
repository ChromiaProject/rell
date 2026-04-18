/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class EnumTest: BaseRellTest() {
    @Test fun testBasics() {
        def("enum foo { A, B, C }")
        chk("foo.A", "foo[A]")
        chk("foo.B", "foo[B]")
        chk("foo.C", "foo[C]")
        chk("foo.X", "ct_err:unknown_member:[foo]:X")

        chk("foo", "ct_err:expr_novalue:enum:[foo]")
        chk("'' + foo", "ct_err:expr_novalue:enum:[foo]")
        chk("_type_of(foo.A)", "text[foo]")
        chk("_type_of(foo)", "ct_err:expr_novalue:enum:[foo]")
    }

    @Test fun testOperators() {
        def("enum foo { A, B, C }")
        chk("foo.A == foo.A", "boolean[true]")
        chk("foo.A == foo.B", "boolean[false]")
        chk("foo.A != foo.A", "boolean[false]")
        chk("foo.A != foo.B", "boolean[true]")
        chk("foo.A === foo.A", "ct_err:binop_operand_type:===:[foo]:[foo]")
        chk("foo.A !== foo.A", "ct_err:binop_operand_type:!==:[foo]:[foo]")
        chk("foo.A < foo.A", "boolean[false]")
        chk("foo.A < foo.B", "boolean[true]")
        chk("foo.A > foo.A", "boolean[false]")
        chk("foo.A > foo.B", "boolean[false]")
        chk("foo.B > foo.A", "boolean[true]")
        chk("foo.A + foo.A", "ct_err:binop_operand_type:+:[foo]:[foo]")
        chk("foo.A - foo.B", "ct_err:binop_operand_type:-:[foo]:[foo]")
        chk("foo.A + 1", "ct_err:binop_operand_type:+:[foo]:[integer]")
        chk("foo.A - 1", "ct_err:binop_operand_type:-:[foo]:[integer]")
        chk("foo.A + ''", "text[A]")
        chk("'' + foo.A", "text[A]")
    }

    @Test fun testMisc() {
        tst.ideDefIdConflictError = false

        chkCompile("enum foo { A, B, C, }", "OK")
        chkCompile("enum foo {}", "OK")

        chkCompile("enum foo { A, B, C, A }", "ct_err:enum_dup:A")

        chkCompile("enum foo {} enum foo {}", """ct_err:
            [name_conflict:user:foo:ENUM:main.rell(1:18)]
            [name_conflict:user:foo:ENUM:main.rell(1:6)]
        """)

        chkCompile("object foo {} enum foo {}", """ct_err:
            [name_conflict:user:foo:ENUM:main.rell(1:20)]
            [name_conflict:user:foo:OBJECT:main.rell(1:8)]
        """)

        chkCompile("entity foo {} enum foo {}", """ct_err:
            [name_conflict:user:foo:ENUM:main.rell(1:20)]
            [name_conflict:user:foo:ENTITY:main.rell(1:8)]
        """)

        chkCompile("struct foo {} enum foo {}", """ct_err:
            [name_conflict:user:foo:ENUM:main.rell(1:20)]
            [name_conflict:user:foo:STRUCT:main.rell(1:8)]
        """)

        chkCompile("enum foo {} object foo {}", """ct_err:
            [name_conflict:user:foo:OBJECT:main.rell(1:20)]
            [name_conflict:user:foo:ENUM:main.rell(1:6)]
        """)

        chkCompile("enum foo {} entity foo {}", """ct_err:
            [name_conflict:user:foo:ENTITY:main.rell(1:20)]
            [name_conflict:user:foo:ENUM:main.rell(1:6)]
        """)

        chkCompile("enum foo {} struct foo {}", """ct_err:
            [name_conflict:user:foo:STRUCT:main.rell(1:20)]
            [name_conflict:user:foo:ENUM:main.rell(1:6)]
        """)
    }

    @Test fun testTypeCompatibility() {
        def("enum foo { A, B, C }")
        def("enum bar { A, B, C }")
        chkEx(": foo = bar.A;", "ct_err:fn_rettype:[foo]:[bar]")
        chkEx(": bar = foo.A;", "ct_err:fn_rettype:[bar]:[foo]")
        chkEx(": foo = null;", "ct_err:fn_rettype:[foo]:[null]")
        chkEx(": foo = 0;", "ct_err:fn_rettype:[foo]:[integer]")
        chkEx(": foo = 1;", "ct_err:fn_rettype:[foo]:[integer]")
        chkEx(": foo = 'Hello';", "ct_err:fn_rettype:[foo]:[text]")
        chkEx(": foo = true;", "ct_err:fn_rettype:[foo]:[boolean]")
        chkEx(": foo? = foo.A;", "foo[A]")
        chkEx(": foo? = null;", "null")
    }

    @Test fun testEntityAttribute() {
        tstCtx.useSql = true
        def("enum foo { A, B, C }")
        def("entity cls { name; f: foo; }")
        def("object obj { mutable f: foo = foo.A; }")
        insert("c0.cls", "name,f", "0,'Bob',0")

        chk("cls @* {} ( _=.name, _=.f )", "list<(text,foo)>[(text[Bob],foo[A])]")
        chkData("cls(0,Bob,0)", "obj(0,0)")

        chkOp("create cls('Alice', foo.B);")
        chk("cls @* {} ( _=.name, _=.f )", "list<(text,foo)>[(text[Bob],foo[A]),(text[Alice],foo[B])]")
        chkData("cls(0,Bob,0)", "cls(1,Alice,1)", "obj(0,0)")

        chk("obj.f", "foo[A]")
        chkOp("update obj (f = foo.C);")
        chk("obj.f", "foo[C]")
        chkData("cls(0,Bob,0)", "cls(1,Alice,1)", "obj(0,2)")

        chk("cls @? { .f == foo.A }", "cls[0]")
        chk("cls @? { .f == foo.B }", "cls[1]")
        chk("cls @? { .f == foo.C }", "null")
    }

    @Test fun testNameConflicts() {
        tstCtx.useSql = true
        def("enum foo { A, B, C }")
        def("entity user { name: text; f: foo; }")
        insert("c0.user", "name,f", "0,'Bob',1")

        chkEx("{ return user @ {} ( _=.f ); }", "foo[B]")
        chkEx("{ val foo = 'Bob'; return user @? { .name == foo }; }", "user[0]")
        chkEx("{ val foo = 'Bob'; return user @? { .f == foo.B }; }", "ct_err:unknown_member:[text]:B")
        chkEx("{ val foo = 'Bob'; return foo; }", "text[Bob]")
        chkEx("{ val foo = 'Bob'; return foo.C; }", "ct_err:unknown_member:[text]:C")
    }

    @Test fun testStaticFunctions() {
        def("enum foo { A, B, C }")

        chk("foo.values()", "list<foo>[foo[A],foo[B],foo[C]]")

        chk("foo.value(0)", "foo[A]")
        chk("foo.value(1)", "foo[B]")
        chk("foo.value(2)", "foo[C]")
        chk("foo.value(3)", "rt_err:enum_badvalue:foo:3")

        chk("foo.value('A')", "foo[A]")
        chk("foo.value('B')", "foo[B]")
        chk("foo.value('C')", "foo[C]")
        chk("foo.value('D')", "rt_err:enum_badname:foo:D")

        chk("foo.value(true)", "ct_err:expr_call_badargs:[rell.enum_ext(foo).value]:[boolean]")
        chk("foo.value(null)", "ct_err:expr_call_badargs:[rell.enum_ext(foo).value]:[null]")
        chk("foo.value(foo.A)", "ct_err:expr_call_badargs:[rell.enum_ext(foo).value]:[foo]")
    }

    @Test fun testMemberProperties() {
        def("enum foo { A, B, C }")

        chk("foo.A.name", "text[A]")
        chk("foo.B.name", "text[B]")
        chk("foo.C.name", "text[C]")

        chk("foo.A.value", "int[0]")
        chk("foo.B.value", "int[1]")
        chk("foo.C.value", "int[2]")

        chkEx("{ foo.A.name = 'X'; return 0; }", "ct_err:attr_not_mutable:rell.enum_ext(foo).name")
        chkEx("{ foo.A.value = 123; return 0; }", "ct_err:attr_not_mutable:rell.enum_ext(foo).value")
        chkEx("{ val v = foo.A; v.name = 'X'; return 0; }", "ct_err:attr_not_mutable:rell.enum_ext(foo).name")
        chkEx("{ val v = foo.A; v.value = 123; return 0; }", "ct_err:attr_not_mutable:rell.enum_ext(foo).value")
    }

    @Test fun testMemberPropertiesAt() {
        tstCtx.useSql = true
        def("enum foo { A, B, C }")
        def("entity user { name; foo; }")
        insert("c0.user", "name,foo", "1,'Bob',0")
        insert("c0.user", "name,foo", "2,'Alice',1")
        insert("c0.user", "name,foo", "3,'Trudy',2")

        chk("user @ { 'Bob' } ( .foo.value )", "int[0]")
        chk("user @ { 'Alice' } ( .foo.value )", "int[1]")
        chk("user @ { 'Trudy' } ( .foo.value )", "int[2]")
        chk("user @ { 'Alice' } ( _=.name, _=.foo, _=.foo.value )", "(text[Alice],foo[B],int[1])")

        chk("user @ { .foo.value == 0 } ( .name )", "text[Bob]")
        chk("user @ { .foo.value == 1 } ( .name )", "text[Alice]")
        chk("user @ { .foo.value == 2 } ( .name )", "text[Trudy]")

        chk("user @ { 'Bob' } ( .foo.name )", "text[A]")
    }

    @Test fun testNullable() {
        def("enum foo { A, B, C }")
        def("function nop(x: foo?): foo? = x;")

        chkEx("{ val f: foo = foo.A; return f.name; }", "text[A]")
        chkEx("{ val f: foo = foo.A; return f?.name; }", "ct_err:expr_safemem_type:[foo]:name")
        chkEx("{ val f: foo = foo.A; return f!!.name; }", "ct_err:unop_operand_type:!!:[foo]")
        chkEx("{ val f: foo = foo.A; return f ?: foo.C; }", "ct_err:binop_operand_type:?::[foo]:[foo]")

        chkEx("{ val f: foo? = nop(foo.A); return f.name; }", "ct_err:expr_mem_null:foo?:name")
        chkEx("{ val f: foo? = nop(foo.A); return f?.name; }", "text[A]")
        chkEx("{ val f: foo? = nop(foo.A); return f!!.name; }", "text[A]")
        chkEx("{ val f: foo? = nop(foo.A); return f ?: foo.C; }", "foo[A]")

        chkEx("{ val f: foo? = nop(null); return f?.name; }", "null")
        chkEx("{ val f: foo? = nop(null); return f!!.name; }", "rt_err:null_value")
        chkEx("{ val f: foo? = nop(null); return f ?: foo.C; }", "foo[C]")

        chkEx("{ val f: foo? = nop(foo.A); return _type_of(f?.name); }", "text[text?]")
        chkEx("{ val f: foo? = nop(foo.A); return _type_of(f?.value); }", "text[integer?]")
    }

    @Test fun testValueFunctionConflict() {
        def("enum foo { a, value }")
        def("enum bar { b, values }")
        chk("foo.value", "foo[value]")
        chk("foo.value(0)", "foo[a]")
        chk("bar.values", "bar[values]")
        chk("bar.values()", "list<bar>[bar[b],bar[values]]")
    }

    @Test fun testEmptyComma() {
        chkCompile("enum foo {}", "OK")
        chkCompile("enum foo {A,}", "OK")
        chkCompile("enum foo {,}", "ct_err:syntax")
    }
}
