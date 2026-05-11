/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_ListComparator
import net.postchain.rell.base.runtime.utils.Rt_TupleComparator
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.plus
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * Inline factory: builds an anonymous [Rt_UntypedGtvConversion] whose decode is [decode].
 * Use only for conversions where the encode direction is structurally impossible
 * (e.g., virtual collections, where the input is a pre-decoded `Gtv`).
 */
fun gtvConversionOf(decode: (GtvToRtContext, Gtv) -> Rt_Value): Rt_GtvCompatibleValueClass<*> =
    object: Rt_UntypedGtvConversion("?") {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
            throw UnsupportedOperationException("toGtv not supported for this conversion")

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value = decode(ctx, gtv)
    }

interface Rt_NativeAdapter {
    val nativeTypes: ImmSet<KType>
    fun rtToNative(value: Rt_Value): Any?
    fun nativeToRt(value: Any?): Rt_Value
}

fun rtValueToGtv(rType: R_Type, value: Rt_Value, pretty: Boolean): Gtv? {
    val conv = gtvConversionFromR(rType) ?: return null
    return try {
        conv.rtToGtv(value, pretty)
    } catch (_: Rt_Exception) {
        null
    }
}

// =============================================================================
// Enum value helpers — produce Rt_RR_EnumValue from R_EnumDefinition
// =============================================================================

private val enumValuesCache = ConcurrentHashMap<R_EnumDefinition, ImmList<Rt_Value>>()

fun R_EnumDefinition.rtValues(): ImmList<Rt_Value> = enumValuesCache.getOrPut(this) {
    val rtType = rTypeStub(type)
    attrs.mapToImmList { Rt_RR_EnumValue(rtType, RR_EnumAttr(it.rName, it.value)) }
}

fun R_EnumDefinition.rtGetValue(attr: R_EnumAttr): Rt_Value {
    val i = attr.value
    check(attrs[i] === attr)
    return rtValues()[i]
}

fun R_EnumDefinition.rtGetValueOrNull(index: Int): Rt_Value? = rtValues().getOrNull(index)


/**
 * Create comparator purely from [RR_Type] — no R_Type needed.
 *
 * Primitives read the comparator from the value-class companion (e.g. `Rt_IntValue.comparator`)
 * via [primitiveValueClass]. Composites compose recursively. Entity/enum comparators are defined
 * here because those types have no value-class companion to host them.
 */
internal fun createComparator(rrType: RR_Type): Comparator<Rt_Value>? = when (rrType) {
    is RR_Type.Primitive -> primitiveValueClass(rrType.kind)?.comparator
    is RR_Type.Entity -> Rt_Comparator.create { (it as Rt_EntityValue).rowid }
    is RR_Type.Enum -> Rt_Comparator.create { (it as Rt_RR_EnumValue).rrAttr.value }
    is RR_Type.Null -> Rt_Comparator.create { 0 }
    is RR_Type.Nullable -> createComparator(rrType.value)?.let { inner ->
        Comparator { a, b ->
            when {
                a == Rt_NullValue && b == Rt_NullValue -> 0
                a == Rt_NullValue -> -1
                b == Rt_NullValue -> 1
                else -> inner.compare(a, b)
            }
        }
    }
    is RR_Type.Tuple -> {
        val fieldComparators = rrType.fields.map { createComparator(it.type) }
        if (fieldComparators.all { it != null }) {
            Rt_TupleComparator(fieldComparators.map { it!! }.toImmList())
        } else null
    }

    is RR_Type.List -> createComparator(rrType.element)?.let { Rt_ListComparator(it) }
    else -> null
}

fun fromCliRR(interpreter: Rt_Interpreter, rtType: Rt_ValueClass<*>, s: String): Rt_Value =
    when (val rrType = rtType.rrType) {
        is RR_Type.Primitive -> when (rrType.kind) {
            RR_PrimitiveKind.INTEGER -> Rt_IntValue.get(s.toLong())
            RR_PrimitiveKind.BOOLEAN -> Rt_BooleanValue.get(s.toBoolean())
            RR_PrimitiveKind.TEXT -> Rt_TextValue.get(s)
            RR_PrimitiveKind.BYTE_ARRAY -> Rt_ByteArrayValue.get(CommonUtils.hexToBytes(s))
            RR_PrimitiveKind.DECIMAL -> Rt_DecimalValue.get(s)
            RR_PrimitiveKind.BIG_INTEGER -> Rt_BigIntegerValue.get(s)
            RR_PrimitiveKind.ROWID -> Rt_RowidValue.get(s.toLong())
            else -> throw UnsupportedOperationException("fromCli not supported for type: ${rtType.name}")
        }

        is RR_Type.Entity -> Rt_EntityValue(rtType, s.toLong())

        is RR_Type.Nullable ->
            if (s == "null") Rt_NullValue else fromCliRR(interpreter, interpreter.resolveType(rrType.value), s)

        is RR_Type.List -> {
            val elemType = interpreter.resolveType(rrType.element)
            Rt_ListValue(rtType, s.split(",").map { fromCliRR(interpreter, elemType, it) }.toMutableList())
        }

        is RR_Type.Set -> {
            val elemType = interpreter.resolveType(rrType.element)
            Rt_SetValue(rtType, s.split(",").map { fromCliRR(interpreter, elemType, it) }.toMutableSet())
        }

        is RR_Type.Map -> {
            val keyType = interpreter.resolveType(rrType.key)
            val valueType = interpreter.resolveType(rrType.value)
            val map = s.split(",").associate {
                val (k, v) = it.split("=")
                fromCliRR(interpreter, keyType, k) to fromCliRR(interpreter, valueType, v)
            }
            Rt_MapValue(rtType, map.toMutableMap())
        }

        else -> throw UnsupportedOperationException("fromCli not supported for type: ${rtType.name}")
    }

/** Create native conversion via dispatch on [RR_Type]. */
private fun createNativeConversion(rrType: RR_Type): Rt_NativeAdapter? = when (rrType) {
    is RR_Type.Primitive -> when (rrType.kind) {
        RR_PrimitiveKind.INTEGER -> Rt_IntValue
        RR_PrimitiveKind.BOOLEAN -> Rt_BooleanValue
        RR_PrimitiveKind.TEXT -> Rt_TextValue
        RR_PrimitiveKind.BYTE_ARRAY -> Rt_ByteArrayValue
        RR_PrimitiveKind.DECIMAL -> Rt_DecimalValue
        RR_PrimitiveKind.BIG_INTEGER -> Rt_BigIntegerValue
        RR_PrimitiveKind.ROWID -> Rt_RowidValue
        RR_PrimitiveKind.UNIT -> Rt_UnitValue
        RR_PrimitiveKind.GTV -> Rt_GtvValue
        else -> null
    }

    is RR_Type.Nullable -> createNativeConversion(rrType.value)?.let { Rt_NativeAdapter_Nullable(it) }
    else -> null
}

internal class Rt_NativeAdapter_Nullable(
    private val valueAdapter: Rt_NativeAdapter,
): Rt_NativeAdapter {
    override val nativeTypes = let {
        val valueTypes = valueAdapter.nativeTypes
        val nullableTypes = valueTypes
            .filter { !it.isMarkedNullable }
            .mapNotNull { it.classifier?.createType(nullable = true) }
        (valueTypes + nullableTypes).toImmSet()
    }

    override fun rtToNative(value: Rt_Value): Any? {
        return if (value == Rt_NullValue) null else valueAdapter.rtToNative(value)
    }

    override fun nativeToRt(value: Any?): Rt_Value = if (value == null) Rt_NullValue else valueAdapter.nativeToRt(value)
}

/**
 * `true` for the virtual* family ([RR_Type.VirtualStruct], [RR_Type.VirtualList],
 * [RR_Type.VirtualSet], [RR_Type.VirtualMap], [RR_Type.VirtualTuple]). Replaces
 * `is R_VirtualType` checks in stdlib body-meta code that operates on [RR_Type].
 */
fun RR_Type.isVirtual(): Boolean = when (this) {
    is RR_Type.VirtualStruct,
    is RR_Type.VirtualList,
    is RR_Type.VirtualSet,
    is RR_Type.VirtualMap,
    is RR_Type.VirtualTuple -> true
    else -> false
}

/** Convert R_Type to RR_Type for stub/compile-time RR construction. */
internal fun rTypeToRRType(rType: R_Type): RR_Type = when (rType) {
    is R_BooleanType -> RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN)
    is R_IntegerType -> RR_Type.Primitive(RR_PrimitiveKind.INTEGER)
    is R_BigIntegerType -> RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER)
    is R_DecimalType -> RR_Type.Primitive(RR_PrimitiveKind.DECIMAL)
    is R_TextType -> RR_Type.Primitive(RR_PrimitiveKind.TEXT)
    is R_ByteArrayType -> RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)
    is R_RowidType -> RR_Type.Primitive(RR_PrimitiveKind.ROWID)
    is R_JsonType -> RR_Type.Primitive(RR_PrimitiveKind.JSON)
    is R_GtvType -> RR_Type.Primitive(RR_PrimitiveKind.GTV)
    is R_RangeType -> RR_Type.Primitive(RR_PrimitiveKind.RANGE)
    is R_UnitType -> RR_Type.Primitive(RR_PrimitiveKind.UNIT)
    is R_SignerType -> RR_Type.Primitive(RR_PrimitiveKind.SIGNER)
    is R_GUIDType -> RR_Type.Primitive(RR_PrimitiveKind.GUID)
    is R_NullType -> RR_Type.Null
    is R_ListType -> RR_Type.List(rTypeToRRType(rType.elementType))
    is R_SetType -> RR_Type.Set(rTypeToRRType(rType.elementType))
    is R_MapType -> RR_Type.Map(rTypeToRRType(rType.keyType), rTypeToRRType(rType.valueType))
    is R_NullableType -> RR_Type.Nullable(rTypeToRRType(rType.valueType))
    is R_TupleType -> RR_Type.Tuple(
        rType.fields.map { RR_TupleField(it.name?.str, rTypeToRRType(it.type)) }
            .toImmList(),
    )

    is R_VirtualListType -> RR_Type.VirtualList(rTypeToRRType(rType.innerType.elementType))
    is R_VirtualSetType -> RR_Type.VirtualSet(rTypeToRRType(rType.innerType.elementType))
    is R_VirtualMapType -> RR_Type.VirtualMap(
        rTypeToRRType(rType.innerType.keyType),
        rTypeToRRType(rType.innerType.valueType),
    )

    is R_VirtualTupleType -> RR_Type.VirtualTuple(
        rType.innerType.fields.map {
            RR_TupleField(
                it.name?.str,
                rTypeToRRType(it.type),
            )
        }.toImmList(),
    )

    is R_EntityType -> RR_Type.Entity(-1)
    is R_StructType -> RR_Type.Struct(rType.struct.rrDefIndex.coerceAtLeast(-1))
    is R_EnumType -> RR_Type.Enum(-1)
    is R_ObjectType -> RR_Type.Object(-1)
    is R_OperationType -> RR_Type.Operation(-1)
    is R_VirtualStructType -> RR_Type.VirtualStruct(-1)
    is R_FunctionType -> RR_Type.Function(rType.params.mapToImmList { rTypeToRRType(it) }, rTypeToRRType(rType.result))
    is R_CtErrorType -> RR_Type.Error
    else -> RR_Type.Generic(rType.name, immListOf())
}

/** Convert an Rt_Value to RR_ConstantValue for compile-time constant storage. */
fun rtValueToRRConstant(rType: R_Type, value: Rt_Value): RR_ConstantValue = rtValueToRRConstant(
    rrType = rTypeToRRType(rType),
    value = value,
    gtvConversion = lazy { gtvConversionFromR(rType) },
)

/** Pure-RR overload — dispatches on [RR_Type], uses [gtvConversion] for complex types. */
fun rtValueToRRConstant(
    rrType: RR_Type,
    value: Rt_Value,
    gtvConversion: Lazy<Rt_GtvCompatibleValueClass<*>?>
): RR_ConstantValue {
    if (value === Rt_NullValue) return RR_ConstantValue.Null
    if (value === Rt_UnitValue) return RR_ConstantValue.Unit
    return when (rrType) {
        is RR_Type.Primitive -> when (rrType.kind) {
            RR_PrimitiveKind.BOOLEAN -> RR_ConstantValue.Bool((value as Rt_BooleanValue).value)
            RR_PrimitiveKind.INTEGER -> RR_ConstantValue.Int((value as Rt_IntValue).value)
            RR_PrimitiveKind.TEXT -> RR_ConstantValue.Text((value as Rt_TextValue).value)
            RR_PrimitiveKind.BYTE_ARRAY -> RR_ConstantValue.ByteArray((value as Rt_ByteArrayValue).value)
            RR_PrimitiveKind.DECIMAL -> RR_ConstantValue.Decimal((value as Rt_DecimalValue).value.toPlainString())
            RR_PrimitiveKind.BIG_INTEGER -> RR_ConstantValue.BigInteger((value as Rt_BigIntegerValue).value.toString())
            RR_PrimitiveKind.ROWID -> RR_ConstantValue.Rowid((value as Rt_RowidValue).value)
            else -> encodeToGtvConstant(rrType, value, gtvConversion)
        }

        is RR_Type.Nullable -> rtValueToRRConstant(rrType.value, value, gtvConversion)
        else -> encodeToGtvConstant(rrType, value, gtvConversion)
    }
}

private fun encodeToGtvConstant(
    rrType: RR_Type,
    value: Rt_Value,
    gtvConversion: Lazy<Rt_GtvCompatibleValueClass<*>?>
): RR_ConstantValue = try {
    val conv = requireNotNull(gtvConversion.value) { "No GTV conversion for $rrType" }
    val gtv = conv.rtToGtv(value, false)
    RR_ConstantValue.Gtv(PostchainGtvUtils.gtvToJson(gtv))
} catch (e: Exception) {
    throw IllegalArgumentException("Cannot convert $rrType to RR_ConstantValue", e)
}
