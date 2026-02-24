/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.stmt

import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lib.type.Rt_RowidValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.*

internal sealed class R_UpdateTarget {
    abstract fun entity(): R_DbAtEntity
    abstract fun extraEntities(): List<R_DbAtEntity>
    abstract fun where(): Db_Expr?

    abstract fun execute(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, errPos: R_ErrorPos)
}

internal class R_UpdateTarget_Simple(
    private val entity: R_DbAtEntity,
    private val extraEntities: ImmList<R_DbAtEntity>,
    private val cardinality: R_AtCardinality,
    private val where: Db_Expr?,
): R_UpdateTarget() {
    init {
        val intersect = extraEntities.filter { it.id == entity.id }
        check(intersect.isEmpty()) { "Extra entities contain main entity: ${entity.id}" }
    }

    private val fromItems = getFromItems(entity, extraEntities)

    override fun entity() = entity
    override fun extraEntities() = extraEntities
    override fun where() = where

    override fun execute(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, errPos: R_ErrorPos) {
        executeCommon(stmt, frame, fromItems, cardinality, errPos)
    }

    companion object {
        fun getFromItems(entity: R_DbAtEntity, extraEntities: List<R_DbAtEntity>): ImmList<RedDb_AtFromItem> {
            val allEntities = listOf(entity) + extraEntities
            return allEntities.mapToImmList { RedDb_AtFromItem(it, null, false) }
        }

        internal fun executeCommon(
            stmt: R_BaseUpdateStatement,
            frame: Rt_CallFrame,
            fromItems: List<RedDb_AtFromItem>,
            cardinality: R_AtCardinality,
            errPos: R_ErrorPos,
        ) {
            val count = stmt.executeSqlCount(frame, fromItems)
            R_AtExpr.checkCount(frame, errPos, cardinality, count, "records")
        }
    }
}

internal sealed class R_UpdateTarget_Expr(
    private val entity: R_DbAtEntity,
    extraEntities: List<R_DbAtEntity>,
    private val where: Db_Expr,
    private val expr: R_Expr,
    private val lambda: R_LambdaBlock,
): R_UpdateTarget() {
    private val extraEntities: ImmList<R_DbAtEntity> = extraEntities.toImmList()
    private val fromItems = R_UpdateTarget_Simple.getFromItems(entity, this.extraEntities)

    final override fun entity() = entity
    final override fun extraEntities() = extraEntities
    final override fun where() = where

    protected abstract fun execute0(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, value: Rt_Value)

    final override fun execute(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, errPos: R_ErrorPos) {
        val value = expr.evaluate(frame)
        execute0(stmt, frame, value)
    }

    fun executeStmt(frame: Rt_CallFrame, stmt: R_BaseUpdateStatement, value: Rt_Value) {
        lambda.execute(frame, value) {
            stmt.executeSql(frame, fromItems)
        }
    }
}

internal class R_UpdateTarget_Expr_One(
    entity: R_DbAtEntity,
    extraEntities: List<R_DbAtEntity>,
    where: Db_Expr,
    expr: R_Expr,
    lambda: R_LambdaBlock,
): R_UpdateTarget_Expr(entity, extraEntities, where, expr, lambda) {
    override fun execute0(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, value: Rt_Value) {
        if (value != Rt_NullValue) {
            executeStmt(frame, stmt, value)
        }
    }
}

internal class R_UpdateTarget_Expr_Many(
    entity: R_DbAtEntity,
    where: Db_Expr,
    expr: R_Expr,
    lambda: R_LambdaBlock,
    private val set: Boolean,
    private val listType: R_Type,
): R_UpdateTarget_Expr(entity, immListOf(), where, expr, lambda) {
    override fun execute0(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, value: Rt_Value) {
        val lst = if (set) {
            value.asSet().toMutableList()
        } else {
            value.asList().toSet().toMutableList()
        }

        if (lst.isEmpty()) {
            return
        }

        // Experimental maximum is 2^15
        val partSize = frame.defCtx.globalCtx.sqlUpdatePortionSize

        for (part in CommonUtils.split(lst, partSize)) {
            val partValue = Rt_ListValue(listType, part)
            executeStmt(frame, stmt, partValue)
        }
    }
}

internal class R_UpdateTarget_Object(private val entity: R_DbAtEntity): R_UpdateTarget() {
    private val fromItems = R_UpdateTarget_Simple.getFromItems(entity, listOf())

    override fun entity() = entity
    override fun extraEntities(): List<R_DbAtEntity> = listOf()
    override fun where() = null

    override fun execute(stmt: R_BaseUpdateStatement, frame: Rt_CallFrame, errPos: R_ErrorPos) {
        R_UpdateTarget_Simple.executeCommon(stmt, frame, fromItems, R_AtCardinality.ONE, errPos)
    }
}

class R_UpdateStatementWhat(val attr: R_Attribute, val expr: Db_Expr)

internal sealed class R_BaseUpdateStatement(
    protected val target: R_UpdateTarget,
    private val fromBlock: R_FrameBlock,
    private val errPos: R_ErrorPos,
): R_Statement() {
    protected abstract fun buildSql(
        frame: Rt_CallFrame,
        ctx: SqlGenContext,
        returning: Boolean,
        returningAttrs: Boolean,
    ): ParameterizedSql

    protected abstract fun processSnapshot(frame: Rt_CallFrame, rEntity: R_EntityDefinition, rows: List<List<Rt_Value>>)

    fun executeSql(frame: Rt_CallFrame, fromItems: List<RedDb_AtFromItem>) {
        frame.block(fromBlock) {
            val ctx = SqlGenContext.createTop(frame.sqlCtx, fromItems)
            val pSql = buildSql(frame, ctx, false, false)
            pSql.execute(frame.userSqlExec)
        }
    }

    fun executeSqlCount(frame: Rt_CallFrame, fromItems: List<RedDb_AtFromItem>): Int {
        val snapshot = frame.exeCtx.opCtx.hasSnapshotContext()
        val rEntity = target.entity().rEntity

        val rows = frame.block(fromBlock) {
            val ctx = SqlGenContext.createTop(frame.sqlCtx, fromItems)
            val returningAttrs = snapshot && this is R_UpdateStatement
            val pSql = buildSql(frame, ctx, true, returningAttrs)

            val rows = mutableListOf<List<Rt_Value>>()
            pSql.executeQuery(frame.userSqlExec) { row ->
                val rowid = Rt_RowidValue.get(row.getLong(1))
                val row = if (!returningAttrs) listOf() else rEntity.attributes.values.mapIndexed { i, attr ->
                    attr.type.sqlAdapter.fromSql(row, i + 2, false)
                }
                rows.add(immListOf(rowid) + row)
            }
            rows
        }

        if (snapshot) {
            processSnapshot(frame, rEntity, rows)
        }

        return rows.size
    }

    final override fun execute(frame: Rt_CallFrame): R_StatementResult? {
        frame.checkDbUpdateAllowed()
        target.execute(this, frame, errPos)
        return null
    }

    protected fun appendMainTable(b: SqlBuilder, sqlCtx: Rt_SqlContext, fromInfo: SqlFromInfo) {
        val entity = target.entity()
        val table = entity.rEntity.sqlMapping.table(sqlCtx)
        b.appendName(table)
        b.append(" ")
        b.append(fromInfo.entities.getValue(entity.id).alias.str)
    }

    protected fun appendExtraTables(
        builder: SqlBuilder,
        sqlCtx: Rt_SqlContext,
        fromInfo: SqlFromInfo,
        keyword: String,
    ) {
        val tables = mutableListOf<Pair<String, SqlTableAlias>>()

        val entity = target.entity()
        for (join in fromInfo.entities.getValue(entity.id).joins) {
            val table = join.alias.entity.sqlMapping.table(sqlCtx)
            tables.add(Pair(table, join.alias))
        }

        for (extraEntity in target.extraEntities()) {
            tables.add(extraEntity.rEntity.sqlMapping.table(sqlCtx) to fromInfo.entities.getValue(extraEntity.id).alias)
            for (join in fromInfo.entities.getValue(extraEntity.id).joins) {
                tables.add(Pair(join.alias.entity.sqlMapping.table(sqlCtx), join.alias))
            }
        }

        if (tables.isEmpty()) {
            return
        }

        builder.append(" $keyword ")

        builder.append(tables, ", ") { (table, alias) ->
            builder.appendName(table)
            builder.append(" ")
            builder.append(alias.str)
        }
    }

    protected fun translateWhere(ctx: SqlGenContext, redWhere: RedDb_Expr?): ParameterizedSql? {
        return if (redWhere == null) null else {
            val b = SqlBuilder()
            redWhere.toSql(ctx, b, false)
            b.build()
        }
    }

    protected fun appendWhere(b: SqlBuilder, fromInfo: SqlFromInfo, explicitWhereSql: ParameterizedSql?) {
        val allJoins = fromInfo.entities.values.flatMap { it.joins }

        val whereB = SqlBuilder()
        appendWhereJoins(whereB, allJoins)

        if (explicitWhereSql != null && !explicitWhereSql.isEmpty()) {
            whereB.appendSep(" AND ")
            whereB.append(explicitWhereSql)
        }

        if (!whereB.isEmpty()) {
            b.append(" WHERE ")
            b.append(whereB)
        }
    }

    private fun appendWhereJoins(b: SqlBuilder, allJoins: List<SqlFromJoin>) {
        b.append(allJoins, " AND ") { join ->
            b.append("(")
            b.appendColumn(join.baseAlias, join.attr)
            b.append(" = ")
            b.appendColumn(join.alias, join.alias.entity.sqlMapping.rowidColumn())
            b.append(")")
        }
    }

    protected fun appendReturning(b: SqlBuilder, fromInfo: SqlFromInfo, appendAttrs: Boolean) {
        val entity = target.entity()
        val alias = fromInfo.entities.getValue(entity.id).alias

        b.append(" RETURNING ")
        b.appendColumn(alias, entity.rEntity.sqlMapping.rowidColumn())

        if (appendAttrs) {
            for (attr in entity.rEntity.attributes.values) {
                b.append(", ")
                b.appendColumn(alias, attr.sqlMapping)
            }
        }
    }
}

internal class R_UpdateStatement(
    target: R_UpdateTarget,
    fromBlock: R_FrameBlock,
    errPos: R_ErrorPos,
    private val what: ImmList<R_UpdateStatementWhat>,
): R_BaseUpdateStatement(target, fromBlock, errPos) {
    override fun buildSql(
        frame: Rt_CallFrame,
        ctx: SqlGenContext,
        returning: Boolean,
        returningAttrs: Boolean,
    ): ParameterizedSql {
        val redWhere = target.where()?.toRedExpr(frame)

        val redWhat = what.map { w ->
            val redExpr = w.expr.toRedExpr(frame)
            redExpr.constantValue()?.let { w.attr.validator?.check(it)?.raise() }
            RedDb_Utils.wrapDecimalExpr(w.expr.type, redExpr)
        }

        val whatSql = translateWhat(ctx, redWhat)
        val whereSql = translateWhere(ctx, redWhere)

        val fromInfo = ctx.getFromInfo()
        return buildSql0(ctx.sqlCtx, returning, returningAttrs, fromInfo, whatSql, whereSql)
    }

    private fun buildSql0(
        sqlCtx: Rt_SqlContext,
        returning: Boolean,
        returningAttrs: Boolean,
        fromInfo: SqlFromInfo,
        whatSql: ParameterizedSql,
        whereSql: ParameterizedSql?,
    ): ParameterizedSql {
        val b = SqlBuilder()

        b.append("UPDATE ")
        appendMainTable(b, sqlCtx, fromInfo)

        b.append(" SET ")
        b.append(whatSql)

        appendExtraTables(b, sqlCtx, fromInfo, "FROM")
        appendWhere(b, fromInfo, whereSql)

        if (returning) {
            appendReturning(b, fromInfo, returningAttrs)
        }

        return b.build()
    }

    private fun translateWhat(ctx: SqlGenContext, redWhat: List<RedDb_Expr>): ParameterizedSql {
        val b = SqlBuilder()

        b.append(redWhat.withIndex(), ", ") { (i, redExpr) ->
            val whatExpr = what[i]
            b.appendName(whatExpr.attr.sqlMapping)
            b.append(" = ")
            redExpr.toSql(ctx, b, false)
        }

        return b.build()
    }

    override fun processSnapshot(frame: Rt_CallFrame, rEntity: R_EntityDefinition, rows: List<List<Rt_Value>>) {
        for (row in rows) {
            val rowid0 = row[0].asRowid()
            val rowid = if (rowid0 != 0L) rowid0 else {
                frame.exeCtx.opCtx.objectSnapshotId(rEntity.metaName)
            }

            val attrValues = rEntity.attributes.values
                .mapIndexed { i, attr -> attr.name to attr.type.rtToGtv(row[i + 1], false) }
                .toImmMap()

            val data = GtvFactory.gtv(
                GtvFactory.gtv(rEntity.metaName),
                GtvFactory.gtv(attrValues),
            )

            frame.exeCtx.opCtx.emitDatum(rowid, data, false)
        }
    }
}

internal class R_DeleteStatement(
    target: R_UpdateTarget,
    fromBlock: R_FrameBlock,
    errPos: R_ErrorPos,
): R_BaseUpdateStatement(target, fromBlock, errPos) {
    override fun buildSql(
        frame: Rt_CallFrame,
        ctx: SqlGenContext,
        returning: Boolean,
        returningAttrs: Boolean,
    ): ParameterizedSql {
        val redWhere = target.where()?.toRedExpr(frame)
        val whereSql = translateWhere(ctx, redWhere)
        val fromInfo = ctx.getFromInfo()
        return buildSql0(ctx.sqlCtx, returning, fromInfo, whereSql)
    }

    private fun buildSql0(
        sqlCtx: Rt_SqlContext,
        returning: Boolean,
        fromInfo: SqlFromInfo,
        whereSql: ParameterizedSql?,
    ): ParameterizedSql {
        val b = SqlBuilder()

        b.append("DELETE FROM ")
        appendMainTable(b, sqlCtx, fromInfo)
        appendExtraTables(b, sqlCtx, fromInfo, "USING")
        appendWhere(b, fromInfo, whereSql)

        if (returning) {
            appendReturning(b, fromInfo, false)
        }

        return b.build()
    }

    override fun processSnapshot(frame: Rt_CallFrame, rEntity: R_EntityDefinition, rows: List<List<Rt_Value>>) {
        val data = GtvFactory.gtv(listOf())
        for (row in rows) {
            val rowid = row[0].asRowid()
            frame.exeCtx.opCtx.emitDatum(rowid, data, false)
        }
    }
}
