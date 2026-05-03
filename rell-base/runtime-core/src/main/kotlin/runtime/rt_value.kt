/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:Suppress("MayBeConstant")

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.rr.RR_EnumAttr
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.CommonUtils
import org.jooq.DataType
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Root of the runtime value hierarchy.
 *
 * Conversions (Gtv/native/SQL) are NOT methods on values — they're capabilities of the type,
 * routed via the metaclass ([Rt_GtvCompatibleValueClass] / [Rt_NativeCompatibleValueClass] /
 * [Rt_SqlCompatibleValueClass]) or per-instance composite conversions. Putting them on the
 * value root would advertise a capability that not every value supports.
 * Iteration/collection/map/lazy capabilities remain instance-level markers
 * ([Rt_IterableValue], [Rt_CollectionValue], [Rt_MapBackedValue], [Rt_MutableMapBackedValue],
 * [Rt_LazyResolvableValue]).
 */
sealed interface Rt_Value {
    val name: String
    val type: Rt_ValueClass<*>

    fun toFormatArg(): Any = str()

    fun str(format: StrFormat = StrFormat.V1): String
    fun strCode(showTupleFieldNames: Boolean = true): String

    fun strPretty(indent: Int): String {
        val maxLen = 500
        val s = str(StrFormat.V2)
        return if (s.length <= maxLen) s else (s.substring(0, maxLen) + "...")
    }

    /** Output format flavour passed to [str]. */
    enum class StrFormat {
        V1,
        V2,
    }
}

/** Top-level alias of [Rt_Value.StrFormat] for ergonomic unqualified use at call sites. */
typealias Rt_StrFormat = Rt_Value.StrFormat

/**
 * Common base for concrete [Rt_Value] implementations. Locks `toString` to route through
 * [str] so debugging never exposes the JVM identity of a value.
 */
sealed class Rt_ValueBase: Rt_Value {
    final override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str(Rt_Value.StrFormat.V1)
    }
}

/** Iteration view over a value (range, list, set, map-as-tuples, byte-array-as-ints, ...). */
interface Rt_IterableValue: Rt_Value, Iterable<Rt_Value> {
    companion object {
        val name = "ITERABLE"
    }
}

/** Mutable collection-backed value (list, set). */
interface Rt_CollectionValue: Rt_IterableValue {
    val collection: MutableCollection<Rt_Value>

    override fun iterator(): Iterator<Rt_Value> = collection.iterator()

    companion object {
        val name = "COLLECTION"
    }
}

/** Read-only map-backed value (map, virtual-map). */
interface Rt_MapBackedValue {
    val mapView: Map<Rt_Value, Rt_Value>
}

/** Mutable map-backed value (only [Rt_MapValue], not virtual). */
interface Rt_MutableMapBackedValue: Rt_MapBackedValue {
    val mutableMapView: MutableMap<Rt_Value, Rt_Value>
}

/** A lazy thunk that resolves to another [Rt_Value]. */
interface Rt_LazyResolvableValue {
    fun resolveLazy(): Rt_Value

    companion object {
        val name = "LAZY"
    }
}

/**
 * Runtime type — every [Rt_Value] points at one via [Rt_Value.type].
 *
 * Owns the type's display name ([name]), its serializable structural identity ([rrType]),
 * the JVM class of values it produces ([klass]), and the optional ordering for sorting
 * heterogeneous collections ([comparator]).
 *
 * Capabilities (Gtv / native / SQL conversion) are NOT methods on this interface — they're
 * sealed-sibling capability interfaces ([Rt_GtvCompatibleValueClass],
 * [Rt_NativeCompatibleValueClass], [Rt_SqlCompatibleValueClass]). A class either implements
 * the capability or it doesn't; misuse is a compile-time `as?` check, not a nullable bag.
 *
 * Concrete shapes:
 * - **Primitive value companions** ([Rt_IntValue.Companion], [Rt_TextValue.Companion], ...) —
 *   implement the relevant capability interfaces directly. They serve as both the type token
 *   for cast/error reporting and the conversion implementation.
 * - **Parameterized type-classes** ([Rt_ListType], [Rt_MapType], etc.) — used for composite types
 *   (`list<T>` / `set<T>` / `map<K,V>` / tuple / nullable / virtuals) and for definition-backed
 *   types (entity / enum / struct / object). Each carries the relevant capability delegates and
 *   exposes them via [gtvConversion] / [sqlAdapter] / [nativeConversion] members.
 */
interface Rt_ValueClass<T: Rt_Value> {
    val name: String
    val klass: KClass<T>

    val comparator: Comparator<Rt_Value>?
        get() = null

    /**
     * Structural type identity. `null` on capability-only implementers (primitive companions /
     * capability adapters) where no canonical [RR_Type] is meaningful at the class level.
     * Concrete parameterized type-classes override with a non-null, real type identity.
     */
    val rrType: RR_Type?
        get() = null

    val gtvConversion: Rt_GtvCompatibleValueClass<*>?
        get() = this as? Rt_GtvCompatibleValueClass<*>

    val sqlAdapter: Rt_SqlCompatibleValueClass<*>?
        get() = this as? Rt_SqlCompatibleValueClass<*>

    val nativeConversion: Rt_NativeAdapter?
        get() = this as? Rt_NativeAdapter

    fun cast(v: Rt_Value): T = klass.safeCast(v) ?: throw Rt_ValueTypeError.exception(name, v.name)
}

/**
 * Metaclass capability: bidirectional Gtv conversion. The typed pair ([toGtv]/[fromGtv]) is for
 * call sites that statically know the value type. The untyped pair ([rtToGtv]/[gtvToRt]) is for
 * dispatching from a generic `Rt_Value` (e.g., per-element encoding inside a list/struct factory);
 * concrete implementations may override [gtvToRt] to short-circuit `validateOnly` mode.
 *
 * Composite/non-Rt_Value-1-1 conversions (entity, enum, nullable, virtual, tuple, list, set, map)
 * extend [Rt_UntypedGtvConversion], an abstract base implementing this interface with `T = Rt_Value`
 * and a degenerate `cast`.
 */
interface Rt_GtvCompatibleValueClass<T: Rt_Value>: Rt_ValueClass<T> {
    fun toGtv(value: T, pretty: Boolean): Gtv
    fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): T

    fun rtToGtv(value: Rt_Value, pretty: Boolean): Gtv = toGtv(cast(value), pretty)
    fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value = fromGtv(ctx, gtv)
}

/**
 * Metaclass capability: bidirectional native (JVM) conversion.
 *
 * [nativeTypes] declares the JVM types this class accepts on the inbound (`fromNative`) side,
 * including any nullable variants the type owner wants to advertise. The untyped pair
 * ([rtToNative]/[nativeToRt]) is for dispatching from a generic [Rt_Value] / `Any?`.
 */
interface Rt_NativeCompatibleValueClass<T: Rt_Value>: Rt_ValueClass<T>, Rt_NativeAdapter {
    fun toNative(value: T): Any
    fun fromNative(value: Any?): T

    override fun rtToNative(value: Rt_Value): Any = toNative(cast(value))
    override fun nativeToRt(value: Any?): T = fromNative(value)
}

/**
 * Metaclass capability: bidirectional SQL binding. Owns reads, writes, schema metadata, and
 * compatibility predicates so the type's SQL story lives in one place rather than being split
 * between a value class and a parallel adapter object.
 *
 * Composite/non-Rt_Value-1-1 conversions (entity, enum, nullable, "no SQL") implement this
 * directly without a typed `T` (use [Rt_Value] for `T`); their `cast` is a no-op pass-through.
 */
interface Rt_SqlCompatibleValueClass<T: Rt_Value>: Rt_ValueClass<T> {
    val sqlType: DataType<*>?

    fun isSqlCompatible(opts: C_CompilerOptions): Boolean = true
    fun isAllowedForEntityAttributes(opts: C_CompilerOptions): Boolean = isSqlCompatible(opts)
    fun metaName(sqlCtx: Rt_SqlContext): String = "sys:$name"

    fun toSqlValue(value: T): Any
    fun toSql(value: T, params: PreparedStatementParams, idx: Int)
    fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value

    fun rtToSqlValue(value: Rt_Value): Any = toSqlValue(cast(value))
    fun rtToSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) = toSql(cast(value), params, idx)
}

/** Shared NULL-handling helpers for [Rt_SqlCompatibleValueClass.fromSql] implementations. */
object Rt_SqlNull {
    fun check(suspect: Boolean, row: ResultSetRow, typeName: String, nullable: Boolean): Rt_Value? =
        if (suspect && row.wasNull()) (if (nullable) Rt_NullValue else err(typeName)) else null

    fun check(typeName: String, nullable: Boolean): Rt_Value = if (nullable) Rt_NullValue else err(typeName)

    private fun err(typeName: String): Nothing =
        throw Rt_Exception.common("sql_null:$typeName", "SQL value is NULL for type $typeName")
}

private fun Rt_Value.typeError(expected: String): Nothing =
    throw Rt_ValueTypeError.exception(expected, name)

fun Rt_Value.asBoolean(): Boolean = (this as? Rt_BooleanValue)?.value ?: typeError(Rt_BooleanValue.name)
fun Rt_Value.asInteger(): Long = (this as? Rt_IntValue)?.value ?: typeError(Rt_IntValue.name)
fun Rt_Value.asBigInteger(): BigInteger = (this as? Rt_BigIntegerValue)?.value ?: typeError(Rt_BigIntegerValue.name)
fun Rt_Value.asDecimal(): BigDecimal = (this as? Rt_DecimalValue)?.value ?: typeError(Rt_DecimalValue.name)
fun Rt_Value.asRowid(): Long = (this as? Rt_RowidValue)?.value ?: typeError(Rt_RowidValue.name)
fun Rt_Value.asString(): String = (this as? Rt_TextValue)?.value ?: typeError(Rt_TextValue.name)
fun Rt_Value.asByteArray(): ByteArray = (this as? Rt_ByteArrayValue)?.value ?: typeError(Rt_ByteArrayValue.name)
fun Rt_Value.asJson(): Rt_JsonValue = (this as? Rt_JsonValue) ?: typeError(Rt_JsonValue.name)
fun Rt_Value.asGtv(): Gtv = (this as? Rt_GtvValue)?.value ?: typeError(Rt_GtvValue.name)
fun Rt_Value.asRange(): Rt_RangeValue = (this as? Rt_RangeValue) ?: typeError(Rt_RangeValue.name)
fun Rt_Value.asObjectId(): Long = (this as? Rt_EntityValue)?.rowid ?: typeError(Rt_EntityValue.name)

fun Rt_Value.asRellTimeFormat(): Rt_TimeFormatValue =
    (this as? Rt_TimeFormatValue) ?: typeError(Rt_TimeFormatValue.name)

fun Rt_Value.asFunction(): Rt_FunctionValue = (this as? Rt_FunctionValue) ?: typeError(Rt_FunctionValue.name)
fun Rt_Value.asStruct(): Rt_StructValue = (this as? Rt_StructValue) ?: typeError(Rt_StructValue.name)
fun Rt_Value.asTuple(): List<Rt_Value> = (this as? Rt_TupleValue)?.elements ?: typeError(Rt_TupleValue.name)
fun Rt_Value.asEnum(): RR_EnumAttr = (this as? Rt_RR_EnumValue)?.rrAttr ?: typeError(Rt_RR_EnumValue.name)

fun Rt_Value.asLazyValue(): Rt_Value =
    (this as? Rt_LazyResolvableValue)?.resolveLazy() ?: typeError(Rt_LazyResolvableValue.name)

fun Rt_Value.asVirtual(): Rt_VirtualValue = (this as? Rt_VirtualValue) ?: typeError(Rt_VirtualValue.name)

fun Rt_Value.asVirtualCollection(): Rt_VirtualCollectionValue =
    (this as? Rt_VirtualCollectionValue) ?: typeError(Rt_VirtualCollectionValue.name)

fun Rt_Value.asVirtualList(): Rt_VirtualListValue =
    (this as? Rt_VirtualListValue) ?: typeError(Rt_VirtualListValue.name)

fun Rt_Value.asVirtualSet(): Rt_VirtualSetValue =
    (this as? Rt_VirtualSetValue) ?: typeError(Rt_VirtualSetValue.name)

fun Rt_Value.asVirtualTuple(): Rt_VirtualTupleValue =
    (this as? Rt_VirtualTupleValue) ?: typeError(Rt_VirtualTupleValue.name)

fun Rt_Value.asVirtualStruct(): Rt_VirtualStructValue =
    (this as? Rt_VirtualStructValue) ?: typeError(Rt_VirtualStructValue.name)

fun Rt_Value.asMapValue(): Rt_MapValue = (this as? Rt_MapValue) ?: typeError(Rt_MapValue.name)

fun Rt_Value.asMap(): Map<Rt_Value, Rt_Value> =
    (this as? Rt_MapBackedValue)?.mapView ?: typeError(Rt_MapValue.name)

fun Rt_Value.asMutableMap(): MutableMap<Rt_Value, Rt_Value> =
    (this as? Rt_MutableMapBackedValue)?.mutableMapView ?: typeError(Rt_MapValue.name)

fun Rt_Value.asList(): MutableList<Rt_Value> = (this as? Rt_ListValue)?.elements ?: typeError(Rt_ListValue.name)
fun Rt_Value.asSet(): MutableSet<Rt_Value> = (this as? Rt_SetValue)?.elements ?: typeError(Rt_SetValue.name)

fun Rt_Value.asCollection(): MutableCollection<Rt_Value> =
    (this as? Rt_CollectionValue)?.collection ?: typeError(Rt_CollectionValue.name)

fun Rt_Value.asIterable(): Iterable<Rt_Value> = (this as? Rt_IterableValue) ?: typeError(Rt_IterableValue.name)
