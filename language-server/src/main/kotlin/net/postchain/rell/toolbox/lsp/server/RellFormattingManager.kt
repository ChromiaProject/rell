package net.postchain.rell.toolbox.lsp.server

import net.postchain.rell.toolbox.common.TextReplacement
import net.postchain.rell.toolbox.formatter.FormatterOptions
import net.postchain.rell.toolbox.formatter.RellFormatter
import net.postchain.rell.toolbox.lsp.editing.Document
import net.postchain.rell.toolbox.lsp.editorconfig.RellFormatterOptionsResolver
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import java.io.File
import java.net.URI

class RellFormattingManager(
    private val workspaceManager: RellWorkspaceManager,
    private val formatterOptionsResolver: RellFormatterOptionsResolver
) {
    fun format(fileUri: URI, options: FormattingOptions?): List<TextEdit> {
        val (fileDocument, replacements) = getDocumentReplacements(fileUri, options)
        return toTextEdits(fileDocument, replacements)
    }

    fun rangeFormat(fileUri: URI, range: Range, options: FormattingOptions?): List<TextEdit> {
        val (fileDocument, replacements) = getDocumentReplacements(fileUri, options)

        val rangeStartOffset = fileDocument.getOffSet(range.start)
        val rangeEndOffset = fileDocument.getOffSet(range.end)
        val rangeReplacements =
            replacements.filter { it.stopOffset in rangeStartOffset..rangeEndOffset }

        return toTextEdits(fileDocument, rangeReplacements)
    }

    private fun getDocumentReplacements(
        fileUri: URI,
        options: FormattingOptions?
    ): Pair<Document, List<TextReplacement>> {
        val formatterRequest = setUserOptions(fileUri, options)
        val document = workspaceManager.getOpenDocument(fileUri)
        val fileDocument = document ?: Document(fileUri, 1, File(fileUri).readText())
        val replacements = RellFormatter.getFormattingChanges(fileDocument.content, formatterRequest)
        return fileDocument to replacements
    }

    private fun toTextEdits(fileDocument: Document, replacements: List<TextReplacement>): List<TextEdit> {
        return replacements.map { textReplacement ->
            val startPosition = fileDocument.getPosition(textReplacement.startOffset)
            val endPosition = fileDocument.getPosition(textReplacement.stopOffset)

            val range = Range(startPosition, endPosition)
            TextEdit(range, textReplacement.text)
        }
    }

    private fun setUserOptions(fileUri: URI, options: FormattingOptions?): FormatterOptions {
        val formatterRequest = FormatterOptions()
        val workspaceUri = workspaceManager.getIndexerFor(fileUri).workspaceUri
        val resolvedFormatterOptions = formatterOptionsResolver.getWorkspaceFormattingOptionsOrNull(workspaceUri)
        if (resolvedFormatterOptions != null) {
            return resolvedFormatterOptions
        }

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
