/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.base.compiler.parser.antlr.RellParser.NamespaceDefContext
import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.formatter.util.TokenAnalyzer

class NamespaceDefFormatter(
    private val tokenAnalyzer: TokenAnalyzer,
) : NodeFormatter<NamespaceDefContext> {
    override fun format(node: NamespaceDefContext, doc: FormattableDocument) {
        // namespaceDef: 'namespace' qualifiedName? '{' annotatedDef* '}'
        doc.surround(node) { it.setNewLines(2) }
        val nsTok = tokenAnalyzer.tokenFor(node, "namespace")
        if (nsTok != null) doc.append(nsTok) { it.oneSpace() }
        node.qualifiedName()?.let { doc.append(it) { c -> c.oneSpace() } }

        val openingCurly = tokenAnalyzer.tokenFor(node, "{")
        if (openingCurly != null) {
            doc.append(openingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
        doc.interiorIndent(node)
        for (xAnnotDef in node.annotatedDef()) {
            doc.format(xAnnotDef)
        }
        val closingCurly = tokenAnalyzer.tokenFor(node, "}")
        if (closingCurly != null) {
            doc.prepend(closingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }

        node.annotatedDef().forEach {
            doc.prepend(it) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
    }
}
