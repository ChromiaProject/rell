package net.postchain.rell.codegen.typescript.util

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
import net.postchain.rell.base.model.*
import net.postchain.rell.codegen.deps.CamelCaseClassName

fun parameterTransformer(name: String, type: R_Type): String = when (type) {
    is R_SetType -> "Array.from($name)"
    else -> name
}

fun rTypeToString(type: R_Type, allowSet: Boolean = false): String {
    return when (type) {
        is R_NullableType -> "${rTypeToString(type.valueType)} | null"
        is R_BooleanType -> "boolean"
        is R_IntegerType -> "number"
        is R_BigIntegerType -> "bigint"
        is R_DecimalType -> "number"
        is R_TextType -> "string"
        is R_ByteArrayType -> "Buffer"
        is R_RowidType -> "number"
        is R_EntityType -> "number"
        is R_JsonType -> "string"
        is R_SetType -> if (allowSet) "Set<${rTypeToString(type.elementType, allowSet)}>" else "${rTypeToString(type.elementType)}[]"
        is R_ListType -> "${rTypeToString(type.elementType)}[]"
        is R_MapType -> "{[x in ${rTypeToString(type.keyType)}]: ${rTypeToString(type.valueType)}}"
        is R_StructType -> CamelCaseClassName.fromRellType(type).className
        is R_EnumType -> CamelCaseClassName.fromRellType(type).className
        is R_TupleType -> formatTupleType(type)
        is R_GtvType -> "any"

        else -> "any"
    }
}

private fun formatTupleType(type: R_TupleType): String {
    if (type.name.contains(":")) return formatNamedTuple(type)
    val fieldTypes = mutableListOf<String>()
    type.fields.forEach { fieldTypes.add(rTypeToString(it.type)) }
    return "$fieldTypes"
}

fun formatNamedTuple(type: R_TupleType): String {
    val fieldTypes = mutableMapOf<String, String>()
    type.fields.forEach { fieldTypes[it.name!!.str] = rTypeToString(it.type) }
    return fieldTypes.toString().replace("=", ":")
}
