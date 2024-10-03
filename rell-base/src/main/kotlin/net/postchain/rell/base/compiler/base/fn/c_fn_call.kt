/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.fn

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.expr.C_CallTypeHints
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_ParameterDefaultValue
import net.postchain.rell.base.compiler.base.utils.C_Utils
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_FunctionType
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.utils.*

abstract class C_FunctionCallTargetInfo {
    abstract fun retType(): R_Type?
    abstract fun typeHints(): C_CallTypeHints
}

abstract class C_FunctionCallTarget: C_FunctionCallTargetInfo() {
    abstract fun compileFull(args: C_FullCallArguments, resTypeHint: C_TypeHint): V_GlobalFunctionCall?
    abstract fun compilePartial(args: C_PartialCallArguments, resTypeHint: R_FunctionType?): V_GlobalFunctionCall?
}

class C_FunctionCallTargetBase(
    val ctx: C_ExprContext,
    val callInfo: C_FunctionCallInfo,
    val callParams: C_FunctionCallParameters,
    val argIdeInfos: Map<R_Name, C_IdeSymbolInfo>,
) {
    companion object {
        fun forDirectFunction(
            ctx: C_ExprContext,
            name: LazyPosString,
            params: C_FormalParameters,
        ): C_FunctionCallTargetBase {
            val callInfo = C_FunctionCallInfo(name.pos, name.lazyStr)
            return C_FunctionCallTargetBase(ctx, callInfo, params.callParameters, params.argIdeInfos)
        }

        fun forFunctionType(ctx: C_ExprContext, callPos: S_Pos, fnType: R_FunctionType): C_FunctionCallTargetBase {
            val callInfo = C_FunctionCallInfo(callPos, null)
            return C_FunctionCallTargetBase(ctx, callInfo, fnType.callParameters, immMapOf())
        }
    }
}

abstract class C_FunctionCallTarget_Regular(
    private val targetBase: C_FunctionCallTargetBase,
    private val retType: R_Type?,
): C_FunctionCallTarget() {
    private val ctx = targetBase.ctx
    private val callInfo = targetBase.callInfo
    private val callParams = targetBase.callParams

    protected open fun vBase(): V_Expr? = null
    protected open fun safe() = false
    protected abstract fun createVTarget(): V_FunctionCallTarget

    final override fun retType() = retType
    final override fun typeHints() = callParams.typeHints

    final override fun compileFull(args: C_FullCallArguments, resTypeHint: C_TypeHint): V_GlobalFunctionCall? {
        retType ?: return null
        val vBase = vBase()
        val vTarget = createVTarget()

        val vCallArgs = args.compileComplexArgs(callInfo, callParams)
        vCallArgs ?: return null

        val safe = safe()
        val vCall = V_CommonFunctionCall_Full(callInfo.callPos, callInfo.callPos, retType, vTarget, vCallArgs)
        val vExpr = V_FunctionCallExpr(ctx, callInfo.callPos, vBase, vCall, safe)
        return V_GlobalFunctionCall(vExpr, null, targetBase.argIdeInfos)
    }

    final override fun compilePartial(
        args: C_PartialCallArguments,
        resTypeHint: R_FunctionType?,
    ): V_GlobalFunctionCall? {
        val vBase = vBase()
        val effArgs = args.compileEffectiveArgs(callInfo, callParams)
        effArgs ?: return null

        val fnType = R_FunctionType(effArgs.wildArgs, retType ?: R_CtErrorType)
        val vTarget = createVTarget()
        val mapping = effArgs.toRMapping(ctx.msgCtx)

        val safe = safe()
        val vCall = V_CommonFunctionCall_Partial(callInfo.callPos, fnType, vTarget, effArgs.exprArgs, mapping)
        val vExpr = V_FunctionCallExpr(ctx, callInfo.callPos, vBase, vCall, safe)
        return V_GlobalFunctionCall(vExpr, null, targetBase.argIdeInfos)
    }
}

class C_FunctionCallTarget_FunctionType(
    base: C_FunctionCallTargetBase,
    private val fnExpr: V_Expr,
    fnType: R_FunctionType,
    private val safe: Boolean
): C_FunctionCallTarget_Regular(base, C_Utils.effectiveMemberType(fnType.result, safe)) {
    override fun vBase() = fnExpr
    override fun safe() = safe
    override fun createVTarget(): V_FunctionCallTarget = V_FunctionCallTarget_FunctionValue
}

class C_FunctionCallInfo(
    val callPos: S_Pos,
    val functionName: LazyString?,
) {
    fun functionNameCode() = functionName?.value ?: "?"
}

class C_FunctionCallParameters(list: List<C_FunctionCallParameter>) {
    val list = list.toImmList()
    val typeHints: C_CallTypeHints = C_FunctionCallParametersTypeHints(this.list)

    val bindParams: C_ArgMatchParams = let {
        val params = list.map { C_ArgMatchParam(it.index, it.name, M_ParamArity.ONE, it.defaultValue) }
        C_ArgMatchParams(params)
    }

    companion object {
        fun fromTypes(types: List<R_Type>): C_FunctionCallParameters {
            val params = types.mapIndexed { index, rType ->
                C_FunctionCallParameter(null, rType, index, null, C_MemberRestrictions.NULL)
            }
            return C_FunctionCallParameters(params)
        }
    }
}

class C_FunctionCallParameter(
    val name: R_Name?,
    val type: R_Type,
    val index: Int,
    val defaultValue: C_ParameterDefaultValue?,
    val restrictions: C_MemberRestrictions,
) {
    fun nameCodeMsg(): C_CodeMsg {
        val code = if (name != null) "$index:$name" else "$index"
        val msg = if (name != null) "'$name'" else "${index+1}"
        return C_CodeMsg(code, msg)
    }
}

private class C_FunctionCallParametersTypeHints(params: List<C_FunctionCallParameter>): C_CallTypeHints {
    private val list = params.toImmList()
    private val map = params.filter { it.name != null }.map { it.name!! to it }.toImmMap()

    override fun getTypeHint(index: Int?, name: R_Name?): C_TypeHint {
        val byName = if (name != null) map[name] else null
        val byIndex = if (index != null && index >= 0 && index < list.size) list[index] else null
        val param = byName ?: byIndex
        return C_TypeHint.ofType(param?.type)
    }
}
