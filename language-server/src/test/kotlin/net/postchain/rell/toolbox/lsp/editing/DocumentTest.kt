package net.postchain.rell.toolbox.lsp.editing

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

internal class DocumentTest {

    private val document = Document(
        URI(""),
        0,
        """
            Holabaloo
            this iS my second line.
        
            foUrth linE
        """.trimIndent()
    )

    @Test
    fun `Get position of capital letters in a document`() {
        assertThrows<IndexOutOfBoundsException> { document.getPosition(-1) }
        assertThrows<IndexOutOfBoundsException> { document.getPosition(47) }
        assertThat(document.getPosition(0)).isEqualTo(Position(0, 0))
        assertThat(document.getPosition(16)).isEqualTo(Position(1, 6))
        assertThat(document.getPosition(37)).isEqualTo(Position(3, 2))
        assertThat(document.getPosition(document.content.length)).isEqualTo(Position(3, 11))
    }

    @Test
    fun `Get offset of a Position`() {
        assertThrows<IndexOutOfBoundsException> { document.getOffSet(Position(0, -1)) }
        assertThrows<IndexOutOfBoundsException> { document.getOffSet(Position(3, 12)) }
        assertThat(document.getOffSet(Position(0, 0))).isEqualTo(0)
        assertThat(document.getOffSet(Position(1, 6))).isEqualTo(16)
        assertThat(document.getOffSet(Position(3, 2))).isEqualTo(37)
        assertThat(document.getOffSet(Position(3, 11))).isEqualTo(document.content.length)
    }

    @Test
    fun `Changes are applied`() {
        val updatedDocument = document.applyTextDocumentChanges(
            listOf(
                TextDocumentContentChangeEvent(Range(Position(0, 0), Position(0, 9)), "New String"),
                TextDocumentContentChangeEvent(Range(Position(2, 0), Position(2, 0)), "Another"),
            )
        )
        assertThat(updatedDocument.fileUri).isEqualTo(document.fileUri)
        assertThat(updatedDocument.version).isEqualTo(1)
        val lines = updatedDocument.content.split('\n')
        assertThat(lines[0]).isEqualTo("New String")
        assertThat(lines[2]).isEqualTo("Another")
    }

    @Test
    fun `Content is replaced`() {
        val updatedDocument = document.applyTextDocumentChanges(
            listOf(TextDocumentContentChangeEvent(null, "New content"))
        )
        assertThat(updatedDocument.fileUri).isEqualTo(document.fileUri)
        assertThat(updatedDocument.version).isEqualTo(1)
        assertThat(updatedDocument.content).isEqualTo("New content")
    }

    @Test
    fun `PreviousNonLetterChar correctly returns previous non letter`() {
        val document = Document(URI(""), 0, "Hello, World!")
        assertThat(document.previousNonLetterChar(6)).isEqualTo(',')
    }

    @Test
    fun `PreviousNonLetterChar returns null when no previous non letter exists`() {
        val document = Document(URI(""), 0, "HelloWorld")
        assertThat(document.previousNonLetterChar(10)).isEqualTo(null)
    }

    @Test
    fun `PreviousNonLetterChar handles offset at start`() {
        val document = Document(URI(""), 0, "Hello, World!")
        assertThat(document.previousNonLetterChar(0)).isEqualTo(null)
    }

    @Test
    fun `PreviousNonLetterChar handles offset at end`() {
        val document = Document(URI(""), 0, "Hello, World!")
        assertThat(document.previousNonLetterChar(13)).isEqualTo('!')
    }

    @Test
    fun `PreviousNonLetterChar handles empty content`() {
        val document = Document(URI(""), 0, "")
        assertThat(document.previousNonLetterChar(0)).isEqualTo(null)
    }
}
