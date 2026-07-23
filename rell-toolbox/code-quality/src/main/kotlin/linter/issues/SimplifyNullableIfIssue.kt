/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue

class SimplifyNullableIfIssue(
    private val ifCtx: RellParser.IfExprContext,
    ruleId: String,
    message: String,
    private val newText: String,
) : LinterIssue(ifCtx, ruleId, message) {

    override fun fix(): LinterFix {
        val start = ifCtx.start
        val stop = ifCtx.stop
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
