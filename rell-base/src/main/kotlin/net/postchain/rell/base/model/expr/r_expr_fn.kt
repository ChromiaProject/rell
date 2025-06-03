/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.R_FilePos
import net.postchain.rell.base.model.R_SysFunction
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.RellInterpreterCrashException
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf

object R_SysFunctionUtils {
    fun call(callCtx: Rt_CallContext, fn: R_SysFunction, nameMsg: LazyString?, values: List<Rt_Value>): Rt_Value {
        return if (nameMsg == null) {
            call0(callCtx, fn, values)
        } else {
            callAndCatch(callCtx, fn, nameMsg, values)
        }
    }

    private fun call0(callCtx: Rt_CallContext, fn: R_SysFunction, values: List<Rt_Value>): Rt_Value {
        val res = fn.call(callCtx, values)
        return res
    }

    private fun callAndCatch(
        callCtx: Rt_CallContext,
        fn: R_SysFunction,
        name: LazyString,
        values: List<Rt_Value>,
    ): Rt_Value {
        val res = try {
            call0(callCtx, fn, values)
        } catch (e: Rt_Exception) {
            throw if (e.info.extraMessage != null || e.err is Rt_RequireError) e else {
                val extra  = extraMessage(name.value)
                val info = Rt_ExceptionInfo(e.info.stack, extra)
                Rt_Exception(e.err, info, e)
            }
        } catch (e: RellInterpreterCrashException) {
            throw e
        } catch (e: Throwable) {
            if (callCtx.globalCtx.wrapFunctionCallErrors) {
                val extra = extraMessage(name.value)
                val info = Rt_ExceptionInfo(stack = immListOf(), extraMessage = extra)
                val err = Rt_CommonError("fn:error:$name:${e.javaClass.canonicalName}", e.message ?: "error")
                throw Rt_Exception(err, info)
            } else {
                throw e
            }
        }
        return res
    }

    private fun extraMessage(name: String) = "System function '$name'"
}

abstract class R_FunctionCall(val returnType: R_Type) {
    abstract fun evaluate(frame: Rt_CallFrame, baseValue: Rt_Value?): Rt_Value
}

class R_FullFunctionCall(
    returnType: R_Type,
    private val target: R_FunctionCallTarget,
    private val callPos: R_FilePos,
    private val args: ImmList<R_Expr>,
    private val mapping: ImmList<Int>,
): R_FunctionCall(returnType) {
    init {
        checkEquals(this.mapping.sorted(), this.args.indices.toList())
    }

    override fun evaluate(frame: Rt_CallFrame, baseValue: Rt_Value?): Rt_Value {
        val values = args.map { it.evaluate(frame) }
        val values2 = mapping.map { values[it] }
        val callCtx = frame.callCtx(callPos)
        val res = target.call(callCtx, baseValue, values2)
        return res
    }
}

class R_PartialArgMapping(val wild: Boolean, val index: Int)

class R_PartialCallMapping(val exprCount: Int, val wildCount: Int, val args: ImmList<R_PartialArgMapping>) {
    init {
        check(exprCount >= 0)
        check(wildCount >= 0)
        checkEquals(this.args.size, exprCount + wildCount)
        checkEquals(this.args.filter { it.wild }.map { it.index }.sorted().toList(), (0 until wildCount).toList())
        checkEquals(this.args.filter { !it.wild }.map { it.index }.sorted().toList(), (0 until exprCount).toList())
    }
}

class R_PartialFunctionCall(
    returnType: R_Type,
    private val target: R_FunctionCallTarget,
    private val mapping: R_PartialCallMapping,
    private val args: ImmList<R_Expr>,
): R_FunctionCall(returnType) {
    init {
        checkEquals(this.args.size, mapping.exprCount)
    }

    override fun evaluate(frame: Rt_CallFrame, baseValue: Rt_Value?): Rt_Value {
        val values = args.map { it.evaluate(frame) }
        return target.createFunctionValue(returnType, mapping, baseValue, values)
    }
}

class R_FunctionCallExpr(
    type: R_Type,
    private val base: R_Expr?,
    private val call: R_FunctionCall,
    private val safe: Boolean,
): R_Expr(type) {
    override fun evaluate0(frame: Rt_CallFrame): Rt_Value {
        val baseValue = base?.evaluate(frame)
        return if (safe && baseValue == Rt_NullValue) {
            Rt_NullValue
        } else {
            call.evaluate(frame, baseValue)
        }
    }
}
