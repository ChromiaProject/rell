/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin.util

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

fun attributeToGtv(name: String, type: R_Type): String = aliasToGtv(name, type) ?: when (type) {
    is R_EnumType -> "gtv(${name}.ordinal.toLong())"
    is R_StructType -> "GtvObjectMapper.toGtvArray($name)"
    is R_DecimalType -> "gtv($name.toString())"
    is R_RowidType -> "gtv($name.id)"
    is R_EntityType -> "gtv($name.id)"
    is R_NullableType -> "$name.let { if (it == null) GtvNull else ${attributeToGtv("it", type.valueType)} }"
    is R_ListType -> "gtv(${nestedAttributeToGtv(name, type.elementType, false)})"
    is R_SetType -> "gtv(${nestedAttributeToGtv(name, type.elementType, true)})"
    is R_MapType -> mapTypeToGtv(name, type)
    is R_GtvType -> name
    else -> "gtv($name)"
}

private fun nestedAttributeToGtv(name: String, type: R_Type, isSet: Boolean): String {
    val valueMapping = attributeToGtv("it", type)
    return if (valueMapping == "it") "$name${if (isSet) ".toList()" else ""}" else "$name.map { $valueMapping }"
}

private fun mapTypeToGtv(name: String, type: R_MapType) = when (type.keyType) {
    is R_TextType -> {
        val valueMapping = attributeToGtv("it.value", type.valueType)
        if (valueMapping == "it.value") "gtv($name)" else "gtv($name.mapValues { $valueMapping })"
    }

    else -> "gtv($name.map { (k, v) -> gtv(${attributeToGtv("k", type.keyType)}, ${attributeToGtv("v", type.valueType)}) })"
}

fun rTypeToString(name: String, type: R_Type, primitiveTypes: Boolean, aliases: Boolean): String =
        aliasToString(name, type, aliases) ?: when (type) {
            is R_NullableType -> "${rTypeToString(name, type.valueType, primitiveTypes, aliases)}?"
            is R_BooleanType -> "Boolean"
            is R_IntegerType -> "Long"
            is R_BigIntegerType -> "BigInteger"
            is R_DecimalType -> "BigDecimal"
            is R_TextType -> "String"
            is R_ByteArrayType -> if (primitiveTypes) "ByteArray" else "WrappedByteArray"
            is R_RowidType -> "RowId"
            is R_EntityType -> "RowId"
            is R_JsonType -> "String"
            is R_SetType -> "Set<${rTypeToString(name, type.elementType, primitiveTypes, aliases)}>"
            is R_ListType -> "List<${rTypeToString(name, type.elementType, primitiveTypes, aliases)}>"
            is R_MapType -> "Map<${rTypeToString(name, type.keyType, primitiveTypes, aliases)}, ${rTypeToString(name, type.valueType, primitiveTypes, aliases)}>"
            is R_StructType -> CamelCaseClassName.fromRellType(type).className
            is R_EnumType -> CamelCaseClassName.fromRellType(type).className
            else -> "Gtv"
        }

fun aliasToString(name: String, type: R_Type, enabled: Boolean): String? = if (!enabled) null else when {
    name == "pubkey" && type is R_ByteArrayType -> "PubKey"
    name == "blockchain_rid" && type is R_ByteArrayType -> "BlockchainRid"
    else -> null
}

fun aliasToGtv(name: String, type: R_Type): String? = when {
    type is R_NullableType && name == "pubkey" && type.valueType is R_ByteArrayType -> "pubkey.let { if (it == null) GtvNull else gtv(it.data) }"
    name == "pubkey" && type is R_ByteArrayType -> "gtv(pubkey.data)"
    else -> null
}

fun kotlinPackage(packageName: String, moduleName: String): String = if (moduleName.isBlank()) packageName else "$packageName.$moduleName"
