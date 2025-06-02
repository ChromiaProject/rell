/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue
import net.postchain.rell.base.compiler.base.expr.C_DbAtWhatValue_Complex
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lib.type.Rt_MapValue
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
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

    override fun toRExpr0(): R_Expr {
        val rExprs = elems.mapToImmList { it.toRExpr() }
        return R_ListLiteralExpr(listType, rExprs)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                val values2 = values.map { it.value() }.toMutableList()
                return Rt_ListValue(listType, values2)
            }
        }
        return C_DbAtWhatValue_Complex(elems, evaluator)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val values = elems.mapNotNull { it.constantValue(ctx) }
        if (values.size != elems.size) return null
        return Rt_ListValue(listType, values.toMutableList())
    }
}

class V_MapLiteralExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        val entries: ImmList<Pair<V_Expr, V_Expr>>,
        private val mapType: R_MapType
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(mapType, entries.flatMap { it.toList() }, canBeDbExpr = false)

    override fun toRExpr0(): R_Expr {
        val rEntries = entries.mapToImmList { it.first.toRExpr() to it.second.toRExpr() }
        return R_MapLiteralExpr(mapType, rEntries)
    }

    override fun toDbExprWhat0(): C_DbAtWhatValue {
        val vExprs = entries.flatMapToImmList { it.toList() }

        val evaluator = object: Db_ComplexAtWhatEvaluator() {
            override fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value {
                check(values.size % 2 == 0) { values.size }
                val map = values.indices.step(2)
                        .associate {
                            val key = values[it].value()
                            val value = values[it + 1].value()
                            key to value
                        }
                        .toMutableMap()
                return Rt_MapValue(mapType, map)
            }
        }

        return C_DbAtWhatValue_Complex(vExprs, evaluator)
    }

    override fun constantValue(ctx: V_ConstantValueEvalContext): Rt_Value? {
        val values = entries.map { (k, v) -> Pair(k.constantValue(ctx), v.constantValue(ctx)) }
        if (values.any { (k, v) -> k == null || v == null }) return null
        return Rt_MapValue(mapType, values.associate { (k, v) -> k!! to v!! }.toMutableMap())
    }
}
