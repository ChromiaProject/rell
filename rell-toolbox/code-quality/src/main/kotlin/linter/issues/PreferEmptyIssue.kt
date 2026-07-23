/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

class PreferEmptyIssue(
    private val binaryCtx: RellParser.BinaryExprContext,
    ruleId: String,
    message: String,
    private val receiverText: String,
    private val empty: Boolean,
) : LinterIssue(binaryCtx, ruleId, message) {

    override fun fix(): LinterFix {
        val start = binaryCtx.start
        val stop = binaryCtx.stop
        val call = "$receiverText.empty()"
        val newText = if (empty) call else "not $call"
        return LinterFix(
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = newText,
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }
}
