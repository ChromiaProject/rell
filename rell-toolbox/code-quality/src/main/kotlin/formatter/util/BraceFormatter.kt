/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.util

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

class BraceFormatter {
    fun formatBracePairWithoutSpace(node: ParserRuleContext, doc: FormattableDocument, pair: BracePairTypes) {
        val (openingNode, closingNode) = bracePairFor(node, pair)

        if (openingNode != null && closingNode != null) {
            doc.append(openingNode) { p ->
                p.noSpace()
                p.highPriority()
            }
            doc.prepend(closingNode) { p ->
                p.noSpace()
                p.highPriority()
            }
        }
    }

    fun formatBracePairWithSpace(node: ParserRuleContext, doc: FormattableDocument, pair: BracePairTypes) {
        val (openingNode, closingNode) = bracePairFor(node, pair)
        if (openingNode != null && closingNode != null) {
            doc.append(openingNode) {
                it.oneSpace()
                it.highPriority()
            }
            doc.prepend(closingNode) {
                it.oneSpace()
                it.highPriority()
            }
        }
    }

    fun bracePairFor(node: ParserRuleContext?, pairTypes: BracePairTypes): Pair<TerminalNode?, TerminalNode?> {
        node ?: return Pair(null, null)
        val allBraces = mutableListOf<TerminalNode>()
        findAllBraces(node, pairTypes, allBraces)

        return findMatchingOpenClosing(allBraces, pairTypes)
    }

    private fun findMatchingOpenClosing(
        allBraces: MutableList<TerminalNode>,
        pairTypes: BracePairTypes
    ): Pair<TerminalNode?, TerminalNode?> {
        var braceCount = 0
        var openingBrace: TerminalNode? = null

        for (brace in allBraces) {
            if (brace.symbol.text == pairTypes.opening) {
                if (openingBrace == null) openingBrace = brace
                braceCount++
            } else {
                braceCount--
                if (braceCount == 0 && openingBrace != null) {
                    return Pair(openingBrace, brace)
                }
            }
        }
        return Pair(null, null)
    }

    private fun findAllBraces(node: ParserRuleContext, pairTypes: BracePairTypes, braces: MutableList<TerminalNode>) {
        node.children?.forEach { child ->
            when (child) {
                is TerminalNode -> {
                    if (child.symbol.text in setOf(pairTypes.opening, pairTypes.closing)) {
                        braces.add(child)
                    }
                }

                is ParserRuleContext -> findAllBraces(child, pairTypes, braces)
            }
        }
    }
}