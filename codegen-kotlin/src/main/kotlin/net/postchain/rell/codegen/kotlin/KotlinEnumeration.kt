package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinEnumeration(enum: R_EnumDefinition) : Enumeration {
    private val name = enum.appLevelName
    override val externalName = enum.simpleName.snakeToUpperCamelCase()
    override val moduleName = enum.defId.module.substringBefore("[")
    private val enumValues = enum.values()

    override val imports = listOf(
        "import javax.annotation.processing.Generated"
    )

    override fun format() = """
        |/*
        |* Enum $name
        |*/
        |${GeneratedAnnotation.createAnnotation(name)}
        |enum class $externalName {
        |${formatEnumValues()}
        |}
    """.trimMargin()

    private fun formatEnumValues() : String {
        return "\t${enumValues.joinToString(",\n\t") { it.asEnum().name } }"
    }
}
