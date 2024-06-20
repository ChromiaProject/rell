/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.def

import net.postchain.rell.base.compiler.base.core.C_AppContext
import net.postchain.rell.base.compiler.base.core.C_CompilerPass
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.namespace.C_DeclarationType
import net.postchain.rell.base.lib.type.R_UnitType
import net.postchain.rell.base.model.R_CtErrorType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.LazyPosString
import net.postchain.rell.base.utils.RecursionAwareCalculator
import net.postchain.rell.base.utils.RecursionAwareResult

/**
 * Deep definition is one with a possibly unspecified return type, which then has to be calculated recursively,
 * based on return types of other deep definitons. Applies to functions, queries and global constants.
 */
class C_DeepDefinitionHeader(
    private val declarationType: C_DeclarationType,
    private val explicitType: R_Type?,
    private val body: C_DeepDefinitionBody?,
) {
    fun returnType(): R_Type {
        return explicitType ?: (body?.returnType()?.value ?: R_CtErrorType)
    }

    fun compileReturnType(ctx: C_ExprContext, name: LazyPosString): R_Type? {
        if (explicitType != null) {
            return explicitType
        } else if (body == null) {
            return null
        }

        val retTypeRes = body.returnType()

        if (retTypeRes.recursion) {
            val nameStr = name.lazyStr.value
            val decTypeMsg = declarationType.capitalizedMsg
            val msg = "$decTypeMsg '$nameStr' is recursive, cannot infer the type; specify type explicitly"
            ctx.msgCtx.error(name.pos, "fn_type_recursion:$declarationType:[$nameStr]", msg)
        } else if (retTypeRes.stackOverflow) {
            val nameStr = name.lazyStr.value
            val decTypeMsg = declarationType.msg
            val msg = "Cannot infer type for $decTypeMsg '$nameStr': call chain is too long; specify type explicitly"
            ctx.msgCtx.error(name.pos, "fn_type_stackoverflow:$declarationType:[$nameStr]", msg)
        }

        return retTypeRes.value
    }
}

sealed class C_DeepDefinitionBody(appCtx: C_AppContext) {
    private val retTypeCalculator = appCtx.functionReturnTypeCalculator

    fun returnType(): RecursionAwareResult<R_Type> {
        val res = retTypeCalculator.calculate(this)
        return res
    }

    abstract fun calculateReturnType(): R_Type

    companion object {
        fun createReturnTypeCalculator(): RecursionAwareCalculator<C_DeepDefinitionBody, R_Type> {
            // Experimental threshold with default JRE settings is 500 (depth > 500 ==> StackOverflowError).
            return RecursionAwareCalculator(200, R_CtErrorType) {
                it.calculateReturnType()
            }
        }
    }
}

abstract class C_CommonDeepDefinitionBody<T>(
    private val appCtx: C_AppContext,
): C_DeepDefinitionBody(appCtx) {
    private val compileLazy: T by lazy {
        doCompile()
    }

    protected abstract fun returnsValue(): Boolean
    protected abstract fun getErrorBody(): T
    protected abstract fun getReturnType(body: T): R_Type
    protected abstract fun compileBody(): T

    final override fun calculateReturnType(): R_Type {
        appCtx.executor.checkPass(C_CompilerPass.EXPRESSIONS)

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
        appCtx.executor.checkPass(C_CompilerPass.EXPRESSIONS)

        val res = appCtx.msgCtx.consumeError {
            compileBody()
        }

        return res ?: getErrorBody()
    }
}
