package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.Entity
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import kotlin.reflect.KClass

class KotlinEntity(val entity: R_EntityDefinition) : Entity {
    val name = entity.simpleName
    val attributes = entity.attributes.values

    override val imports = mutableListOf("import net.postchain.gtv.mapper.Name")

    private fun addImport(import: KClass<*>): String {
        imports.add("import ${import.qualifiedName}")
        return import.simpleName!!
    }

    override fun format() = """
        |class ${name.snakeToUpperCamelCase()}(
        |${formatAttributes()}
        |)
    """.trimMargin()

    private fun formatAttributes(): String {
        return "\t${attributes.joinToString("\n\t") { formatAttribute(it) }}"
    }

    private fun formatAttribute(attribute: R_Attribute): String {
        return attribute.run {
            "@Name(\"$name\") val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"
        }
    }

    private fun rTypeToString(type: R_Type): String {
        return when (type) {
            is R_TextType -> addImport(String::class)
            is R_IntegerType -> addImport(Integer::class)
            is R_BooleanType -> addImport(Boolean::class)
            else -> type.name.split(":").last().snakeToUpperCamelCase() // Entity types
        }
    }
}
