package net.postchain.rell.codegen.kotlin

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvNull
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.kotlin.util.attributeToGtv
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal
import kotlin.reflect.KClass

abstract class ExtensionMethodSection(
    val className: ClassName,
    val simpleName: String,
    val extendedClass: KClass<*>,
    val extendenMethod: String,
    val params: List<R_Param>,
    val returnType: R_Type?,
    val basePackage: String
) : DocumentSection {
    override val moduleName: String
        get() = className.module

    final override val imports: List<String>
    final override val deps: Set<ClassName>

    init {
        val alwaysImports = listOf(
            "import ${extendedClass.qualifiedName}",
            "import net.postchain.gtv.GtvFactory.gtv",
            "import net.postchain.gtv.mapper.toObject",
            "import javax.annotation.processing.Generated"
        )
        val additionalImports = listOf(
            "import ${GtvArray::class.qualifiedName}",
            "import ${GtvNull::class.qualifiedName}",
        )
        imports = alwaysImports + additionalImports
        val returnDeps = DependencyFinder.findDependencies(returnType)
        val paramDeps = DependencyFinder.findDependencies(params.map { it.type })
        deps = paramDeps + returnDeps
    }

    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(className.rellName)}
        |fun ${extendedClass.simpleName}.${className.name}(${formatInputParameters()}) = 
        |   $extendenMethod("$simpleName"${formatGtvParameters()})${formatReturn()}
        |${returnStructure(returnType)}
    """.trimMargin()


    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(",\n\t") { "${it.name.snakeToLowerCamelCase()}: ${formatParameter(it.type)}" }
    }

    private fun formatParameter(type: R_Type): String {
        return when (type) {
            is R_NullableType -> "${formatParameter(type.valueType)}?"
            is R_BooleanType -> Boolean::class.simpleName!!
            is R_IntegerType -> Long::class.simpleName!!
            is R_DecimalType -> BigDecimal::class.simpleName!!
            is R_TextType -> String::class.simpleName!!
            is R_ByteArrayType -> ByteArray::class.simpleName!!
            is R_RowidType -> Long::class.simpleName!!
            is R_JsonType -> throw IllegalArgumentException("JSON not supported")
            is R_EntityType -> Long::class.simpleName!!         // Note that entities are encoded as GtvInteger
            is R_ListType -> "List<${formatParameter(type.elementType)}>"
            is R_SetType -> "Set<${formatParameter(type.elementType)}>"
            is R_MapType -> "Map<${rTypeToString(type.keyType)}, ${formatParameter(type.valueType)}>"
            else -> type.name.split(":").last().snakeToUpperCamelCase() // Struct types
        }
    }

    abstract fun formatGtvParameters(): String

    private fun formatReturn(): String {

        return formatReturnType(returnType)
    }

    internal fun parameterToGtv(param: R_Param): String {
        return attributeToGtv(param.name.snakeToLowerCamelCase(), param.type)
    }

    private fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_CollectionType) return returnStructure(returnType.elementType)
        if (returnType !is R_TupleType || !returnType.name.contains(":")) return "" // Non-tuples and unnamed tuples
        val resultObject = DataClassSection(
            CamelCaseClassName("", simpleName.snakeToUpperCamelCase() + "Result", className.module),
            returnType.fields.associate { it.name!!.str to it.type })
        return "\n${resultObject.format()}"
    }


    fun formatReturnType(type: R_Type?): String {
        return when (type) {
            null -> ""
            is R_NullableType -> ".let { if (it is GtvNull) null else it${formatReturnType(type.valueType)} }"
            is R_BooleanType -> ".asBoolean()"
            is R_EnumType -> ".let { ${
                type.name.substringAfter(":").snakeToUpperCamelCase()
            }.valueOf(it.asString()) }"
            is R_TextType -> ".asString()"
            is R_IntegerType -> ".asInteger()"
            is R_ByteArrayType -> ".asByteArray()"
            is R_EntityType -> ".asInteger()"            // Note that entities are encoded as GtvInteger
            is R_DecimalType -> ".let { BigDecimal(it.asString()) }"            // Note that decimals are encoded as GtvString(?)
            is R_RowidType -> ".asInteger()"             // Same as EntityType
            is R_MapType -> formatMapReturnType(type)
            is R_StructType -> ".toObject<${CamelCaseClassName.fromString(type.name).name}>()"
            is R_ListType -> ".asArray().map { it${formatReturnType(type.elementType)} }"
            is R_SetType -> ".asArray().map { it${formatReturnType(type.elementType)} }.toSet()"
            is R_TupleType -> formatTupleType(type)
            else -> ""                                  // All structs (should be "unknown structs"
        }
    }

    private fun formatMapReturnType(type: R_MapType) = if (type.keyType !is R_TextType) "" else
        ".asDict().mapValues { (k, v) -> v${formatReturnType(type.valueType)} }"

    private fun formatTupleType(type: R_TupleType): String {
        if (!type.name.contains(":")) return ".asArray()"
        return ".toObject<${simpleName.snakeToUpperCamelCase()}Result>()"
    }
}
