package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.core.Position
import net.postchain.rell.toolbox.core.TextReplacement
import net.postchain.rell.toolbox.core.applyTextReplacements
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.core.positionToOffset
import net.postchain.rell.toolbox.formatter.FormatterIssue

class AutoFixer {
    fun fix(resource: Resource, sourceText: String): String {
        val replacements = getFormatterReplacements(
            resource.formatterIssues,
            sourceText
        ) + getLinterReplacements(resource.linterIssues, sourceText)
        return applyTextReplacements(sourceText, replacements)
    }

    private fun getLinterReplacements(linterIssues: List<LinterIssue>, sourceText: String): List<TextReplacement> {
        return linterIssues.map {
            it.fix()?.let { fix ->
                val startOffset = positionToOffset(sourceText, Position(fix.line, fix.charPositionInLine))
                val endOffset = positionToOffset(sourceText, Position(fix.line, fix.charPositionInLine + fix.length))
                TextReplacement(startOffset, endOffset, fix.newText)
            }
        }.filterNotNull()
    }

    private fun getFormatterReplacements(
        formatterIssues: List<FormatterIssue>,
        sourceText: String
    ): List<TextReplacement> {
        return formatterIssues.map {
            val startOffset = positionToOffset(sourceText, it.textEdit.range.start)
            val endOffset = positionToOffset(sourceText, it.textEdit.range.end)
            TextReplacement(startOffset, endOffset, it.textEdit.newText)
        }
    }
}
