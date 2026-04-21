/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.misc

import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CliSnippetsTest {
    @Test fun testMod() = chkModule("mod")
    @Test fun testModComplexBar() = chkModule("mod.complex.bar")
    @Test fun testModComplexFoo() = chkModule("mod.complex.foo")

    @Test fun testCalc() = chkModule("calc")
    @Test fun testLegacyTest() = chkModule("legacy_test")
    @Test fun testMisc() = chkModule("misc")
    @Test fun testRunSimple() = chkModule("run_simple")
    @Test fun testStair() = chkModule("stair")

    @Test fun testAbstr() = chkModule("abstr.main")
    @Test fun testStackTrace() = chkModule("stack_trace")

    @Test fun testRunTests() {
        chkTestModules("run_tests.bar", "run_tests.bar_extra_test", "run_tests.common_test")
        chkTestModules("run_tests.foo", "run_tests.foo_extra_test", "run_tests.common_test")
        chkTestModules("run_tests.foo_10")
        chkTestModules("run_tests.generic")

        chkModule("run_tests.bar.bar_test")
        chkModule("run_tests.foo.foo_test")
        chkModule("run_tests.foo_10.foo_10_test")
        chkModule("run_tests.generic.tests")
        chkModule("run_tests.bar_extra_test")
        chkModule("run_tests.foo_extra_test")
        chkModule("run_tests.common_test")
    }

    @Test fun testTests() {
        chkTestModules("tests")
        chkModule("tests.calc_test")
        chkModule("tests.data_test")
        chkModule("tests.op_mods_test")
        chkModule("tests.lib_test")
        chkModule("tests.foobar")
    }

    private fun chkModule(module: String) {
        val modules = immListOf(ModuleName.of(module))
        val modSel = C_CompilerModuleSelection(modules, immListOf())
        chkModules(modSel)
    }

    private fun chkTestModules(vararg modules: String) {
        val modNames = modules.mapToImmList { ModuleName.of(it) }
        val modSel = C_CompilerModuleSelection(immListOf(), modNames)
        chkModules(modSel)
    }

    private fun chkModules(modSel: C_CompilerModuleSelection) {
        // When run from IntelliJ as "All_tests", the working directory is the repository root, not the sub-project.
        val dir = listOf(".", "..", "../..").map { File(it, "work/testproj/src") }.find { it.isDirectory }
        checkNotNull(dir) { "Sources not found: ${File(".").absolutePath}" }
        val sourceDir = C_SourceDir.diskDir(dir)
        val res = RellTestUtils.compileApp(sourceDir, modSel, C_CompilerOptions.DEFAULT)
        assertEquals(0, res.messages.size, res.messages.toString())
    }
}
