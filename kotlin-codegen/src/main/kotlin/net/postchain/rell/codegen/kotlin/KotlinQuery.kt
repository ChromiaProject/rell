package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.Query
import net.postchain.rell.codegen.kotlin.util.rTypeToString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.*

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
        |   querySync("$name", gtv(mapOf(${formatQuery()}))).${formatReturnStatement()}
    """.trimMargin()

    private fun formatParameters() : String {
        if (params.isEmpty()) return ""
        return params.joinToString(", ") { "${it.name}: ${rTypeToString(it.type)}" }
    }

    private fun formatQuery(): String {
        if (params.isEmpty()) return ""
        return params.joinToString(", ") { "\"${it.name}\" to gtv(${it.name})" }
    }

    private fun formatReturnStatement() : String {
        if (returnType is R_ListType) {
            imports.add("import net.postchain.gtv.mapper.toList")
            return "toList<${rTypeToString(returnType.elementType)}>()"
        }
        return formatReturnType(returnType)
    }

    private fun formatReturnType(type: R_Type): String {
        return when (type) {
            is R_BooleanType -> "asBoolean()"
            is R_TextType -> "asString()"
            is R_IntegerType -> "asInteger()"
            is R_ByteArrayType -> "asByteArray()"
            is R_EntityType -> "asInteger()"            // Note that entities are encoded as GtvInteger
            is R_DecimalType -> "asString()"            // Note that decimals are encoded as GtvString(?)
            is R_RowidType -> "asInteger()"             // Same as EntityType
            is R_MapType -> formatMapReturnType(type)
            else -> throw IllegalArgumentException("Not supported return type")
        }
    }

    private fun formatMapReturnType(type: R_MapType) = if (type.keyType !is R_TextType) "" else
        "asDict().mapValues { (k, v) -> v.${formatReturnType(type.valueType)} }"
}