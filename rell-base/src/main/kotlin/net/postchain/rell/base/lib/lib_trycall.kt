/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.immListOf

object Lib_TryCall {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("try_call", "boolean") {
            param("fn", type = "() -> unit", exact = true)
            bodyContext { ctx, f ->
                tryCall(ctx, f, Rt_BooleanValue.TRUE) { Rt_BooleanValue.FALSE }
            }
        }

        function("try_call") {
            generic("T")
            result(type = "T?")
            param("fn", type = "() -> T")
            bodyContext { ctx, f ->
                tryCall(ctx, f, null) { Rt_NullValue }
            }
        }

        function("try_call") {
            generic("T")
            result(type = "T")
            param("fn", type = "() -> T")
            param("default", type = "T", lazy = true)
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
