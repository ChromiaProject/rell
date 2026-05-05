/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.ObjectDefContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer
import net.postchain.rell.toolbox.formatter.util.WhitespaceFormatter

class ObjectDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
    private val whitespaceFormatter: WhitespaceFormatter,
) : NodeFormatter<ObjectDefContext> {
    override fun format(node: ObjectDefContext, doc: FormattableDocument) {
        // objectDef: 'object' RULE_ID '{' attributeClause* '}'
        doc.surround(node) { it.setNewLines(2) }
        doc.interiorIndent(node)
        doc.surround(node.RULE_ID()) { it.oneSpace() }

        for (xAttriDef in node.attributeClause()) {
            whitespaceFormatter.formatSemicolon(node, doc)
            whitespaceFormatter.formatEqualSign(node, doc)
            doc.append(xAttriDef.baseAttributeDefinition().attrHeader()) {
                it.setNewLines(0)
                it.oneSpace()
            }
            doc.format(xAttriDef)
        }

        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        doc.prepend(closingCurly) { it.newLine() }
    }
}
