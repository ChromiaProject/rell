package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinEnumeration(enum: R_EnumDefinition) : Enumeration {
    private val name = enum.simpleName
    override val externalName = name.snakeToUpperCamelCase()
    override val moduleName = enum.defId.module.substringBefore("[")
    private val enumValues = enum.values()

    override val imports = listOf<String>()

    override fun format() = """
        |enum class $externalName {
        |${formatEnumValues()}
        |}
    """.trimMargin()

    private fun formatEnumValues() : String {
        return "\t${enumValues.joinToString(",\n\t") { it.asEnum().name.snakeToUpperCamelCase() } }"
    }
}
