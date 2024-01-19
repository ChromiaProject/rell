package net.postchain.rell.toolbox.lsp.editorconfig

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellFormatterOptionsResolverTest {

    @Test
    fun `Options gets correctly resolved from current folder`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir)
        val resolver = createRellFormatterOptionsResolver(tempDir)

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getFormattingOptionsFor(workspaceUri)

        assertOptions(options)
    }

    @Test
    fun `Options gets correctly resolved from parent folder`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir)
        val resolver = createRellFormatterOptionsResolver(tempDir.resolve("src"))

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getFormattingOptionsFor(workspaceUri)

        assertOptions(options)
    }

    @Test
    fun `Options gets correctly resolved from grand parent folder`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir)
        val resolver = createRellFormatterOptionsResolver(tempDir.resolve("rell/src"))

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getFormattingOptionsFor(workspaceUri)

        assertOptions(options)
    }

    private fun assertOptions(options: FormatterOptions?) {
        assertThat(options).isNotNull()
        assertThat(options!!.maxLineWidth).isEqualTo(65)
        assertThat(options.insertSpaces).isTrue()
        assertThat(options.tabSize).isEqualTo(5)
    }

    private fun createRellFormatterOptionsResolver(tempDir: File): RellFormatterOptionsResolver {
        val workspaceManager = mockk<RellWorkspaceManager>()
        every { workspaceManager.getIndexerFor(any()) } returns WorkspaceIndexer(tempDir.toURI())
        val resolver = RellFormatterOptionsResolver(workspaceManager)
        return resolver
    }


    private fun createRellFormatFile(tempDir: File) {
        File(tempDir, ".rellformat").apply {
            writeText(
                """
                    [*.rell]
                    max_line_width = 65
                    insert_spaces = true
                    tab_size = 5
                """.trimIndent()
            )
        }
    }
}