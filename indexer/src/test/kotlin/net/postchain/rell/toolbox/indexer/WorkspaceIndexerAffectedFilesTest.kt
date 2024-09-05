package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.LinterOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createDirectory
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter

class WorkspaceIndexerAffectedFilesTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var mainFile: File
    private lateinit var importFileDepth1: File
    private lateinit var implicitlyImporting: File

    private val rellLinter =  mockk<AbstractRellLinter>()
    private val formattingStyleLinter = mockk<AbstractFormattingStyleLinter>()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    @BeforeEach
    fun setup() {
        every { rellLinter.enhanceWithLintIssues(any(), any()) } returns Unit
        every { formattingStyleLinter.enhanceWithFormatterIssues(any(), any(), any(), any()) } returns Unit

        mainFile = File(tempDir, "syntax_error.rell").apply {
            writeText(
                """
                module;
                import ^.imported_module.*;
            """.trimIndent()
            )
        }

        importFileDepth1 = File(tempDir, "imported_module.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }

        val submoduleFolder = File(tempDir, "submodule")
        submoduleFolder.toPath().createDirectory()
        File(submoduleFolder, "module.rell").apply {
            writeText(
                """
                module;
                import ^.imported_module.*;
            """.trimIndent()
            )
        }

        implicitlyImporting = File(submoduleFolder, "implicitly_importing.rell").apply {
            writeText(
                """
                fun no_imports_inside_file() {}
            """.trimIndent()
            )
        }
    }

    @Test
    fun `find Affected Files depth one`() {
        val workspaceIndexer =
            WorkspaceIndexer(tempDir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        workspaceIndexer.initialFileIndexBuild()
        val files = workspaceIndexer.findAffectedFiles(importFileDepth1.toURI())
        assertThat(files.size).isEqualTo(4)
        assertThat(files.containsAll(listOf(importFileDepth1.toURI(), mainFile.toURI()))).isTrue()
    }

    @Test
    fun `find Affected Files no imports`() {
        val workspaceIndexer =
            WorkspaceIndexer(tempDir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        workspaceIndexer.initialFileIndexBuild()
        val files = workspaceIndexer.findAffectedFiles(mainFile.toURI())
        assertThat(files.size).isEqualTo(1)
        assertThat(files.first()).isEqualTo(mainFile.toURI())
    }
}
