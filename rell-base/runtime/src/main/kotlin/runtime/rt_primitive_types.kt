/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.immListOf

/**
 * Pre-built singleton [Rt_Type] instances for every Rell primitive type.
 *
 * These are constructed once at module init from the existing primitive
 * conversion classes in `lib/type/lib_type_*.kt` — they don't depend on an
 * [Rt_Interpreter], because every primitive's capabilities are fully determined
 * by its [RR_PrimitiveKind] alone (no entity/struct/enum cross-references).
 *
 * Stdlib body lambdas that previously did `rTypeToRtType(R_IntegerType)` (which
 * allocated a fresh `Rt_Type` per call and pulled in the legacy R_-bridged
 * capability lookups) should now reference these constants directly.
 *
 * The interpreter's per-app type cache still hands out its own instances for
 * the same logical types — that is fine, because [Rt_Type.equals] is keyed on
 * [Rt_Type.rrType] only, so the two routes interoperate freely.
 */
object Rt_PrimitiveTypes {
    val BOOLEAN: Rt_Type = build(RR_PrimitiveKind.BOOLEAN)
    val INTEGER: Rt_Type = build(RR_PrimitiveKind.INTEGER)
    val BIG_INTEGER: Rt_Type = build(RR_PrimitiveKind.BIG_INTEGER)
    val DECIMAL: Rt_Type = build(RR_PrimitiveKind.DECIMAL)
    val TEXT: Rt_Type = build(RR_PrimitiveKind.TEXT)
    val BYTE_ARRAY: Rt_Type = build(RR_PrimitiveKind.BYTE_ARRAY)
    val ROWID: Rt_Type = build(RR_PrimitiveKind.ROWID)
    val JSON: Rt_Type = build(RR_PrimitiveKind.JSON)
    val UNIT: Rt_Type = build(RR_PrimitiveKind.UNIT)
    val GUID: Rt_Type = build(RR_PrimitiveKind.GUID)
    val SIGNER: Rt_Type = build(RR_PrimitiveKind.SIGNER)
    val GTV: Rt_Type = build(RR_PrimitiveKind.GTV)
    val RANGE: Rt_Type = build(RR_PrimitiveKind.RANGE)
    val NULL: Rt_Type = Rt_Type(
        rrType = RR_Type.Null,
        name = "null",
        sqlAdapter = null,
        gtvConversion = Rt_TypeGtvConversionLazy { GtvRtConversion_Null },
        comparator = { _, _ -> 0 },
        nativeConversion = null,
    )

    private fun build(kind: RR_PrimitiveKind): Rt_Type = Rt_Type(
        rrType = RR_Type.Primitive(kind),
        name = primitiveName(kind),
        sqlAdapter = primitiveSqlAdapter(kind),
        gtvConversion = primitiveGtvConversion(kind),
        comparator = primitiveComparator(kind),
        nativeConversion = primitiveNativeConversion(kind),
    )
}

/** Display name for a primitive kind, matching what the compiler/interpreter uses elsewhere. */
internal fun primitiveName(kind: RR_PrimitiveKind): String = when (kind) {
    RR_PrimitiveKind.BOOLEAN -> "boolean"
    RR_PrimitiveKind.INTEGER -> "integer"
    RR_PrimitiveKind.BIG_INTEGER -> "big_integer"
    RR_PrimitiveKind.DECIMAL -> "decimal"
    RR_PrimitiveKind.TEXT -> "text"
    RR_PrimitiveKind.BYTE_ARRAY -> "byte_array"
    RR_PrimitiveKind.ROWID -> "rowid"
    RR_PrimitiveKind.JSON -> "json"
    RR_PrimitiveKind.UNIT -> "unit"
    RR_PrimitiveKind.GUID -> "guid"
    RR_PrimitiveKind.SIGNER -> "signer"
    RR_PrimitiveKind.GTV -> "gtv"
    RR_PrimitiveKind.RANGE -> "range"
}

/** SQL adapter for a primitive — bridged from the existing legacy primitive adapter classes. */
internal fun primitiveSqlAdapter(kind: RR_PrimitiveKind): Rt_TypeSqlAdapter? {
    val rAdapter: R_TypeSqlAdapter = when (kind) {
        RR_PrimitiveKind.INTEGER -> R_TypeSqlAdapter_Integer
        RR_PrimitiveKind.BOOLEAN -> R_TypeSqlAdapter_Boolean
        RR_PrimitiveKind.TEXT -> R_TypeSqlAdapter_Text
        RR_PrimitiveKind.BYTE_ARRAY -> R_TypeSqlAdapter_ByteArray
        RR_PrimitiveKind.DECIMAL -> R_TypeSqlAdapter_Decimal
        RR_PrimitiveKind.BIG_INTEGER -> R_TypeSqlAdapter_BigInteger
        RR_PrimitiveKind.ROWID -> R_TypeSqlAdapter_Rowid
        RR_PrimitiveKind.JSON -> R_TypeSqlAdapter_Json
        else -> return null
    }
    return R_TypeSqlAdapterBridge(rAdapter)
}

/** GTV conversion for a primitive. */
internal fun primitiveGtvConversion(kind: RR_PrimitiveKind): Rt_TypeGtvConversion? {
    val conv: GtvRtConversion = when (kind) {
        RR_PrimitiveKind.INTEGER -> GtvRtConversion_Integer
        RR_PrimitiveKind.BOOLEAN -> GtvRtConversion_Boolean
        RR_PrimitiveKind.TEXT -> GtvRtConversion_Text
        RR_PrimitiveKind.BYTE_ARRAY -> GtvRtConversion_ByteArray
        RR_PrimitiveKind.DECIMAL -> GtvRtConversion_Decimal
        RR_PrimitiveKind.BIG_INTEGER -> GtvRtConversion_BigInteger
        RR_PrimitiveKind.ROWID -> GtvRtConversion_Rowid
        RR_PrimitiveKind.JSON -> GtvRtConversion_Json
        RR_PrimitiveKind.GTV -> GtvRtConversion_Gtv
        RR_PrimitiveKind.UNIT, RR_PrimitiveKind.GUID,
        RR_PrimitiveKind.SIGNER, RR_PrimitiveKind.RANGE -> return null
    }
    return Rt_TypeGtvConversionLazy { conv }
}

internal fun primitiveComparator(kind: RR_PrimitiveKind): Comparator<Rt_Value>? = when (kind) {
    RR_PrimitiveKind.INTEGER -> Comparator { a, b -> a.asInteger().compareTo(b.asInteger()) }
    RR_PrimitiveKind.BIG_INTEGER -> Comparator { a, b -> a.asBigInteger().compareTo(b.asBigInteger()) }
    RR_PrimitiveKind.DECIMAL -> Comparator { a, b -> a.asDecimal().compareTo(b.asDecimal()) }
    RR_PrimitiveKind.TEXT -> Comparator { a, b -> a.asString().compareTo(b.asString()) }
    RR_PrimitiveKind.BOOLEAN -> Comparator { a, b -> a.asBoolean().compareTo(b.asBoolean()) }
    RR_PrimitiveKind.ROWID -> Comparator { a, b -> a.asRowid().compareTo(b.asRowid()) }
    RR_PrimitiveKind.RANGE -> Comparator { a, b -> a.asRange().compareTo(b.asRange()) }
    RR_PrimitiveKind.BYTE_ARRAY -> Comparator { a, b ->
        val la = a.asByteArray()
        val lb = b.asByteArray()
        val len = minOf(la.size, lb.size)
        for (i in 0 until len) {
            val c = (la[i].toInt() and 0xFF).compareTo(lb[i].toInt() and 0xFF)
            if (c != 0) return@Comparator c
        }
        la.size.compareTo(lb.size)
    }

    else -> null
}

internal fun primitiveNativeConversion(kind: RR_PrimitiveKind): Rt_TypeNativeConversion? = when (kind) {
    RR_PrimitiveKind.INTEGER -> Rt_NativeConversion_Integer
    RR_PrimitiveKind.BOOLEAN -> Rt_NativeConversion_Boolean
    RR_PrimitiveKind.TEXT -> Rt_NativeConversion_Text
    RR_PrimitiveKind.BYTE_ARRAY -> Rt_NativeConversion_ByteArray
    RR_PrimitiveKind.DECIMAL -> Rt_NativeConversion_Decimal
    RR_PrimitiveKind.BIG_INTEGER -> Rt_NativeConversion_BigInteger
    RR_PrimitiveKind.ROWID -> Rt_NativeConversion_Rowid
    RR_PrimitiveKind.UNIT -> Rt_NativeConversion_Unit
    RR_PrimitiveKind.GTV -> Rt_NativeConversion_Gtv
    else -> null
}

/**
 * Builds a singleton [Rt_Type] for a stdlib library type backed by [RR_Type.Generic].
 *
 * Used by stdlib value classes whose `type()` override previously did
 * `rTypeToRtType(R_SomeLibType)`. Stdlib library types have no SQL/GTV/comparator/native
 * representation by default — pass non-null arguments only when the type genuinely
 * supports them.
 *
 * Each call returns a fresh `Rt_Type`; cache the result in a top-level `val` if you
 * want a singleton.
 */
fun makeStdlibLibType(
    name: String,
    sqlAdapter: Rt_TypeSqlAdapter? = null,
    gtvConversion: Rt_TypeGtvConversion? = null,
    comparator: Comparator<Rt_Value>? = null,
    nativeConversion: Rt_TypeNativeConversion? = null,
): Rt_Type = Rt_Type(
    rrType = RR_Type.Generic(name, immListOf()),
    name = name,
    sqlAdapter = sqlAdapter,
    gtvConversion = gtvConversion,
    comparator = comparator,
    nativeConversion = nativeConversion,
)
