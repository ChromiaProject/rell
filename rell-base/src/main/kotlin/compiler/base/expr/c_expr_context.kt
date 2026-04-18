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
    internal val blkCtx: C_BlockContext,
    internal val varStates: C_VarStates,
    internal val atCtx: C_AtContext?,
    internal val insideGuardBlock: Boolean,
): C_IdeCompletionsScopeProvider {
    internal val defCtx = blkCtx.defCtx
    internal val modCtx = defCtx.modCtx
    internal val nsCtx = defCtx.nsCtx
    internal val globalCtx = defCtx.globalCtx
    internal val symCtx = defCtx.symCtx
    internal val nameCtx = symCtx.nameCtx
    internal val appCtx = defCtx.appCtx
    internal val msgCtx = nsCtx.msgCtx
    internal val typeMgr = modCtx.typeMgr
    internal val executor = defCtx.executor

    internal fun makeAtEntity(rEntity: R_EntityDefinition, atExprId: R_AtExprId): R_DbAtEntity {
        return R_DbAtEntity(rEntity, appCtx.nextAtEntityId(atExprId))
    }

    internal fun copy(
        blkCtx: C_BlockContext = this.blkCtx,
        varStates: C_VarStates = this.varStates,
        atCtx: C_AtContext? = this.atCtx,
        insideGuardBlock: Boolean = this.insideGuardBlock,
    ): C_ExprContext {
        val insideGuardBlock2 = insideGuardBlock || this.insideGuardBlock
        return if (
            blkCtx === this.blkCtx
            && varStates === this.varStates
            && atCtx === this.atCtx
            && insideGuardBlock2 == this.insideGuardBlock
        ) this else C_ExprContext(
            blkCtx = blkCtx,
            varStates = varStates,
            atCtx = atCtx,
            insideGuardBlock = insideGuardBlock2,
        )
    }

    internal fun updateVarStates(delta: C_VarStatesDelta): C_ExprContext {
        val resVarStates = varStates.and(delta)
        return copy(varStates = resVarStates)
    }

    internal fun getDbModificationRestriction(): C_CodeMsg? {
        val r = defCtx.getDbModificationRestriction()
        return r ?: if (insideGuardBlock) {
            C_CodeMsg("no_db_update:guard", "Database modifications are not allowed inside or before a guard block")
        } else {
            null
        }
    }

    internal fun checkDbUpdateAllowed(pos: S_Pos) {
        val r = getDbModificationRestriction()
        if (r != null) {
            msgCtx.error(pos, r.code, r.msg)
        }
    }

    internal fun findWhereAttributesByName(name: C_Name) = blkCtx.lookupAtImplicitAttributesByName(this, name)

    internal fun findWhereAttributesByType(pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr> {
        return blkCtx.lookupAtImplicitAttributesByType(this, pos, type)
    }

    override fun ideCompletionsScope(): C_IdeCompletionsScope = blkCtx.ideCompletionsScope()

    internal companion object {
        fun createRoot(blkCtx: C_BlockContext) = C_ExprContext(
            blkCtx = blkCtx,
            varStates = C_VarStates.EMPTY,
            insideGuardBlock = false,
            atCtx = null,
        )
    }
}

class C_StmtContext private constructor(
    internal val blkCtx: C_BlockContext,
    internal val exprCtx: C_ExprContext,
    internal val loop: C_LoopUid?,
    internal val afterGuardBlock: Boolean = false,
    internal val topLevel: Boolean = false,
) {
    internal val appCtx = blkCtx.appCtx
    internal val fnCtx = blkCtx.fnCtx
    internal val defCtx = fnCtx.defCtx
    internal val nsCtx = defCtx.nsCtx
    internal val symCtx = defCtx.symCtx
    internal val msgCtx = nsCtx.msgCtx
    internal val globalCtx = defCtx.globalCtx
    internal val executor = defCtx.executor

    internal fun copy(
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

    internal fun updateVarStates(delta: C_VarStatesDelta): C_StmtContext {
        return copy(exprCtx = exprCtx.updateVarStates(delta))
    }

    internal fun subBlock(loop: C_LoopUid?): Pair<C_StmtContext, C_OwnerBlockContext> {
        val subBlkCtx = blkCtx.createSubContext("blk")
        val subExprCtx = exprCtx.copy(blkCtx = subBlkCtx)
        val subCtx = copy(blkCtx = subBlkCtx, exprCtx = subExprCtx, loop = loop, topLevel = subBlkCtx.isTopLevelBlock())
        return Pair(subCtx, subBlkCtx)
    }

    fun checkDbUpdateAllowed(pos: S_Pos) {
        exprCtx.checkDbUpdateAllowed(pos)
    }

    companion object {
        internal fun createRoot(blkCtx: C_BlockContext): C_StmtContext {
            val exprCtx = C_ExprContext.createRoot(blkCtx)
            return C_StmtContext(blkCtx, exprCtx, loop = null, topLevel = true)
        }
    }
}
