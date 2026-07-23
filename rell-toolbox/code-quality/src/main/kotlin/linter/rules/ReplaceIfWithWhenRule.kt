/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.ReplaceIfWithWhenIssue

class ReplaceIfWithWhenRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override fun visitIfStmtAlt(ctx: RellParser.IfStmtAltContext) {
        if (isDisabled(config.ruleReplaceIfWithWhen) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        if (isElseBranch(ctx) || !hasElseIf(ctx)) {
            return
        }
        report(ReplaceIfWithWhenIssue(ctx, ruleId, "Replace 'if' with 'when'"))
    }

    // Only the head of an if/else-if chain is reported; inner ifs are covered by it.
    private fun isElseBranch(ctx: RellParser.IfStmtAltContext): Boolean {
        val parent = ctx.parent
        return parent is RellParser.IfStmtAltContext && parent.statement().size > 1 && parent.statement(1) === ctx
    }

    private fun hasElseIf(ctx: RellParser.IfStmtAltContext): Boolean =
        ctx.statement().size > 1 && ctx.statement(1) is RellParser.IfStmtAltContext

    companion object {
        const val RULE_ID = "rule_replace_if_with_when"
    }
}
