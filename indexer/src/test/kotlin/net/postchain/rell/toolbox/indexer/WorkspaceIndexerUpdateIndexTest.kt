package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.linter.LinterOptions
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
import net.postchain.rell.toolbox.linter.AbstractFormattingStyleLinter
import net.postchain.rell.toolbox.linter.AbstractRellLinter

class WorkspaceIndexerUpdateIndexTest {
    @TempDir
    lateinit var tempDir: Path

    private val rellLinter = mockk<AbstractRellLinter>()
    private val formattingStyleLinter = mockk<AbstractFormattingStyleLinter>()
    private val formatterOptions = FormatterOptions()
    private val linterOptions = LinterOptions()

    @BeforeEach
    fun setup() {
        every { rellLinter.enhanceWithLintIssues(any(), any()) } returns Unit
        every { formattingStyleLinter.enhanceWithFormatterIssues(any(), any(), any(), any()) } returns Unit

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
    fun `updateFileUriResourceMap updates resource of existing uri`() {
        val workspaceIndexer = WorkspaceIndexer(tempDir.toUri(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
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
        val workspaceIndexer = WorkspaceIndexer(tempDir.toUri(), rellLinter, linterOptions, formattingStyleLinter, formatterOptions)
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
}
