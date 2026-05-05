/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.QueryDefContext
import net.postchain.rell.toolbox.formatter.BracePairTypes
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.*

class QueryDefFormatter(
    private val braceFormatter: BraceFormatter,
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val argumentFormatter: ArgumentFormatter,
) : NodeFormatter<QueryDefContext> {
    override fun format(node: QueryDefContext, doc: FormattableDocument) {
        // queryDef: 'query' RULE_ID formalParameters (':' type)? queryBody
        doc.surround(node) { it.setNewLines(2) }
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
        doc.format(node.queryBody())
    }
}
