package net.postchain.rell.toolbox.lsp.editing

import net.postchain.rell.toolbox.common.offsetToPosition
import net.postchain.rell.toolbox.common.positionToOffset
import net.postchain.rell.toolbox.util.toLspPosition
import org.antlr.v4.runtime.misc.Interval
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.io.File
import java.io.IOException
import java.net.URI

data class Document(val fileUri: URI, val version: Int, var content: String) {

    fun getOffSet(position: Position): Int {
        return try {
            positionToOffset(content, net.postchain.rell.toolbox.common.Position(position.line, position.character))
        } catch (e: IndexOutOfBoundsException) {
            // HACK: This is a workaround for the case when the file was changed on disk
            // If the position is out of bounds, we need to read the file again in case file on disk was changed
            try {
                content = File(fileUri).readText()
                positionToOffset(content, net.postchain.rell.toolbox.common.Position(position.line, position.character))
            } catch (_: IOException) {
                throw e
            }
        }
    }

    fun getPosition(offset: Int): Position = offsetToPosition(content, offset).toLspPosition()

    fun getStartAndEndOffset(range: Range): Pair<Int, Int> =
        Pair(getOffSet(range.start), getOffSet(range.end))

    fun offSetInRange(offset: Int, range: Range): Boolean {
        try {
            val (startOffSet, endOffSet) = getStartAndEndOffset(range)
            return offset >= startOffSet && offset <= endOffSet
        } catch (e: IndexOutOfBoundsException) {
            return false
        }
    }

    /**
     * As opposed to [TextEdit][org.eclipse.lsp4j.TextEdit] the positions in the edits of a
     * [DidChangeTextDocumentParams][org.eclipse.lsp4j.DidChangeTextDocumentParams] refer to the
     * state after applying the preceding edits. See
     * https://microsoft.github.io/language-server-protocol/specification#textedit-1
     * https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textEditArray
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
                (
                    currentDocument.content.substring(0, start) + change.text +
                        currentDocument.content.substring(end)
                    )
            }
            currentDocument = Document(fileUri, newVersion, newContent)
        }
        return currentDocument
    }

    fun getTextIn(interval: Interval): String = content.substring(interval.a, interval.b + 1)

    fun previousNonLetterChar(offset: Int): Char? {
        var currentOffset = offset - 1
        while (content.getOrNull(currentOffset)?.isLetter() == true) {
            currentOffset--
        }
        return content.getOrNull(currentOffset)
    }
}
