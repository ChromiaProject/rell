/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_ListComparator
import net.postchain.rell.base.runtime.utils.Rt_TupleComparator
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmSet
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmSet
import org.jooq.DataType
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.plus
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * Runtime type — the runtime's view of a type.
 *
 * [rrType] is the serializable identity (what the type IS).
 * Capabilities (SQL, GTV, comparator) are what the type CAN DO — populated
 * during stdlib init (primitives) or interpreter construction (user types).
 */
class Rt_Type(
    val rrType: RR_Type? = null,
    val name: String,
    val sqlAdapter: Rt_TypeSqlAdapter? = null,
    val gtvConversion: Rt_TypeGtvConversion? = null,
    val comparator: Comparator<Rt_Value>? = null,
    val nativeConversion: Rt_TypeNativeConversion? = null,
) {
    override fun toString() = name
    override fun equals(other: Any?) = other is Rt_Type && name == other.name
    override fun hashCode() = name.hashCode()
}

/** SQL adapter for binding values to prepared statements and reading from result sets. */
interface Rt_TypeSqlAdapter {
    /** JOOQ DataType for DDL generation; null when this type isn't backed by a SQL column. */
    val sqlType: DataType<*>? get() = null

    fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value)
    fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value =
        throw UnsupportedOperationException("fromSql not supported for this type")

    fun metaName(sqlCtx: Rt_SqlContext): String =
        throw UnsupportedOperationException("metaName not supported for this type")
}

/** GTV conversion — serialize Rt_Value to/from Gtv. */
interface Rt_TypeGtvConversion {
    fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv
    fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value
}

interface Rt_TypeNativeConversion {
    val nativeTypes: ImmSet<KType>
    fun rtToNative(value: Rt_Value): Any?
    fun nativeToRt(value: Any?): Rt_Value
}

internal fun rTypeToRtType(rType: R_Type): Rt_Type {
    val rrType = rTypeToRRType(rType)
    return Rt_Type(
        rrType = rrType,
        name = rType.name,
        sqlAdapter = buildBridgeSqlAdapter(rType, rrType),
        gtvConversion = buildBridgeGtvConversion(rType, rrType),
        comparator = createComparator(rrType),
        nativeConversion = createNativeConversion(rrType),
    )
}

/**
 * Build [Rt_Type] purely from an [RR_Type].
 *
 * Handles primitives, [RR_Type.Null], [RR_Type.Nullable], [RR_Type.List], [RR_Type.Set],
 * [RR_Type.Map] and [RR_Type.Tuple] (and any composition of those). Throws
 * [IllegalArgumentException] for definition-backed types (entity, struct, enum, object,
 * operation, virtual-struct) or virtual collections — those need interpreter context
 * (`rrApp`) to be resolved.
 *
 * Use this instead of [rTypeToRtType] in stdlib initialization where the type is built
 * from primitives only and no [R_Type] would otherwise exist.
 */
fun rrTypeToRtType(rrType: RR_Type): Rt_Type {
    val name = staticRrTypeName(rrType)
    return Rt_Type(
        rrType = rrType,
        name = name,
        sqlAdapter = R_TypeSqlAdapterBridge(createSqlAdapter(rrType, name)),
        gtvConversion = createPureRrGtvConversion(rrType, name),
        comparator = createComparator(rrType),
        nativeConversion = createNativeConversion(rrType),
    )
}

/** Static type-name for [rrTypeToRtType] — covers the same shapes as that function. */
private fun staticRrTypeName(rrType: RR_Type): String = when (rrType) {
    is RR_Type.Primitive -> rrType.kind.name.lowercase()
    is RR_Type.Null -> "null"
    is RR_Type.Nullable -> "${staticRrTypeName(rrType.value)}?"
    is RR_Type.List -> "list<${staticRrTypeName(rrType.element)}>"
    is RR_Type.Set -> "set<${staticRrTypeName(rrType.element)}>"
    is RR_Type.Map -> "map<${staticRrTypeName(rrType.key)},${staticRrTypeName(rrType.value)}>"
    is RR_Type.Tuple -> "(${
        rrType.fields.joinToString(",") { f ->
            if (f.name != null) "${f.name}:${staticRrTypeName(f.type)}" else staticRrTypeName(f.type)
        }
    })"
    else -> throw IllegalArgumentException(
        "rrTypeToRtType requires interpreter context for type: ${rrType::class.simpleName}",
    )
}

/** Pure-RR GTV conversion for primitives + composites of primitives. */
private fun createPureRrGtvConversion(rrType: RR_Type, name: String): Rt_TypeGtvConversion? = when (rrType) {
    is RR_Type.Primitive, is RR_Type.Null -> {
        val conv = createGtvConversion(rrType) ?: return null
        Rt_TypeGtvConversionLazy { conv }
    }

    is RR_Type.Nullable -> {
        val inner = rrTypeToRtType(rrType.value)
        Rt_TypeGtvConversionLazy {
            GtvRtConversion_Nullable(lazy { inner.gtvConversion!! })
        }
    }

    is RR_Type.List -> {
        val element = rrTypeToRtType(rrType.element)
        val self = lazy { rrTypeToRtType(rrType) }
        Rt_TypeGtvConversionLazy {
            GtvRtConversion_List(
                typeName = name,
                elementConversion = lazy { element.gtvConversion!! },
                rtType = self,
            )
        }
    }

    is RR_Type.Set -> {
        val element = rrTypeToRtType(rrType.element)
        val self = lazy { rrTypeToRtType(rrType) }
        Rt_TypeGtvConversionLazy {
            GtvRtConversion_Set(
                typeName = name,
                elementConversion = lazy { element.gtvConversion!! },
                rtType = self,
            )
        }
    }

    is RR_Type.Map -> {
        val key = rrTypeToRtType(rrType.key)
        val value = rrTypeToRtType(rrType.value)
        val self = lazy { rrTypeToRtType(rrType) }
        val isTextKey = rrType.key is RR_Type.Primitive
                && (rrType.key as RR_Type.Primitive).kind == RR_PrimitiveKind.TEXT
        Rt_TypeGtvConversionLazy {
            GtvRtConversion_Map(
                typeName = name,
                isTextKey = isTextKey,
                keyConversion = lazy { key.gtvConversion!! },
                valueConversion = lazy { value.gtvConversion!! },
                rtType = self,
            )
        }
    }

    is RR_Type.Tuple -> {
        val fieldRtTypes = rrType.fields.mapToImmList { rrTypeToRtType(it.type) }
        val self = lazy { rrTypeToRtType(rrType) }
        Rt_TypeGtvConversionLazy {
            GtvRtConversion_Tuple(
                typeName = name,
                fieldNames = rrType.fields.mapToImmList { it.name },
                fieldConversions = lazy { fieldRtTypes.mapToImmList { it.gtvConversion!! } },
                rtType = self,
            )
        }
    }

    else -> null
}

/**
 * Convert an [Rt_Value] to [Gtv] using the given [R_Type]'s GTV conversion.
 * Public bridge for external modules (e.g., dokka) that need GTV serialization
 * but should not construct [Rt_Type] directly.
 */
fun rtValueToGtv(rType: R_Type, value: Rt_Value, pretty: Boolean): Gtv? {
    val conv = rTypeToRtType(rType).gtvConversion ?: return null
    return try {
        conv.rtToGtv(value, pretty)
    } catch (_: Exception) {
        null
    }
}

/** Bridges [R_TypeSqlAdapter] → [Rt_TypeSqlAdapter] so that JVM-path values carry a SQL adapter. */
internal class R_TypeSqlAdapterBridge(internal val rAdapter: R_TypeSqlAdapter): Rt_TypeSqlAdapter {
    override val sqlType: DataType<*>? get() = rAdapter.sqlType

    override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) =
        rAdapter.toSql(params, idx, value)

    override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value =
        rAdapter.fromSql(row, idx, nullable)

    override fun metaName(sqlCtx: Rt_SqlContext): String =
        rAdapter.metaName(sqlCtx)
}

// =============================================================================
// Enum value helpers — produce Rt_RR_EnumValue from R_EnumDefinition
// =============================================================================

private val enumValuesCache = ConcurrentHashMap<R_EnumDefinition, ImmList<Rt_Value>>()

fun R_EnumDefinition.rtValues(): ImmList<Rt_Value> = enumValuesCache.getOrPut(this) {
    val rtTypeRef = lazy { rTypeToRtType(type) }
    attrs.mapToImmList { Rt_RR_EnumValue(rtTypeRef, RR_EnumAttr(it.rName, it.value)) }
}

fun R_EnumDefinition.rtGetValue(attr: R_EnumAttr): Rt_Value {
    val i = attr.value
    check(attrs[i] === attr)
    return rtValues()[i]
}

fun R_EnumDefinition.rtGetValueOrNull(index: Int): Rt_Value? = rtValues().getOrNull(index)


/**
 * Build SQL adapter for the [rTypeToRtType] bridge.
 * Definition-backed types (entity/enum) and nullable are handled inline with [rType] data.
 * Primitives delegate to the pure-RR [createSqlAdapter].
 */
private fun buildBridgeSqlAdapter(rType: R_Type, rrType: RR_Type): R_TypeSqlAdapterBridge = R_TypeSqlAdapterBridge(
    when (rType) {
        is R_EntityType -> {
            val entity = rType.rEntity
            R_TypeSqlAdapter_Entity(
                lazy { rTypeToRtType(rType) },
                rType.strCode(),
                entity.sqlMapping.metaName,
                entity.external?.chain?.index ?: -1,
            )
        }

        is R_EnumType -> {
            val rrAttrs = rType.enum.attrs.mapToImmList { RR_EnumAttr(it.rName, it.value) }
            R_TypeSqlAdapter_Enum(lazy { rTypeToRtType(rType) }, rType.strCode(), rrAttrs)
        }

        is R_NullableType -> R_TypeSqlAdapter_Nullable(
            buildBridgeSqlAdapter(rType.valueType, (rrType as RR_Type.Nullable).value).rAdapter,
        )
        // Primitives and fallback: pure-RR dispatch
        else -> createSqlAdapter(rrType, rType.strCode())
    },
)

/**
 * Create SQL adapter purely from [RR_Type] — handles primitives only.
 * Falls back to [R_TypeSqlAdapter_None] for unsupported types.
 * The interpreter has its own pure-RR equivalent.
 */
internal fun createSqlAdapter(rrType: RR_Type, typeName: String): R_TypeSqlAdapter = when (rrType) {
    is RR_Type.Primitive -> when (rrType.kind) {
        RR_PrimitiveKind.BOOLEAN -> R_TypeSqlAdapter_Boolean
        RR_PrimitiveKind.INTEGER -> R_TypeSqlAdapter_Integer
        RR_PrimitiveKind.BIG_INTEGER -> R_TypeSqlAdapter_BigInteger
        RR_PrimitiveKind.DECIMAL -> R_TypeSqlAdapter_Decimal
        RR_PrimitiveKind.TEXT -> R_TypeSqlAdapter_Text
        RR_PrimitiveKind.BYTE_ARRAY -> R_TypeSqlAdapter_ByteArray
        RR_PrimitiveKind.ROWID -> R_TypeSqlAdapter_Rowid
        RR_PrimitiveKind.JSON -> R_TypeSqlAdapter_Json
        else -> R_TypeSqlAdapter_None.create(typeName)
    }

    else -> R_TypeSqlAdapter_None.create(typeName)
}

/** Create comparator purely from [RR_Type] — no R_Type needed. */
private fun createComparator(rrType: RR_Type): Comparator<Rt_Value>? = when (rrType) {
    is RR_Type.Primitive -> when (rrType.kind) {
        RR_PrimitiveKind.INTEGER -> Rt_Comparator.create { it.asInteger() }
        RR_PrimitiveKind.BIG_INTEGER -> Rt_Comparator.create { it.asBigInteger() }
        RR_PrimitiveKind.DECIMAL -> Rt_Comparator.create { it.asDecimal() }
        RR_PrimitiveKind.TEXT -> Rt_Comparator.create { it.asString() }
        RR_PrimitiveKind.BYTE_ARRAY -> Comparator { a, b ->
            val la = a.asByteArray()
            val ra = b.asByteArray()
            val len = minOf(la.size, ra.size)
            for (i in 0 until len) {
                val c = la[i].toInt().and(0xFF).compareTo(ra[i].toInt().and(0xFF)); if (c != 0) return@Comparator c
            }
            la.size.compareTo(ra.size)
        }

        RR_PrimitiveKind.BOOLEAN -> Rt_Comparator.create { it.asBoolean() }
        RR_PrimitiveKind.ROWID -> Rt_Comparator.create { it.asRowid() }
        RR_PrimitiveKind.RANGE -> Rt_Comparator.create { it.asRange() }
        else -> null
    }

    is RR_Type.Entity -> Rt_Comparator.create { it.asObjectId() }
    is RR_Type.Enum -> Rt_Comparator.create { it.asEnum().value }
    is RR_Type.Null -> Rt_Comparator.create { 0 }
    is RR_Type.Nullable -> createComparator(rrType.value)
    is RR_Type.Tuple -> {
        val fieldComparators = rrType.fields.map { createComparator(it.type) }
        if (fieldComparators.all { it != null }) {
            Rt_TupleComparator(fieldComparators.map { it!! }.toImmList())
        } else null
    }

    is RR_Type.List -> createComparator(rrType.element)?.let { Rt_ListComparator(it) }
    else -> null
}

/**
 * Parse a CLI argument string into an [Rt_Value] using runtime-only type information.
 * Works on [Rt_Type] / [RR_Type] without an [R_Type]. The [interpreter] is required
 * to resolve composite element/key/value types.
 */
fun fromCliRR(interpreter: Rt_Interpreter, rtType: Rt_Type, s: String): Rt_Value = when (val rrType = rtType.rrType) {
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
private fun createNativeConversion(rrType: RR_Type): Rt_TypeNativeConversion? = when (rrType) {
    is RR_Type.Primitive -> when (rrType.kind) {
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

    is RR_Type.Nullable -> createNativeConversion(rrType.value)?.let { Rt_TypeNativeConversion_Nullable(it) }
    else -> null
}

internal class Rt_TypeNativeConversion_Nullable(
    private val valueAdapter: Rt_TypeNativeConversion,
): Rt_TypeNativeConversion {
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

/** Convert R_Type to RR_Type for the migration bridge. */
private fun rTypeToRRType(rType: R_Type): RR_Type = when (rType) {
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
    gtvConversion = lazy { rTypeToRtType(rType).gtvConversion },
)

/** Pure-RR overload — dispatches on [RR_Type], uses [gtvConversion] for complex types. */
fun rtValueToRRConstant(
    rrType: RR_Type,
    value: Rt_Value,
    gtvConversion: Lazy<Rt_TypeGtvConversion?>
): RR_ConstantValue {
    if (value === Rt_NullValue) return RR_ConstantValue.Null
    if (value === Rt_UnitValue) return RR_ConstantValue.Unit
    return when (rrType) {
        is RR_Type.Primitive -> when (rrType.kind) {
            RR_PrimitiveKind.BOOLEAN -> RR_ConstantValue.Bool(value.asBoolean())
            RR_PrimitiveKind.INTEGER -> RR_ConstantValue.Int(value.asInteger())
            RR_PrimitiveKind.TEXT -> RR_ConstantValue.Text(value.asString())
            RR_PrimitiveKind.BYTE_ARRAY -> RR_ConstantValue.ByteArray(value.asByteArray())
            RR_PrimitiveKind.DECIMAL -> RR_ConstantValue.Decimal(value.asDecimal().toPlainString())
            RR_PrimitiveKind.BIG_INTEGER -> RR_ConstantValue.BigInteger(value.asBigInteger().toString())
            RR_PrimitiveKind.ROWID -> RR_ConstantValue.Rowid(value.asRowid())
            else -> encodeToGtvConstant(rrType, value, gtvConversion)
        }

        is RR_Type.Nullable -> rtValueToRRConstant(rrType.value, value, gtvConversion)
        else -> encodeToGtvConstant(rrType, value, gtvConversion)
    }
}

private fun encodeToGtvConstant(
    rrType: RR_Type,
    value: Rt_Value,
    gtvConversion: Lazy<Rt_TypeGtvConversion?>
): RR_ConstantValue {
    return try {
        val conv = requireNotNull(gtvConversion.value) { "No GTV conversion for $rrType" }
        val gtv = conv.rtToGtv(value, false)
        RR_ConstantValue.Gtv(PostchainGtvUtils.gtvToJson(gtv))
    } catch (e: Exception) {
        throw IllegalArgumentException("Cannot convert $rrType to RR_ConstantValue", e)
    }
}
