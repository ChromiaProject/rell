/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import mu.KLogging
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_ExecutionContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.SqlUtils.withSavepoint
import net.postchain.rell.base.utils.immListOf

object Lib_TryCall: KLogging() {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("try_call", "boolean", since = "0.13.0") {
            comment("""
                Calls a function that doesn't return a value and handles exceptions gracefully.
                @return `true` if call succeeds, `false` otherwise.
            """)
            param("fn", type = "() -> unit", exact = true, comment = "The function to be called.")
            bodyContext { ctx, f ->
                tryCall(ctx, f, Rt_BooleanValue.TRUE) { Rt_BooleanValue.FALSE }
            }
        }

        function("try_call", result = "T?", since = "0.13.0") {
            comment("""
                Calls a function and handles exceptions gracefully, returning null if an exception occurs.
                @return `T` if call succeeds, `null` otherwise.
            """)
            generic("T")
            param("fn", type = "() -> T", comment = "The function to be called.")
            bodyContext { ctx, f ->
                tryCall(ctx, f, null) { Rt_NullValue }
            }
        }

        function("try_call", result = "T", since = "0.13.0") {
            comment("""
                Calls a function and handles exceptions gracefully, providing a fallback value if an exception occurs.
                @return `T` if call succeeds and the supplied default value otherwise.
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
        return try {
            if (needsSavepoint(ctx.exeCtx)) {
                ctx.exeCtx.sqlExec.connection { con ->
                    withSavepoint(con) {
                        tryCall0(f, ctx, okValue)
                    }
                }
            } else {
                tryCall0(f, ctx, okValue)
            }
        } catch (e: Exception) {
            processException(e)
            errValueFn()
        }

    }

    private fun tryCall0(f: Rt_Value, ctx: Rt_CallContext, okValue: Rt_Value?): Rt_Value {
        val fnValue = f.asFunction()
        val v = fnValue.call(ctx, immListOf())
        return okValue ?: v
    }

    private fun processException(e: Exception) {
        logger.info {
            val msg = "try_call() failed"
            when (e) {
                is Rt_Exception -> {
                    val fullMsg = "$msg: ${e.fullMessage()}"
                    Rt_Utils.appendStackTrace(fullMsg, e.info.stack)
                }
                else -> "$msg: $e"
            }
        }
    }

    /**
    * In order to optimize performance, we do it by avoiding creating unnecessary savepoints. The savepoints are only
    * created if there is a possible write operation towards the database
    */
    private fun needsSavepoint(ctx: Rt_ExecutionContext) = ctx.sqlExec.hasRealConnection() && !ctx.dbReadOnly
}
