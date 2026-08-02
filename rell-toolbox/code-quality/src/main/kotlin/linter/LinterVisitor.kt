/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.base.compiler.parser.antlr.RellBaseVisitor
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.rules.*
import org.antlr.v4.runtime.tree.RuleNode

internal class LinterVisitor(
    val config: LinterOptions,
    val resource: Resource,
    linterContext: LinterContext
) : RellBaseVisitor<Unit>() {

    private val rules = listOf(
        NamingConventionRule(config, resource, linterContext),
        SpecificQuotesRule(config, resource, linterContext),
        ConstantDetectionRule(config, resource, linterContext),
        UnusedVariableRule(config, resource, linterContext),
        ImportFromNonModuleRule(config, resource, linterContext),
        OuterJoinCartesianProductRule(config, resource, linterContext),
        ReplaceIfWithWhenRule(config, resource, linterContext),
        SimplifyBooleanReturnRule(config, resource, linterContext),
        RedundantBooleanComparisonRule(config, resource, linterContext),
        SimplifyNullableIfRule(config, resource, linterContext),
        PreferNullCheckOperatorRule(config, resource, linterContext),
        PreferEmptyRule(config, resource, linterContext),
        AtCardinalityMisuseRule(config, resource, linterContext),
        PreferAtProjectionRule(config, resource, linterContext),
    )

    override fun visitChildren(node: RuleNode) {
        val context = node.ruleContext
        for (rule in rules) {
            if (context.javaClass in rule.handledContexts) {
                context.accept(rule)
            }
        }
        super.visitChildren(node)
    }
}
