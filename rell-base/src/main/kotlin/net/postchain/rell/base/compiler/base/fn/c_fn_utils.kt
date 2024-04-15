/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.fn

import net.postchain.rell.base.compiler.ast.*
import net.postchain.rell.base.compiler.base.core.*
import net.postchain.rell.base.compiler.base.def.*
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_DefinitionName
import net.postchain.rell.base.model.R_GlobalConstantId
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.LazyPosString

object C_FunctionUtils {
    fun compileFunctionHeader(
        defCtx: C_DefinitionContext,
        fnPos: S_Pos,
        defName: R_DefinitionName,
        params: List<S_FormalParameter>,
        retType: S_Type?,
        body: S_FunctionBody?
    ): C_UserFunctionHeader {
        val explicitRetType = if (retType == null) null else (retType.compileOpt(defCtx) ?: R_CtErrorType)
        val bodyRetType = if (body == null) R_UnitType else null
        val rRetType = explicitRetType ?: bodyRetType

        val cParams = C_FormalParameters.compile(defCtx, params, false)

        val cBody = if (body == null) null else {
            val bodyCtx = C_FunctionBodyContext(defCtx, fnPos, defName, rRetType, cParams)
            C_UserFunctionBody(bodyCtx, body)
        }

        return C_UserFunctionHeader(rRetType, cParams, cBody)
    }

    fun compileQueryHeader(
        defCtx: C_DefinitionContext,
        simpleName: S_Name,
        defName: R_DefinitionName,
        params: List<S_FormalParameter>,
        retType: S_Type?,
        body: S_FunctionBody
    ): C_QueryFunctionHeader {
        val rRetType = if (retType == null) null else (retType.compileOpt(defCtx) ?: R_CtErrorType)
        val cParams = C_FormalParameters.compile(defCtx, params, defCtx.globalCtx.compilerOptions.gtv)
        val bodyCtx = C_FunctionBodyContext(defCtx, simpleName.pos, defName, rRetType, cParams)
        val cBody = C_QueryFunctionBody(bodyCtx, body)
        return C_QueryFunctionHeader(rRetType, cParams, cBody)
    }

    fun compileGlobalConstantHeader(
        defCtx: C_DefinitionContext,
        simpleName: C_Name,
        defName: R_DefinitionName,
        explicitType: S_Type?,
        expr: S_Expr,
        constId: R_GlobalConstantId
    ): C_GlobalConstantFunctionHeader {
        val explicitRetType = if (explicitType == null) null else {
            val type = (explicitType.compileOpt(defCtx) ?: R_CtErrorType)
            C_Types.checkNotUnit(defCtx.msgCtx, explicitType.pos, type, simpleName.str) {
                "def:const" toCodeMsg "global constant"
            }
            type
        }

        val bodyCtx = C_FunctionBodyContext(defCtx, simpleName.pos, defName, explicitRetType, C_FormalParameters.EMPTY)
        val body = C_GlobalConstantFunctionBody(bodyCtx, expr, constId)
        return C_GlobalConstantFunctionHeader(explicitRetType, body)
    }

    fun compileRegularCall(
        base: C_FunctionCallTargetBase,
        callTarget: C_FunctionCallTarget,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint
    ): V_GlobalFunctionCall {
        val res = C_FunctionCallArgsUtils.compileCall(base.ctx, args, resTypeHint, callTarget, base.argIdeInfos)
        return res ?: C_ExprUtils.errorVGlobalCall(base.ctx, base.callInfo.callPos, callTarget.retType() ?: R_CtErrorType)
    }

    fun compileReturnType(ctx: C_ExprContext, name: LazyPosString, header: C_FunctionHeader): R_Type? {
        if (header.explicitType != null) {
            return header.explicitType
        } else if (header.body == null) {
            return null
        }

        val decType = header.declarationType
        val retTypeRes = header.body.returnType()

        if (retTypeRes.recursion) {
            val nameStr = name.lazyStr.value
            ctx.msgCtx.error(name.pos, "fn_type_recursion:$decType:[$nameStr]",
                    "${decType.capitalizedMsg} '$nameStr' is recursive, cannot infer the type; specify type explicitly")
        } else if (retTypeRes.stackOverflow) {
            val nameStr = name.lazyStr.value
            ctx.msgCtx.error(name.pos, "fn_type_stackoverflow:$decType:[$nameStr]",
                    "Cannot infer type for ${decType.msg} '$nameStr': call chain is too long; specify type explicitly")
        }

        return retTypeRes.value
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
