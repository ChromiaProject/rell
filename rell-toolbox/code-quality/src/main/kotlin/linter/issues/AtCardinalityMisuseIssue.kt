/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.issues

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.RellIssueSeverity
import net.postchain.rell.toolbox.linter.LinterFix
import net.postchain.rell.toolbox.linter.LinterIssue
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.TerminalNode

/** Fix data for `@? {...}!!` → `@ {...}`: drop the `?` cardinality marker and the trailing `!!`. */
class NotNullToUnitFix(
    val baseCtx: RellParser.BaseExprContext,
    val qToken: TerminalNode,
    val bangCtx: RellParser.BaseExprTailNotNullContext,
)

class AtCardinalityMisuseIssue(
    anchorCtx: ParserRuleContext,
    ruleId: String,
    message: String,
    private val fixData: NotNullToUnitFix?,
) : LinterIssue(anchorCtx, ruleId, message, RellIssueSeverity.WARNING) {

    override fun fix(): LinterFix? {
        val fd = fixData ?: return null
        val base = fd.baseCtx
        val start = base.start
        val stop = base.stop
        val input = start.inputStream
        val q = fd.qToken.symbol
        val bang = fd.bangCtx
        // Rebuild the base expression, excising the `?` and the `!!` tokens.
        val sb = StringBuilder()
        sb.append(input.getText(Interval.of(start.startIndex, q.startIndex - 1)))
        sb.append(input.getText(Interval.of(q.stopIndex + 1, bang.start.startIndex - 1)))
        if (bang.stop.stopIndex < stop.stopIndex) {
            sb.append(input.getText(Interval.of(bang.stop.stopIndex + 1, stop.stopIndex)))
        }
        return LinterFix(
            line = start.line - 1,
            charPositionInLine = start.charPositionInLine,
            length = stop.stopIndex - start.startIndex + 1,
            newText = sb.toString(),
            endLine = stop.line - 1,
            endCharPositionInLine = stop.charPositionInLine + stop.text.length,
        )
    }
}
