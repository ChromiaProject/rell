package net.postchain.rell.toolbox.lsp.testrunner

import assertk.assertThat
import assertk.assertions.containsOnly
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
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
            )
        }
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
