/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_BlockContext
import net.postchain.rell.base.compiler.base.core.C_LoopUid
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.core.C_OwnerBlockContext
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsScope
import net.postchain.rell.base.compiler.base.utils.C_IdeCompletionsScopeProvider
import net.postchain.rell.base.model.R_AtExprId
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_DbAtEntity
import net.postchain.rell.base.utils.ImmList

class C_ExprContext private constructor(
    val blkCtx: C_BlockContext,
    val varStates: C_VarStates,
    val atCtx: C_AtContext?,
    val insideGuardBlock: Boolean,
    // True when this expression is (transitively) part of a body statement of the current function
    // - as opposed to e.g. a global constant, a default value or an at-expression body. Decides
    // whether a value block used as an if/when expression arm may contain a `return`.
    val insideStmt: Boolean,
    // The nearest enclosing loop reachable without crossing a function or at-expression boundary;
    // gives `break`/`continue` inside a value block arm their loop target.
    val loop: C_LoopUid?,
): C_IdeCompletionsScopeProvider {
    val defCtx = blkCtx.defCtx
    val modCtx = defCtx.modCtx
    val nsCtx = defCtx.nsCtx
    val globalCtx = defCtx.globalCtx
    val symCtx = defCtx.symCtx
    val nameCtx = symCtx.nameCtx
    val appCtx = defCtx.appCtx
    val msgCtx = nsCtx.msgCtx
    val typeMgr = modCtx.typeMgr
    val executor = defCtx.executor

    fun makeAtEntity(rEntity: R_EntityDefinition, atExprId: R_AtExprId): R_DbAtEntity {
        return R_DbAtEntity(rEntity, appCtx.nextAtEntityId(atExprId))
    }

    fun copy(
        blkCtx: C_BlockContext = this.blkCtx,
        varStates: C_VarStates = this.varStates,
        atCtx: C_AtContext? = this.atCtx,
        insideGuardBlock: Boolean = this.insideGuardBlock,
        insideStmt: Boolean = this.insideStmt,
        loop: C_LoopUid? = this.loop,
    ): C_ExprContext {
        val insideGuardBlock2 = insideGuardBlock || this.insideGuardBlock
        return if (
            blkCtx === this.blkCtx
            && varStates === this.varStates
            && atCtx === this.atCtx
            && insideGuardBlock2 == this.insideGuardBlock
            && insideStmt == this.insideStmt
            && loop == this.loop
        ) this else C_ExprContext(
            blkCtx = blkCtx,
            varStates = varStates,
            atCtx = atCtx,
            insideGuardBlock = insideGuardBlock2,
            insideStmt = insideStmt,
            loop = loop,
        )
    }

    fun updateVarStates(delta: C_VarStatesDelta): C_ExprContext {
        val resVarStates = varStates.and(delta)
        return copy(varStates = resVarStates)
    }

    fun getDbModificationRestriction(): C_CodeMsg? {
        val r = defCtx.getDbModificationRestriction()
        return r ?: if (insideGuardBlock) {
            C_CodeMsg("no_db_update:guard", "Database modifications are not allowed inside or before a guard block")
        } else {
            null
        }
    }

    fun checkDbUpdateAllowed(pos: S_Pos) {
        val r = getDbModificationRestriction()
        if (r != null) {
            msgCtx.error(pos, r.code, r.msg)
        }
    }

    fun findWhereAttributesByName(name: C_Name) = blkCtx.lookupAtImplicitAttributesByName(this, name)

    fun findWhereAttributesByType(pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr> {
        return blkCtx.lookupAtImplicitAttributesByType(this, pos, type)
    }

    override fun ideCompletionsScope(): C_IdeCompletionsScope = blkCtx.ideCompletionsScope()

    companion object {
        fun createRoot(blkCtx: C_BlockContext) = C_ExprContext(
            blkCtx = blkCtx,
            varStates = C_VarStates.EMPTY,
            insideGuardBlock = false,
            atCtx = null,
            insideStmt = false,
            loop = null,
        )
    }
}

class C_StmtContext private constructor(
    val blkCtx: C_BlockContext,
    exprCtx: C_ExprContext,
    val loop: C_LoopUid?,
    val afterGuardBlock: Boolean = false,
    val topLevel: Boolean = false,
) {
    // Invariant: expressions compiled from statements know which loop (if any) encloses them, so a
    // value block used as an if/when expression arm gives `break`/`continue` their loop target.
    val exprCtx: C_ExprContext = exprCtx.copy(loop = loop)

    val appCtx = blkCtx.appCtx
    val fnCtx = blkCtx.fnCtx
    val defCtx = fnCtx.defCtx
    val nsCtx = defCtx.nsCtx
    val symCtx = defCtx.symCtx
    val msgCtx = nsCtx.msgCtx
    val globalCtx = defCtx.globalCtx
    val executor = defCtx.executor

    fun copy(
        blkCtx: C_BlockContext = this.blkCtx,
        exprCtx: C_ExprContext = this.exprCtx,
        loop: C_LoopUid? = this.loop,
        afterGuardBlock: Boolean = this.afterGuardBlock,
        topLevel: Boolean = this.topLevel,
    ): C_StmtContext {
        return if (blkCtx == this.blkCtx
                && exprCtx == this.exprCtx
                && loop == this.loop
                && afterGuardBlock == this.afterGuardBlock
                && topLevel == this.topLevel
        ) this else C_StmtContext(
            blkCtx = blkCtx,
            exprCtx = exprCtx,
            loop = loop,
            afterGuardBlock = afterGuardBlock,
            topLevel = topLevel,
        )
    }

    fun updateVarStates(delta: C_VarStatesDelta): C_StmtContext {
        return copy(exprCtx = exprCtx.updateVarStates(delta))
    }

    fun subBlock(loop: C_LoopUid?): Pair<C_StmtContext, C_OwnerBlockContext> {
        val subBlkCtx = blkCtx.createSubContext("blk")
        val subExprCtx = exprCtx.copy(blkCtx = subBlkCtx)
        val subCtx = copy(blkCtx = subBlkCtx, exprCtx = subExprCtx, loop = loop, topLevel = subBlkCtx.isTopLevelBlock())
        return Pair(subCtx, subBlkCtx)
    }

    fun checkDbUpdateAllowed(pos: S_Pos) {
        exprCtx.checkDbUpdateAllowed(pos)
    }

    companion object {
        fun createRoot(blkCtx: C_BlockContext): C_StmtContext {
            // Body statements set insideStmt: expressions below them may contain a value block
            // whose `return` targets this body. Expression-only roots (global constants, default
            // values) never go through here, so the flag stays false there.
            val exprCtx = C_ExprContext.createRoot(blkCtx).copy(insideStmt = true)
            return C_StmtContext(blkCtx, exprCtx, loop = null, topLevel = true)
        }

        /**
         * A statement context for statements nested inside an expression (a value block used as an
         * if/when expression arm): same frame, same var states, and the enclosing loop and
         * insideStmt inherited from the expression context.
         */
        fun forExpr(exprCtx: C_ExprContext): C_StmtContext {
            return C_StmtContext(exprCtx.blkCtx, exprCtx, loop = exprCtx.loop, topLevel = false)
        }
    }
}
