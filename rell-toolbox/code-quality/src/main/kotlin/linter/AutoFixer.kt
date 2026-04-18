/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.common.Position
import net.postchain.rell.toolbox.common.TextReplacement
import net.postchain.rell.toolbox.common.applyTextReplacements
import net.postchain.rell.toolbox.common.positionToOffset
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.indexer.Resource

class AutoFixer {
    fun fix(resource: Resource, sourceText: String): String {
        val replacements = getFormatterReplacements(
            resource.formatterIssues,
            sourceText
        ) + getLinterReplacements(resource.linterIssues, sourceText)
        return applyTextReplacements(sourceText, replacements)
    }

    private fun getLinterReplacements(linterIssues: List<LinterIssue>, sourceText: String): List<TextReplacement> {
        return linterIssues.mapNotNull {
            it.fix()?.let { fix ->
                val startOffset = positionToOffset(sourceText, Position(fix.line, fix.charPositionInLine))
                val endOffset = positionToOffset(sourceText, Position(fix.line, fix.charPositionInLine + fix.length))
                TextReplacement(startOffset, endOffset, fix.newText)
            }
        }
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
