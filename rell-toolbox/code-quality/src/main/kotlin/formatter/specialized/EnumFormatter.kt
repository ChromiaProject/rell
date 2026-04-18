/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getXNamesWithTrailingComma
import net.postchain.rell.toolbox.parser.RellParser.RuleX_EnumDefContext

class EnumDefFormatter(
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<RuleX_EnumDefContext> {
    override fun format(node: RuleX_EnumDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2, 2, 2) }
        doc.surround(node.ruleX_Name()) { it.oneSpace() }

        val (xNames, trailingComma) = node.getXNamesWithTrailingComma()
        val lineSeparate = lineAnalyzer.formatAsMultiLine(xNames)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        xNames?.forEachIndexed { index, xName ->
            doc.prepend(xName) { it.newLine() }
            doc.surround(xName) { it.noSpace() }
            doc.prepend(xName) { it.indent() }

            if (index == xNames.lastIndex) {
                doc.append(xName) { it.newLine() }
            }
        }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}
