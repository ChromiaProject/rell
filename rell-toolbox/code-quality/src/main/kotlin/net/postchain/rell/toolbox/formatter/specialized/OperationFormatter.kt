/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.getFormalParameterWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.RuleX_OpDefContext

class OpDefFormatter(
    private val braceFormatter: net.postchain.rell.toolbox.formatter.util.BraceFormatter,
    private val argumentFormatter: net.postchain.rell.toolbox.formatter.util.ArgumentFormatter,
    private val whitespaceFormatter: net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter,
    private val lineAnalyzer: net.postchain.rell.toolbox.formatter.util.LineAnalyzer,
) : NodeFormatter<RuleX_OpDefContext> {
    override fun format(xOpDef: RuleX_OpDefContext, doc: FormattableDocument) {
        doc.surround(xOpDef) { it.setNewLines(2, 2, 2) }
        doc.prepend(xOpDef.ruleX_Name()) { it.oneSpace() }
        doc.append(xOpDef.ruleX_Name()) { it.noSpace() }
        braceFormatter.formatBracePairWithoutSpace(xOpDef, doc, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatType(xOpDef, doc)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(xOpDef, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = xOpDef.ruleX_FormalParameters().getFormalParameterWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(formalParameters, doc, formatAsMultiLine = lineSeparate)
        argumentFormatter.formatParametersType(formalParameters, doc)
        doc.format(xOpDef.ruleX_BlockStmt())
    }
}
