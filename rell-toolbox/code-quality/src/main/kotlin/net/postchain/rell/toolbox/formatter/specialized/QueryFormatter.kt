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
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getFormalParameterWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.RuleX_QueryDefContext

class QueryDefFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_QueryDefContext> {
    override fun format(xQueryDef: RuleX_QueryDefContext, doc: FormattableDocument) {
        doc.surround(xQueryDef) { it.setNewLines(2) }
        doc.prepend(xQueryDef.ruleX_Name()) { it.oneSpace() }
        doc.append(xQueryDef.ruleX_Name()) { it.noSpace() }
        braceFormatter.formatBracePairWithoutSpace(xQueryDef, doc, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatType(xQueryDef, doc)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(xQueryDef, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = xQueryDef.ruleX_FormalParameters().getFormalParameterWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(formalParameters, doc, formatAsMultiLine = lineSeparate)
        argumentFormatter.formatParametersType(formalParameters, doc)
        doc.format(xQueryDef.ruleX_QueryBody())
    }
}