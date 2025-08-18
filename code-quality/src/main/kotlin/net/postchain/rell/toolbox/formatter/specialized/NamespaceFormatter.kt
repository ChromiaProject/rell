package net.postchain.rell.toolbox.formatter.specialized

import net.postchain.rell.toolbox.formatter.FormattableDocument
import net.postchain.rell.toolbox.formatter.NodeFormatter
import net.postchain.rell.toolbox.parser.RellParser.*

class NamespaceDefFormatter : NodeFormatter<RuleX_NamespaceDefContext> {
    override fun format(xNamespaceDef: RuleX_NamespaceDefContext, doc: FormattableDocument) {
        doc.surround(xNamespaceDef) { it.setNewLines(2) }
        doc.append(xNamespaceDef.ruleX_tkNAMESPACE()) { it.oneSpace() }
        doc.append(xNamespaceDef.ruleX_QualifiedName()) { it.oneSpace() }
        val openingCurly = xNamespaceDef.ruleX_tkLCURL()
        if (openingCurly != null) {
            doc.append(openingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
        doc.interiorIndent(xNamespaceDef)
        for (xAnnotDef in xNamespaceDef.ruleX_AnnotatedDef()) {
            doc.format(xAnnotDef)
        }
        val closingCurly = xNamespaceDef.ruleX_tkRCURL()
        if (closingCurly != null) {
            doc.prepend(closingCurly) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }

        xNamespaceDef.ruleX_AnnotatedDef().forEach {
            doc.prepend(it) {
                it.setNewLines(1, 1, 2)
                it.highPriority()
            }
        }
    }
}
