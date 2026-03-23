/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.parser.RellParser.RuleX_StructDefContext


class StructDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_StructDefContext> {
    override fun format(xObjectDef: RuleX_StructDefContext, doc: FormattableDocument) {
        doc.surround(xObjectDef) { it.setNewLines(2) }
        doc.interiorIndent(xObjectDef)
        doc.surround(xObjectDef.ruleX_Name()) { it.oneSpace() }
        for (xAttriDef in xObjectDef.ruleX_AttributeClause()) {
            whitespaceFormatter.formatSemicolon(xObjectDef, doc)
            doc.format(xAttriDef)
        }
        val closingCurly = tokenAnalyzer.tokenFor(xObjectDef, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}
