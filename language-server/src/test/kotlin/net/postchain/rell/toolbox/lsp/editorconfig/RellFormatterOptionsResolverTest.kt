package net.postchain.rell.toolbox.lsp.editorconfig

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.core.indexer.WorkspaceIndexer
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.FormattingStyleLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.RellLinter
import net.postchain.rell.toolbox.lsp.server.RellWorkspaceManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RellFormatterOptionsResolverTest {

    private val rellLinter = RellLinter()
    private val formattingStyleLinter = FormattingStyleLinter()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    @Test
    fun `Options gets correctly resolved from current folder`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir)
        val resolver = createRellFormatterOptionsResolver(tempDir)

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getWorkspaceFormattingOptions(workspaceUri)

        assertOptions(options)
    }

    @Test
    fun `Options gets correctly resolved from deprecated options file`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir, FormatterOptions.DEPRECATED_RELL_FORMAT_FILE_NAME)
        val resolver = createRellFormatterOptionsResolver(tempDir)

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getWorkspaceFormattingOptions(workspaceUri)

        assertOptions(options)
    }

    @Test
    fun `Options gets correctly resolved when both preferred and deprecated options exist`(@TempDir tempDir: File) {
        val deprecatedOptions = """
                    [*.rell]
                    tab_size = 17
                """.trimIndent()
        createRellFormatFile(tempDir, FormatterOptions.PREFERRED_RELL_FORMAT_FILE_NAME)
        createRellFormatFile(tempDir, FormatterOptions.DEPRECATED_RELL_FORMAT_FILE_NAME, deprecatedOptions)
        val resolver = createRellFormatterOptionsResolver(tempDir)

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getWorkspaceFormattingOptions(workspaceUri)

        assertOptions(options)
    }

    @Test
    fun `Options gets correctly resolved from parent folder`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir)
        val resolver = createRellFormatterOptionsResolver(tempDir.resolve("src"))

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getWorkspaceFormattingOptions(workspaceUri)

        assertOptions(options)
    }

    @Test
    fun `Options gets correctly resolved from grand parent folder`(@TempDir tempDir: File) {
        createRellFormatFile(tempDir)
        val resolver = createRellFormatterOptionsResolver(tempDir.resolve("rell/src"))

        val workspaceUri = tempDir.resolve("dummy_file.rell").toURI()
        val options = resolver.getWorkspaceFormattingOptions(workspaceUri)

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
        every { workspaceManager.getIndexerFor(any()) } returns WorkspaceIndexer(
            tempDir.toURI(),
            rellLinter,
            linterOptions,
            formattingStyleLinter,
            formatterOptions
        )
        val resolver = RellFormatterOptionsResolver()
        return resolver
    }


    private fun createRellFormatFile(
        tempDir: File,
        fileName: String = FormatterOptions.PREFERRED_RELL_FORMAT_FILE_NAME,
        content: String? = null
    ) {
        File(tempDir, fileName).apply {
            writeText(
                content ?: """
                    [*.rell]
                    max_line_width = 65
                    insert_spaces = true
                    tab_size = 5
                """.trimIndent()
            )
        }
    }
}