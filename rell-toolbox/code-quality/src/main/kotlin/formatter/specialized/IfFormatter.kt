/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.IfExprContext
import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.IfStmtAltContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer

class IfStmtFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val expressionFormatter: ExpressionFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<IfStmtAltContext> {
    override fun format(node: IfStmtAltContext, doc: FormattableDocument) {
        // ifStmtAlt: 'if' '(' expression ')' statement ('else' statement)?
        val ifTok = tokenAnalyzer.directTokenFor(node, "if") ?: tokenAnalyzer.tokenFor(node, "if")
        if (ifTok != null) doc.append(ifTok) { it.oneSpace() }

        val xExpression = node.expression()
        doc.surround(xExpression) { it.noSpace() }
        if (lineAnalyzer.formatAsMultiLine(
                expressionFormatter.expressionOperands(xExpression)
            ) && expressionFormatter.hasBinaryOperands(xExpression)
        ) {
            expressionFormatter.formatMultiLineStmts(xExpression, doc)
        } else {
            doc.format(xExpression)
        }

        val statements = node.statement()
        val thenStmt = statements.firstOrNull() ?: return
        val elseStmt = statements.getOrNull(1)

        if (ifTok != null && ifTok.symbol.line != thenStmt.start.line) {
            val openingCurly = tokenAnalyzer.tokenFor(thenStmt, "{")
            doc.prepend(openingCurly) {
                it.oneSpace()
                it.setNewLines(0)
                it.highPriority()
            }
            doc.prepend(thenStmt) {
                it.newLine()
            }
            if (openingCurly == null) {
                doc.prepend(thenStmt) {
                    it.indent()
                }
                doc.interiorIndentRangeIncludeLast(thenStmt, thenStmt)
            }
        } else {
            doc.prepend(thenStmt) { it.oneSpace() }
        }

        val elseTok = tokenAnalyzer.directTokenFor(node, "else") ?: tokenAnalyzer.tokenFor(node, "else")
        if (elseTok != null && elseStmt != null) {
            if (elseTok.symbol.line != elseStmt.start.line) {
                doc.prepend(elseTok) { it.newLine() }
                doc.prepend(elseStmt) {
                    it.newLine()
                    it.indent()
                }
                doc.interiorIndentRangeIncludeLast(elseStmt, elseStmt)
                doc.append(elseStmt) { it.noSpace() }
            } else {
                doc.prepend(elseTok) { it.oneSpace() }
                doc.prepend(elseStmt) {
                    it.oneSpace()
                    it.highPriority()
                }
            }
        }
        doc.format(thenStmt)
        elseStmt?.let { doc.format(it) }
    }
}

class IfExprFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<IfExprContext> {
    override fun format(node: IfExprContext, doc: FormattableDocument) {
        // ifExpr: 'if' '(' expression ')' expression 'else' expression
        val expressions = node.expression()
        val checkExpr = expressions[0]
        val conditionalIfExpr = expressions[1]
        val conditionalElseExpr = expressions[2]

        val ifTok = tokenAnalyzer.directTokenFor(node, "if") ?: tokenAnalyzer.tokenFor(node, "if")
        if (ifTok != null) doc.surround(ifTok) { it.oneSpace() }
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
