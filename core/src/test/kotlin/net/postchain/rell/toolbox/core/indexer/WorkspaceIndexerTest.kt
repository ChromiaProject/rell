package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectory

class WorkspaceIndexerTest {

    @Test
    fun `WorkspaceIndexer scan returns uris to all Rell files in workspace`(@TempDir dir: Path) {
        File(dir.toFile(), "rell-file.rell").apply {
            writeText("module")
        }
        val fileUris = WorkspaceIndexer().scan(dir.toUri())
        assertThat(fileUris.size).isEqualTo(1)
    }

    @Test
    fun `WorkspaceIndexer scan returns uris to all Rell files from folders within workspace`(@TempDir dir: Path) {
        val childDir = File(dir.toFile(), "directory").toPath().createDirectory()
        File(childDir.toFile(), "rell-file.rell").apply {
            writeText("module")
        }
        File(dir.toFile(), "rell-file.rell").apply {
            writeText("module")
        }
        File(dir.toFile(), "not-a-rell-file.json").apply {
            writeText("{module}")
        }
        val fileUris = WorkspaceIndexer().scan(dir.toUri())
        assertThat(fileUris.size).isEqualTo(2)
    }

}