/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.test.Rt_TestOpValue
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lib.type.Rt_MapValue
import net.postchain.rell.base.lib.type.Rt_UnitValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.rr.RR_FunctionBase
import net.postchain.rell.base.model.rr.RR_FunctionCallTarget
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.runtime.utils.isPostgresQueryCanceled
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.checkNull
import net.postchain.rell.base.utils.mapIndexedToImmList

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

/**
 * Runtime dispatch for R_FunctionCallTarget — pattern-matching replacement
 * for the old virtual call() method on R_FunctionCallTarget subclasses.
 */
fun callFunctionTarget(
    target: R_FunctionCallTarget,
    callCtx: Rt_CallContext,
    baseValue: Rt_Value?,
    values: List<Rt_Value>,
): Rt_Value = when (target) {
    is R_FunctionCallTarget_RegularUserFunction -> {
        checkNull(baseValue)
        callRoutineDefinition(target.fn, callCtx, values)
    }

    is R_FunctionCallTarget_AbstractUserFunction -> {
        error("R_FunctionCallTarget_AbstractUserFunction dispatch removed")
    }

    is R_FunctionCallTarget_NativeUserFunction -> {
        checkNull(baseValue)
        val fn = callCtx.appCtx.nativeFunctions.getValue(target.fnName)
        val nativeArgs = values.mapIndexedToImmList { i, value ->
            rTypeToRtType(target.argTypes[i]).nativeConversion!!.rtToNative(value)
        }
        val nativeRes = fn.call(nativeArgs)
        rTypeToRtType(target.resultType).nativeConversion!!.nativeToRt(nativeRes)
    }

    is R_FunctionCallTarget_Operation -> {
        checkNull(baseValue)
        val params = target.op.params()
        val gtvArgs = values.mapIndexedToImmList { i, arg ->
            params[i].validator?.check(arg)?.raise()
            checkNotNull(arg.type().gtvConversion) { "No GTV conversion for ${arg.type().name}" }
                .rtToGtv(arg, false)
        }
        Rt_TestOpValue(target.op.mountName, gtvArgs)
    }

    is R_FunctionCallTarget_FunctionValue -> {
        val fnValue = getFnValueFromBase(baseValue)
        fnValue.call(callCtx, values)
    }

    is R_FunctionCallTarget_SysGlobalFunction -> {
        checkNull(baseValue)
        val fn = target.fn as R_SysFunction
        R_SysFunctionUtils.call(callCtx, fn, target.fullName, values)
    }

    is R_FunctionCallTarget_SysMemberFunction -> {
        checkNotNull(baseValue)
        val fn = target.fn as R_SysFunction
        val values2 = listOf(baseValue) + values
        R_SysFunctionUtils.call(callCtx, fn, target.fullName, values2)
    }

    is R_FunctionCallTarget_ExtendableUserFunction -> {
        checkNull(baseValue)
        val interpreter = callCtx.appCtx.interpreter
        val extensions = interpreter.rrApp.functionExtensions[target.descriptor.uid.id].extensions
        val rtCombiner = createExtendableCombiner(target.descriptor.combiner)
        for (rrFnBase in extensions) {
            val value = interpreter.callFunctionBase(rrFnBase, callCtx, values)
            if (rtCombiner.addExtensionResult(value)) break
        }
        rtCombiner.getCombinedResult()
    }

    is Rt_FunctionCallTargetAdapter -> {
        target.interpreter.callTarget(target.rrTarget, baseValue, values, target.outerFrame)
    }

    else -> throw IllegalArgumentException("Unknown R_FunctionCallTarget: ${target::class.simpleName}")
}

fun createFunctionValueFromTarget(
    target: R_FunctionCallTarget,
    resType: Rt_Type,
    mapping: R_PartialCallMapping,
    baseValue: Rt_Value?,
    args: List<Rt_Value>,
): Rt_Value {
    // FunctionValue targets combine with existing function values
    if (target is R_FunctionCallTarget_FunctionValue && baseValue != null && baseValue != Rt_NullValue) {
        return baseValue.asFunction().combine(resType, mapping, args)
    }
    // RR adapter: check for FunctionValue variant
    if (target is Rt_FunctionCallTargetAdapter && target.rrTarget is RR_FunctionCallTarget.FunctionValue
        && baseValue != null && baseValue != Rt_NullValue
    ) {
        return baseValue.asFunction().combine(resType, mapping, args)
    }
    return Rt_FunctionValue(resType, mapping, target, baseValue, args)
}

fun functionTargetStr(target: R_FunctionCallTarget, baseValue: Rt_Value?, format: Rt_Value.StrFormat): String =
    when (target) {
        is R_FunctionCallTarget_RegularUserFunction -> target.fn.appLevelName
        is R_FunctionCallTarget_AbstractUserFunction -> target.baseFn.appLevelName
        is R_FunctionCallTarget_NativeUserFunction -> target.fnName.str()
        is R_FunctionCallTarget_Operation -> target.op.appLevelName
        is R_FunctionCallTarget_FunctionValue -> getFnValueFromBase(baseValue).str(format)
        is R_FunctionCallTarget_SysGlobalFunction -> target.fullName.value
        is R_FunctionCallTarget_SysMemberFunction -> target.fullName.value
        is R_FunctionCallTarget_ExtendableUserFunction -> target.baseFn.appLevelName
        is Rt_FunctionCallTargetAdapter -> {
            if (target.rrTarget is RR_FunctionCallTarget.FunctionValue && baseValue != null && baseValue != Rt_NullValue) {
                baseValue.asFunction().str(format)
            } else {
                rrFunctionCallTargetStr(target)
            }
        }

        else -> target::class.simpleName ?: "unknown"
    }

private fun rrFunctionCallTargetStr(target: Rt_FunctionCallTargetAdapter): String = when (val rr = target.rrTarget) {
    is RR_FunctionCallTarget.RegularUser -> target.interpreter.rrApp.allFunctions[rr.fnDefIndex].base.appLevelName
    is RR_FunctionCallTarget.RegularQuery -> target.interpreter.rrApp.allQueries[rr.queryDefIndex].base.appLevelName
    is RR_FunctionCallTarget.AbstractUser -> target.interpreter.rrApp.allFunctions[rr.fnDefIndex].base.appLevelName
    is RR_FunctionCallTarget.AbstractOverride -> "abstract_override"
    is RR_FunctionCallTarget.Extendable -> "extendable[${rr.extendableUidId}]"
    is RR_FunctionCallTarget.SysGlobal -> sysFnDisplayName(rr.fnName)
    is RR_FunctionCallTarget.SysMember -> sysFnDisplayName(rr.fnName)
    is RR_FunctionCallTarget.Operation -> target.interpreter.rrApp.allOperations[rr.opDefIndex].base.appLevelName
    is RR_FunctionCallTarget.NativeUser -> rr.fullName.str()
    is RR_FunctionCallTarget.FunctionValue -> "function_value"
}

fun functionTargetStrCode(target: R_FunctionCallTarget, baseValue: Rt_Value?): String = when (target) {
    is R_FunctionCallTarget_FunctionValue -> getFnValueFromBase(baseValue).strCode()
    is Rt_FunctionCallTargetAdapter -> {
        if (target.rrTarget is RR_FunctionCallTarget.FunctionValue && baseValue != null && baseValue != Rt_NullValue) {
            baseValue.asFunction().strCode()
        } else {
            functionTargetStr(target, baseValue, Rt_Value.StrFormat.V1)
        }
    }

    else -> functionTargetStr(target, baseValue, Rt_Value.StrFormat.V1)
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

private fun getFnValueFromBase(baseValue: Rt_Value?): Rt_FunctionValue {
    checkNotNull(baseValue)
    check(baseValue != Rt_NullValue)
    return baseValue.asFunction()
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

fun createExtendableCombiner(combiner: R_ExtendableFunctionCombiner): Rt_ExtendableFunctionCombiner = when (combiner) {
    is R_ExtendableFunctionCombiner_Unit -> Rt_ExtendableFunctionCombiner_Unit
    is R_ExtendableFunctionCombiner_Boolean -> Rt_ExtendableFunctionCombiner_Boolean()
    is R_ExtendableFunctionCombiner_Nullable -> Rt_ExtendableFunctionCombiner_Nullable()
    is R_ExtendableFunctionCombiner_List -> Rt_ExtendableFunctionCombiner_List(rTypeToRtType(combiner.type))
    is R_ExtendableFunctionCombiner_Map -> Rt_ExtendableFunctionCombiner_Map(rTypeToRtType(combiner.mapType))
}

// =============================================================================
// Runtime dispatch for R_ model call() methods — moved from model/r_def_fn.kt
// =============================================================================

/** Dispatches a call on [R_RoutineDefinition] by concrete type. */
fun callRoutineDefinition(routine: R_RoutineDefinition, callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value =
    when (routine) {
        is R_FunctionDefinition -> error("R_FunctionDefinition dispatch removed: ${routine.appLevelName}")
        is R_OperationDefinition -> throw Rt_Exception.common("call:operation", "Calling operation is not allowed")
        is R_QueryDefinition -> callQueryDefinition(routine, callCtx, args)
    }

/** Calls an [RR_FunctionBase] via the RR interpreter. */
fun Rt_Interpreter.callFunctionBase(fnBase: RR_FunctionBase, callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
    val frame = createFrame(
        callCtx.defCtx.exeCtx,
        fnBase.frame,
        dbUpdateAllowed = callCtx.dbUpdateAllowed(),
        fnBase.defId,
    )
    for (i in fnBase.paramVars.indices) {
        frame.setUnchecked(fnBase.paramVars[i].ptr, args[i], false)
    }
    val result = executeStmt(fnBase.body, frame)
    return if (result is R_StatementResult_Return) result.value ?: Rt_UnitValue else Rt_UnitValue
}

/** Calls an [R_QueryDefinition] via its body. */
private fun callQueryDefinition(query: R_QueryDefinition, callCtx: Rt_CallContext, args: List<Rt_Value>): Rt_Value {
    val body = query.bodyLate.get()
    checkCallArgs(query.appLevelName, body.params, args)
    val subDefCtx = Rt_DefinitionContext(callCtx.defCtx.exeCtx, false, query.defId)
    return callQueryBody(body, subDefCtx, args)
}

/** Calls an [R_QueryBody] by concrete type. */
private fun callQueryBody(body: R_QueryBody, defCtx: Rt_DefinitionContext, args: List<Rt_Value>): Rt_Value =
    when (body) {
        is R_UserQueryBody -> throw UnsupportedOperationException("Use RR interpreter; R_Statement.execute() removed")
        is R_SysQueryBody -> {
            val callCtx = Rt_CallContext(defCtx, dbUpdateAllowed = false)
            (body.fn as R_SysFunction).call(callCtx, args)
        }
    }

private fun checkCallArgs(name: String, params: List<R_FunctionParam>, args: List<Rt_Value>) {
    if (args.size != params.size) {
        throw Rt_Exception.common(
            "fn_wrong_arg_count:$name:${params.size}:${args.size}",
            "Wrong number of arguments for '$name': ${args.size} instead of ${params.size}",
        )
    }
}
