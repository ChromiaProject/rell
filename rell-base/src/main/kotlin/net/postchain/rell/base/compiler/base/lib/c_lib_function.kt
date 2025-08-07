/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import com.google.common.collect.Multimap
import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_CallArgumentValue_Expr
import net.postchain.rell.base.compiler.ast.S_CallArgumentValue_Wildcard
import net.postchain.rell.base.compiler.ast.S_Expr
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_IdeSymbolInfo
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.def.C_GlobalFunction
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.fn.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_FeatureRestrictions
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.R_FunctionType
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_MemberCalculator
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion

internal object C_LibFunctionUtils {
    val RESTRICTIONS_NAMED_ARGS = C_FeatureRestrictions.make(
        "0.13.9",
        "lib_call_named_arg" toCodeMsg "Named arguments of library functions are",
        C_CompilerOptions::allowLibNamedArgsAnyVersion,
    )

    fun makeGlobalFunction(
        naming: C_MemberNaming,
        cases: ImmList<C_LibFuncCase<V_GlobalFunctionCall>>,
    ): C_LibGlobalFunction {
        return C_RegularLibGlobalFunction(naming, cases)
    }

    fun makeMemberFunction(
        cases: ImmList<C_LibFuncCase<V_MemberFunctionCall>>,
    ): C_LibMemberFunction {
        return C_RegularLibMemberFunction(cases)
    }
}

internal abstract class C_LibGlobalFunction: C_GlobalFunction() {
    abstract fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibGlobalFunction
}

internal sealed class C_LibMemberFunction {
    abstract fun getDefaultIdeInfo(): C_IdeSymbolInfo
    abstract fun getDefaultArgIdeInfos(): Map<R_Name, C_IdeSymbolInfo>
    abstract fun getCallTypeHints(selfType: R_Type): C_CallTypeHints

    abstract fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibMemberFunction

    open fun ideGetParameterCompletions(): Multimap<String, IdeCompletion> = immMultimapOf()

    abstract fun compileCallFull(
        ctx: C_ExprContext,
        callCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_FullCallArguments,
        resTypeHint: C_TypeHint,
    ): V_MemberFunctionCall

    abstract fun compileCallPartial(
        ctx: C_ExprContext,
        caseCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_PartialCallArguments,
        resTypeHint: R_FunctionType?,
    ): V_MemberFunctionCall?
}

abstract class C_SpecialLibGlobalFunctionBody {
    open fun paramCount(): IntRange? = null

    abstract fun compileCall(ctx: C_ExprContext, name: LazyPosString, args: ImmList<S_Expr>): V_Expr
}

internal class C_SpecialLibGlobalFunction(
    private val body: C_SpecialLibGlobalFunctionBody,
    private val ideInfo: C_IdeSymbolInfo,
    private val restrictions: C_MemberRestrictions,
): C_LibGlobalFunction() {
    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibGlobalFunction {
        return this
    }

    override fun compileCall0(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: ImmList<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        restrictions.access(ctx.msgCtx, name.pos)

        val errPartial = ctx.msgCtx.firstErrorReporter()

        val argExprsZ = args.map {
            when (val value = it.value) {
                is S_CallArgumentValue_Expr -> value.expr
                is S_CallArgumentValue_Wildcard -> {
                    errPartial.error(value.pos, C_Errors.msgPartialCallNotAllowed(name.str))
                    null
                }
            }
        }

        val argExprs = argExprsZ.filterNotNullToImmList()
        if (argExprs.size != argExprsZ.size) return C_ExprUtils.errorVGlobalCall(ctx, name.pos)

        val argName = args.firstNotNullOfOrNull { it.name }
        if (argName != null) {
            C_Errors.errLibFunctionNamedArg(ctx.msgCtx, name.str, argName)
            argExprs.forEach { it.compileSafe(ctx) }
            return C_ExprUtils.errorVGlobalCall(ctx, name.pos)
        }

        val paramCountRange = body.paramCount()
        val argCount = argExprs.size
        if (paramCountRange != null && argCount !in paramCountRange) {
            val paramsMin = paramCountRange.first
            val paramsMax = paramCountRange.last
            val paramCountMsg = if (paramsMin == paramsMax) "$paramsMin" else "$paramsMin .. $paramsMax"
            ctx.msgCtx.error(name.pos, "fn:sys:wrong_arg_count:$paramsMin:$paramsMax:$argCount",
                    "Wrong number of arguments for function '$name': $argCount instead of $paramCountMsg")
            argExprs.forEach { it.compileSafe(ctx) }
            return C_ExprUtils.errorVGlobalCall(ctx, name.pos, R_BooleanType)
        }

        val vExpr = body.compileCall(ctx, name, argExprs)
        return V_GlobalFunctionCall(vExpr, ideInfo, immMapOf())
    }
}

abstract class C_SpecialLibMemberFunctionBody {
    internal abstract fun compileCall(
        ctx: C_ExprContext,
        callCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: ImmList<V_Expr>,
    ): V_SpecialMemberFunctionCall?
}

internal abstract class V_SpecialMemberFunctionCall(
    protected val exprCtx: C_ExprContext,
    val returnType: R_Type,
) {
    abstract fun calculator(): R_MemberCalculator

    open fun globalConstantRestriction(): V_GlobalConstantRestriction? = null
    open fun canBeDbExpr(): Boolean = false
    open fun dbExprWhat(base: V_Expr, safe: Boolean): C_DbAtWhatValue? = null
}

internal class C_SpecialLibMemberFunction(
    private val body: C_SpecialLibMemberFunctionBody,
    private val ideInfo: C_IdeSymbolInfo,
    private val restrictions: C_MemberRestrictions,
): C_LibMemberFunction() {
    override fun getDefaultIdeInfo() = ideInfo
    override fun getDefaultArgIdeInfos() = immMapOf<R_Name, C_IdeSymbolInfo>()
    override fun getCallTypeHints(selfType: R_Type): C_CallTypeHints = C_CallTypeHints_None

    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibMemberFunction = this

    override fun compileCallFull(
        ctx: C_ExprContext,
        callCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_FullCallArguments,
        resTypeHint: C_TypeHint,
    ): V_MemberFunctionCall {
        restrictions.access(ctx.msgCtx, callCtx.linkPos)
        val vArgs = args.compileSimpleArgs(callCtx.fullNameLazy)
        val vCall = body.compileCall(ctx, callCtx, selfType, vArgs)
        vCall ?: return V_MemberFunctionCall_Error(ctx, ideInfo)
        return V_MemberFunctionCall_SpecialLibFunction(ctx, ideInfo, vArgs, vCall)
    }

    override fun compileCallPartial(
        ctx: C_ExprContext,
        caseCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_PartialCallArguments,
        resTypeHint: R_FunctionType?,
    ): V_MemberFunctionCall? {
        restrictions.access(ctx.msgCtx, caseCtx.linkPos)
        args.errPartialNotSupportedFn(caseCtx.qualifiedNameMsg())
        return null
    }

    private class V_MemberFunctionCall_SpecialLibFunction(
        exprCtx: C_ExprContext,
        ideInfo: C_IdeSymbolInfo,
        private val vExprs: ImmList<V_Expr>,
        private val specialCall: V_SpecialMemberFunctionCall,
    ): V_MemberFunctionCall(exprCtx, ideInfo, immMapOf()) {
        override fun vExprs() = vExprs
        override fun globalConstantRestriction() = specialCall.globalConstantRestriction()
        override fun returnType() = specialCall.returnType
        override fun canBeDbExpr() = specialCall.canBeDbExpr()

        override fun calculator(): R_MemberCalculator {
            return specialCall.calculator()
        }

        override fun dbExprWhat(base: V_Expr, safe: Boolean): C_DbAtWhatValue? {
            return specialCall.dbExprWhat(base, safe)
        }
    }
}

private class C_RegularLibGlobalFunction(
    private val naming: C_MemberNaming,
    private val cases: ImmList<C_LibFuncCase<V_GlobalFunctionCall>>,
): C_LibGlobalFunction() {
    private val defaultCase = cases.first()

    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibGlobalFunction {
        val naming2 = naming.replaceSelfType(rep.selfType)
        val cases2 = cases.mapOrSame { it.replaceTypeParams(rep) }
        return if (naming2 === naming && cases2 === cases) this else C_RegularLibGlobalFunction(naming2, cases2)
    }

    override fun compileCall0(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: ImmList<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        val target = C_FunctionCallTarget_LibGlobalFunction(ctx, name)
        val vCall = C_FunctionCallArgsUtils.compileCall(ctx, args, resTypeHint, target, defaultCase.argIdeInfos)
        return vCall ?: C_ExprUtils.errorVGlobalCall(
            ctx,
            name.pos,
            ideInfo = defaultCase.ideInfo,
            argIdeInfos = defaultCase.argIdeInfos,
        )
    }

    override fun ideGetParameterCompletions() = C_LibFuncCase.ideGetParameterCompletions(cases)

    private inner class C_FunctionCallTarget_LibGlobalFunction(
        val ctx: C_ExprContext,
        val name: LazyPosString,
    ): C_FunctionCallTarget() {
        override fun retType() = null
        override fun typeHints() = C_LibFunctionParamHints(R_UnitType, cases)

        override fun compileFull(args: C_FullCallArguments, resTypeHint: C_TypeHint): V_GlobalFunctionCall? {
            val caseCtx = C_LibFuncCaseCtx(name.pos, naming.fullNameLazy)
            val match = C_LibFuncCase.matchCase(ctx.msgCtx, caseCtx, R_UnitType, args, resTypeHint, cases)
            match ?: return null
            return match.compileCall(ctx, caseCtx)
        }

        override fun compilePartial(args: C_PartialCallArguments, resTypeHint: R_FunctionType?): V_GlobalFunctionCall? {
            val caseCtx = C_LibFuncCaseCtx(name.pos, naming.fullNameLazy)
            return compileCallPartialCommon(ctx, caseCtx, R_UnitType, cases, args, resTypeHint)
        }
    }

    companion object {
        fun <CallT: V_FunctionCall> compileCallPartialCommon(
            ctx: C_ExprContext,
            caseCtx: C_LibFuncCaseCtx,
            selfType: R_Type,
            cases: ImmList<C_LibFuncCase<CallT>>,
            args: C_PartialCallArguments,
            resTypeHint: R_FunctionType?
        ): CallT? {
            if (args.firstNamedArg != null) {
                C_LibFunctionUtils.RESTRICTIONS_NAMED_ARGS.access(ctx.msgCtx, args.firstNamedArg.pos)
            }

            val caseTargets = cases.mapNotNull { it.getPartialCallTarget(caseCtx, selfType) }
            if (caseTargets.isEmpty()) {
                val name = getFunctionNameForMessage(caseCtx, cases.mapToImmList { it.getSpecificName(selfType) })
                args.errPartialNotSupportedFn(name)
                return null
            }

            val partMatch = if (resTypeHint == null) {
                if (caseTargets.size > 1) {
                    val name = getFunctionNameForMessage(caseCtx, caseTargets.mapToImmList { it.fullName.value })
                    ctx.msgCtx.error(args.wildcardPos, C_Errors.msgPartialCallAmbiguous(name))
                    return null
                }
                PartMatch(caseTargets[0], caseTargets[0].match())
            } else if (caseTargets.size > 1) {
                var matches = caseTargets.mapNotNull { caseTarget ->
                    caseTarget.match(resTypeHint)?.let { PartMatch(caseTarget, it) }
                }
                if (matches.size > 1) {
                    val exactMatches = matches.filter { it.match.exact }
                    if (exactMatches.size == 1) {
                        matches = exactMatches
                    }
                }
                if (matches.size != 1) {
                    val targets = if (matches.isEmpty()) caseTargets else matches.map { it.target }
                    val name = getFunctionNameForMessage(caseCtx, targets.mapToImmList { it.fullName.value })
                    ctx.msgCtx.error(args.wildcardPos, C_Errors.msgPartialCallAmbiguous(name))
                    return null
                }
                matches[0]
            } else {
                val caseTarget = caseTargets[0]
                val match = caseTarget.match(resTypeHint) ?: caseTarget.match()
                PartMatch(caseTarget, match)
            }

            return compileMatch(ctx, caseCtx, args, partMatch)
        }

        private fun getFunctionNameForMessage(caseCtx: C_LibFuncCaseCtx, caseNames: ImmList<String>): String {
            val caseName = caseNames.toSet().singleOrNull()
            return caseName ?: caseCtx.qualifiedNameMsg()
        }

        private fun <CallT: V_FunctionCall> compileMatch(
            ctx: C_ExprContext,
            caseCtx: C_LibFuncCaseCtx,
            args: C_PartialCallArguments,
            match: PartMatch<CallT>,
        ): CallT? {
            val params = match.match.parameters()
            if (params == null) {
                args.errPartialNotSupportedCase(match.target.codeMsg())
                return null
            }

            val callInfo = C_FunctionCallInfo(caseCtx.linkPos, match.target.fullName)
            val callParams = C_FunctionCallParameters(params)
            val effArgs = args.compileEffectiveArgs(callInfo, callParams)
            effArgs ?: return null

            return match.match.compileCall(ctx, effArgs)
        }

        private class PartMatch<CallT: V_FunctionCall>(
            val target: C_LibPartialCallTarget<CallT>,
            val match: C_LibPartialCallTargetMatch<CallT>,
        )
    }
}

private class C_RegularLibMemberFunction(
    private val cases: ImmList<C_LibFuncCase<V_MemberFunctionCall>>,
): C_LibMemberFunction() {
    private val defaultCase = cases.first()

    override fun getDefaultIdeInfo() = defaultCase.ideInfo
    override fun getDefaultArgIdeInfos() = defaultCase.argIdeInfos
    override fun getCallTypeHints(selfType: R_Type): C_CallTypeHints = C_LibFunctionParamHints(selfType, cases)

    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibMemberFunction {
        val cases2 = cases.mapOrSame { it.replaceTypeParams(rep) }
        return if (cases2 === cases) this else C_RegularLibMemberFunction(cases2)
    }

    override fun ideGetParameterCompletions() = C_LibFuncCase.ideGetParameterCompletions(cases)

    override fun compileCallFull(
        ctx: C_ExprContext,
        callCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_FullCallArguments,
        resTypeHint: C_TypeHint,
    ): V_MemberFunctionCall {
        val match = C_LibFuncCase.matchCase(ctx.msgCtx, callCtx, selfType, args, resTypeHint, cases)
        match ?: return C_ExprUtils.errorVMemberCall(ctx, ideInfo = defaultCase.ideInfo)
        return match.compileCall(ctx, callCtx)
    }

    override fun compileCallPartial(
        ctx: C_ExprContext,
        caseCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_PartialCallArguments,
        resTypeHint: R_FunctionType?,
    ): V_MemberFunctionCall? {
        return C_RegularLibGlobalFunction.compileCallPartialCommon(ctx, caseCtx, selfType, cases, args, resTypeHint)
    }
}

private class C_LibFunctionParamHints(
    private val selfType: R_Type,
    private val cases: ImmList<C_LibFuncCase<*>>,
): C_CallTypeHints {
    private val caseHints: ImmList<C_CallTypeHints> by lazy {
        cases.mapToImmList { it.getCallTypeHints(selfType) }
    }

    override fun getTypeHint(index: Int?, name: R_Name?): C_TypeHint {
        val hints = caseHints.map { it.getTypeHint(index, name) }
        return C_TypeHint.combined(hints)
    }
}
