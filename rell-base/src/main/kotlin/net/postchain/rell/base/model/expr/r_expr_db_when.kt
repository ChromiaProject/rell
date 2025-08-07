/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.core.C_Types
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.partitionMap

class Db_WhenCase(val conds: ImmList<Db_Expr>, val expr: Db_Expr)

internal class Db_WhenExpr(
    type: R_Type,
    private val keyExpr: Db_Expr?,
    private val cases: ImmList<Db_WhenCase>,
    private val elseExpr: Db_Expr,
): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val internalCases = mutableListOf<Pair<RedDb_Expr, Db_Expr>>()

        val matchedCase = if (keyExpr != null) {
            val redKeyExpr = keyExpr.toRedExpr(frame)
            makeRedCasesKeyed(frame, keyExpr.type, redKeyExpr, internalCases)
        } else {
            makeRedCasesGeneral(frame, internalCases)
        }

        if (matchedCase != null) {
            val redExpr = matchedCase.expr.toRedExpr(frame)
            return redExpr
        }

        val redCases = internalCases.mapToImmList { (redCond, expr) ->
            val redExpr = expr.toRedExpr(frame)
            RedDb_WhenCase(redCond, redExpr)
        }

        val redElse = elseExpr.toRedExpr(frame)
        if (redCases.isEmpty()) {
            return redElse
        }

        return RedDb_WhenExpr(redCases, redElse)
    }

    private fun makeRedCasesKeyed(
        frame: Rt_CallFrame,
        keyType: R_Type,
        redKeyExpr: RedDb_Expr,
        resCases: MutableList<Pair<RedDb_Expr, Db_Expr>>,
    ): Db_WhenCase? {
        for (case in cases) {
            val matched = makeRedCaseKeyed(frame, keyType, redKeyExpr, case, resCases)
            if (matched) {
                return case
            }
        }
        return null
    }

    private fun makeRedCaseKeyed(
        frame: Rt_CallFrame,
        keyType: R_Type,
        redKeyExpr: RedDb_Expr,
        case: Db_WhenCase,
        resCases: MutableList<Pair<RedDb_Expr, Db_Expr>>,
    ): Boolean {
        val redConds = Db_InExpr.toRedExprs(frame, redKeyExpr, case.conds)

        if (redConds == null) {
            return true
        } else if (redConds.isEmpty()) {
            return false
        }

        val (nullableConds, normalConds) = redConds
            .partitionMap {
                val nullable = C_Types.isNullOrNullable(keyType) || C_Types.isNullOrNullable(it.second)
                it.first to nullable
            }

        val redExprs = mutableListOf<RedDb_Expr>()

        if (normalConds.isNotEmpty()) {
            val redNormalExpr = RedDb_Utils.makeRedDbInExpr(redKeyExpr, normalConds, false)
            redExprs.add(redNormalExpr)
        }

        for (redCond in nullableConds) {
            val redExpr = RedDb_Utils.makeRedDbEqExpr(redKeyExpr, redCond, equal = true, nullable = true)
            redExprs.add(redExpr)
        }

        val redExpr = RedDb_Utils.makeRedDbBinaryExprChain(Db_BinaryOp_Or, redExprs)
        resCases.add(Pair(redExpr, case.expr))

        return false
    }

    private fun makeRedCasesGeneral(
        frame: Rt_CallFrame,
        resCases: MutableList<Pair<RedDb_Expr, Db_Expr>>,
    ): Db_WhenCase? {
        for (case in cases) {
            val redConds = mutableListOf<RedDb_Expr>()
            for (cond in case.conds) {
                val redCond = cond.toRedExpr(frame)
                val condValue = redCond.constantValue()
                if (condValue != null) {
                    if (condValue.asBoolean()) {
                        return case
                    }
                } else {
                    redConds.add(redCond)
                }
            }

            if (redConds.isNotEmpty()) {
                val redCond = RedDb_Utils.makeRedDbBinaryExprChain(Db_BinaryOp_Or, redConds)
                resCases.add(Pair(redCond, case.expr))
            }
        }

        return null
    }
}

private class RedDb_WhenCase(val cond: RedDb_Expr, val expr: RedDb_Expr)

private class RedDb_WhenExpr(val cases: ImmList<RedDb_WhenCase>, val elseExpr: RedDb_Expr): RedDb_Expr() {
    override fun needsEnclosing() = false

    override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
        bld.append("CASE")

        for (case in cases) {
            bld.append(" WHEN ")
            case.cond.toSql(ctx, bld, false)
            bld.append(" THEN ")
            case.expr.toSql(ctx, bld, false)
        }

        bld.append(" ELSE ")
        elseExpr.toSql(ctx, bld, false)

        bld.append(" END")
    }
}
