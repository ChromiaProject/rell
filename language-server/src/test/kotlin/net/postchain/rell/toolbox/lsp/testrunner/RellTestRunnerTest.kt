package net.postchain.rell.toolbox.lsp.testrunner

import assertk.assertThat
import assertk.assertions.containsOnly
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectory

class RellTestRunnerTest : WorkspaceManagerTestBase() {

    @Test
    fun `Returns all test files in workspace`(@TempDir dir: File) {
        val childDir = File(dir, "directory").toPath().createDirectory()
        val firstTestFile = File(childDir.toFile(), "first_test_file.rell").apply {
            writeText("@test module; function test_1() { return 1; }")
        }
        val secondTestFile = File(dir, "second_test_file.rell").apply {
            writeText("@test module; function test_2() { return 1; }")
        }
        File(dir, "not_a_test_file.rell").apply {
            writeText("module; function test_like() { return 1; }")
        }

        initializeWorkspace(dir)
        val testRunner = RellTestRunner(workspaceManager, symbolService)

        testRunner.getTestFiles(dir.toURI()).let { testFiles ->
            assertThat(testFiles).containsOnly(
                createRellTestFile(
                    firstTestFile,
                    "directory.first_test_file",
                    createRellTestCase("test_1", 0, 14, 0, 44, firstTestFile)
                ),
                createRellTestFile(
                    secondTestFile,
                    "second_test_file",
                    createRellTestCase("test_2", 0, 14, 0, 44, secondTestFile)
                ),
            )
        }
    }

    @Test
    fun `Returns all test functions in file`(@TempDir dir: File) {
        val childDir = File(dir, "directory").toPath().createDirectory()
        val testFile = File(childDir.toFile(), "test_file.rell").apply {
            writeText(
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
        val testRunner = RellTestRunner(workspaceManager, symbolService)

        testRunner.getTestCases(testFile.toURI()).let { testCases ->
            assertThat(testCases).containsOnly(
                createRellTestCase("test_1", 1, 0, 1, 30, testFile),
                createRellTestCase("test_2", 2, 0, 2, 30, testFile),
                createRellTestCase("test", 4, 0, 4, 28, testFile),
                createRellTestCase("test_3", 5, 0, 5, 30, testFile),
            )
        }
    }

    private fun createRellTestFile(file: File, name: String, rellTestCase: RellTestCase): RellTestFile {
        return RellTestFile(file.toURI(), name, true, listOf(rellTestCase))
    }

    private fun createRellTestCase(
        name: String,
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
        file: File
    ): RellTestCase {
        return RellTestCase(
            name,
            Range(Position(startLine, startColumn), Position(endLine, endColumn)),
            file.toURI().toString()
        )
    }
}