package net.postchain.rell.toolbox.lsp.editing

import java.net.URI
import net.postchain.rell.toolbox.core.offsetToPosition
import net.postchain.rell.toolbox.core.positionToOffset
import net.postchain.rell.toolbox.util.toLspPosition
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentContentChangeEvent

class Document(val fileUri: URI, val version: Int, val content: String) {

    fun getOffSet(position: Position): Int =
        positionToOffset(content, net.postchain.rell.toolbox.core.Position(position.line, position.character))

    fun getPosition(offset: Int): Position = offsetToPosition(content, offset).toLspPosition()

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

    fun getTextIn(interval: Interval): String = content.substring(interval.a, interval.b + 1)

}
