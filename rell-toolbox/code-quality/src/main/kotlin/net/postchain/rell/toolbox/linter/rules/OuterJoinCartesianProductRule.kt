/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.OuterJoinCartesianProductIssue
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser

class OuterJoinCartesianProductRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    override val ruleId = RULE_ID

    override fun visitRuleX_AtExprFromItem(ctx: RellParser.RuleX_AtExprFromItemContext) {
        if (isDisabled(config.ruleOuterJoinCartesianProduct) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }

        if (hasOuterJoin(ctx) && joinConditionMissing(ctx)) {
            report(
                OuterJoinCartesianProductIssue(
                    ctx,
                    ruleId,
                    "Missing 'outer join' condition. resulting in cartesian product"
                )
            )
        }
    }

    private fun hasOuterJoin(ctx: RellParser.RuleX_AtExprFromItemContext): Boolean {
        return ctx.ruleX_Annotation().any { it.text == "@outer" }
    }

    private fun joinConditionMissing(ctx: RellParser.RuleX_AtExprFromItemContext): Boolean {
        val baseExprTailFinder = BaseExprTailFinder()
        val expr = baseExprTailFinder.getExpressionTail(ctx)
        return expr == null || expr.ruleX_BaseExprTailAt()?.ruleX_AtExprWhere()?.ruleX_CommaSeparated_20()
            ?.ruleX_CommaSeparated_19() == null
    }

    companion object {
        const val RULE_ID = "rule_outer_join_cartesian_product"
    }
}

class BaseExprTailFinder : RellBaseVisitor<Unit>() {
    var expressionTail: RellParser.RuleX_BaseExprTailContext? = null

    override fun visitRuleX_BaseExprTail(ctx: RellParser.RuleX_BaseExprTailContext?) {
        if (ctx != null && expressionTail == null) {
            expressionTail = ctx
        }
    }

    fun getExpressionTail(ctx: RellParser.RuleX_AtExprFromItemContext): RellParser.RuleX_BaseExprTailContext? {
        expressionTail = null
        ctx.accept(this)
        return expressionTail
    }
}
