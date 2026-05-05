/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.UpdateStmtAltContext
import net.postchain.rell.base.compiler.parser.antlr.RellParser.UpdateTargetAtContext
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*

class UpdateStmtFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<UpdateStmtAltContext> {
    override fun format(node: UpdateStmtAltContext, doc: FormattableDocument) {
        val updateTok = tokenAnalyzer.directTokenFor(node, "update") ?: tokenAnalyzer.tokenFor(node, "update")
        if (updateTok != null) doc.append(updateTok) { it.oneSpace() }
        doc.append(node.updateTarget()) { it.oneSpace() }
        doc.format(node.updateTarget())
        // Find the parens that wrap the "what" expression list (direct children).
        // updateStmtAlt: 'update' updateTarget '(' ... ')' ';'
        braceFormatter.formatBracePairWithSpace(node, doc, BracePairTypes.PARENTHESES)
        // The "what" items are top-level expressions of the update statement.
        // The grammar:
        //   '(' (('.'? RULE_ID ('=' | '+=' ... ))? expression)
        //       (',' (('.'? RULE_ID ('=' | '+=' ... ))? expression)* ','? ')' ';'
        // So expression children of the statement are the items.
        val items = node.expression()
        val trailingComma = node.findTrailingComma(")")
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(items, doc, formatAsMultiLine = lineSeparate)
        whitespaceFormatter.formatSemicolon(node, doc)
    }
}

class UpdateTargetAtFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<UpdateTargetAtContext> {
    override fun format(node: UpdateTargetAtContext, doc: FormattableDocument) {
        // updateTargetAt: (qualifiedName | '(' ... ')') atExprAt atExprWhere
        doc.append(node.atExprAt()) { it.oneSpace() }

        val atExprWhere = node.atExprWhere()
        braceFormatter.formatBracePairWithSpace(atExprWhere, doc, BracePairTypes.CURLY)
        val (expressionRef, trailingComma) = atExprWhere.getWhereItems()
        val lineSeparate = lineAnalyzer.lineSeparateArguments(atExprWhere, BracePairTypes.CURLY)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(expressionRef, doc, formatAsMultiLine = lineSeparate)
        expressionRef.forEach { doc.format(it) }
    }
}
