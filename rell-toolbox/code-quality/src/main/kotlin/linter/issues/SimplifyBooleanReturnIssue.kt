/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import net.postchain.rell.toolbox.linter.sourceText
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

internal class SimplifyBooleanReturnIssue(
    private val stmtCtx: ParserRuleContext,
    ruleId: String,
    message: String,
    private val condExpr: RellParser.ExpressionContext,
    private val negate: Boolean,
) : LinterIssue(stmtCtx, ruleId, message) {

    // The if/else spans several lines; underline only the leading `if` keyword.
    override val highlightStop: Token get() = ctx.start

    override fun fix(): LinterFix {
        val start = stmtCtx.start
        val stop = stmtCtx.stop
        val condText = condExpr.sourceText()
        val body = if (negate) negated(condText) else condText
        return LinterFix(
            title = "Replace with 'return $body;'",
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = "return $body;",
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }

    // `not` needs parentheses only when the condition has a lower-precedence binary operator.
    private fun negated(condText: String): String {
        val simple = condExpr.binaryExpr()?.childCount == 1
        return if (simple) "not $condText" else "not ($condText)"
    }
}
