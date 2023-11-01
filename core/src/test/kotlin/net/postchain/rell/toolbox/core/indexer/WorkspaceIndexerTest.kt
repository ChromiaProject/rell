package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.test.assertNotNull

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

    @Test
    fun `updateFileUriResourceMap updates resource of existing uri`() {
        val workspaceIndexer = WorkspaceIndexer(tempDir.toUri())
        workspaceIndexer.initialFileIndexBuild()

        val prevUriResourceMap = workspaceIndexer.fileUriResourceMap.toMap()
        val appendText = "val a = \"a\";"
        val pathToFile = Path("$tempDir/rell_file.rell")
        Files.write(pathToFile, appendText.toByteArray(), StandardOpenOption.APPEND)
        val pathUri = pathToFile.toUri()

        workspaceIndexer.updateFileUriResourceMap(pathToFile.toUri())
        val updateFileUriResourceMap = workspaceIndexer.fileUriResourceMap

        assertThat(updateFileUriResourceMap.size).isEqualTo(prevUriResourceMap.size)
        prevUriResourceMap.keys.forEach { assertNotNull(updateFileUriResourceMap[it]) }
        assertThat(updateFileUriResourceMap[pathUri]!!.parseTree.children.size).isNotEqualTo(
            prevUriResourceMap[pathUri]!!.parseTree.children.size
        )
    }

    @Test
    fun `updateFileUriResourceMap updates uri of existing resource`() {
        val workspaceIndexer = WorkspaceIndexer(tempDir.toUri())
        workspaceIndexer.initialFileIndexBuild()
        val prevUriResourceMap = workspaceIndexer.fileUriResourceMap.toMap()

        val oldUri = Path("$tempDir/rell_file.rell").toUri()
        val newUri = Path("$tempDir/renamed_rell_file.rell").toUri()
        workspaceIndexer.updateFileUriResourceMap(oldUri, newUri)
        val updateFileUriResourceMap = workspaceIndexer.fileUriResourceMap

        assertThat(updateFileUriResourceMap.size).isEqualTo(prevUriResourceMap.size)
        assertThat(updateFileUriResourceMap[oldUri]).isNull()
        assertThat(updateFileUriResourceMap[newUri]).isEqualTo(prevUriResourceMap[oldUri])
    }

    @Test
    fun `find Affected Files depth one`(@TempDir dir: File) {
        val file1 = File(dir, "main.rell").apply {
            writeText(
                """
                module;
                import ^.imported_module.*;
            """.trimIndent()
            )
        }

        val file2 = File(dir, "imported_module.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }

        val workspaceIndexer = WorkspaceIndexer(dir.toURI())
        workspaceIndexer.initialFileIndexBuild()
        val files = workspaceIndexer.findAffectedFiles(file2.toURI())
        assertThat(files.size).isEqualTo(2)
        assertThat(files.containsAll(listOf(file1.toURI(), file2.toURI()))).isTrue()
    }

    @Test
    fun `find Affected Files no imports`(@TempDir dir: File) {
        val file1 = File(dir, "main.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }

        val file2 = File(dir, "imported_module.rell").apply {
            writeText(
                """
                module;
            """.trimIndent()
            )
        }

        val workspaceIndexer = WorkspaceIndexer(dir.toURI())
        workspaceIndexer.initialFileIndexBuild()
        val files = workspaceIndexer.findAffectedFiles(file2.toURI())
        assertThat(files.size).isEqualTo(1)
        assertThat(files.first()).isEqualTo(file2.toURI())
    }
}
