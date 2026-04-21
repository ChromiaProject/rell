/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.stmt

import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VarPtr
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.toImmList

abstract class R_Statement

object R_EmptyStatement: R_Statement()

sealed class R_VarDeclarator

class R_SimpleVarDeclarator(
    val ptr: R_VarPtr,
    val type: R_Type,
    val adapter: R_TypeAdapter,
): R_VarDeclarator()

class R_TupleVarDeclarator(
    val subDeclarators: ImmList<R_VarDeclarator>,
): R_VarDeclarator()

data object R_WildcardVarDeclarator: R_VarDeclarator()

class R_VarStatement(
    val declarator: R_VarDeclarator,
    val expr: R_Expr?,
): R_Statement()

class R_ReturnStatement(val expr: R_Expr?): R_Statement()

class R_BlockStatement(
    val stmts: ImmList<R_Statement>,
    val frameBlock: R_FrameBlock,
): R_Statement() {
    fun getGuardStmts(): R_BlockStatement? {
        val guardStmts = stmts.dropLastWhile { it !is R_GuardStatement }.toImmList()
        return if (guardStmts.isEmpty()) null else R_BlockStatement(guardStmts, frameBlock)
    }
}

class R_ExprStatement(val expr: R_Expr): R_Statement()

class R_ReplExprStatement(val expr: R_Expr): R_Statement()

class R_AssignStatement(
    val dstExpr: R_DestinationExpr,
    val expr: R_Expr,
    val op: R_BinaryOp?,
): R_Statement()

class R_IfStatement(
    val expr: R_Expr,
    val trueStmt: R_Statement,
    val falseStmt: R_Statement,
): R_Statement()

class R_WhenStatement(
    val chooser: R_WhenChooser,
    val stmts: ImmList<R_Statement>,
): R_Statement()

class R_WhileStatement(
    val expr: R_Expr,
    val stmt: R_Statement,
    val frameBlock: R_FrameBlock,
): R_Statement()

sealed class R_IterableAdapter

object R_IterableAdapter_Direct: R_IterableAdapter()

object R_IterableAdapter_LegacyMap: R_IterableAdapter()

class R_ForStatement(
    val varDeclarator: R_VarDeclarator,
    val expr: R_Expr,
    val iterator: R_IterableAdapter,
    val stmt: R_Statement,
    val frameBlock: R_FrameBlock,
): R_Statement()

class R_BreakStatement: R_Statement()

class R_ContinueStatement: R_Statement()

class R_GuardStatement(val subStmt: R_Statement): R_Statement()

class R_LambdaStatement(
    val args: ImmList<Pair<R_Expr, R_VarPtr>>,
    val block: R_FrameBlock,
    val stmt: R_Statement,
): R_Statement()
