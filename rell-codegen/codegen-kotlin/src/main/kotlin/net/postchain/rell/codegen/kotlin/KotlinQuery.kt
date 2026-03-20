package net.postchain.rell.codegen.kotlin

import net.postchain.client.core.PostchainQuery
import net.postchain.rell.base.lib.type.R_BigIntegerType
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_ByteArrayType
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_DecimalType
import net.postchain.rell.base.lib.type.R_GtvType
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.R_RowidType
import net.postchain.rell.base.lib.type.R_SetType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.section.Query
import net.postchain.rell.codegen.util.hasUnnamedFields
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import java.util.Locale

class KotlinQuery(queryDef: R_QueryDefinition) : ExtensionMethodSection(
    "Query",
    CamelCaseClassName.fromRellQuery(queryDef),
    queryDef.mountName,
    PostchainQuery::class,
    "query",
    queryDef.params(),
    queryDef.type(),
    queryDef.docSymbol,
), Query {

    override fun formatGtvParameters(): String {
        if (params.isEmpty()) return ", gtv(mapOf())"
        return ", gtv(mapOf(" + params.joinToString(", ") { "\"${it.name}\" to ${parameterToGtv(it)}" } + "))"
    }

    override fun formatReturnType(type: R_Type?, depth: Int): String {
        return when (type) {
            null -> ""
            is R_NullableType -> ".let { v$depth -> if (v$depth is GtvNull) null else v$depth${formatReturnType(type.valueType, depth + 1)} }"
            is R_BooleanType -> ".asBoolean()"
            is R_EnumType -> ".let { ${CamelCaseClassName.fromRellType(type).className}.valueOf(it.asString()) }"
            is R_TextType -> ".asString()"
            is R_IntegerType -> ".asInteger()"
            is R_BigIntegerType -> ".asBigInteger()"
            is R_ByteArrayType -> ".asByteArray()"
            is R_DecimalType -> ".let { BigDecimal(it.asString()) }"            // Note that decimals are encoded as GtvString(?)
            is R_RowidType -> ".let { RowId(it.asInteger()) }"
            is R_EntityType -> ".let { RowId(it.asInteger()) }"            // Note that entities are encoded as GtvInteger
            is R_MapType -> formatMapReturnType(type, depth + 1)
            is R_StructType -> ".toObject<${CamelCaseClassName.fromRellType(type).className}>()"
            is R_ListType -> ".asArray()${formatNestedReturnType(type.elementType, depth + 1)}"
            is R_SetType -> ".asArray()${formatNestedReturnType(type.elementType, depth + 1)}.toSet()"
            is R_TupleType -> formatTupleType(type)
            is R_GtvType -> ""
            else -> ""                                  // All structs (should be "unknown structs")
        }
    }

    private fun formatNestedReturnType(type: R_Type, depth: Int): String {
        val returnType = formatReturnType(type, depth + 1)
        return if (returnType.isEmpty()) "" else ".map { v$depth -> v$depth$returnType }"
    }

    private fun formatMapReturnType(type: R_MapType, depth: Int) = when (type.keyType) {
        is R_TextType -> ".asDict().mapValues { (_, v$depth) -> v$depth${formatReturnType(type.valueType, depth + 1)} }"
        else -> ".asArray().map { pair -> pair.asArray().let { it[0]${formatReturnType(type.keyType, depth + 1)} to it[1]${formatReturnType(type.valueType, depth + 1)} } }"
    }

    private fun formatTupleType(type: R_TupleType): String {
        if (type.hasUnnamedFields()) return ".asArray()"
        return ".toObject<${buildResultType()}>()"
    }

    // Creates a Data class if the return-type is a named tuple
    override fun returnStructure(returnType: R_Type?): String {
        if (returnType == null) return ""
        if (returnType is R_NullableType) return returnStructure(returnType.valueType)
        if (returnType is R_CollectionType) return returnStructure(returnType.elementType)
        if (returnType !is R_TupleType || returnType.hasUnnamedFields()) return "" // Non-tuples and unnamed tuples
        val resultObject = DataClassSection(
                CamelCaseClassName("", buildResultType(), mountName.toString().replace(".", "_").uppercase(Locale.getDefault()), className.module),
                returnType.fields.associateBy({ it.name!!.str }, { it.type }),
                docSymbol
        )
        return "\n${resultObject.format()}"
    }

    private fun buildResultType() = mountName.toString().replace(".", "_").snakeToUpperCamelCase() + "Result"
}
