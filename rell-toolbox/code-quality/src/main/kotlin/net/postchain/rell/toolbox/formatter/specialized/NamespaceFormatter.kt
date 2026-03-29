/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.parser.RellParser.*

class NamespaceDefFormatter : NodeFormatter<RuleX_NamespaceDefContext> {
    override fun format(node: RuleX_NamespaceDefContext, doc: FormattableDocument) {
        doc.surround(node) { it.setNewLines(2) }
        doc.append(node.ruleX_tkNAMESPACE()) { it.oneSpace() }
        doc.append(node.ruleX_QualifiedName()) { it.oneSpace() }
        val openingCurly = node.ruleX_tkLCURL()
        if (openingCurly != null) {
            doc.append(openingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
        doc.interiorIndent(node)
        for (xAnnotDef in node.ruleX_AnnotatedDef()) {
            doc.format(xAnnotDef)
        }
        val closingCurly = node.ruleX_tkRCURL()
        if (closingCurly != null) {
            doc.prepend(closingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }

        node.ruleX_AnnotatedDef().forEach {
            doc.prepend(it) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
    }
}
