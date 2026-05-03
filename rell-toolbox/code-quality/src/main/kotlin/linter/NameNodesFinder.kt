/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Finds the first defining-name identifier under a parser rule context.
 *
 * In the legacy `Rell.g4` grammar, name-introducing positions were wrapped in a synthetic
 * `RuleX_NameNode` rule, so the finder simply visited that node. In the canonical
 * `RellManual.g4` grammar there is no such wrapper — names are bare `RULE_ID` terminals.
 * We approximate the old behaviour by descending into the parse tree and returning the
 * first `RULE_ID` terminal encountered (depth-first, left-to-right).
 */
class NameNodesFinder {
    fun getFirstNodeUnder(parent: ParserRuleContext): ParserRuleContext? {
        val terminal = findFirstIdentifier(parent) ?: return null
        return TerminalNameContext(terminal)
    }

    private fun findFirstIdentifier(node: ParseTree): TerminalNode? {
        if (node is TerminalNode) {
            val tokenType = node.symbol.type
            if (tokenType == net.postchain.rell.base.compiler.parser.antlr.RellManualLexer.RULE_ID) {
                return node
            }
            return null
        }
        for (i in 0 until node.childCount) {
            val r = findFirstIdentifier(node.getChild(i))
            if (r != null) return r
        }
        return null
    }
}

/**
 * Lightweight `ParserRuleContext` adapter that wraps a single `RULE_ID` terminal
 * so existing rule code can keep using the `ctx.start`, `ctx.stop`, `ctx.text`
 * surface that the legacy `RuleX_NameNodeContext` exposed.
 */
class TerminalNameContext(private val terminal: TerminalNode) : ParserRuleContext() {
    init {
        start = terminal.symbol
        stop = terminal.symbol
        addChild(terminal)
    }

    override fun getText(): String = terminal.text
}
