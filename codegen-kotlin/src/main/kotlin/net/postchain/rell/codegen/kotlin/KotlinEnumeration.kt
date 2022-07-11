package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinEnumeration(val className: ClassName, enum: R_EnumDefinition) : Enumeration {
    private val name = className.rellName
    override val moduleName = className.moduleName
    private val enumValues = enum.values()

    override val imports = listOf(
        "import javax.annotation.processing.Generated"
    )

    override fun format() = """
        |/*
        |* Enum $name
        |*/
        |${GeneratedAnnotation.createAnnotation(name)}
        |enum class ${className.className} {
        |${formatEnumValues()}
        |}
    """.trimMargin()

    private fun formatEnumValues() : String {
        return "\t${enumValues.joinToString(",\n\t") { it.asEnum().name } }"
    }
}
