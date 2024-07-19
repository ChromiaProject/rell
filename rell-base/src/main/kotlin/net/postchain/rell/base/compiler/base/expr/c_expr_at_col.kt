/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_BlockEntry_Var
import net.postchain.rell.base.compiler.base.core.C_LocalVar
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Constants
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VarPtr
import net.postchain.rell.base.model.expr.R_ColAtParam
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

class C_AtFrom_Iterable(
    outerExprCtx: C_ExprContext,
    fromCtx: C_AtFromContext,
    fromBlock: R_FrameBlock?,
    private val item: C_AtFromItem_Iterable,
): C_AtFrom(outerExprCtx, fromCtx, fromBlock) {
    private val pos = fromCtx.pos

    private val placeholderVar: C_LocalVar = let {
        val alias = item.alias
        val metaName = alias?.str ?: C_Constants.AT_PLACEHOLDER
        innerBlkCtx.newLocalVar(metaName, alias?.rName, item.elemType, false, atExprId)
    }

    private val varPtr: R_VarPtr = let {
        val phEntry = C_BlockEntry_Var(placeholderVar, item.aliasIdeDef.refInfo)

        val alias = item.alias
        if (alias == null) {
            innerBlkCtx.addAtPlaceholder(phEntry)
        } else {
            innerBlkCtx.addEntry(alias.pos, alias.rName, true, phEntry)
        }

        placeholderVar.toRef(innerBlkCtx.blockUid).ptr
    }

    private val innerExprCtx: C_ExprContext = let {
        outerExprCtx
            .updateVarStates(C_VarStatesDelta.changed(placeholderVar.varKey))
            .copy(blkCtx = innerBlkCtx, atCtx = innerAtCtx)
    }

    override fun getAllExprs() = immListOf(item.vExpr)
    override fun innerExprCtx() = innerExprCtx

    override fun makeDefaultWhatFields(): List<V_DbAtWhatField> {
        val vExpr = compilePlaceholderRef(innerExprCtx, pos)
        val field = V_DbAtWhatField(outerExprCtx.appCtx, null, vExpr.type, vExpr, V_AtWhatFieldFlags.DEFAULT, null)
        return immListOf(field)
    }

    override fun findMembers(name: R_Name): List<C_AtFromMember> {
        val base = C_AtFromBase_Iterable()
        val selfType = item.elemType
        val members = innerExprCtx.typeMgr.getValueMembers(selfType, name)
        return members.map { C_AtFromMember(base, selfType, it, false) }.toImmList()
    }

    override fun findImplicitAttributesByName(name: R_Name): List<C_AtFromImplicitAttr> {
        val base = C_AtFromBase_Iterable()
        val selfType = item.elemType
        val members = innerExprCtx.typeMgr.getAtImplicitAttrsByName(selfType, name)
        return members
            .map { C_AtFromImplicitAttr(base, selfType, it) }
            .toImmList()
    }

    override fun findImplicitAttributesByType(type: R_Type): List<C_AtFromImplicitAttr> {
        val base = C_AtFromBase_Iterable()
        val selfType = item.elemType
        return innerExprCtx.typeMgr.getAtImplicitAttrsByType(selfType, type)
            .map { C_AtFromImplicitAttr(base, selfType, it) }
            .toImmList()
    }

    private fun compilePlaceholderRef(ctx: C_ExprContext, pos: S_Pos): V_Expr {
        return C_BlockEntry_Var.compile0(ctx, pos, placeholderVar)
    }

    override fun compile(details: C_AtDetails): V_Expr {
        val rParam = R_ColAtParam(item.elemType, varPtr)
        val vFrom = item.compile(fromBlock)
        val what = compileColWhat(details, details.base.what.allFields)
        val extras = V_AtExprExtras(details.limit, details.offset)

        val cBlock = innerBlkCtx.buildBlock()

        return V_ColAtExpr(
            outerExprCtx,
            details.startPos,
            result = details.res,
            from = vFrom,
            what = what,
            where = details.base.where,
            cardinality = details.cardinality.value,
            extras = extras,
            block = cBlock.rBlock,
            param = rParam,
            resVarStates = details.varStatesDelta,
        )
    }

    override fun compileJoin(details: C_AtDetails, isOuter: Boolean): C_AtFromItem {
        msgCtx.error(pos, "expr:at:join:iterable", "Cannot use a collection-at-expression as a join")
        return item
    }

    private inner class C_AtFromBase_Iterable: C_AtFromBase() {
        override fun nameMsg(): C_CodeMsg {
            return "${placeholderVar.metaName}:${item.elemType.name}" toCodeMsg placeholderVar.metaName
        }

        override fun compile(ctx: C_ExprContext, pos: S_Pos): V_Expr {
            return compilePlaceholderRef(ctx, pos)
        }
    }
}
