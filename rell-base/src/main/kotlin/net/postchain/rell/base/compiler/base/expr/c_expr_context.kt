/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_BlockContext
import net.postchain.rell.base.compiler.base.core.C_LoopUid
import net.postchain.rell.base.compiler.base.core.C_OwnerBlockContext
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.model.R_AtExprId
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.R_DbAtEntity

class C_ExprContext private constructor(
    val blkCtx: C_BlockContext,
    val factsCtx: C_VarFactsContext,
    val atCtx: C_AtContext?,
    val insideGuardBlock: Boolean,
) {
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

    val docFactory = globalCtx.docFactory

    fun makeAtEntity(rEntity: R_EntityDefinition, atExprId: R_AtExprId) = R_DbAtEntity(rEntity, appCtx.nextAtEntityId(atExprId))

    fun update(
        blkCtx: C_BlockContext = this.blkCtx,
        factsCtx: C_VarFactsContext = this.factsCtx,
        atCtx: C_AtContext? = this.atCtx,
        insideGuardBlock: Boolean = this.insideGuardBlock,
    ): C_ExprContext {
        val insideGuardBlock2 = insideGuardBlock || this.insideGuardBlock
        return if (
                blkCtx === this.blkCtx
                && factsCtx === this.factsCtx
                && atCtx === this.atCtx
                && insideGuardBlock2 == this.insideGuardBlock
        ) this else C_ExprContext(
                blkCtx = blkCtx,
                factsCtx = factsCtx,
                atCtx = atCtx,
                insideGuardBlock = insideGuardBlock2
        )
    }

    fun updateFacts(facts: C_VarFactsAccess): C_ExprContext {
        if (facts.isEmpty()) return this
        return update(factsCtx = this.factsCtx.sub(facts))
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

    fun findWhereAttributesByName(name: R_Name) = blkCtx.lookupAtImplicitAttributesByName(name)
    fun findWhereAttributesByType(type: R_Type) = blkCtx.lookupAtImplicitAttributesByType(type)

    companion object {
        fun createRoot(blkCtx: C_BlockContext) = C_ExprContext(
                blkCtx = blkCtx,
                factsCtx = C_VarFactsContext.EMPTY,
                insideGuardBlock = false,
                atCtx = null
        )
    }
}

class C_StmtContext private constructor(
        val blkCtx: C_BlockContext,
        val exprCtx: C_ExprContext,
        val loop: C_LoopUid?,
        val afterGuardBlock: Boolean = false,
        val topLevel: Boolean = false
) {
    val appCtx = blkCtx.appCtx
    val fnCtx = blkCtx.fnCtx
    val defCtx = fnCtx.defCtx
    val nsCtx = defCtx.nsCtx
    val symCtx = defCtx.symCtx
    val msgCtx = nsCtx.msgCtx
    val globalCtx = defCtx.globalCtx
    val executor = defCtx.executor

    fun update(
            blkCtx: C_BlockContext? = null,
            exprCtx: C_ExprContext? = null,
            loop: C_LoopUid? = null,
            afterGuardBlock: Boolean? = null,
            topLevel: Boolean? = null
    ): C_StmtContext {
        val blkCtx2 = blkCtx ?: this.blkCtx
        val exprCtx2 = exprCtx ?: this.exprCtx
        val loop2 = loop ?: this.loop
        val afterGuardBlock2 = afterGuardBlock ?: this.afterGuardBlock
        val topLevel2 = topLevel ?: this.topLevel
        return if (blkCtx2 == this.blkCtx
                && exprCtx2 == this.exprCtx
                && loop2 == this.loop
                && afterGuardBlock2 == this.afterGuardBlock
                && topLevel2 == this.topLevel
        ) this else C_StmtContext(
                blkCtx = blkCtx2,
                exprCtx = exprCtx2,
                loop = loop2,
                afterGuardBlock = afterGuardBlock2,
                topLevel = topLevel2
        )
    }

    fun updateFacts(facts: C_VarFactsAccess): C_StmtContext {
        return update(exprCtx = exprCtx.updateFacts(facts))
    }

    fun subBlock(loop: C_LoopUid?): Pair<C_StmtContext, C_OwnerBlockContext> {
        val subBlkCtx = blkCtx.createSubContext("blk")
        val subExprCtx = exprCtx.update(blkCtx = subBlkCtx)
        val subCtx = update(blkCtx = subBlkCtx, exprCtx = subExprCtx, loop = loop, topLevel = subBlkCtx.isTopLevelBlock())
        return Pair(subCtx, subBlkCtx)
    }

    fun checkDbUpdateAllowed(pos: S_Pos) {
        exprCtx.checkDbUpdateAllowed(pos)
    }

    companion object {
        fun createRoot(blkCtx: C_BlockContext): C_StmtContext {
            val exprCtx = C_ExprContext.createRoot(blkCtx)
            return C_StmtContext(blkCtx, exprCtx, loop = null, topLevel = true)
        }
    }
}
