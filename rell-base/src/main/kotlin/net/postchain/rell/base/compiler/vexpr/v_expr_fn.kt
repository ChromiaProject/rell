/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.core.C_VarId
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.*

class V_FunctionCallArgs(
    exprs: List<V_Expr>,
    paramsToExprs: List<Int>,
    exprsToParams: List<Int>,
) {
    val exprs = exprs.toImmList()
    val paramsToExprs = paramsToExprs.toImmList()
    val exprsToParams = exprsToParams.toImmList()

    init {
        checkEquals(this.paramsToExprs.size, this.exprs.size)
        checkEquals(this.exprsToParams.size, this.exprs.size)
        for (i in exprs.indices) {
            checkEquals(this.paramsToExprs[this.exprsToParams[i]], i)
            checkEquals(this.exprsToParams[this.paramsToExprs[i]], i)
        }
    }

    companion object {
        val EMPTY = V_FunctionCallArgs(immListOf(), immListOf(), immListOf())

        fun positional(args: List<V_Expr>): V_FunctionCallArgs {
            val mapping = args.indices.toImmList()
            return V_FunctionCallArgs(args, mapping, mapping)
        }
    }
}

class V_FunctionCallExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val base: V_Expr?,
    private val call: V_CommonFunctionCall,
    private val safe: Boolean,
    varId: C_VarId? = null,
): V_Expr(exprCtx, pos) {
    private val allExprs = (listOfNotNull(base) + call.args).toImmList()
    private val actualType = C_Utils.effectiveMemberType(call.returnType, safe)
    private val varKey = if (varId == null) null else C_VarStateKey(varId)

    override fun exprInfo0() = V_ExprInfo.simple(
        actualType,
        allExprs,
        canBeDbExpr = call.canBeDbExpr()
    )

    override fun varStatesDelta0(): C_ExprVarStatesDelta {
        var resDelta = call.varStatesDelta()
        if (base != null) {
            resDelta = resDelta.and(base.varStatesDelta.always)
        }
        return C_ExprVarStatesDelta.make(always = resDelta)
    }

    override fun globalConstantRestriction() = call.globalConstantRestriction()

    override fun toRExpr0(): R_Expr {
        val rBase = base?.toRExpr()
        val rCall = call.rCall()
        return R_FunctionCallExpr(actualType, rBase, rCall, safe)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbBase = base?.toDbExpr()
        return call.dbExpr(dbBase)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        return call.dbExprWhat(base, safe)
    }

    override fun varKey() = varKey
}

sealed class V_CommonFunctionCall(
    protected val pos: S_Pos,
    val returnType: R_Type,
    protected val target: V_FunctionCallTarget,
    args: List<V_Expr>,
) {
    val args = args.toImmList()

    abstract fun varStatesDelta(): C_VarStatesDelta
    abstract fun globalConstantRestriction(): V_GlobalConstantRestriction?

    fun canBeDbExpr() = target.canBeDb()

    protected abstract fun rCall0(rTarget: R_FunctionCallTarget, rArgExprs: List<R_Expr>): R_FunctionCall

    protected abstract fun callTarget(
        callCtx: Rt_CallContext,
        rTarget: R_FunctionCallTarget,
        baseValue: Rt_Value?,
        argValues: List<Rt_Value>
    ): Rt_Value

    fun rCall(): R_FunctionCall {
        val rTarget = target.toRTarget()
        val rArgExprs = args.map { it.toRExpr() }
        return rCall0(rTarget, rArgExprs)
    }

    open fun dbExpr(dbBase: Db_Expr?): Db_Expr = throw C_Errors.errExprDbNotAllowed(pos)

    fun dbExprWhat(base: V_Expr?, safe: Boolean): C_DbAtWhatValue {
        val rTarget = target.toRTarget()
        val baseCount = if (base == null) 0 else 1

        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                val baseValue = if (baseCount == 0) null else values[0].value()
                if (safe && baseValue == Rt_NullValue) {
                    return Rt_NullValue
                }
                val argValues = values.subList(baseCount, values.size).map { it.value() }
                val callCtx = frame.callCtx()
                return callTarget(callCtx, rTarget, baseValue, argValues)
            }
        }

        val allExprs = listOfNotNull(base) + args
        return C_DbAtWhatValue_Complex(allExprs, evaluator)
    }
}

class V_CommonFunctionCall_Full(
    pos: S_Pos,
    callPos: S_Pos,
    returnType: R_Type,
    target: V_FunctionCallTarget,
    private val callArgs: V_FunctionCallArgs,
    private val argsVarStatesDelta: C_VarStatesDelta = C_VarStatesDelta.forExpressions(callArgs.exprs)
): V_CommonFunctionCall(pos, returnType, target, callArgs.exprs) {
    private val callFilePos = callPos.toFilePos()

    override fun varStatesDelta() = argsVarStatesDelta
    override fun globalConstantRestriction() = target.globalConstantRestriction()

    override fun callTarget(
        callCtx: Rt_CallContext,
        rTarget: R_FunctionCallTarget,
        baseValue: Rt_Value?,
        argValues: List<Rt_Value>,
    ): Rt_Value {
        val values2 = callArgs.paramsToExprs.map { argValues[it] }
        val subCallCtx = callCtx.subContext(callFilePos)
        return rTarget.call(subCallCtx, baseValue, values2)
    }

    override fun rCall0(rTarget: R_FunctionCallTarget, rArgExprs: List<R_Expr>): R_FunctionCall {
        return R_FullFunctionCall(returnType, rTarget, callFilePos, rArgExprs, callArgs.paramsToExprs)
    }

    override fun dbExpr(dbBase: Db_Expr?): Db_Expr {
        val dbArgs = callArgs.exprs.map { it.toDbExpr() }
        return target.toDbExpr(pos, dbBase, dbArgs)
    }
}

class V_CommonFunctionCall_Partial(
    pos: S_Pos,
    returnType: R_Type,
    target: V_FunctionCallTarget,
    args: List<V_Expr>,
    private val mapping: R_PartialCallMapping,
): V_CommonFunctionCall(pos, returnType, target, args) {
    override fun varStatesDelta(): C_VarStatesDelta = C_VarStatesDelta.forExpressions(args)
    override fun globalConstantRestriction() = V_GlobalConstantRestriction("partial_call", null)

    override fun rCall0(rTarget: R_FunctionCallTarget, rArgExprs: List<R_Expr>): R_FunctionCall {
        return R_PartialFunctionCall(returnType, rTarget, mapping, rArgExprs)
    }

    override fun callTarget(
        callCtx: Rt_CallContext,
        rTarget: R_FunctionCallTarget,
        baseValue: Rt_Value?,
        argValues: List<Rt_Value>,
    ): Rt_Value {
        return rTarget.createFunctionValue(returnType, mapping, baseValue, argValues)
    }
}

sealed class V_FunctionCallTarget {
    abstract fun toRTarget(): R_FunctionCallTarget
    open fun canBeDb() = false
    open fun toDbExpr(pos: S_Pos, dbBase: Db_Expr?, dbArgs: List<Db_Expr>): Db_Expr = throw C_Errors.errExprDbNotAllowed(pos)
    open fun globalConstantRestriction(): V_GlobalConstantRestriction? = null
}

class V_FunctionCallTarget_RegularUserFunction(
        private val fn: R_RoutineDefinition
): V_FunctionCallTarget() {
    override fun toRTarget(): R_FunctionCallTarget = R_FunctionCallTarget_RegularUserFunction(fn)
    override fun globalConstantRestriction() = V_GlobalConstantRestriction("fn:${fn.appLevelName}", "user function call")
}

class V_FunctionCallTarget_AbstractUserFunction(
        private val baseFn: R_FunctionDefinition,
        private val overrideGetter: C_LateGetter<R_FunctionBase>
): V_FunctionCallTarget() {
    override fun toRTarget(): R_FunctionCallTarget {
        return R_FunctionCallTarget_AbstractUserFunction(baseFn, overrideGetter)
    }

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("fn:${baseFn.appLevelName}", "user function call")
}

class V_FunctionCallTarget_ExtendableUserFunction(
        private val baseFn: R_FunctionDefinition,
        private val descriptor: R_ExtendableFunctionDescriptor
): V_FunctionCallTarget() {
    override fun toRTarget(): R_FunctionCallTarget {
        return R_FunctionCallTarget_ExtendableUserFunction(baseFn, descriptor)
    }

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("fn:${baseFn.appLevelName}", "user function call")
}

class V_FunctionCallTarget_Operation(
        private val op: R_OperationDefinition
): V_FunctionCallTarget() {
    override fun toRTarget(): R_FunctionCallTarget = R_FunctionCallTarget_Operation(op)
    override fun globalConstantRestriction() = V_GlobalConstantRestriction("op:${op.appLevelName}", "operation call")
}

object V_FunctionCallTarget_FunctionValue: V_FunctionCallTarget() {
    override fun toRTarget(): R_FunctionCallTarget {
        return R_FunctionCallTarget_FunctionValue
    }

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("fn_value_call", "user function call")
}

class V_SysFunctionTargetDescriptor(
    val resType: R_Type,
    val rFn: R_SysFunction,
    val dbFn: Db_SysFunction?,
    val fullName: LazyString,
    pure: Boolean,
    val synth: Boolean = false,
) {
    val pure: Boolean = pure && resType.completeFlags().pure
}

abstract class V_FunctionCallTarget_SysFunction(
    protected val desc: V_SysFunctionTargetDescriptor,
): V_FunctionCallTarget() {
    final override fun canBeDb() = desc.dbFn != null

    final override fun globalConstantRestriction(): V_GlobalConstantRestriction? {
        return if (desc.pure) null else {
            val name = desc.fullName.value
            val code = if (desc.synth) "fn:prop:[$name]" else "fn:sys:[$name]"
            val msg = if (desc.synth) null else "function '$name'"
            V_GlobalConstantRestriction(code, msg)
        }
    }
}

class V_FunctionCallTarget_SysGlobalFunction(
    desc: V_SysFunctionTargetDescriptor,
): V_FunctionCallTarget_SysFunction(desc) {
    override fun toRTarget(): R_FunctionCallTarget = R_FunctionCallTarget_SysGlobalFunction(desc.rFn, desc.fullName)

    override fun toDbExpr(pos: S_Pos, dbBase: Db_Expr?, dbArgs: List<Db_Expr>): Db_Expr {
        checkEquals(dbBase, null)
        if (desc.dbFn == null) {
            throw C_Errors.errFunctionNoSql(pos, desc.fullName.value)
        }
        return Db_CallExpr(desc.resType, desc.dbFn, dbArgs)
    }
}

abstract class V_FunctionCall(
    val argIdeInfos: Map<R_Name, C_IdeSymbolInfo>,
)

class V_GlobalFunctionCall(
    private val expr: V_Expr,
    val ideInfo: C_IdeSymbolInfo? = null,
    argIdeInfos: Map<R_Name, C_IdeSymbolInfo>,
): V_FunctionCall(argIdeInfos) {
    fun vExpr() = expr
}

abstract class V_MemberFunctionCall(
    protected val exprCtx: C_ExprContext,
    val ideInfo: C_IdeSymbolInfo,
    paramIdeInfos: Map<R_Name, C_IdeSymbolInfo>,
): V_FunctionCall(paramIdeInfos) {
    abstract fun vExprs(): List<V_Expr>
    open fun varStatesDelta(): C_VarStatesDelta = C_VarStatesDelta.forExpressions(vExprs())
    open fun globalConstantRestriction(): V_GlobalConstantRestriction? = null

    abstract fun returnType(): R_Type
    abstract fun calculator(): R_MemberCalculator

    open fun canBeDbExpr(): Boolean = false
    open fun dbExpr(base: Db_Expr): Db_Expr? = null
    open fun dbExprWhat(base: V_Expr, safe: Boolean): C_DbAtWhatValue? = null
}

class V_MemberFunctionCall_CommonCall(
    exprCtx: C_ExprContext,
    ideInfo: C_IdeSymbolInfo,
    argIdeInfos: Map<R_Name, C_IdeSymbolInfo>,
    private val call: V_CommonFunctionCall,
    private val returnType: R_Type,
): V_MemberFunctionCall(exprCtx, ideInfo, argIdeInfos) {
    override fun vExprs() = call.args
    override fun varStatesDelta() = call.varStatesDelta()
    override fun globalConstantRestriction() = call.globalConstantRestriction()
    override fun returnType() = returnType

    override fun calculator(): R_MemberCalculator {
        val rCall = call.rCall()
        return R_MemberCalculator_CommonCall(returnType, rCall)
    }

    override fun canBeDbExpr(): Boolean = call.canBeDbExpr()
    override fun dbExpr(base: Db_Expr) = call.dbExpr(base)
    override fun dbExprWhat(base: V_Expr, safe: Boolean) = call.dbExprWhat(base, safe)

    private class R_MemberCalculator_CommonCall(type: R_Type, private val call: R_FunctionCall): R_MemberCalculator(type) {
        override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
            return call.evaluate(frame, baseValue)
        }
    }
}

class V_MemberFunctionCall_Error(
    exprCtx: C_ExprContext,
    ideInfo: C_IdeSymbolInfo,
    private val returnType: R_Type = R_CtErrorType,
    private val msg: String = "Compilation error",
): V_MemberFunctionCall(exprCtx, ideInfo, immMapOf()) {
    override fun vExprs() = immListOf<V_Expr>()
    override fun returnType() = returnType
    override fun calculator(): R_MemberCalculator = R_MemberCalculator_Error(returnType, msg)
    override fun canBeDbExpr() = true
    override fun dbExpr(base: Db_Expr) = C_ExprUtils.errorDbExpr(returnType, msg)
}
