/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
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
                Safely call a function that may fail (i.e. that may throw an exception).

                Accepts nullary unit-typed function references, i.e. references to functions of type `() -> unit`.

                Exceptions thrown during the call are caught and logged with a stack trace.

                Changes to the database that occur during the call are rolled back when an exception is thrown.

                Examples:
                ```rell
                function fails(): unit {
                    list<integer>()[1]; // out of bounds
                }
                function succeeds(): unit {}
                try_call(fails(*)) // logs an out of bounds exception message and returns false
                try_call(succeeds(*)) // logs nothing, returns true
                ```
                @return `true` if call returns without throwing any exceptions, `false` otherwise
            """)
            param("fn", type = "() -> unit", exact = true, comment = "the function to call")
            bodyContext { ctx, f ->
                tryCall(ctx, f, Rt_BooleanValue.TRUE) { Rt_BooleanValue.FALSE }
            }
        }

        function("try_call", result = "T?", since = "0.13.0") {
            comment("""
                Safely call a function that may fail (i.e. that may throw an exception).

                Accepts nullary function references, i.e. references to functions of type `() -> T`, and returns `T?`,
                i.e. the return value of the reference function, or `null`.

                Exceptions thrown during the call are caught and logged with a stack trace.

                Changes to the database that occur during the call are rolled back when an exception is thrown.

                Examples:
                ```rell
                function fails(): integer {
                    return list<integer>()[1]; // out of bounds
                }
                function succeeds(): unit { return 0; }
                try_call(fails(*)) // logs an out of bounds exception message and returns null
                try_call(succeeds(*)) // logs nothing, returns 0
                ```
                @return the return value of `fn` if the call returns without throwing any exceptions, `null` otherwise
            """)
            generic("T")
            param("fn", type = "() -> T", comment = "the function to be call")
            bodyContext { ctx, f ->
                tryCall(ctx, f, null) { Rt_NullValue }
            }
        }

        function("try_call", result = "T", since = "0.13.0") {
            comment("""
                Safely call a function that may fail (i.e. that may throw an exception).

                Accepts nullary function references, i.e. references to functions of type `() -> T`, and a default
                value to return if the call fails.

                Exceptions thrown during the call are caught and logged with a stack trace.

                Changes to the database that occur during the call are rolled back when an exception is thrown.

                Examples:
                ```rell
                function fails(): integer {
                    return list<integer>()[1]; // out of bounds
                }
                function succeeds(): unit { return 0; }
                try_call(fails(*), 17) // logs an out of bounds exception message and returns 17
                try_call(succeeds(*), 17) // logs nothing, returns 0
                ```
                @return the return value of `fn` if the call returns without throwing any exceptions, `default`
                otherwise
            """)
            generic("T")
            param("fn", type = "() -> T", comment = "the function to be call")
            param("default", type = "T", lazy = true, comment = "the default value")
            bodyContext { ctx, f, v ->
                tryCall(ctx, f, null) { v.asLazyValue() }
            }
        }
    }

    private fun tryCall(ctx: Rt_CallContext, f: Rt_Value, okValue: Rt_Value?, errValueFn: () -> Rt_Value): Rt_Value {
        return try {
            if (needsSavepoint(ctx.exeCtx)) {
                ctx.exeCtx.sysSqlExec.connection { con ->
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
    private fun needsSavepoint(ctx: Rt_ExecutionContext) = ctx.sysSqlExec.hasRealConnection() && !ctx.dbReadOnly
}
