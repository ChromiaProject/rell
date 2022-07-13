package net.postchain.rell.codegen.kotlin.util

import net.postchain.rell.model.*

fun attributeToGtv(name: String, type: R_Type): String {
    return when (type) {
        is R_EnumType -> "gtv(${name}.ordinal.toLong())"
        is R_StructType -> "$name.toGtv()"
        is R_DecimalType -> "gtv($name.toString())"
        is R_NullableType -> "$name.let { if (it == null) GtvNull else ${attributeToGtv("it", type.valueType)} }"
        is R_ListType -> "gtv($name.map { ${attributeToGtv("it", type.elementType)} })"
        is R_SetType -> "gtv($name.map { ${attributeToGtv("it", type.elementType)} })"
        else -> "gtv($name)"
    }
}
