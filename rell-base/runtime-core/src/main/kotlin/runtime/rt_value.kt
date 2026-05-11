/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:Suppress("MayBeConstant")
@file:JvmName("RtValueUtils")

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import org.jooq.DataType

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
interface Rt_MapBackedValue: Rt_Value {
    val mapView: Map<Rt_Value, Rt_Value>
}

/** Mutable map-backed value (only [Rt_MapValue], not virtual). */
interface Rt_MutableMapBackedValue: Rt_MapBackedValue {
    val mutableMapView: MutableMap<Rt_Value, Rt_Value>
}

/** A lazy thunk that resolves to another [Rt_Value]. */
interface Rt_LazyResolvableValue: Rt_Value {
    fun resolveLazy(): Rt_Value

    companion object {
        val name = "LAZY"
    }
}

/**
 * Runtime type — every [Rt_Value] points at one via [Rt_Value.type].
 *
 * Owns the type's display name ([name]), its serializable structural identity ([rrType]),
 * and the optional ordering for sorting heterogeneous collections ([comparator]).
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
 *
 * The [cast] default uses an unchecked cast; on a wrong-type value the JVM bridge for the
 * implementer's typed `to*`/`from*` method throws `ClassCastException`. This trades the
 * previous structured `Rt_ValueTypeError` for a smaller surface — these paths fire only on
 * compiler/runtime bugs, never on healthy execution.
 */
interface Rt_ValueClass<T: Rt_Value> {
    val name: String

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

    @Suppress("UNCHECKED_CAST")
    fun cast(v: Rt_Value): T = v as T
}

/**
 * Metaclass capability: bidirectional Gtv conversion. The typed pair ([toGtv]/[fromGtv]) is for
 * call sites that statically know the value type. The untyped pair ([rtToGtv]/[gtvToRt]) is for
 * dispatching from a generic `Rt_Value` (e.g., per-element encoding inside a list/struct factory);
 * concrete implementations may override [gtvToRt] to short-circuit `validateOnly` mode.
 *
 * Composite/non-Rt_Value-1-1 conversions (entity, enum, nullable, virtual, tuple, list, set, map)
 * extend [Rt_UntypedGtvConversion], an abstract base implementing this interface with `T = Rt_Value`.
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
 * directly without a typed `T` (use [Rt_Value] for `T`).
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
