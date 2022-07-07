package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.section.DocumentSection

abstract class AbstractDocument(override val intro: String = "",
                                override val packageString: String) : Document {

    private val sections = mutableSetOf<DocumentSection>()

    override fun format(): String {
        return """
            |$intro
            |$packageString
            |
            |${sections.flatMap { it.imports }.toSet().sorted().joinToString("\n")}
            |
            |${sections.joinToString("\n\n") { it.format() }}
            |
        """.trimMargin()
    }

    override fun addSection(section: DocumentSection) {
        sections.add(section)
    }
}