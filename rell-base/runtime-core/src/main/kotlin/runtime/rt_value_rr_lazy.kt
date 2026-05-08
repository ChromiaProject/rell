/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_Expr

/**
 * Lazy value that evaluates an [RR_Expr] on demand through the [Rt_Interpreter].
 * Used for `try_call` and similar constructs.
 */
class Rt_RR_LazyValue(
    override val type: Rt_ValueClass<*>,
    private val expr: RR_Expr,
    private val frame: Rt_Frame,
    private val interpreter: Rt_Interpreter,
): Rt_Value, Rt_LazyResolvableValue {
    private var cachedValue: Rt_Value? = null

    override val name
        get() = Companion.name

    override fun str(format: Rt_StrFormat): String = "lazy[...]"
    override fun strCode(showTupleFieldNames: Boolean): String = "lazy[...]"

    override fun resolveLazy(): Rt_Value {
        var res = cachedValue
        if (res == null) {
            res = interpreter.evaluateExpr(expr, frame)
            cachedValue = res
        }
        return res
    }

    companion object: Rt_ValueClass<Rt_RR_LazyValue> {
        override val name
            get() = "lazy"

        override val klass = Rt_RR_LazyValue::class
    }
}
