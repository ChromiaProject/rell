/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellManualParser.EnumDefContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.LineAnalyzer
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.formatter.util.getEnumValues

class EnumDefFormatter(
    private val lineAnalyzer: LineAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<EnumDefContext> {
    override fun format(node: EnumDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2, 2, 2) }
        // The first RULE_ID is the enum name (the only non-value RULE_ID).
        val enumName = node.RULE_ID(0)
        doc.surround(enumName) { it.oneSpace() }

        val (xNames, trailingComma) = node.getEnumValues()
        val lineSeparate = xNames.isNotEmpty() &&
            (xNames.first().symbol.line != xNames.last().symbol.line)
        whitespaceFormatter.formatTrailingComma(trailingComma, doc, lineSeparate)
        xNames.forEachIndexed { index, xName ->
            doc.prepend(xName) { it.newLine() }
            doc.append(xName) { it.noSpace() }
            doc.prepend(xName) { it.indent() }

            if (index == xNames.lastIndex) {
                doc.append(xName) { it.newLine() }
            }
        }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}
