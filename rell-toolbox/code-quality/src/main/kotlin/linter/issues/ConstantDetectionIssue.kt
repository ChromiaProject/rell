/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class ConstantDetectionIssue(
    ctx: ParserRuleContext,
    ruleId: String,
    message: String,
    /** The `var` statement to rewrite as `val`, or null when no safe rewrite exists. */
    private val varStmtCtx: RellParser.VarStmtAltContext? = null,
) : LinterIssue(ctx, ruleId, message) {

    // Underline the misused `var` keyword; a tuple declarator shares one `var` between several
    // names, so there the variable name itself is underlined instead.
    override val highlightStart: Token get() = varStmtCtx?.start ?: ctx.start
    override val highlightStop: Token get() = varStmtCtx?.start ?: super.highlightStop

    override fun fix(): LinterFix? {
        // Replace only the leading `var` keyword with `val`.
        val keyword = varStmtCtx?.start ?: return null
        return LinterFix(
            title = "Replace 'var' with 'val'",
            line = keyword.line - 1,
            charPositionInLine = keyword.charPositionInLine,
            length = KEYWORD_LENGTH,
            newText = "val",
            endLine = keyword.line - 1,
            endCharPositionInLine = keyword.charPositionInLine + KEYWORD_LENGTH,
        )
    }

    companion object {
        private const val KEYWORD_LENGTH = 3
    }
}
