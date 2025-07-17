/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.repl

import net.postchain.rell.base.testutils.BaseRellTest
import kotlin.test.Test

class ReplDefinitionTest: BaseRellTest() {
    @Test fun testModule() {
        file("lib/main.rell", "struct rec { x: integer; } function f(x: integer): integer = x * x;")
        file("lib/sub.rell", "module; function g(): integer = 456;")
        tst.replModule = "lib"

        repl.chk("f(123)", "RES:int[15129]")
        repl.chk("rec(456)", "RES:lib:rec[x=int[456]]")

        repl.chk("function f(x: integer): integer = x * x * x;", "CTE:<console>:name_conflict:user:f:FUNCTION:lib/main.rell(1:37)")
        repl.chk("f(123)", "RES:int[15129]")
        repl.chk("struct rec {}", "CTE:<console>:name_conflict:user:rec:STRUCT:lib/main.rell(1:8)")
        repl.chk("rec(789)", "RES:lib:rec[x=int[789]]")

        repl.chk("import .sub;")
        repl.chk("sub.g()", "RES:int[456]")
    }

    @Test fun testModuleRoot() {
        file("root.rell", "function f(x: integer): integer = x * x;")
        tst.replModule = ""
        repl.chk("f(123)", "RES:int[15129]")
        repl.chk("function f(x: integer): integer = x * x * x;", "CTE:<console>:name_conflict:user:f:FUNCTION:root.rell(1:10)")
        repl.chk("f(123)", "RES:int[15129]")
    }

    @Test fun testModuleNotFound() {
        file("root.rell", "function f(x: integer): integer = x * x;")
        tst.replModule = "unknown"
        repl.chk("123", "CTE:import:not_found:unknown")
    }

    @Test fun testModuleInvalid() {
        file("lib.rell", "module; function f(): integer = 'Hello';")
        tst.replModule = "lib"
        repl.chk("123", "CTE:lib.rell:fn_rettype:[integer]:[text]")
    }

    @Test fun testModuleNameConflict() {
        file("lib.rell", "module; function f(): integer = 123;")
        tst.replModule = "lib"
        repl.chk("function f(): integer = 456;", "CTE:<console>:name_conflict:user:f:FUNCTION:lib.rell(1:18)")
        repl.chk("f()", "RES:int[123]")
    }

    @Test fun testImport() {
        file("lib.rell", """module;
            function f(x: integer): integer = x * x;
            struct rec { p: text = 'Hello'; q: integer = 123; }
        """)

        repl.chk("f(123)", "CTE:<console>:unknown_name:f")

        repl.chk("import lib;")
        repl.chk("lib.f(123)", "RES:int[15129]")
        repl.chk("lib.f(456)", "RES:int[207936]")
        repl.chk("lib.rec()", "RES:lib:rec[p=text[Hello],q=int[123]]")

        repl.chk("struct dat { r: rec; }", "CTE:<console>:unknown_name:rec")
        repl.chk("struct dat { r: lib.rec; }")
        repl.chk("dat(lib.rec('Bye',456))", "RES:dat[r=lib:rec[p=text[Bye],q=int[456]]]")
    }

    @Test fun testImportExact() {
        file("lib.rell", "module; function f(): integer = 123; function g(): integer = 456;")
        file("other.rell", "module; function f(): integer = 789;")
        repl.chk("import lib.{f};")
        repl.chk("f()", "RES:int[123]")
        repl.chk("import lib.{g};")
        repl.chk("g()", "RES:int[456]")
        repl.chk("import other.{f};", "CTE:<console>:name_conflict:user:f:IMPORT:<console>(1:13)")
        repl.chk("f()", "RES:int[123]")
    }

    @Test fun testImportWildcard() {
        file("lib.rell", "module; function f(): integer = 123; function g(): integer = 456;")
        file("other.rell", "module; function f(): integer = 789;")
        repl.chk("import lib.*;")
        repl.chk("f()", "RES:int[123]")
        repl.chk("g()", "RES:int[456]")
        repl.chk("import other.*;")
        repl.chk("f()", "CTE:<console>:namespace:ambig:f:[FUNCTION:[lib:f],FUNCTION:[other:f]]")
        repl.chk("g()", "RES:int[456]")
    }

    @Test fun testImportIndirect() {
        file("lib.rell", """module;
            struct rec { x: integer; }
            function h(r: rec): integer = r.x + 2;
        """)

        file("mid.rell", """module;
            import lib;
            function f(r: lib.rec): integer = r.x + 1;
            function g(x: integer): integer = f(lib.rec(x));
        """)

        repl.chk("import mid;")
        repl.chk("mid.g(123)", "RES:int[124]")

        repl.chk("import lib;")
        repl.chk("mid.f(lib.rec(123))", "RES:int[124]")
        repl.chk("mid.f(mid.lib.rec(123))", "RES:int[124]")
        repl.chk("lib.h(mid.lib.rec(123))", "RES:int[125]")
        repl.chk("mid.lib.h(lib.rec(123))", "RES:int[125]")
    }

    @Test fun testImportOperationQuery() {
        file("o.rell", "module; operation op(){}")
        file("q.rell", "module; query qu() = 123;")
        repl.chk("import o;")
        repl.chk("import q;")
    }

    @Test fun testImportParent() {
        file("foo/module.rell", "module;")
        file("foo/bar/module.rell", "module;")
        repl.chk("import foo;")
        repl.chk("import foo.bar;")
    }

    @Test fun testImportParentIsTest() {
        file("foo/module.rell", "@test module;")
        file("foo/bar/module.rell", "module;")
        repl.chk("import foo;")
        repl.chk("import foo.bar;", "CTE:foo/bar/module.rell:module:parent_is_test:foo.bar:foo")
    }

    @Test fun testFunctionsComplex() {
        repl.chk("f(123)", "CTE:<console>:unknown_name:f")
        repl.chk("function f(x: integer): integer = x * x;")
        repl.chk("f(123)", "RES:int[15129]")
        repl.chk("f(456)", "RES:int[207936]")

        repl.chk("function /* adjust pos */ f(x: integer): integer = x * x * x;",
                "CTE:<console>:name_conflict:user:f:FUNCTION:<console>(1:10)")
        repl.chk("f(123)", "RES:int[15129]")

        repl.chk("function g(x: integer): integer = x * x * x;")
        repl.chk("g(123)", "RES:int[1860867]")
        repl.chk("f(789)", "RES:int[622521]")

        repl.chk("struct rec { p: text = 'Hello'; q: integer = 555; }")
        repl.chk("rec()", "RES:rec[p=text[Hello],q=int[555]]")
        repl.chk("rec(f(111) + g(222))", "RES:rec[p=text[Hello],q=int[10953369]]")
        repl.chk("f(rec().q)", "RES:int[308025]")
    }

    @Test fun testFunctionRecursiveDirect() {
        repl.chk("function f(x: integer): integer = if (x == 0) 1 else x * f(x - 1);")
        repl.chk("f(5)", "RES:int[120]")
    }

    @Test fun testFunctionRecursiveIndirect() {
        repl.chk("""
            function f(x: integer): integer = if (x == 0) 1 else x * g(x - 1);
            function g(x: integer): integer = f(x);
        """)
        repl.chk("f(5)", "RES:int[120]")
    }

    @Test fun testStructCyclicDirect() {
        repl.chk("struct node { value: integer; next: node?; }")
        repl.chk("node(123, null)", "RES:node[value=int[123],next=null]")
        repl.chk("node(123, node(456, null))", "RES:node[value=int[123],next=node[value=int[456],next=null]]")
    }

    @Test fun testStructCyclicIndirect() {
        repl.chk("struct node { value: integer; next: link?; } struct link { tag: integer; node; } ")
        repl.chk("node(123, null)", "RES:node[value=int[123],next=null]")
        repl.chk("node(123, link(456, node(789, null)))",
                "RES:node[value=int[123],next=link[tag=int[456],node=node[value=int[789],next=null]]]")
    }

    @Test fun testNameConflict() {
        repl.chk("struct rec {x: integer = 123;}")
        repl.chk("rec()", "RES:rec[x=int[123]]")
        repl.chk("struct rec {y: integer;}", "CTE:<console>:name_conflict:user:rec:STRUCT:<console>(1:8)")
        repl.chk("function rec (y: integer) {}", "CTE:<console>:name_conflict:user:rec:STRUCT:<console>(1:8)")
        repl.chk("enum rec { A, B, C }", "CTE:<console>:name_conflict:user:rec:STRUCT:<console>(1:8)")
        repl.chk("rec()", "RES:rec[x=int[123]]")

        repl.chk("enum foo {X}")
        repl.chk("enum bar {Y}")
        repl.chk("enum rec { A, B, C }", "CTE:<console>:name_conflict:user:rec:STRUCT:<console>(1:8)")

        repl.chk("enum integer {X}", "CTE:<console>:name_conflict:sys:integer:TYPE")
        repl.chk("enum boolean {X}", "CTE:<console>:name_conflict:sys:boolean:TYPE")
        repl.chk("enum abs {X}", "CTE:<console>:name_conflict:sys:abs:FUNCTION")
        repl.chk("enum op_context {X}", "CTE:<console>:name_conflict:sys:op_context:NAMESPACE")
        repl.chk("enum transaction {X}", "CTE:<console>:name_conflict:sys:transaction:ENTITY")
        repl.chk("enum block {X}", "CTE:<console>:name_conflict:sys:block:ENTITY")
    }

    @Test fun testMountConflict() {
        file("u.rell", "module; entity user { name; }")
        file("c.rell", "module; entity company { name; }")
        file("op.rell", "module; operation op1() {}")
        tstCtx.useSql = true

        repl.chk("import u;")
        repl.chk("entity user { name; };",
                "CTE:<console>:def_repl:ENTITY",
                "CTE:<console>:mnt_conflict:user:[user]:user:ENTITY:[u:user]:u.rell(1:16)"
        )

        repl.chk("entity company { name; }", "CTE:<console>:def_repl:ENTITY")
        repl.chk("import c;")

        repl.chk("import op;")
        repl.chk("operation op1() {}",
                "CTE:<console>:def_repl:OPERATION",
                "CTE:<console>:mnt_conflict:user:[op1]:op1:OPERATION:[op:op1]:op.rell(1:19)"
        )
    }

    @Test fun testMountConflict2() {
        file("module.rell", "entity user { name; }")
        tst.replModule = ""
        repl.chk("entity user { name; };",
                "CTE:<console>:def_repl:ENTITY",
                "CTE:<console>:name_conflict:user:user:ENTITY:module.rell(1:8)"
        )
    }

    @Test fun testNamespace() {
        repl.chk("namespace a { function f(): integer = 123; }")
        repl.chk("a.f()", "RES:int[123]")
        repl.chk("namespace a { function g(): integer = 456; }")
        repl.chk("a.f()", "RES:int[123]")
        repl.chk("a.g()", "RES:int[456]")
        repl.chk("namespace a { function g(): integer = 789; }", "CTE:<console>:name_conflict:user:g:FUNCTION:<console>(1:24)")
        repl.chk("a.g()", "RES:int[456]")
        repl.chk("namespace a { namespace b { namespace c { function p(): text = 'P'; } } }")
        repl.chk("a.b.c.p()", "RES:text[P]")
        repl.chk("namespace a { namespace b { namespace c { function q(): text = 'Q'; } } }")
        repl.chk("a.b.c.p()", "RES:text[P]")
        repl.chk("a.b.c.q()", "RES:text[Q]")
    }

    @Test fun testNamespace2() {
        repl.chk("namespace a { function f(): integer = 123; } namespace a { function g(): integer = 456; }")
        repl.chk("a.f()", "RES:int[123]")
        repl.chk("a.g()", "RES:int[456]")
        repl.chk("namespace a { function h(): integer = 321; } namespace a { function i(): integer = 654; }")
        repl.chk("a.f()", "RES:int[123]")
        repl.chk("a.g()", "RES:int[456]")
        repl.chk("a.h()", "RES:int[321]")
        repl.chk("a.i()", "RES:int[654]")
    }

    @Test fun testDisallowedDefs() {
        repl.chk("operation op(x: integer) {}", "CTE:<console>:def_repl:OPERATION")
        repl.chk("query q() = 123;", "CTE:<console>:def_repl:QUERY")
    }

    @Test fun testRuntimeError() {
        repl.chk("function f(): integer = 123; print(123 / (1 - 1));", "rt_err:expr:/:div0:123")
        repl.chk("f()", "CTE:<console>:unknown_name:f")
        repl.chk("function f(): integer = 456;")
        repl.chk("f()", "RES:int[456]")
    }

    @Test fun testAbstractFunctionImport() {
        file("lib.rell", "abstract module; abstract function f(): integer; function g(): integer = f() + 1;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 123;")
        repl.chk("import lib;", "CTE:lib.rell:override:missing:[lib:f]")
        repl.chk("import imp;")
        repl.chk("imp.lib.f()", "RES:int[123]")
        repl.chk("imp.lib.g()", "RES:int[124]")
        repl.chk("import lib;")
        repl.chk("lib.f()", "RES:int[123]")
        repl.chk("lib.g()", "RES:int[124]")
    }

    @Test fun testAbstractFunctionLinked() {
        file("lib.rell", "abstract module; abstract function f(): integer; function g(): integer = f() + 1;")
        tst.replModule = "lib"
        repl.chk("123", "CTE:lib.rell:override:missing:[lib:f]")
    }

    @Test fun testOverrideFunction() {
        file("lib.rell", "abstract module; abstract function f(): integer; function g(): integer = f() + 1;")
        file("imp.rell", "module; import lib; override function lib.f(): integer = 123;")
        repl.chk("import imp;")
        repl.chk("import lib;")
        repl.chk("override function lib.f(): integer = 456;", "CTE:<console>:fn:override:repl")
    }

    @Test fun testModuleArgsSimple() {
        file("lib.rell", "module; struct module_args { x: integer; } function f() = chain_context.args;")
        tst.moduleArgs("lib" to "{'x':123}")
        repl.chk("import lib; lib.f();", "RES:lib:module_args[x=int[123]]")
    }

    @Test fun testModuleArgsImport() {
        file("lib.rell", """module;
            struct module_args { x: integer; }
            function f(): module_args = chain_context.args;
        """)
        repl.chk("import lib;")
        repl.chk("lib.f()", "rt_err:chain_context.args:no_module_args:lib")
    }

    @Test fun testModuleArgsLinked() {
        file("lib.rell", """module;
            struct module_args { x: integer; }
            function f(): module_args = chain_context.args;
        """)
        tst.replModule = "lib"
        repl.chk("123", "RES:int[123]")
        repl.chk("f()", "rt_err:chain_context.args:no_module_args:lib")
    }

    @Test fun testModuleArgsImportComplex() {
        file("a.rell", "module; struct module_args { x: integer; } function f() = chain_context.args;")
        file("b.rell", "module; struct module_args { y: integer; } function g() = chain_context.args;")
        file("c.rell", "module; struct module_args { z: integer; } function h() = chain_context.args;")

        tst.moduleArgs("a" to "{'x':123}", "b" to "{'y':456}")
        repl.chk("0", "RES:int[0]")
        repl.chk("import a;", "")
        repl.chk("a.f()", "RES:a:module_args[x=int[123]]")
        repl.chk("import b;", "")
        repl.chk("b.g()", "RES:b:module_args[y=int[456]]")
        repl.chk("import c;", "")
        repl.chk("c.h()", "rt_err:chain_context.args:no_module_args:c")
        repl.chk("a.f()", "RES:a:module_args[x=int[123]]")
        repl.chk("b.g()", "RES:b:module_args[y=int[456]]")
    }

    @Test fun testImportConstants() {
        file("a.rell", "module; val X = _nop_print(123);")
        file("b.rell", "module; val Y = _nop_print(456);")
        file("c.rell", "module; val Z = _nop_print(789);")

        repl.chk("import a;", "OUT:123")
        repl.chk("a.X", "RES:int[123]")

        repl.chk("import b;", "OUT:456")
        repl.chk("b.Y", "RES:int[456]")

        repl.chk("import c;", "OUT:789")
        repl.chk("c.Z", "RES:int[789]")

        repl.chk("a.X", "RES:int[123]")
        repl.chk("b.Y", "RES:int[456]")
        repl.chk("c.Z", "RES:int[789]")

        repl.chk("import t: a;")
        repl.chk("t.X", "RES:int[123]")
    }
}
