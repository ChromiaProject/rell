/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import mu.KLogging
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.Rt_TryCallResultValue
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.isPostgresQueryCanceled
import net.postchain.rell.base.sql.SqlUtils.withSavepoint
import net.postchain.rell.base.utils.immListOf
import java.sql.SQLException

internal object Lib_TryCall: KLogging() {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("try_call", result = "boolean", since = "0.13.0") {
            comment("""
                Safely call a function that may fail (i.e. that may throw an exception).

                Accepts 0-ary unit-typed function references, i.e. references to functions of type `() -> unit`.

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
            bodyContext { ctx, fn ->
                tryCall(ctx, fn, onSuccess = { Rt_BooleanValue.TRUE }, onFailure = { Rt_BooleanValue.FALSE })
            }
        }

        function("try_call", result = "T?", since = "0.13.0") {
            comment("""
                Safely call a function that may fail (i.e. that may throw an exception).

                Accepts 0-ary function references, i.e. references to functions of type `() -> T`, and returns `T?`,
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
            param("fn", type = "() -> T", comment = "the function to be called")
            bodyContext { ctx, fn ->
                tryCall(ctx, fn, onFailure = { Rt_NullValue })
            }
        }

        function("try_call", result = "T", since = "0.13.0") {
            comment("""
                Safely call a function that may fail (i.e. that may throw an exception).

                Accepts 0-ary function references, i.e. references to functions of type `() -> T`, and a default
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
            param("fn", type = "() -> T", comment = "the function to be called")
            param("default", type = "T", lazy = true, comment = "the default value")
            bodyContext { ctx, fn, default ->
                tryCall(ctx, fn, onFailure = { (default as Rt_LazyResolvableValue).resolveLazy() })
            }
        }

        function("try_call_catch", result = "try_call_result<T>", since = "0.14.16") {
            comment("""
                Safely call a function that may fail (i.e. that may throw an exception), returning a result that can be
                inspected for errors.

                Accepts 0-ary function references, i.e. references to functions of type `() -> T`, and returns a
                `try_call_result<T>` which wraps
                either the return value of the function, or error information if the function threw an exception.

                Only exceptions of type `require` are caught, and any other exceptions are re-thrown. Use `try_call()`
                variants if you want to handle
                all exceptions.

                Changes to the database that occur during the call are rolled back when an exception is thrown.

                Examples:
                ```rell
                function fails(): integer {
                    require(false, "This fails");
                    return 0;
                }
                function succeeds(): integer { return 17; }
                try_call_catch(fails(*)) // returns a try_call_result containing the error message
                try_call_catch(succeeds(*)) // returns a try_call_result containing 17
                ```
                @return a `try_call_result<T>` containing either the return value of `fn` or error information
            """)
            generic("T")
            param("fn", type = "() -> T", comment = "the function to be called")

            bodyContext { ctx, fn ->
                val resultType = tryCallResultType(fn)
                tryCall(
                    ctx,
                    fn,
                    logException = false,
                    onSuccess = { callResult ->
                        Rt_TryCallResultValue(
                            resultType,
                            valueOrNull = callResult,
                            requireMessageOrNull = null,
                        )
                    },
                    onFailure = { exception ->
                        if (exception is Rt_Exception && exception.err is Rt_RequireError) {
                            Rt_TryCallResultValue(
                                resultType,
                                valueOrNull = null,
                                requireMessageOrNull = exception.err.message()
                            )
                        } else {
                            throw exception
                        }
                    },
                )
            }
        }

        type("try_call_result", since = "0.14.16") {
            generic("T")
            comment("""
                Type representing the result of a function call that may have thrown a `require` exception.

                Constructed by calling `try_call_catch()`. Contains either a value of type `T` or error information.

                Use `is_error` to check if the call failed, `value` to get the value (if the call succeeded),
                `value_or_null` to get the value or null (if the call failed), or `require_message_or_null` to get
                the error message (if the call failed) or null (if the call succeeded).
            """)

            rType { t -> R_TryCallResultType(t) }

            property("is_error", type = "boolean", pure = true, since = "0.14.16") {
                comment("""
                    Check if this result represents an error.
                """)
                value { self ->
                    Rt_BooleanValue.get((self as Rt_TryCallResultValue).isError)
                }
            }

            property(
                name = "value",
                type = "T",
                pure = true,
                since = "0.14.16",
                comment = """
                    Get the value contained in this result.
                    @throws exception if the function call threw a `require` exception
                """,
            ) {
                value { self ->
                    (self as Rt_TryCallResultValue).valueOrNull
                        ?: throw Rt_Exception.common("try_call_result:value:novalue", "Field 'value' has no value")
                }
            }

            property(
                name = "value_or_null",
                type = "T?",
                pure = true,
                since = "0.14.16",
                comment = """
                    Get the value contained in this result or null if this result represents an error.
                """,
            ) {
                value { self ->
                    (self as Rt_TryCallResultValue).valueOrNull ?: Rt_NullValue
                }
            }

            property("require_message_or_null", type = "text?", pure = true, since = "0.14.16") {
                comment("""
                    Get the error message from the `require` exception that was thrown by the function call,
                    or null if the function call did not throw an exception.
                """)
                value { self ->
                    val msg = (self as Rt_TryCallResultValue).requireMessageOrNull
                    if (msg != null) Rt_TextValue.get(msg) else Rt_NullValue
                }
            }
        }
    }

    private fun tryCallResultType(fn: Rt_Value): Rt_ValueClass<*> {
        val fnType = (fn as Rt_FunctionValue).type
        val rrFnType = checkNotNull(fnType.rrType as? RR_Type.Function) {
            "Expected function type, got ${fnType.name}"
        }
        return Rt_GenericRrType(
            rrType = RR_Type.Generic("try_call_result", immListOf(rrFnType.result)),
            name = "try_call_result",
        )
    }

    private fun tryCall(
        ctx: Rt_CallContext,
        fn: Rt_Value,
        logException: Boolean = true,
        onSuccess: (callResult: Rt_Value) -> Rt_Value = { it },
        onFailure: (exception: Exception) -> Rt_Value,
    ): Rt_Value {
        return try {
            if (needsSavepoint(ctx.exeCtx)) {
                ctx.exeCtx.sysSqlExec.connection { con ->
                    withSavepoint(con) {
                        doCall(fn, ctx, onSuccess)
                    }
                }
            } else {
                doCall(fn, ctx, onSuccess)
            }
        } catch (e: Exception) {
            rethrowIfCancellation(e)

            if (logException) {
                logException(e)
            }
            onFailure(e)
        }
    }

    private fun doCall(
        fn: Rt_Value,
        ctx: Rt_CallContext,
        onSuccess: (Rt_Value) -> Rt_Value = { it },
    ): Rt_Value {
        val v = (fn as Rt_FunctionValue).call(immListOf())
        return onSuccess(v)
    }

    private fun logException(e: Exception) {
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
     * To optimize performance, we do it by avoiding creating unnecessary savepoints. The savepoints are only
     * created if there is a possible write operation towards the database
     */
    private fun needsSavepoint(ctx: Rt_ExecutionContext) = ctx.sysSqlExec.hasRealConnection() && !ctx.dbReadOnly

    private fun rethrowIfCancellation(e: Exception) {
        // Rethrow InterruptedException to support thread interruption for query cancellation.
        if (e is InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
        // Rethrow SQLException with SQLState 57014 ("query_canceled") to support Postgres query cancellation.
        if (e is SQLException && e.isPostgresQueryCanceled) {
            throw e
        }
        // Check for wrapped InterruptedException or query cancellation SQLException.
        val cause = e.cause
        if (cause != null) {
            rethrowIfCancellation(cause as? Exception ?: return)
        }
    }
}

private class R_TryCallResultType(val elementType: R_Type): R_Type("try_call_result") {
    override fun equals0(other: R_Type): Boolean = other is R_TryCallResultType && elementType == other.elementType

    override fun hashCode0(): Int = elementType.hashCode()
    override fun isError(): Boolean = elementType.isError()

    override fun strCode(): String = name

    override fun getLibType0() = C_LibType.make(Lib_Rell.TRY_CALL_RESULT_TYPE, elementType)
}

