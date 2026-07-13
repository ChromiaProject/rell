/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.ast

import net.postchain.rell.base.compiler.base.expr.C_Expr
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprHint
import net.postchain.rell.base.compiler.base.expr.C_ExprVarStatesDelta
import net.postchain.rell.base.compiler.base.expr.C_StmtContext
import net.postchain.rell.base.compiler.base.expr.C_ValueExpr
import net.postchain.rell.base.compiler.vexpr.V_ValueBlockExpr
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_NothingType
import net.postchain.rell.base.model.stmt.R_BreakStatement
import net.postchain.rell.base.model.stmt.R_ContinueStatement
import net.postchain.rell.base.model.stmt.R_Statement
import net.postchain.rell.base.utils.MutableTypedKeyMap
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immSetOf

/**
 * A jump (`return`/`break`/`continue`) used as an expression: an if/when arm, a value block's
 * trailing expression, the right operand of `?:`, etc. Its type is the non-denotable bottom type
 * [R_NothingType] - evaluating it never produces a value, it escapes the enclosing statement.
 *
 * Compiled as a value block whose only statement is the jump (`return x` = `{ return x; }` with
 * no trailing expression), reusing the value-block escape machinery end to end: the same
 * placement rules apply (a jump expression is rejected where no enclosing body statement or loop
 * exists - at-expression bodies, default values, global constants), and the same runtime unwinds
 * it at the nearest statement boundary.
 */
internal sealed class S_JumpExpr(pos: S_Pos): S_Expr(pos) {
    /** Compiles the jump to its statement form, reporting placement errors via the context. */
    protected abstract fun compileJump(ctx: C_StmtContext): R_Statement

    protected open fun operandExpr(): S_Expr? = null

    final override fun compile(ctx: C_ExprContext, hint: C_ExprHint): C_Expr {
        val stmtCtx = C_StmtContext.forExpr(ctx)
        val (subCtx, subBlkCtx) = stmtCtx.subBlock(stmtCtx.loop)
        val rStmt = compileJump(subCtx)
        val frameBlock = subBlkCtx.buildBlock()

        val vExpr = V_ValueBlockExpr(
            ctx,
            startPos,
            R_NothingType,
            immListOf(rStmt),
            frameBlock.rBlock,
            result = null,
            resVarStates = C_ExprVarStatesDelta.EMPTY,
            alwaysExits = true,
        )
        return C_ValueExpr(vExpr)
    }

    final override fun discoverVars(map: MutableTypedKeyMap): Set<Name> {
        return operandExpr()?.discoverVars(map) ?: immSetOf()
    }
}

internal class S_ReturnExpr(pos: S_Pos, private val expr: S_Expr?): S_JumpExpr(pos) {
    override fun compileJump(ctx: C_StmtContext): R_Statement {
        return S_ReturnStatement.compileReturn(ctx, startPos, expr)
    }

    override fun operandExpr() = expr
}

internal class S_BreakExpr(pos: S_Pos): S_JumpExpr(pos) {
    override fun compileJump(ctx: C_StmtContext): R_Statement {
        if (ctx.loop == null) {
            ctx.msgCtx.error(startPos, "stmt_break_noloop", "Break without a loop")
        }
        return R_BreakStatement()
    }
}

internal class S_ContinueExpr(pos: S_Pos): S_JumpExpr(pos) {
    override fun compileJump(ctx: C_StmtContext): R_Statement {
        if (ctx.loop == null) {
            ctx.msgCtx.error(startPos, "stmt_continue_noloop", "Continue without a loop")
        }
        return R_ContinueStatement()
    }
}
