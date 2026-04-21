/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.fn

import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.*
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.base.utils.lateInit
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.model.GlobalConstantId
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_UnitType
import net.postchain.rell.base.utils.ImmMultimap
import net.postchain.rell.base.utils.associateNotNullValues
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.doc.DocFunctionParamComments
import net.postchain.rell.base.utils.ide.IdeCompletion

abstract class C_SubprogramHeader(
    val params: C_FormalParameters,
    val docComment: DocComment?,
)

object C_FunctionUtils {
    fun compileFunctionHeader(
        defCtx: C_DefinitionContext,
        fnPos: S_Pos,
        params: List<S_FormalParameter>,
        retType: S_Type?,
        body: S_FunctionBody?,
        comment: S_Comment?,
        ideCompsLate: C_LateInit<ImmMultimap<String, IdeCompletion>>,
    ): C_UserFunctionHeader {
        val explicitRetType = if (retType == null) null else (retType.compileOpt(defCtx) ?: R_CtErrorType)
        val bodyRetType = if (body == null) R_UnitType else null
        val rRetType = explicitRetType ?: bodyRetType

        val rawHeader = compileCommonHeader(defCtx, fnPos, params, comment, false)

        val bodyCtx = C_FunctionBodyContext(defCtx, fnPos, rRetType, rawHeader.params, ideCompsLate)
        val cBody = if (body == null) null else C_UserFunctionDeepDefinitionBody(bodyCtx, body)

        return C_UserFunctionHeader(rawHeader.params, rawHeader.comment, rRetType, cBody)
    }

    fun compileOperationHeader(
        defCtx: C_DefinitionContext,
        pos: S_Pos,
        params: List<S_FormalParameter>,
        comment: S_Comment?,
    ): C_OperationHeader {
        val commonHeader = compileCommonHeader(defCtx, pos, params, comment, true)
        return C_OperationHeader(commonHeader.params, commonHeader.comment)
    }

    fun compileQueryHeader(
        defCtx: C_DefinitionContext,
        simpleName: C_Name,
        params: List<S_FormalParameter>,
        retType: S_Type?,
        body: S_FunctionBody,
        comment: S_Comment?,
        ideCompsLate: C_LateInit<ImmMultimap<String, IdeCompletion>>,
    ): C_QueryHeader {
        val rRetType = if (retType == null) null else (retType.compileOpt(defCtx) ?: R_CtErrorType)
        val rawHeader = compileCommonHeader(defCtx, simpleName.pos, params, comment, defCtx.globalCtx.compilerOptions.gtv)
        val bodyCtx = C_FunctionBodyContext(defCtx, simpleName.pos, rRetType, rawHeader.params, ideCompsLate)
        val cBody = C_QueryDeepDefinitionBody(bodyCtx, body)
        return C_QueryHeader(rawHeader.params, rawHeader.comment, rRetType, cBody)
    }

    private fun compileCommonHeader(
        defCtx: C_DefinitionContext,
        pos: S_Pos,
        params: List<S_FormalParameter>,
        comment: S_Comment?,
        gtv: Boolean,
    ): C_RawSubprogramHeader {
        val docCommentsLate = defCtx.lateInit(C_CompilerPass.APPDEFS, DocFunctionParamComments.NULL)
        val cParams = C_FormalParameters.compile(defCtx, params, gtv, docCommentsLate.getter)

        val paramNames = cParams.list.map { it.name.rName }
        val paramComments = cParams.list.associateNotNullValues { it.name.rName to it.comment }

        val docComments = defCtx.symCtx.docSymbolFactory.compileFunctionParamComments(
            pos,
            defCtx.defName,
            comment,
            defCtx.definitionType.docKind,
            paramNames,
            paramComments,
        )
        docCommentsLate.set(docComments, allowEarly = true)

        return C_RawSubprogramHeader(cParams, docComments.functionComment)
    }

    private class C_RawSubprogramHeader(val params: C_FormalParameters, val comment: DocComment?)

    fun compileGlobalConstantHeader(
            defCtx: C_DefinitionContext,
            simpleName: C_Name,
            explicitType: S_Type?,
            expr: S_Expr,
            constId: GlobalConstantId,
    ): C_GlobalConstantHeader {
        val explicitRetType = if (explicitType == null) null else {
            val type = (explicitType.compileOpt(defCtx) ?: R_CtErrorType)
            C_Types.checkNotUnit(defCtx.msgCtx, explicitType.pos, type, simpleName.str) {
                "def:const" toCodeMsg "global constant"
            }
            type
        }

        val body = C_GlobalConstantDeepDefinitionBody(defCtx, expr, constId, explicitRetType)
        return C_GlobalConstantHeader(explicitRetType, body)
    }

    internal fun compileRegularCall(
        base: C_FunctionCallTargetBase,
        callTarget: C_FunctionCallTarget,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint
    ): V_GlobalFunctionCall {
        val res = C_FunctionCallArgsUtils.compileCall(base.ctx, args, resTypeHint, callTarget, base.argIdeInfos)
        return res ?: C_ExprUtils.errorVGlobalCall(base.ctx, base.callInfo.callPos, callTarget.retType() ?: R_CtErrorType)
    }

    fun checkParamRestrictions(
        msgCtx: C_MessageContext,
        mapping: List<C_ArgMatchParamArg>,
        restrictionsGetter: (C_ArgMatchParam) -> C_MemberRestrictions,
    ) {
        var last = C_MemberRestrictions.NULL

        for (m in mapping) {
            if (m.callArg != null) {
                val restrictions = restrictionsGetter(m.param)
                // Do not report error for each argument of the same vararg parameter.
                if (restrictions !== last) {
                    restrictions.access(msgCtx, m.callArg.value.pos)
                    last = restrictions
                }
            }
        }
    }
}
