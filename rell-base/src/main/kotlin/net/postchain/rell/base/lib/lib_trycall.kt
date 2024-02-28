/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.immListOf

object Lib_TryCall {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("try_call", "boolean") {
            comment("""
                Calls a function that doesn't return a value and handles exceptions gracefully.
                @returns `true` if call succeeds, `false` otherwise.
            """)
            param("fn", type = "() -> unit", exact = true, comment = "The function to be called.")
            bodyContext { ctx, f ->
                tryCall(ctx, f, Rt_BooleanValue.TRUE) { Rt_BooleanValue.FALSE }
            }
        }

        function("try_call", result = "T?") {
            comment("""
                Calls a function and handles exceptions gracefully, returning null if an exception occurs.
                @returns `T` if call succeeds, `null` otherwise.
            """)
            generic("T")
            param("fn", type = "() -> T", comment = "The function to be called.")
            bodyContext { ctx, f ->
                tryCall(ctx, f, null) { Rt_NullValue }
            }
        }

        function("try_call", result = "T") {
            comment("""
                Calls a function and handles exceptions gracefully, providing a fallback value if an exception occurs.
                @returns `T` if call succeeds and the supplied default value otherwise.
            """)
            generic("T")
            param("fn", type = "() -> T", comment = "The function to be called.")
            param("default", type = "T", lazy = true, comment = "The fallback value to be returned if an exception occurs.")
            bodyContext { ctx, f, v ->
                tryCall(ctx, f, null) { v.asLazyValue() }
            }
        }
    }

    private fun tryCall(ctx: Rt_CallContext, f: Rt_Value, okValue: Rt_Value?, errValueFn: () -> Rt_Value): Rt_Value {
        val fnValue = f.asFunction()
        return try {
            val v = fnValue.call(ctx, immListOf())
            okValue ?: v
        } catch (e: Throwable) {
            errValueFn()
        }
    }
}
