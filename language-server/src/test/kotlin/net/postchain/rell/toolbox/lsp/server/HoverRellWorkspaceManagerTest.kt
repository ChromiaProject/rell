package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.postchain.rell.toolbox.lsp.server.utils.WorkspaceManagerTestBase
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Test
import java.awt.SystemColor.text
import java.io.File

internal class HoverRellWorkspaceManagerTest : WorkspaceManagerTestBase() {

    @Test
    fun `Hover information is never null`() {
        val testFile = createFile(
            sourceDir, "my_rell_module.rell",
            """
                module;
                val my_string = "HOVER_THIS";
            """.trimIndent()
        )

        initializeWorkspace()
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())
        val hoverDocs = hoverOn("HOVER_THIS", testFile)

        assertThat(hoverDocs.kind).isEqualTo("markdown")
        assertThat(hoverDocs.value).isEmpty()
    }

    @Test
    fun `Hover on system lib gives information`() {
        val testFile = createFile(
            sourceDir, "my_rell_module.rell",
            """
                module;
                function foo() {
                    val my_integer = integer.from_text("123");
                }
            """.trimIndent()
        )

        initializeWorkspace()
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())
        val hoverDocs = hoverOn("from_text", testFile)

        assertThat(hoverDocs.kind).isEqualTo("markdown")
        assertThat(hoverDocs.value).contains("function from_text")
    }

    @Test
    fun `Hover on user function gives no information (yet)`() {
        val testFile = createFile(
            sourceDir, "my_rell_module.rell",
            """
                module;
                
                function test() = my_function();
                
                /**
                 * This docs is currently not accessible
                 */
                function my_function() = 32;
            """.trimIndent()
        )

        initializeWorkspace()
        workspaceManager.didOpen(testFile.toURI(), 1, testFile.readText())
        val hoverDocs = hoverOn("my_function", testFile)

        assertThat(hoverDocs.kind).isEqualTo("markdown")
        assertThat(hoverDocs.value).isEmpty()
    }

    @Test
    fun `Hover on non-rell file should not fail`() {
        val testFile = createFile(
            sourceDir, ".gitignore",
            """
                build
                .DS_Store
            """.trimIndent()
        )
        initializeWorkspace()
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
                Position(0,0)
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
