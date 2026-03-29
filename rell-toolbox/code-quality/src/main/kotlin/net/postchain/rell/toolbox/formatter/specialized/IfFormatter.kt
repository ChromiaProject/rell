/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.parser.RellParser.*

class IfStmtFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val expressionFormatter: ExpressionFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<RuleX_IfStmtContext> {
    override fun format(node: RuleX_IfStmtContext, doc: FormattableDocument) {
        doc.append(node.ruleX_tkIF()) { it.oneSpace() }
        doc.surround(node.ruleX_Expression()) { it.noSpace() }
        val xExpression = node.ruleX_Expression()
        if (lineAnalyzer.formatAsMultiLine(
                expressionFormatter.prependNodeList(xExpression, xExpression.ruleX_BinaryExprOperand())
            )
        ) {
            expressionFormatter.formatMultiLineStmts(
                xExpression.ruleX_UnaryExpr(),
                xExpression.ruleX_BinaryExprOperand(),
                doc
            )
        } else {
            doc.format(xExpression)
        }

        if (node.ruleX_tkIF().stop.line != node.ruleX_StatementRef().start.line) {
            val openingCurly = tokenAnalyzer.tokenFor(node.ruleX_StatementRef(), "{")
            doc.prepend(openingCurly) {
                it.oneSpace()
                it.setNewLines(0)
                it.highPriority()
            }
            doc.prepend(node.ruleX_StatementRef()) {
                it.newLine()
            }
            if (openingCurly == null) {
                doc.prepend(node.ruleX_StatementRef()) {
                    it.indent()
                }
                doc.interiorIndentRangeIncludeLast(
                    node.ruleX_StatementRef(),
                    node.ruleX_StatementRef()
                )
            }
        } else {
            doc.prepend(node.ruleX_StatementRef()) { it.oneSpace() }
        }

        val elseStatement = node.ruleX_ElseStmt()
        if (elseStatement?.ruleX_tkELSE() != null && elseStatement.ruleX_StatementRef() != null) {
            if (elseStatement.ruleX_tkELSE().stop.line != elseStatement.ruleX_StatementRef().start.line) {
                doc.prepend(elseStatement.ruleX_tkELSE()) { it.newLine() }
                doc.prepend(elseStatement.ruleX_StatementRef()) {
                    it.newLine()
                    it.indent()
                }
                doc.interiorIndentRangeIncludeLast(
                    elseStatement.ruleX_StatementRef(),
                    elseStatement.ruleX_StatementRef()
                )
                doc.append(elseStatement.ruleX_StatementRef()) { it.noSpace() }
            } else {
                doc.prepend(elseStatement.ruleX_tkELSE()) { it.oneSpace() }
                doc.prepend(elseStatement.ruleX_StatementRef()) {
                    it.oneSpace()
                    it.highPriority()
                }
            }
        }
        doc.format(node.ruleX_StatementRef())
        doc.format(node.ruleX_ElseStmt())
    }
}

class IfExprFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_IfExprContext> {
    override fun format(node: RuleX_IfExprContext, doc: FormattableDocument) {
        val checkExpr = node.ruleX_ExpressionRef(0)
        val conditionalIfExpr = node.ruleX_ExpressionRef(1)
        val conditionalElseExpr = node.ruleX_ExpressionRef(2)

        doc.surround(node.ruleX_tkIF()) { it.oneSpace() }
        doc.surround(checkExpr) { it.noSpace() }
        doc.format(checkExpr)

        if (checkExpr.stop.line != conditionalIfExpr.start.line) {
            doc.prepend(conditionalIfExpr) {
                it.newLine()
                it.indent()
                it.highPriority()
            }
            doc.interiorIndentRangeIncludeLast(conditionalIfExpr, conditionalIfExpr)
            doc.append(conditionalIfExpr) { it.newLine() }
        } else {
            doc.surround(conditionalIfExpr) {
                it.oneSpace()
                it.highPriority()
            }
        }

        val elseKeyword = tokenAnalyzer.tokenFor(node, "else")
        if (elseKeyword != null) {
            if (elseKeyword.symbol.line != conditionalElseExpr.start.line) {
                doc.prepend(conditionalElseExpr) {
                    it.newLine()
                    it.indent()
                    it.highPriority()
                }
                doc.append(conditionalElseExpr) { it.noSpace() }
            } else {
                doc.surround(elseKeyword) { it.oneSpace() }
                doc.prepend(conditionalElseExpr) {
                    it.oneSpace()
                    it.highPriority()
                }
                doc.append(conditionalElseExpr) {
                    it.oneSpace()
                    it.lowPriority()
                }
            }
        }

        doc.format(conditionalIfExpr)
        doc.format(conditionalElseExpr)
    }
}
