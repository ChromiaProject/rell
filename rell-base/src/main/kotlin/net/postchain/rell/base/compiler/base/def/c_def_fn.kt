/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.ast.S_CallArgument
import net.postchain.rell.base.compiler.ast.S_FunctionBody
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.core.C_FunctionBodyContext
import net.postchain.rell.base.compiler.base.core.C_TypeHint
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.fn.*
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.compiler.base.utils.C_LateInit
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget
import net.postchain.rell.base.compiler.vexpr.V_FunctionCallTarget_RegularUserFunction
import net.postchain.rell.base.compiler.vexpr.V_GlobalFunctionCall
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.RecursionAwareCalculator
import net.postchain.rell.base.utils.RecursionAwareResult

abstract class C_GlobalFunction {
    open fun getFunctionDefinition(): R_FunctionDefinition? = null
    open fun getAbstractDescriptor(): C_AbstractFunctionDescriptor? = null
    open fun getExtendableDescriptor(): C_ExtendableFunctionDescriptor? = null
    open fun getDefMeta(): R_DefinitionMeta? = null

    abstract fun compileCall(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall
}

abstract class C_FunctionHeader(val explicitType: R_Type?, val body: C_FunctionBody?) {
    abstract val declarationType: C_DeclarationType

    fun returnType(): R_Type {
        return explicitType ?: (body?.returnType()?.value ?: R_CtErrorType)
    }
}

class C_UserFunctionHeader(
        explicitType: R_Type?,
        val params: C_FormalParameters,
        val fnBody: C_UserFunctionBody?
): C_FunctionHeader(explicitType, fnBody) {
    override val declarationType = C_DeclarationType.FUNCTION

    companion object {
        val ERROR = C_UserFunctionHeader(null, C_FormalParameters.EMPTY, null)
    }
}

abstract class C_UserGlobalFunction(
    val rFunction: R_FunctionDefinition,
): C_GlobalFunction() {
    private val headerLate = C_LateInit(C_CompilerPass.MEMBERS, C_UserFunctionHeader.ERROR)

    protected val headerGetter = headerLate.getter

    fun setHeader(header: C_UserFunctionHeader) {
        headerLate.set(header)
    }

    final override fun getFunctionDefinition() = rFunction

    protected abstract fun compileCallTarget(base: C_FunctionCallTargetBase, retType: R_Type?): C_FunctionCallTarget

    final override fun compileCall(
        ctx: C_ExprContext,
        name: LazyPosString,
        args: List<S_CallArgument>,
        resTypeHint: C_TypeHint,
    ): V_GlobalFunctionCall {
        val header = headerLate.get()
        val retType = C_FunctionUtils.compileReturnType(ctx, name, header)
        val callTargetBase = C_FunctionCallTargetBase.forDirectFunction(ctx, name, header.params)
        val callTarget = compileCallTarget(callTargetBase, retType)
        return C_FunctionUtils.compileRegularCall(callTargetBase, callTarget, args, resTypeHint)
    }
}

sealed class C_FunctionBody(bodyCtx: C_FunctionBodyContext) {
    private val retTypeCalculator = bodyCtx.appCtx.functionReturnTypeCalculator

    fun returnType(): RecursionAwareResult<R_Type> {
        val res = retTypeCalculator.calculate(this)
        return res
    }

    abstract fun calculateReturnType(): R_Type

    companion object {
        fun createReturnTypeCalculator(): RecursionAwareCalculator<C_FunctionBody, R_Type> {
            // Experimental threshold with default JRE settings is 500 (depth > 500 ==> StackOverflowError).
            return RecursionAwareCalculator(200, R_CtErrorType) {
                it.calculateReturnType()
            }
        }
    }
}

abstract class C_CommonFunctionBody<T>(protected val bodyCtx: C_FunctionBodyContext): C_FunctionBody(bodyCtx) {
    private val compileLazy: T by lazy {
        doCompile()
    }

    protected abstract fun returnsValue(): Boolean
    protected abstract fun getErrorBody(): T
    protected abstract fun getReturnType(body: T): R_Type
    protected abstract fun compileBody(): T

    final override fun calculateReturnType(): R_Type {
        bodyCtx.executor.checkPass(C_CompilerPass.EXPRESSIONS)

        if (!returnsValue()) {
            return R_UnitType
        }

        val rBody = compileLazy
        val rType = getReturnType(rBody)
        return rType
    }

    fun compile(): T {
        // Needed to put the type calculation result to the cache. If this is not done, in case of a recursion,
        // subsequently getting the return type will calculate it and emit an extra compilation error (recursion).
        returnType()
        return compileLazy
    }

    private fun doCompile(): T {
        bodyCtx.executor.checkPass(C_CompilerPass.EXPRESSIONS)

        val res = bodyCtx.appCtx.msgCtx.consumeError {
            compileBody()
        }

        return res ?: getErrorBody()
    }
}

class C_UserFunctionBody(
        bodyCtx: C_FunctionBodyContext,
        private val sBody: S_FunctionBody
): C_CommonFunctionBody<R_FunctionBody>(bodyCtx) {
    override fun returnsValue() = sBody.returnsValue()
    override fun getErrorBody() = R_FunctionBody.ERROR
    override fun getReturnType(body: R_FunctionBody) = body.type
    override fun compileBody() = sBody.compileFunction(bodyCtx)
}

class C_RegularUserGlobalFunction(
    rFunction: R_FunctionDefinition,
    private val abstractDescriptor: C_AbstractFunctionDescriptor?,
): C_UserGlobalFunction(rFunction) {
    override fun getAbstractDescriptor() = abstractDescriptor

    override fun compileCallTarget(base: C_FunctionCallTargetBase, retType: R_Type?): C_FunctionCallTarget {
        return C_FunctionCallTarget_RegularUserFunction(base, retType, rFunction)
    }
}

class C_FunctionCallTarget_RegularUserFunction(
    base: C_FunctionCallTargetBase,
    retType: R_Type?,
    private val rFunction: R_RoutineDefinition,
): C_FunctionCallTarget_Regular(base, retType) {
    override fun createVTarget(): V_FunctionCallTarget = V_FunctionCallTarget_RegularUserFunction(rFunction)
}
