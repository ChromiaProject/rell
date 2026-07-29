/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.parser.SyntaxError

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
     * a point (compiler messages, syntax errors) leave the default zero-width span, which editors
     * render as a one-character highlight.
     */
    val endLine: Int = line,
    val endColumn: Int = column,
) {

    companion object {
        fun fromCMessage(message: C_Message): RellIssue {
            // TODO: figure out lineEnd and columnEnd positions if possible to improve error reporting
            return RellIssue(
                message = message.text,
                code = message.code,
                severity = when (message.type) {
                    C_MessageType.ERROR -> RellIssueSeverity.ERROR
                    C_MessageType.WARNING -> RellIssueSeverity.WARNING
                },
                line = message.pos.line(),
                column = message.pos.column()
            )
        }

        fun fromSyntaxError(syntaxError: SyntaxError): RellIssue {
            // TODO: figure out lineEnd and columnEnd positions if possible to improve error reporting
            return RellIssue(
                message = syntaxError.message,
                code = "Syntax Error",
                severity = RellIssueSeverity.ERROR,
                line = syntaxError.line,
                column = syntaxError.charPositionInLine + 1
            )
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
