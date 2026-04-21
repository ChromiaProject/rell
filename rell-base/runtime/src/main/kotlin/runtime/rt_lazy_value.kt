/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

sealed class Rt_LazyValue(
    private val rtType: Rt_Type,
): Rt_Value() {
    private var value: Rt_Value? = null

    final override val valueType = Rt_CoreValueTypes.LAZY.type()

    final override fun type() = rtType
    final override fun strCode(showTupleFieldNames: Boolean) = "lazy[...]"
    final override fun str(format: StrFormat): String = "lazy[...]"

    protected abstract fun calcValue(): Rt_Value

    final override fun asLazyValue(): Rt_Value {
        var res = value
        if (res == null) {
            res = calcValue()
            value = res
        }
        return res
    }
}

/** Lazy value wrapper around an already-computed value. Used by the RR interpreter's Lazy combiner. */
class Rt_EagerLazyValue(
    type: Rt_Type,
    private val innerValue: Rt_Value,
): Rt_LazyValue(type) {
    override fun calcValue(): Rt_Value = innerValue
}

/** Lazy value that defers computation until first access. Used by the RR interpreter for lazy parameters in at-expression what-clauses. */
class Rt_DeferredLazyValue(
    type: Rt_Type,
    private val compute: () -> Rt_Value,
): Rt_LazyValue(type) {
    override fun calcValue(): Rt_Value = compute()
}
