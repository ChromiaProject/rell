/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.model.*
import net.postchain.rell.base.utils.checkEquals

enum class R_AtCardinality(val code: String, val zero: Boolean, val many: Boolean) {
    ZERO_ONE("@?", true, false),
    ONE("@", false, false),
    ZERO_MANY("@*", true, true),
    ONE_MANY("@+", false, true),
    ;

    fun matches(count: Int): Boolean = !(count < 0 || count == 0 && !zero || count > 1 && !many)
}

class R_DbAtEntity(val rEntity: R_EntityDefinition, val id: R_AtEntityId) {
    override fun toString() = "$rEntity:$id"

    companion object {
        fun checkList(entities : List<R_DbAtEntity>): R_AtExprId {
            val entityIds = entities.map { it.id }
            checkEquals(entityIds.toSet().size, entityIds.size) { "Entities not unique: $entityIds" }

            val exprIds = entityIds.map { it.exprId }.toSet()
            checkEquals(exprIds.size, 1) { "Entities belong to different expressions: $entityIds" }
            return exprIds.first()
        }
    }
}

enum class R_AtWhatSort(val asc: Boolean) {
    ASC(true),
    DESC(false),
}

class R_AtWhatFieldFlags(val omit: Boolean, val sort: R_AtWhatSort?, val group: Boolean, val aggregate: Boolean) {
    companion object {
        val DEFAULT = R_AtWhatFieldFlags(omit = false, sort = null, group = false, aggregate = false)
    }
}

class R_AtExprExtras(val limit: R_Expr?, val offset: R_Expr?)

class Rt_AtExprExtras(val limit: Long?, val offset: Long?) {
    companion object {
        val NULL = Rt_AtExprExtras(null, null)
    }
}

class R_DbAtExprInternals(
    val block: R_FrameBlock,
)

abstract class R_AtExpr(
    type: R_Type,
    val cardinality: R_AtCardinality,
    val extras: R_AtExprExtras,
): R_BaseExpr(type)

class R_DbAtExpr(
        type: R_Type,
        val base: Db_AtExprBase,
        cardinality: R_AtCardinality,
        extras: R_AtExprExtras,
        val internals: R_DbAtExprInternals,
        val errPos: ErrorPos,
): R_AtExpr(type, cardinality, extras)
