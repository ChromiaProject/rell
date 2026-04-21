/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

/**
 * Display/diagnostic helpers for [RR_ConstantValue]. Defined here rather than on the data
 * type so that `rr-tree` stays a pure data layer with no error-message formatting concerns.
 */

/** Returns a user-friendly display string matching the old Rt_Value.strCode() format, e.g. "int[0]". */
fun RR_ConstantValue.displayStr(): String = when (this) {
    is RR_ConstantValue.Null -> "null"
    is RR_ConstantValue.Unit -> "unit"
    is RR_ConstantValue.Bool -> "boolean[$value]"
    is RR_ConstantValue.Int -> "int[$value]"
    is RR_ConstantValue.Text -> "text[$value]"
    is RR_ConstantValue.ByteArray -> "byte_array[${value.joinToString("") { "%02x".format(it) }}]"
    is RR_ConstantValue.Decimal -> "dec[$value]"
    is RR_ConstantValue.BigInteger -> "bigint[$value]"
    is RR_ConstantValue.Rowid -> "rowid[$value]"
    is RR_ConstantValue.Enum ->
        if (enumTypeName.isNotEmpty() && enumAttrName.isNotEmpty()) "$enumTypeName[$enumAttrName]"
        else "enum[$enumValue]"
    is RR_ConstantValue.Gtv -> "gtv[$json]"
    is RR_ConstantValue.Struct -> "struct[$structDefIndex]"
    is RR_ConstantValue.Collection -> "list[${elementValues.joinToString(",") { it.displayStr() }}]"
    is RR_ConstantValue.MapConstant -> "map[...]"
    is RR_ConstantValue.TupleConstant -> "(${fieldValues.joinToString(",") { it.displayStr() }})"
    is RR_ConstantValue.Meta -> "meta[$fullName]"
}

/** Returns the Rell type name for error messages, e.g. "integer", "text", "boolean". */
fun RR_ConstantValue.typeStr(): String = when (this) {
    is RR_ConstantValue.Null -> "null"
    is RR_ConstantValue.Unit -> "unit"
    is RR_ConstantValue.Bool -> "boolean"
    is RR_ConstantValue.Int -> "integer"
    is RR_ConstantValue.Text -> "text"
    is RR_ConstantValue.ByteArray -> "byte_array"
    is RR_ConstantValue.Decimal -> "decimal"
    is RR_ConstantValue.BigInteger -> "big_integer"
    is RR_ConstantValue.Rowid -> "rowid"
    is RR_ConstantValue.Enum -> enumTypeName.ifEmpty { "enum" }
    is RR_ConstantValue.Gtv -> "gtv"
    is RR_ConstantValue.Struct -> "struct"
    is RR_ConstantValue.Collection -> "collection"
    is RR_ConstantValue.MapConstant -> "map"
    is RR_ConstantValue.TupleConstant -> "tuple"
    is RR_ConstantValue.Meta -> "meta"
}
