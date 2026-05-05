/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.OpDefContext
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*

class OpDefFormatter(
    private val braceFormatter: BraceFormatter,
    private val argumentFormatter: ArgumentFormatter,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val lineAnalyzer: LineAnalyzer,
) : NodeFormatter<OpDefContext> {
    override fun format(node: OpDefContext, doc: FormattableDocument) {
        // opDef: 'operation' RULE_ID formalParameters blockStmt
        doc.surround(node) { it.setNewLines(2, 2, 2) }
        doc.prepend(node.RULE_ID()) { it.oneSpace() }
        doc.append(node.RULE_ID()) { it.noSpace() }
        val params = node.formalParameters()
        braceFormatter.formatBracePairWithoutSpace(params, doc, BracePairTypes.PARENTHESES)
        whitespaceFormatter.formatType(node, doc)
        val lineSeparate = lineAnalyzer.lineSeparateArguments(node, BracePairTypes.PARENTHESES)
        val (formalParameters, trailingComma) = params.getFormalParametersItems()
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        argumentFormatter.formatArguments(formalParameters, doc, formatAsMultiLine = lineSeparate)
        argumentFormatter.formatParametersType(formalParameters, doc)
        doc.format(node.blockStmt())
    }
}
