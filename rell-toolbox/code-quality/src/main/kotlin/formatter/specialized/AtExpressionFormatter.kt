/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.*
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Formats `atExprModifiers`: `'limit' expression ('offset' expression)?`
 * or `'offset' expression ('limit' expression)?`. Surround the keyword tokens with one space.
 */
class AtExprModFormatter(val tokenAnalyzer: TokenAnalyzer) : NodeFormatter<AtExprModifiersContext> {
    override fun format(node: AtExprModifiersContext, doc: FormattableDocument) {
        val limit = tokenAnalyzer.tokenFor(node, "limit")
        val offset = tokenAnalyzer.tokenFor(node, "offset")
        if (limit != null) doc.surround(limit) { it.oneSpace() }
        if (offset != null) doc.surround(offset) { it.oneSpace() }
        node.expression().forEach { doc.format(it) }
    }
}

/**
 * Formats `# atExpr` alt of `baseExprHead`:
 *   '(' (annotation* (RULE_ID ':')? expression) (',' annotation* (RULE_ID ':')? expression)* ','? ')'
 *      atExprAt atExprWhere atExprWhat? atExprModifiers?
 *
 * The from-items list is flat as direct children of [AtExprContext] (no per-item wrapper rule).
 */
class AtExprFromFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<AtExprContext> {
    override fun format(node: AtExprContext, doc: FormattableDocument) {
        // Walk children to find: opening '(', list of items, closing ')', then atExprAt/where/what/modifiers.
        val items = collectFromItems(node)
        val trailingComma = node.findTrailingComma(")")
        val (open, close) = openCloseParens(node)

        val lineSeparate = if (open != null && close != null) {
            open.symbol.line != close.symbol.line ||
                items.any { it.start.line != it.stop.line }
        } else {
            false
        }
        if (!lineSeparate || items.isEmpty()) {
            // Treat this paren pair like a normal one without space inside.
            if (open != null && close != null) {
                doc.append(open) {
                    it.noSpace()
                    it.highPriority()
                }
                doc.prepend(close) {
                    it.noSpace()
                    it.highPriority()
                }
            }
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(items, doc, formatAsMultiLine = lineSeparate)

        // Format children that follow.
        doc.format(node.atExprAt())
        doc.format(node.atExprWhere())
        node.atExprWhat()?.let { doc.format(it) }
        node.atExprModifiers()?.let { doc.format(it) }
    }

    /**
     * Each "from item" is the synthetic span: optional `annotation*`, optional `RULE_ID ':'`,
     * `expression`. We treat the [ExpressionContext] as the item anchor since its line range
     * fully covers the visible item content for indentation purposes.
     */
    private fun collectFromItems(node: AtExprContext): List<ParserRuleContext> {
        // The expressions are accessible via node.expression(); they appear inside the paren pair.
        // Items past the closing ')' are part of `atExprAt`/`atExprWhere`/etc.
        val open = openCloseParens(node).first ?: return emptyList()
        val close = openCloseParens(node).second ?: return emptyList()
        val openIdx = open.symbol.tokenIndex
        val closeIdx = close.symbol.tokenIndex
        return node.expression().filter {
            it.start.tokenIndex > openIdx && it.stop.tokenIndex < closeIdx
        }
    }

    private fun openCloseParens(node: AtExprContext):
        Pair<org.antlr.v4.runtime.tree.TerminalNode?, org.antlr.v4.runtime.tree.TerminalNode?> {
        // First child '(' and the first ')' before atExprAt.
        var open: org.antlr.v4.runtime.tree.TerminalNode? = null
        var close: org.antlr.v4.runtime.tree.TerminalNode? = null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i)
            if (c is org.antlr.v4.runtime.tree.TerminalNode) {
                val t = c.symbol.text
                if (t == "(" && open == null) open = c
                else if (t == ")" && open != null && close == null) close = c
            } else if (c is AtExprAtContext) {
                break
            }
        }
        return Pair(open, close)
    }
}

class AtExprAtFormatter : NodeFormatter<AtExprAtContext> {
    override fun format(node: AtExprAtContext, doc: FormattableDocument) {
        doc.surround(node) {
            it.oneSpace()
            it.highPriority()
        }
    }
}

class AtExprWhereFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<AtExprWhereContext> {
    override fun format(node: AtExprWhereContext, doc: FormattableDocument) {
        val (expressionRef, trailingComma) = node.getWhereItems()
        val formatAsMultiLine = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.CURLY)

        if (!formatAsMultiLine || expressionRef.isEmpty()) {
            braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.CURLY)
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, formatAsMultiLine)
        argumentFormatter.formatArguments(expressionRef, doc, formatAsMultiLine = formatAsMultiLine)
    }
}

class AtExprWhatCmplxFormatter(
    val lineAnalyzer: LineAnalyzer,
    val braceFormatter: BraceFormatter,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
    val expressionFormatter: ExpressionFormatter,
) : NodeFormatter<AtExprWhatComplexContext> {
    override fun format(node: AtExprWhatComplexContext, doc: FormattableDocument) {
        doc.prepend(node) { it.oneSpace() }

        val formatAsMultiLine = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        // Items here are expressions inside the paren list, plus possible annotation prefixes.
        // We use ExpressionContext children as the items.
        val items = node.expression()
        val trailingComma = node.findTrailingComma(")")

        if (!formatAsMultiLine) {
            braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.PARENTHESES)
        }
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, formatAsMultiLine)
        argumentFormatter.formatArguments(items, doc, formatAsMultiLine = formatAsMultiLine)

        // Annotations between commas: collapse to one space before and after each annotation.
        node.annotation().forEach { ann ->
            doc.prepend(ann) {
                it.setNewLines(0)
                it.oneSpace()
                it.highPriority()
            }
            doc.append(ann) {
                it.setNewLines(0)
                it.oneSpace()
                it.highPriority()
            }
        }

        items.forEach { doc.format(it) }
    }
}
