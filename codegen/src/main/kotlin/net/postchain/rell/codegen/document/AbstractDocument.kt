package net.postchain.rell.codegen.document

import net.postchain.rell.codegen.StringSerializable
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.DocumentSection

abstract class AbstractDocument(override val intro: String = "",
                                override val packageString: String) : Document {

    private val sections = mutableSetOf<DocumentSection>()

    abstract fun importMap(className: ClassName): String

    private fun collectImports(): Set<String> {
        return sections.flatMap { it.imports }.toSet() + sections.flatMap { it.deps.map { c -> importMap(c) } }
    }
    override fun format(): String {
        return """
            |$intro
            |$packageString
            |
            |${collectImports().sorted().joinToString("\n")}
            |
            |${sections.joinToString("\n\n") { it.format() }}
            |
        """.trimMargin()
    }

    override fun addSection(section: DocumentSection) {
        sections.add(section)
    }
}