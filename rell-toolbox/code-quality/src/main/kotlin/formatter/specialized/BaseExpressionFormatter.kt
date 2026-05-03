/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.BaseExprContext
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.ExpressionContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.BaseExprTail
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.tails
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode


class BaseExprFormatter(
    private val expressionFormatter: ExpressionFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<BaseExprContext> {
    override fun format(node: BaseExprContext, doc: FormattableDocument) {
        val exprHead = node.baseExprHead()
        val tails = node.tails()

        doc.append(exprHead) {
            it.noSpace()
            it.lowPriority()
        }

        if (tails.isNotEmpty()) {
            expressionFormatter.indentExpressionTail(exprHead, tails, doc)
        }

        var previousNode: ParserRuleContext = exprHead
        for (i in tails.indices) {
            val current = tails[i]
            val refForLineSeparation: ParserRuleContext = if (i == 0) exprHead else tails[i - 1].last
            val shouldLineSeparate = lineAnalyzer.lineSeparateExpr(current.first, refForLineSeparation)
            if (shouldLineSeparate) {
                expressionFormatter.formatExprTailMultiline(current, previousNode, doc)
            } else {
                expressionFormatter.formatExprTailSingleline(current, doc)
            }
            previousNode = current.last
        }
        doc.format(exprHead)
    }
}

/**
 * Formats inline binary operator tokens of an [ExpressionContext]. The new grammar inlines
 * the binary operators as direct terminal children of the expression context (instead of
 * wrapping them in a `binaryOperator` rule context as the legacy grammar did), so we walk
 * the children to surround each binary-operator token with a single space.
 *
 * For unary prefix tokens (`+`, `-`, `not`, `++`, `--`) that appear before an operand, we
 * also emit `noSpace` after them — except for `not` which needs one space after.
 */
class ExpressionInlineOpFormatter : NodeFormatter<ExpressionContext> {
    override fun format(node: ExpressionContext, doc: FormattableDocument) {
        val n = node.childCount
        var afterOperand = false
        var i = 0
        while (i < n) {
            val c = node.getChild(i)
            when {
                c is ParserRuleContext -> {
                    afterOperand = true
                    doc.format(c)
                }
                c is TerminalNode -> {
                    val txt = c.symbol.text
                    if (afterOperand) {
                        // Binary operator (possibly two-token `not in`).
                        doc.prepend(c) {
                            it.oneSpace()
                            it.highPriority()
                        }
                        doc.append(c) {
                            it.oneSpace()
                            it.highPriority()
                        }
                        if (txt == "not" && i + 1 < n) {
                            val nxt = node.getChild(i + 1)
                            if (nxt is TerminalNode && nxt.symbol.text == "in") {
                                doc.prepend(nxt) { it.oneSpace() }
                                doc.append(nxt) { it.oneSpace() }
                                i++
                            }
                        }
                        afterOperand = false
                    } else {
                        // Prefix operator before an operand. No space after most prefix ops;
                        // for `not` keep one space.
                        if (txt == "not") {
                            doc.append(c) { it.oneSpace() }
                        } else {
                            doc.append(c) { it.noSpace() }
                        }
                    }
                }
            }
            i++
        }
    }
}
