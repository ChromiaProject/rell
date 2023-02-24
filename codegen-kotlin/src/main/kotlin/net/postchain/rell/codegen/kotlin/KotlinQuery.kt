package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.PostchainQuery
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinQuery(queryDef: R_QueryDefinition) : ExtensionMethodSection(
        CamelCaseClassName.fromRellQuery(queryDef),
        queryDef.mountName,
        PostchainQuery::class,
        "query",
        queryDef.params(),
        queryDef.type()
), Query {

    override fun format() = """
        |/**
        | * Query ${className.rellName} 
        | */
        |${super.format()}
    """.trimMargin()

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ", gtv(mapOf())"
        return ", gtv(mapOf(" + params.joinToString(", ") { "\"${it.name}\" to ${parameterToGtv(it)}" } + "))"
    }

    override fun formatReturnType(type: R_Type?): String {
        return when (type) {
            null -> ""
            is R_NullableType -> ".let { v -> if (v is GtvNull) null else v${formatReturnType(type.valueType)} }"
            is R_BooleanType -> ".asBoolean()"
            is R_EnumType -> ".let { ${CamelCaseClassName.fromRellType(type).name}.values()[it.asInteger().toInt()] }"
            is R_TextType -> ".asString()"
            is R_IntegerType -> ".asInteger()"
            is R_ByteArrayType -> ".asByteArray()"
            is R_DecimalType -> ".let { BigDecimal(it.asString()) }"            // Note that decimals are encoded as GtvString(?)
            is R_RowidType -> ".let { RowId(it.asInteger()) }"
            is R_EntityType -> ".let { RowId(it.asInteger()) }"            // Note that entities are encoded as GtvInteger
            is R_MapType -> formatMapReturnType(type)
            is R_StructType -> ".toObject<${CamelCaseClassName.fromRellType(type).name}>()"
            is R_ListType -> ".asArray()${formatNestedReturnType(type.elementType)}"
            is R_SetType -> ".asArray()${formatNestedReturnType(type.elementType)}.toSet()"
            is R_TupleType -> formatTupleType(type)
            is R_GtvType -> ""
            else -> ""                                  // All structs (should be "unknown structs")
        }
    }

    private fun formatNestedReturnType(type: R_Type): String {
        val returnType = formatReturnType(type)
        return if (returnType.isEmpty()) "" else ".map { v -> v$returnType }"
    }

    private fun formatMapReturnType(type: R_MapType) = when (type.keyType) {
        is R_TextType -> ".asDict().mapValues { (_, v) -> v${formatReturnType(type.valueType)} }"
        else -> ".asArray().map { pair -> pair.asArray().let { it[0]${formatReturnType(type.keyType)} to it[1]${formatReturnType(type.valueType)} } }"
    }

    private fun formatTupleType(type: R_TupleType): String {
        if (!type.name.contains(":")) return ".asArray()"
        return ".toObject<${buildResultType()}>()"
    }

    // Creates a Data class if the return-type is a named tuple
    override fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_NullableType) return returnStructure(returnType.valueType)
        if (returnType is R_CollectionType) return returnStructure(returnType.elementType)
        if (returnType !is R_TupleType || !returnType.name.contains(":")) return "" // Non-tuples and unnamed tuples
        val resultObject = DataClassSection(
                CamelCaseClassName("", buildResultType(), className.module),
                returnType.fields.associateBy({ it.name!!.str }, { it.type })
        )
        return "\n${resultObject.format()}"
    }

    private fun buildResultType() = mountName.toString().replace(".", "_").snakeToUpperCamelCase() + "Result"
}
