/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.model.expr.Db_SysFunction

/**
 * [rFn] is opaque — at runtime it's `R_SysFunction`, but the compiler doesn't reference that type.
 * Typed factory methods are in the runtime module (extension functions on the companion).
 */
class C_SysFunctionBody(val pure: Boolean, val rFn: Any, val dbFn: Db_SysFunction?) {
    companion object {
        fun direct(rFn: Any, dbFn: Db_SysFunction? = null, pure: Boolean = false): C_SysFunctionBody =
            C_SysFunctionBody(pure, rFn, dbFn)
    }
}

class C_SysFunctionCtx(val exprCtx: C_ExprContext, val callPos: S_Pos)

abstract class C_SysFunction {
    abstract fun compileCall(ctx: C_SysFunctionCtx): C_SysFunctionBody

    companion object {
        fun direct(body: C_SysFunctionBody): C_SysFunction = C_SysFunction_Direct(body)

        fun validating(cFn: C_SysFunction, validator: (C_SysFunctionCtx) -> Unit): C_SysFunction =
            C_SysFunction_Validating(cFn, validator)
    }
}

private class C_SysFunction_Direct(private val body: C_SysFunctionBody): C_SysFunction() {
    override fun compileCall(ctx: C_SysFunctionCtx) = body
}

private class C_SysFunction_Validating(
    private val cFn: C_SysFunction,
    private val validator: (C_SysFunctionCtx) -> Unit
): C_SysFunction() {
    override fun compileCall(ctx: C_SysFunctionCtx): C_SysFunctionBody {
        validator(ctx)
        return cFn.compileCall(ctx)
    }
}
