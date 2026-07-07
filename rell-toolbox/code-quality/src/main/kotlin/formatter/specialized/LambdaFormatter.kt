/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.LambdaBodyBlockContext
import net.postchain.rell.base.compiler.parser.antlr.RellParser.LambdaExprContext
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.BraceFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Formats a lambda expression `lambdaParams '->' lambdaBody`. The `->` is surrounded by a single
 * space; a parenthesised parameter list is tightened to `(x: integer, y: integer)`; the body is
 * delegated (an expression body falls through to the generic recursion, a value block to
 * [LambdaBodyBlockFormatter]).
 */
class LambdaExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<LambdaExprContext> {
    override fun format(node: LambdaExprContext, doc: FormattableDocument) {
        val arrow = tokenAnalyzer.directTokenFor(node, "->")
        if (arrow != null) doc.surround(arrow) { it.oneSpace() }

        val params = node.lambdaParams()
        // Only the parenthesised form needs spacing work; a bare `x` parameter has none.
        if (tokenAnalyzer.directTokenFor(params, "(") != null) {
            braceFormatter.formatBracePairWithoutSpace(params, doc, BracePairTypes.PARENTHESES)
            for (param in params.lambdaParam()) {
                whitespaceFormatter.formatType(param, doc)
            }
            for (i in 0 until params.childCount) {
                val c = params.getChild(i)
                if (c is TerminalNode && c.symbol.text == ",") {
                    doc.prepend(c) { it.noSpace() }
                    doc.append(c) { it.oneSpace() }
                }
            }
        }

        doc.format(node.lambdaBody())
    }
}

/**
 * Formats a value-block lambda body `'{' statement* expression? '}'`. Mirrors [BlockStmtFormatter]
 * but also lays out the trailing result expression, and renders an empty body as `{}`. Statements
 * and the trailing expression each go on their own indented line; the closing brace drops to its
 * own line.
 */
class LambdaBodyBlockFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<LambdaBodyBlockContext> {
    override fun format(node: LambdaBodyBlockContext, doc: FormattableDocument) {
        val open = tokenAnalyzer.directTokenFor(node, "{")
        val close = tokenAnalyzer.directTokenFor(node, "}")
        val statements = node.statement()
        val tailExpr = node.expression()

        if (statements.isEmpty() && tailExpr == null) {
            // Empty body: `{}`.
            doc.append(open) { it.noSpace() }
            doc.prepend(close) { it.noSpace() }
            return
        }

        doc.interiorIndent(node)

        statements.forEach { statement ->
            doc.prepend(statement) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
            doc.format(statement)
        }

        if (tailExpr != null) {
            doc.prepend(tailExpr) {
                it.newLine()
                it.highPriority()
            }
            doc.format(tailExpr)
        }

        doc.prepend(close) { it.newLine() }
    }
}
