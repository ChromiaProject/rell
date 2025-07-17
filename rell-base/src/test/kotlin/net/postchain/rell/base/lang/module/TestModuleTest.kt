/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lang.module

import net.postchain.rell.base.testutils.BaseRellTest
import net.postchain.rell.base.testutils.expCtError
import kotlin.test.Test

class TestModuleTest: BaseRellTest() {
    @Test fun testRunTests() {
        file("some_tests.rell", """
            @test module;
            function test_foo() { print("foo"); require(true); }
            function test_bar() { print("bar"); require(false); }
            function not_a_test() { print("not_a_test"); }
        """)
        chkTests("some_tests", "test_foo=OK,test_bar=req_err:null")
        chkOut("foo", "bar")
    }

    @Test fun testSimpleFunctionAnnotation() {
        file("some_tests.rell", """
            @test module;
            @test function foo() { print("foo"); require(true); }
            @test function bar() { print("bar"); require(false); }
            function not_a_test() { print("not_a_test"); }
        """)
        chkTests("some_tests", "foo=OK,bar=req_err:null")
        chkOut("foo", "bar")
    }

    @Test fun testMixedTestFunctionNameAndFunctionAnnotation() {
        file("some_tests.rell", """
            @test module;
            @test function foo() { print("foo"); require(true); }
            @test function bar() { print("bar"); require(false); }
            function test_foo() { print("foo"); require(true); }
            function test_bar() { print("bar"); require(false); }
            function not_a_test() { print("not_a_test"); }
        """)
        chkTests("some_tests", "foo=OK,bar=req_err:null,test_foo=OK,test_bar=req_err:null")
        chkOut("foo", "bar", "foo", "bar")
    }

    @Test fun testImportTestFromRegular() {
        file("test_module.rell", "@test module;")
        file("regular_module.rell", "module; import test_module;")
        chkCompile("import regular_module;", "ct_err:regular_module.rell:import:module_test:test_module")
    }

    @Test fun testImportRegularFromTest() {
        file("regular_module.rell", "module; function f() = 123;")
        file("test_module.rell", "@test module; import regular_module; function test() { print(regular_module.f()); }")
        chkTests("test_module", "test=OK")
        chkOut("123")
    }

    @Test fun testImportTestFromTest() {
        file("helper_module.rell", "@test module; function f() = 123;")
        file("test_module.rell", "@test module; import helper_module; function test() { print(helper_module.f()); }")
        chkTests("test_module", "test=OK")
        chkOut("123")
    }

    @Test fun testAbstractTestModule() {
        file("a.rell", "abstract @test module;")
        file("b.rell", "@test abstract module;")
        chkCompile("import a;", "ct_err:[main.rell:import:module_test:a][a.rell:modifier:bad_combination:kw:abstract,ann:test]")
        chkCompile("import b;", "ct_err:[main.rell:import:module_test:b][b.rell:modifier:bad_combination:ann:test,kw:abstract]")
    }

    @Test fun testDirectoryModuleAsTest() {
        file("some_tests/module.rell", "@test module;")
        file("some_tests/foo.rell", "function test_foo() { print('foo'); require(true); }")
        file("some_tests/bar.rell", "function test_bar() { print('bar'); require(true); }")
        chkTests("some_tests", "test_bar=OK,test_foo=OK")
        chkOut("bar", "foo")
    }

    @Test fun testMountName() {
        file("test.rell", "@mount('mytest') @test module; function test_foo() {}")
        chkCompile("", "OK") //TODO must fail
    }

    @Test fun testUnallowedDefinitions() {
        tst.mainModule("main")
        chkCompile("@test module; operation op() {}", "ct_err:def_test:OPERATION")
        chkCompile("@test module; query q() = 123;", "ct_err:def_test:QUERY")
        chkCompile("@test module; entity user { name; }", "ct_err:def_test:ENTITY")
        chkCompile("@test module; object state { mutable v: integer = 0; }", "ct_err:def_test:OBJECT")
        chkCompile("module; operation op() {}", "OK")
    }

    @Test fun testFunctionAnnotationUnsupportedDefinitions() {
        tst.mainModule("main")
        chkCompile("module; @test operation op() {}", "ct_err:modifier:invalid:ann:test")
        chkCompile("module; @test query q() = 123;", "ct_err:modifier:invalid:ann:test")
        chkCompile("module; @test entity user { name; }", "ct_err:modifier:invalid:ann:test")
        chkCompile("module; @test object state { mutable v: integer = 0; }", "ct_err:modifier:invalid:ann:test")
    }

    @Test fun testNamespacedTestFunctions() {
        file("some_tests.rell", """
            @test module;
            function test_foo() { print("foo"); require(true); }
            namespace ns { function test_bar() { print("bar"); require(true); } }
        """)
        chkTests("some_tests", "test_foo=OK")
        chkOut("foo")
    }

    @Test fun testImportFromRepl() {
        file("tests.rell", "@test module; function f() = 123;")
        repl.chk("import tests;")
        repl.chk("tests.f()", "RES:int[123]")
    }

    @Test fun testTestModuleAsParentOfRegularModule() {
        file("foo/module.rell", "@test module;")
        file("foo/a.rell", "module;")
        file("foo/a_test.rell", "@test module; function test() {}")
        file("foo/b/module.rell", "module;")
        file("foo/c/empty.rell", "")

        chkCompile("import foo.a;", "ct_err:foo/a.rell:module:parent_is_test:foo.a:foo")
        chkCompile("import foo.b;", "ct_err:foo/b/module.rell:module:parent_is_test:foo.b:foo")
        chkCompile("import foo.c;", "OK") // Empty file - nowhere to report an error
        chkCompile("import foo.a_test;", "ct_err:import:module_test:foo.a_test")

        chkTests("foo.a_test", "test=OK")
    }

    @Test fun testMountNameOfTestModule() {
        file("tests.rell", "@test @mount('foo') module; function test() {}")
        chkTests("tests", "ct_err:tests.rell:modifier:bad_combination:ann:test,ann:mount")
    }

    @Test fun testSysNameConflict() {
        mainModule("main")
        chkCompile("module; function abs() {}", "ct_err:name_conflict:sys:abs:FUNCTION")
        chkCompile("@test module; function abs() {}", "ct_err:name_conflict:sys:abs:FUNCTION")
        chkCompile("@test module; function assert_equals() {}", "ct_err:name_conflict:sys:assert_equals:FUNCTION")
    }

    @Test fun testFunctionAnnotationOnIneligibleFunctions() {
        tst.mainModule("main")
        chkCompile("@test module; @test function foo(x: integer) {}", "ct_err:fn:test_function_with_parameters:main:foo")
        chkCompile("module; @test function foo() {}", "ct_err:fn:test_function_outside_test_module:main:foo")
    }

    @Test fun testFunctionAnnotationIncompatibleWithOtherModifiers() {
        tst.mainModule("main")
        file("a.rell", "abstract module; abstract function abst() {} @extendable function extd() {}")
        chkFunAnnIncompatible("@extendable", "foo", "ann:extendable", "def_test:fn_extendable:main:foo")
        chkFunAnnIncompatible("@extend(a.extd)", "foo", "ann:extend", "def_test:fn_extend:main:foo")
        chkFunAnnIncompatible("abstract", "foo", "kw:abstract", "fn:abstract:non_abstract_module:main:main:foo")
        chkFunAnnIncompatible("override", "a.abst", "kw:override")
    }

    private fun chkFunAnnIncompatible(modName: String, funName: String, errName: String, vararg extraErrs: String) {
        chkCompile("@test module; import a; @test $modName function $funName() {}",
            expCtError("modifier:bad_combination:ann:test,$errName", *extraErrs))
        chkCompile("@test module; import a; $modName @test function $funName() {}",
            expCtError("modifier:bad_combination:$errName,ann:test", *extraErrs))
    }
}
