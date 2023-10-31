package net.postchain.rell.toolbox.core.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
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
        File(childDir.toFile(), "rell-file.rell").apply {
            writeText("module;")
        }
        File(tempDir.toFile(), "rell-file.rell").apply {
            writeText("module;")
        }
        File(tempDir.toFile(), "not-a-rell-file.json").apply {
            writeText("{module}")
        }
    }

    @Test
    fun `addRellFilesUri returns uris to all Rell files from folders within workspace`() {
        val fileUris = WorkspaceIndexer().addRellFilesUri(tempDir.toUri())
        assertThat(fileUris.size).isEqualTo(2)
    }

    @Test
    fun `initialFileIndexBuild builds index mapper of files in workspace`() {
        val workspaceIndexer = WorkspaceIndexer()
        workspaceIndexer.initialFileIndexBuild(tempDir.toUri())

        assertThat(workspaceIndexer.fileUriResourceMap.size).isEqualTo(2)
        workspaceIndexer.fileUriResourceMap.forEach {
            assertThat(it.value.parseTree.children).isNotEmpty()
        }
    }

    @Test
    fun `updateFileUriResourceMap updates resource of existing uri`() {
        val workspaceIndexer = WorkspaceIndexer()
        workspaceIndexer.initialFileIndexBuild(tempDir.toUri())

        val prevUriResourceMap = workspaceIndexer.fileUriResourceMap.toMap()
        val appendText = "val a = \"a\";"
        val pathToFile = Path("$tempDir/rell-file.rell")
        Files.write(pathToFile, appendText.toByteArray(), StandardOpenOption.APPEND)

        workspaceIndexer.updateFileUriResourceMap(pathToFile.toUri())
        val updateFileUriResourceMap = workspaceIndexer.fileUriResourceMap

        assertThat(updateFileUriResourceMap.size).isEqualTo(prevUriResourceMap.size)
        prevUriResourceMap.keys.forEach { assertNotNull(updateFileUriResourceMap[it]) }
        assertThat(updateFileUriResourceMap.get(pathToFile.toUri())).isNotEqualTo(prevUriResourceMap.get(pathToFile.toUri()))
    }

    @Test
    fun `updateFileUriResourceMap updates uri of existing resource`() {
        val workspaceIndexer = WorkspaceIndexer()
        workspaceIndexer.initialFileIndexBuild(tempDir.toUri())
        val prevUriResourceMap = workspaceIndexer.fileUriResourceMap.toMap()

        val oldUri = Path("$tempDir/rell-file.rell").toUri()
        val newUri = Path("$tempDir/renamed-rell-file.rell").toUri()
        workspaceIndexer.updateFileUriResourceMap(oldUri, newUri)
        val updateFileUriResourceMap = workspaceIndexer.fileUriResourceMap

        assertThat(updateFileUriResourceMap.size).isEqualTo(prevUriResourceMap.size)
        assertThat(updateFileUriResourceMap[oldUri]).isNull()
        assertThat(updateFileUriResourceMap[newUri]).isEqualTo(prevUriResourceMap[oldUri])
    }
}
