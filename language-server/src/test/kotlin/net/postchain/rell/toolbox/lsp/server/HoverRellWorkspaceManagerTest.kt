package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Test
import java.io.File

internal class HoverRellWorkspaceManagerTest : WorkspaceManagerTestBase() {

    private val testFilePath = "my_rell_module.rell"

    @Test
    fun `Hover information is never null`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                val my_string = "HOVER_THIS";
                """.trimIndent()
            )
        }

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())
        val hoverDocs = hoverOn("HOVER_THIS", testFile)

        assertThat(hoverDocs.kind).isEqualTo("markdown")
        assertThat(hoverDocs.value).isEmpty()
    }

    @Test
    fun `Hover on system lib gives information`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                function foo() {
                    val my_integer = integer.from_text("123");
                }
                """.trimIndent()
            )
        }

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())
        val hoverDocs = hoverOn("from_text", testFile)

        assertThat(hoverDocs.kind).isEqualTo("markdown")
        assertThat(hoverDocs.value).contains("function from_text")
    }

    @Test
    fun `Hover on user function gives signature and Rell Docs`() {
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                module;
                
                function test() = my_function();
                
                /**
                 * This doc comment is accessible
                 * @see other_function
                 * @since 1.0.0
                 * @param first This is the first parameter
                 * @param second This is the second parameter
                 * @returns This is the return value
                 */
                function my_function(first: integer, second: integer) = 32;
                """.trimIndent()
            )
        }

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())
        val hoverDocs = hoverOn("my_function", testFile)

        assertThat(hoverDocs.kind).isEqualTo("markdown")
        assertThat(hoverDocs.value).contains(
            """
            function my_function(
            	first: integer,
            	second: integer
            ): integer
            """.trimIndent()
        )
        assertThat(hoverDocs.value).contains("This doc comment is accessible")
        assertThat(hoverDocs.value).contains("*since:* 1.0.0")
        assertThat(hoverDocs.value).contains("*See also:* other_function")
        assertThat(hoverDocs.value).contains("*@param* `first` - This is the first parameter")
        assertThat(hoverDocs.value).contains("*@param* `second` - This is the second parameter")
        assertThat(hoverDocs.value).contains("*@return* - This is the return value")
    }

    @Test
    fun `Hover on non-rell file should not fail`() {
        val testFilePath = ".gitignore"
        val testDataBuilder = testData(sourceDir) {
            addFile(
                testFilePath,
                """
                build
                .DS_Store
                """.trimIndent()
            )
        }

        initializeWorkspace()
        val testFile = testDataBuilder.sourceFile(testFilePath)
        val hoverDocs = hoverOn("build", testFile)
        assertThat(hoverDocs.kind).isEqualTo("plaintext")
        assertThat(hoverDocs.value).isEmpty()
    }

    @Test
    fun `Hover on a non-file should not fail`() {
        initializeWorkspace()
        val hoverDocs = workspaceManager.getHoverDocumentation(
            HoverParams(
                TextDocumentIdentifier("http://localhost"),
                Position(0, 0)
            )
        )
        assertThat(hoverDocs.kind).isEqualTo("plaintext")
        assertThat(hoverDocs.value).isEmpty()
    }

    private fun hoverOn(text: String, file: File) =
        workspaceManager.getHoverDocumentation(
            HoverParams(
                TextDocumentIdentifier(file.toURI().toString()),
                findPosition(text, file)
            )
        )

    private fun findPosition(text: String, file: File): Position {
        file.readLines().forEachIndexed { lineNumber, line ->
            val character = line.indexOf(text)
            if (character != -1) {
                return Position(lineNumber, character + 1)
            }
        }
        throw IllegalArgumentException("text $text not found in file $file")
    }
}
