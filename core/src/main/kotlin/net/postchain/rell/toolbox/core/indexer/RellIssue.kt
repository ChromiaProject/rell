package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_Message
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.toolbox.core.parser.SyntaxError

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
    }
}