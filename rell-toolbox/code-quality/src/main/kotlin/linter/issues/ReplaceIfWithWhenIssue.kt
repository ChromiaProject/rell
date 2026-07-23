/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval

class ReplaceIfWithWhenIssue(
    private val ifCtx: RellParser.IfStmtAltContext,
    ruleId: String,
    message: String
) : LinterIssue(ifCtx, ruleId, message) {

    override fun fix(): LinterFix {
        val start = ifCtx.start
        val stop = ifCtx.stop
        return LinterFix(
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = buildWhenText(),
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }

    private fun buildWhenText(): String {
        val indent = " ".repeat(ifCtx.start.charPositionInLine)
        val armIndent = indent + INDENT_UNIT
        val sb = StringBuilder("when {\n")
        var current = ifCtx
        while (true) {
            sb.append(armIndent)
                .append(reindent(sourceText(current.expression())))
                .append(" -> ")
                .append(reindent(sourceText(current.statement(0))))
                .append("\n")
            val elseBranch = if (current.statement().size > 1) current.statement(1) else null
            when (elseBranch) {
                null -> break
                is RellParser.IfStmtAltContext -> current = elseBranch
                else -> {
                    sb.append(armIndent)
                        .append("else -> ")
                        .append(reindent(sourceText(elseBranch)))
                        .append("\n")
                    break
                }
            }
        }
        return sb.append(indent).append("}").toString()
    }

    private fun sourceText(ctx: ParserRuleContext): String {
        return ctx.start.inputStream.getText(Interval.of(ctx.start.startIndex, ctx.stop.stopIndex))
    }

    // Arms sit one level deeper than the original `if`, so continuation lines
    // of multi-line conditions and blocks are shifted by one indent unit.
    private fun reindent(text: String): String {
        return text.lines().mapIndexed { index, line ->
            if (index == 0 || line.isBlank()) line else INDENT_UNIT + line
        }.joinToString("\n")
    }

    companion object {
        private const val INDENT_UNIT = "    "
    }
}
