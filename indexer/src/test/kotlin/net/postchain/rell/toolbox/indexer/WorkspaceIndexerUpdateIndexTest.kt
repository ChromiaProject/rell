package net.postchain.rell.toolbox.indexer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
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
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.assertNotNull

class WorkspaceIndexerUpdateIndexTest {
    @TempDir
    lateinit var tempDir: Path

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
            emptyRellModule("directory/rell_file.rell")
            emptyRellModule("rell_file.rell")
            addFile("not_a_rell_file.json", "{module}")
        }
    }

    @Test
    fun `updateFileUriResourceMap updates resource of existing uri`() {
        val workspaceIndexer =
            WorkspaceIndexer(
                testDataBuilder.sourceFolderUri,
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions
            )
        workspaceIndexer.initialFileIndexBuild()

        val prevUriResourceMap = workspaceIndexer.fileUriResourceMap.toMap()
        val appendText = "val a = \"a\";"

        testDataBuilder.appendToSourceFile("rell_file.rell", appendText)
        val rellFileUri = testDataBuilder.sourceFile("rell_file.rell").toURI()
        workspaceIndexer.updateFileUriResourceMap(rellFileUri)
        val updateFileUriResourceMap = workspaceIndexer.fileUriResourceMap

        assertThat(updateFileUriResourceMap.size).isEqualTo(prevUriResourceMap.size)
        prevUriResourceMap.keys.forEach { assertNotNull(updateFileUriResourceMap[it]) }
        assertThat(updateFileUriResourceMap[rellFileUri]!!.parseTree.children.size).isNotEqualTo(
            prevUriResourceMap[rellFileUri]!!.parseTree.children.size
        )
    }

    @Test
    fun `updateFileUriResourceMap updates uri of existing resource`() {
        val sourceFolder = testDataBuilder.sourceFolder
        val workspaceIndexer =
            WorkspaceIndexer(
                testDataBuilder.sourceFolderUri,
                rellLinter,
                linterOptions,
                formattingStyleLinter,
                formatterOptions
            )
        workspaceIndexer.initialFileIndexBuild()
        val prevUriResourceMap = workspaceIndexer.fileUriResourceMap.toMap()

        val oldUri = testDataBuilder.sourceFile("rell_file.rell").toURI()
        val newUri = URI("$sourceFolder/renamed_rell_file.rell")
        workspaceIndexer.updateFileUriResourceMap(oldUri, newUri)
        val updateFileUriResourceMap = workspaceIndexer.fileUriResourceMap

        assertThat(updateFileUriResourceMap.size).isEqualTo(prevUriResourceMap.size)
        assertThat(updateFileUriResourceMap[oldUri]).isNull()
        assertThat(updateFileUriResourceMap[newUri]).isEqualTo(prevUriResourceMap[oldUri])
    }
}
