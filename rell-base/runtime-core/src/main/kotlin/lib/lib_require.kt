/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_RellErrorType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils

internal object Lib_Require {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("require", "unit", pure = true, since = "0.6.0") {
            """
                Asserts a boolean condition.
                @throws exception if the provided condition is false
            """.comment()
            val value by param(
                "boolean",
                cast = Rt_Value,
                implies = L_ParamImplication.TRUE.since("0.14.0"),
                comment = "the boolean condition to check",
            )
            val message by paramOpt(
                "text",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the message for the exception to be thrown if the condition is false",
            )
            makeRequireBody(this, { value }, { message }, Rt_RequireCondition_Boolean)
        }

        function("require", pure = true, since = "0.6.0") {
            """
                Asserts that a value is not `null`.
                @return the passed `value`, but with type cast from `T?` to `T`
                @throws exception if the provided value is `null`
            """.comment()
            generic("T", subOf = "any")
            result(type = "T")
            val value by param(
                "T?",
                cast = Rt_Value,
                nullable = true,
                implies = L_ParamImplication.NOT_NULL,
                comment = "the value to check, and returned if it is not `null`",
            )
            val message by paramOpt(
                "text",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the error message to be thrown if the value is `null`",
            )
            makeRequireBody(this, { value }, { message }, Rt_RequireCondition_Nullable)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            """
                Asserts that a list is non-null and non-empty.
                @return the passed `value`, but with type cast from `list<T>?` to `list<T>`
                @throws exception if the provided list is `null` or empty
            """.comment()
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("T")
            result(type = "list<T>")
            val value by param(
                "list<T>?",
                cast = Rt_Value,
                implies = L_ParamImplication.NOT_NULL,
                comment = "the list to be checked.",
            )
            val message by paramOpt(
                "text",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the message for the exception to be thrown if the list is `null` or empty",
            )
            makeRequireBody(this, { value }, { message }, Rt_RequireCondition_Collection)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            """
                Asserts that a set is non-null and non-empty.
                @return the passed `value`, but with type cast from `set<T>?` to `set<T>`
                @throws exception if the provided set is `null` or empty
            """.comment()
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("T", subOf = "immutable")
            result(type = "set<T>")
            val value by param(
                "set<T>?",
                cast = Rt_Value,
                implies = L_ParamImplication.NOT_NULL,
                comment = "the set to be checked",
            )
            val message by paramOpt(
                "text",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the message for the exception to be thrown if the set is `null` or empty",
            )
            makeRequireBody(this, { value }, { message }, Rt_RequireCondition_Collection)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            """
                Asserts that a map is non-null and non-empty.
                @return the passed `value`, but with type cast from `map<K,V>?` to `map<K,V>`
                @throws exception if the provided map is `null` or empty
            """.comment()
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("K", subOf = "immutable")
            generic("V")
            result(type = "map<K,V>")
            val value by param(
                "map<K,V>?",
                cast = Rt_Value,
                implies = L_ParamImplication.NOT_NULL,
                comment = "the map to be checked",
            )
            val message by paramOpt(
                "text",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the message for the exception to be thrown if the map is `null` or empty",
            )
            makeRequireBody(this, { value }, { message }, Rt_RequireCondition_Map)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            """
                Asserts that a value is non-null.
                @return the passed `value`, but with type cast from `T?` to `T`
                @throws exception if the provided value is `null`
            """.comment()
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("T", subOf = "any")
            result(type = "T")
            val value by param(
                "T?",
                cast = Rt_Value,
                nullable = true,
                implies = L_ParamImplication.NOT_NULL,
                comment = "the nullable value to be checked",
            )
            val message by paramOpt(
                "text",
                cast = Rt_LazyResolvableValue,
                lazy = true,
                comment = "the message for the exception to be thrown if the value is `null`",
            )
            makeRequireBody(this, { value }, { message }, Rt_RequireCondition_Nullable)
        }


        namespace("rell", since = "0.14.15") {
            type("error_type", rType = R_RellErrorType, abstract = true, hidden = true, since = "0.14.15")

            function("error", pure = true, since = "0.14.15") {
                """
                    Unconditionally fail, raising an exception, with an optional message.

                    Ends control flow, enabling one to write e.g.

                    ```rell
                    function f(x: integer?): integer {
                        if (x == null) {
                            rell.error('null argument');
                        }
                        return x * x; // compiler knows that x cannot be null, so we can write x * x
                    }
                    ```

                    `rell.error(message)` is equivalent to `require(false, message)`, except that `rell.error()` has the
                    additional end-of-control-flow behaviour.

                    @throws exception unconditionally
                """.comment()
                result(type = "rell.error_type")
                val message by paramOpt(
                    "text",
                    cast = Rt_LazyResolvableValue,
                    lazy = true,
                    comment = "the message for the exception to be thrown",
                )
                bodyN { args ->
                    Rt_Utils.checkRange(args.size, 0, 1)
                    val msg = (message?.resolveLazy() as? Rt_TextValue)?.value
                    throw Rt_RequireError.exception(msg)
                }
            }
        }
    }

    private fun makeRequireBody(
        m: Ld_FunctionDsl,
        value: () -> Rt_Value,
        message: () -> Rt_LazyResolvableValue?,
        condition: Rt_RequireCondition,
    ) = with(m) {
        body {
            val res = condition.calculate(value())
            res ?: throw Rt_RequireError.exception((message()?.resolveLazy() as? Rt_TextValue)?.value)
        }
    }
}

internal sealed interface Rt_RequireCondition {
    fun calculate(v: Rt_Value): Rt_Value?
}

private object Rt_RequireCondition_Boolean: Rt_RequireCondition {
    override fun calculate(v: Rt_Value) = if ((v as Rt_BooleanValue).value) Rt_UnitValue else null
}

internal object Rt_RequireCondition_Nullable: Rt_RequireCondition {
    override fun calculate(v: Rt_Value) = if (v != Rt_NullValue) v else null
}

internal object Rt_RequireCondition_Collection: Rt_RequireCondition {
    override fun calculate(v: Rt_Value) = if (v != Rt_NullValue && (v as Rt_CollectionValue).collection.isNotEmpty()) v else null
}

internal object Rt_RequireCondition_Map: Rt_RequireCondition {
    override fun calculate(v: Rt_Value) = if (v != Rt_NullValue && (v as Rt_MapBackedValue).mapView.isNotEmpty()) v else null
}
