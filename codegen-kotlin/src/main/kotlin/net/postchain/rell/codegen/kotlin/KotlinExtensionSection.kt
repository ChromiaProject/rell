package net.postchain.rell.codegen.kotlin

import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvNull
import net.postchain.rell.codegen.deps.ImportResolver
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.GeneratedAnnotation
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal
import kotlin.reflect.KClass

abstract class KotlinExtensionSection(
    val appLevelName: String,
    val simpleName: String,
    override val externalName: String,
    val extendedClass: KClass<*>,
    val extendenMethod: String,
    val params: List<R_Param>,
    val returnType: R_Type?,
    val basePackage: String
) : DocumentSection {

    final override val imports: List<String>

    init {
        val alwaysImports = listOf(
            "import ${extendedClass.qualifiedName}",
            "import net.postchain.gtv.GtvFactory.gtv",
            "import javax.annotation.processing.Generated"
        )
        val additionalImports = listOf(
            "import ${GtvArray::class.qualifiedName}",
            "import ${GtvNull::class.qualifiedName}",
        )
        val moduleImports = ImportResolver().resolveQueryOp(params, returnType)
            .map { ImportResolver.appLevelNameToModuleName(it) }
            .map { "import $basePackage.$it" }
        imports = alwaysImports + additionalImports + moduleImports
    }

    override fun format() = """
        |${GeneratedAnnotation.createAnnotation(appLevelName)}
        |fun ${extendedClass.simpleName}.${externalName}(${formatInputParameters()}) = 
        |   $extendenMethod("$simpleName"${formatGtvParameters()})${formatReturn()}
    """.trimMargin()


    private fun formatInputParameters(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(", ") { "${it.name}: ${formatParameter(it.type)}" }
    }

    private fun formatParameter(type: R_Type): String {
        return when (type) {
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
        if (returnType == null) return ""
        if (returnType is R_TupleType) return ""
        if (returnType is R_ListType) {
            if (returnType.elementType is R_TupleType) return ""
            return ".asArray().map{ it${formatReturnType(returnType.elementType)} }"
        }
        return formatReturnType(returnType)
    }

    internal fun parameterToGtv(param: R_Param): String {
        return when (param.type) {
            is R_StructType -> "${param.name}.toGtv()"
            is R_ListType -> "gtv(${param.name}.map { gtv(it) })"
            is R_SetType -> "gtv(${param.name}.map { gtv(it) })"
            else -> "gtv(${param.name})"
        }
    }

    companion object {

        fun formatReturnType(type: R_Type): String {
            return when (type) {
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
                is R_StructType -> ".let { ${
                    ImportResolver.extractStructureName(type).first.substringAfter(":").snakeToUpperCamelCase()
                }.fromGtv(it as GtvArray) }"
                is R_ListType -> ".asArray().map { it${formatReturnType(type.elementType)} }"
                is R_SetType -> ".asArray().map { it${formatReturnType(type.elementType)} }.toSet()"
                else -> ""                                  // All structs (should be "unknown structs"
            }
        }

        private fun formatMapReturnType(type: R_MapType) = if (type.keyType !is R_TextType) "" else
            ".asDict().mapValues { (k, v) -> v${formatReturnType(type.valueType)} }"
    }
}
