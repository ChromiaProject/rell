package net.postchain.rell.toolbox.lsp.hover

import net.postchain.rell.base.utils.doc.DocCodeTokenVisitor

/**
 * Formats a signature directly from rell repo
 */
class DocDeclarationVisitor(private val sb: StringBuilder) : DocCodeTokenVisitor {
    override fun keyword(s: String) {
        sb.append(s)
    }

    override fun link(s: String) {
        sb.append(s)
    }

    override fun raw(s: String) {
        sb.append(s)
    }

    override fun tab() {
        sb.append("\t")
    }
}
