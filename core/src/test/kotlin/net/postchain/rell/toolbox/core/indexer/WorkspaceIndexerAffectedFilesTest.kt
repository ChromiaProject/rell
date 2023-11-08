package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class WorkspaceIndexerAffectedFilesTest {
    @TempDir
    lateinit var tempDir: File
    private lateinit var mainFile: File
    private lateinit var importFileDepth1: File


    @BeforeEach
    fun setup() {
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
    }

    @Test
    fun `find Affected Files depth one`() {
        val workspaceIndexer = WorkspaceIndexer(tempDir.toURI())
        workspaceIndexer.initialFileIndexBuild()
        val files = workspaceIndexer.findAffectedFiles(importFileDepth1.toURI())
        assertThat(files.size).isEqualTo(2)
        assertThat(files.containsAll(listOf(importFileDepth1.toURI(), mainFile.toURI()))).isTrue()
    }

    @Test
    fun `find Affected Files no imports`() {
        val workspaceIndexer = WorkspaceIndexer(tempDir.toURI())
        workspaceIndexer.initialFileIndexBuild()
        val files = workspaceIndexer.findAffectedFiles(mainFile.toURI())
        assertThat(files.size).isEqualTo(1)
        assertThat(files.first()).isEqualTo(mainFile.toURI())
    }
}
