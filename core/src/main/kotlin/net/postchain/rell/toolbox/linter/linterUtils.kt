package net.postchain.rell.toolbox.linter

import net.postchain.rell.toolbox.core.parser.RellBaseVisitor
import net.postchain.rell.toolbox.core.parser.RellParser
import org.antlr.v4.runtime.ParserRuleContext


class NameNodesFinder : RellBaseVisitor<Unit>() {
    private val names = mutableListOf<RellParser.RuleX_NameNodeContext>()
    override fun visitRuleX_NameNode(ctx: RellParser.RuleX_NameNodeContext) {
        names.add(ctx)
    }

    fun getNodesUnder(parent: ParserRuleContext): List<ParserRuleContext> {
        names.clear()
        parent.accept(this)
        return names
    }

    fun getFirstNodeUnder(parent: ParserRuleContext): ParserRuleContext? = getNodesUnder(parent).firstOrNull()
}

fun ParserRuleContext.isUnderscore(): Boolean  = this.text == "_"