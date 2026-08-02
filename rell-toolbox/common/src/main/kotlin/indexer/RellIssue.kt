/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.parser.AbstractRellCommonTokenStream
import net.postchain.rell.toolbox.parser.SyntaxError
import org.antlr.v4.runtime.Token

enum class RellIssueSeverity {
    /** Renders as a subtle hint rather than a warning; the default for style inspections. */
    WEAK_WARNING,
    WARNING,
    ERROR
}

data class RellIssue(
    val message: String?,
    val code: String,
    val severity: RellIssueSeverity,
    val line: Int,
    val column: Int,
    /**
     * Exclusive end of the highlighted span, 1-based like [line]/[column]. Sources that only know
     * a point leave the default zero-width span, which editors render as a one-character highlight.
     */
    val endLine: Int = line,
    val endColumn: Int = column,
) {

    companion object {
        fun fromCMessage(message: C_Message, tokenStream: AbstractRellCommonTokenStream): RellIssue {
            val span = issueSpan(tokenStream, message.pos.line(), message.pos.column() - 1)
            return RellIssue(
                message = message.text,
                code = message.code,
                severity = when (message.type) {
                    C_MessageType.ERROR -> RellIssueSeverity.ERROR
                    C_MessageType.WARNING -> RellIssueSeverity.WARNING
                },
                line = span.line,
                column = span.column,
                endLine = span.endLine,
                endColumn = span.endColumn,
            )
        }

        fun fromSyntaxError(syntaxError: SyntaxError, tokenStream: AbstractRellCommonTokenStream): RellIssue {
            val span = issueSpan(tokenStream, syntaxError.line, syntaxError.charPositionInLine)
            return RellIssue(
                message = syntaxError.message,
                code = "Syntax Error",
                severity = RellIssueSeverity.ERROR,
                line = span.line,
                column = span.column,
                endLine = span.endLine,
                endColumn = span.endColumn,
            )
        }

        private data class IssueSpan(val line: Int, val column: Int, val endLine: Int, val endColumn: Int)

        private fun issueSpan(
            tokenStream: AbstractRellCommonTokenStream,
            line: Int,
            column0: Int,
        ): IssueSpan {
            val end = tokenSpanEnd(tokenStream, line, column0)
            if (end != null) return IssueSpan(line, column0 + 1, end.first, end.second)
            return eofSpan(tokenStream, line, column0 + 1)
        }

        /**
         * Exclusive 1-based end of the token containing the given position, or null when no token
         * covers it (e.g. an error reported at end of file).
         */
        private fun tokenSpanEnd(
            tokenStream: AbstractRellCommonTokenStream,
            line: Int,
            column0: Int,
        ): Pair<Int, Int>? {
            val token = tokenStream.tokens.firstOrNull { token ->
                val length = if (token.type == Token.EOF) 0 else token.text?.length ?: 0
                token.line == line &&
                    column0 >= token.charPositionInLine &&
                    column0 < token.charPositionInLine + length
            } ?: return null
            return tokenEnd(token)
        }

        /**
         * Span for an error with no covering token — one reported at end of file. A zero-width
         * span at the very end of the document makes editors widen it forwards past the document
         * end (IntelliJ refuses such an annotation), so widen one character backwards instead,
         * anchoring to the last visible token when the error sits at a line start.
         */
        private fun eofSpan(
            tokenStream: AbstractRellCommonTokenStream,
            line: Int,
            column: Int,
        ): IssueSpan {
            if (column > 1) return IssueSpan(line, column - 1, line, column)
            val lastVisible = tokenStream.tokens.lastOrNull {
                it.type != Token.EOF && it.text?.isNotBlank() == true
            } ?: return IssueSpan(line, column, line, column)
            val (endLine, endColumn) = tokenEnd(lastVisible)
            val startColumn = if (endColumn > 1) endColumn - 1 else endColumn
            return IssueSpan(endLine, startColumn, endLine, endColumn)
        }

        private fun tokenEnd(token: Token): Pair<Int, Int> {
            val text = token.text ?: ""
            val lastNewline = text.lastIndexOf('\n')
            return if (lastNewline < 0) {
                token.line to token.charPositionInLine + text.length + 1
            } else {
                token.line + text.count { it == '\n' } to text.length - lastNewline
            }
        }

        fun fromLinterIssue(linterIssue: LinterIssue): RellIssue {
            val start = linterIssue.highlightStart
            val stop = linterIssue.highlightStop
            return RellIssue(
                message = linterIssue.message,
                code = "linter_issue:${linterIssue.ruleId}",
                severity = linterIssue.severity,
                line = start.line,
                column = start.charPositionInLine + 1,
                endLine = stop.line,
                // A synthesized token (error recovery) has no text; degrade to a zero-width end.
                endColumn = stop.charPositionInLine + (stop.text?.length ?: 0) + 1,
            )
        }

        fun fromFormatterIssue(formatterIssue: FormatterIssue): RellIssue {
            return RellIssue(
                message = formatterIssue.message,
                code = "linter_issue:formatting",
                severity = RellIssueSeverity.WARNING,
                line = formatterIssue.line,
                column = formatterIssue.column + 1
            )
        }
    }
}
