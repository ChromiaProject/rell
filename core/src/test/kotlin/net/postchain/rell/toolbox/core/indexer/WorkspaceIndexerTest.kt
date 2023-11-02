package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectory

class WorkspaceIndexerTest {
    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        val childDir = File(tempDir.toFile(), "directory").toPath().createDirectory()
        File(childDir.toFile(), "rell_file.rell").apply {
            writeText("module;")
        }
        File(tempDir.toFile(), "rell_file.rell").apply {
            writeText("module;")
        }
        File(tempDir.toFile(), "not_a_rell_file.json").apply {
            writeText("{module}")
        }
    }

    @Test
    fun `addRellFilesUri returns uris to all Rell files from folders within workspace`() {
        val fileUris = WorkspaceIndexer(tempDir.toUri()).addRellFilesUri()
        assertThat(fileUris.size).isEqualTo(2)
    }

    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace`() {
        val workspaceIndexer = WorkspaceIndexer(tempDir.toUri())
        workspaceIndexer.initialFileIndexBuild()

        assertThat(workspaceIndexer.fileUriResourceMap.size).isEqualTo(2)
        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
        }
    }
}
