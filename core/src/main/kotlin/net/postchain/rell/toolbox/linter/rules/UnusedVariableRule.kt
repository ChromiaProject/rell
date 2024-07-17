package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.toolbox.core.indexer.Resource
import net.postchain.rell.toolbox.core.parser.RellParser
import net.postchain.rell.toolbox.core.references.ReferenceIndexer
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.NameNodesFinder
import net.postchain.rell.toolbox.linter.isUnderscore
import net.postchain.rell.toolbox.linter.issues.UnusedVariableIssue
import org.antlr.v4.runtime.misc.Interval

class UnusedVariableRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {
    private val referenceIndexer = ReferenceIndexer(resource.workspaceUri, mutableMapOf(resource.fileUri to resource))

    companion object {
        const val RULE_ID = "rule_unused_variable"
    }

    override val ruleId = RULE_ID

    override fun visitRuleX_VarStmt(ctx: RellParser.RuleX_VarStmtContext) {
        if (isDisabled(config.ruleUnusedVariable) || hasIgnoreCommentOnTop(ctx.start) || hasSemanticErrors()) {
            return
        }
        val varDeclaratorCollector = SimpleVarDeclaratorCollector()
        ctx.accept(varDeclaratorCollector)
        val declarators = varDeclaratorCollector.declarations
        val nameNodesFinder = NameNodesFinder()
        for (declarator in declarators) {
            val name = nameNodesFinder.getFirstNodeUnder(declarator) ?: continue
            if (name.isUnderscore()) {
                continue
            }
            val symbolInterval = Interval.of(name.start.startIndex, name.start.startIndex)
            val symbolInfo = resource.locationInfo[symbolInterval]
            val references = referenceIndexer.findAllReferences(resource.fileUri, symbolInfo)
            if (references.isEmpty()) {
                report(UnusedVariableIssue(name, ruleId, "Variable '${name.text}' is never used"))
            }
        }
    }
}