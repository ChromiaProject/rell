/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.BaseExprContext
import net.postchain.rell.base.compiler.parser.antlr.RellParser.BinaryExprContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.tails
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode


internal class BaseExprFormatter(
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
 * Formats inline binary operator tokens of a [BinaryExprContext]. The grammar inlines the binary
 * operators as direct terminal children of the `binaryExpr` context (rather than wrapping them in a
 * `binaryOperator` rule context as the legacy grammar did), so we walk the children to surround each
 * binary-operator token with a single space.
 *
 * A line break the author put on either side of an operator is preserved, with the operator moved to
 * the start of the continuation line and indented one level. Both `a\n?: b` and `a ?:\nb` normalise to
 *
 *     a
 *         ?: b
 *
 * while `a ?: b` stays on one line.
 *
 * For unary prefix tokens (`+`, `-`, `not`, `++`, `--`) that appear before an operand, we
 * also emit `noSpace` after them, except for `not` which needs one space after.
 */
internal class ExpressionInlineOpFormatter : NodeFormatter<BinaryExprContext> {
    override fun format(node: BinaryExprContext, doc: FormattableDocument) {
        val n = node.childCount
        var previousOperand: ParserRuleContext? = null
        var afterOperand = false
        var i = 0
        while (i < n) {
            when (val c = node.getChild(i)) {
                is ParserRuleContext -> {
                    previousOperand = c
                    afterOperand = true
                    doc.format(c)
                }

                is TerminalNode -> {
                    val txt = c.symbol.text
                    if (afterOperand) {
                        // Binary operator, possibly the two-token `not in`.
                        var opEnd = c
                        if (txt == "not" && i + 1 < n) {
                            val nxt = node.getChild(i + 1)
                            if (nxt is TerminalNode && nxt.symbol.text == "in") {
                                opEnd = nxt
                                i++
                            }
                        }
                        val wraps = wrapsAtOperator(previousOperand, c, opEnd, node, i)
                        doc.prepend(c) {
                            if (wraps) {
                                it.noSpace()
                                it.newLine()
                                it.indent()
                            } else {
                                it.oneSpace()
                            }
                            it.highPriority()
                        }
                        doc.append(c) {
                            it.oneSpace()
                            it.highPriority()
                        }
                        if (opEnd !== c) {
                            doc.prepend(opEnd) { it.oneSpace() }
                            doc.append(opEnd) { it.oneSpace() }
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

    /**
     * True when the author put a line break on either side of the operator spanning [opStart]..[opEnd],
     * where [opEndIndex] is the child index of [opEnd].
     */
    private fun wrapsAtOperator(
        previousOperand: ParserRuleContext?,
        opStart: TerminalNode,
        opEnd: TerminalNode,
        node: BinaryExprContext,
        opEndIndex: Int,
    ): Boolean {
        if (previousOperand == null) return false
        if (previousOperand.stop.line != opStart.symbol.line) return true
        val rhsLine = startLineOfChildAfter(node, opEndIndex) ?: return false
        return opEnd.symbol.line != rhsLine
    }

    private fun startLineOfChildAfter(node: BinaryExprContext, index: Int): Int? {
        for (j in index + 1 until node.childCount) {
            when (val c = node.getChild(j)) {
                is ParserRuleContext -> return c.start.line
                is TerminalNode -> return c.symbol.line
            }
        }

        return null
    }
}
