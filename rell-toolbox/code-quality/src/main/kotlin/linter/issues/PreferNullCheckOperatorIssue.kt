/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

internal class PreferNullCheckOperatorIssue(
    private val binaryCtx: RellParser.BinaryExprContext,
    ruleId: String,
    message: String,
    private val newText: String,
) : LinterIssue(binaryCtx, ruleId, message) {

    override fun fix(): LinterFix {
        val start = binaryCtx.start
        val stop = binaryCtx.stop
        return LinterFix(
            title = "Replace with '$newText'",
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = newText,
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }
}
