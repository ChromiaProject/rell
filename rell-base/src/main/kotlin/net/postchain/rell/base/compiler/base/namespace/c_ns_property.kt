/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.namespace

import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueExpr
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_Value

class C_NamespacePropertyContext(val exprCtx: C_ExprContext) {
    val defCtx = exprCtx.defCtx
    val globalCtx = defCtx.globalCtx
    val msgCtx = defCtx.msgCtx
    val modCtx = defCtx.modCtx
}

abstract class C_NamespaceProperty {
    abstract fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr
}

class C_NamespaceProperty_RtValue(
    private val value: Rt_Value,
): C_NamespaceProperty() {
    override fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr {
        return V_ConstantValueExpr(ctx.exprCtx, name.pos, value)
    }
}

class C_NamespaceProperty_SysFunction(
    private val resultType: R_Type,
    private val fn: C_SysFunction,
): C_NamespaceProperty() {
    override fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr {
        val body = fn.compileCall(C_SysFunctionCtx(ctx.exprCtx, name.pos))
        return C_ExprUtils.createSysGlobalPropExpr(ctx.exprCtx, resultType, body.rFn, name, pure = body.pure)
    }
}
