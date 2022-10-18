package net.postchain.rell.codegen.kotlin.util

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.model.*

fun attributeToGtv(name: String, type: R_Type): String {
    return when (type) {
        is R_EnumType -> "gtv(${name}.ordinal.toLong())"
        is R_StructType -> "$name.toGtv()"
        is R_DecimalType -> "gtv($name.toString())"
        is R_NullableType -> "$name.let { if (it == null) GtvNull else ${attributeToGtv("it", type.valueType)} }"
        is R_ListType -> "gtv($name.map { ${attributeToGtv("it", type.elementType)} })"
        is R_SetType -> "gtv($name.map { ${attributeToGtv("it", type.elementType)} })"
        is R_MapType -> "gtv($name.mapValues { ${attributeToGtv("it.value", type.valueType)} })"
        else -> "gtv($name)"
    }
}
fun rTypeToString(type: R_Type): String {
    return when (type) {
        is R_NullableType -> "${rTypeToString(type.valueType)}?"
        is R_BooleanType -> "Boolean"
        is R_IntegerType -> "Long"
        is R_DecimalType -> "BigDecimal"
        is R_TextType -> "String"
        is R_ByteArrayType -> "WrappedByteArray"
        is R_RowidType -> "Long"
        is R_JsonType -> "String"
        is R_EntityType -> "Long"
        is R_SetType -> "Set<${rTypeToString(type.elementType)}>"
        is R_ListType -> "List<${rTypeToString(type.elementType)}>"
        is R_MapType -> "Map<${rTypeToString(type.keyType)}, ${rTypeToString(type.valueType)}>"
        is R_StructType -> CamelCaseClassName.fromString(type.name).name
        is R_EnumType -> CamelCaseClassName.fromString(type.name).name
        else -> "Gtv"
    }
}
