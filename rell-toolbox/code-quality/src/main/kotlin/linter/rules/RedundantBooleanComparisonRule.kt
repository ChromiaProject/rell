/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.*
import net.postchain.rell.toolbox.linter.issues.RedundantBooleanComparisonIssue
import org.antlr.v4.runtime.RuleContext

/**
 * Flags comparisons of a value against a boolean literal (`x == true`, `x != false`, ...).
 */
internal class RedundantBooleanComparisonRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId
        get() = RULE_ID

    override val handledContexts: Set<Class<out RuleContext>> = setOf(
        RellParser.BinaryExprContext::class.java,
    )

    override fun visitBinaryExpr(ctx: RellParser.BinaryExprContext) {
        if (isDisabled(config.ruleRedundantBooleanComparison) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        if (ctx.isInsideAtWhere()) {
            return
        }
        val cmp = ctx.asSimpleComparison() ?: return
        val op = cmp.op.text
        if (op != "==" && op != "!=") {
            return
        }
        val leftBool = cmp.left.asBooleanLiteral()
        val rightBool = cmp.right.asBooleanLiteral()
        // Exactly one side is a boolean literal (skip `true == false`, which is a different smell).
        val literal = when {
            leftBool != null && rightBool == null -> leftBool
            rightBool != null && leftBool == null -> rightBool
            else -> return
        }
        report(RedundantBooleanComparisonIssue(ctx, ruleId, "Redundant comparison with '$literal'"))
    }

    companion object {
        const val RULE_ID = "rule_redundant_boolean_comparison"
    }
}
