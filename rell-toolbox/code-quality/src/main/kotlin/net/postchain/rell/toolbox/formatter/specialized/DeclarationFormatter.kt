/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.ArgumentFormatter
import net.postchain.rell.toolbox.formatter.util.BraceFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getTupleVarContext
import net.postchain.rell.toolbox.formatter.util.getVarDeclaratorWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.*


class VarStmtFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_VarStmtContext> {
    override fun format(node: RuleX_VarStmtContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(node, doc)
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.surround(node.ruleX_VarDeclarator()) { it.oneSpace() }
        doc.format(node.ruleX_VarDeclarator())
        doc.format(node.ruleX_Expression())
    }
}

class TupleVarDecFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<RuleX_TupleVarDeclaratorContext> {
    override fun format(node: RuleX_TupleVarDeclaratorContext, doc: FormattableDocument) {
        val tupleVarContext = node.getTupleVarContext()
        braceFormatter.formatBracePairWithoutSpace(tupleVarContext, doc, BracePairTypes.PARENTHESES)
        val (varDeclarators, trailingComma) = node.getVarDeclaratorWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(varDeclarators)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(varDeclarators, doc)
        varDeclarators?.forEach { xVarDec -> doc.format(xVarDec) }
    }
}

class ConstantDefFormatter(
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_ConstantDefContext> {
    override fun format(node: RuleX_ConstantDefContext, doc: FormattableDocument) {
        val equalSign = tokenAnalyzer.tokenFor(node, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(node.ruleX_Name()) { it.oneSpace() }
        whitespaceFormatter.formatType(node, doc)
        whitespaceFormatter.formatSemicolon(node, doc)
        doc.format(node.ruleX_TypeRef())
        doc.format(node.ruleX_ExpressionRef())
    }
}
