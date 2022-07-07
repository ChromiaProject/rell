package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.ImportResolver
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal

class KotlinQuery(queryDef: R_QueryDefinition, basePackage: String) : Query {
    val appLevelName = queryDef.appLevelName
    val name = queryDef.simpleName
    override val externalName = queryDef.simpleName.snakeToLowerCamelCase()
    override val moduleName = queryDef.defId.module.substringBefore("[")
    private val params = queryDef.params()

    override val imports: List<String>

    private val returnType = queryDef.type()

    init {
        val moduleImports = ImportResolver().resolveQueryImports(queryDef)
            .filterNot { it.startsWith("$moduleName.") }
            .map { "import $basePackage.$it" }
        imports = moduleImports + listOf(
            "import net.postchain.client.core.PostchainClient",
            "import net.postchain.gtv.GtvFactory.gtv",
            "import net.postchain.gtv.GtvArray",
        )
    }

    override fun format() = """
        |/**
        | * Query $appLevelName 
        | */
        |fun PostchainClient.$externalName(${formatParameters()}) = 
        |   querySync("$name", gtv(mapOf(${formatQuery()})))${formatReturnStatement()}
    """.trimMargin()

    private fun formatParameters(): String {
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
            is R_MapType -> formatMapType(type)
            is R_EntityType -> Long::class.simpleName!!         // Note that entities are encoded as GtvInteger
            is R_ListType -> "List<${formatParameter(type.elementType)}>"
            is R_SetType -> "Set<${formatParameter(type.elementType)}>"
            else -> type.name.split(":").last().snakeToUpperCamelCase() // Struct types
        }
    }

    private fun formatMapType(type: R_MapType): String {
        return "Map<${rTypeToString(type.keyType)}, ${formatParameter(type.valueType)}>"
    }

    private fun formatQuery(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(", ") { "\"${it.name}\" to ${parameterToGtv(it)}" }
    }

    private fun parameterToGtv(param: R_Param): String {
        return when (param.type) {
            is R_StructType -> "${param.name}.toGtv()"
            is R_ListType -> "gtv(${param.name}.map { gtv(it) })"
            is R_SetType -> "gtv(${param.name}.map { gtv(it) })"
            else -> "gtv(${param.name})"
        }
    }


    private fun formatReturnStatement(): String {
        if (returnType is R_TupleType) return ""
        if (returnType is R_ListType) {
            if (returnType.elementType is R_TupleType) return ""
            return ".asArray().map{ it${formatReturnType(returnType.elementType)} }"
        }
        return formatReturnType(returnType)
    }

    companion object {
        fun formatReturnType(type: R_Type): String {
            return when (type) {
                is R_NullableType -> ".let { if (it is GtvNull) null else it${formatReturnType(type.valueType)} }"
                is R_BooleanType -> ".asBoolean()"
                is R_EnumType -> ".let { ${type.name.substringAfter(":").snakeToUpperCamelCase()}.valueOf(it.asString()) }"
                is R_TextType -> ".asString()"
                is R_IntegerType -> ".asInteger()"
                is R_ByteArrayType -> ".asByteArray()"
                is R_EntityType -> ".asInteger()"            // Note that entities are encoded as GtvInteger
                is R_DecimalType -> ".let { BigDecimal(it.asString()) }"            // Note that decimals are encoded as GtvString(?)
                is R_RowidType -> ".asInteger()"             // Same as EntityType
                is R_MapType -> formatMapReturnType(type)
                is R_StructType -> ".let { ${ImportResolver.extractStructureName(type).first.substringAfter(":").snakeToUpperCamelCase()}.fromGtv(it as GtvArray) }"
                is R_ListType -> ".asArray().map { it${formatReturnType(type.elementType)} }"
                is R_SetType -> ".asArray().map { it${formatReturnType(type.elementType)} }.toSet()"
                else -> ""                                  // All structs (should be "unknown structs"
            }
        }

        private fun formatMapReturnType(type: R_MapType) = if (type.keyType !is R_TextType) "" else
            ".asDict().mapValues { (k, v) -> v${formatReturnType(type.valueType)} }"
    }
}