/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import kotlin.test.Test

internal class RellApiRunTestsDisabledTest: BaseRellApiRunTestsTest() {
    @Test fun testDisabledFunctionSkipped() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to """
                @test module;
                @disabled @test function test_skip() { assert_true(false); }
                @test function test_run() {}
            """.trimIndent(),
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "test:test_run:OK")
    }

    @Test fun testDisabledLegacyFunctionSkipped() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to """
                @test module;
                @disabled function test_legacy_skip() { assert_true(false); }
                function test_legacy_run() {}
            """.trimIndent(),
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "test:test_legacy_run:OK")
    }

    @Test fun testDisabledModuleSkipsAllTests() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to """
                @test @disabled module;
                function test_first() { assert_true(false); }
                @test function test_second() {}
            """.trimIndent(),
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"))
    }

    @Test fun testDisabledModuleSkipsSubmoduleTests() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "a/module.rell" to """
                @test @disabled module;
            """.trimIndent(),
            "a/b.rell" to """
                @test module;
                @test function test_sub() { assert_true(false); }
            """.trimIndent(),
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("a"))
    }

    @Test fun testDisabledOnNonTestFunctionFails() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "test.rell" to """
                @test module;
                @disabled function foo() {}
            """.trimIndent(),
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("test"), "CTE:test.rell:fn:disabled:not_test:foo")
    }

    @Test fun testDisabledOnNonTestModuleFails() {
        val runConfig = RellApiRunTests.Config.Builder().build()
        val sourceDir = C_SourceDir.mapDirOf(
            "mod.rell" to "@disabled module;",
        )

        chkRunTests(runConfig, sourceDir, listOf(), listOf("mod"), "CTE:mod.rell:module:disabled:not_test")
    }
}
