package net.postchain.rell.codegen.kotlin.util

import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal

fun applicationNameToPackageName(str: String) = "${str.split(":").first()}.${str.split(":").last().toObjectName()}"

private fun String.toObjectName() = nameToObjectName(this)
fun nameToObjectName(str: String) = str.snakeToUpperCamelCase()
fun rTypeToString(type: R_Type): String {
    return when (type) {
        is R_BooleanType -> Boolean::class.simpleName!!
        is R_IntegerType -> Long::class.simpleName!!
        is R_DecimalType -> BigDecimal::class.simpleName!!
        is R_TextType -> String::class.simpleName!!
        is R_ByteArrayType -> ByteArray::class.simpleName!!
        is R_RowidType -> Long::class.simpleName!!
        is R_JsonType -> throw IllegalArgumentException("JSON not supported")
        is R_MapType -> formatMapType(type)
        else -> type.name.split(":").last().snakeToUpperCamelCase() // Entity types
    }
}

private fun formatMapType(type: R_MapType): String {
    return "Map<${rTypeToString(type.keyType)}, ${rTypeToString(type.valueType)}>"
}

fun attributeToGtv(attributeName: String, attributeType: R_Type): String {
    return when (attributeType) {
        is R_EnumType -> "gtv(${attributeName}.ordinal.toLong())"
        is R_StructType -> "$attributeName.toGtv()"
        is R_DecimalType -> "gtv($attributeName.toString())"
        is R_NullableType -> "$attributeName.let { if (it == null) GtvNull else ${attributeToGtv("it", attributeType.valueType)} }"
        is R_ListType -> "gtv($attributeName.map { ${attributeToGtv("it", attributeType.elementType)} })"
        is R_SetType -> "gtv($attributeName.map { ${attributeToGtv("it", attributeType.elementType)} })"
        else -> "gtv($attributeName)"
    }
}
