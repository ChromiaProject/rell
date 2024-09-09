package net.postchain.rell.toolbox.indexer

import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.toolbox.formatter.FormatterIssue
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.parser.SyntaxError

enum class RellIssueSeverity {
    WARNING,
    ERROR
}

class RellIssue(
    val message: String?,
    val code: String,
    val severity: RellIssueSeverity,
    val line: Int,
    val column: Int,
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
            return RellIssue(
                message = linterIssue.message,
                code = "linter_issue:${linterIssue.ruleId}",
                severity = RellIssueSeverity.WARNING,
                line = linterIssue.ctx.start.line,
                column = linterIssue.ctx.start.charPositionInLine + 1
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
