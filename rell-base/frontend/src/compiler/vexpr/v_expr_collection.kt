/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue_Complex
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.model.R_ListType
import net.postchain.rell.base.model.R_MapType
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.flatMapToImmList
import net.postchain.rell.base.utils.mapToImmList

class V_ListLiteralExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    val elems: ImmList<V_Expr>,
    private val listType: R_ListType
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(listType, elems, canBeDbExpr = false)

    override fun toRExpr(): R_Expr {
        val rExprs = elems.mapToImmList { it.toRExpr() }
        return R_ListLiteralExpr(listType, rExprs)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun combinerInfo() = Db_AtWhatCombinerInfo.ListLiteral(listType)
        }
        return C_DbAtWhatValue_Complex(elems, evaluator)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? = null
}

class V_MapLiteralExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    val entries: ImmList<Pair<V_Expr, V_Expr>>,
    private val mapType: R_MapType,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(mapType, entries.flatMap { it.toList() }, canBeDbExpr = false)

    override fun toRExpr(): R_Expr {
        val rEntries = entries.mapToImmList { it.first.toRExpr() to it.second.toRExpr() }
        val errPos = pos.toErrorPos()
        return R_MapLiteralExpr(mapType, rEntries, errPos)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val vExprs = entries.flatMapToImmList { it.toList() }

        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun combinerInfo() = Db_AtWhatCombinerInfo.MapLiteral(mapType)
        }

        return C_DbAtWhatValue_Complex(vExprs, evaluator)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): RR_ConstantValue? = null
}
