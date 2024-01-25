package net.postchain.rell.toolbox.lsp.editing


import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.net.URI


class Document(val fileUri: URI, val version: Int, val contents: String) {

    fun getOffSet(position: Position): Int {
        val l = contents.length
        val NL = '\n'
        var line = 0
        var column = 0
        for (i in 0 until l) {
            val ch = contents[i]
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
            return l
        }
        throw IndexOutOfBoundsException(position.toString())
    }

    fun getPosition(offset: Int): Position {
        val l = contents.length
        if (offset < 0 || offset > l) {
            throw IndexOutOfBoundsException(offset.toString())
        }
        val NL = '\n'
        var line = 0
        var column = 0
        for (i in 0 until l) {
            val ch = contents[i]
            if (i == offset) {
                return Position(line, column)
            }
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
        val newVersion: Int? = Integer.valueOf(currentDocument.version + 1)
        for (change in changes) {
            val newContent: String = if (change.range == null) {
                change.text
            } else {
                val start = currentDocument.getOffSet(change.range.start)
                val end = currentDocument.getOffSet(change.range.end)
                (currentDocument.contents.substring(0, start) + change.text
                        + currentDocument.contents.substring(end))
            }
            currentDocument = Document(fileUri, newVersion ?: 0, newContent)
        }
        return currentDocument
    }

}
