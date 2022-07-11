package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.PostchainClient
import net.postchain.rell.codegen.deps.ImportResolver
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinQuery(queryDef: R_QueryDefinition, basePackage: String) : KotlinExtensionSection(
    queryDef.appLevelName,
    queryDef.simpleName,
    queryDef.simpleName.snakeToLowerCamelCase(),
    PostchainClient::class,
    "querySync",
    queryDef.params(),
    queryDef.type(),
    basePackage
), Query {
    val name = queryDef.simpleName
    override val moduleName = queryDef.defId.module.substringBefore("[")

    override fun format() = """
        |/**
        | * Query $appLevelName 
        | */
        |${super.format()}
    """.trimMargin()

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ", gtv(mapOf())"
        return ", gtv(mapOf(" + params.joinToString(", ") { "\"${it.name}\" to ${parameterToGtv(it)}" } + "))"
    }

    private fun parameterToGtv(param: R_Param): String {
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