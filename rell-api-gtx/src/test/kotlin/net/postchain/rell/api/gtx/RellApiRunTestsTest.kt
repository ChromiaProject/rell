/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.Rt_TestPrinter
import kotlin.test.Test

internal class RellApiRunTestsTest: BaseRellApiRunTestsTest() {
    @Test fun testRunTestsBasic() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to "@test module; function test_1(){} function test_2(){} function test_3(){}",
        )
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"),
            "test:test_1:OK", "test:test_2:OK", "test:test_3:OK"
        )
    }

    @Test fun testRunTestsStopOnError() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to "@test module; function test_1(){} function test_2(){ assert_true(false); } function test_3(){}",
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"),
            "test:test_1:OK", "test:test_2:FAILED", "test:test_3:OK"
        )

        val runConfig1 = runConfig.toBuilder().stopOnError(false).build()
        chkRunTests(runConfig1, sourceDir, listOf(), listOf("test"),
            "test:test_1:OK", "test:test_2:FAILED", "test:test_3:OK"
        )

        val runConfig2 = runConfig.toBuilder().stopOnError(true).build()
        chkRunTests(runConfig2, sourceDir, listOf(), listOf("test"), "test:test_1:OK", "test:test_2:FAILED")
    }

    @Test fun testRunTestsSubModules() {
        val compileConfig1 = configBuilder().includeTestSubModules(true).build()
        val compileConfig2 = configBuilder().includeTestSubModules(false).build()
        val runConfig1 = RellApiRunTests.Config.Builder().compileConfig(compileConfig1).build()
        val runConfig2 = RellApiRunTests.Config.Builder().compileConfig(compileConfig2).build()

        val sourceDir = C_SourceDir.mapDirOf(
            "a/module.rell" to "module;",
            "a/b/module.rell" to "@test module; function test_1(){}",
            "a/b/c/module.rell" to "@test module; function test_2(){}",
            "a/b/c/d.rell" to "@test module; function test_3(){}",
        )

        chkRunTests(runConfig1, sourceDir, listOf(), listOf("a.b"),
            "a.b:test_1:OK", "a.b.c:test_2:OK", "a.b.c.d:test_3:OK")
        chkRunTests(runConfig2, sourceDir, listOf(), listOf("a.b"), "a.b:test_1:OK")
        chkRunTests(runConfig1, sourceDir, listOf(), listOf("a.b.c"), "a.b.c:test_2:OK", "a.b.c.d:test_3:OK")
        chkRunTests(runConfig2, sourceDir, listOf(), listOf("a.b.c"), "a.b.c:test_2:OK")
        chkRunTests(runConfig1, sourceDir, listOf(), listOf("a.b.c.d"), "a.b.c.d:test_3:OK")
        chkRunTests(runConfig2, sourceDir, listOf(), listOf("a.b.c.d"), "a.b.c.d:test_3:OK")
        chkRunTests(runConfig1, sourceDir, listOf(), listOf("a"),
            "a.b:test_1:OK", "a.b.c:test_2:OK", "a.b.c.d:test_3:OK")
        chkRunTests(runConfig2, sourceDir, listOf(), listOf("a"), "CME:module:not_test:a")
        chkRunTests(runConfig1, sourceDir, listOf(), listOf(""),
            "a.b:test_1:OK", "a.b.c:test_2:OK", "a.b.c.d:test_3:OK")
        chkRunTests(runConfig2, sourceDir, listOf(), listOf(""), "CME:import:not_found:")
    }

    @Test fun testRunTestsModuleNotFound() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        chkRunTests(runConfig, generalSourceDir, listOf(), listOf("foo"), "CME:import:not_found:foo")
    }

    @Test fun testRunTestsAllAppModules() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to "module; @extendable function f(): integer?;",
            "def.rell" to "module; import lib; @extend(lib.f) function() = 123;",
            "test.rell" to "@test module; import lib; function test_1(){ assert_equals(lib.f(), 123); }",
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "test:test_1:FAILED")
        chkRunTests(runConfig, sourceDir, listOf("lib"), listOf("test"), "test:test_1:FAILED")
        chkRunTests(runConfig, sourceDir, listOf("def"), listOf("test"), "test:test_1:OK")
        chkRunTests(runConfig, sourceDir, null, listOf("test"), "test:test_1:OK")
    }

    @Test fun testRunTestsModuleArgs() {
        val sourceDir = C_SourceDir.mapDirOf(
            "foo.rell" to """
                @test module;
                struct module_args { x: integer; }
                function test_1() { assert_equals(chain_context.args.x, 123); }
            """,
            "bar.rell" to """
                @test module;
                struct module_args { y: text; }
                function test_2() { assert_equals(chain_context.args.y, 'Hello'); }
            """,
        )

        val fooArgs = mapOf("x" to GtvFactory.gtv(123))
        val barArgs = mapOf("y" to GtvFactory.gtv("Hello"))

        var runConfig = RellApiRunTests.Config.Builder().build()
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "CME:module_args_missing:bar,foo")
        runConfig = runTestsConfig(configBuilder().moduleArgs())
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "CME:module_args_missing:bar,foo")
        runConfig = runTestsConfig(configBuilder().moduleArgs("foo" to fooArgs))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "CME:module_args_missing:bar")
        runConfig = runTestsConfig(configBuilder().moduleArgs("bar" to barArgs))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "CME:module_args_missing:foo")
        runConfig = runTestsConfig(configBuilder().moduleArgs("foo" to barArgs, "bar" to barArgs))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""),
            "CME:module_args_bad:foo:gtv_err:struct_noattr:foo:module_args:x")
        runConfig = runTestsConfig(configBuilder().moduleArgs("foo" to fooArgs, "bar" to fooArgs))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""),
            "CME:module_args_bad:bar:gtv_err:struct_noattr:bar:module_args:y")
        runConfig = runTestsConfig(configBuilder().moduleArgs("foo" to fooArgs, "bar" to barArgs))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "bar:test_2:OK", "foo:test_1:OK")

        val fooArgs2 = mapOf("x" to GtvFactory.gtv(456))
        val barArgs2 = mapOf("y" to GtvFactory.gtv("Bye"))
        runConfig = runTestsConfig(configBuilder().moduleArgs("foo" to fooArgs2, "bar" to barArgs))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "bar:test_2:OK", "foo:test_1:FAILED")
        runConfig = runTestsConfig(configBuilder().moduleArgs("foo" to fooArgs, "bar" to barArgs2))
        chkRunTests(runConfig, sourceDir, listOf(), listOf(""), "bar:test_2:FAILED", "foo:test_1:OK")
    }

    @Test fun testRunTestsMainModules() {
        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to "module; operation op() {}",
            "test.rell" to "@test module; import lib; function test() { lib.op().run(); }",
        )

        var runConfig = runTestsDbConfig()
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "test:test:OK")
        runConfig = runConfig.toBuilder().activateTestDependencies(false).build()
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "test:test:FAILED")
        chkRunTests(runConfig, sourceDir, listOf("lib"), listOf("test"), "test:test:OK")
    }

    @Test fun testRunTestsModuleArgsOperation() {
        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to """
                module;
                struct module_args { x: integer; }
                operation op() { require(chain_context.args.x > 0); }
            """,
            "test.rell" to """
                @test module;
                import lib;
                function test() {
                    print('begin');
                    lib.op().run();
                    print('end');
                }
            """,
        )

        var runConfig = runTestsDbConfig()
        chkRunTests(runConfig, sourceDir, listOf("lib"), listOf("test"), "CME:module_args_missing:lib")
        runConfig = runTestsConfig(configBuilder().moduleArgs("lib" to mapOf("x" to GtvFactory.gtv(123))), runConfig)
        chkRunTests(runConfig, sourceDir, listOf("lib"), listOf("test"), "test:test:OK")
        runConfig = runTestsConfig(configBuilder().moduleArgs("lib" to mapOf("x" to GtvFactory.gtv(1))), runConfig)
        chkRunTests(runConfig, sourceDir, listOf("lib"), listOf("test"), "test:test:OK")
        runConfig = runTestsConfig(configBuilder().moduleArgs("lib" to mapOf("x" to GtvFactory.gtv(0))), runConfig)
        chkRunTests(runConfig, sourceDir, listOf("lib"), listOf("test"), "test:test:FAILED")
    }

    @Test fun testCompilationError() {
        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to "module; function f() { print(__unknown__); }",
            "test.rell" to "@test module; import lib; function test() {}",
        )

        val runConfig = RellApiRunTests.Config.Builder().build()
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "CTE:lib.rell:unknown_name:__unknown__")
    }

    @Test fun testFunctionExtendCallFromTest() {
        val sourceDir = initFnExtendSourceDir(
            "app.rell" to "module;",
            "test.rell" to """
                @test module;
                import f; import g; import h;
                function test() { print(f.f()); }
            """,
        )

        chkFnExtendDepsYes(sourceDir, null)
        chkFnExtendDepsYes(sourceDir, true)
        chkFnExtendDepsNo(sourceDir)
    }

    @Test fun testFunctionExtendCallFromOperation() {
        val sourceDir = initFnExtendSourceDir(
            "app.rell" to "module; import f; operation op() { print(f.f()); }",
            "test.rell" to """
                @test module;
                import app; import g; import h;
                function test() { app.op().run(); }
            """,
        )

        chkFnExtendDepsYes(sourceDir, null)
        chkFnExtendDepsYes(sourceDir, true)

        chkFnExtend(false, sourceDir, listOf(), listOf("test"), err = true)
        chkFnExtendDepsNo(sourceDir)
    }

    private fun initFnExtendSourceDir(vararg extra: Pair<String, String>): C_SourceDir {
        return C_SourceDir.mapDirOf(
            "f/a.rell" to "@extendable function f(): list<text> = ['f'];",
            "f/b.rell" to "@extend(f) function e() = ['e'];",
            "g.rell" to "module; import f.*; @extend(f) function g() = ['g'];",
            "h.rell" to "module; import f.*; @extend(f) function h() = ['h'];",
            *extra,
        )
    }

    private fun chkFnExtendDepsYes(sourceDir: C_SourceDir, useTestDeps: Boolean?) {
        chkFnExtend(useTestDeps, sourceDir, listOf(), listOf("test"), "[e, g, h, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf("g"), listOf("test"), "[g, e, h, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf("h"), listOf("test"), "[h, e, g, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf("g", "h"), listOf("test"), "[g, e, h, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf(), listOf("g", "h", "test"), "[e, g, h, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf("g"), listOf("g", "h", "test"), "[g, e, h, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf("h"), listOf("g", "h", "test"), "[h, e, g, f]")
        chkFnExtend(useTestDeps, sourceDir, listOf("g", "h"), listOf("g", "h", "test"), "[g, e, h, f]")
    }

    private fun chkFnExtendDepsNo(sourceDir: C_SourceDir) {
        chkFnExtend(false, sourceDir, listOf("app"), listOf("test"), "[e, f]")
        chkFnExtend(false, sourceDir, listOf("g", "app"), listOf("test"), "[g, e, f]")
        chkFnExtend(false, sourceDir, listOf("h", "app"), listOf("test"), "[h, e, f]")
        chkFnExtend(false, sourceDir, listOf("g", "h", "app"), listOf("test"), "[g, e, h, f]")
        chkFnExtend(false, sourceDir, listOf("app"), listOf("g", "h", "test"), "[e, f]")
        chkFnExtend(false, sourceDir, listOf("g", "app"), listOf("g", "h", "test"), "[g, e, f]")
        chkFnExtend(false, sourceDir, listOf("h", "app"), listOf("g", "h", "test"), "[h, e, f]")
        chkFnExtend(false, sourceDir, listOf("g", "h", "app"), listOf("g", "h", "test"), "[g, e, h, f]")
    }

    private fun chkFnExtend(
        useTestDeps: Boolean?,
        sourceDir: C_SourceDir,
        appModules: List<String>?,
        testModules: List<String>,
        vararg expectedOut: String,
        err: Boolean = false,
    ) {
        val compileConfig = configBuilder().appModuleInTestsError(false).build()

        val printer = Rt_TestPrinter()

        val runConfig = runTestsDbConfig().toBuilder()
            .compileConfig(compileConfig)
            .outPrinter(printer)
            .also { if (useTestDeps != null) it.activateTestDependencies(useTestDeps) }
            .build()

        val expRes = if (err) "test:test:FAILED" else "test:test:OK"
        chkRunTests(runConfig, sourceDir, appModules, testModules, expRes)
        printer.chk(*expectedOut)
    }

    @Test fun testRellVersion() {
        chkRellVersion("0.0.1", "CME:config:version:unknown")
        chkRellVersion("100.100.100", "CME:config:version:unknown")
        chkRellVersion("0.10.8", "CME:config:version:unsupported")
        chkRellVersion(RellTestUtils.NEXT_VER, "CME:config:version:unknown")
    }

    private fun chkRellVersion(version: String, err: String) {
        val config = configBuilder().version(version).build()
        val runConfig = RellApiRunTests.Config.Builder().compileConfig(config).build()
        val sourceDir = C_SourceDir.mapDirOf("test.rell" to "@test module; function test_1(){}")
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "$err:$version")
    }

    @Test fun testMerkleHashOperation() {
        val runConfig = runTestsDbConfig()

        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to """
                module;
                entity data { h: byte_array; }
                operation op(v: gtv) { create data(v.hash()); }
            """,
            "test.rell" to """
                @test module;
                import lib;
                function chk(s: text, exp: byte_array) {
                    lib.op(gtv.from_json(s)).run();
                    val act = lib.data@{}(.h);
                    assert_equals(act, exp);
                }
                function test_1() { chk('[[]]', x'46af9064f12528cad6a7c377204acd0ac38cdc6912903e7dab3703764c8dd5e5'); }
                function test_2() { chk('[[]]', x'b27d13915e478770d8cbaaf72d2c92f67a17250b2c40c9a7b36c3e996ae5fad7'); }
                function test_3() { chk('[{}]', x'5ac6c92dffe0a0defa0581023e84c3d344a42d4ff90fc2a3af0d40dbf8d7a622'); }
            """,
        )
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"),
            "test:test_1:FAILED", "test:test_2:OK", "test:test_3:OK",
        )
    }

    @Test fun testMerkleHashVersionControl() {
        val sourceDir = C_SourceDir.mapDirOf(
            "lib.rell" to "module; operation op(v: gtv) { print('op', v.hash()); }",
            "test.rell" to """
                @test module;
                import lib;
                function chk(s: text) {
                    print('input', s);
                    val g = gtv.from_json(s);
                    print('direct', g.hash());
                    lib.op(g).run();
                }
                function test_1() { chk('[[]]'); }
                function test_2() { chk('[{}]'); }
            """,
        )

        chkVersionControl(sourceDir, "0.14.5", listOf("test:test_1:OK", "test:test_2:OK"),
            "input [[]]",
            "direct 0xb27d13915e478770d8cbaaf72d2c92f67a17250b2c40c9a7b36c3e996ae5fad7",
            "op 0xb27d13915e478770d8cbaaf72d2c92f67a17250b2c40c9a7b36c3e996ae5fad7",
            "input [{}]",
            "direct 0x5ac6c92dffe0a0defa0581023e84c3d344a42d4ff90fc2a3af0d40dbf8d7a622",
            "op 0x5ac6c92dffe0a0defa0581023e84c3d344a42d4ff90fc2a3af0d40dbf8d7a622",
        )

        chkVersionControl(sourceDir, "0.14.4", listOf("test:test_1:OK", "test:test_2:OK"),
            "input [[]]",
            "direct 0x46af9064f12528cad6a7c377204acd0ac38cdc6912903e7dab3703764c8dd5e5",
            "op 0x46af9064f12528cad6a7c377204acd0ac38cdc6912903e7dab3703764c8dd5e5",
            "input [{}]",
            "direct 0x46af9064f12528cad6a7c377204acd0ac38cdc6912903e7dab3703764c8dd5e5",
            "op 0x46af9064f12528cad6a7c377204acd0ac38cdc6912903e7dab3703764c8dd5e5",
        )
    }

    private fun chkVersionControl(
        sourceDir: C_SourceDir,
        version: String,
        expStatus: List<String>,
        vararg expOut: String,
    ) {
        val printer = Rt_TestPrinter()

        val runConfig0 = runTestsDbConfig()
        val runConfig = runTestsDbConfig().toBuilder()
            .compileConfig(runConfig0.compileConfig.toBuilder().version(version).build())
            .outPrinter(printer)
            .build()

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), *expStatus.toTypedArray())
        printer.chk(*expOut)
    }

    @Test fun testFunctionTestAnnotation() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to """
                @test module;
                function successor(x: integer) { return x + 1; }
                @test function case1() { assert_equals(successor(-1), 0); }
                function test_case2() { assert_equals(successor(0), 1); }
                function test() { assert_false(true); }
                @test function thisTestFails() { assert_true(false); }
            """,
        )
        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"),
            "test:case1:OK",
            "test:test_case2:OK",
            "test:test:FAILED",
            "test:thisTestFails:FAILED"
        )
    }

    @Test fun testCompoundSingularOperations() {
        val runConfig = runTestsDbConfig()
        val sourceDir = C_SourceDir.mapDirOf(
            "op_mods.rell" to """
                module;
                entity person { name; }
                operation add_person(name) { create person(name); }
                @singular operation add_person_singular(name) { create person(name); }
                @compound operation add_person_compound(name) { create person(name); }
                @singular @compound operation add_person_singular_compound(name) { create person(name); }
            """,
            "op_mods_test.rell" to """
                @test module;
                import op_mods.*;

                @test function test__singular__twice_same_args__fails() {
                    rell.test.tx([
                        add_person_singular('Alice'),
                        add_person_singular('Alice')
                    ]).run_must_fail("contains more than one");
                }

                @test function test__compound__only_operation__fails() {
                    rell.test.tx([add_person_compound('Bob')]).run_must_fail("contains no normal operation");
                }

                @test function test__singular__twice_nonconsecutive__fails() {
                    rell.test.tx([
                        add_person_singular('Alice'),
                        add_person('Charlie'),
                        add_person_singular('Bob')
                    ]).run_must_fail("contains more than one");
                }

                @test function test__singular_compound__twice_no_normal__fails() {
                    rell.test.tx([
                        add_person_singular_compound('Bob'),
                        add_person_singular_compound('Alice')
                    ]).run_must_fail("contains more than one");
                }
            """,
        )
        chkRunTests(runConfig, sourceDir, listOf(), listOf("op_mods_test"),
            "op_mods_test:test__singular__twice_same_args__fails:OK",
            "op_mods_test:test__compound__only_operation__fails:OK",
            "op_mods_test:test__singular__twice_nonconsecutive__fails:OK",
            "op_mods_test:test__singular_compound__twice_no_normal__fails:OK",
        )
    }

    @Test fun testSpecialOperation() {
        val runConfig = runTestsDbConfig()
        val sourceDir = C_SourceDir.mapDirOf(
            "special_op.rell" to """
                module;
                entity system_data { id: text; }
                operation __add_system_id(id: text) { create system_data(id); }
            """,
            "special_op_test.rell" to """
                @test module;
                import special_op.*;

                @test function test__special_operation() {
                    assert_equals((system_data @* {}).size(), 0);
                    rell.test.tx([
                        __add_system_id("one"),
                        __add_system_id("two")
                    ]).run();
                    assert_equals((system_data @* {}).size(), 2);
                }
            """,
        )
        chkRunTests(runConfig, sourceDir, listOf(), listOf("special_op_test"),
            "special_op_test:test__special_operation:OK",
        )
    }

    @Test fun testSizeConstrainedParameterOperation() {
        val runConfig = runTestsDbConfig()
        val sourceDir = C_SourceDir.mapDirOf(
            "size_param_op.rell" to """
                module;
                entity a { x: text; }
                entity b { x: byte_array; }
                operation foo(@min_size(1) id: text) { create a(id); }
                operation bar(@max_size(50) desc: text) { create a(desc); }
                operation baz(@size(32) arr: byte_array) { create b(arr); }
            """,
            "size_param_op_test.rell" to """
                @test module;
                import size_param_op.*;

                function foo_too_small_op_val(): rell.test.op {
                    return foo('');
                }

                @test function test_foo_too_small() {
                    assert_equals((a @* {}).size(), 0);
                    assert_fails("Parameter id of operation foo: size too small", foo_too_small_op_val(*));
                    assert_equals((a @* {}).size(), 0);
                }

                @test function test_foo_ok() {
                    assert_equals((a @* {}).size(), 0);
                    rell.test.tx([foo('hello')]).run();
                    assert_equals((a @* {}).size(), 1);
                }

                function bar_too_large_op_val(): rell.test.op {
                    return bar('this message is greater than fifty characters in length.');
                }

                @test function test_bar_too_large() {
                    assert_equals((a @* {}).size(), 0);
                    assert_fails("Parameter desc of operation bar: size too large", bar_too_large_op_val(*));
                    assert_equals((a @* {}).size(), 0);
                }

                @test function test_bar_ok() {
                    assert_equals((a @* {}).size(), 0);
                    rell.test.tx([bar('this message is shorter.')]).run();
                    assert_equals((a @* {}).size(), 1);
                }

                @test function test_baz_too_small() {
                    assert_equals((b @* {}).size(), 0);
                    rell.test.tx([rell.test.op('baz', x'CAFEC0FFEE'.to_gtv())])
                        .run_must_fail("Parameter arr of operation baz: size too small");
                    assert_equals((b @* {}).size(), 0);
                }

                @test function test_baz_too_large() {
                    assert_equals((b @* {}).size(), 0);
                    rell.test.tx([rell.test.op('baz', x'0123456789ABCDEF'.repeat(8).to_gtv())])
                        .run_must_fail("Parameter arr of operation baz: size too large");
                    assert_equals((b @* {}).size(), 0);
                }

                @test function test_baz_ok() {
                    assert_equals((b @* {}).size(), 0);
                    rell.test.tx([baz(x'0123456789ABCDEF'.repeat(4))]).run();
                    assert_equals((b @* {}).size(), 1);
                }
            """,
        )
        chkRunTests(runConfig, sourceDir, listOf(), listOf("size_param_op_test"),
            "size_param_op_test:test_foo_too_small:OK",
            "size_param_op_test:test_foo_ok:OK",
            "size_param_op_test:test_bar_too_large:OK",
            "size_param_op_test:test_bar_ok:OK",
            "size_param_op_test:test_baz_too_small:OK",
            "size_param_op_test:test_baz_too_large:OK",
            "size_param_op_test:test_baz_ok:OK",
        )
    }
}
