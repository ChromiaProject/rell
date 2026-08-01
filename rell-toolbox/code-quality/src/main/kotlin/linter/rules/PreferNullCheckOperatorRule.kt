/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.asNullGuard
import net.postchain.rell.toolbox.linter.isInsideAtWhere
import net.postchain.rell.toolbox.linter.issues.PreferNullCheckOperatorIssue
import net.postchain.rell.toolbox.linter.nullComparisonOperand
import net.postchain.rell.toolbox.linter.sourceText
import org.antlr.v4.runtime.RuleContext

/**
 * Suggests the null-check operator over the verbose comparison: `x != null` → `x??`.
 *
 * Yields to [SimplifyNullableIfRule]: when the enclosing `if` collapses to `?:` or `?.` altogether,
 * rewriting only its condition is the worse of the two suggestions, so this rule stays quiet there.
 */
internal class PreferNullCheckOperatorRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.BinaryExprContext::class.java,
    )

    override fun visitBinaryExpr(ctx: RellParser.BinaryExprContext) {
        if (isDisabled(config.rulePreferNullCheckOperator) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        // A where-clause is compiled to SQL, where the two forms are not interchangeable.
        if (ctx.isInsideAtWhere()) {
            return
        }
        val parent = ctx.parent as? RellParser.ExpressionContext ?: return
        val checked = parent.nullComparisonOperand("!=") ?: return
        if (isSimplifiableIfCondition(parent)) {
            return
        }
        report(PreferNullCheckOperatorIssue(ctx, ruleId, MESSAGE, "${checked.sourceText()}??"))
    }

    /** True if this expression is the condition of an `if` that [SimplifyNullableIfRule] rewrites whole. */
    private fun isSimplifiableIfCondition(cond: RellParser.ExpressionContext): Boolean {
        if (isDisabled(config.ruleSimplifyNullableIf)) {
            return false
        }
        val ifExpr = cond.parent as? RellParser.IfExprContext ?: return false
        return ifExpr.expression() === cond && ifExpr.asNullGuard() != null
    }

    companion object {
        const val RULE_ID = "rule_prefer_null_check_operator"
        private const val MESSAGE = "Replace '!= null' with the null-check operator '??'"
    }
}
