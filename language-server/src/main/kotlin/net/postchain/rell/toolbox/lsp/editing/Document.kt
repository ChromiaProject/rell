package net.postchain.rell.toolbox.lsp.editing


import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextEdit
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
        val l = contents!!.length
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
     * Returns with the text for a certain line without the trailing end line marker.
     */
    fun getLineContent(lineNumber: Int): String {
        if (lineNumber < 0) {
            throw IndexOutOfBoundsException(lineNumber.toString())
        }
        val NL = '\n'
        val LF = '\r'
        val l = contents!!.length
        val lineContent = StringBuilder()
        var line = 0
        for (i in 0 until l) {
            if (line > lineNumber) {
                return lineContent.toString()
            }
            val ch = contents[i]
            if (line == lineNumber && ch != NL && ch != LF) {
                lineContent.append(ch)
            }
            if (ch == NL) {
                line++
            }
        }
        if (line < lineNumber) {
            throw IndexOutOfBoundsException(lineNumber.toString())
        }
        return lineContent.toString()
    }

    val lineCount: Int
        /**
         * Get the number of lines in the document. Empty document has line count: `1`.
         */
        get() = getPosition(contents.length).line + 1

    fun getSubstring(range: Range): String {
        val start = getOffSet(range.start)
        val end = getOffSet(range.end)
        return contents.substring(start, end)
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
        var newVersion: Int? = null
        if (currentDocument.version != null) {
            newVersion = Integer.valueOf(currentDocument.version!! + 1)
        }
        for (change in changes) {
            val newContent: String
            newContent = if (change.range == null) {
                change.text
            } else {
                val start = currentDocument.getOffSet(change.range.start)
                val end = currentDocument.getOffSet(change.range.end)
                (currentDocument.contents!!.substring(0, start) + change.text
                        + currentDocument.contents!!.substring(end))
            }
            currentDocument = Document(fileUri, newVersion ?: 0, newContent)
        }
        return currentDocument
    }

    /**
     * Only use for testing.
     *
     * All positions in the [TextEdit]s refer to the same original document (this).
     */
    //TODO: If only used for testing, should we have it as a test util function instead?
    fun applyChanges(changes: Iterable<TextEdit>): Document {
        var newContent = contents
        for (change in changes) {
            newContent = if (change.range == null) {
                change.newText
            } else {
                val start = getOffSet(change.range.start)
                val end = getOffSet(change.range.end)
                newContent.substring(0, start) + change.newText + newContent.substring(end)
            }
        }
        val newVersion = Integer.valueOf(version + 1)
        return Document(fileUri, newVersion, newContent)
    }
}
