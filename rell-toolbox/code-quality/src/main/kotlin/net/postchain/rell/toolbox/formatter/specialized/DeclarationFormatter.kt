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
    override fun format(xVarStmt: RuleX_VarStmtContext, doc: FormattableDocument) {
        whitespaceFormatter.formatSemicolon(xVarStmt, doc)
        val equalSign = tokenAnalyzer.tokenFor(xVarStmt, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.surround(xVarStmt.ruleX_VarDeclarator()) { it.oneSpace() }
        doc.format(xVarStmt.ruleX_VarDeclarator())
        doc.format(xVarStmt.ruleX_Expression())
    }
}

class TupleVarDecFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<RuleX_TupleVarDeclaratorContext> {
    override fun format(xTupleVarDec: RuleX_TupleVarDeclaratorContext, doc: FormattableDocument) {
        val tupleVarContext = xTupleVarDec.getTupleVarContext()
        braceFormatter.formatBracePairWithoutSpace(tupleVarContext, doc, BracePairTypes.PARENTHESES)
        val (varDeclarators, trailingComma) = xTupleVarDec.getVarDeclaratorWithTrailingComma()
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
    override fun format(xConstantDef: RuleX_ConstantDefContext, doc: FormattableDocument) {
        val equalSign = tokenAnalyzer.tokenFor(xConstantDef, "=")
        doc.surround(equalSign) { it.oneSpace() }
        doc.prepend(xConstantDef.ruleX_Name()) { it.oneSpace() }
        whitespaceFormatter.formatType(xConstantDef, doc)
        whitespaceFormatter.formatSemicolon(xConstantDef, doc)
        doc.format(xConstantDef.ruleX_TypeRef())
        doc.format(xConstantDef.ruleX_ExpressionRef())
    }
}
