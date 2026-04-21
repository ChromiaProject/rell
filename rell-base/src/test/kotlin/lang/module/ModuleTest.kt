/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.module

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_CommonError
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.RellCodeTester
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.mapToImmList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModuleTest: BaseRellTest() {
    @Test fun testForwardTypeReferenceFunction() {
        val code = """
            function f(x: foo): bar = bar(x);
            struct foo { p: integer; }
            struct bar { x: foo; }
            query q() = f(foo(123));
        """
        chkFull(code, "bar[x=foo[p=int[123]]]")
    }

    @Test fun testForwardTypeReferenceOperation() {
        val code = """
            operation o(x: foo) { print(_strict_str(bar(x))); }
            struct foo { p: integer; }
            struct bar { x: foo; }
        """
        tst.chkOpExGtvStr(code, listOf("""[123]"""), "OK")
        chkOut("bar[x=foo[p=int[123]]]")
    }

    @Test fun testForwardTypeReferenceQuery() {
        val code = """
            query q(x: foo): bar = bar(x);
            struct foo { p: integer; }
            struct bar { x: foo; }
        """
        tst.chkQueryGtvEx(code, listOf("""{"p":123}"""), "bar[x=foo[p=int[123]]]")
    }

    @Test fun testImportSimplest() {
        file("dir/foo.rell", "module; function f(x: integer): integer = x * x;")
        chkImp("", "f(123)", "ct_err:unknown_name:f")
        chkImp("", "foo.f(123)", "ct_err:unknown_name:foo")
        chkImp("", "dir.foo.f(123)", "ct_err:unknown_name:dir")
        chkImp("import dir.foo;", "f(123)", "ct_err:unknown_name:f")
        chkImp("import dir.foo;", "foo.f(123)", "int[15129]")
    }

    @Test fun testImportFileNoHeader() {
        file("dir/foo.rell", "function f(x: integer): integer = x * x;")
        chkImp("import dir.foo;", "foo.f(123)", "ct_err:[import:not_found:dir.foo][unknown_name:foo]")
        chkImp("import dir;", "dir.f(123)", "int[15129]")
    }

    @Test fun testImportNotFound() {
        chkCompile("import foo;", "ct_err:import:not_found:foo")
        chkCompile("import a.b.c.d.foo;", "ct_err:import:not_found:a.b.c.d.foo")
    }

    @Test fun testImportSyntaxError() {
        file("dir/foo.rell", "syntax error")
        chkCompile("", "OK")
        chkCompile("import dir.foo;", "ct_err:dir/foo.rell:syntax")
        chkCompile("import dir.foo; query q() = foo.f();", "ct_err:[main.rell:unknown_name:[foo]:f][dir/foo.rell:syntax]")
    }

    @Test fun testImportCompilationError() {
        file("lib/a.rell", "module; function f(): text = ERROR_A;")
        file("lib/b.rell", "module; function f(): text = 'OK_B';")
        file("lib/c.rell", "module; function f(): text = ERROR_C;")
        file("lib/d.rell", "module; function f(): text = 'OK_D';")
        file("lib/e.rell", "module; function f(): text = ERROR_E;")
        chkCompile("import lib.a; import lib.b; import lib.c; import lib.d; import lib.e; query q() = ERROR_MAIN;", """ct_err:
            [main.rell:unknown_name:ERROR_MAIN]
            [lib/a.rell:unknown_name:ERROR_A]
            [lib/c.rell:unknown_name:ERROR_C]
            [lib/e.rell:unknown_name:ERROR_E]
        """)
    }

    @Test fun testImportCompilationError2() {
        file("lib/a.rell", "module; entity user { a: integer; b: text; c: boolean; x: ERROR; }")
        chkCompile("import lib.a; query q() = (a.user@{}).a;", "ct_err:lib/a.rell:unknown_name:ERROR")
        chkCompile("import lib.a; query q() = (a.user@{}).b;", "ct_err:lib/a.rell:unknown_name:ERROR")
        chkCompile("import lib.a; query q() = (a.user@{}).c;", "ct_err:lib/a.rell:unknown_name:ERROR")
        chkCompile("import lib.a; query q() = (a.user@{}).x;", "ct_err:lib/a.rell:unknown_name:ERROR")
        chkCompile("import lib.a; query q() = (a.user@{}).z;", "ct_err:[main.rell:unknown_member:[lib.a:user]:z][lib/a.rell:unknown_name:ERROR]")
        chkCompile("import lib.a; query q() = 123;", "ct_err:lib/a.rell:unknown_name:ERROR")
    }

    @Test fun testImportAliasConflict() {
        file("a/foo.rell", "module;")
        file("b/foo.rell", "module;")

        chkCompile("import a.foo;", "OK")

        tst.ideDefIdConflictError = false

        chkCompile("import a.foo; import b.foo;", """ct_err:
            [name_conflict:user:foo:IMPORT:main.rell(1:24)]
            [name_conflict:user:foo:IMPORT:main.rell(1:10)]
        """)

        chkCompile("import a.foo; function foo(){}", """ct_err:
            [name_conflict:user:foo:FUNCTION:main.rell(1:24)]
            [name_conflict:user:foo:IMPORT:main.rell(1:10)]
        """)

        chkCompile("function foo(){} import a.foo;", """ct_err:
            [name_conflict:user:foo:IMPORT:main.rell(1:27)]
            [name_conflict:user:foo:FUNCTION:main.rell(1:10)]
        """)

        chkCompile("import a.foo; namespace foo {}", """ct_err:
            [name_conflict:user:foo:NAMESPACE:main.rell(1:25)]
            [name_conflict:user:foo:IMPORT:main.rell(1:10)]
        """)

        chkCompile("import boolean: a.foo;", "ct_err:name_conflict:sys:boolean:TYPE")
        chkCompile("import integer: a.foo;", "ct_err:name_conflict:sys:integer:TYPE")
        chkCompile("import text: a.foo;", "ct_err:name_conflict:sys:text:TYPE")
        chkCompile("import abs: a.foo;", "ct_err:name_conflict:sys:abs:FUNCTION")
        chkCompile("import block: a.foo;", "ct_err:name_conflict:sys:block:ENTITY")

        chkCompile("namespace a { import a.foo; } import b.foo;", "OK")
        chkCompile("import a.foo; namespace b { import b.foo; }", "OK")
        chkCompile("namespace a { import a.foo; } namespace b { import b.foo; }", "OK")
    }

    @Test fun testImportCyclic() {
        file("a/foo.rell", "module; import a.bar; function f(): integer = bar.g(); function g(): integer = 123;")
        file("a/bar.rell", "module; import a.foo; function f(): integer = foo.g(); function g(): integer = 456;")
        chkImp("import a.foo;", "foo.f()", "int[456]")
        chkImp("import a.bar;", "bar.f()", "int[123]")
    }

    @Test fun testInterModuleFunctionRecursion() {
        tst.strictToString = false
        file("a/foo.rell", "module; import a.bar; function f(x: integer): text = 'f('+x+')' + if(x==0) '' else (','+bar.g(x-1));")
        file("a/bar.rell", "module; import a.foo; function g(x: integer): text = 'g('+x+')' + if(x==0) '' else (','+foo.f(x-1));")
        chkImp("import a.foo;", "foo.f(0)", "f(0)")
        chkImp("import a.foo;", "foo.f(5)", "f(5),g(4),f(3),g(2),f(1),g(0)")
        chkImp("import a.bar;", "bar.g(0)", "g(0)")
        chkImp("import a.bar;", "bar.g(5)", "g(5),f(4),g(3),f(2),g(1),f(0)")
    }

    @Test fun testInterModuleEntityCycle() {
        file("a/foo.rell", "module; import a.bar; entity company { user: bar.user; }")
        file("a/bar.rell", "module; import a.foo; entity user { company: foo.company; }")
        chkCompile("", "OK")
        chkCompile("import a.foo;", "ct_err:a/foo.rell:entity_cycle:a.foo:company,a.bar:user")
        chkCompile("import a.bar;", "ct_err:a/bar.rell:entity_cycle:a.bar:user,a.foo:company")
    }

    @Test fun testImportInNamespace() {
        file("a/foo.rell", "module; function f(): integer = 123;")
        chkImp("import a.foo;", "foo.f()", "int[123]")
        chkImp("namespace x { import a.foo; }", "x.foo.f()", "int[123]")
        chkImp("namespace x { import a.foo; }", "foo.f()", "ct_err:unknown_name:foo")
    }

    @Test fun testImportInWrongPlace() {
        file("a/foo.rell", "module;")
        chkCompile("entity user { import a.foo; }", "ct_err:syntax")
        chkCompile("object state { import a.foo; }", "ct_err:syntax")
        chkCompile("enum e { import a.foo; }", "ct_err:syntax")
        chkCompile("struct rec { import a.foo; }", "ct_err:syntax")
        chkCompile("function f() { import a.foo; }", "ct_err:syntax")
        chkCompile("operation o() { import a.foo; }", "ct_err:syntax")
        chkCompile("query q() { import a.foo; }", "ct_err:syntax")
    }

    @Test fun testImportLongChain() {
        tst.errMsgPos = true
        for (i in 1 .. 99) file("lib/a$i.rell", "module; import x: lib.a${i+1}; function f${i}(): integer = x.f${i+1}()+1;")
        file("lib/a100.rell", "module; function f100(): integer = 1;")
        chkImp("import lib.a1;", "a1.f1()", "int[100]")
    }

    @Test fun testImportVariousDefs() {
        file("lib/a.rell", """
            module;
            entity cls {}
            object state { p: integer = 123; }
            struct rec {}
            enum e { A }
            function f() {}
        """)

        def("import lib.a;")

        chkCompile("function x(c: a.cls){}", "OK")
        chkCompile("function x(): integer = a.state.p;", "OK")
        chkCompile("function x(r: a.rec){}", "OK")
        chkCompile("function x(e: a.e){}", "OK")
        chkCompile("function x(){ a.f(); }", "OK")
    }

    @Test fun testImportsVisibleFromOutside() {
        file("a.rell", "module; function f(): integer = 123;")
        file("b.rell", "module; import a; function g(): integer = a.f() * 456;")
        file("c/c1.rell", "import a; function g(): integer = a.f() * 789;")
        chkImp("import b;", "b.g()", "int[56088]")
        chkImp("import b;", "b.a.f()", "int[123]")
        chkImp("import c;", "c.g()", "int[97047]")
        chkImp("import c;", "c.a.f()", "int[123]")
    }

    @Test fun testImportNotAtTop() {
        file("lib/a.rell", "module; function f(): integer = 123;")
        chkFull("query q() = a.f(); import lib.a;", "int[123]")
        chkFull("function g(): integer = a.f(); import lib.a; query q() = g();", "int[123]")
        chkFull("namespace foo { function g(): integer = a.f(); import lib.a; } query q() = foo.g();", "int[123]")
        chkFull("namespace foo { function g(): integer = a.f(); } import lib.a; query q() = foo.g();", "int[123]")
        chkFull("namespace foo { function g(): integer = a.f(); } query q() = foo.g(); import lib.a;", "int[123]")
    }

    @Test fun testImportSameModuleMultipleTimes() {
        file("lib/a.rell", "module; struct rec { x: integer; }")
        file("lib/b.rell", "module; import lib.a; function f(r: a.rec): integer = r.x * 2;")

        val imports = "import lib.b; import lib.a; import a2: lib.a; namespace ns { import lib.a; }"
        chkImp(imports, "b.f(a.rec(123))", "int[246]")
        chkImp(imports, "b.f(a2.rec(123))", "int[246]")
        chkImp(imports, "b.f(ns.a.rec(123))", "int[246]")
        chkImp(imports, "_type_of(a.rec(123))", "text[lib.a:rec]")
        chkImp(imports, "_type_of(a2.rec(123))", "text[lib.a:rec]")
        chkImp(imports, "_type_of(ns.a.rec(123))", "text[lib.a:rec]")
    }

    /*@Test*/ fun testImportSameModuleMultipleTimes2() {
        file("lib/a.rell", "module; struct rec { x: integer; }")
        file("lib/b.rell", "module; public import lib.a; public import a2: lib.a; namespace ns { public import lib.a; }")
        file("lib/c.rell", "module; import lib.a; function f(r: a.rec): integer = r.x * 2;")

        val imports = "import lib.b; import lib.c;"
        chkImp(imports, "c.f(b.a.rec(123))", "int[246]")
        chkImp(imports, "c.f(b.a2.rec(123))", "int[246]")
        chkImp(imports, "c.f(b.ns.a.rec(123))", "int[246]")
        chkImp(imports, "_type_of(b.a.rec(123))", "text[lib.a#rec]")
        chkImp(imports, "_type_of(b.a1.rec(123))", "text[lib.a#rec]")
        chkImp(imports, "_type_of(b.ns.a.rec(123))", "text[lib.a#rec]")
    }

    @Test fun testImportRelativePath() {
        chkImportRelativePath("", "ct_err:a/b/c/module.rell:syntax")

        chkImportRelativePath(".", "a.b.c")
        chkImportRelativePath(".d", "a.b.c.d")

        chkImportRelativePath("^", "a.b")
        chkImportRelativePath("^^", "a")
        chkImportRelativePath("^^^", "")

        chkImportRelativePath("^.z", "a.b.z")
        chkImportRelativePath("^^.y", "a.y")
        chkImportRelativePath("^^^.x", "x")

        chkImportRelativePath("^.c", "a.b.c")
        chkImportRelativePath("^^.b.c", "a.b.c")
        chkImportRelativePath("^^^.a.b.c", "a.b.c")

        chkImportRelativePath("^^^^", "ct_err:a/b/c/module.rell:import:up:3:4")
        chkImportRelativePath(".q", "ct_err:a/b/c/module.rell:import:not_found:a.b.c.q")
        chkImportRelativePath("^^.q", "ct_err:a/b/c/module.rell:import:not_found:a.q")

        chkImportRelativePath0("import .;", "ct_err:a/b/c/module.rell:import:no_alias")
        chkImportRelativePath0("import ^;", "ct_err:a/b/c/module.rell:import:no_alias")
        chkImportRelativePath0("import ^^;", "ct_err:a/b/c/module.rell:import:no_alias")

        chkImportRelativePath0("import .d; function g(): text = d.f();", "a.b.c.d")
        chkImportRelativePath0("import ^.z; function g(): text = z.f();", "a.b.z")
    }

    private fun chkImportRelativePath(imp: String, exp: String) {
        chkImportRelativePath0("import x: $imp; function g(): text = x.f(); function f(): text = 'a.b.c';", exp)
    }

    private fun chkImportRelativePath0(code: String, exp: String) {
        val t = RellCodeTester(tstCtx)
        t.wrapInit = true
        t.strictToString = false
        t.file("module.rell", "function f(): text = '';")
        t.file("a/module.rell", "function f(): text = 'a';")
        t.file("a/b/module.rell", "function f(): text = 'a.b';")
        t.file("a/b/c/module.rell", code)
        t.file("a/b/c/d.rell", "module; function f(): text = 'a.b.c.d';")
        t.file("x/module.rell", "function f(): text = 'x';")
        t.file("a/y/module.rell", "function f(): text = 'a.y';")
        t.file("a/b/z/module.rell", "function f(): text = 'a.b.z';")
        t.def("import a.b.c;")
        t.chk("c.g()", exp)
    }

    @Test fun testImportSelf() {
        file("lib/a.rell", "module; import self: .; function f(): integer = 123; function g(): integer = self.f() * 2;")
        file("lib/b.rell", "module; import lib.a; function p(): integer = a.g() * 3;")
        file("lib/c.rell", "module; import lib.a; import self: .; function q(): integer = self.a.g() * 4;")
        chkFull("import lib.a; query q() = a.f();", "int[123]")
        chkFull("import lib.a; query q() = a.g();", "int[246]")
        chkFull("import lib.b; query q() = b.p();", "int[738]")
        chkFull("import lib.c; query q() = c.q();", "int[984]")
    }

    @Test fun testImportNoDirectParent() {
        file("foo/bar/a.rell", "module; function f() = 123;")
        chkFull("import foo.bar.a; query q() = a.f();", "int[123]")
    }

    @Test fun testCannotAccessImporterDefs() {
        file("lib/a.rell", "function g(): integer = f();")
        chkCompile("import lib; function f(): integer = 123;", "ct_err:lib/a.rell:unknown_name:f")
        chkCompile("function f(): integer = 123; import lib;", "ct_err:lib/a.rell:unknown_name:f")
    }

    @Test fun testSystemDefsNotVisibleFromOutside() {
        file("a.rell", "module; struct rec { x: integer = 123; }")
        chkSystemDefsNotVisibleFromOutside()
    }

    @Test fun testSystemDefsNotVisibleFromOutside2() {
        file("a/a1.rell", "struct rec { x: integer = 123; }")
        chkSystemDefsNotVisibleFromOutside()
    }

    private fun chkSystemDefsNotVisibleFromOutside() {
        chkFull("import a; query q(): a.rec = a.rec();", "a:rec[x=int[123]]")
        chkFull("import a; query q(): a.integer = 123;", "ct_err:unknown_name:a.integer")
        chkFull("import a; query q(): a.boolean = false;", "ct_err:unknown_name:a.boolean")
        chkFull("import a; query q(): a.text = 'Abc';", "ct_err:unknown_name:a.text")
        chkFull("import a; query q(): a.byte_array = x'1234';", "ct_err:unknown_name:a.byte_array")
        chkFull("import a; query q() = a.abs(-123);", "ct_err:unknown_name:[a]:abs")
        chkFull("import a; query q() = abs(-123);", "int[123]")
        chkFull("import a; query q() = a.integer.MIN_VALUE;", "ct_err:unknown_name:[a]:integer")
        chkFull("import a; query q() = integer.MIN_VALUE;", "int[-9223372036854775808]")
    }

    @Test fun testNameConflictRecoveryInFile() {
        tst.errMsgPos = true

        file("a.rell", """
            module;
            function f(): integer = unknown_1;
            function f(): integer = unknown_2;
        """.trimIndent())

        chkCompile("import a;", """ct_err:
            [a.rell(2:10):name_conflict:user:f:FUNCTION:a.rell(3:10)]
            [a.rell(2:25):unknown_name:unknown_1]
            [a.rell(3:10):name_conflict:user:f:FUNCTION:a.rell(2:10)]
            [a.rell(3:25):unknown_name:unknown_2]
        """)
    }

    @Test fun testNameConflictRecoveryInterFile() {
        tst.errMsgPos = true

        file("a/a1.rell", "function f(): integer = unknown_1;")
        file("a/a2.rell", "function f(): integer = unknown_2;")

        chkCompile("import a;", """ct_err:
            [a/a1.rell(1:10):name_conflict:user:f:FUNCTION:a/a2.rell(1:10)]
            [a/a1.rell(1:25):unknown_name:unknown_1]
            [a/a2.rell(1:10):name_conflict:user:f:FUNCTION:a/a1.rell(1:10)]
            [a/a2.rell(1:25):unknown_name:unknown_2]
        """)
    }

    @Test fun testNameConflictMount() {
        tst.errMsgPos = true
        tst.ideDefIdConflictError = false
        val code = """
            entity user {}
            entity user {}
        """.trimIndent()
        chkCompile(code, """ct_err:
            [main.rell(1:8):name_conflict:user:user:ENTITY:main.rell(2:8)]
            [main.rell(2:8):name_conflict:user:user:ENTITY:main.rell(1:8)]
        """)
    }

    @Test fun testInclude() {
        file("sub/foo.rell", "function f(): integer = 123;")
        chkCompile("include 'sub/foo';", "ct_err:include")
        chkCompile("include 'sub/foo'; function g(): integer = f();", "ct_err:[include][unknown_name:f]")
        chkCompile("include 'sub/foo.rell';", "ct_err:include")
        chkCompile("include 'sub/bar';", "ct_err:include")
        chkFull("import sub; query q() = sub.f();", "int[123]")
    }

    @Test fun testSyntaxErrorBugs() {
        // There was a bug in the Xtext parser when parsing following broken code.
        chkCompile("namespace ft3 {", "ct_err:syntax")
        chkCompile("external 'foo' {", "ct_err:syntax")
        chkCompile("query q() {", "ct_err:syntax")
    }

    @Test fun testCompileTestModules() {
        val sourceDir = C_SourceDir.mapDirOf(
                "module.rell" to "query root() = 0;",
                "part.rell" to "query part() = 0;",
                "module_test.rell" to "@test module;",
                "simple.rell" to "module; query simple() = 0;",
                "simple_test.rell" to "@test module;",
                "nested/main.rell" to "query nested() = 0;",
                "nested/helper.rell" to "query helper() = 0;",
                "nested/test.rell" to "@test module;",
                "nested/deeper/module.rell" to "query deeper() = 0;",
                "nested/deeper/deeper_test.rell" to "@test module;",
                "noparent/tests/foo_test.rell" to "@test module;",
                "noparent/tests/bar_test.rell" to "@test module;",
        )

        chkCompileTestModules(sourceDir, listOf(), listOf(), "[]")
        chkCompileTestModules(sourceDir, listOf(""), listOf(), "[[*]]")
        chkCompileTestModules(sourceDir, listOf("simple"), listOf(), "[[],[*simple]]")
        chkCompileTestModules(sourceDir, listOf("nested"), listOf(), "[[],[*nested]]")
        chkCompileTestModules(sourceDir, listOf("nested.deeper"), listOf(), "[[],[nested],[*nested.deeper]]")
        chkCompileTestModules(sourceDir, listOf(), listOf(""),
                "[[*module_test],[*nested.deeper.deeper_test],[*nested.test],[*noparent.tests.bar_test],[*noparent.tests.foo_test],[*simple_test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("simple"), "[]")
        chkCompileTestModules(sourceDir, listOf(), listOf("simple_test"), "[[*simple_test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("nested"), "[[*nested.deeper.deeper_test],[*nested.test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("nested.test"), "[[*nested.test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("nested.deeper"), "[[*nested.deeper.deeper_test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("noparent"), "[[*noparent.tests.bar_test],[*noparent.tests.foo_test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("noparent.tests"), "[[*noparent.tests.bar_test],[*noparent.tests.foo_test]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("nested", "noparent"),
                "[[*nested.deeper.deeper_test],[*nested.test],[*noparent.tests.bar_test],[*noparent.tests.foo_test]]")
    }

    @Test fun testCompileTestModulesEmptyDir() {
        val sourceDir = C_SourceDir.mapDirOf("empty/blank/none.txt" to "")
        chkCompileTestModules(sourceDir, listOf("empty"), listOf(), "cm_err:import:not_found:empty")
        chkCompileTestModules(sourceDir, listOf("empty.blank"), listOf(), "cm_err:import:not_found:empty.blank")
        chkCompileTestModules(sourceDir, listOf(), listOf("empty"), "cm_err:import:not_found:empty")
        chkCompileTestModules(sourceDir, listOf(), listOf("empty.blank"), "cm_err:import:not_found:empty.blank")
    }

    @Test fun testCompileTestModulesUnknownModule() {
        val sourceDir = C_SourceDir.mapDirOf("module.rell" to "query root() = 0;")
        chkCompileTestModules(sourceDir, listOf("unknown"), listOf(), "cm_err:import:not_found:unknown")
        chkCompileTestModules(sourceDir, listOf(), listOf("unknown"), "cm_err:import:not_found:unknown")
        chkCompileTestModules(sourceDir, listOf(), listOf("single.unknown"), "cm_err:import:not_found:single.unknown")
        chkCompileTestModules(sourceDir, listOf(), listOf("nested.unknown"), "cm_err:import:not_found:nested.unknown")
        chkCompileTestModules(sourceDir, listOf(), listOf("misc.unknown"), "cm_err:import:not_found:misc.unknown")
        chkCompileTestModules(sourceDir, listOf(), listOf("misc.tests.unknown"), "cm_err:import:not_found:misc.tests.unknown")
    }

    @Test fun testCompileTestModulesFileDirConflict() {
        val sourceDir = C_SourceDir.mapDirOf(
                "trouble/conflict/module.rell" to "module;",
                "trouble/conflict.rell" to "module;",
        )
        chkCompileTestModules(sourceDir, listOf("trouble.conflict"), listOf(), "cm_err:import:file_dir:trouble.conflict")
        chkCompileTestModules(sourceDir, listOf(), listOf("trouble.conflict"), "cm_err:import:file_dir:trouble.conflict")
    }

    @Test fun testCompileTestModulesStrangeFileNames() {
        val sourceDir = C_SourceDir.mapDirOf(
                "app/module.rell" to "module;",
                "app/123.rell" to "function foo() = 123;",
                "app/other-strange.name.rell" to "function bar() = 456;",
                "app/not_a_rell_file.txt" to "Hi!",
                "app/tests/tests1.rell" to "@test module; import app; function test() { app.foo(); }",
                "app/tests/tests2.rell" to "@test module; import app; function test() { app.bar(); }",
        )
        chkCompileTestModules(sourceDir, listOf(""), listOf(""), "cm_err:import:not_found:")
        chkCompileTestModules(sourceDir, listOf(), listOf(""), "[[app],[*app.tests.tests1],[*app.tests.tests2]]")
        chkCompileTestModules(sourceDir, listOf("app"), listOf(""), "[[*app],[*app.tests.tests1],[*app.tests.tests2]]")
        chkCompileTestModules(sourceDir, listOf("app"), listOf("app.tests"), "[[*app],[*app.tests.tests1],[*app.tests.tests2]]")
        chkCompileTestModules(sourceDir, listOf("app"), listOf(), "[[*app]]")
    }

    @Test fun testCompileTestModulesImport() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app/module.rell" to "module;",
            "app/test/module.rell" to "@test module; import lib.test;",
            "lib/module.rell" to "module;",
            "lib/test/module.rell" to "@test module;",
        )
        chkCompileTestModules(sourceDir, listOf(), listOf("app"), "[[*app.test],[lib.test]]")
    }

    @Test fun testCompileTestModulesImport2() {
        val sourceDir = C_SourceDir.mapDirOf(
            "top1/module.rell" to "@test module;",
            "top1/mid1/module.rell" to "@test module;",
            "top1/mid1/sub1/module.rell" to "@test module; import top2;",
            "top2/module.rell" to "@test module;",
            "top2/mid2/module.rell" to "@test module;",
            "top2/mid2/sub2/module.rell" to "@test module; import top1;",
        )
        chkCompileTestModules(sourceDir, listOf(), listOf("top1"), "[[*top1],[*top1.mid1],[*top1.mid1.sub1],[top2]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("top2"), "[[top1],[*top2],[*top2.mid2],[*top2.mid2.sub2]]")
        chkCompileTestModules(sourceDir, listOf(), listOf("top1", "top2"),
            "[[*top1],[*top1.mid1],[*top1.mid1.sub1],[*top2],[*top2.mid2],[*top2.mid2.sub2]]")
    }

    private fun chkCompileTestModules(
            sourceDir: C_SourceDir,
            modules: List<String>,
            testModules: List<String>,
            expected: String
    ) {
        val actual = evalCompileAllModules(sourceDir, modules, testModules)
        assertEquals(expected, actual)
    }

    private fun evalCompileAllModules(sourceDir: C_SourceDir, modules: List<String>, testModules: List<String>): String {
        val modules2 = modules.mapToImmList { ModuleName.of(it) }
        val testModules2 = testModules.mapToImmList { ModuleName.of(it) }
        val modSel = C_CompilerModuleSelection(modules2, testModules2)

        val res = try {
            RellTestUtils.compileApp(sourceDir, modSel, C_CompilerOptions.DEFAULT)
        } catch (e: C_CommonError) {
            return "cm_err:${e.code}"
        }

        if (res.errors.isNotEmpty()) {
            val s = RellTestUtils.msgsToString(res.errors, false)
            return "ct_err:$s"
        }

        val rrApp = res.rrApp
        assertNotNull(rrApp)

        return rrApp.modules.sortedBy { it.name } .joinToString(",", "[", "]") {
            val sel = if (it.selected) "*" else ""
            "[$sel${it.name}]"
        }
    }

    @Test fun testParentModuleOfMainModule() {
        file("module.rell", "module; function f_root() {}")
        file("a/module.rell", "module; function f_a() {}")
        file("a/b/module.rell", "module; function f_b() {}")
        file("a/b/c/module.rell", "module; function f_c() {}")
        mainModule("a.b.c")
        chkAppFns("f_a", "f_b", "f_c", "f_root")
    }

    @Test fun testParentModuleOfImportedModule() {
        file("module.rell", "module; function f_root() {}")
        file("a/module.rell", "module; function f_a() {}")
        file("a/b/module.rell", "module; function f_b() {}")
        file("a/b/c/module.rell", "module; function f_c() {}")
        file("entry.rell", "module; import a.b.c;")
        mainModule("entry")
        chkAppFns("f_a", "f_b", "f_c", "f_root")
    }

    @Test fun testParentModuleOfUnreferencedModule() {
        file("module.rell", "module; function f_root() {}")
        file("a/module.rell", "module; function f_a() {}")
        file("a/b/module.rell", "module; function f_b() {}")
        file("a/b/c/module.rell", "module; function f_c() {}")
        chkAppFns("f_root")
    }

    @Test fun testParentModuleOfTestModule() {
        file("module.rell", "module; function f_root() {}")
        file("a/module.rell", "module; function f_a() {}")
        file("a/b/module.rell", "module; function f_b() {}")
        file("a/b/c/module.rell", "@test module; function f_c() {}")
        tst.testModules("a.b.c")
        chkAppFns("f_c", "f_root")
    }

    @Test fun testParentModuleOfTestModule2() {
        file("module.rell", "@test module; function f_root() {}")
        file("a/module.rell", "@test module; function f_a() {}")
        file("a/b/module.rell", "@test module; function f_b() {}")
        file("a/b/c/module.rell", "@test module; function f_c() {}")
        tst.testModules("a.b.c")
        chkAppFns("f_c", "f_root")
    }

    @Test fun testFileModuleAsTestModule() {
        file("foo/bar.rell", "@test module; function foo_bar_f() {}")
        tst.testModules("foo.bar")
        chkAppFns("foo_bar_f")
    }

    @Test fun testRegularModuleAsTestModule() {
        file("a/b/c.rell", "module; function c_f() {}")
        file("a/b/d.rell", "@test module; function d_f() {}")
        file("a/b/e/module.rell", "module; function e_f() {}")
        file("a/b/f/module.rell", "@test module; function f_f() {}")
        tst.testModules("a")
        chkAppFns("d_f", "f_f")
    }

    @Test fun testRellModuleFile() {
        file("rell.rell", "module;")
        file("a/rell.rell", "module; val X = 123;")
        chkCompile("import rell;", "ct_err:[main.rell:import:not_found:rell][rell.rell:module:reserved_name:rell]")
        chkCompile("import r: rell;", "ct_err:[main.rell:import:not_found:rell][rell.rell:module:reserved_name:rell]")
        chkCompile("import r: a.rell; function f() = r.X;", "OK")
    }

    @Test fun testRellModuleDir() {
        file("rell/module.rell", "")
        file("rell/a.rell", "module;")
        file("a/rell.rell", "module; val X = 123;")
        chkCompile("import rell;", "ct_err:import:not_found:rell")
        chkCompile("import r: rell;", "ct_err:import:not_found:rell")
        chkCompile("import rell.a;", "ct_err:[main.rell:import:not_found:rell.a][rell/a.rell:module:reserved_name:rell.a]")
        chkCompile("import r: a.rell; function f() = r.X;", "OK")
    }

    @Test fun testMountNameInReplImportParentFirst() {
        file("a/module.rell", "@mount('foo') module; entity data_a {}")
        file("a/b/module.rell", "module; entity data_b {}")
        repl.chk("import a;", "")
        repl.chk("rell.meta(a.data_a).mount_name", "RES:text[foo.data_a]")
        repl.chk("import a.b;", "")
        repl.chk("rell.meta(b.data_b).mount_name", "RES:text[foo.data_b]")
    }

    @Test fun testMountNameInReplImportChildFirst() {
        file("a/module.rell", "@mount('foo') module; entity data_a {}")
        file("a/b/module.rell", "module; entity data_b {}")
        repl.chk("import a.b;", "")
        repl.chk("rell.meta(b.data_b).mount_name", "RES:text[foo.data_b]")
        repl.chk("import a;", "")
        repl.chk("rell.meta(a.data_a).mount_name", "RES:text[foo.data_a]")
    }

    private fun chkAppFns(vararg expected: String) {
        val rrApp = tst.compileAppEx("")
        val fns = rrApp.modules.flatMap { it.functions.values.map { it.base.simpleName } }.sorted()
        assertEquals(listOf(*expected), fns)
    }

    private fun chkImp(imp: String, code: String, exp: String) {
        chkFull("$imp query q() = $code;", exp)
    }
}
