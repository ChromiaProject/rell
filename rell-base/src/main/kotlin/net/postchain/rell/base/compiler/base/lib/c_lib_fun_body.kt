/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.model.R_SysFunction
import net.postchain.rell.base.model.expr.Db_SysFunction
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils

class C_SysFunctionBody(val pure: Boolean, val rFn: R_SysFunction, val dbFn: Db_SysFunction?) {
    companion object {
        fun simple(
            dbFn: Db_SysFunction? = null,
            pure: Boolean = false,
            rCode: (Rt_Value) -> Rt_Value,
        ): C_SysFunctionBody {
            val rFn = C_SysFunction.rSimple(rCode)
            return direct(rFn, dbFn, pure = pure)
        }

        fun simple(
            dbFn: Db_SysFunction? = null,
            pure: Boolean = false,
            rCode: (Rt_Value, Rt_Value) -> Rt_Value,
        ): C_SysFunctionBody {
            val rFn = R_SysFunction { _, args ->
                Rt_Utils.checkEquals(args.size, 2)
                rCode(args[0], args[1])
            }
            return direct(rFn, dbFn, pure = pure)
        }

        fun direct(rFn: R_SysFunction, dbFn: Db_SysFunction? = null, pure: Boolean = false): C_SysFunctionBody {
            return C_SysFunctionBody(pure, rFn, dbFn)
        }
    }
}

class C_SysFunctionCtx(val exprCtx: C_ExprContext, val callPos: S_Pos)

abstract class C_SysFunction {
    abstract fun compileCall(ctx: C_SysFunctionCtx): C_SysFunctionBody

    companion object {
        fun rSimple(rCode: (Rt_Value) -> Rt_Value): R_SysFunction {
            return R_SysFunction { _, args ->
                Rt_Utils.checkEquals(args.size, 1)
                rCode(args[0])
            }
        }

        fun direct(body: C_SysFunctionBody): C_SysFunction {
            return C_SysFunction_Direct(body)
        }

        fun validating(cFn: C_SysFunction, validator: (C_SysFunctionCtx) -> Unit): C_SysFunction {
            return C_SysFunction_Validating(cFn, validator)
        }
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
