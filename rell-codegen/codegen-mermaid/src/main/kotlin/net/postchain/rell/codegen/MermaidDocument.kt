package net.postchain.rell.codegen

import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.section.DocumentSection

class MermaidDocument(private val mdx: Boolean, private val erDiagram: Boolean): Document {
    override val intro: String
        get() = ""
    override val packageString: String
        get() = ""

    val sections = mutableSetOf<DocumentSection>()
    override fun addSection(section: DocumentSection) {
        sections.add(section)
    }

    override fun format(): String {
        if (sections.isEmpty()) return ""
        return """
            |${if (mdx) "```mermaid" else ""}
            |${if (erDiagram) "erDiagram" else "classDiagram"}
            |${sections.joinToString("\n") { it.format() }}
            |${if (mdx) "```" else ""}
        """.trimMargin()
    }
}
