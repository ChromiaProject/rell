/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token

class SimplifyNullableIfIssue(
    anchorCtx: ParserRuleContext,
    ruleId: String,
    message: String,
    /** Replacement text, or null when the rewrite cannot be applied safely (diagnostic only). */
    private val newText: String?,
    // The statement-form guard is rewritten together with the declaration above it, so the replaced
    // span is wider than the reported node.
    private val start: Token = anchorCtx.start,
    private val stop: Token = anchorCtx.stop,
) : LinterIssue(anchorCtx, ruleId, message) {

    override fun fix(): LinterFix? {
        val text = newText ?: return null
        return LinterFix(
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = text,
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }
}
