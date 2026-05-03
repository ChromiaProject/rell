/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.indexer.references.ReferenceIndexer
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.NameNodesFinder
import net.postchain.rell.toolbox.linter.isUnderscore
import org.antlr.v4.runtime.misc.Interval

class UnusedVariableRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {
    private val referenceIndexer = ReferenceIndexer(resource.workspaceUri, mutableMapOf(resource.fileUri to resource))

    companion object {
        const val RULE_ID = "rule_unused_variable"
    }

    override val ruleId = RULE_ID

    override fun visitVarStmtAlt(ctx: RellManualParser.VarStmtAltContext) {
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
                report(
                    net.postchain.rell.toolbox.linter.issues.UnusedVariableIssue(
                        name,
                        ruleId,
                        "Variable '${name.text}' is never used"
                    )
                )
            }
        }
    }

}
