/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.lib

import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue_Complex
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ExprInfo
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.immListOf

class V_LazyExpr(
    exprCtx: C_ExprContext,
    private val innerExpr: V_Expr,
): V_Expr(exprCtx, innerExpr.pos) {
    private val resType: R_Type = R_LazyType(innerExpr.type)

    override fun exprInfo0() = V_ExprInfo.simple(resType, innerExpr)

    override fun toRExpr(): R_Expr {
        val rInnerExpr = innerExpr.toRExpr()
        return R_LazyExpr(resType, rInnerExpr)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun combinerInfo() = Db_AtWhatCombinerInfo.Lazy(resType)
        }

        val allExprs = immListOf(innerExpr)
        return C_DbAtWhatValue_Complex(allExprs, evaluator)
    }
}
