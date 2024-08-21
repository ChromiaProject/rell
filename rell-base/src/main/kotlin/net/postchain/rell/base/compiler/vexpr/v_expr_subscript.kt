/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.vexpr

import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_VirtualType
import net.postchain.rell.base.compiler.base.expr.C_Destination
import net.postchain.rell.base.compiler.base.expr.C_Destination_Simple
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_VarPathItem
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.lib.type.Lib_Type_ByteArray
import net.postchain.rell.base.lib.type.Lib_Type_Text
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_TextType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.expr.*

sealed class V_CommonSubscriptKind(val resType: R_Type) {
    abstract fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr): R_Expr

    open fun canBeDbExpr() = false
    open fun compileDb(pos: S_Pos, dbBase: Db_Expr, dbKey: Db_Expr): Db_Expr = throw C_Errors.errExprDbNotAllowed(pos)

    open fun compileDestination(pos: S_Pos, rBase: R_Expr, rKey: R_Expr): R_DestinationExpr? = null
}

object V_CommonSubscriptKind_Text: V_CommonSubscriptKind(R_TextType) {
    override fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_TextSubscriptExpr(rBase, rKey)

    override fun canBeDbExpr() = true

    override fun compileDb(pos: S_Pos, dbBase: Db_Expr, dbKey: Db_Expr): Db_Expr {
        return Db_CallExpr(R_TextType, Lib_Type_Text.DB_SUBSCRIPT, listOf(dbBase, dbKey))
    }
}

object V_CommonSubscriptKind_ByteArray: V_CommonSubscriptKind(R_IntegerType) {
    override fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_ByteArraySubscriptExpr(rBase, rKey)

    override fun canBeDbExpr() = true

    override fun compileDb(pos: S_Pos, dbBase: Db_Expr, dbKey: Db_Expr): Db_Expr {
        return Db_CallExpr(R_IntegerType, Lib_Type_ByteArray.DB_SUBSCRIPT, listOf(dbBase, dbKey))
    }
}

class V_CommonSubscriptKind_List(elementType: R_Type): V_CommonSubscriptKind(elementType) {
    override fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_ListSubscriptExpr(resType, rBase, rKey)
    override fun compileDestination(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_ListSubscriptExpr(resType, rBase, rKey)
}

class V_CommonSubscriptKind_VirtualList(resType: R_Type): V_CommonSubscriptKind(resType) {
    override fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_VirtualListSubscriptExpr(resType, rBase, rKey)
}

class V_CommonSubscriptKind_Map(valueType: R_Type): V_CommonSubscriptKind(valueType) {
    override fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_MapSubscriptExpr(resType, rBase, rKey)
    override fun compileDestination(pos: S_Pos, rBase: R_Expr, rKey: R_Expr) = R_MapSubscriptExpr(resType, rBase, rKey)
}

class V_CommonSubscriptKind_VirtualMap(valueType: R_Type): V_CommonSubscriptKind(valueType) {
    override fun compileR(pos: S_Pos, rBase: R_Expr, rKey: R_Expr): R_Expr {
        val virtualValueType = S_VirtualType.virtualMemberType(resType)
        return R_VirtualMapSubscriptExpr(virtualValueType, rBase, rKey)
    }
}

sealed class V_TupleSubscriptKind {
    abstract fun compile(resType: R_Type, index: Int): R_MemberCalculator
}

object V_TupleSubscriptKind_Simple: V_TupleSubscriptKind() {
    override fun compile(resType: R_Type, index: Int) = R_MemberCalculator_TupleAttr(resType, index)
}

object V_TupleSubscriptKind_Virtual: V_TupleSubscriptKind() {
    override fun compile(resType: R_Type, index: Int) = R_MemberCalculator_VirtualTupleAttr(resType, index)
}

sealed class V_SubscriptExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    protected val baseExpr: V_Expr,
): V_Expr(exprCtx, pos)

class V_CommonSubscriptExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    baseExpr: V_Expr,
    private val keyExpr: V_Expr,
    private val kind: V_CommonSubscriptKind,
): V_SubscriptExpr(exprCtx, pos, baseExpr) {
    override fun exprInfo0() = V_ExprInfo.simple(kind.resType, baseExpr, keyExpr, canBeDbExpr = kind.canBeDbExpr())

    override fun toRExpr0(): R_Expr {
        val rBase = baseExpr.toRExpr()
        val rKey = keyExpr.toRExpr()
        return kind.compileR(pos, rBase, rKey)
    }

    override fun toDbExpr0(): Db_Expr {
        val dbBase = baseExpr.toDbExpr()
        val dbKey = keyExpr.toDbExpr()
        return kind.compileDb(pos, dbBase, dbKey)
    }

    override fun destination(): C_Destination {
        val rBase = baseExpr.toRExpr()
        val rKey = keyExpr.toRExpr()
        val dstExpr = kind.compileDestination(pos, rBase, rKey)
        if (dstExpr == null) {
            val baseType = baseExpr.type
            val type = baseType.strCode()
            throw C_Error.stop(pos, "expr_immutable:$type", "Value of type '$type' cannot be modified")
        }
        return C_Destination_Simple(dstExpr)
    }
}

class V_TupleSubscriptExpr(
    exprCtx: C_ExprContext,
    pos: S_Pos,
    baseExpr: V_Expr,
    private val kind: V_TupleSubscriptKind,
    private val resType: R_Type,
    private val index: Int,
    private val varPathItem: C_VarPathItem?,
): V_SubscriptExpr(exprCtx, pos, baseExpr) {
    override fun exprInfo0() = V_ExprInfo.simple(resType, baseExpr, canBeDbExpr = false)

    override fun toRExpr0(): R_Expr {
        val rBase = baseExpr.toRExpr()
        val calculator = kind.compile(resType, index)
        return R_MemberExpr(rBase, calculator, false)
    }

    override fun varKey() = V_ValueMemberExpr.varKey(exprCtx, baseExpr, varPathItem)
}
