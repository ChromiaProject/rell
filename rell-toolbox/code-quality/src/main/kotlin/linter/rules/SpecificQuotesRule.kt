/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.SpecificQuotesIssue
import org.antlr.v4.runtime.RuleContext

class SpecificQuotesRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.StringExprContext::class.java,
    )

    override fun visitStringExpr(ctx: RellParser.StringExprContext) {
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
