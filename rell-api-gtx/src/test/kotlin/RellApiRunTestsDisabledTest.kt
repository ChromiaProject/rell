/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.base.compiler.base.utils.C_SourceDir.Companion.mapDirOf
import kotlin.test.Test

internal class RellApiRunTestsDisabledTest: BaseRellApiRunTestsTest() {
    private val cfg = RellApiRunTests.Config.DEFAULT

    private fun chkRunTests(
        moduleName: String,
        sourceCode: String,
        expected: String? = null,
        isAppModule: Boolean = false,
    ): Unit = chkRunTests(
        cfg,
        mapDirOf("$moduleName.rell" to sourceCode),
        if (isAppModule) listOf(moduleName) else emptyList(),
        if (isAppModule) emptyList() else listOf(moduleName),
        *expected?.let { arrayOf(it) } ?: arrayOf(),
    )

    @Test fun testDisabledFunctionSkipped() {
        chkRunTests("test", """
            @test module;
            @disabled @test function test_skip() { assert_true(false); }
            @test function test_run() {}
        """.trimIndent(), "test:test_run:OK")
    }

    @Test fun testDisabledLegacyFunctionSkipped() {
        chkRunTests("test", """
            @test module;
            @disabled function test_legacy_skip() { assert_true(false); }
            function test_legacy_run() {}
        """.trimIndent(), "test:test_legacy_run:OK")
    }

    @Test fun testDisabledModuleSkipsAllTests() {
        chkRunTests("test", """
            @test @disabled module;
            function test_first() { assert_true(false); }
            @test function test_second() {}
        """.trimIndent())
    }

    @Test fun testDisabledModuleSkipsSubmoduleTests() {
        val sourceDir = mapDirOf(
            "a/module.rell" to """
                @test @disabled module;
            """.trimIndent(),
            "a/b.rell" to """
                @test module;
                @test function test_sub() { assert_true(false); }
            """.trimIndent(),
        )

        chkRunTests(cfg, sourceDir, listOf(), listOf("a"))
    }

    @Test fun testDisabledOnNonTestFunctionFails() {
        chkRunTests("test", """
            @test module;
            @disabled function foo() {}
        """.trimIndent(), "CTE:test.rell:fn:disabled:not_test:foo")
    }

    @Test fun testDisabledOnNonTestModuleFails() {
        chkRunTests("mod", "@disabled module;", "CTE:mod.rell:module:disabled:not_test")
    }

    @Test fun testDisabledAnnotationUnsupportedDefinitions() {
        chkRunTests("test", "module; @disabled query q() = 1;",
            "CTE:test.rell:modifier:invalid:ann:disabled", isAppModule = true)

        chkRunTests("test", "module; @disabled operation op() {}",
            "CTE:test.rell:modifier:invalid:ann:disabled", isAppModule =  true)

        chkRunTests("test", "module; @disabled entity e { name; }",
            "CTE:test.rell:modifier:invalid:ann:disabled", isAppModule = true)

        chkRunTests("test", "module; @disabled object o { mutable v: integer = 0; }",
            "CTE:test.rell:modifier:invalid:ann:disabled", isAppModule = true)

        chkRunTests("test", "module; @disabled struct s { name; }",
            "CTE:test.rell:modifier:invalid:ann:disabled", isAppModule = true)
    }
}
