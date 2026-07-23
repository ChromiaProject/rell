/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.linter

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Finds the first defining-name identifier under a parser rule context.
 */
class NameNodesFinder {
    fun getFirstNodeUnder(parent: ParserRuleContext): ParserRuleContext? {
        val terminal = findFirstIdentifier(parent) ?: return null
        return TerminalNameContext(terminal)
    }

    private fun findFirstIdentifier(node: ParseTree): TerminalNode? {
        if (node is TerminalNode) {
            val tokenType = node.symbol.type
            if (tokenType == net.postchain.rell.base.compiler.parser.antlr.RellLexer.RULE_ID) {
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
