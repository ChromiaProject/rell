/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_RequireError
import net.postchain.rell.base.runtime.Rt_Value

object Lib_Require {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("require", "unit", pure = true, since = "0.6.0") {
            comment("""
                Asserts a boolean condition.
                @throws exception if the provided condition is false
            """)
            param("value", "boolean", implies = L_ParamImplication.TRUE.since("0.14.0")) {
                comment("the boolean condition to check")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("the message for the exception to be thrown if the condition is false")
            }
            makeRequireBody(this, R_RequireCondition_Boolean)
        }

        function("require", pure = true, since = "0.6.0") {
            comment("""
                Asserts that a value is not `null`.
                @return the passed `value`, but with type cast from `T?` to `T`
                @throws exception if the provided value is `null`
            """)
            generic("T", subOf = "any")
            result(type = "T")
            param("value", type = "T?", nullable = true, implies = L_ParamImplication.NOT_NULL) {
                comment("the value to check, and returned if it is not `null`")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("the error message to be thrown if the value is `null`")
            }
            makeRequireBody(this, R_RequireCondition_Nullable)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            comment("""
                Asserts that a list is non-null and non-empty.
                @return the passed `value`, but with type cast from `list<T>?` to `list<T>`
                @throws exception if the provided list is `null` or empty
            """)
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("T")
            result(type = "list<T>")
            param("value", type = "list<T>?", implies = L_ParamImplication.NOT_NULL) {
                comment("the list to be checked.")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("the message for the exception to be thrown if the list is `null` or empty")
            }
            makeRequireBody(this, R_RequireCondition_Collection)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            comment("""
                Asserts that a set is non-null and non-empty.
                @return the passed `value`, but with type cast from `set<T>?` to `set<T>`
                @throws exception if the provided set is `null` or empty
            """)
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("T", subOf = "immutable")
            result(type = "set<T>")
            param("value", type = "set<T>?", implies = L_ParamImplication.NOT_NULL, comment = "the set to be checked")
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("the message for the exception to be thrown if the set is `null` or empty")
            }
            makeRequireBody(this, R_RequireCondition_Collection)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            comment("""
                Asserts that a map is non-null and non-empty.
                @return the passed `value`, but with type cast from `map<K,V>?` to `map<K,V>`
                @throws exception if the provided map is `null` or empty
            """)
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("K", subOf = "immutable")
            generic("V")
            result(type = "map<K,V>")
            param("value", type = "map<K,V>?", implies = L_ParamImplication.NOT_NULL) {
                comment("the map to be checked")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("the message for the exception to be thrown if the map is `null` or empty")
            }
            makeRequireBody(this, R_RequireCondition_Map)
        }

        function("require_not_empty", pure = true, since = "0.9.0") {
            comment("""
                Asserts that a value is non-null.
                @return the passed `value`, but with type cast from `T?` to `T`
                @throws exception if the provided value is `null`
            """)
            alias("requireNotEmpty", C_MessageType.ERROR, since = "0.6.0")
            generic("T", subOf = "any")
            result(type = "T")
            param("value", type = "T?", nullable = true, implies = L_ParamImplication.NOT_NULL) {
                comment("the nullable value to be checked")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("the message for the exception to be thrown if the value is `null`")
            }
            makeRequireBody(this, R_RequireCondition_Nullable)
        }
    }

    private fun makeRequireBody(m: Ld_FunctionDsl, condition: R_RequireCondition) = with(m) {
        bodyOpt1 { arg1, arg2 ->
            val res = condition.calculate(arg1)
            if (res == null) {
                val msg = arg2?.asLazyValue()?.asString()
                throw Rt_RequireError.exception(msg)
            }
            res
        }
    }
}

sealed class R_RequireCondition {
    abstract fun calculate(v: Rt_Value): Rt_Value?
}

private object R_RequireCondition_Boolean: R_RequireCondition() {
    override fun calculate(v: Rt_Value) = if (v.asBoolean()) Rt_UnitValue else null
}

object R_RequireCondition_Nullable: R_RequireCondition() {
    override fun calculate(v: Rt_Value) = if (v != Rt_NullValue) v else null
}

object R_RequireCondition_Collection: R_RequireCondition() {
    override fun calculate(v: Rt_Value) = if (v != Rt_NullValue && v.asCollection().isNotEmpty()) v else null
}

object R_RequireCondition_Map: R_RequireCondition() {
    override fun calculate(v: Rt_Value) = if (v != Rt_NullValue && v.asMap().isNotEmpty()) v else null
}
