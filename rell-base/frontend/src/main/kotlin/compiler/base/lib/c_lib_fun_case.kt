/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.fn.*
import net.postchain.rell.base.compiler.base.utils.*
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.lmodel.*
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_CallExpr
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_FunctionCallTarget
import net.postchain.rell.base.model.expr.R_FunctionCallTarget_SysMemberFunction
import net.postchain.rell.base.mtype.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.ide.IdeSymbolKind

object C_LibFuncCaseUtils {
    fun makeGlobalCase(
        naming: C_MemberNaming,
        lFunction: L_Function,
        outerTypeArgs: ImmMap<Name, R_Type>,
        restrictions: C_MemberRestrictions,
        ideInfo: C_IdeSymbolInfo,
    ): C_LibFuncCase<V_GlobalFunctionCall> {
        return C_GlobalLibFuncCase(lFunction, ideInfo, restrictions, naming, outerTypeArgs)
    }

    fun makeMemberCase(
        lFunction: L_Function,
        ideInfo: C_IdeSymbolInfo,
        naming: C_MemberNaming,
        restrictions: C_MemberRestrictions,
    ): C_LibFuncCase<V_MemberFunctionCall> {
        return C_MemberLibFuncCase(lFunction, ideInfo, restrictions, naming)
    }

    fun errNoMatch(msgCtx: C_MessageContext, pos: S_Pos, name: String, args: List<Pair<Name?, R_Type>>) {
        if (args.any { it.second.isError() }) return
        val argsStrShort = args.joinToString(",") { (argName, argType) ->
            val typeStr = argType.strCode()
            if (argName == null) typeStr else "$argName:$typeStr"
        }
        val argsStr = args.joinToString { (argName, argType) ->
            val typeStr = argType.str()
            if (argName == null) typeStr else "$argName: $typeStr"
        }

        val msg = "Function '$name' undefined for arguments ($argsStr)"
        msgCtx.error(pos, "expr_call_badargs:[$name]:[$argsStrShort]", msg)
    }
}

class C_LibFuncCaseCtx(val linkPos: S_Pos, val fullNameLazy: Lazy<String>) {
    /** Beware that the returned name is a link name, not actual function definition name
     * (may differ sometimes, e.g. aliases, namespace member links, etc.). */
    fun qualifiedNameMsg() = fullNameLazy.value
}

abstract class C_LibFuncCase<CallT: V_FunctionCall>(
    val ideInfo: C_IdeSymbolInfo,
) {
    abstract val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo>

    abstract fun getSpecificName(selfType: R_Type): String
    abstract fun getCallTypeHints(selfType: R_Type): C_CallTypeHints

    abstract fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibFuncCase<CallT>

    abstract fun match(
        msgMgr: C_MessageManager,
        caseCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_FullCallArguments,
        resTypeHint: C_TypeHint,
    ): C_LibFuncCaseMatch<CallT>?

    open fun getPartialCallTarget(caseCtx: C_LibFuncCaseCtx, selfType: R_Type): C_LibPartialCallTarget<CallT>? = null

    protected open fun ideGetParameterCompletions(): ImmMultimap<String, IdeCompletion> = immMultimapOf()

    companion object {
        fun <CallT: V_FunctionCall> matchCase(
            msgCtx: C_MessageContext,
            caseCtx: C_LibFuncCaseCtx,
            selfType: R_Type,
            args: C_FullCallArguments,
            resTypeHint: C_TypeHint,
            cases: List<C_LibFuncCase<CallT>>,
        ): C_LibFuncCaseMatch<CallT>? {
            var msgMgr = C_DefaultMessageManager()

            val valid = args.rawArgs.validate(msgCtx)

            for (case in cases) {
                msgMgr = C_DefaultMessageManager() // Use a new manager for each case.
                val res = case.match(msgMgr, caseCtx, selfType, args, resTypeHint)
                if (res != null) {
                    return res
                }
            }

            errNoMatch(msgCtx, caseCtx, cases, args, msgMgr, valid)
            return null
        }

        private fun errNoMatch(
            msgCtx: C_MessageContext,
            caseCtx: C_LibFuncCaseCtx,
            cases: List<C_LibFuncCase<*>>,
            args: C_FullCallArguments,
            lastMsgMgr: C_MessageManager,
            valid: Boolean,
        ) {
            var errorReported = !valid
            if (cases.size == 1) {
                val messages = lastMsgMgr.messages()
                for (message in messages) {
                    msgCtx.message(message)
                    errorReported = errorReported || message.type == C_MessageType.ERROR
                }
            }

            if (!errorReported) {
                val qName = caseCtx.qualifiedNameMsg()
                val expectedArgs = args.rawArgs.all.mapNotNull {
                    when (it.value) {
                        is C_CallArgumentValue_Expr -> it.name?.rName to it.value.vExpr.type
                        is C_CallArgumentValue_Wildcard -> null // Must not happen
                    }
                }
                C_LibFuncCaseUtils.errNoMatch(msgCtx, caseCtx.linkPos, qName, expectedArgs)
            }
        }

        fun ideGetParameterCompletions(cases: List<C_LibFuncCase<*>>): ImmMultimap<String, IdeCompletion> {
            val res = mutableMultimapOf<String, IdeCompletion>()
            for (case in cases) {
                res.putAll(case.ideGetParameterCompletions())
            }
            return res.mapValues { it.value.distinct() }
                .toImmMultimap()
        }
    }
}

abstract class C_LibFuncCaseMatch<CallT: V_FunctionCall> {
    abstract fun compileCall(ctx: C_ExprContext, caseCtx: C_LibFuncCaseCtx): CallT
}

abstract class C_LibPartialCallTarget<CallT: V_FunctionCall>(
    val callPos: S_Pos,
    val fullName: Lazy<String>,
) {
    abstract fun codeMsg(): C_CodeMsg
    abstract fun match(): C_LibPartialCallTargetMatch<CallT>
    abstract fun match(fnType: R_FunctionType): C_LibPartialCallTargetMatch<CallT>?
}

abstract class C_LibPartialCallTargetMatch<CallT: V_FunctionCall>(val exact: Boolean) {
    abstract fun parameters(): ImmList<C_FunctionCallParameter>?
    abstract fun compileCall(ctx: C_ExprContext, args: C_EffectivePartialArguments): CallT
}

private class C_GenericFuncCaseCtx(
    val outerTypeArgs: ImmMap<Name, R_Type>,
    val header: C_LibFunctionHeader,
)

private class C_LibFunctionHeader(val lHeader: L_FunctionHeader) {
    val header = lHeader.intHeader

    val bindParams: C_ArgMatchParams by lazy {
        val paramList = lHeader.params.mapIndexedToImmList { i, param -> C_ArgMatchParam(i, param.name, param.arity, null) }
        C_ArgMatchParams(paramList)
    }

    fun replaceTypeParams(map: Map<M_TypeParam, M_TypeSet>): C_LibFunctionHeader {
        val lHeader2 = lHeader.replaceTypeParams(map)
        return if (lHeader2 === lHeader) this else C_LibFunctionHeader(lHeader2)
    }
}

private class C_CaseMatchBase(
    val resType: R_Type,
    val fullNameLazy: Lazy<String>,
    val linkPos: S_Pos,
    private val restrictions: C_MemberRestrictions,
) {
    fun makeCallTargetGlobal(ctx: C_ExprContext, linkPos: S_Pos, cFn: C_SysFunction): V_FunctionCallTarget {
        val desc = makeCallTargetDescriptor(ctx, linkPos, cFn)
        return V_FunctionCallTarget_SysGlobalFunction(desc)
    }

    fun makeCallTargetMember(ctx: C_ExprContext, linkPos: S_Pos, cFn: C_SysFunction): V_FunctionCallTarget {
        val desc = makeCallTargetDescriptor(ctx, linkPos, cFn)
        return V_FunctionCallTarget_SysMemberFunction(desc)
    }

    private fun makeCallTargetDescriptor(
        ctx: C_ExprContext,
        linkPos: S_Pos,
        cFn: C_SysFunction,
    ): V_SysFunctionTargetDescriptor {
        restrictions.access(ctx.msgCtx, linkPos)
        val body = cFn.compileCall(C_SysFunctionCtx(ctx, linkPos))
        return V_SysFunctionTargetDescriptor(resType, body.rFn, body.dbFn, fullNameLazy, body.pure)
    }
}

private class C_LibMatchParams(
    val actualParams: ImmList<L_FunctionParam>,
    private val cHeader: C_LibFunctionHeader,
    private val args: ImmList<V_ExprWrapper>,
    private val adapters: ImmList<C_TypeAdapter>,
    private val argMatching: C_ArgMatching,
    private val namedArg: C_Name?,
) {
    init {
        checkEquals(args.size, actualParams.size)
        checkEquals(adapters.size, actualParams.size)
    }

    fun effectiveArgs(ctx: C_ExprContext): V_FunctionCallArgs {
        checkRestrictions(ctx)

        val exprs = args.mapIndexedToImmList { i, arg ->
            var expr = arg.unwrap()
            expr = adapters[i].adaptExpr(ctx, expr)

            val lParam = actualParams[argMatching.exprsToParams[i]]
            if (lParam.lazy) expr = V_LazyExpr(ctx, expr)
            expr
        }

        val paramsToExprs = argMatching.mapping.mapToImmList { it.index }
        return V_FunctionCallArgs(exprs, paramsToExprs, argMatching.exprsToParams)
    }

    private fun checkRestrictions(ctx: C_ExprContext) {
        C_FunctionUtils.checkParamRestrictions(ctx.msgCtx, argMatching.mapping) { param ->
            cHeader.lHeader.params[param.index].restrictions
        }

        if (namedArg != null) {
            C_LibFunctionUtils.RESTRICTIONS_NAMED_ARGS.access(ctx.msgCtx, namedArg.pos)
        }
    }
}

private abstract class C_CommonLibFuncCase<CallT: V_FunctionCall>(
    protected val lFunction: L_Function,
    ideInfo: C_IdeSymbolInfo,
    protected val restrictions: C_MemberRestrictions,
): C_LibFuncCase<CallT>(ideInfo) {
    override val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo> by lazy {
        lFunction.header.params.associateToImmMap {
            it.name to C_IdeSymbolInfo.direct(IdeSymbolKind.EXPR_CALL_ARG, null, null, it.docSymbol)
        }
    }

    protected abstract fun getFullName(selfType: R_Type): Lazy<String>
    protected abstract fun getCaseContext(selfType: R_Type): C_GenericFuncCaseCtx?

    protected abstract fun makeMatch(
        matchBase: C_CaseMatchBase,
        matchParams: C_LibMatchParams,
        cFn: C_SysFunction,
    ): C_LibFuncCaseMatch<CallT>

    protected abstract fun makeErrorMatch(
        matchBase: C_CaseMatchBase,
        params: C_LibMatchParams,
        codeMsg: C_CodeMsg,
    ): C_LibFuncCaseMatch<CallT>

    protected abstract fun makePartTarget(
        matchBase: C_CaseMatchBase,
        params: ImmList<L_FunctionParam>,
        minParams: Int,
        cFn: C_SysFunction,
    ): C_LibPartialCallTarget_Common<CallT>

    final override fun getSpecificName(selfType: R_Type): String {
        val fullNameLazy = getFullName(selfType)
        return fullNameLazy.value
    }

    final override fun getCallTypeHints(selfType: R_Type): C_CallTypeHints {
        val libCtx = getCaseContext(selfType)
        libCtx ?: return C_CallTypeHints_None
        return C_LibFunctionCallTypeHints(libCtx.header.lHeader)
    }

    final override fun match(
        msgMgr: C_MessageManager,
        caseCtx: C_LibFuncCaseCtx,
        selfType: R_Type,
        args: C_FullCallArguments,
        resTypeHint: C_TypeHint,
    ): C_LibFuncCaseMatch<CallT>? {
        val genCaseCtx = getCaseContext(selfType)
        genCaseCtx ?: return null

        val header = genCaseCtx.header
        val paramsMatch = matchParams(msgMgr, caseCtx, header, args.rawArgs)
        paramsMatch ?: return null

        val vArgExprs = getArgExprs(paramsMatch)
        vArgExprs ?: return null
        checkEquals(vArgExprs.size, paramsMatch.argMatching.mapping.size)

        val vArgExprWrappers = vArgExprs.mapIndexedToImmList { i, arg ->
            val paramIndex = paramsMatch.argMatching.exprsToParams[i]
            val param = paramsMatch.actualParams[paramIndex]
            if (param.nullable) arg.asNullable() else arg.asWrapper()
        }

        val match = matchArgs(header.lHeader, resTypeHint, paramsMatch, vArgExprWrappers)
        match ?: return null

        val rResultType = match.actualHeader.rResultType
        val unresolved = match.actualHeader.typeParams
        val fullName = getFullName(selfType)

        val matchBase = C_CaseMatchBase(rResultType, fullName, caseCtx.linkPos, restrictions)

        val valueAdapters = paramsMatch.argMatching.paramsToValues(match.adapters)
        val namedArg = args.rawArgs.named.firstOrNull()?.name

        val matchParams = C_LibMatchParams(
            match.actualHeader.params,
            header,
            vArgExprWrappers,
            valueAdapters,
            paramsMatch.argMatching,
            namedArg,
        )

        if (!rResultType.isValid() || unresolved.isNotEmpty()) {
            return makeUnresolvedMatch(matchBase, matchParams, unresolved)
        }

        val allTypeArgs = genCaseCtx.outerTypeArgs.unionNoConflicts(match.typeArgs).toImmMap()
        val cFn = getSysFunction(caseCtx, selfType, rResultType, allTypeArgs)
        cFn ?: return null

        try {
            header.lHeader.validate()
        } catch (e: M_TypeException) {
            val codeMsg = e.code toCodeMsg e.msg
            return makeErrorMatch(matchBase, matchParams, codeMsg)
        }

        return makeMatch(matchBase, matchParams, cFn)
    }

    private fun matchArgs(
        header: L_FunctionHeader,
        resTypeHint: C_TypeHint,
        paramsMatch: L_FunctionParamsMatch,
        vArgExprs: List<V_ExprWrapper>,
    ): L_FunctionHeaderMatch? {
        val expectedResultType = resTypeHint.getSourceType(header.resultType)
        val argValueTypes = vArgExprs.map { it.type }
        val argParamTypes = paramsMatch.argMatching.valuesToParams(argValueTypes)
        return paramsMatch.matchArgs(argParamTypes, expectedResultType)
    }

    private fun matchParams(
        msgMgr: C_MessageManager,
        caseCtx: C_LibFuncCaseCtx,
        header: C_LibFunctionHeader,
        args: C_CallArguments,
    ): L_FunctionParamsMatch? {
        val callInfo = C_FunctionCallInfo(caseCtx.linkPos, caseCtx.fullNameLazy)
        val matcherRes = C_ArgMatcher.bind(msgMgr, callInfo, header.bindParams, args, false)
        val argMatching = matcherRes.matching
        argMatching ?: return null

        val paramIndexes = argMatching.mapping.mapToImmList { it.param.index }

        // A list-based cache to not convert same parameter multiple times (can happen for vararg parameters).
        val simpleParams = MutableList<L_FunctionParam?>(header.lHeader.params.size) { null }

        val actualParams = argMatching.mapping
            .mapToImmList {
                val i = it.param.index
                simpleParams.getOrSet(i) {
                    val lParam = header.lHeader.params[i]
                    lParam.toSimpleParam()
                }
            }

        return L_FunctionParamsMatch(header.header, paramIndexes, actualParams, argMatching)
    }

    private fun getArgExprs(paramsMatch: L_FunctionParamsMatch): List<V_Expr>? {
        if (paramsMatch.argMatching.wildArgs.isNotEmpty()) {
            // Must not happen, checking just in case.
            return null
        }

        return paramsMatch.argMatching.exprArgs.mapNotNullAllOrNull {
            when (it) {
                is C_ArgMatchArg_Expr -> it.vExpr
                is C_ArgMatchArg_Default -> null // Must not happen
            }
        }
    }

    private fun makeUnresolvedMatch(
        matchBase: C_CaseMatchBase,
        matchParams: C_LibMatchParams,
        unresolved: List<M_TypeParam>,
    ): C_LibFuncCaseMatch<CallT> {
        val codeMsg = if (unresolved.isNotEmpty()) {
            val unresolvedNames = unresolved.map { it.name }.sorted()
            val paramsCode = unresolvedNames.joinToString(",")
            val paramsMsg = unresolvedNames.toString()
            "fn:sys:unresolved_type_params:[%s]:$paramsCode" toCodeMsg
                    "Failed to infer type arguments for function '%s': $paramsMsg"
        } else {
            "fn:sys:no_res_type:[%s]" toCodeMsg "Return type is unknown for function '%s'"
        }

        return makeErrorMatch(matchBase, matchParams, codeMsg)
    }

    final override fun getPartialCallTarget(caseCtx: C_LibFuncCaseCtx, selfType: R_Type): C_LibPartialCallTarget<CallT>? {
        val genCaseCtx = getCaseContext(selfType)
        genCaseCtx ?: return null

        val header = genCaseCtx.header.lHeader
        if (header.typeParams.isNotEmpty() || header.params.any { it.lazy || it.arity.many }) {
            return null
        }

        val rResultType = L_TypeUtils.getRTypeOrNull(header.resultType)
        rResultType ?: return null

        val cFn = getSysFunction(caseCtx, selfType, rResultType, genCaseCtx.outerTypeArgs)
        cFn ?: return null

        try {
            header.validate()
        } catch (_: M_TypeException) {
            return null
        }

        val minParams = header.params.takeWhile { it.arity == M_ParamArity.ONE }.size

        val fullName = getFullName(selfType)
        val matchBase = C_CaseMatchBase(rResultType, fullName, caseCtx.linkPos, restrictions)
        return makePartTarget(matchBase, header.params, minParams, cFn)
    }

    private fun getSysFunction(
        caseCtx: C_LibFuncCaseCtx,
        rSelfType: R_Type,
        rResultType: R_Type,
        typeArgs: ImmMap<Name, R_Type>,
    ): C_SysFunction? {
        for (rType in typeArgs.values) {
            if (rType.isAbstract() || !rType.isValid()) {
                return null
            }
        }

        val meta = L_FunctionBodyMeta(
            callPos = caseCtx.linkPos,
            rSelfType = rSelfType,
            rResultType = rResultType,
            rTypeArgs = typeArgs.mapKeysToImmMap { it.key.str },
        )

        return lFunction.body.getSysFunction(meta)
    }

    final override fun ideGetParameterCompletions(): ImmMultimap<String, IdeCompletion> {
        val location = lFunction.fullName.str()
        return lFunction.header.params
            .filterNot { it.arity.many }
            .toImmMultimap {
                it.name.str to C_IdeCompletionsUtils.makeIdeCompletion(it.docSymbol, location)
            }
    }
}

sealed class C_MemberNaming {
    abstract val fullNameLazy: Lazy<String>

    abstract fun replaceSelfType(selfType: M_Type?): C_MemberNaming

    final override fun toString() = fullNameLazy.value

    companion object {
        fun makeFullName(fullName: FullName): C_MemberNaming = C_MemberNaming_FullName(fullName)

        fun makeTypeMember(mType: M_Type, simpleName: Name): C_MemberNaming =
            C_MemberNaming_TypeMember(mType, simpleName)

        fun makeTypeExtensionMember(qualifiedName: QualifiedName, simpleName: Name): C_MemberNaming {
            return C_MemberNaming_TypeExtensionMember(qualifiedName, simpleName, null)
        }

        fun makeConstructor(mType: M_Type): C_MemberNaming {
            return C_MemberNaming_Constructor(mType)
        }
    }
}

private class C_MemberNaming_FullName(
    private val fullName: FullName,
): C_MemberNaming() {
    override val fullNameLazy = lazy {
        // Using a qualified name (w/o the module name) for compatibility; may be changed in the future.
        fullName.qualifiedName.str()
    }

    override fun replaceSelfType(selfType: M_Type?) = this
}

private class C_MemberNaming_TypeMember(
    private val mType: M_Type,
    private val simpleName: Name,
): C_MemberNaming() {
    override val fullNameLazy = lazy {
        "${mType.strCode()}.$simpleName"
    }

    override fun replaceSelfType(selfType: M_Type?): C_MemberNaming {
        return if (selfType == null) this else C_MemberNaming_TypeMember(selfType, simpleName)
    }
}

private class C_MemberNaming_TypeExtensionMember(
    private val qualifiedName: QualifiedName,
    private val simpleName: Name,
    private val actualSelfType: M_Type?,
): C_MemberNaming() {
    override val fullNameLazy = lazy {
        if (actualSelfType == null) {
            "$qualifiedName.$simpleName"
        } else {
            "$qualifiedName(${actualSelfType.strCode()}).$simpleName"
        }
    }

    override fun replaceSelfType(selfType: M_Type?): C_MemberNaming {
        return if (selfType == null) this else
            C_MemberNaming_TypeExtensionMember(qualifiedName, simpleName, selfType)
    }
}

private class C_MemberNaming_Constructor(private val mType: M_Type): C_MemberNaming() {
    override val fullNameLazy = lazy {
        mType.strCode()
    }

    override fun replaceSelfType(selfType: M_Type?) = this
}

private class C_GlobalLibFuncCase(
    lFunction: L_Function,
    ideInfo: C_IdeSymbolInfo,
    restrictions: C_MemberRestrictions,
    private val naming: C_MemberNaming,
    private val outerTypeArgs: ImmMap<Name, R_Type>,
): C_CommonLibFuncCase<V_GlobalFunctionCall>(lFunction, ideInfo, restrictions) {
    private val cHeader = C_LibFunctionHeader(lFunction.header)

    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibFuncCase<V_GlobalFunctionCall> {
        val naming2 = naming.replaceSelfType(rep.selfType)
        val lFunction2 = lFunction.replaceTypeParams(rep.map)
        return if (naming2 === naming && lFunction2 === lFunction) this else {
            C_GlobalLibFuncCase(lFunction2, ideInfo, restrictions, naming2, outerTypeArgs)
        }
    }

    override fun getFullName(selfType: R_Type): Lazy<String> = naming.fullNameLazy

    override fun getCaseContext(selfType: R_Type): C_GenericFuncCaseCtx {
        checkEquals(selfType, R_UnitType)
        return C_GenericFuncCaseCtx(outerTypeArgs, cHeader)
    }

    override fun makeMatch(
        matchBase: C_CaseMatchBase,
        matchParams: C_LibMatchParams,
        cFn: C_SysFunction,
    ): C_LibFuncCaseMatch<V_GlobalFunctionCall> {
        return C_GlobalLibFuncCaseMatch(matchBase, matchParams, cFn, ideInfo, argIdeInfos)
    }

    override fun makeErrorMatch(
        matchBase: C_CaseMatchBase,
        params: C_LibMatchParams,
        codeMsg: C_CodeMsg,
    ): C_LibFuncCaseMatch<V_GlobalFunctionCall> {
        return C_GlobalErrorLibFuncCaseMatch(matchBase, params, codeMsg, ideInfo, argIdeInfos)
    }

    override fun makePartTarget(
        matchBase: C_CaseMatchBase,
        params: ImmList<L_FunctionParam>,
        minParams: Int,
        cFn: C_SysFunction,
    ): C_LibPartialCallTarget_Common<V_GlobalFunctionCall> {
        return C_LibPartialCallTarget_Global(params, minParams, matchBase, cFn, ideInfo, argIdeInfos)
    }
}

private class C_MemberLibFuncCase(
    lFunction: L_Function,
    ideInfo: C_IdeSymbolInfo,
    restrictions: C_MemberRestrictions,
    private val naming: C_MemberNaming,
): C_CommonLibFuncCase<V_MemberFunctionCall>(lFunction, ideInfo, restrictions) {
    private val genericHeader = C_LibFunctionHeader(lFunction.header)

    override fun replaceTypeParams(rep: C_TypeMemberReplacement): C_LibFuncCase<V_MemberFunctionCall> {
        val lFunction2 = lFunction.replaceTypeParams(rep.map)
        return if (lFunction2 === lFunction) this else C_MemberLibFuncCase(lFunction2, ideInfo, restrictions, naming)
    }

    override fun getFullName(selfType: R_Type): Lazy<String> {
        val actualNaming = naming.replaceSelfType(selfType.mType)
        return actualNaming.fullNameLazy
    }

    override fun getCaseContext(selfType: R_Type): C_GenericFuncCaseCtx? {
        val outerTypeArgs = M_TypeUtils.getTypeArgs(selfType.mType)
        val specificHeader = genericHeader.replaceTypeParams(outerTypeArgs)

        if (specificHeader !== genericHeader) {
            try {
                specificHeader.lHeader.validate()
            } catch (_: M_TypeException) {
                return null
            }
        }

        val outerTypeArgTypes = outerTypeArgs
            .mapKeys { Name.of(it.key.name) }
            .mapValuesToImmMap { L_TypeUtils.getRType(it.value.captureType()) }

        return C_GenericFuncCaseCtx(outerTypeArgTypes, specificHeader)
    }

    override fun makeMatch(
        matchBase: C_CaseMatchBase,
        matchParams: C_LibMatchParams,
        cFn: C_SysFunction,
    ): C_LibFuncCaseMatch<V_MemberFunctionCall> {
        return C_MemberLibFuncCaseMatch(matchBase, matchParams, cFn, ideInfo, argIdeInfos)
    }

    override fun makeErrorMatch(
        matchBase: C_CaseMatchBase,
        params: C_LibMatchParams,
        codeMsg: C_CodeMsg,
    ): C_LibFuncCaseMatch<V_MemberFunctionCall> {
        return C_MemberErrorLibFuncCaseMatch(matchBase, params, codeMsg, ideInfo)
    }

    override fun makePartTarget(
        matchBase: C_CaseMatchBase,
        params: ImmList<L_FunctionParam>,
        minParams: Int,
        cFn: C_SysFunction,
    ): C_LibPartialCallTarget_Common<V_MemberFunctionCall> {
        return C_LibPartialCallTarget_Member(params, minParams, matchBase, cFn, ideInfo, argIdeInfos)
    }
}

private class C_LibFunctionCallTypeHints(private val header: L_FunctionHeader): C_CallTypeHints {
    override fun getTypeHint(index: Int?, name: Name?): C_TypeHint {
        val param = when {
            index == null -> null
            index < 0 -> null
            index < header.params.size -> header.params[index]
            header.params.isNotEmpty() && header.params.last().arity.many -> header.params.last()
            else -> null
        }
        return C_TypeHint.ofType(param?.rType)
    }
}

private abstract class C_BaseLibFuncCaseMatch<CallT: V_FunctionCall>(
    protected val matchBase: C_CaseMatchBase,
    private val matchParams: C_LibMatchParams,
): C_LibFuncCaseMatch<CallT>() {
    protected val resType = matchBase.resType

    protected abstract fun compileCall(ctx: C_ExprContext, linkPos: S_Pos, args: V_FunctionCallArgs): CallT

    final override fun compileCall(ctx: C_ExprContext, caseCtx: C_LibFuncCaseCtx): CallT {
        val vCallArgs = matchParams.effectiveArgs(ctx)
        return compileCall(ctx, caseCtx.linkPos, vCallArgs)
    }
}

private abstract class C_NormalLibFuncCaseMatch<CallT: V_FunctionCall>(
    matchBase: C_CaseMatchBase,
    matchParams: C_LibMatchParams,
): C_BaseLibFuncCaseMatch<CallT>(matchBase, matchParams) {
    private val lParams = matchParams.actualParams

    protected fun makeVarStatesDelta(ctx: C_ExprContext, args: V_FunctionCallArgs): C_VarStatesDelta {
        checkEquals(args.exprs.size, lParams.size)

        var res = C_VarStatesDelta.EMPTY

        for ((i, arg) in args.exprs.withIndex()) {
            val param = lParams[args.exprsToParams[i]]
            if (!param.lazy) {
                res = res.and(arg.varStatesDelta.always)

                if (param.implies != null) {
                    val impDeltas = implicationVarStatesDelta(ctx, arg, param.implies)
                    res = res.and(impDeltas)
                }
            }
        }

        return res
    }

    private fun implicationVarStatesDelta(
        ctx: C_ExprContext,
        arg: V_Expr,
        implies: L_ParamImplication,
    ): C_VarStatesDelta {
        if (implies.since != null) {
            if (!C_FeatureSwitch.isActive(implies.since, ctx.globalCtx.compilerOptions.compatibility)) {
                return C_VarStatesDelta.EMPTY
            }
        }

        return when (implies.kind) {
            L_ParamImplication.Kind.TRUE -> arg.varStatesDelta.whenTrue
            L_ParamImplication.Kind.NOT_NULL -> {
                val varKey = arg.varKey()
                if (varKey == null) C_VarStatesDelta.EMPTY else C_VarStatesDelta.nulled(varKey, C_VarNulled.NO)
            }
        }
    }
}

private class C_GlobalLibFuncCaseMatch(
    matchBase: C_CaseMatchBase,
    matchParams: C_LibMatchParams,
    private val cFn: C_SysFunction,
    private val ideInfo: C_IdeSymbolInfo,
    private val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo>,
): C_NormalLibFuncCaseMatch<V_GlobalFunctionCall>(matchBase, matchParams) {
    override fun compileCall(ctx: C_ExprContext, linkPos: S_Pos, args: V_FunctionCallArgs): V_GlobalFunctionCall {
        val callTarget = matchBase.makeCallTargetGlobal(ctx, linkPos, cFn)
        val varStates = makeVarStatesDelta(ctx, args)
        val vCall = V_CommonFunctionCall_Full(linkPos, linkPos, resType, callTarget, args, varStates)
        val vExpr: V_Expr = V_FunctionCallExpr(ctx, linkPos, null, vCall, false)
        return V_GlobalFunctionCall(vExpr, ideInfo, argIdeInfos)
    }
}

private class C_MemberLibFuncCaseMatch(
    matchBase: C_CaseMatchBase,
    matchParams: C_LibMatchParams,
    private val cFn: C_SysFunction,
    private val ideInfo: C_IdeSymbolInfo,
    private val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo>,
): C_NormalLibFuncCaseMatch<V_MemberFunctionCall>(matchBase, matchParams) {
    override fun compileCall(ctx: C_ExprContext, linkPos: S_Pos, args: V_FunctionCallArgs): V_MemberFunctionCall {
        val callTarget = matchBase.makeCallTargetMember(ctx, linkPos, cFn)
        val varStates = makeVarStatesDelta(ctx, args)
        val vCall = V_CommonFunctionCall_Full(linkPos, linkPos, resType, callTarget, args, varStates)
        return V_MemberFunctionCall_CommonCall(ctx, ideInfo, argIdeInfos, vCall, resType)
    }
}

private abstract class C_ErrorLibFuncCaseMatch<CallT: V_FunctionCall>(
    matchBase: C_CaseMatchBase,
    matchParams: C_LibMatchParams,
    protected val errCodeMsg: C_CodeMsg,
): C_BaseLibFuncCaseMatch<CallT>(matchBase, matchParams) {
    protected abstract fun compileCall0(ctx: C_ExprContext, linkPos: S_Pos): CallT

    final override fun compileCall(ctx: C_ExprContext, linkPos: S_Pos, args: V_FunctionCallArgs): CallT {
        val fnName = matchBase.fullNameLazy.value
        ctx.msgCtx.error(linkPos, errCodeMsg.code.formatOrOriginal(fnName), errCodeMsg.msg.formatOrOriginal(fnName))
        return compileCall0(ctx, linkPos)
    }
}

private class C_GlobalErrorLibFuncCaseMatch(
    matchBase: C_CaseMatchBase,
    matchParams: C_LibMatchParams,
    errCodeMsg: C_CodeMsg,
    private val ideInfo: C_IdeSymbolInfo,
    private val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo>,
): C_ErrorLibFuncCaseMatch<V_GlobalFunctionCall>(matchBase, matchParams, errCodeMsg) {
    override fun compileCall0(ctx: C_ExprContext, linkPos: S_Pos): V_GlobalFunctionCall {
        val expr = C_ExprUtils.errorVExpr(ctx, linkPos, resType)
        return V_GlobalFunctionCall(expr, ideInfo, argIdeInfos)
    }
}

private class C_MemberErrorLibFuncCaseMatch(
    matchBase: C_CaseMatchBase,
    matchParams: C_LibMatchParams,
    errCodeMsg: C_CodeMsg,
    private val ideInfo: C_IdeSymbolInfo,
): C_ErrorLibFuncCaseMatch<V_MemberFunctionCall>(matchBase, matchParams, errCodeMsg) {
    override fun compileCall0(ctx: C_ExprContext, linkPos: S_Pos): V_MemberFunctionCall {
        return V_MemberFunctionCall_Error(ctx, ideInfo, resType, errCodeMsg.msg)
    }
}

private abstract class C_LibPartialCallTarget_Common<CallT: V_FunctionCall>(
    private val params: ImmList<L_FunctionParam>,
    private val minParams: Int,
    protected val matchBase: C_CaseMatchBase,
): C_LibPartialCallTarget<CallT>(matchBase.linkPos, matchBase.fullNameLazy) {
    init {
        check(minParams >= 0)
        check(minParams <= params.size)
    }

    abstract fun compileCall0(ctx: C_ExprContext, linkPos: S_Pos, args: C_EffectivePartialArguments): CallT

    final override fun codeMsg(): C_CodeMsg {
        val name = fullName.value
        val code = "$name(${params.joinToString(",") { it.rType.strCode() }}):${matchBase.resType.strCode()}"
        val msg = "$name(${params.joinToString(", "){ it.rType.str() }}): ${matchBase.resType.str()}"
        return code toCodeMsg msg
    }

    final override fun match(): C_LibPartialCallTargetMatch<CallT> {
        return C_LibPartialCallTargetMatch_Common(true, params)
    }

    final override fun match(fnType: R_FunctionType): C_LibPartialCallTargetMatch<CallT>? {
        val paramCount = fnType.params.size
        val resParams = when {
            paramCount == params.size -> params
            paramCount >= minParams && paramCount < params.size -> params.take(paramCount)
            else -> null
        }
        resParams ?: return null

        val rSelfType = R_FunctionType(resParams.mapToImmList { it.rType }, matchBase.resType)
        if (!fnType.isAssignableFrom(rSelfType)) {
            return null
        }

        val mFnParams = fnType.params.map { it.mType }
        val matchParams = mFnParams.mapIndexedToImmList { i, type ->
            val lParam = params[i]
            val mParam = lParam.mParam.replaceType(type)
            lParam.replaceMParam(mParam)
        }

        val exact = fnType == rSelfType
        return C_LibPartialCallTargetMatch_Common(exact, matchParams)
    }

    private inner class C_LibPartialCallTargetMatch_Common(
        exact: Boolean,
        private val params: ImmList<L_FunctionParam>,
    ): C_LibPartialCallTargetMatch<CallT>(exact) {
        override fun parameters(): ImmList<C_FunctionCallParameter>? {
            if (params.any { !it.rType.isValid() }) {
                return null
            }
            return params.mapIndexedToImmList { i, param ->
                C_FunctionCallParameter(
                    name = param.name,
                    type = param.rType,
                    index = i,
                    defaultValue = null,
                    restrictions = param.restrictions,
                )
            }
        }

        override fun compileCall(ctx: C_ExprContext, args: C_EffectivePartialArguments): CallT {
            return compileCall0(ctx, matchBase.linkPos, args)
        }
    }
}

private class C_LibPartialCallTarget_Global(
    params: ImmList<L_FunctionParam>,
    minParams: Int,
    matchBase: C_CaseMatchBase,
    private val cFn: C_SysFunction,
    private val ideInfo: C_IdeSymbolInfo,
    private val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo>,
): C_LibPartialCallTarget_Common<V_GlobalFunctionCall>(params, minParams, matchBase) {
    override fun compileCall0(
        ctx: C_ExprContext,
        linkPos: S_Pos,
        args: C_EffectivePartialArguments,
    ): V_GlobalFunctionCall {
        val callTarget = matchBase.makeCallTargetGlobal(ctx, linkPos, cFn)
        val fnType = R_FunctionType(args.wildArgs, matchBase.resType)
        val mapping = args.toRMapping(ctx.msgCtx)
        val vCall: V_CommonFunctionCall = V_CommonFunctionCall_Partial(linkPos, fnType, callTarget, args.exprArgs, mapping)
        val vExpr: V_Expr = V_FunctionCallExpr(ctx, linkPos, null, vCall, false)
        return V_GlobalFunctionCall(vExpr, ideInfo, argIdeInfos)
    }
}

private class C_LibPartialCallTarget_Member(
    params: ImmList<L_FunctionParam>,
    minParams: Int,
    matchBase: C_CaseMatchBase,
    private val cFn: C_SysFunction,
    private val ideInfo: C_IdeSymbolInfo,
    private val argIdeInfos: ImmMap<Name, C_IdeSymbolInfo>,
): C_LibPartialCallTarget_Common<V_MemberFunctionCall>(params, minParams, matchBase) {
    override fun compileCall0(
        ctx: C_ExprContext,
        linkPos: S_Pos,
        args: C_EffectivePartialArguments,
    ): V_MemberFunctionCall {
        val callTarget = matchBase.makeCallTargetMember(ctx, linkPos, cFn)
        val fnType = R_FunctionType(args.wildArgs, matchBase.resType)
        val mapping = args.toRMapping(ctx.msgCtx)
        val vCall = V_CommonFunctionCall_Partial(linkPos, fnType, callTarget, args.exprArgs, mapping)
        return V_MemberFunctionCall_CommonCall(ctx, ideInfo, argIdeInfos, vCall, fnType)
    }
}

private class V_FunctionCallTarget_SysMemberFunction(
    desc: V_SysFunctionTargetDescriptor,
): V_FunctionCallTarget_SysFunction(desc) {
    override fun toRTarget(): R_FunctionCallTarget {
        return R_FunctionCallTarget_SysMemberFunction(desc.rFn, desc.fullName)
    }

    override fun toDbExpr(pos: S_Pos, dbBase: Db_Expr?, dbArgs: ImmList<Db_Expr>): Db_Expr {
        checkNotNull(dbBase)

        if (desc.dbFn == null) {
            throw C_Errors.errFunctionNoSql(pos, desc.fullName.value)
        }

        val dbFullArgs = immListOf(dbBase) + dbArgs
        return Db_CallExpr(desc.resType, desc.dbFn, dbFullArgs)
    }
}
