/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class WorkspaceIndexerAffectedFilesTest {
    @TempDir
    lateinit var tempDir: File
    private val syntaxErrorFile = "syntax_error.rell"
    private val importFileDepth1 = "imported_module.rell"
    private val submodule = "submodule/module.rell"
    private val implicitlyImporting = "submodule/implicitly_importing.rell"

    private val rellLinter = mockk<AbstractRellLinter>()
    private val formattingStyleLinter = mockk<AbstractFormattingStyleLinter>()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    private lateinit var testDataBuilder: TestDataBuilder

    @BeforeEach
    fun setup() {
        every { rellLinter.enhanceWithLintIssues(any(), any()) } returns Unit
        every { formattingStyleLinter.enhanceWithFormatterIssues(any(), any(), any(), any()) } returns Unit

        testDataBuilder = testData(tempDir) {
            addFile(
                syntaxErrorFile,
                """
                module;
                import ^.imported_module.*;
                """.trimIndent()
            )
            addFile(importFileDepth1, "module;")
            addFile(
                submodule,
                """
                module;
                import ^.imported_module.*;
                """.trimIndent()
            )
            addFile(implicitlyImporting, "fun no_imports_inside_file() {}")
        }
    }

    @Test
    fun `find Affected Files depth one`() {
        val workspaceIndexer =
            WorkspaceIndexer(tempDir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        workspaceIndexer.initialFileIndexBuild()
        val importFileDepth1Uri = testDataBuilder.sourceFile(importFileDepth1).toURI()
        val syntaxErrorFileUri = testDataBuilder.sourceFile(syntaxErrorFile).toURI()
        val files = workspaceIndexer.findAffectedFiles(importFileDepth1Uri)
        assertThat(files).all {
            hasSize(4)
            containsAtLeast(importFileDepth1Uri, syntaxErrorFileUri)
        }
    }

    @Test
    fun `find Affected Files no imports`() {
        val workspaceIndexer =
            WorkspaceIndexer(tempDir.toURI(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
        workspaceIndexer.initialFileIndexBuild()
        val syntaxErrorFileUri = testDataBuilder.sourceFile(syntaxErrorFile).toURI()
        val files = workspaceIndexer.findAffectedFiles(syntaxErrorFileUri)
        assertThat(files).hasSize(1)
        assertThat(files.first()).isEqualTo(syntaxErrorFileUri)
    }
}
