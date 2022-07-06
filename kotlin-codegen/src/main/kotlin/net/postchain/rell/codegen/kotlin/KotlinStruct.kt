package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal
import kotlin.reflect.KClass

class KotlinStruct(struct: R_StructDefinition) : Struct {
    private val name = struct.simpleName
    override val externalName = name.snakeToUpperCamelCase()
    override val moduleName = struct.defId.module.substringBefore("[")
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
        |class $externalName(
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
        return "gtv(${attributes.joinToString(",\n") { attributeToGtv(it.name.snakeToLowerCamelCase(), it.type) }})"
    }

    private fun attributeToGtv(attributeName: String, attributeType: R_Type): String {
        return when (attributeType) {
            is R_EnumType -> "gtv($attributeName.ordinal.toLong())"
            is R_StructType -> "$attributeName.toGtv()"
            is R_SetType -> "gtv($attributeName.toList().map { ${attributeToGtv("it", attributeType.elementType)} })"
            is R_DecimalType -> "gtv($attributeName.longValueExact())"
            is R_NullableType -> "$attributeName.let { if (it == null) GtvNull else gtv(it) }"
            is R_ListType -> "gtv($attributeName.map { ${attributeToGtv("it", attributeType.elementType)} })"
            else -> "gtv($attributeName)"
        }
    }

    private fun rTypeToString(type: R_Type): String {
        return when (type) {
            is R_NullableType -> "${rTypeToString(type.valueType)}?".also { imports.add("import net.postchain.gtv.GtvNull") }
            is R_BooleanType -> addImport(Boolean::class)
            is R_IntegerType -> addImport(Long::class)
            is R_DecimalType -> addImport(BigDecimal::class)
            is R_TextType -> addImport(String::class)
            is R_ByteArrayType -> addImport(ByteArray::class)
            is R_RowidType -> addImport(Long::class)
            is R_JsonType -> throw IllegalArgumentException("JSON not supported")
            is R_EntityType -> addImport(Long::class)
            is R_SetType -> "Set<${rTypeToString(type.elementType)}>"
            is R_ListType -> "Set<${rTypeToString(type.elementType)}>"
            is R_MapType -> "Map<${rTypeToString(type.keyType)}, ${rTypeToString(type.valueType)}>"
            else -> type.name.split(":").last().snakeToUpperCamelCase().replace(">", "") // Struct types
        }
    }
}
