/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.base.expr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_BlockEntry_Var
import net.postchain.rell.base.compiler.base.core.C_LocalVar
import net.postchain.rell.base.compiler.base.core.C_Name
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.C_Constants
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.compiler.vexpr.*
import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VarPtr
import net.postchain.rell.base.model.expr.R_ColAtParam
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMultimap
import net.postchain.rell.base.utils.ide.IdeCompletion
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmMultimap

internal class C_AtFrom_Iterable(
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

    override fun makeDefaultWhatFields(ctx: C_ExprContext): ImmList<V_DbAtWhatField> {
        val vExpr = compilePlaceholderRef(ctx, pos)
        val field = V_DbAtWhatField(ctx.appCtx, null, vExpr.type, vExpr, V_AtWhatFieldFlags.DEFAULT, null)
        return immListOf(field)
    }

    override fun findMembers(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromMember> {
        val base = C_AtFromBase_Iterable(ctx, name.pos)
        val selfType = base.vItemExpr.type
        val members = ctx.typeMgr.getValueMembers(selfType, name.rName)
        return members.mapToImmList { C_AtFromMember(base, selfType, it, false) }
    }

    override fun findImplicitAttributesByName(ctx: C_ExprContext, name: C_Name): ImmList<C_AtFromImplicitAttr> {
        val base = C_AtFromBase_Iterable(ctx, name.pos)
        val selfType = base.vItemExpr.type
        val members = ctx.typeMgr.getAtImplicitAttrsByName(selfType, name.rName)
        return members.mapToImmList { C_AtFromImplicitAttr(base, selfType, it) }
    }

    override fun findImplicitAttributesByType(ctx: C_ExprContext, pos: S_Pos, type: R_Type): ImmList<C_AtFromImplicitAttr> {
        val base = C_AtFromBase_Iterable(ctx, pos)
        val selfType = base.vItemExpr.type
        return ctx.typeMgr.getAtImplicitAttrsByType(selfType, type)
            .mapToImmList { C_AtFromImplicitAttr(base, selfType, it) }
    }

    override fun ideCompletions(): ImmMultimap<String, IdeCompletion> {
        val selfType = item.elemType
        val members = outerExprCtx.typeMgr.getValueMembers(selfType)
        return members
            .mapNotNull {
                val name = it.optionalName?.str
                val completion = it.ideCompletion()
                if (name == null || completion == null) null else (".$name" to completion)
            }
            .toImmMultimap()
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

    private inner class C_AtFromBase_Iterable(ctx: C_ExprContext, pos: S_Pos): C_AtFromBase() {
        val vItemExpr = compilePlaceholderRef(ctx, pos)

        override fun nameMsg(): C_CodeMsg {
            return "${placeholderVar.metaName}:${item.elemType.name}" toCodeMsg placeholderVar.metaName
        }

        override fun compile(ctx: C_ExprContext, pos: S_Pos) = vItemExpr
    }
}
