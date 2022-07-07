package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

open class GtvConvertibleSection(
    val name: String,
    override val externalName: String,
    override val moduleName: String, private val attributes: Collection<R_Attribute>
) : DocumentSection {

    private val globalImports = listOf(
        "import net.postchain.gtv.Gtv",
        "import net.postchain.gtv.GtvArray",
        "import net.postchain.gtv.GtvFactory.gtv",
    )
    private val attributeImports = mutableSetOf<String>()

    override val imports: List<String>
        get() = globalImports + attributeImports

    private val classFields = attributes.map { formatAttribute(it) }

    private fun formatAttribute(attribute: R_Attribute): String {
        return attribute.run {
            "val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"
        }
    }
    override fun format() = """
        |data class $externalName(
        |    ${classFields.joinToString(",\n\t")}
        |) {
        |    fun toGtv(): Gtv {
        |        return ${formatGtv()}
        |    }
        |    
        |    companion object {
        |       fun fromGtv(gtv: GtvArray): $externalName {
        |            return $externalName(
        |                ${attributes.mapIndexed { i, attribute -> "gtv[$i]${KotlinQuery.formatReturnType(attribute.type)}" }.joinToString(",\n\t\t\t\t")}
        |            )
        |        }
        |    }
        |}
    """.trimMargin()

    private fun formatGtv(): String {
        return "gtv(\n\t\t\t${
            attributes.joinToString(",\n\t\t\t") {
                attributeToGtv(
                    it.name.snakeToLowerCamelCase(),
                    it.type
                )
            }
        }\n\t\t)"
    }

    private fun attributeToGtv(attributeName: String, attributeType: R_Type): String {
        return when (attributeType) {
            is R_EnumType -> "gtv(${attributeName}.toString())"
            is R_StructType -> "$attributeName.toGtv()"
            is R_SetType -> "gtv($attributeName.toList().map { ${attributeToGtv("it", attributeType.elementType)} })"
            is R_DecimalType -> "gtv($attributeName.toString())"
            is R_NullableType -> "$attributeName.let { if (it == null) GtvNull else gtv(it) }"
            is R_ListType -> "gtv($attributeName.map { ${attributeToGtv("it", attributeType.elementType)} })"
            else -> "gtv($attributeName)"
        }
    }

    private fun rTypeToString(type: R_Type): String {
        return when (type) {
            is R_NullableType -> "${rTypeToString(type.valueType)}?".also { attributeImports.add("import net.postchain.gtv.GtvNull") }
            is R_BooleanType -> "Boolean"
            is R_IntegerType -> "Long"
            is R_DecimalType -> "BigDecimal".also { attributeImports.add("import java.math.BigDecimal") }
            is R_TextType -> "String"
            is R_ByteArrayType -> "ByteArray"
            is R_RowidType -> "Long"
            is R_JsonType -> throw IllegalArgumentException("JSON not supported")
            is R_EntityType -> "Long"
            is R_SetType -> "Set<${rTypeToString(type.elementType)}>"
            is R_ListType -> "Set<${rTypeToString(type.elementType)}>"
            is R_MapType -> "Map<${rTypeToString(type.keyType)}, ${rTypeToString(type.valueType)}>"
            else -> type.name.split(":").last().snakeToUpperCamelCase().replace(">", "") // Struct types
        }
    }
}