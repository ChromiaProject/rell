/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.def

import net.postchain.rell.base.testutils.BaseRellTest
import org.junit.Test

class DeprecatedAnnotationTest: BaseRellTest(useSql = false) {
    @Test fun testAnnotationVisibility() {
        tst.hiddenLib = false
        chkFull("@deprecated function f() = 123; query q() = f();", "ct_err:modifier:invalid:ann:deprecated")
        tst.hiddenLib = true
        chkFull("@deprecated function f() = 123; query q() = f();", "ct_err:deprecated:FUNCTION:[f]")
    }

    @Test fun testDefConstant() {
        def("val A = 123;")
        def("@deprecated val B = 456;")
        chk("A", "int[123]")
        chk("B", "ct_err:deprecated:CONSTANT:[B]")
        chk("B.to_text()", "ct_err:deprecated:CONSTANT:[B]")
        chk("987 * B", "ct_err:deprecated:CONSTANT:[B]")
        chk("B(123)", "ct_err:[deprecated:CONSTANT:[B]][expr_call_nofn:integer]")
    }

    @Test fun testDefEntity() {
        tstCtx.useSql = true
        def("entity foo { mutable name; }")
        def("@deprecated entity bar { mutable name; }")
        chk("foo @* {}", "list<foo>[]")
        chk("bar @* {}", "ct_err:deprecated:ENTITY:[bar]")
        chk("bar.from_gtv(gtv.from_json(''))", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f() = create bar('A');", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f() { update bar @* {} ( .name = 'X' ); }", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f() { delete bar @* {}; }", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f(bar) {}", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f(b: bar) {}", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f(b: list<bar>) {}", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f() = struct<bar>(name = 'Bob');", "ct_err:deprecated:ENTITY:[bar]")
        chkCompile("function f(x: struct<bar>) {}", "ct_err:deprecated:ENTITY:[bar]")
    }

    @Test fun testDefEntityHeader() {
        chkCompile("@external('foo') namespace ns { entity block; }", "OK")
        chkCompile("@external('foo') namespace ns { entity transaction; }", "OK")
        chkCompile("@external('foo') namespace ns { @deprecated entity block; }", "ct_err:modifier:invalid:ann:deprecated")
        chkCompile("@external('foo') namespace ns { @deprecated entity transaction; }", "ct_err:modifier:invalid:ann:deprecated")
    }

    @Test fun testDefEnum() {
        def("enum foo { a }")
        def("@deprecated enum bar { a }")
        chk("foo.a", "foo[a]")
        chk("bar.a", "ct_err:deprecated:ENUM:[bar]")
        chk("bar.b", "ct_err:[deprecated:ENUM:[bar]][unknown_member:[bar]:b]")
        chk("bar.values()", "ct_err:deprecated:ENUM:[bar]")
        chk("bar.value(0)", "ct_err:deprecated:ENUM:[bar]")
        chk("bar.a.value", "ct_err:deprecated:ENUM:[bar]")
        chkCompile("function f(x: foo) {}", "OK")
        chkCompile("function f(x: bar) {}", "ct_err:deprecated:ENUM:[bar]")
    }

    @Test fun testDefFunction() {
        def("function f() = 123;")
        def("@deprecated function g() = 456;")
        chk("f()", "int[123]")
        chk("g()", "ct_err:deprecated:FUNCTION:[g]")
        chk("f(*)", "fn[f()]")
        chk("g(*)", "ct_err:deprecated:FUNCTION:[g]")
        chkCompile("function h() { f(); }", "OK")
        chkCompile("function h() { g(); }", "ct_err:deprecated:FUNCTION:[g]")
    }

    @Test fun testDefFunctionAbstract() {
        file("lib.rell", "abstract module; @deprecated abstract function f();")
        chkCompile("import lib; override function lib.f() {}", "ct_err:deprecated:FUNCTION:[lib:f]")
        chkCompile("import lib; function g(){ lib.f(); }",
            "ct_err:[override:missing:[lib:f]:[lib.rell:1]][deprecated:FUNCTION:[lib:f]]")
    }

    @Test fun testDefFunctionExtendable() {
        chkCompile("@deprecated @extendable function f(){} function g(){}", "OK")
        chkCompile("@deprecated @extendable function f(){} function g(){ f(); }", "ct_err:deprecated:FUNCTION:[f]")
        chkCompile("@deprecated @extendable function f(){} @extend(f) function g(){}", "ct_err:deprecated:FUNCTION:[f]")
    }

    @Test fun testDefImport() {
        file("lib.rell", "module; function f() = 123; function g() = 456;")
        chkCompile("@deprecated import lib;", "ct_err:modifier:invalid:ann:deprecated")
        chkCompile("@deprecated import lib.{f};", "ct_err:modifier:invalid:ann:deprecated")
        chkCompile("@deprecated import lib.*;", "ct_err:modifier:invalid:ann:deprecated")
    }

    @Test fun testDefNamespace() {
        def("namespace a { val k = 111; }")
        def("@deprecated namespace b { val k = 222; }")
        def("@deprecated namespace c.d.e { val k = 333; }")
        chk("a.k", "int[111]")
        chk("b.k", "ct_err:deprecated:NAMESPACE:[b]")
        chk("c.d.e.k", "ct_err:deprecated:NAMESPACE:[c.d.e]")
        chkCompile("@deprecated namespace { val r = 123; }", "ct_err:modifier:invalid:ann:deprecated")
    }

    @Test fun testDefNamespaceMerge() {
        def("namespace a { val u = 123; }")
        def("@deprecated namespace b { val v = 456; }")
        chkCompile("@deprecated namespace a { val w = 789; } function f() = a.w;", "ct_err:deprecated:NAMESPACE:[a]")
        chkCompile("namespace b { val w = 789; } function f() = b.w;", "ct_err:deprecated:NAMESPACE:[b]")
    }

    @Test fun testDefNamespaceAmbiguous() {
        file("a.rell", "module; @deprecated namespace ns { function f() = 123; }")
        file("b.rell", "module; namespace ns { function g() = 456; }")
        chkCompile("import a.*; import b.*; function h() = ns.f();",
            "ct_err:[deprecated:NAMESPACE:[a:ns]][namespace:ambig:ns:[NAMESPACE:[a:ns],NAMESPACE:[b:ns]]]")
        chkCompile("import b.*; import a.*; function h() = ns.f();",
            "ct_err:[namespace:ambig:ns:[NAMESPACE:[b:ns],NAMESPACE:[a:ns]]][unknown_name:[b:ns]:f]")
        chkCompile("import a.*; import b.*; function h() = ns.g();",
            "ct_err:[deprecated:NAMESPACE:[a:ns]][namespace:ambig:ns:[NAMESPACE:[a:ns],NAMESPACE:[b:ns]]][unknown_name:[a:ns]:g]")
        chkCompile("import b.*; import a.*; function h() = ns.g();",
            "ct_err:namespace:ambig:ns:[NAMESPACE:[b:ns],NAMESPACE:[a:ns]]")
    }

    @Test fun testDefObject() {
        tstCtx.useSql = true
        def("object foo { mutable x: integer = 123; }")
        def("@deprecated object bar { mutable x: integer = 456; }")
        chk("foo.x", "int[123]")
        chk("bar.x", "ct_err:deprecated:OBJECT:[bar]")
        chkCompile("function f() { bar.x += 1; }", "ct_err:deprecated:OBJECT:[bar]")
        chkCompile("function f() { update bar (x  += 1); }", "ct_err:deprecated:OBJECT:[bar]")
        chkCompile("function f() = struct<bar>();", "ct_err:deprecated:OBJECT:[bar]")
        chkCompile("function f(x: struct<bar>) {}", "ct_err:deprecated:OBJECT:[bar]")
    }

    @Test fun testDefOperation() {
        tst.testLib = true
        def("operation foo() {}")
        def("@deprecated operation bar() {}")
        chk("foo()", "op[foo()]")
        chk("bar()", "ct_err:deprecated:OPERATION:[bar]")
        chkCompile("function f() = struct<bar>();", "ct_err:deprecated:OPERATION:[bar]")
        chkCompile("function f(x: struct<bar>) {}", "ct_err:deprecated:OPERATION:[bar]")
    }

    @Test fun testDefQuery() {
        def("query foo() = 123;")
        def("@deprecated query bar() = 123;")
        chk("foo()", "int[123]")
        chk("bar()", "ct_err:deprecated:QUERY:[bar]")
    }

    @Test fun testDefStruct() {
        def("struct foo { x: integer; }")
        def("@deprecated struct bar { x: integer; }")
        chk("foo(123)", "foo[x=int[123]]")
        chk("bar(123)", "ct_err:deprecated:STRUCT:[bar]")
        chk("bar.from_gtv(gtv.from_json(''))", "ct_err:deprecated:STRUCT:[bar]")
        chk("bar.from_bytes(x'')", "ct_err:deprecated:STRUCT:[bar]")
        chkCompile("function f(bar) {}", "ct_err:deprecated:STRUCT:[bar]")
        chkCompile("function f(x: bar) {}", "ct_err:deprecated:STRUCT:[bar]")
    }

    @Test fun testModule() {
        file("lib.rell", "@deprecated module;")
        chkCompile("import lib;", "ct_err:lib.rell:modifier:invalid:ann:deprecated")
    }

    @Test fun testImportDeprecatedDef() {
        file("lib.rell", "module; @deprecated function f() = 123; @deprecated struct s { x: integer; }")
        def("import lib;")
        chkCompile("function g() = lib.f();", "ct_err:deprecated:FUNCTION:[lib:f]")
        chkCompile("function g() = lib.f(*);", "ct_err:deprecated:FUNCTION:[lib:f]")
        chkCompile("function g() = lib.s(123);", "ct_err:deprecated:STRUCT:[lib:s]")
        chkCompile("function g(x: lib.s) {}", "ct_err:deprecated:STRUCT:[lib:s]")
    }

    @Test fun testNamespacePath() {
        def("""
            namespace a {
                entity x { mutable v: integer = 0; }
                @deprecated namespace b {
                    entity y { mutable v: integer = 0; }
                    namespace c {
                        entity z { mutable v: integer = 0; }
                    }
                }
            }
        """)
        chkCompile("function f() = a.x @ {};", "OK")
        chkCompile("function f() = a.b.y @ {};", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f() = a.b.c.z @ {};", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f() = create a.x();", "OK")
        chkCompile("function f() = create a.b.y();", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f() = create a.b.c.z();", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f() { update a.x @* {} (v = 1); }", "OK")
        chkCompile("function f() { update a.b.y @* {} (v = 1); }", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f() { update a.b.c.z @* {} (v = 1); }", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f(p: a.x) {}", "OK")
        chkCompile("function f(p: a.b.y) {}", "ct_err:deprecated:NAMESPACE:[a.b]")
        chkCompile("function f(p: a.b.c.z) {}", "ct_err:deprecated:NAMESPACE:[a.b]")
    }
}
