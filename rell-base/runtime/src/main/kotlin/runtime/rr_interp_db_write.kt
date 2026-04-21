/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.lib.type.Rt_ListValue
import net.postchain.rell.base.lib.type.Rt_RowidValue
import net.postchain.rell.base.model.expr.R_AtCardinality
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap

@Suppress("UnusedReceiverParameter")
fun Rt_Interpreter.buildRowidSql(frame: Rt_CallFrame): ParameterizedSql {
    val rowidFunc = frame.sqlCtx.mainChainMapping().rowidFunction
    return ParameterizedSql.generate { b -> b.append("\"$rowidFunc\"()") }
}

fun Rt_Interpreter.evaluateRegularCreate(expr: RR_Expr.RegularCreate, frame: Rt_CallFrame): Rt_Value {
    try {
        frame.checkDbUpdateAllowed()
    } catch (e: Rt_Exception) {
        frame.error(expr.errPos, e, true)
    }
    val entityDef = rrApp.allEntities[expr.entityDefIndex]
    val attrValues = mutableMapOf<String, Rt_Value>()
    for (attr in expr.attrs) {
        attrValues[attr.attrName] = evaluateExpr(attr.expr, frame)
    }

    // Validate size constraints
    for (attr in expr.attrs) {
        val rrAttr = entityDef.strAttributes[attr.attrName] ?: continue
        val constraint = rrAttr.sizeConstraint ?: continue
        val value = attrValues[attr.attrName] ?: continue
        checkAttrSizeConstraint(constraint, value)
    }

    val b = SqlBuilder()
    b.append("INSERT INTO ")
    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    b.appendName(table)

    val rowidCol = entityDef.sqlMapping.rowidColumn
    val rowidSql = buildRowidSql(frame)

    val attrs = attrValues.keys.mapNotNull { name -> entityDef.strAttributes[name] }
    b.append("(")
    b.appendName(rowidCol)
    for (attr in attrs) {
        b.append(", ")
        b.appendName(attr.sqlMapping)
    }
    b.append(") VALUES (")
    b.append(rowidSql)
    for (attr in attrs) {
        b.append(", ")
        b.append(attrValues[attr.name] ?: Rt_NullValue)
    }
    b.append(")")
    b.append(" RETURNING ")
    b.appendName(rowidCol)

    val pSql = b.build()
    val select = SqlSelectRt(pSql, listOf(resolveType(entityDef.type)).toImmList())
    val rows = select.execute(frame.userSqlExec)
    val result = rows.single().single()
    emitCreateSnapshot(frame, entityDef, result.asObjectId(), attrValues)
    return result
}

fun Rt_Interpreter.evaluateStructEntityCreate(expr: RR_Expr.StructEntityCreate, frame: Rt_CallFrame): Rt_Value {
    frame.checkDbUpdateAllowed()
    val entityDef = rrApp.allEntities[expr.entityDefIndex]
    val structValue = evaluateExpr(expr.structExpr, frame).asStruct()

    val b = SqlBuilder()
    b.append("INSERT INTO ")
    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    b.appendName(table)

    val rowidCol = entityDef.sqlMapping.rowidColumn
    val rowidSql = buildRowidSql(frame)

    // Use entity attributes for column mapping — they match the DB schema
    val attrs = entityDef.strAttributes.values.toList()
    b.append("(")
    b.appendName(rowidCol)
    for (attr in attrs) {
        b.append(", ")
        b.appendName(attr.sqlMapping)
    }
    b.append(") VALUES (")
    b.append(rowidSql)
    for (i in attrs.indices) {
        b.append(", ")
        b.append(structValue.get(i))
    }
    b.append(")")
    b.append(" RETURNING ")
    b.appendName(rowidCol)

    val pSql = b.build()
    val select = SqlSelectRt(pSql, listOf(resolveType(entityDef.type)).toImmList())
    val rows = select.execute(frame.userSqlExec)
    val result = rows.single().single()
    if (frame.exeCtx.opCtx.hasSnapshotContext()) {
        val attrValues = attrs.mapIndexed { i, attr -> attr.name to structValue.get(i) }.toMap()
        emitCreateSnapshot(frame, entityDef, result.asObjectId(), attrValues)
    }
    return result
}

fun Rt_Interpreter.evaluateStructListCreate(expr: RR_Expr.StructListCreate, frame: Rt_CallFrame): Rt_Value {
    frame.checkDbUpdateAllowed()
    val entityDef = rrApp.allEntities[expr.entityDefIndex]
    val list = evaluateExpr(expr.listExpr, frame).asList()
    if (list.isEmpty()) return Rt_ListValue(resolveType(expr.resultListType), mutableListOf())

    val rowidCol = entityDef.sqlMapping.rowidColumn
    val attrs = entityDef.strAttributes.values.toList()
    val entityRtType = resolveType(entityDef.type)
    val rtResultTypes = listOf(entityRtType).toImmList()
    val table = entityDef.sqlMapping.table(frame.sqlCtx)

    // Pre-compute snapshot GTV conversions once per call instead of per row.
    // hasSnapshotContext() is checked here so the conversions are only built when needed.
    val snapshotEnabled = frame.exeCtx.opCtx.hasSnapshotContext()
    val snapshotConvs: List<Pair<String, Rt_TypeGtvConversion>>
    val snapshotMetaNameGtv: Gtv?
    if (snapshotEnabled) {
        snapshotConvs = entityGtvConversions(entityDef)
        snapshotMetaNameGtv = GtvFactory.gtv(entityDef.sqlMapping.metaName)
    } else {
        snapshotConvs = emptyList()
        snapshotMetaNameGtv = null
    }

    // Determine rowid generation strategy: fast (pre-allocated) vs slow (per-row)
    val count = list.size
    val useFastRowid = count >= frame.defCtx.globalCtx.sqlInsertFastRowidCountThreshold
    val firstRowid = if (useFastRowid) {
        val sql = ParameterizedSql.generate { b ->
            val rowidsFn = frame.sqlCtx.mainChainMapping().rowidsFunction
            b.append("SELECT \"$rowidsFn\"(")
            b.append(count.toLong())
            b.append(")")
        }
        var firstRowidVar: Long? = null
        sql.executeQuery(frame.userSqlExec) { row -> firstRowidVar = row.getLong(1) }
        firstRowidVar!!
    } else 0L

    // Split into pages based on sqlUpdatePortionSize
    val pageSize = if (attrs.isEmpty()) count else maxOf(frame.defCtx.globalCtx.sqlUpdatePortionSize / attrs.size, 1)
    val allResults = mutableListOf<Rt_Value>()

    for (pageStart in 0 until count step pageSize) {
        val pageEnd = minOf(pageStart + pageSize, count)
        val pageItems = list.subList(pageStart, pageEnd)

        val b = SqlBuilder()
        b.append("INSERT INTO ")
        b.appendName(table)
        b.append("(")
        b.appendName(rowidCol)
        for (attr in attrs) {
            b.append(", ")
            b.appendName(attr.sqlMapping)
        }
        b.append(") VALUES ")

        for ((idx, item) in pageItems.withIndex()) {
            if (idx > 0) b.append(", ")
            val structValue = item.asStruct()
            b.append("(")
            if (useFastRowid) {
                b.append(firstRowid + pageStart + idx)
            } else {
                b.append(buildRowidSql(frame))
            }
            for (i in attrs.indices) {
                b.append(", ")
                b.append(structValue.get(i))
            }
            b.append(")")
        }
        b.append(" RETURNING ")
        b.appendName(rowidCol)

        val pSql = b.build()
        val select = SqlSelectRt(pSql, rtResultTypes)
        val rows = select.execute(frame.userSqlExec)
        for ((idx, row) in rows.withIndex()) {
            val rowid = row.single()
            allResults.add(rowid)
            if (snapshotEnabled) {
                val structValue = pageItems[idx].asStruct()
                emitCreateSnapshotWith(
                    frame, snapshotMetaNameGtv!!, rowid.asObjectId(), snapshotConvs,
                ) { i -> structValue.get(i) }
            }
        }
    }

    return Rt_ListValue(resolveType(expr.resultListType), allResults.toMutableList())
}

fun Rt_Interpreter.executeUpdate(stmt: RR_Statement.Update, frame: Rt_CallFrame) {
    frame.checkDbUpdateAllowed()

    when (stmt.targetKind) {
        RR_UpdateTargetKind.EXPR_ONE -> {
            // Evaluate the target expression — skip if null (entity not found)
            executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
                // lambdaExpr was already evaluated and set via executeInLambdaBlock
                executeUpdateSql(stmt, frame)
            }
            return
        }

        RR_UpdateTargetKind.EXPR_MANY -> {
            executeExprManyUpdate(stmt, frame)
            return
        }

        RR_UpdateTargetKind.SIMPLE -> {
            val count = executeUpdateSqlCount(stmt, frame)
            val cardinality = stmt.cardinality
            if (cardinality != null) {
                checkAtCount(frame, stmt.errPos, toRCardinality(cardinality), count, "records")
            }
            return
        }

        RR_UpdateTargetKind.OBJECT -> {
            val count = executeUpdateSqlCount(stmt, frame)
            checkAtCount(frame, stmt.errPos, R_AtCardinality.ONE, count, "records")
            return
        }
    }
}

private fun Rt_Interpreter.executeUpdateSqlCount(stmt: RR_Statement.Update, frame: Rt_CallFrame): Int {
    var count = 0
    executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
        frame.block(stmt.fromBlock) {
            count = executeUpdateSqlInner(stmt, frame)
        }
    }
    return count
}

private fun Rt_Interpreter.executeUpdateSqlInner(stmt: RR_Statement.Update, frame: Rt_CallFrame): Int {
    val entityDef = rrApp.allEntities[stmt.entity.entityDefIndex]
    val allEntities = listOf(stmt.entity) + (stmt.extraEntities ?: emptyList())

    // Validate size constraints before building/executing SQL.
    // Mirrors R_ model: R_UpdateStatement.buildSql calls redExpr.constantValue()?.let { validator?.check(it)?.raise() }
    for (w in stmt.what) {
        val wExpr = w.expr
        if (wExpr is RR_DbExpr.Interpreted) {
            val value = evaluateExpr(wExpr.expr, frame)
            val rrAttr = entityDef.strAttributes[w.attrName]
            val constraint = rrAttr?.sizeConstraint
            if (constraint != null) {
                checkAttrSizeConstraint(constraint, value)
            }
        }
    }

    val sqlGen = DbSqlGen(this, frame.sqlCtx, allEntities)

    val b = SqlBuilder()
    b.append("UPDATE ")
    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    b.appendName(table)
    b.append(" ")
    b.append(sqlGen.getEntityAlias(stmt.entity.entityId)!!)

    // SET clause — pre-generate SQL for each what-expression into separate builders
    // so that relationship JOINs they create are tracked before the FROM clause.
    val setSqls = stmt.what.map { w ->
        val attr = entityDef.strAttributes[w.attrName]!!
        ParameterizedSql.generate { sb ->
            sb.appendName(attr.sqlMapping)
            sb.append(" = ")
            sqlGen.dbExprToSql(w.expr, sb, frame, false)
        }
    }

    // WHERE clause — generate WHERE SQL first (so relationship JOINs are created lazily),
    // then append join conditions.
    val userWhereB = SqlBuilder()
    val stmtWhere = stmt.where
    if (stmtWhere != null) {
        sqlGen.dbExprToSql(stmtWhere, userWhereB, frame, false)
    }
    // Now all relationship JOINs (from both SET and WHERE) have been created.
    // Build the final WHERE clause with join conditions first, then user WHERE.
    val whereB = SqlBuilder()
    sqlGen.appendJoinConditions(whereB)
    if (!userWhereB.isEmpty()) {
        if (!whereB.isEmpty()) whereB.append(" AND ")
        whereB.append(userWhereB)
    }

    // Now build the SQL statement using pre-generated fragments and all collected joins.
    b.append(" SET ")
    b.append(setSqls, ", ") { sql: ParameterizedSql -> b.append(sql) }

    // FROM clause for extra entities
    val extras = stmt.extraEntities
    if (!extras.isNullOrEmpty()) {
        b.append(" FROM ")
        b.append(extras.toList(), ", ") { extra: RR_DbAtEntity ->
            val extraDef = rrApp.allEntities[extra.entityDefIndex]
            val extraTable = extraDef.sqlMapping.table(frame.sqlCtx)
            b.appendName(extraTable)
            b.append(" ")
            b.append(sqlGen.getEntityAlias(extra.entityId)!!)
        }
    }

    // Relationship JOINs in FROM — now includes joins from both SET and WHERE expressions
    val joins = sqlGen.getAllRelJoins()
    if (joins.isNotEmpty()) {
        if (extras.isNullOrEmpty()) b.append(" FROM ") else b.append(", ")
        b.append(joins, ", ") { join: DbSqlJoinInfo ->
            b.appendName(join.table)
            b.append(" ")
            b.append(join.alias)
        }
    }

    // WHERE clause
    if (!whereB.isEmpty()) {
        b.append(" WHERE ")
        b.append(whereB)
    }

    // RETURNING clause (for snapshot sync + count)
    val snapshot = frame.exeCtx.opCtx.hasSnapshotContext()
    val mainAlias = sqlGen.getEntityAlias(stmt.entity.entityId)!!
    b.append(" RETURNING ")
    b.append(mainAlias)
    b.append(".\"")
    b.append(entityDef.sqlMapping.rowidColumn)
    b.append("\"")

    val allAttrs = entityDef.strAttributes.values.toList()
    if (snapshot) {
        for (attr in allAttrs) {
            b.append(", ")
            b.append(mainAlias)
            b.append(".\"")
            b.append(attr.sqlMapping)
            b.append("\"")
        }
    }

    val pSql = b.build()
    if (!snapshot) {
        var count = 0
        pSql.executeQuery(frame.userSqlExec) { _ -> count++ }
        return count
    }

    // Hoist per-attribute Rt_Type → SQL adapter resolution out of the per-row loop —
    // this is consensus hot-path code; on bulk updates of many rows we don't want
    // rows × attrs hash-map lookups against the resolveType cache.
    val sqlAdapters = allAttrs.map { attr ->
        val rtType = resolveType(attr.type)
        checkNotNull(rtType.sqlAdapter) { "No SQL adapter for type: ${rtType.name}" }
    }

    val rows = mutableListOf<List<Rt_Value>>()
    pSql.executeQuery(frame.userSqlExec) { row ->
        val rowid = Rt_RowidValue.get(row.getLong(1))
        val attrValues = List(allAttrs.size) { i -> sqlAdapters[i].fromSql(row, i + 2, false) }
        rows.add(listOf(rowid) + attrValues)
    }
    emitUpdateSnapshot(frame, entityDef, rows)
    return rows.size
}

private fun Rt_Interpreter.executeExprManyUpdate(stmt: RR_Statement.Update, frame: Rt_CallFrame) {
    val value = stmt.lambdaExpr?.let { evaluateExpr(it, frame) } ?: return
    val lst = if (stmt.isExprSet) {
        value.asSet().toMutableList()
    } else {
        value.asList().toSet().toMutableList()
    }
    if (lst.isEmpty()) return
    val partSize = frame.defCtx.globalCtx.sqlUpdatePortionSize
    val listType = stmt.exprListType?.let { resolveType(it) } ?: resolveType(
        RR_Type.List(
            RR_Type.Primitive(
                RR_PrimitiveKind.INTEGER,
            ),
        ),
    )
    for (part in CommonUtils.split(lst, partSize)) {
        val partValue = Rt_ListValue(listType, part)
        frame.block(stmt.lambdaBlock!!) {
            frame.setUnchecked(stmt.lambdaVarPtr!!, partValue, false)
            frame.block(stmt.fromBlock) {
                executeUpdateSqlInner(stmt, frame)
            }
        }
    }
}

private fun Rt_Interpreter.executeExprManyDelete(stmt: RR_Statement.Delete, frame: Rt_CallFrame) {
    val value = stmt.lambdaExpr?.let { evaluateExpr(it, frame) } ?: return
    val lst = if (stmt.isExprSet) {
        value.asSet().toMutableList()
    } else {
        value.asList().toSet().toMutableList()
    }
    if (lst.isEmpty()) return
    val partSize = frame.defCtx.globalCtx.sqlUpdatePortionSize
    val listType = stmt.exprListType?.let { resolveType(it) } ?: resolveType(
        RR_Type.List(
            RR_Type.Primitive(
                RR_PrimitiveKind.INTEGER,
            ),
        ),
    )
    for (part in CommonUtils.split(lst, partSize)) {
        val partValue = Rt_ListValue(listType, part)
        frame.block(stmt.lambdaBlock!!) {
            frame.setUnchecked(stmt.lambdaVarPtr!!, partValue, false)
            frame.block(stmt.fromBlock) {
                executeDeleteSqlInner(stmt, frame)
            }
        }
    }
}

private fun Rt_Interpreter.executeUpdateSql(stmt: RR_Statement.Update, frame: Rt_CallFrame) {
    frame.block(stmt.fromBlock) {
        executeUpdateSqlInner(stmt, frame)
    }
}

fun Rt_Interpreter.executeDelete(stmt: RR_Statement.Delete, frame: Rt_CallFrame) {
    frame.checkDbUpdateAllowed()

    when (stmt.targetKind) {
        RR_UpdateTargetKind.EXPR_ONE -> {
            executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
                executeDeleteSql(stmt, frame)
            }
            return
        }

        RR_UpdateTargetKind.EXPR_MANY -> {
            executeExprManyDelete(stmt, frame)
            return
        }

        RR_UpdateTargetKind.SIMPLE -> {
            val count = executeDeleteSqlCount(stmt, frame)
            val cardinality = stmt.cardinality
            if (cardinality != null) {
                checkAtCount(frame, stmt.errPos, toRCardinality(cardinality), count, "records")
            }
            return
        }

        RR_UpdateTargetKind.OBJECT -> {
            val count = executeDeleteSqlCount(stmt, frame)
            checkAtCount(frame, stmt.errPos, R_AtCardinality.ONE, count, "records")
            return
        }
    }
}

private fun Rt_Interpreter.executeDeleteSqlCount(stmt: RR_Statement.Delete, frame: Rt_CallFrame): Int {
    var count = 0
    executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
        frame.block(stmt.fromBlock) {
            count = executeDeleteSqlInner(stmt, frame)
        }
    }
    return count
}

private fun Rt_Interpreter.executeDeleteSqlInner(stmt: RR_Statement.Delete, frame: Rt_CallFrame): Int {
    val entityDef = rrApp.allEntities[stmt.entity.entityDefIndex]
    val allEntities = listOf(stmt.entity) + (stmt.extraEntities ?: emptyList())

    val sqlGen = DbSqlGen(this, frame.sqlCtx, allEntities)

    // WHERE clause — generate WHERE SQL first (so relationship JOINs are created lazily),
    // then append join conditions.
    val userWhereB = SqlBuilder()
    val deleteWhere = stmt.where
    if (deleteWhere != null) {
        sqlGen.dbExprToSql(deleteWhere, userWhereB, frame, false)
    }
    // Now all relationship JOINs have been created.
    // Build the final WHERE clause with join conditions first, then user WHERE.
    val whereB = SqlBuilder()
    sqlGen.appendJoinConditions(whereB)
    if (!userWhereB.isEmpty()) {
        if (!whereB.isEmpty()) whereB.append(" AND ")
        whereB.append(userWhereB)
    }

    val b = SqlBuilder()
    b.append("DELETE FROM ")
    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    b.appendName(table)
    b.append(" ")
    b.append(sqlGen.getEntityAlias(stmt.entity.entityId)!!)

    // USING clause for extra entities and relationship joins (now includes all joins)
    val extras = stmt.extraEntities
    val joins = sqlGen.getAllRelJoins()
    val usingItems = mutableListOf<Pair<String, String>>()
    if (extras != null) {
        for (extra in extras) {
            val extraDef = rrApp.allEntities[extra.entityDefIndex]
            usingItems.add(extraDef.sqlMapping.table(frame.sqlCtx) to sqlGen.getEntityAlias(extra.entityId)!!)
        }
    }
    for (join in joins) {
        usingItems.add(join.table to join.alias)
    }
    if (usingItems.isNotEmpty()) {
        b.append(" USING ")
        b.append(usingItems, ", ") { (tbl, alias): Pair<String, String> ->
            b.appendName(tbl)
            b.append(" ")
            b.append(alias)
        }
    }

    // WHERE clause
    if (!whereB.isEmpty()) {
        b.append(" WHERE ")
        b.append(whereB)
    }

    // RETURNING clause (for snapshot sync + count)
    val mainAlias = sqlGen.getEntityAlias(stmt.entity.entityId)!!
    b.append(" RETURNING ")
    b.append(mainAlias)
    b.append(".\"")
    b.append(entityDef.sqlMapping.rowidColumn)
    b.append("\"")

    val pSql = b.build()
    val snapshot = frame.exeCtx.opCtx.hasSnapshotContext()
    if (!snapshot) {
        var count = 0
        pSql.executeQuery(frame.userSqlExec) { _ -> count++ }
        return count
    }

    val rowids = mutableListOf<Long>()
    pSql.executeQuery(frame.userSqlExec) { row -> rowids.add(row.getLong(1)) }
    emitDeleteSnapshot(frame, rowids)
    return rowids.size
}

private fun Rt_Interpreter.executeDeleteSql(stmt: RR_Statement.Delete, frame: Rt_CallFrame) {
    frame.block(stmt.fromBlock) {
        executeDeleteSqlInner(stmt, frame)
    }
}

// --- Snapshot emission helpers ---

private fun Rt_Interpreter.emitCreateSnapshot(
    frame: Rt_CallFrame,
    entityDef: RR_EntityDefinition,
    rowid: Long,
    attrValues: Map<String, Rt_Value>,
) {
    val opCtx = frame.exeCtx.opCtx
    if (!opCtx.hasSnapshotContext()) return

    val attrs = entityDef.strAttributes.values.associate { attr ->
        val rtType = resolveType(attr.type)
        val conv: Rt_TypeGtvConversion = checkNotNull(rtType.gtvConversion) {
            "No GTV conversion for type: ${rtType.name}"
        }
        attr.name to conv.rtToGtv(attrValues[attr.name] ?: Rt_NullValue, false)
    }.toImmMap()
    val datum = GtvFactory.gtv(GtvFactory.gtv(entityDef.sqlMapping.metaName), GtvFactory.gtv(attrs))
    opCtx.emitDatum(rowid, datum, false)
}

/**
 * Pre-computed (attribute name, GTV conversion) pairs for an entity's attributes, in
 * `strAttributes.values` iteration order. Use [emitCreateSnapshotWith] to emit snapshots
 * in a hot loop without paying per-row hash-map lookups against [Rt_Interpreter.resolveType].
 */
private fun Rt_Interpreter.entityGtvConversions(
    entityDef: RR_EntityDefinition,
): List<Pair<String, Rt_TypeGtvConversion>> = entityDef.strAttributes.values.map { attr ->
    val rtType = resolveType(attr.type)
    val conv = checkNotNull(rtType.gtvConversion) { "No GTV conversion for type: ${rtType.name}" }
    attr.name to conv
}

/**
 * Bulk-create snapshot emission with pre-computed [convs] (one per attribute, in
 * `strAttributes.values` order). Avoids the per-row resolveType + gtvConversion lookup
 * loop performed by [emitCreateSnapshot].
 */
private fun emitCreateSnapshotWith(
    frame: Rt_CallFrame,
    metaNameGtv: Gtv,
    rowid: Long,
    convs: List<Pair<String, Rt_TypeGtvConversion>>,
    valueAt: (Int) -> Rt_Value,
) {
    val attrGtvs = LinkedHashMap<String, Gtv>(convs.size)
    for (i in convs.indices) {
        val (name, conv) = convs[i]
        attrGtvs[name] = conv.rtToGtv(valueAt(i), false)
    }
    val datum = GtvFactory.gtv(metaNameGtv, GtvFactory.gtv(attrGtvs))
    frame.exeCtx.opCtx.emitDatum(rowid, datum, false)
}

private fun Rt_Interpreter.emitUpdateSnapshot(
    frame: Rt_CallFrame,
    entityDef: RR_EntityDefinition,
    rows: List<List<Rt_Value>>,
) {
    val opCtx = frame.exeCtx.opCtx
    val attrs = entityDef.strAttributes.values.toList()
    // Hoist per-attribute resolveType + gtvConversion lookups out of the per-row loop:
    // bulk updates can return thousands of rows; we don't want rows × attrs hash-map
    // lookups against the resolveType cache in consensus hot-path code.
    val convs = attrs.map { attr ->
        val rtType = resolveType(attr.type)
        checkNotNull(rtType.gtvConversion) { "No GTV conversion for type: ${rtType.name}" }
    }
    val metaName = entityDef.sqlMapping.metaName
    val metaNameGtv = GtvFactory.gtv(metaName)
    for (row in rows) {
        val rowid0 = row[0].asRowid()
        val rowid = if (rowid0 != 0L) rowid0 else opCtx.objectSnapshotId(metaName)

        val attrValues = LinkedHashMap<String, Gtv>(attrs.size)
        for (i in attrs.indices) {
            attrValues[attrs[i].name] = convs[i].rtToGtv(row[i + 1], false)
        }

        val datum = GtvFactory.gtv(metaNameGtv, GtvFactory.gtv(attrValues))
        opCtx.emitDatum(rowid, datum, false)
    }
}

private fun emitDeleteSnapshot(
    frame: Rt_CallFrame,
    rowids: List<Long>,
) {
    val opCtx = frame.exeCtx.opCtx
    val datum: Gtv = GtvFactory.gtv(listOf())
    for (rowid in rowids) {
        opCtx.emitDatum(rowid, datum, false)
    }
}

/** Validates a value against a [RR_SizeConstraint], throwing [Rt_Exception] on violation. */
fun checkAttrSizeConstraint(constraint: RR_SizeConstraint, value: Rt_Value) {
    if (value == Rt_NullValue) return
    val size = when (constraint.kind) {
        RR_SizeConstraintKind.BYTE_ARRAY -> value.asByteArray().size
        RR_SizeConstraintKind.TEXT -> value.asString().length
    }
    val min = constraint.min
    val max = constraint.max
    if (min != null && size < min) {
        throw Rt_Exception.common(
            "${constraint.codePrefix}:validator:size:too_small",
            "Size too small: specified minimum is $min (inclusive), got $size",
        )
    }
    if (max != null && size > max) {
        throw Rt_Exception.common(
            "${constraint.codePrefix}:validator:size:too_large",
            "Size too large: specified maximum is $max (inclusive), got $size",
        )
    }
}
