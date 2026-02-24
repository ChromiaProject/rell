package net.postchain.rell.toolbox.lsp.testrunner

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellTestRunnerTest : WorkspaceManagerTestBase() {

    @Test
    fun `Returns all test files in workspace`(@TempDir dir: File) {
        val firstTestFilePath = "directory/first_test_file.rell"
        val secondTestFilePath = "second_test_file.rell"

        val testDataBuilder = testData(dir) {
            addFile(
                firstTestFilePath,
                "@test module; function test_1() { return 1; }"
            )
            addFile(
                secondTestFilePath,
                "@test module; function test_2() { return 1; }"
            )
            addFile(
                "not_a_test_file.rell",
                "module; function test_like() { return 1; }"
            )
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)

        val firstTestFile = testDataBuilder.sourceFile(firstTestFilePath)
        val secondTestFile = testDataBuilder.sourceFile(secondTestFilePath)
        testRunner.getTestFiles(testDataBuilder.sourceFolderUri).let { testFiles ->
            assertThat(testFiles).containsOnly(
                createRellTestFile(
                    firstTestFile,
                    "directory.first_test_file",
                    createRellTestCase("test_1", Position(0, 14), Position(0, 44), firstTestFile)
                ),
                createRellTestFile(
                    secondTestFile,
                    "second_test_file",
                    createRellTestCase("test_2", Position(0, 14), Position(0, 44), secondTestFile)
                ),
            )
        }
    }

    @Test
    fun `Returns test files for active workspace only`(@TempDir dir: File) {
        val firstWorkspacePath = dir.resolve("workspace_one")
        val secondWorkspacePath = dir.resolve("workspace_two")

        val testFilePath = "directory/first_test_file.rell"
        val firstWsBuilder = testData(firstWorkspacePath) {
            addFile(
                testFilePath,
                "@test module; function test_1() { return 1; }"
            )
        }
        val secondWsBuilder = testData(secondWorkspacePath) {
            addFile(
                testFilePath,
                "@test module; function test_1() { return 1; }"
            )
        }

        initializeWorkspace(dir)

        val testRunner = RellTestRunner(indexingManager, symbolService)
        val firstWsTestFiles = testRunner.getTestFiles(firstWsBuilder.sourceFolderUri)
        val expectedTestFile = firstWsBuilder.sourceFile(testFilePath)

        assertThat(indexingManager.indexers.keys).containsOnly(firstWsBuilder.sourceFolderUri, secondWsBuilder.sourceFolderUri)
        assertThat(firstWsTestFiles).containsOnly(
            createRellTestFile(
                expectedTestFile,
                "directory.first_test_file",
                createRellTestCase("test_1", Position(0, 14), Position(0, 44), expectedTestFile)
            )
        )
    }

    @Test
    fun `Returns all test functions in file`(@TempDir dir: File) {
        val testFilePath = "test_file.rell"
        val testDataBuilder = testData(dir) {
            addFile(
                testFilePath,
                """
                @test module; 
                function test_1() { return 1; }
                function test_2() { return 1; }
                function not_test() { return 1; }
                function test() { return 1; }
                function test_3() { return 1; }
                @test
                function annotated_test() { return 1; }
                @test() function another_annotated_test() { return 1; }
                """.trimIndent()
            )
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val testFile = testDataBuilder.sourceFile(testFilePath)
        testRunner.getTestCases(testFile.toURI()).let { testCases ->
            assertThat(testCases).containsOnly(
                createRellTestCase("test_1", Position(1, 0), Position(1, 30), testFile),
                createRellTestCase("test_2", Position(2, 0), Position(2, 30), testFile),
                createRellTestCase("test", Position(4, 0), Position(4, 28), testFile),
                createRellTestCase("test_3", Position(5, 0), Position(5, 30), testFile),
                createRellTestCase("annotated_test", Position(6, 0), Position(7, 38), testFile),
                createRellTestCase("another_annotated_test", Position(8, 0), Position(8, 54), testFile),
            )
        }
    }

    @Disabled("Uncomment when bumping to rell version with test disable annotation")
    @Test
    fun `Functions with @disabled annotation are excluded from test cases`(@TempDir dir: File) {
        val testFilePath = "test_file.rell"
        val testDataBuilder = testData(dir) {
            addFile(
                testFilePath,
                """
                @test module;
                function test_included() { return 1; }
                @disabled
                function test_disabled() { return 1; }
                """.trimIndent()
            )
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val testFile = testDataBuilder.sourceFile(testFilePath)
        testRunner.getTestCases(testFile.toURI()).let { testCases ->
            assertThat(testCases).containsOnly(
                createRellTestCase("test_included", Position(1, 0), Position(1, 37), testFile)
            )
        }
    }

    @Disabled("Uncomment when bumping to rell version with test disable annotation")
    @Test
    fun `Functions with @test and @disabled annotations are excluded from test cases`(@TempDir dir: File) {
        val testFilePath = "test_file.rell"
        val testDataBuilder = testData(dir) {
            addFile(
                testFilePath,
                """
                @test module;
                @test
                function valid_test() { return 1; }
                @test
                @disabled
                function annotated_disabled() { return 1; }
                """.trimIndent()
            )
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val testFile = testDataBuilder.sourceFile(testFilePath)
        testRunner.getTestCases(testFile.toURI()).let { testCases ->
            assertThat(testCases).containsOnly(
                createRellTestCase("valid_test", Position(1, 0), Position(2, 34), testFile)
            )
        }
    }

    @Test
    fun `Returns empty test cases for non-test module file`(@TempDir dir: File) {
        val testFilePath = "regular_module.rell"
        val testDataBuilder = testData(dir) {
            addFile(
                testFilePath,
                "module; function test_1() { return 1; } function test_2() { return 1; }"
            )
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val testFile = testDataBuilder.sourceFile(testFilePath)
        assertThat(testRunner.getTestCases(testFile.toURI())).isEmpty()
    }

    @Test
    fun `Returns empty test cases for unknown file URI`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile("module.rell", "module;")
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val unknownUri = File(testDataBuilder.sourceFolder, "nonexistent.rell").toURI()
        assertThat(testRunner.getTestCases(unknownUri)).isEmpty()
    }

    @Test
    fun `Returns RellTestFile for test module file`(@TempDir dir: File) {
        val testFilePath = "test_module.rell"
        val testDataBuilder = testData(dir) {
            addFile(testFilePath, "@test module; function test_1() { return 1; }")
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val testFile = testDataBuilder.sourceFile(testFilePath)

        assertThat(testRunner.getTestFile(testFile.toURI())).isEqualTo(
            createRellTestFile(
                testFile,
                "test_module",
                createRellTestCase("test_1", Position(0, 14), Position(0, 44), testFile)
            )
        )
    }

    @Test
    fun `Returns null for non-test module file`(@TempDir dir: File) {
        val testFilePath = "regular_module.rell"
        val testDataBuilder = testData(dir) {
            addFile(testFilePath, "module; function test_1() { return 1; }")
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val testFile = testDataBuilder.sourceFile(testFilePath)

        assertThat(testRunner.getTestFile(testFile.toURI())).isNull()
    }

    @Test
    fun `Returns null for unknown file URI`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile("module.rell", "module;")
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        val unknownUri = File(testDataBuilder.sourceFolder, "nonexistent.rell").toURI()

        assertThat(testRunner.getTestFile(unknownUri)).isNull()
    }

    @Test
    fun `Returns empty list when workspace has no test files`(@TempDir dir: File) {
        val testDataBuilder = testData(dir) {
            addFile("module_a.rell", "module; function test_1() { return 1; }")
            addFile("module_b.rell", "module; function test_2() { return 1; }")
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(indexingManager, symbolService)
        assertThat(testRunner.getTestFiles(testDataBuilder.sourceFolderUri)).isEmpty()
    }


    @Test
    fun `Disabled annotation isn't released yet`(@TempDir dir: File) {
        val testFilePath = "test_file.rell"
        val testDataBuilder = testData(dir) {
            addFile(
                testFilePath,
                """
                @test module;
                @disabled
                function test_1() { return 1; }
                """.trimIndent()
            )
        }
        initializeWorkspace(dir)
        val testFile = testDataBuilder.sourceFile(testFilePath)
        val issues = diagnostics[testFile.toURI()]!!
        /*
         * TODO: When the @disabled annotation is released in Rell, this test case will fail.
         * Remove this test case and add new one to verify that
         * functions annotated with both @disabled and @test(or named with test_ prefix)
         * are not included in the test cases.
         */
        assertThat(issues).extracting { it.message }.containsOnly("Annotation '@disabled' is invalid")
    }

    private fun createRellTestFile(file: File, name: String, rellTestCase: RellTestCase): RellTestFile {
        return RellTestFile(file.toURI(), name, true, listOf(rellTestCase))
    }

    private fun createRellTestCase(
        name: String,
        startPosition: Position,
        endPosition: Position,
        file: File
    ): RellTestCase {
        return RellTestCase(
            name,
            Range(startPosition, endPosition),
            file.toURI().toString()
        )
    }
}
