package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.formatter.FormatterRequest
import net.postchain.rell.toolbox.formatter.RellFormatter
import net.postchain.rell.toolbox.formatter.TextReplacement
import net.postchain.rell.toolbox.lsp.editing.Document
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import java.io.File
import java.net.URI

class RellFormattingManager(
    private val workspaceManager: RellWorkspaceManager
) {
    fun format(fileUri: URI, options: FormattingOptions?): List<TextEdit> {
        val formatterRequest = setUserOptions(options)
        val document = workspaceManager.getDocument(fileUri)
        val fileDocument = document ?: Document(fileUri, 1, File(fileUri).readText())
        val replacements = RellFormatter.getFormattingChanges(fileDocument.contents, formatterRequest)
        return toTextEdits(replacements, fileDocument)
    }

    private fun toTextEdits(replacements: List<TextReplacement>, fileDocument: Document): List<TextEdit> {
        return replacements.map { textReplacement ->
            val startPosition = fileDocument.getPosition(textReplacement.startOffset)
            val endPosition = fileDocument.getPosition(textReplacement.stopOffset)

            val range = Range(startPosition, endPosition)
            TextEdit(range, textReplacement.text)
        }
    }

    private fun setUserOptions(options: FormattingOptions?): FormatterRequest {
        val formatterRequest = FormatterRequest()
        if (options == null) return formatterRequest

        if (options["maxLineWidth"] != null) {
            formatterRequest.maxLineWidth = options["maxLineWidth"]!!.second.toInt()
        }
        if (options["insertSpaces"] != null) {
            formatterRequest.insertSpaces = options["insertSpaces"]!!.third
        }
        if (options["tabSize"] != null) {
            formatterRequest.tabSize = options["tabSize"]!!.second.toInt()
        }
        return formatterRequest
    }
}
