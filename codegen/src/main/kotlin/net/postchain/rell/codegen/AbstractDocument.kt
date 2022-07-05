package net.postchain.rell.codegen

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