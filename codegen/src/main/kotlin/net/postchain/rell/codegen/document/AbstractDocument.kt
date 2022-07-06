package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.section.DocumentSection

abstract class AbstractDocument(override val intro: String = "",
                                override val packageString: String) : Document {

    private val imports = mutableSetOf<String>()
    private val sections = mutableSetOf<StringSerializable>()

    override fun format(): String {
        return """
            |$intro
            |$packageString
            |
            |${imports.joinToString("\n")}
            |
            |${sections.joinToString("\n") { it.format() }}
            |
        """.trimMargin()
    }

    override fun addSection(section: DocumentSection) {
        imports.addAll(section.imports)
        sections.add(section)
    }
}