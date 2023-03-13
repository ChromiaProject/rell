package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.R_EnumDefinition

class TypescriptEnumeration(private val className: ClassName, enum: R_EnumDefinition) : Enumeration {
    override val moduleName = className.module
    private val enumValues = enum.values()

    override val imports = listOf("")

    override fun format() = """
        |export enum ${className.className} {
        |${formatEnumValues()}
        |}
    """.trimMargin()

    private fun formatEnumValues() : String {
        return "\t${enumValues.joinToString(",\n\t") { it.asEnum().name.snakeToUpperCamelCase() }}" }
}
