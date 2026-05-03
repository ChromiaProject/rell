/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.*
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*


class VarStmtFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<VarStmtAltContext> {
    override fun format(node: VarStmtAltContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        if (equalSign != null) doc.surround(equalSign) { it.oneSpace() }
        doc.surround(node.varDeclarator()) { it.oneSpace() }
        doc.format(node.varDeclarator())
        node.expression()?.let { doc.format(it) }
    }
}

class TupleVarDecFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<TupleVarDeclaratorContext> {
    override fun format(node: TupleVarDeclaratorContext, doc: FormattableDocument) {
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
        val (varDeclarators, trailingComma) = node.getVarDeclaratorItems()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(varDeclarators)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(varDeclarators, doc)
        varDeclarators.forEach { xVarDec -> doc.format(xVarDec) }
    }
}

class ConstantDefFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<ConstantDefContext> {
    override fun format(node: ConstantDefContext, doc: FormattableDocument) {
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        if (equalSign != null) doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(node.RULE_ID()) { it.oneSpace() }
        whitespaceFormatter.formatType(node, doc)
        whitespaceFormatter.formatSemicolon(node, doc)
        node.type()?.let { doc.format(it) }
        doc.format(node.expression())
    }
}
