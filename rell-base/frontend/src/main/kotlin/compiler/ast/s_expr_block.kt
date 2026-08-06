/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.core.C_BlockCodeBuilder
import net.postchain.rell.base.compiler.base.core.C_BlockCodeProto
import net.postchain.rell.base.compiler.base.core.C_StatementVarsBlock
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_FeatureRestrictions
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_ValueBlockExpr
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_NothingType
import net.postchain.rell.base.model.R_UnitType
import net.postchain.rell.base.utils.MutableTypedKeyMap

/**
 * A value block `{ stmt; ...; result }` used as an arm of an `if` or `when` expression (the only
 * positions where the grammar produces it). Compiled inline, as a sub-block of the enclosing
 * function's frame - like a plain code block, plus a trailing result expression:
 *
 * - outer local variables are visible and assignable, and at-expression scope applies;
 * - `return` returns from the enclosing function (where a plain block could), and
 *   `break`/`continue` bind to the enclosing loop;
 * - the trailing expression (no `;`) is the block's value; a block with no trailing expression
 *   that returns on all paths yields no value, and the surrounding conditional takes its result
 *   type from the other arms.
 */
internal class S_ValueBlockExpr(val block: S_LambdaBody_Block): S_Expr(block.posRange.start) {
    override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        RESTRICTIONS_VALUE_BLOCK.access(ctx.msgCtx, startPos)

        val stmtCtx = C_StmtContext.forExpr(ctx)
        val (subCtx, subBlkCtx) = stmtCtx.subBlock(stmtCtx.loop)

        val hasGuardBlock = block.stmts.any { it is S_GuardStatement }
        val builder = C_BlockCodeBuilder(
            subCtx,
            repl = false,
            hasGuardBlock = hasGuardBlock,
            posRange = block.posRange,
            proto = C_BlockCodeProto.EMPTY,
        )
        for (stmt in block.stmts) {
            builder.add(stmt)
        }
        val blockCode = builder.build()

        val result = block.result
        var vResult: V_Expr? = null
        if (result != null) {
            if (blockCode.alwaysReturns) {
                ctx.msgCtx.error(result.startPos, "stmt_deadcode", "Dead code")
            }
            val resExprCtx = subCtx.exprCtx.updateVarStates(blockCode.varStatesDelta)
            vResult = result.compileSafe(resExprCtx, C_ExprHint(hint.typeHint)).vExpr()
        }

        val frameBlock = subBlkCtx.buildBlock()

        val alwaysExits = vResult == null && blockCode.alwaysReturns
        // An always-exiting block yields no value; its type is the bottom type, which the
        // surrounding conditional's common-type computation absorbs.
        val resType = vResult?.type ?: if (alwaysExits) R_NothingType else R_UnitType

        val resVarStates = if (vResult == null) {
            C_ExprVarStatesDelta.make(always = blockCode.varStatesDelta)
        } else {
            val resultStates = vResult.varStatesDelta
            C_ExprVarStatesDelta.make(
                always = blockCode.varStatesDelta.and(resultStates.always),
                whenTrue = resultStates.whenTrue,
                whenFalse = resultStates.whenFalse,
            )
        }

        val vExpr = V_ValueBlockExpr(
            ctx,
            startPos,
            resType,
            blockCode.rStmts,
            frameBlock.rBlock,
            vResult,
            resVarStates,
            alwaysExits,
        )
        return C_ValueExpr(vExpr)
    }

    override fun discoverVars(map: MutableTypedKeyMap): Set<Name> {
        val varsBlock = C_StatementVarsBlock()
        for (stmt in block.stmts) {
            val vars = stmt.discoverVars(map)
            varsBlock.declared(vars.declared)
            varsBlock.modified(vars.modified)
        }
        if (block.result != null) {
            varsBlock.modified(block.result.discoverVars(map))
        }
        return varsBlock.modified()
    }

    companion object {
        private val RESTRICTIONS_VALUE_BLOCK = C_FeatureRestrictions.make(
            "0.16.1",
            "expr_value_block" toCodeMsg "Value blocks as if/when arms are",
        )
    }
}
