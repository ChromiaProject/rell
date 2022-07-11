package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.attributeToGtv
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

open class GtvConvertibleSection(
    val className: ClassName,
    private val attributes: Map<String, R_Type>
) : DocumentSection {
    override val moduleName: String
        get() = className.module

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

    override val deps = DependencyFinder.findDependencies(attributes.values, false)

    private val classFields = attributes.map { formatAttribute(it.key, it.value) }

    private fun formatAttribute(name: String, type: R_Type): String {
        return "@Name(\"$name\") val ${name.snakeToLowerCamelCase()}: ${rTypeToString(type)}"
    }
    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |data class ${className.name}(
        |    ${classFields.joinToString(",\n\t")}
        |) {
        |    fun toGtv(): Gtv {
        |        return ${formatGtv()}
        |    }
        |}
    """.trimMargin()

    private fun formatGtv(): String {
        return "gtv(\n\t\t\t${
            attributes.map { attributeToGtv(it.key.snakeToLowerCamelCase(), it.value)  }.joinToString(",\n\t\t\t")
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
            is R_StructType -> CamelCaseClassName.fromString(type.name).name
            is R_EnumType -> CamelCaseClassName.fromString(type.name).name
            is R_GtvType -> "Gtv"
            else -> throw IllegalArgumentException("Type ${type.name} not supported as field on struct/entity ${className.rellName}")
        }
    }
}