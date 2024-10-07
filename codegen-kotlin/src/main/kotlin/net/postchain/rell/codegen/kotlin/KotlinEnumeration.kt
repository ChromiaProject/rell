package net.postchain.rell.codegen.kotlin

import net.postchain.gtv.GtvFactory
import net.postchain.gtv.mapper.ToGtv
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.util.GeneratedAnnotation

class KotlinEnumeration(val className: ClassName, enum: R_EnumDefinition) : Enumeration {
    private val name = className.rellName
    override val moduleName = className.module
    private val enumValues = enum.values()
    override val docSymbol = enum.docSymbol

    override val imports = listOf(
        "import javax.annotation.processing.Generated",
        "import ${ToGtv::class.qualifiedName}",
        "import ${GtvFactory::class.qualifiedName}",
        "import net.postchain.gtv.Gtv",
    )

    override fun format() = """
        |/**
        |* Enum $name
        |${KotlinDocGenerator.formatDoc(docSymbol)}
        |*/
        |${GeneratedAnnotation.createAnnotation(name)}
        |enum class ${className.className}: ToGtv {
        |${formatEnumValues()};
        |
        |${"\t"}override fun toGtv(): Gtv = GtvFactory.gtv(ordinal.toLong())
        |}
    """.trimMargin()

    private fun formatEnumValues(): String {
        return "\t${enumValues.joinToString(",\n\t") { it.asEnum().name }}"
    }
}
