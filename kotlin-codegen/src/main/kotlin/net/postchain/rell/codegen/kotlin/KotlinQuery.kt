package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.Query
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal

class KotlinQuery(val queryDef: R_QueryDefinition) : Query {
    val name = queryDef.simpleName
    val params = queryDef.params()
    val returnType = queryDef.type()

    override val imports = mutableListOf(
            "import net.postchain.client.core.PostchainClient",
            "import net.postchain.gtv.GtvFactory.gtv",
        )

    override fun format() = """
        |fun PostchainClient.${name.snakeToLowerCamelCase()}(${formatParameters()}) = 
        |   querySync("$name", gtv(mapOf(${formatQuery()})))${formatReturnStatement()}
    """.trimMargin()

    private fun formatParameters() : String {
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
            else -> "gtv(${param.name})"
        }
    }


    private fun formatReturnStatement() : String {
        if (returnType is R_ListType) {
            imports.add("import net.postchain.gtv.mapper.toList")
            return ".toList<${rTypeToString(returnType.elementType)}>()"
        }
        return formatReturnType(returnType)
    }

    private fun formatReturnType(type: R_Type): String {
        return when (type) {
            is R_BooleanType -> ".asBoolean()"
            is R_TextType -> ".asString()"
            is R_IntegerType -> ".asInteger()"
            is R_ByteArrayType -> ".asByteArray()"
            is R_EntityType -> ".asInteger()"            // Note that entities are encoded as GtvInteger
            is R_DecimalType -> ".asString()"            // Note that decimals are encoded as GtvString(?)
            is R_RowidType -> ".asInteger()"             // Same as EntityType
            is R_MapType -> formatMapReturnType(type)
            else -> ""                                  // All structs (should be "unknown structs"
        }
    }

    private fun formatMapReturnType(type: R_MapType) = if (type.keyType !is R_TextType) "" else
        ".asDict().mapValues { (k, v) -> v${formatReturnType(type.valueType)} }"
}