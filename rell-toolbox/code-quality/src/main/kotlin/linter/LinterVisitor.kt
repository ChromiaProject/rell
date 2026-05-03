/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.base.compiler.parser.antlr.RellManualBaseVisitor
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.rules.*
import org.antlr.v4.runtime.tree.RuleNode

class LinterVisitor(
    val config: LinterOptions,
    val resource: Resource,
    linterContext: LinterContext
) : RellManualBaseVisitor<Unit>() {

    private val rules = listOf(
        NamingConventionRule(config, resource, linterContext),
        SpecificQuotesRule(config, resource, linterContext),
        ConstantDetectionRule(config, resource, linterContext),
        UnusedVariableRule(config, resource, linterContext),
        ImportFromNonModuleRule(config, resource, linterContext),
        OuterJoinCartesianProductRule(config, resource, linterContext),
    )

    override fun visitChildren(node: RuleNode) {
        for (rule in rules) {
            if (isMethodDefined(
                    rule.javaClass,
                    "visit" + node.javaClass.simpleName.removeSuffix("Context"),
                    node.ruleContext.javaClass
                )
            ) {
                node.ruleContext.accept(rule)
            }
        }
        super.visitChildren(node)
    }

    private fun isMethodDefined(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>?): Boolean {
        try {
            clazz.getDeclaredMethod(methodName, *parameterTypes)
            return true
        } catch (@Suppress("SwallowedException") e: NoSuchMethodException) {
            // Method not found, so it's not overridden.
            return false
        }
    }
}
