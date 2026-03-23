/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.parser.RellBaseVisitor
import net.postchain.rell.toolbox.parser.RellParser
import org.antlr.v4.runtime.ParserRuleContext

class NameNodesFinder : RellBaseVisitor<Unit>() {
    private val names = mutableListOf<RellParser.RuleX_NameNodeContext>()
    override fun visitRuleX_NameNode(ctx: RellParser.RuleX_NameNodeContext) {
        names.add(ctx)
    }

    private fun getNodesUnder(parent: ParserRuleContext): List<ParserRuleContext> {
        names.clear()
        parent.accept(this)
        return names
    }

    fun getFirstNodeUnder(parent: ParserRuleContext): ParserRuleContext? = getNodesUnder(parent).firstOrNull()
}
