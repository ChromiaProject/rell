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
