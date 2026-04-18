/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellParser.*

class BlockStmtFormatter : NodeFormatter<RuleX_BlockStmtContext> {
    override fun format(node: RuleX_BlockStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(node)
        doc.prepend(node.ruleX_tkLCURL()) { it.oneSpace() }
        val statements = node.ruleX_StatementRef()

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
    override fun format(node: RuleX_ReturnStmtContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        doc.append(node.ruleX_tkRETURN()) { it.oneSpace() }
        doc.format(node.ruleX_Expression())
    }
}

class WhileStmtFormatter(
    val expressionFormatter: ExpressionFormatter,
    val lineAnalyzer: LineAnalyzer
) : NodeFormatter<RuleX_WhileStmtContext> {
    override fun format(node: RuleX_WhileStmtContext, doc: FormattableDocument) {
        doc.append(node.ruleX_tkWHILE()) { it.oneSpace() }
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
        doc.prepend(node.ruleX_StatementRef()) { it.oneSpace() }
        doc.format(node.ruleX_StatementRef())
    }
}

class ForStmtFormatter : NodeFormatter<RuleX_ForStmtContext> {
    override fun format(node: RuleX_ForStmtContext, doc: FormattableDocument) {
        doc.append(node.ruleX_tkFOR()) { it.oneSpace() }
        doc.prepend(node.ruleX_VarDeclarator()) { it.noSpace() }
        doc.append(node.ruleX_Expression()) { it.noSpace() }
        doc.prepend(node.ruleX_StatementRef()) { it.oneSpace() }
        doc.format(node.ruleX_StatementRef())
    }
}

class CreateExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_CreateExprContext> {
    override fun format(node: RuleX_CreateExprContext, doc: FormattableDocument) {
        doc.append(node.ruleX_tkCREATE()) { it.oneSpace() }
        doc.append(node.ruleX_QualifiedName()) { it.oneSpace() }
        braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.PARENTHESES)
        val (createExprArg, trailingComma) = node.getCreateExprArgWithTrailingComma()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(createExprArg, doc, formatAsMultiLine = lineSeparate)
    }
}

class DeleteStmtFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_DeleteStmtContext> {
    override fun format(node: RuleX_DeleteStmtContext, doc: FormattableDocument) {
        doc.append(node.ruleX_tkDELETE()) { it.oneSpace() }
        doc.format(node.ruleX_UpdateTarget())
        whitespaceFormatter.formatSemicolon(node, doc)
    }
}
