/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.StructDefContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter


class StructDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<StructDefContext> {
    override fun format(node: StructDefContext, doc: FormattableDocument) {
        // structDef: ('struct' | 'record') RULE_ID '{' attributeClause* '}'
        doc.surround(node) { it.setNewLines(2) }
        doc.interiorIndent(node)
        doc.surround(node.RULE_ID()) { it.oneSpace() }
        for (xAttriDef in node.attributeClause()) {
            whitespaceFormatter.formatSemicolon(node, doc)
            doc.format(xAttriDef)
        }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}
