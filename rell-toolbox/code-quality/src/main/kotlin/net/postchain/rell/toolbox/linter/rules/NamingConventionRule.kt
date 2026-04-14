/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter.rules

import net.postchain.rell.base.utils.ide.IdeSymbolKind
import net.postchain.rell.toolbox.indexer.Resource
import net.postchain.rell.toolbox.linter.LinterContext
import net.postchain.rell.toolbox.linter.LinterOptions
import net.postchain.rell.toolbox.linter.issues.NamingConventionIssue
import net.postchain.rell.toolbox.parser.RellParser.*
import org.antlr.v4.runtime.misc.Interval

class NamingConventionRule(config: LinterOptions, resource: Resource, linterContext: LinterContext) :
    LinterRule(config, resource, linterContext) {

    companion object {
        const val RULE_ID = "rule_naming_convention"
    }

    override val ruleId = RULE_ID

    override fun visitRuleX_NameNode(ctx: RuleX_NameNodeContext) {
        if (isDisabled(config.ruleNamingConvention) || hasIgnoreCommentOnTop(ctx.start)) {
            return
        }
        val name = ctx.text
        val uppercaseAllowed = uppercaseAllowed(ctx)
        if (!hasIgnoreCommentOnTop(ctx.start) && !isSnakeCase(name, uppercaseAllowed)) {
            if (name != "<missing RULE_ID>") {
                report(NamingConventionIssue(ctx, ruleId, "'$name' should be in snake case"))
            }
        }
    }

    private fun isSnakeCase(name: String, uppercaseAllowed: Boolean = false): Boolean {
        return if (uppercaseAllowed) {
            name.matches(Regex("^[a-z_][a-z0-9_]*$")) || name.matches(Regex("^[A-Z_][A-Z0-9_]*$"))
        } else {
            name.matches(Regex("^[a-z_][a-z0-9_]*$"))
        }
    }

    private fun uppercaseAllowed(ctx: RuleX_NameNodeContext): Boolean {
        val symbolInterval = Interval.of(ctx.start.startIndex, ctx.stop.stopIndex)
        val symbolInfo = resource.locationInfo[symbolInterval]
        return ctx.parent is RuleX_ConstantDefContext ||
            ctx.parent is RuleX_CommaSeparated_11Context ||
            symbolInfo?.ideSymbolInfo?.kind == IdeSymbolKind.MEM_ENUM_VALUE ||
            symbolInfo?.ideSymbolInfo?.kind == IdeSymbolKind.DEF_CONSTANT
    }
}
