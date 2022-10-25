package net.postchain.rell.codegen.kotlin.util

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.model.*

fun attributeToGtv(name: String, type: R_Type): String {
    return aliasToGtv(name, type) ?: when (type) {
        is R_EnumType -> "gtv(${name}.ordinal.toLong())"
        is R_StructType -> "GtvObjectMapper.toGtvArray($name)"
        is R_DecimalType -> "gtv($name.toString())"
        is R_RowidType -> "gtv($name.id)"
        is R_EntityType -> "gtv($name.id)"
        is R_NullableType -> "$name.let { if (it == null) GtvNull else ${attributeToGtv("it", type.valueType)} }"
        is R_ListType -> "gtv($name.map { ${attributeToGtv("it", type.elementType)} })"
        is R_SetType -> "gtv($name.map { ${attributeToGtv("it", type.elementType)} })"
        is R_MapType -> mapTypeToGtv(name, type)
        else -> "gtv($name)"
    }
}

private fun mapTypeToGtv(name: String, type: R_MapType) = when (type.keyType) {
    is R_TextType -> "gtv($name.mapValues { ${attributeToGtv("it.value", type.valueType)} })"
    is R_EnumType -> "gtv($name.map { (k, v) -> k.name to ${attributeToGtv("v", type.valueType)} }.toMap())"
    else -> throw IllegalArgumentException("Cannot map type to gtv")
}

fun rTypeToString(name: String, type: R_Type): String {
    return aliasToString(name, type) ?: when (type) {
        is R_NullableType -> "${rTypeToString(name, type.valueType)}?"
        is R_BooleanType -> "Boolean"
        is R_IntegerType -> "Long"
        is R_DecimalType -> "BigDecimal"
        is R_TextType -> "String"
        is R_ByteArrayType -> "WrappedByteArray"
        is R_RowidType -> "RowId"
        is R_EntityType -> "RowId"
        is R_JsonType -> "String"
        is R_SetType -> "Set<${rTypeToString(name, type.elementType)}>"
        is R_ListType -> "List<${rTypeToString(name, type.elementType)}>"
        is R_MapType -> "Map<${rTypeToString(name, type.keyType)}, ${rTypeToString(name, type.valueType)}>"
        is R_StructType -> CamelCaseClassName.fromString(type.name).name
        is R_EnumType -> CamelCaseClassName.fromString(type.name).name
        else -> "Gtv"
    }
}

fun aliasToString(name: String, type: R_Type): String? {
    return when {
        name == "pubkey" && type is R_ByteArrayType -> "PubKey"
        name == "blockchain_rid" && type is R_ByteArrayType -> "BlockchainRid"
        else -> null
    }
}

fun aliasToGtv(name: String, type: R_Type): String? {
    return when {
        type is R_NullableType && name == "pubkey" && type.valueType is R_ByteArrayType -> "pubkey.let { if (it == null) GtvNull else gtv(it.data) }"
        name == "pubkey" && type is R_ByteArrayType -> "gtv(pubkey.data)"
        else -> null
    }
}
