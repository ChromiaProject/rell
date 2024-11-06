package net.postchain.rell.toolbox.lsp.server

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.testing.TestDataBuilder
import net.postchain.rell.toolbox.testing.testData
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

class RellDocumentManagerTest {

    @TempDir
    private lateinit var tempDir: File
    private lateinit var testDataBuilder: TestDataBuilder
    private val documentManager = RellDocumentManager()
    private val mainFileContent = "module;"
    private val otherFile = "other.rell"

    @BeforeEach
    fun setup() {
        testDataBuilder = testData(tempDir) {
            addMainFile("module;")
            addFile(otherFile, "module;")
        }
    }

    @Test
    fun `should open and close a document`() {
        val fileUri = testDataBuilder.mainFileUri

        documentManager.openDocument(fileUri, 1, mainFileContent)
        assertThat(documentManager.getOpenDocument(fileUri)).isEqualTo(Document(fileUri, 1, mainFileContent))

        documentManager.closeDocument(fileUri)
        assertThat(documentManager.getOpenDocument(fileUri)).isNull()
    }

    @Test
    fun `should get a document by URI`() {
        val fileUri = testDataBuilder.mainFileUri
        val document = documentManager.getDocument(fileUri)
        assertThat(document).isEqualTo(Document(fileUri, 0, mainFileContent))
    }

    @Test
    fun `should apply text document changes`() {
        val mainFile = testDataBuilder.mainFile
        val mainFileUri = mainFile.toURI()
        documentManager.openDocument(mainFileUri, 1, mainFile.readText())
        val updatedContent = "module; function updated() { }"
        val changes = listOf(
            TextDocumentContentChangeEvent(
                Range(Position(0, 0), Position(0, 7)),
                updatedContent
            )
        )
        val updatedDocument = documentManager.applyTextDocumentChanges(mainFileUri, changes)

        assertThat(updatedDocument).isEqualTo(Document(mainFileUri, 2, updatedContent))
        assertThat(documentManager.getOpenDocument(mainFileUri)).isEqualTo(updatedDocument)
    }

    @Test
    fun `should not apply changes to a closed document`() {
        val mainFile = testDataBuilder.mainFile
        val mainFileUri = mainFile.toURI()
        documentManager.openDocument(mainFileUri, 1, mainFile.readText())
        documentManager.closeDocument(mainFileUri)
        val updatedContent = "module; function updated() { }"
        val changes = listOf(
            TextDocumentContentChangeEvent(
                Range(Position(0, 0), Position(0, 7)),
                updatedContent
            )
        )
        val exception = assertThrows<IllegalStateException> {
            documentManager.applyTextDocumentChanges(mainFileUri, changes)
        }
        assertThat(exception.message).isEqualTo("Document $mainFileUri not opened")
    }

    @Test
    fun `should get all open documents`() {
        val file1 = testDataBuilder.mainFile
        val file2 = testDataBuilder.sourceFile(otherFile)
        val file1Uri = file1.toURI()
        val file2Uri = file2.toURI()
        val file1Content = file1.readText()
        val file2Content = file2.readText()
        documentManager.openDocument(file1Uri, 1, file1Content)
        documentManager.openDocument(file2Uri, 1, file2Content)

        val openDocuments = documentManager.getOpenDocuments()
        assertThat(openDocuments).containsOnly(
            Pair(file1Uri, Document(file1Uri, 1, file1Content)),
            Pair(file2Uri, Document(file2Uri, 1, file2Content))
        )
    }

    @Test
    fun `should return null for a non-existent document`() {
        val manager = RellDocumentManager()
        val fileUri = URI.create("file://not_existing.rell")
        assertThat(manager.getOpenDocument(fileUri)).isNull()
    }

    @Test
    fun `should update document when opened twice`() {
        val fileUri = testDataBuilder.mainFileUri
        val initialContent = mainFileContent
        val updatedContent = "module; function updated() { }"

        documentManager.openDocument(fileUri, 1, initialContent)
        assertThat(documentManager.getOpenDocument(fileUri)).isEqualTo(Document(fileUri, 1, initialContent))

        documentManager.openDocument(fileUri, 2, updatedContent)
        assertThat(documentManager.getOpenDocument(fileUri)).isEqualTo(Document(fileUri, 2, updatedContent))
        assertThat(documentManager.getOpenDocuments()).containsOnly(Pair(fileUri, Document(fileUri, 2, updatedContent)))
    }
}
