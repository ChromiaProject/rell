/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

class PreferValIssue(
    private val varCtx: RellParser.VarStmtAltContext,
    ruleId: String,
    message: String,
) : LinterIssue(varCtx, ruleId, message) {
    override fun fix(): LinterFix {
        // Replace only the leading `var` keyword with `val`.
        val kw = varCtx.start
        return LinterFix(
            line = kw.line - 1,
            charPositionInLine = kw.charPositionInLine,
            length = KEYWORD_LENGTH,
            newText = "val",
            endLine = kw.line - 1,
            endCharPositionInLine = kw.charPositionInLine + KEYWORD_LENGTH,
        )
    }

    companion object {
        private const val KEYWORD_LENGTH = 3
    }
}
