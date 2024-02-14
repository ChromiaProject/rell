/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.lib.test.Rt_TestOpValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_FunctionValue
import net.postchain.rell.base.runtime.Rt_NullValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.checkEquals

abstract class R_FunctionCallTarget {
    abstract fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value

    open fun createFunctionValue(resType: R_Type, mapping: R_PartialCallMapping, baseValue: Rt_Value?, args: List<Rt_Value>): Rt_Value {
        return Rt_FunctionValue(resType, mapping, this, baseValue, args)
    }

    abstract fun str(): String
    open fun str(baseValue: Rt_Value?, format: Rt_Value.StrFormat) = str()
    open fun strCode(baseValue: Rt_Value?) = str()

    final override fun toString(): String {
        CommonUtils.failIfUnitTest()
        return str()
    }
}

class R_FunctionCallTarget_RegularUserFunction(
        private val fn: R_RoutineDefinition
): R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        checkEquals(baseValue, null)
        val res = fn.call(callCtx, values)
        return res
    }

    override fun str() = fn.appLevelName
}

class R_FunctionCallTarget_AbstractUserFunction(
        private val baseFn: R_FunctionDefinition,
        private val overrideGetter: C_LateGetter<R_FunctionBase>
): R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        checkEquals(baseValue, null)
        val overrideBaseFn = overrideGetter.get()
        val res = overrideBaseFn.call(callCtx, values)
        return res
    }

    override fun str() = baseFn.appLevelName
}

class R_FunctionCallTarget_Operation(
        private val op: R_OperationDefinition
): R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        checkEquals(baseValue, null)
        val gtvArgs = values.map { it.type().rtToGtv(it, false) }
        return Rt_TestOpValue(op.mountName, gtvArgs)
    }

    override fun str() = op.appLevelName
}

object R_FunctionCallTarget_FunctionValue: R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        val fnValue = getFnValue(baseValue)
        return fnValue.call(callCtx, values)
    }

    override fun createFunctionValue(resType: R_Type, mapping: R_PartialCallMapping, baseValue: Rt_Value?, args: List<Rt_Value>): Rt_Value {
        val fnValue = getFnValue(baseValue)
        return fnValue.combine(resType, mapping, args)
    }

    override fun str() = "function_value"
    override fun str(baseValue: Rt_Value?, format: Rt_Value.StrFormat) = getFnValue(baseValue).str(format)
    override fun strCode(baseValue: Rt_Value?) = getFnValue(baseValue).strCode()

    private fun getFnValue(baseValue: Rt_Value?): Rt_FunctionValue {
        checkNotNull(baseValue)
        check(baseValue != Rt_NullValue)
        return baseValue.asFunction()
    }
}

class R_FunctionCallTarget_SysGlobalFunction(
        private val fn: R_SysFunction,
        private val fullName: LazyString
): R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        checkEquals(baseValue, null)
        return R_SysFunctionUtils.call(callCtx, fn, fullName, values)
    }

    override fun str() = fullName.value
}

class R_FunctionCallTarget_SysMemberFunction(
        private val fn: R_SysFunction,
        private val fullName: LazyString
): R_FunctionCallTarget() {
    override fun call(callCtx: Rt_CallContext, baseValue: Rt_Value?, values: List<Rt_Value>): Rt_Value {
        checkNotNull(baseValue)
        val values2 = listOf(baseValue) + values
        return R_SysFunctionUtils.call(callCtx, fn, fullName, values2)
    }

    override fun str() = fullName.value
}
