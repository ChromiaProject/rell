/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.ReplaceIfExprWithWhenIssue
import net.postchain.rell.toolbox.linter.issues.ReplaceIfStmtWithWhenIssue
import net.postchain.rell.toolbox.linter.issues.elseIfChainNext
import org.antlr.v4.runtime.RuleContext

class ReplaceIfWithWhenRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.IfStmtAltContext::class.java,
        RellParser.IfExprContext::class.java,
    )

    override fun visitIfStmtAlt(ctx: RellParser.IfStmtAltContext) {
        if (isDisabled(config.ruleReplaceIfWithWhen) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        if (isElseBranch(ctx) || !hasElseIf(ctx)) {
            return
        }
        report(ReplaceIfStmtWithWhenIssue(ctx, ruleId, "Replace 'if' with 'when'"))
    }

    override fun visitIfExpr(ctx: RellParser.IfExprContext) {
        if (isDisabled(config.ruleReplaceIfWithWhen) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        if (isElseIfBranch(ctx) || ctx.elseIfChainNext() == null) {
            return
        }
        report(ReplaceIfExprWithWhenIssue(ctx, ruleId, "Replace 'if' with 'when'"))
    }

    // Only the head of an if/else-if chain is reported; inner ifs are covered by it.
    private fun isElseBranch(ctx: RellParser.IfStmtAltContext): Boolean {
        val parent = ctx.parent
        return parent is RellParser.IfStmtAltContext && parent.statement().size > 1 && parent.statement(1) === ctx
    }

    // The else arm nests as exprOrValueBlock -> expression -> binaryExpr -> ifExpr.
    private fun isElseIfBranch(ctx: RellParser.IfExprContext): Boolean {
        val branch = ctx.parent?.parent?.parent as? RellParser.ExprOrValueBlockContext ?: return false
        return (branch.parent as? RellParser.IfExprContext)?.elseIfChainNext() === ctx
    }

    private fun hasElseIf(ctx: RellParser.IfStmtAltContext): Boolean =
        ctx.statement().size > 1 && ctx.statement(1) is RellParser.IfStmtAltContext

    companion object {
        const val RULE_ID = "rule_replace_if_with_when"
    }
}
