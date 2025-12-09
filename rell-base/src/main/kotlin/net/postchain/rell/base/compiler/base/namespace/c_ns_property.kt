/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.namespace

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.core.C_VarId
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.expr.C_VarStateKey
import net.postchain.rell.base.compiler.base.lib.C_SysFunction
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_ConstantValueEvalContext
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ExprInfo
import net.postchain.rell.base.compiler.vexpr.V_SmartNullableExpr
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_ConstantValueExpr
import net.postchain.rell.base.runtime.Rt_Value

internal class C_NamespacePropertyContext(val exprCtx: C_ExprContext) {
    val defCtx = exprCtx.defCtx
    val globalCtx = defCtx.globalCtx
    val msgCtx = defCtx.msgCtx
    val modCtx = defCtx.modCtx
}

abstract class C_NamespaceProperty {
    internal abstract fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr
}

class C_NamespaceProperty_RtValue(
    private val value: Rt_Value,
    private val valueType: R_Type,
    varId: C_VarId?,
): C_NamespaceProperty() {
    private val varKey = if (varId == null) null else C_VarStateKey(varId)

    override fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr {
        val vExpr = V_ValuePropertyExpr(ctx.exprCtx, name.pos, value, valueType, varKey)
        return V_SmartNullableExpr.wrap(ctx.exprCtx, vExpr, "const" toCodeMsg "constant")
    }

    private class V_ValuePropertyExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        private val value: Rt_Value,
        private val valueType: R_Type,
        private val varKey: C_VarStateKey?,
    ): V_Expr(exprCtx, pos) {
        override fun exprInfo0() = V_ExprInfo.simple(valueType)
        override fun toRExpr() = R_ConstantValueExpr(type, value)
        override fun constantValue(ctx: V_ConstantValueEvalContext) = value
        override fun varKey() = varKey
    }
}

class C_NamespaceProperty_SysFunction(
    private val resultType: R_Type,
    private val fn: C_SysFunction,
    private val varId: C_VarId?,
): C_NamespaceProperty() {
    override fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr {
        val body = fn.compileCall(C_SysFunctionCtx(ctx.exprCtx, name.pos))
        return C_ExprUtils.createSysGlobalPropExpr(
            ctx.exprCtx,
            resultType,
            body.rFn,
            name,
            pure = body.pure,
            varId = varId,
        )
    }
}
