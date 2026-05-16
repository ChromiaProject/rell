/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.Rt_ValueClass
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * The argument list of the in-flight system-function call, per thread. A typed `body { }` whose
 * function declared `self()` / `param(...)` delegates publishes its arguments around the call via
 * [enter] / [restore]; [Ld_ParamRef] / [Ld_OptParamRef] read them with [current].
 *
 * A single slot, not a stack: [enter] returns the previous occupant and [restore] puts it back,
 * so nesting — a stdlib function re-entering itself or another typed-body function — is held on
 * the JVM call stack rather than a heap-allocated deque.
 */
internal object Ld_CallArgs {
    private val slot: ThreadLocal<List<Rt_Value>?> = ThreadLocal()

    /** Publish [args] as the current call's arguments; returns the previous value, for [restore]. */
    fun enter(args: List<Rt_Value>): List<Rt_Value>? {
        val prev = slot.get()
        slot.set(args)
        return prev
    }

    /** Undo an [enter], restoring the value it returned. */
    fun restore(prev: List<Rt_Value>?) {
        slot.set(prev)
    }

    fun current(): List<Rt_Value> =
        slot.get() ?: error("No typed-body system-function call in progress on this thread")
}

/**
 * A typed handle to a mandatory system-function parameter, used as a property delegate:
 * `val exponent by param(Rt_IntValue, "exponent")`. Reading the property yields the matching
 * argument of the in-flight call, cast to [T].
 */
class Ld_ParamRef<T : Rt_Value> internal constructor(
    private val index: Int,
    private val type: Rt_ValueClass<T>,
): ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = type.cast(Ld_CallArgs.current()[index])
}

/**
 * Like [Ld_ParamRef], but for an optional trailing parameter: yields `null` when the argument
 * was omitted at the call site.
 */
class Ld_OptParamRef<T : Rt_Value> internal constructor(
    private val index: Int,
    private val type: Rt_ValueClass<T>,
): ReadOnlyProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        Ld_CallArgs.current().getOrNull(index)?.let(type::cast)
}

/**
 * Provider returned by the typed `param` overloads. Registers the parameter under the name of the
 * delegating Kotlin property — `val data_hash by param(Rt_ByteArrayValue)` declares a Rell
 * parameter named `data_hash` — so the parameter name is never spelled twice.
 */
class Ld_ParamProvider<T : Rt_Value> internal constructor(
    private val bind: (name: String) -> Ld_ParamRef<T>,
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Ld_ParamRef<T> = bind(property.name)
}

class Ld_OptParamProvider<T : Rt_Value> internal constructor(
    private val bind: (name: String) -> Ld_OptParamRef<T>,
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Ld_OptParamRef<T> = bind(property.name)
}
