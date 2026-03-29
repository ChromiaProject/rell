/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter
import net.postchain.rell.toolbox.parser.RellParser.*

class ObjectDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<RuleX_ObjectDefContext> {
    override fun format(node: RuleX_ObjectDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        doc.interiorIndent(node)
        doc.surround(node.ruleX_Name()) { it.oneSpace() }

        for (xAttriDef in node.ruleX_AttributeClause()) {
            whitespaceFormatter.formatSemicolon(node, doc)
            whitespaceFormatter.formatEqualSign(node, doc)
            doc.append(xAttriDef.ruleX_AttributeDefinition().ruleX_BaseAttributeDefinition().ruleX_AttrHeader()) {
                it.setNewLines(0)
                it.oneSpace()
            }
            doc.format(xAttriDef)
        }

        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}
