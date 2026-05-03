/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.SpecificQuotesIssue

class SpecificQuotesRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId = RULE_ID

    override fun visitStringExpr(ctx: RellManualParser.StringExprContext) {
        if (!config.enabled || config.ruleQuoteFormat == null || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        val quote = config.ruleQuoteFormat!!
        val literal = quote.literal
        val string = ctx.text
        if (!string.startsWith(literal) && !string.endsWith(literal) && !hasIgnoreCommentOnTop(ctx.start)) {
            report(SpecificQuotesIssue(ctx, ruleId, "Use ${quote.name.lowercase()} quotes for $string", quote))
        }
    }

    companion object {
        const val RULE_ID = "rule_quote_format"
    }
}
