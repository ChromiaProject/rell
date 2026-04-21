/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_FunctionBody
import net.postchain.rell.base.compiler.base.core.C_CompilerExecutor
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_FunctionBodyContext
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.fn.C_FormalParameters
import net.postchain.rell.base.compiler.base.fn.C_FunctionCallTargetBase
import net.postchain.rell.base.compiler.base.fn.C_FunctionUtils
import net.postchain.rell.base.compiler.base.fn.C_SubprogramHeader
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.lateInit
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.doc.DocComment

class C_QueryHeader(
    params: C_FormalParameters,
    docComment: DocComment?,
    explicitType: R_Type?,
    val queryBody: C_QueryDeepDefinitionBody?,
): C_SubprogramHeader(params, docComment) {
    val deepHeader = C_DeepDefinitionHeader(C_DeclarationType.QUERY, explicitType, queryBody)

    companion object {
        val ERROR = C_QueryHeader(C_FormalParameters.EMPTY, docComment = null, explicitType = null, queryBody = null)
    }
}

class C_QueryGlobalFunction(
    executor: C_CompilerExecutor,
    val rQuery: R_QueryDefinition,
): C_GlobalFunction() {
    private val headerLate = executor.lateInit(C_CompilerPass.MEMBERS, C_QueryHeader.ERROR)

    override fun getDefMeta(): R_DefinitionMeta {
        return R_DefinitionMeta("query", rQuery.defName, mountName = rQuery.mountName)
    }

    fun setHeader(header: C_QueryHeader) {
        headerLate.set(header)
    }

    override fun compileCall0(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: ImmList<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        val header = headerLate.get()
        val retType = header.deepHeader.compileReturnType(ctx, name)
        val callTargetBase = C_FunctionCallTargetBase.forDirectFunction(ctx, name, header.params)
        val callTarget = C_FunctionCallTarget_RegularUserFunction(callTargetBase, retType, rQuery)
        return C_FunctionUtils.compileRegularCall(callTargetBase, callTarget, args, resTypeHint)
    }
}

class C_QueryDeepDefinitionBody(
    private val bodyCtx: C_FunctionBodyContext,
    private val sBody: S_FunctionBody,
): C_CommonDeepDefinitionBody<R_QueryBody>(bodyCtx.appCtx) {
    override fun returnsValue() = sBody.returnsValue()
    override fun getErrorBody() = R_UserQueryBody.ERROR
    override fun getReturnType(body: R_QueryBody) = body.retType
    override fun compileBody() = sBody.compileQuery(bodyCtx)
}
