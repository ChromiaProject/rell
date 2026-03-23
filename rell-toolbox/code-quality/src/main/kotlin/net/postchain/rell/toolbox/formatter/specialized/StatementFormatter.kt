/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ArgumentFormatter
import net.postchain.rell.toolbox.formatter.util.BraceFormatter
import net.postchain.rell.toolbox.formatter.util.ExpressionFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getCreateExprArgWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.*

class BlockStmtFormatter : NodeFormatter<RuleX_BlockStmtContext> {
    override fun format(xBlockStmt: RuleX_BlockStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(xBlockStmt)
        doc.prepend(xBlockStmt.ruleX_tkLCURL()) { it.oneSpace() }
        val statements = xBlockStmt.ruleX_StatementRef()

        statements.forEachIndexed { index, statement ->
            doc.prepend(statement) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
            if (index == statements.lastIndex) {
                doc.append(statement) { it.newLine() }
            }
            doc.format(statement)
        }
    }
}

class ReturnStmtFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_ReturnStmtContext> {
    override fun format(xReturnStmt: RuleX_ReturnStmtContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(xReturnStmt, doc)
        doc.append(xReturnStmt.ruleX_tkRETURN()) { it.oneSpace() }
        doc.format(xReturnStmt.ruleX_Expression())
    }
}

class WhileStmtFormatter(
    val expressionFormatter: ExpressionFormatter,
    val lineAnalyzer: LineAnalyzer
) : NodeFormatter<RuleX_WhileStmtContext> {
    override fun format(xWhileStmt: RuleX_WhileStmtContext, doc: FormattableDocument) {
        doc.append(xWhileStmt.ruleX_tkWHILE()) { it.oneSpace() }
        doc.surround(xWhileStmt.ruleX_Expression()) { it.noSpace() }
        val xExpression = xWhileStmt.ruleX_Expression()
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
        doc.prepend(xWhileStmt.ruleX_StatementRef()) { it.oneSpace() }
        doc.format(xWhileStmt.ruleX_StatementRef())
    }
}

class ForStmtFormatter : NodeFormatter<RuleX_ForStmtContext> {
    override fun format(xForStmt: RuleX_ForStmtContext, doc: FormattableDocument) {
        doc.append(xForStmt.ruleX_tkFOR()) { it.oneSpace() }
        doc.prepend(xForStmt.ruleX_VarDeclarator()) { it.noSpace() }
        doc.append(xForStmt.ruleX_Expression()) { it.noSpace() }
        doc.prepend(xForStmt.ruleX_StatementRef()) { it.oneSpace() }
        doc.format(xForStmt.ruleX_StatementRef())
    }
}

class CreateExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_CreateExprContext> {
    override fun format(xCreateExpr: RuleX_CreateExprContext, doc: FormattableDocument) {
        doc.append(xCreateExpr.ruleX_tkCREATE()) { it.oneSpace() }
        doc.append(xCreateExpr.ruleX_QualifiedName()) { it.oneSpace() }
        braceFormatter.formatBracePairWithSpace(xCreateExpr, doc, BracePairTypes.PARENTHESES)
        val (createExprArg, trailingComma) = xCreateExpr.getCreateExprArgWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(xCreateExpr, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(createExprArg, doc, formatAsMultiLine = lineSeparate)
    }
}

class DeleteStmtFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_DeleteStmtContext> {
    override fun format(xDeleteStmt: RuleX_DeleteStmtContext, doc: FormattableDocument) {
        doc.append(xDeleteStmt.ruleX_tkDELETE()) { it.oneSpace() }
        doc.format(xDeleteStmt.ruleX_UpdateTarget())
        whitespaceFormatter.formatSemicolon(xDeleteStmt, doc)
    }
}
