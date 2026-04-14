/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*
import net.postchain.rell.toolbox.parser.RellParser.RuleX_QueryDefContext

class QueryDefFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<RuleX_QueryDefContext> {
    override fun format(node: RuleX_QueryDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        doc.prepend(node.ruleX_Name()) { it.oneSpace() }
        doc.append(node.ruleX_Name()) { it.noSpace() }
        braceFormatter.formatBracePairWithoutSpace(node, doc, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatType(node, doc)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = node.ruleX_FormalParameters().getFormalParameterWithTrailingComma()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(formalParameters, doc, formatAsMultiLine = lineSeparate)
        argumentFormatter.formatParametersType(formalParameters, doc)
        doc.format(node.ruleX_QueryBody())
    }
}