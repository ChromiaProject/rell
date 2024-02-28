/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.L_ParamImplication
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_RequireError
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.runtime.Rt_Value

object Lib_Require {
    val NAMESPACE = Ld_NamespaceDsl.make {
        function("require", "unit", pure = true) {
            comment("Checks a boolean condition and throws an exception if false.")
            param("value", "boolean", comment = "The boolean condition to be checked.")
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("The error message to be thrown if the condition is false.")
            }
            makeRequireBody(this, R_RequireCondition_Boolean)
        }

        function("require", pure = true) {
            comment("Checks for null values and throws an exception if null.")
            generic("T", subOf = "any")
            result(type = "T")
            param("value", type = "T?", nullable = true, implies = L_ParamImplication.NOT_NULL) {
                comment("The value to be checked for null.")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment( "The error message to be thrown if the value is null.")
            }
            makeRequireBody(this, R_RequireCondition_Nullable)
        }

        function("require_not_empty", pure = true) {
            comment("Checks for empty list and throws an exception if empty.")
            alias("requireNotEmpty", C_MessageType.ERROR)
            generic("T")
            result(type = "list<T>")
            param("value", type = "list<T>?", comment = "The list to be checked.")
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("The error message to be thrown if the list is empty.")
            }
            makeRequireBody(this, R_RequireCondition_Collection)
        }

        function("require_not_empty", pure = true) {
            comment("Checks for empty set and throws an exception if empty.")
            alias("requireNotEmpty", C_MessageType.ERROR)
            generic("T", subOf = "immutable")
            result(type = "set<T>")
            param("value", type = "set<T>?", comment = "The set to be checked.")
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("The error message to be thrown if the set is empty.")
            }
            makeRequireBody(this, R_RequireCondition_Collection)
        }

        function("require_not_empty", pure = true) {
            comment("Checks for empty map and throws an exception if empty.")
            alias("requireNotEmpty", C_MessageType.ERROR)
            generic("K", subOf = "immutable")
            generic("V")
            result(type = "map<K,V>")
            param("value", type = "map<K,V>?", comment = "The map to be checked.")
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("The error message to be thrown if the map is empty.")
            }
            makeRequireBody(this, R_RequireCondition_Map)
        }

        function("require_not_empty", pure = true) {
            comment("Checks for precence of a value and throws an exception if null.")
            alias("requireNotEmpty", C_MessageType.ERROR)
            generic("T", subOf = "any")
            result(type = "T")
            param("value", type = "T?", nullable = true, implies = L_ParamImplication.NOT_NULL) {
                comment("The nullable or empty value to be checked.")
            }
            param("message", "text", lazy = true, arity = L_ParamArity.ZERO_ONE) {
                comment("The error message to be thrown if the value is null or empty.")
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
