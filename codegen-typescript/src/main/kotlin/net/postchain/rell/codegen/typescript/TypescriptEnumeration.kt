package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.util.capitalize
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

class TypescriptEnumeration(private val className: ClassName, enum: R_EnumDefinition) : Enumeration {
    override val moduleName = className.module
    private val enumValues = enum.values()

    override val imports = listOf("")

    override fun format() = """
        |enum ${className.name} {
        |${formatEnumValues()}
        |}
    """.trimMargin()

    private fun formatEnumValues() : String {
        return "\t${enumValues.joinToString(",\n\t") { capitalize(it.asEnum().name.snakeToLowerCamelCase()) }}"
    }
}
