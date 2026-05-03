/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.ide

import net.postchain.rell.base.compiler.base.utils.C_SourceDir
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.utils.ide.IdeApi
import net.postchain.rell.base.utils.ide.IdeModuleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IdeModuleInfoTest {
    @Test fun testFolderTestModuleHeaderFile() {
        val sourceDir = C_SourceDir.mapDirOf(
            "tests/module.rell" to "@test module;",
            "tests/test_one.rell" to "function test_foo() {}",
            "tests/test_two.rell" to "function test_bar() {}",
        )
        chkInfo(sourceDir, "tests/module.rell", "tests", directory = true, test = true)
    }

    @Test fun testFolderTestModuleNonHeaderFiles() {
        val sourceDir = C_SourceDir.mapDirOf(
            "tests/module.rell" to "@test module;",
            "tests/test_one.rell" to "function test_foo() {}",
            "tests/test_two.rell" to "function test_bar() {}",
        )
        chkInfo(sourceDir, "tests/test_one.rell", "tests", directory = true, test = true)
        chkInfo(sourceDir, "tests/test_two.rell", "tests", directory = true, test = true)
    }

    @Test fun testFolderAppModuleNonHeaderFiles() {
        val sourceDir = C_SourceDir.mapDirOf(
            "app/module.rell" to "module;",
            "app/part.rell" to "function helper() {}",
        )
        chkInfo(sourceDir, "app/module.rell", "app", directory = true, test = false)
        chkInfo(sourceDir, "app/part.rell", "app", directory = true, test = false)
    }

    @Test fun testNoSiblingModuleFile() {
        val sourceDir = C_SourceDir.mapDirOf(
            "scripts/loose.rell" to "function helper() {}",
        )
        chkInfo(sourceDir, "scripts/loose.rell", "scripts", directory = true, test = false)
    }

    @Test fun testSingleFileTestModule() {
        val sourceDir = C_SourceDir.mapDirOf(
            "single_test.rell" to "@test module;",
        )
        chkInfo(sourceDir, "single_test.rell", "single_test", directory = false, test = true)
    }

    @Test fun testSingleFileAppModule() {
        val sourceDir = C_SourceDir.mapDirOf(
            "single.rell" to "module; query q() = 0;",
        )
        chkInfo(sourceDir, "single.rell", "single", directory = false, test = false)
    }

    @Test fun testLegacyOverloadDoesNotInferTest() {
        val sourceDir = C_SourceDir.mapDirOf(
            "tests/module.rell" to "@test module;",
            "tests/test_one.rell" to "function test_foo() {}",
        )
        val path = C_SourcePath.parse("tests/test_one.rell")
        val ast = sourceDir.file(path)!!.readAst()
        val info = IdeApi.getModuleInfo(path, ast)
        assertNotNull(info)
        assertEquals("tests", info.name.str())
        assertEquals(true, info.directory)
        assertEquals(false, info.test)
        assertEquals(true, info.app)
    }

    private fun chkInfo(
        sourceDir: C_SourceDir,
        filePath: String,
        expectedName: String,
        directory: Boolean,
        test: Boolean,
    ) {
        val path = C_SourcePath.parse(filePath)
        val sourceFile = sourceDir.file(path) ?: error("File not found: $filePath")
        val ast = sourceFile.readAst()
        val info: IdeModuleInfo? = IdeApi.getModuleInfo(sourceDir, path, ast)
        assertNotNull(info, "Expected module info for $filePath")
        assertEquals(expectedName, info.name.str(), "module name for $filePath")
        assertEquals(directory, info.directory, "directory flag for $filePath")
        assertEquals(test, info.test, "test flag for $filePath")
        assertEquals(!test, info.app, "app flag for $filePath")
    }
}
