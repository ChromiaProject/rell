/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.fn

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallArgs
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// public part

class C_EffectivePartialArguments(
    private val callParams: C_FunctionCallParameters,
    val exprArgs: ImmList<V_Expr>,
    val wildArgs: ImmList<R_Type>,
    private val mapping: ImmList<C_ArgMatchParamArg>,
) {
    fun toRMapping(msgCtx: C_MessageContext): R_PartialCallMapping {
        C_FunctionUtils.checkParamRestrictions(msgCtx, mapping) { param ->
            callParams.list[param.index].restrictions
        }

        val combinedArgs = mapping.mapToImmList { R_PartialArgMapping(it.wild, it.index) }
        return R_PartialCallMapping(exprArgs.size, wildArgs.size, combinedArgs)
    }
}

sealed class C_AbstractCallArguments(private val argHands: ImmList<C_CallArgumentHandle>) {
    fun setArgIdeInfos(ideInfos: Map<R_Name, C_IdeSymbolInfo>) {
        for (arg in argHands) {
            arg.nameHand?.setIdeInfo(ideInfos[arg.nameHand.rName] ?: C_IdeSymbolInfo.UNKNOWN)
        }
    }
}

sealed class C_FullCallArguments(
    protected val ctx: C_ExprContext,
    val rawArgs: C_CallArguments,
    argHands: ImmList<C_CallArgumentHandle>,
): C_AbstractCallArguments(argHands) {
    abstract fun compileSimpleArgs(functionName: LazyString): ImmList<V_Expr>

    abstract fun compileComplexArgs(
        callInfo: C_FunctionCallInfo,
        callParams: C_FunctionCallParameters,
    ): V_FunctionCallArgs?
}

sealed class C_PartialCallArguments(
    protected val ctx: C_ExprContext,
    val wildcardPos: S_Pos,
    rawArgs: ImmList<C_CallArgumentHandle>,
    val firstNamedArg: C_Name?,
): C_AbstractCallArguments(rawArgs) {
    abstract fun compileEffectiveArgs(
        callInfo: C_FunctionCallInfo,
        callParams: C_FunctionCallParameters,
    ): C_EffectivePartialArguments?

    abstract fun errPartialNotSupportedFn(functionName: String)
    abstract fun errPartialNotSupportedCase(fnCase: C_CodeMsg)
}

object C_FunctionCallArgsUtils {
    fun makeCallArguments(msgCtx: C_MessageContext, args: ImmList<C_CallArgument>): C_CallArguments? {
        val positional = mutableListOf<C_CallArgument>()
        val namedNames = mutableSetOf<R_Name>()
        val named = mutableListOf<C_NameValue<C_CallArgument>>()

        val errPositionalAfterNamed = msgCtx.firstErrorReporter()
        var error = false

        for (arg in args) {
            if (arg.name == null) {
                if (named.isNotEmpty()) {
                    errPositionalAfterNamed.error(arg.value.pos, "expr:call:positional_after_named",
                            "Unnamed argument after a named argument")
                    error = true
                } else {
                    positional.add(arg)
                }
            } else {
                val name = arg.name
                if (!namedNames.add(name.rName)) {
                    // Recoverable error - not setting the error flag.
                    msgCtx.error(name.pos, "expr:call:named_arg_dup:$name",
                        "Named argument '$name' specified more than once")
                } else {
                    named.add(C_NameValue(name, arg))
                }
            }
        }

        return if (error) null else C_CallArguments(args, positional.toImmList(), named.toImmList())
    }

    fun compileCall(
        ctx: C_ExprContext,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint,
        target: C_FunctionCallTarget,
        defaultArgIdeInfos: Map<R_Name, C_IdeSymbolInfo>,
    ): V_GlobalFunctionCall? {
        val callArgs = compileCallArgs(ctx, args, target)
        callArgs ?: return null

        val vCall = when (callArgs) {
            is C_FullCallArguments -> {
                target.compileFull(callArgs, resTypeHint)
            }
            is C_PartialCallArguments -> {
                val resFnType = resTypeHint.getFunctionType()
                target.compilePartial(callArgs, resFnType)
            }
        }

        callArgs.setArgIdeInfos(vCall?.argIdeInfos ?: defaultArgIdeInfos)
        return vCall
    }

    fun compileCallArgs(
        ctx: C_ExprContext,
        args: List<S_CallArgument>,
        targetInfo: C_FunctionCallTargetInfo,
    ): C_AbstractCallArguments? {
        val cArgs = C_CallArgument.compileArguments(ctx, args, targetInfo.typeHints())

        val res = C_ArgsListProcessor.processArgs(ctx, cArgs)
        if (res == null) {
            cArgs.forEach { it.nameHand?.setIdeInfo(C_IdeSymbolInfo.UNKNOWN) }
        }

        return res
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// private part

private class C_FullCallArguments_Impl(
    ctx: C_ExprContext,
    rawArgs: ImmList<C_CallArgumentHandle>,
    args: C_CallArguments,
): C_FullCallArguments(ctx, args, rawArgs) {
    override fun compileSimpleArgs(functionName: LazyString): ImmList<V_Expr> {
        val named = rawArgs.named.firstOrNull()
        if (named != null) {
            C_Errors.errNamedArgsNotSupported(ctx.msgCtx, functionName, named.name)
        }

        return rawArgs.positional.mapToImmList {
            when (it.value) {
                is C_CallArgumentValue_Expr -> it.value.vExpr
                is C_CallArgumentValue_Wildcard -> throw IllegalStateException()
            }
        }
    }

    override fun compileComplexArgs(
        callInfo: C_FunctionCallInfo,
        callParams: C_FunctionCallParameters,
    ): V_FunctionCallArgs? {
        val matcherRes = C_ArgMatcher.bind(ctx.msgCtx, callInfo, callParams.bindParams, rawArgs, false)
        val matching = matcherRes.matching

        if (matching == null) {
            C_InternalFnArgsUtils.validateArgTypes(ctx, callInfo, callParams, matcherRes)
            return null
        }

        val exprs = C_InternalFnArgsUtils.makeExprArgs(ctx, callInfo, callParams, matching)
        val mapping = matching.mapping.mapToImmList { it.index }
        return V_FunctionCallArgs(exprs, mapping, matching.exprsToParams)
    }
}

private class C_PartialCallArguments_Impl(
    ctx: C_ExprContext,
    rawArgs: ImmList<C_CallArgumentHandle>,
    private val args: C_CallArguments,
    private val firstWildcardPos: S_Pos,
): C_PartialCallArguments(ctx, firstWildcardPos, rawArgs, args.named.firstOrNull()?.name) {
    override fun compileEffectiveArgs(
        callInfo: C_FunctionCallInfo,
        callParams: C_FunctionCallParameters,
    ): C_EffectivePartialArguments? {
        val errWatcher = ctx.msgCtx.errorWatcher()

        val matcherRes = C_ArgMatcher.bind(ctx.msgCtx, callInfo, callParams.bindParams, args, true)
        val matching = matcherRes.matching

        if (matching == null) {
            C_InternalFnArgsUtils.validateArgTypes(ctx, callInfo, callParams, matcherRes)
            return null
        }

        val exprArgs = C_InternalFnArgsUtils.makeExprArgs(ctx, callInfo, callParams, matching)
        val wildArgs = matching.wildArgs.mapToImmList { callParams.list[it.index].type }

        val res = C_EffectivePartialArguments(callParams, exprArgs, wildArgs, matching.mapping)
        return if (errWatcher.hasNewErrors()) null else res
    }

    override fun errPartialNotSupportedFn(functionName: String) {
        ctx.msgCtx.error(firstWildcardPos, C_Errors.msgPartialCallNotAllowed(functionName))
    }

    override fun errPartialNotSupportedCase(fnCase: C_CodeMsg) {
        val code = "expr:call:partial_bad_case:[${fnCase.code}]"
        val msg = "Partial application not supported for function ${fnCase.msg}"
        ctx.msgCtx.error(firstWildcardPos, code, msg)
    }
}

private object C_ArgsListProcessor {
    fun processArgs(ctx: C_ExprContext, rawArgs: ImmList<C_CallArgumentHandle>): C_AbstractCallArguments? {
        val args = rawArgs.mapToImmList { it.toCallArgument() }

        val wildArgs = args.filter {
            when (it.value) {
                is C_CallArgumentValue_Wildcard -> true
                is C_CallArgumentValue_Expr -> false
            }
        }

        val res = if (wildArgs.isEmpty()) {
            val callArgs = C_FunctionCallArgsUtils.makeCallArguments(ctx.msgCtx, args)
            if (callArgs == null) null else C_FullCallArguments_Impl(ctx, rawArgs, callArgs)
        } else {
            compilePartialArgs(ctx, rawArgs, args, wildArgs)
        }

        return res
    }

    private fun compilePartialArgs(
        ctx: C_ExprContext,
        rawArgs: ImmList<C_CallArgumentHandle>,
        args: ImmList<C_CallArgument>,
        wildArgs: List<C_CallArgument>,
    ): C_AbstractCallArguments? {
        val lastArg = args.last()
        val realArgsList = if (lastArg.name == null && lastArg.index == wildArgs.last().index) {
            if (wildArgs.size > 1) {
                val code = "expr:call:last_wildcard_not_alone"
                val msg = "Wildcard not allowed as the last argument if there are other wildcard arguments"
                ctx.msgCtx.error(lastArg.value.pos, code, msg)
            }
            args.subList(0, args.size - 1)
        } else {
            args
        }

        val realArgs = C_FunctionCallArgsUtils.makeCallArguments(ctx.msgCtx, realArgsList)
        realArgs ?: return null

        val firstWildPos = wildArgs.first().value.pos
        return C_PartialCallArguments_Impl(ctx, rawArgs, realArgs, firstWildPos)
    }
}

private object C_InternalFnArgsUtils {
    fun adaptArgType(
        ctx: C_ExprContext,
        callInfo: C_FunctionCallInfo,
        param: C_FunctionCallParameter,
        arg: V_Expr,
    ): V_Expr {
        val argType = arg.type
        val m = matchArgType(ctx, callInfo, param, argType)
        return m?.adaptExpr(ctx, arg) ?: arg
    }

    fun validateArgTypes(
        ctx: C_ExprContext,
        callInfo: C_FunctionCallInfo,
        callParams: C_FunctionCallParameters,
        matcherRes: C_ArgMatcherResult,
    ) {
        for ((param, vExpr) in matcherRes.paramValues) {
            val callParam = callParams.list[param.index]
            adaptArgType(ctx, callInfo, callParam, vExpr)
        }
    }

    private fun matchArgType(
        ctx: C_ExprContext,
        callInfo: C_FunctionCallInfo,
        param: C_FunctionCallParameter,
        argType: R_Type,
    ): C_TypeAdapter? {
        val paramType = param.type
        return if (paramType.isError()) C_TypeAdapter_Direct else {
            val adapter = paramType.getTypeAdapter(argType)
            if (adapter == null && argType.isNotError()) {
                val paramName = param.nameCodeMsg()
                val fnNameCode = callInfo.functionName ?: "?"
                val code = "expr_call_argtype:[$fnNameCode]:${paramName.code}:${paramType.strCode()}:${argType.strCode()}"
                val msg = "Wrong argument type for parameter ${paramName.msg}: ${argType.str()} instead of ${paramType.str()}"
                ctx.msgCtx.error(callInfo.callPos, code, msg)
            }
            adapter
        }
    }

    fun makeExprArgs(
        ctx: C_ExprContext,
        callInfo: C_FunctionCallInfo,
        callParams: C_FunctionCallParameters,
        matching: C_ArgMatching,
    ): ImmList<V_Expr> {
        return matching.exprArgs.mapToImmList { arg ->
            val param = callParams.list[arg.param.index]
            when (arg) {
                is C_ArgMatchArg_Expr -> {
                    adaptArgType(ctx, callInfo, param, arg.vExpr)
                }
                is C_ArgMatchArg_Default -> {
                    arg.defaultValue.createArgumentExpr(ctx, callInfo.callPos, param.type)
                }
            }
        }
    }
}
