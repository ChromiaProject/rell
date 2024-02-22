package net.postchain.rell.toolbox.lsp.editing


import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.net.URI



class Document(val fileUri: URI, val version: Int, val content: String) {

    fun getOffSet(position: Position): Int {
        var line = 0
        var column = 0
        for (i in content.indices) {
            val ch = content[i]
            if (position.line == line && position.character == column) {
                return i
            }
            if (ch == NL) {
                line++
                column = 0
            } else {
                column++
            }
        }
        if (position.line == line && position.character == column) {
            return content.length
        }
        throw IndexOutOfBoundsException("Position $position out of bounds")
    }

    fun getPosition(offset: Int): Position {
        val contentLength = content.length
        if (offset < 0 || offset > contentLength) {
            throw IndexOutOfBoundsException("Offset $offset is out of bounds for range [0, $contentLength]")
        }
        var line = 0
        var column = 0

        for (i in content.indices) {
            val ch = content[i]
            if (i == offset) break
            if (ch == NL) {
                line++
                column = 0
            } else {
                column++
            }
        }
        return Position(line, column)
    }


    /**
     * As opposed to [TextEdit][] the positions in the edits of a [DidChangeTextDocumentParams] refer to the
     * state after applying the preceding edits. See
     * https://microsoft.github.io/language-server-protocol/specification#textedit-1 and
     * https://github.com/microsoft/vscode/issues/23173#issuecomment-289378160 for details.
     *
     * @return a new document with an incremented version and the text document changes applied.
     */
    fun applyTextDocumentChanges(changes: Iterable<TextDocumentContentChangeEvent>): Document {
        var currentDocument = this
        val newVersion = currentDocument.version + 1
        for (change in changes) {
            val newContent: String = if (change.range == null) {
                change.text
            } else {
                val start = currentDocument.getOffSet(change.range.start)
                val end = currentDocument.getOffSet(change.range.end)
                (currentDocument.content.substring(0, start) + change.text
                        + currentDocument.content.substring(end))
            }
            currentDocument = Document(fileUri, newVersion, newContent)
        }
        return currentDocument
    }

    companion object {
        const val NL = '\n'
    }
}
