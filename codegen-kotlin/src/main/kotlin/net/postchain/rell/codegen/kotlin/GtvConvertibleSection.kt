package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.kotlin.KotlinExtensionSection.Companion.formatReturnType
import net.postchain.rell.codegen.kotlin.util.attributeToGtv
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
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
        "import net.postchain.gtv.mapper.Name",
        "import javax.annotation.processing.Generated"
    )
    private val attributeImports = mutableSetOf<String>()

    override val imports: List<String>
        get() = globalImports + attributeImports

    private val classFields = attributes.map { formatAttribute(it) }

    private fun formatAttribute(attribute: R_Attribute): String {
        return attribute.run {
            "@Name(\"$name\") val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"
        }
    }
    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(name)}
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
        |                ${attributes.mapIndexed { i, attribute -> "gtv[$i]${formatReturnType(attribute.type)}" }.joinToString(",\n\t\t\t\t")}
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