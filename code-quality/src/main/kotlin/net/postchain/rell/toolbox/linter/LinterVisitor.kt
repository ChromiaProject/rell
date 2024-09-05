package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.linter.rules.ConstantDetectionRule
import net.postchain.rell.toolbox.linter.rules.ImportFromNonModuleRule
import net.postchain.rell.toolbox.linter.rules.NamingConventionRule
import net.postchain.rell.toolbox.linter.rules.SpecificQuotesRule
import net.postchain.rell.toolbox.linter.rules.UnusedVariableRule
import org.antlr.v4.runtime.tree.RuleNode


class LinterVisitor(
    val config: LinterOptions,
    val resource: Resource,
    val linterContext: LinterContext
) : RellBaseVisitor<Unit>() {

    private val rules = listOf(
        NamingConventionRule(config, resource, linterContext),
        SpecificQuotesRule(config, resource, linterContext),
        ConstantDetectionRule(config, resource, linterContext),
        UnusedVariableRule(config, resource, linterContext),
        ImportFromNonModuleRule(config, resource, linterContext)
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
        } catch (e: NoSuchMethodException) {
            // Method not found, so it's not overridden.
            return false
        }
    }
}
