/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.stmt

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.Db_Expr
import net.postchain.rell.base.model.expr.R_AtCardinality
import net.postchain.rell.base.model.expr.R_DbAtEntity
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

sealed class R_UpdateTarget {
    abstract fun entity(): R_DbAtEntity
    abstract fun extraEntities(): List<R_DbAtEntity>
    abstract fun where(): Db_Expr?
}

class R_UpdateTarget_Simple(
    val entity: R_DbAtEntity,
    val extraEntities: ImmList<R_DbAtEntity>,
    val cardinality: R_AtCardinality,
    val where: Db_Expr?,
): R_UpdateTarget() {
    init {
        val intersect = extraEntities.filter { it.id == entity.id }
        check(intersect.isEmpty()) { "Extra entities contain main entity: ${entity.id}" }
    }

    override fun entity() = entity
    override fun extraEntities() = extraEntities
    override fun where() = where
}

sealed class R_UpdateTarget_Expr(
    val entity: R_DbAtEntity,
    extraEntities: List<R_DbAtEntity>,
    private val where: Db_Expr,
    val expr: R_Expr,
    val lambda: R_LambdaBlock,
): R_UpdateTarget() {
    private val extraEntities: ImmList<R_DbAtEntity> = extraEntities.toImmList()

    final override fun entity() = entity
    final override fun extraEntities() = extraEntities
    final override fun where() = where
}

class R_UpdateTarget_Expr_One(
    entity: R_DbAtEntity,
    extraEntities: List<R_DbAtEntity>,
    where: Db_Expr,
    expr: R_Expr,
    lambda: R_LambdaBlock,
): R_UpdateTarget_Expr(entity, extraEntities, where, expr, lambda)

class R_UpdateTarget_Expr_Many(
    entity: R_DbAtEntity,
    where: Db_Expr,
    expr: R_Expr,
    lambda: R_LambdaBlock,
    val set: Boolean,
    val listType: R_Type,
): R_UpdateTarget_Expr(entity, immListOf(), where, expr, lambda)

class R_UpdateTarget_Object(val entity: R_DbAtEntity): R_UpdateTarget() {
    override fun entity() = entity
    override fun extraEntities(): List<R_DbAtEntity> = listOf()
    override fun where() = null
}

class R_UpdateStatementWhat(val attr: R_Attribute, val expr: Db_Expr)

sealed class R_BaseUpdateStatement(
        val target: R_UpdateTarget,
        val fromBlock: R_FrameBlock,
        val errPos: ErrorPos,
): R_Statement()

class R_UpdateStatement(
        target: R_UpdateTarget,
        fromBlock: R_FrameBlock,
        errPos: ErrorPos,
        val what: ImmList<R_UpdateStatementWhat>,
): R_BaseUpdateStatement(target, fromBlock, errPos)

class R_DeleteStatement(
        target: R_UpdateTarget,
        fromBlock: R_FrameBlock,
        errPos: ErrorPos,
): R_BaseUpdateStatement(target, fromBlock, errPos)
