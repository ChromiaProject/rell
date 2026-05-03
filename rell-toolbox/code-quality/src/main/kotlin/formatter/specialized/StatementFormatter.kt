/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.*
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*

class BlockStmtFormatter : NodeFormatter<BlockStmtContext> {
    override fun format(node: BlockStmtContext, doc: FormattableDocument) {
        doc.interiorIndent(node)
        // blockStmt: '{' statement* '}'
        // First child is `{`.
        node.children?.firstOrNull()?.let { c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode && c.symbol.text == "{") {
                doc.prepend(c) { it.oneSpace() }
            }
        }
        val statements = node.statement()

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
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<ReturnStmtAltContext> {
    override fun format(node: ReturnStmtAltContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        val ret = tokenAnalyzer.directTokenFor(node, "return") ?: tokenAnalyzer.tokenFor(node, "return")
        if (ret != null) doc.append(ret) { it.oneSpace() }
        node.expression()?.let { doc.format(it) }
    }
}

class WhileStmtFormatter(
    val expressionFormatter: ExpressionFormatter,
    val lineAnalyzer: LineAnalyzer,
    val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<WhileStmtAltContext> {
    override fun format(node: WhileStmtAltContext, doc: FormattableDocument) {
        val whileTok = tokenAnalyzer.directTokenFor(node, "while") ?: tokenAnalyzer.tokenFor(node, "while")
        if (whileTok != null) doc.append(whileTok) { it.oneSpace() }
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
        val stmt = node.statement()
        doc.prepend(stmt) { it.oneSpace() }
        doc.format(stmt)
    }
}

class ForStmtFormatter(
    val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<ForStmtAltContext> {
    override fun format(node: ForStmtAltContext, doc: FormattableDocument) {
        // forStmtAlt: 'for' '(' varDeclarator 'in' expression ')' statement
        val forTok = tokenAnalyzer.directTokenFor(node, "for") ?: tokenAnalyzer.tokenFor(node, "for")
        if (forTok != null) doc.append(forTok) { it.oneSpace() }
        doc.prepend(node.varDeclarator()) { it.noSpace() }
        doc.append(node.expression()) { it.noSpace() }
        doc.prepend(node.statement()) { it.oneSpace() }
        doc.format(node.statement())
    }
}

class CreateExprFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    @Suppress("unused") private val argumentFormatter: ArgumentFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
    private val expressionFormatter: ExpressionFormatter,
) : NodeFormatter<CreateExprContext> {
    override fun format(node: CreateExprContext, doc: FormattableDocument) {
        // createExpr (alt of baseExprHead):
        //   'create' qualifiedName '(' (...)? ')'
        val createTok = tokenAnalyzer.directTokenFor(node, "create") ?: tokenAnalyzer.tokenFor(node, "create")
        if (createTok != null) doc.append(createTok) { it.oneSpace() }
        doc.append(node.qualifiedName()) { it.oneSpace() }
        braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.PARENTHESES)
        // Items are expression children inside '(' ... ')'.
        val items = node.expression()
        val trailingComma = node.findTrailingComma(")")
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        expressionFormatter.formatLabeledParenList(node, items, doc, multiLine = lineSeparate)
    }
}

class DeleteStmtFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<DeleteStmtAltContext> {
    override fun format(node: DeleteStmtAltContext, doc: FormattableDocument) {
        val delTok = tokenAnalyzer.directTokenFor(node, "delete") ?: tokenAnalyzer.tokenFor(node, "delete")
        if (delTok != null) doc.append(delTok) { it.oneSpace() }
        doc.format(node.updateTarget())
        whitespaceFormatter.formatSemicolon(node, doc)
    }
}

/**
 * exprStmtAlt: baseExpr (('=' | '+=' | '-=' | '*=' | '/=' | '%=') expression)? ';'
 *
 * Formats inline assignment operators with surrounding spaces, plus semicolon.
 */
class ExprStmtAltFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<ExprStmtAltContext> {
    override fun format(node: ExprStmtAltContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        // Surround any of the assignment operators present as direct terminal children with a space.
        for (op in ASSIGN_OPS) {
            val tok = tokenAnalyzer.directTokenFor(node, op)
            if (tok != null) {
                doc.surround(tok) {
                    it.oneSpace()
                    it.highPriority()
                }
            }
        }
        doc.format(node.baseExpr())
        node.expression()?.let { doc.format(it) }
    }

    companion object {
        private val ASSIGN_OPS = listOf("=", "+=", "-=", "*=", "/=", "%=")
    }
}

/**
 * incrementStmtAlt: ('++' | '--') baseExpr ';'
 *
 * The `++`/`--` is a direct terminal child; no space between it and baseExpr.
 */
class IncrementStmtAltFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<IncrementStmtAltContext> {
    override fun format(node: IncrementStmtAltContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        node.children?.firstOrNull()?.let { c ->
            if (c is org.antlr.v4.runtime.tree.TerminalNode &&
                (c.symbol.text == "++" || c.symbol.text == "--")
            ) {
                doc.append(c) { it.noSpace() }
            }
        }
        doc.format(node.baseExpr())
    }
}
