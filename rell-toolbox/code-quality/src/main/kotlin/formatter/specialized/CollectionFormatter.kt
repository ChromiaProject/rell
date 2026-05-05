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

class MapExprFormatter(
    val braceFormatter: BraceFormatter,
    val lineAnalyzer: LineAnalyzer,
    val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<NonEmptyMapLiteralExprContext> {
    override fun format(node: NonEmptyMapLiteralExprContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.BRACKETS)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.BRACKETS)
        val trailingComma = node.findTrailingComma("]")
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)

        val entries = node.getMapEntries()
        entries.forEachIndexed { index, (keyExpr, valExpr) ->
            doc.prepend(keyExpr) { it.oneSpace() }
            // No space before the ':' between key and value (the ':' is a terminal between them).
            doc.append(keyExpr) { it.noSpace() }
            doc.prepend(valExpr) { it.oneSpace() }
            doc.append(valExpr) { it.noSpace() }

            if (lineSeparate) {
                doc.prepend(keyExpr) {
                    it.newLine()
                    it.indent()
                }
                if (index == entries.lastIndex) {
                    doc.append(valExpr) {
                        it.newLine()
                    }
                }
            }
            doc.format(keyExpr)
            doc.format(valExpr)
        }
    }
}

class ListExprFormatter(
    val braceFormatter: BraceFormatter,
    val lineAnalyzer: LineAnalyzer,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<ListLiteralExprContext> {
    override fun format(node: ListLiteralExprContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.BRACKETS)
        val (items, trailingComma) = node.getListItems()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(items)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(items, doc)
        items.forEach { doc.format(it) }
    }
}

/**
 * `# mirrorStructExpr` alt of `baseExprHead`: `'struct' '<' 'mutable'? type '>'`.
 */
class MirrorStructExprFormatterImpl(
    val braceFormatter: BraceFormatter,
) : NodeFormatter<MirrorStructExprContext> {
    override fun format(node: MirrorStructExprContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.ANGLE)
        // Walk children: 'struct' '<' 'mutable'? type '>'.
        node.children?.forEachIndexed { i, c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode) {
                when (c.symbol.text) {
                    "struct" -> doc.append(c) {
                        it.noSpace()
                        it.setNewLines(0)
                        it.highPriority()
                    }
                    "mutable" -> doc.append(c) { it.oneSpace() }
                }
            }
        }
    }
}

/**
 * `# mirrorStructType` alt of `primaryType`: `'struct' '<' 'mutable'? type '>'`.
 */
class MirrorStructTypeFormatter(
    val braceFormatter: BraceFormatter,
) : NodeFormatter<MirrorStructTypeContext> {
    override fun format(node: MirrorStructTypeContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.ANGLE)
        node.children?.forEach { c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode) {
                when (c.symbol.text) {
                    "struct" -> doc.append(c) {
                        it.noSpace()
                        it.setNewLines(0)
                        it.highPriority()
                    }
                    "mutable" -> doc.append(c) { it.oneSpace() }
                }
            }
        }
    }
}

/**
 * `# tupleHead` alt of `baseExprHead`:
 *   '(' ((RULE_ID '=')? expression) (',' (RULE_ID '=')? expression)* ','? ')'
 *
 * (Was `RuleX_TupleExprContext` in the old grammar.)
 */
class TupleHeadFormatter(
    val braceFormatter: BraceFormatter,
    val lineAnalyzer: LineAnalyzer,
    val whitespaceFormatter: WhitespaceFormatter,
    val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<TupleHeadContext> {
    override fun format(node: TupleHeadContext, doc: FormattableDocument) {
        val items = collectItemAnchors(node)
        val trailingComma = node.findTrailingComma(")")
        val lineSeparateExpression = node.start.line != node.stop.line

        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparateExpression)

        // RULE_ID '=' field labels: surround '=' with one space and ensure the label is on
        // the same line as the value.
        node.children?.forEach { c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode && c.symbol.text == "=") {
                doc.prepend(c) {
                    it.oneSpace()
                    it.setNewLines(0)
                    it.highPriority()
                }
                doc.append(c) {
                    it.oneSpace()
                    it.setNewLines(0)
                    it.highPriority()
                }
            }
        }

        if (lineSeparateExpression) {
            doc.interiorIndent(node)
            items.forEachIndexed { idx, anchor ->
                anchor.applyPrepend(doc) { it.newLine() }
                if (idx == items.lastIndex) {
                    doc.append(node.expression().last()) { it.newLine() }
                }
            }
        } else {
            items.forEachIndexed { idx, anchor ->
                anchor.applyPrepend(doc) { it.oneSpace() }
                if (idx == items.lastIndex) {
                    doc.append(node.expression().last()) { it.noSpace() }
                }
            }
        }
    }

    /**
     * Collect each tuple item's anchor: either the RULE_ID label terminal (for `name = expr`
     * items) or the leading expression context (for plain `expr` items). Returns a list
     * sized to [TupleHeadContext.expression].size.
     */
    private fun collectItemAnchors(node: TupleHeadContext): List<ItemAnchor> {
        val result = mutableListOf<ItemAnchor>()
        var i = 0
        val n = node.childCount
        var atItemStart = false
        while (i < n) {
            val c = node.getChild(i)
            if (c is org.antlr.v4.runtime.tree.TerminalNode) {
                val txt = c.symbol.text
                when (txt) {
                    "(" -> atItemStart = true
                    "," -> atItemStart = true
                    ")" -> atItemStart = false
                    else -> {
                        if (atItemStart && c.symbol.type == net.postchain.rell.base.compiler.parser.antlr.RellParser.RULE_ID) {
                            result.add(ItemAnchor.Term(c))
                            atItemStart = false
                        }
                    }
                }
            } else if (c is ParserRuleContext) {
                if (atItemStart) {
                    result.add(ItemAnchor.Rule(c))
                    atItemStart = false
                }
            }
            i++
        }
        return result
    }

    private sealed class ItemAnchor {
        class Term(val node: org.antlr.v4.runtime.tree.TerminalNode) : ItemAnchor()
        class Rule(val ctx: ParserRuleContext) : ItemAnchor()

        fun applyPrepend(doc: FormattableDocument, mod: (net.postchain.rell.toolbox.formatter.Changes) -> Unit) {
            when (this) {
                is Term -> doc.prepend(node, mod)
                is Rule -> doc.prepend(ctx, mod)
            }
        }
    }
}
