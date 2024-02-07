/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.C_CreateAttributes
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.lib.type.R_ListType
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_RegularCreateExpr
import net.postchain.rell.base.model.expr.R_StructCreateExpr
import net.postchain.rell.base.model.expr.R_StructListCreateExpr

sealed class V_CreateExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        protected val entity: R_EntityDefinition
): V_Expr(exprCtx, pos)

class V_RegularCreateExpr(
        exprCtx: C_ExprContext,
        pos: S_Pos,
        entity: R_EntityDefinition,
        private val attrs: C_CreateAttributes
): V_CreateExpr(exprCtx, pos, entity) {
    override fun exprInfo0() = V_ExprInfo.simple(
            entity.type,
            attrs.explicitAttrs.map { it.expr },
            hasDbModifications = true,
            canBeDbExpr = false
    )

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("create", null)

    override fun toRExpr0(): R_Expr {
        val rAttrs = (attrs.explicitAttrs + attrs.implicitAttrs).map { it.toRAttr() }
        return R_RegularCreateExpr(entity, rAttrs)
    }
}

class V_StructCreateExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    entity: R_EntityDefinition,
    private val structType: R_StructType,
    private val structExpr: V_Expr,
): V_CreateExpr(exprCtx, pos, entity) {
    override fun exprInfo0() = V_ExprInfo.simple(
            entity.type,
            structExpr,
            hasDbModifications = true,
            canBeDbExpr = false
    )

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("create:struct", null)

    override fun toRExpr0(): R_Expr {
        val rStructExpr = structExpr.toRExpr()
        return R_StructCreateExpr(entity, structType, rStructExpr)
    }
}

class V_StructListCreateExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    entity: R_EntityDefinition,
    private val structType: R_StructType,
    private val listExpr: V_Expr,
): V_CreateExpr(exprCtx, pos, entity) {
    override fun exprInfo0() = V_ExprInfo.simple(
            R_ListType(entity.type),
            listExpr,
            hasDbModifications = true,
            canBeDbExpr = false
    )

    override fun globalConstantRestriction() = V_GlobalConstantRestriction("create:struct", null)

    override fun toRExpr0(): R_Expr {
        val rListExpr = listExpr.toRExpr()
        val resultListType = R_ListType(entity.type)
        return R_StructListCreateExpr(entity, structType, resultListType, rListExpr)
    }
}
