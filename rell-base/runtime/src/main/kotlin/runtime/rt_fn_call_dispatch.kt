/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lib.type.Rt_MapValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.model.rr.RR_FunctionCallTarget
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.runtime.utils.isPostgresQueryCanceled
import net.postchain.rell.base.utils.LazyString

/**
 * Recovers the display name from an RR_ sys-function key.
 *
 * Keys are `displayName#signature` (content-derived, see [RR_IrResolver.buildSysFnKey]).
 * `#` is not used in Rell type syntax, so splitting on it cleanly yields the user-facing
 * name. `@` is preserved as a secondary split for defensive compatibility with legacy
 * identity-hash-suffixed keys (e.g. from a pre-migration serialized artifact).
 */
internal fun sysFnDisplayName(fnName: String): String {
    return fnName.substringBefore('#').substringBefore('@')
}

fun createFunctionValueFromTarget(
    target: Rt_FunctionCallTarget,
    resType: Rt_Type,
    mapping: R_PartialCallMapping,
    baseValue: Rt_Value?,
    args: List<Rt_Value>,
): Rt_Value {
    if (target.rrTarget is RR_FunctionCallTarget.FunctionValue && baseValue != null && baseValue != Rt_NullValue) {
        return baseValue.asFunction().combine(resType, mapping, args)
    }
    return Rt_FunctionValue(resType, mapping, target, baseValue, args)
}

fun Rt_FunctionCallTarget.targetStr(baseValue: Rt_Value?, format: Rt_Value.StrFormat): String {
    if (rrTarget is RR_FunctionCallTarget.FunctionValue && baseValue != null && baseValue != Rt_NullValue) {
        return baseValue.asFunction().str(format)
    }
    return rrTargetName()
}

fun Rt_FunctionCallTarget.targetStrCode(baseValue: Rt_Value?): String {
    if (rrTarget is RR_FunctionCallTarget.FunctionValue && baseValue != null && baseValue != Rt_NullValue) {
        return baseValue.asFunction().strCode()
    }
    return targetStr(baseValue, Rt_Value.StrFormat.V1)
}

private fun Rt_FunctionCallTarget.rrTargetName(): String = when (val rr = rrTarget) {
    is RR_FunctionCallTarget.RegularUser -> interpreter.rrApp.allFunctions[rr.fnDefIndex].base.appLevelName
    is RR_FunctionCallTarget.RegularQuery -> interpreter.rrApp.allQueries[rr.queryDefIndex].base.appLevelName
    is RR_FunctionCallTarget.AbstractUser -> interpreter.rrApp.allFunctions[rr.fnDefIndex].base.appLevelName
    is RR_FunctionCallTarget.AbstractOverride -> "abstract_override"
    is RR_FunctionCallTarget.Extendable -> "extendable[${rr.extendableUidId}]"
    is RR_FunctionCallTarget.SysGlobal -> sysFnDisplayName(rr.fnName)
    is RR_FunctionCallTarget.SysMember -> sysFnDisplayName(rr.fnName)
    is RR_FunctionCallTarget.Operation -> interpreter.rrApp.allOperations[rr.opDefIndex].base.appLevelName
    is RR_FunctionCallTarget.NativeUser -> rr.fullName.str()
    is RR_FunctionCallTarget.FunctionValue -> "function_value"
}

// =============================================================================
// R_SysFunctionUtils — moved from model/expr/r_expr_fn.kt
// =============================================================================

object R_SysFunctionUtils {
    fun call(callCtx: Rt_CallContext, fn: R_SysFunction, name: String, values: List<Rt_Value>): Rt_Value {
        return call(callCtx, fn, LazyString.of(name), values)
    }

    fun call(callCtx: Rt_CallContext, fn: R_SysFunction, nameMsg: LazyString?, values: List<Rt_Value>): Rt_Value {
        return if (nameMsg == null) {
            fn.call(callCtx, values)
        } else {
            callAndCatch(callCtx, fn, nameMsg, values)
        }
    }

    private fun callAndCatch(
        callCtx: Rt_CallContext,
        fn: R_SysFunction,
        name: LazyString,
        values: List<Rt_Value>,
    ): Rt_Value {
        return try {
            fn.call(callCtx, values)
        } catch (e: Rt_Exception) {
            throw if (e.info.extraMessage != null || e.err is Rt_RequireError) e else {
                val extra = "System function '${name.value}'"
                val info = Rt_ExceptionInfo(e.info.stack, extra)
                Rt_Exception(e.err, info, e)
            }
        } catch (e: RellInterpreterCrashException) {
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: Throwable) {
            if (e is java.sql.SQLException && e.isPostgresQueryCanceled) throw e
            if (callCtx.globalCtx.wrapFunctionCallErrors) {
                val extra = "System function '${name.value}'"
                val info = Rt_ExceptionInfo(stack = net.postchain.rell.base.utils.immListOf(), extraMessage = extra)
                val err = Rt_CommonError("fn:error:$name:${e.javaClass.canonicalName}", e.message ?: "error")
                throw Rt_Exception(err, info)
            } else {
                throw e
            }
        }
    }
}

// =============================================================================
// Rt_ExtendableFunctionCombiner — runtime combiner for extendable functions
// =============================================================================

sealed class Rt_ExtendableFunctionCombiner {
    abstract fun addExtensionResult(value: Rt_Value): Boolean
    abstract fun getCombinedResult(): Rt_Value
}

object Rt_ExtendableFunctionCombiner_Unit: Rt_ExtendableFunctionCombiner() {
    override fun addExtensionResult(value: Rt_Value) = false
    override fun getCombinedResult() = Rt_UnitValue
}

class Rt_ExtendableFunctionCombiner_Boolean: Rt_ExtendableFunctionCombiner() {
    private var result: Rt_Value = Rt_BooleanValue.FALSE

    override fun addExtensionResult(value: Rt_Value): Boolean {
        val v = value.asBoolean()
        result = value
        return v
    }

    override fun getCombinedResult() = result
}

class Rt_ExtendableFunctionCombiner_Nullable: Rt_ExtendableFunctionCombiner() {
    private var result: Rt_Value = Rt_NullValue

    override fun addExtensionResult(value: Rt_Value): Boolean {
        result = value
        return value != Rt_NullValue
    }

    override fun getCombinedResult() = result
}

class Rt_ExtendableFunctionCombiner_List(private val type: Rt_Type): Rt_ExtendableFunctionCombiner() {
    private val result = mutableListOf<Rt_Value>()
    private var done = false

    override fun addExtensionResult(value: Rt_Value): Boolean {
        check(!done)
        val col = value.asCollection()
        result.addAll(col)
        return false
    }

    override fun getCombinedResult(): Rt_Value {
        check(!done)
        done = true
        return Rt_ListValue(type, result)
    }
}

class Rt_ExtendableFunctionCombiner_Map(private val mapType: Rt_Type): Rt_ExtendableFunctionCombiner() {
    private val result = mutableMapOf<Rt_Value, Rt_Value>()
    private var done = false

    override fun addExtensionResult(value: Rt_Value): Boolean {
        check(!done)
        val map = value.asMap()
        for ((k, v) in map) {
            val v0 = result.put(k, v)
            if (v0 != null) {
                val code = "extendable_fn:map:key_conflict:${k.strCode()}:${v0.strCode()}:${v.strCode()}"
                val msg = "Map key conflict: ${k.str()}"
                throw Rt_Exception.common(code, msg)
            }
        }
        return false
    }

    override fun getCombinedResult(): Rt_Value {
        check(!done)
        done = true
        return Rt_MapValue(mapType, result)
    }
}

