/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprVarStatesDelta
import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_ValueBlockExpr
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.ImmList

/**
 * A value block `{ stmt; ...; result }` used as an `if`/`when` expression arm. Compiled inline:
 * the statements execute in a sub-block of the enclosing function's frame (so outer locals,
 * `return`, `break`/`continue` and at-expression scope behave exactly as in a plain code block),
 * and the trailing expression's value is the block's value. [alwaysExits] marks a block with no
 * trailing expression that returns on all paths - such an arm yields no value and is skipped when
 * the surrounding conditional computes its result type.
 */
class V_ValueBlockExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    private val resType: R_Type,
    private val rStmts: ImmList<R_Statement>,
    private val rFrameBlock: R_FrameBlock,
    private val result: V_Expr?,
    private val resVarStates: C_ExprVarStatesDelta,
    val alwaysExits: Boolean,
): V_Expr(exprCtx, pos) {
    override fun exprInfo0() = V_ExprInfo.simple(
        resType,
        subExprs = listOfNotNull(result),
        canBeDbExpr = false,
    )

    override fun varStatesDelta0() = resVarStates

    override fun toRExpr(): R_Expr = R_ValueBlockExpr(resType, rStmts, result?.toRExpr(), rFrameBlock)

    // The statements' sub-expressions are already lowered to R and thus invisible to the
    // constant-expression validation traversal, so a value block cannot be proven pure - reject it
    // in global constants rather than let side effects slip through.
    override fun globalConstantRestriction() = V_GlobalConstantRestriction("value_block", "value block")

    companion object {
        fun alwaysExits(vExpr: V_Expr): Boolean = vExpr is V_ValueBlockExpr && vExpr.alwaysExits
    }
}
