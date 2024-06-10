/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.expr.C_ExprVarFacts
import net.postchain.rell.base.compiler.base.expr.C_VarFacts
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

class V_WhenChooserDetails(
    val keyExpr: V_Expr?,
    val keyPostFacts: C_VarFacts,
    constantCases: Map<Rt_Value, Int>,
    variableCases: List<IndexedValue<V_Expr>>,
    val elseCase: IndexedValue<S_Pos>?,
    val full: Boolean,
    caseFacts: List<C_VarFacts>,
    val elseFacts: C_VarFacts,
) {
    val constantCases = constantCases.toImmMap()
    val variableCases = variableCases.toImmList()
    val caseFacts = caseFacts.toImmList()

    fun makeChooser(): R_WhenChooser {
        if (keyExpr == null) {
            val keyExpr = R_ConstantValueExpr.makeBool(true)
            val caseExprs = variableCases.map { IndexedValue(it.index, it.value.toRExpr()) }
            return R_IterativeWhenChooser(keyExpr, caseExprs, elseCase?.index)
        }

        val rKeyExpr = keyExpr.toRExpr()

        val chooser = if (constantCases.size == variableCases.size) {
            R_LookupWhenChooser(rKeyExpr, constantCases.toMap(), elseCase?.index)
        } else {
            val caseExprs = variableCases.map { IndexedValue(it.index, it.value.toRExpr()) }
            R_IterativeWhenChooser(rKeyExpr, caseExprs, elseCase?.index)
        }

        return chooser
    }
}

class V_WhenExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val chooserDetails: V_WhenChooserDetails,
    private val valueExprs: List<V_Expr>,
    private val resType: R_Type,
    private val resVarFacts: C_ExprVarFacts,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0(): V_ExprInfo {
        val subExprs = listOfNotNull(chooserDetails.keyExpr) + chooserDetails.variableCases.map { it.value } + valueExprs
        return V_ExprInfo.simple(resType, subExprs)
    }

    override fun varFacts0() = resVarFacts

    override fun toRExpr0(): R_Expr {
        val rChooser = chooserDetails.makeChooser()
        val rExprs = valueExprs.map { it.toRExpr() }
        return R_WhenExpr(resType, rChooser, rExprs)
    }

    override fun toDbExpr0(): Db_Expr {
        val caseCondMap = mutableMapOf<Int, MutableList<Db_Expr>>()
        for (case in chooserDetails.variableCases) caseCondMap[case.index] = mutableListOf()
        for (case in chooserDetails.variableCases) caseCondMap.getValue(case.index).add(case.value.toDbExpr())

        val keyExpr = chooserDetails.keyExpr?.toDbExpr()

        val caseExprs = caseCondMap.keys.sorted().map { idx ->
            val conds = caseCondMap.getValue(idx)
            val vExpr = valueExprs[idx]
            Db_WhenCase(conds, vExpr.toDbExpr())
        }

        val elseIdx = chooserDetails.elseCase
        if (elseIdx == null) {
            if (chooserDetails.full) {
                msgCtx.error(pos, "expr_when:no_else", "When must have an 'else' in a database expression")
            }
            return C_ExprUtils.errorDbExpr(resType)
        }

        val elseExpr = valueExprs[elseIdx.index].toDbExpr()
        return Db_WhenExpr(resType, keyExpr, caseExprs, elseExpr)
    }
}
