/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.util

import net.postchain.rell.codegen.CodeGenerator
import java.util.*
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.lib.type.R_BigIntegerType
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_ByteArrayType
import net.postchain.rell.base.lib.type.R_DecimalType
import net.postchain.rell.base.lib.type.R_GtvType
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_JsonType
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.R_RowidType
import net.postchain.rell.base.lib.type.R_SetType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_EntityType
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.CamelCaseClassName

fun capitalize(name: String) =
    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
val snakeRegex = "_[a-zA-Z0-9]".toRegex()

// String extensions
fun String.camelToSnakeCase(): String {
    return camelRegex.replace(this) {
        "_${it.value}"
    }.lowercase(Locale.getDefault())
}

fun String.snakeToLowerCamelCase(): String {
    return snakeRegex.replace(this) {
        it.value.replace("_","")
            .uppercase(Locale.getDefault())
    }
}

fun String.snakeToUpperCamelCase(): String {
    return capitalize(this.snakeToLowerCamelCase())
}

// See type conversions: https://docs.chromia.com/intro/architecture/generic-transaction-protocol#type-conversions
fun rTypeToJsTypeString(type: R_Type, allowSet: Boolean = false, queryReturn: Boolean = false): String {
    return when (type) {
        is R_NullableType -> if (type.valueType is R_GtvType) JsTypeRawGtvString else "${rTypeToJsTypeString(type.valueType)} | null"
        is R_BooleanType -> "number"
        is R_IntegerType -> "number"
        is R_BigIntegerType -> "bigint"
        is R_DecimalType -> "string"
        is R_TextType -> "string"
        is R_ByteArrayType -> "Buffer"
        is R_RowidType -> "number"
        is R_EntityType -> "number"
        is R_JsonType -> "string"
        is R_SetType -> if (allowSet) "Set<${rTypeToJsTypeString(type.elementType, allowSet)}>" else "${rTypeToJsTypeString(type.elementType)}[]"
        is R_ListType -> "${rTypeToJsTypeString(type.elementType)}[]"
        is R_MapType -> formatMapType(type)
        is R_StructType -> CamelCaseClassName.fromRellType(type).className
        is R_EnumType -> if (queryReturn) rTypeToJsTypeString(R_TextType) else CamelCaseClassName.fromRellType(type).className
        is R_TupleType -> formatTupleType(type)
        is R_GtvType -> JsTypeRawGtvString

        else -> JsTypeRawGtvString
    }
}


fun rTypeToPythonType(type: R_Type): String {
    return when (type) {
        is R_NullableType -> "Optional[${rTypeToPythonType(type.valueType)}]"
        is R_BooleanType -> "bool"
        is R_IntegerType -> "int"
        is R_BigIntegerType -> "BigInt"
        is R_DecimalType -> "float"
        is R_TextType -> "str"
        is R_ByteArrayType -> "bytes"
        is R_RowidType -> "int"
        is R_EntityType -> "int"
        is R_JsonType -> "str"
        is R_SetType -> "Set[${rTypeToPythonType(type.elementType)}]"
        is R_ListType -> "List[${rTypeToPythonType(type.elementType)}]"
        is R_MapType -> "Dict[${rTypeToPythonType(type.keyType)}, ${rTypeToPythonType(type.valueType)}]"
        is R_StructType -> CamelCaseClassName.fromRellType(type).className
        is R_EnumType -> CamelCaseClassName.fromRellType(type).className
        is R_TupleType -> "tuple[${type.fields.joinToString(", ") { rTypeToPythonType(it.type) } }]"
        is R_GtvType -> "RawGtv"
        else -> "Any"
    }
}

const val JsTypeRawGtvString = "RawGtv"

private fun formatMapType(type: R_MapType): String {
    return if (type.keyType is R_TextType) {
        "Record<string, ${rTypeToJsTypeString(type.valueType)}>"
    } else {
        "Array<[${rTypeToJsTypeString(type.keyType)}, ${rTypeToJsTypeString(type.valueType)}]>"
    }
}

private fun formatTupleType(type: R_TupleType): String {
    if (!type.hasUnnamedFields()) return formatNamedTuple(type)
    val fieldTypes = mutableListOf<String>()
    type.fields.forEach { fieldTypes.add(rTypeToJsTypeString(it.type)) }
    return "$fieldTypes"
}

fun formatNamedTuple(type: R_TupleType): String {
    val fieldTypes = mutableMapOf<String, String>()
    type.fields.forEach { fieldTypes[it.name!!.str] = rTypeToJsTypeString(it.type) }
    return fieldTypes.toString().replace("=", ":")
}

fun R_TupleType.hasUnnamedFields(): Boolean =
    fields.any { it.name == null }

object GeneratedAnnotation {
    fun createAnnotation(comment: String) = "@Generated(\"${CodeGenerator::class.qualifiedName}\", comments = \"$comment\")"
}

class CachedRellCliEnv(
    private val rellCliEnv: RellCliEnv,
    private val cacheOutput: Boolean = false,
    private val cacheError: Boolean = false
) : RellCliEnv {
    val errorCache = mutableListOf<String>()
    val outputCache = mutableListOf<String>()
    override fun error(msg: String) = rellCliEnv.error(msg).also { if (cacheError) errorCache.add(msg) }
    override fun print(msg: String) = rellCliEnv.print(msg).also { if (cacheOutput) outputCache.add(msg) }
}
