/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_CallArguments
import net.postchain.rell.base.compiler.ast.S_FunctionBody
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_FunctionBodyContext
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.fn.*
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsUtils
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget_RegularUserFunction
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocComment
import net.postchain.rell.base.utils.ide.IdeCompletion

abstract class C_GlobalFunction {
    internal open fun getFunctionDefinition(): R_FunctionDefinition? = null
    internal open fun getAbstractDescriptor(): C_AbstractFunctionDescriptor? = null
    internal open fun getExtendableDescriptor(): C_ExtendableFunctionDescriptor? = null
    internal open fun getDefMeta(): R_DefinitionMeta? = null
    internal open fun ideGetParameterCompletions(): ImmMultimap<String, IdeCompletion> = immMultimapOf()

    protected abstract fun compileCall0(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: ImmList<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall

    fun compileCall(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: S_CallArguments,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        val completionsLate = C_LateInit(C_CompilerPass.COMPLETIONS, immMultimapOf<String, IdeCompletion>())

        ctx.executor.onPass(C_CompilerPass.COMPLETIONS) {
            val completions = ideGetParameterCompletions()
            completionsLate.set(completions)
        }
        ctx.blkCtx.frameCtx.ideCompCtx.trackScope(args.posRange, ctx, completionsLate.getter)

        return compileCall0(ctx, name, args.list, resTypeHint)
    }
}

internal class C_UserFunctionHeader(
    params: C_FormalParameters,
    docComment: DocComment?,
    explicitType: R_Type?,
    val fnBody: C_UserFunctionDeepDefinitionBody?,
): C_SubprogramHeader(params, docComment) {
    val deepHeader = C_DeepDefinitionHeader(C_DeclarationType.FUNCTION, explicitType, fnBody)

    companion object {
        val ERROR = C_UserFunctionHeader(
            C_FormalParameters.EMPTY,
            docComment = null,
            explicitType = null,
            fnBody = null,
        )
    }
}

internal abstract class C_UserGlobalFunction(
    val rFunction: R_FunctionDefinition,
): C_GlobalFunction() {
    private val headerLate = C_LateInit(C_CompilerPass.MEMBERS, C_UserFunctionHeader.ERROR)

    protected val headerGetter = headerLate.getter

    fun setHeader(header: C_UserFunctionHeader) {
        headerLate.set(header)
    }

    final override fun getFunctionDefinition() = rFunction

    protected abstract fun compileCallTarget(base: C_FunctionCallTargetBase, retType: R_Type?): C_FunctionCallTarget

    final override fun compileCall0(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: ImmList<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        val header = headerLate.get()
        val retType = header.deepHeader.compileReturnType(ctx, name)
        val callTargetBase = C_FunctionCallTargetBase.forDirectFunction(ctx, name, header.params)
        val callTarget = compileCallTarget(callTargetBase, retType)
        return C_FunctionUtils.compileRegularCall(callTargetBase, callTarget, args, resTypeHint)
    }

    override fun ideGetParameterCompletions(): ImmMultimap<String, IdeCompletion> {
        return rFunction.params()
            .map {
                val comp = C_IdeCompletionsUtils.makeIdeCompletion(it.docSymbol)
                it.name.str to comp
            }
            .toImmMultimap()
    }
}

internal class C_UserFunctionDeepDefinitionBody(
    private val bodyCtx: C_FunctionBodyContext,
    private val sBody: S_FunctionBody,
): C_CommonDeepDefinitionBody<R_FunctionBody>(bodyCtx.appCtx) {
    override fun returnsValue() = sBody.returnsValue()
    override fun getErrorBody() = R_FunctionBody.ERROR
    override fun getReturnType(body: R_FunctionBody) = body.type
    override fun compileBody() = sBody.compileFunction(bodyCtx)
}

internal class C_RegularUserGlobalFunction(
    rFunction: R_FunctionDefinition,
    private val abstractDescriptor: C_AbstractFunctionDescriptor?,
): C_UserGlobalFunction(rFunction) {
    override fun getAbstractDescriptor() = abstractDescriptor

    override fun compileCallTarget(base: C_FunctionCallTargetBase, retType: R_Type?): C_FunctionCallTarget {
        return C_FunctionCallTarget_RegularUserFunction(base, retType, rFunction)
    }
}

internal class C_FunctionCallTarget_RegularUserFunction(
    base: C_FunctionCallTargetBase,
    retType: R_Type?,
    private val rFunction: R_RoutineDefinition,
): C_FunctionCallTarget_Regular(base, retType) {
    override fun createVTarget(): V_FunctionCallTarget = V_FunctionCallTarget_RegularUserFunction(rFunction)
}
