package net.postchain.rell.toolbox.lsp.hover

import net.postchain.rell.base.utils.doc.DocCodeTokenVisitor

/**
 * Naively removes some keywords and reformats optional parameters using bracket notation.
 */
class SanitizedDocDeclarationVisitor(val sb: StringBuilder): DocCodeTokenVisitor {
    var isOptional = false
    var hasStarted = false
    override fun keyword(s: String) {
        if (s == "zero_one") {
            sb.append("[")
            isOptional = true
            return
        }
        if (s in listOf("pure", "static")) return
        hasStarted = true
        sb.append(s)
    }

    // qualifiedName of linked object
    override fun link(s: String) {
        hasStarted = true
        sb.append(s)
    }

    override fun raw(s: String) {
        if (!hasStarted) return
        if (s == "\n" && isOptional) {
            sb.append(" ]")
            isOptional = false
        }
        if (s != "\n") sb.append(s)
    }

    override fun tab() {
    }
}
