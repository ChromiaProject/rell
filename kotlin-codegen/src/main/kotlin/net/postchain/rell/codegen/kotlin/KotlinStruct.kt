package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal
import kotlin.reflect.KClass

class KotlinStruct(struct: R_StructDefinition) : Struct {
    private val name = struct.simpleName
    private val attributes = struct.struct.attributes.values

    override val imports = mutableListOf(
        "import net.postchain.gtv.Gtv",
        "import net.postchain.gtv.GtvFactory.gtv",
        "import net.postchain.gtv.mapper.Name",
    )

    private fun addImport(import: KClass<*>): String {
        imports.add("import ${import.qualifiedName}")
        return import.simpleName!!
    }

    override fun format() = """
        |class ${name.snakeToUpperCamelCase()}(
        |${formatAttributes()}
        |) {
        |   fun toGtv(): Gtv {
        |       return ${formatGtv()}
        |   }
        |}
    """.trimMargin()

    private fun formatAttributes(): String {
        return "\t${attributes.joinToString(",\n\t") { formatAttribute(it) }}"
    }

    private fun formatAttribute(attribute: R_Attribute): String {
        return attribute.run {
            "@Name(\"$name\") val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"
        }
    }

    private fun formatGtv(): String {
        return "gtv(${attributes.joinToString(",\n") { "gtv(${it.name.snakeToLowerCamelCase()})" }})"
    }

    private fun rTypeToString(type: R_Type): String {
        return when (type) {
            is R_BooleanType -> addImport(Boolean::class)
            is R_IntegerType -> addImport(Long::class)
            is R_DecimalType -> addImport(BigDecimal::class)
            is R_TextType -> addImport(String::class)
            is R_ByteArrayType -> addImport(ByteArray::class)
            is R_RowidType -> addImport(Long::class)
            is R_JsonType -> throw IllegalArgumentException("JSON not supported")
            is R_MapType -> formatMapType(type)
            else -> type.name.split(":").last().snakeToUpperCamelCase() // Entity types
        }
    }

    private fun formatMapType(type: R_MapType): String {
        addImport(Map::class)
        return "Map<${rTypeToString(type.keyType)}, ${rTypeToString(type.valueType)}>"
    }
}
