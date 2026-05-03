/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.AtCardinality
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.CommonUtils
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL

/** Builds a `<alias>."<col>"` qualified column reference as a jOOQ [Field]. */
private fun aliasedCol(alias: String, column: String): Field<Any?> =
    DbSqlGen.columnFieldExt(alias, column)

internal fun Rt_InterpreterImpl.evaluateRegularCreate(expr: RR_Expr.RegularCreate, frame: Rt_CallFrame): Rt_Value {
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

    for (attr in expr.attrs) {
        val rrAttr = entityDef.strAttributes[attr.attrName] ?: continue
        val constraint = rrAttr.sizeConstraint ?: continue
        val value = attrValues[attr.attrName] ?: continue
        checkAttrSizeConstraint(constraint, value)
    }

    val attrs = attrValues.keys.mapNotNull { name -> entityDef.strAttributes[name] }
    val pSql = buildInsertSql(
        frame,
        entityDef,
        attrSqlMappings = attrs.map { it.sqlMapping },
        attrValues = attrs.map { attrValues[it.name] ?: Rt_NullValue },
    )
    val select = SqlSelectRt(pSql, listOf(resolveType(entityDef.type)).toImmList())
    val rows = select.execute(frame.userSqlExec)
    val result = rows.single().single()
    emitCreateSnapshot(frame, entityDef, result.asObjectId(), attrValues)
    return result
}

internal fun Rt_InterpreterImpl.evaluateStructEntityCreate(
    expr: RR_Expr.StructEntityCreate,
    frame: Rt_CallFrame
): Rt_Value {
    frame.checkDbUpdateAllowed()
    val entityDef = rrApp.allEntities[expr.entityDefIndex]
    val structValue = evaluateExpr(expr.structExpr, frame).asStruct()

    val attrs = entityDef.strAttributes.values.toList()
    val pSql = buildInsertSql(
        frame,
        entityDef,
        attrSqlMappings = attrs.map { it.sqlMapping },
        attrValues = attrs.indices.map { i -> structValue.get(i) },
    )
    val select = SqlSelectRt(pSql, listOf(resolveType(entityDef.type)).toImmList())
    val rows = select.execute(frame.userSqlExec)
    val result = rows.single().single()
    if (frame.exeCtx.opCtx.hasSnapshotContext()) {
        val attrValues = attrs.mapIndexed { i, attr -> attr.name to structValue.get(i) }.toMap()
        emitCreateSnapshot(frame, entityDef, result.asObjectId(), attrValues)
    }
    return result
}

/**
 * Builds a single-row `INSERT INTO ... (rowid, attrs...) VALUES (rowid_fn(), ?, ?, ...) RETURNING "rowid"`
 * SQL string via jOOQ's [InsertQuery] API. `?` placeholders are emitted by jOOQ for each
 * [attrValues] entry and bound positionally to the same list.
 */
private fun buildInsertSql(
    frame: Rt_CallFrame,
    entityDef: RR_EntityDefinition,
    attrSqlMappings: List<String>,
    attrValues: List<Rt_Value>,
): ParameterizedSql {
    val tableName = entityDef.sqlMapping.table(frame.sqlCtx)
    val rowidCol = entityDef.sqlMapping.rowidColumn
    val rowidFn = frame.sqlCtx.mainChainMapping().rowidFunction

    val table: Table<Record> = DSL.table(DSL.name(tableName))
    val rowidFnExpr: Field<Any> = DSL.field("{0}()", Any::class.java, DSL.name(rowidFn))

    // Insertion order matters for VALUES (...) — use LinkedHashMap. Field<Any> on both sides
    // disambiguates `addValues(Map<Field, Field>)` from `addValues(Map<Field, Object>)`.
    val values = LinkedHashMap<Field<*>, Field<*>>()
    values[DSL.field(DSL.name(rowidCol))] = rowidFnExpr
    for (mapping in attrSqlMappings) {
        values[DSL.field(DSL.name(mapping))] = ANY_PARAM_PLACEHOLDER
    }

    val q = JOOQ_CTX.insertQuery(table)
    q.addValues(values)
    q.setReturning(DSL.field(DSL.name(rowidCol)))
    return ParameterizedSql(renderJooq(q), attrValues.toImmList())
}

/** `?` placeholder field with `Any` type, suitable for jOOQ's `addValue(Field<T>, Field<T>)`. */
private val ANY_PARAM_PLACEHOLDER: Field<Any> = DSL.field("?", Any::class.java)

internal fun Rt_InterpreterImpl.evaluateStructListCreate(
    expr: RR_Expr.StructListCreate,
    frame: Rt_CallFrame,
): Rt_Value {
    frame.checkDbUpdateAllowed()
    val entityDef = rrApp.allEntities[expr.entityDefIndex]
    val list = evaluateExpr(expr.listExpr, frame).asList()
    if (list.isEmpty()) return Rt_ListValue(resolveType(expr.resultListType), mutableListOf())

    val rowidCol = entityDef.sqlMapping.rowidColumn
    val attrs = entityDef.strAttributes.values.toList()
    val entityRtType = resolveType(entityDef.type)
    val rtResultTypes = listOf(entityRtType).toImmList()
    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    val rowidFn = frame.sqlCtx.mainChainMapping().rowidFunction

    val snapshotEnabled = frame.exeCtx.opCtx.hasSnapshotContext()
    val snapshotConvs: List<Pair<String, Rt_GtvCompatibleValueClass<*>>>
    val snapshotMetaNameGtv: Gtv?
    if (snapshotEnabled) {
        snapshotConvs = entityGtvConversions(entityDef)
        snapshotMetaNameGtv = GtvFactory.gtv(entityDef.sqlMapping.metaName)
    } else {
        snapshotConvs = emptyList()
        snapshotMetaNameGtv = null
    }

    val count = list.size
    val useFastRowid = count >= frame.defCtx.globalCtx.sqlInsertFastRowidCountThreshold
    val firstRowid = if (useFastRowid) {
        val rowidsFn = frame.sqlCtx.mainChainMapping().rowidsFunction
        // SELECT "<chain>.make_rowids"(?) — one-shot rowid block allocation. The `?` placeholder
        // is itself a Field so jOOQ doesn't consume our QueryPart args for the `?` substitution.
        val sqlField = DSL.field("{0}({1})", Any::class.java, DSL.name(rowidsFn), ANY_PARAM_PLACEHOLDER)
        val pSql = ParameterizedSql(
            "SELECT ${renderJooq(sqlField)}",
            listOf(Rt_IntValue.get(count.toLong())).toImmList(),
        )
        var firstRowidVar: Long? = null
        frame.userSqlExec.executeQuery(pSql) { row -> firstRowidVar = row.getLong(1) }
        firstRowidVar!!
    } else 0L

    val pageSize = if (attrs.isEmpty()) count else maxOf(frame.defCtx.globalCtx.sqlUpdatePortionSize / attrs.size, 1)
    val allResults = mutableListOf<Rt_Value>()
    val tableRef: Table<Record> = DSL.table(DSL.name(table))
    val rowidColField: Field<Any> = DSL.field(DSL.name(rowidCol))
    val attrColFields: List<Field<Any>> = attrs.map { DSL.field(DSL.name(it.sqlMapping)) }
    val rowidFnExpr: Field<Any> = DSL.field("{0}()", Any::class.java, DSL.name(rowidFn))

    for (pageStart in 0 until count step pageSize) {
        val pageEnd = minOf(pageStart + pageSize, count)
        val pageItems = list.subList(pageStart, pageEnd)

        val binds = mutableListOf<Rt_Value>()
        val q = JOOQ_CTX.insertQuery(tableRef)
        for ((idx, item) in pageItems.withIndex()) {
            if (idx > 0) q.newRecord()
            val structValue = item.asStruct()
            val row = LinkedHashMap<Field<*>, Field<*>>()
            if (useFastRowid) {
                row[rowidColField] = ANY_PARAM_PLACEHOLDER
                binds.add(Rt_IntValue.get(firstRowid + pageStart + idx))
            } else {
                row[rowidColField] = rowidFnExpr
            }
            for (i in attrs.indices) {
                row[attrColFields[i]] = ANY_PARAM_PLACEHOLDER
                binds.add(structValue.get(i))
            }
            q.addValues(row)
        }
        q.setReturning(rowidColField)

        val pSql = ParameterizedSql(renderJooq(q), binds.toImmList())
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

internal fun Rt_InterpreterImpl.executeUpdate(stmt: RR_Statement.Update, frame: Rt_CallFrame) {
    frame.checkDbUpdateAllowed()

    when (stmt.targetKind) {
        RR_UpdateTargetKind.EXPR_ONE -> {
            executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
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
                checkAtCount(frame, stmt.errPos, cardinality, count, "records")
            }
            return
        }

        RR_UpdateTargetKind.OBJECT -> {
            val count = executeUpdateSqlCount(stmt, frame)
            checkAtCount(frame, stmt.errPos, AtCardinality.ONE, count, "records")
            return
        }
    }
}

private fun Rt_InterpreterImpl.executeUpdateSqlCount(stmt: RR_Statement.Update, frame: Rt_CallFrame): Int {
    var count = 0
    executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
        frame.block(stmt.fromBlock) {
            count = executeUpdateSqlInner(stmt, frame)
        }
    }
    return count
}

private fun Rt_InterpreterImpl.executeUpdateSqlInner(stmt: RR_Statement.Update, frame: Rt_CallFrame): Int {
    val entityDef = rrApp.allEntities[stmt.entity.entityDefIndex]
    val allEntities = listOf(stmt.entity) + (stmt.extraEntities ?: emptyList())

    // Validate size constraints before building/executing SQL.
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
    val mainAlias = sqlGen.getEntityAlias(stmt.entity.entityId)!!

    // SET clause — build (col_sqlMapping, value Field) pairs. Building the value Field populates
    // any rel-joins lazily.
    val setEntries: List<Pair<String, Field<Any?>>> = stmt.what.map { w ->
        val attr = entityDef.strAttributes[w.attrName]!!
        attr.sqlMapping to sqlGen.dbExprToField(w.expr, frame)
    }

    // WHERE — generate the user WHERE field first (to populate lazy rel-joins), then collect them.
    val userWhereField = stmt.where?.let { sqlGen.dbExprToField(it, frame) }
    val joinConditions = sqlGen.joinConditionsAsConditions()

    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    val aliasedMain: Table<Record> = DSL.table(DSL.name(table)).`as`(DSL.unquotedName(mainAlias))
    val q = JOOQ_CTX.updateQuery(aliasedMain)

    val setMap = LinkedHashMap<Field<*>, Field<*>>()
    for (e in setEntries) {
        setMap[DSL.field(DSL.name(e.first)) as Field<*>] = e.second
    }
    q.addValues(setMap)

    val extras = stmt.extraEntities
    val joins = sqlGen.getAllRelJoins()
    if (extras != null) {
        for (extra in extras) {
            val extraDef = rrApp.allEntities[extra.entityDefIndex]
            q.addFrom(
                DSL.table(DSL.name(extraDef.sqlMapping.table(frame.sqlCtx)))
                    .`as`(DSL.unquotedName(sqlGen.getEntityAlias(extra.entityId)!!)),
            )
        }
    }
    for (join in joins) {
        q.addFrom(DSL.table(DSL.name(join.table)).`as`(DSL.unquotedName(join.alias)))
    }

    for (cond in joinConditions) q.addConditions(cond)
    if (userWhereField != null) q.addConditions(boolFieldToCondition(userWhereField))

    val snapshot = frame.exeCtx.opCtx.hasSnapshotContext()
    val allAttrs = entityDef.strAttributes.values.toList()
    val returnFields = mutableListOf<Field<*>>(aliasedCol(mainAlias, entityDef.sqlMapping.rowidColumn))
    if (snapshot) {
        for (attr in allAttrs) returnFields.add(aliasedCol(mainAlias, attr.sqlMapping))
    }
    q.setReturning(returnFields)

    val pSql = ParameterizedSql(renderJooq(q), sqlGen.binds().toImmList())
    if (!snapshot) {
        var count = 0
        frame.userSqlExec.executeQuery(pSql) { _ -> count++ }
        return count
    }

    val sqlAdapters = allAttrs.map { attr ->
        val rtType = resolveType(attr.type)
        checkNotNull(rtType.sqlAdapter) { "No SQL adapter for type: ${rtType.name}" }
    }

    val rows = mutableListOf<List<Rt_Value>>()
    frame.userSqlExec.executeQuery(pSql) { row ->
        val rowid = Rt_RowidValue.get(row.getLong(1))
        val attrValues = List(allAttrs.size) { i -> sqlAdapters[i].fromSql(row, i + 2, false) }
        rows.add(listOf(rowid) + attrValues)
    }
    emitUpdateSnapshot(frame, entityDef, rows)
    return rows.size
}

private fun Rt_InterpreterImpl.executeExprManyUpdate(stmt: RR_Statement.Update, frame: Rt_CallFrame) {
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

private fun Rt_InterpreterImpl.executeExprManyDelete(stmt: RR_Statement.Delete, frame: Rt_CallFrame) {
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

private fun Rt_InterpreterImpl.executeUpdateSql(stmt: RR_Statement.Update, frame: Rt_CallFrame) {
    frame.block(stmt.fromBlock) {
        executeUpdateSqlInner(stmt, frame)
    }
}

internal fun Rt_InterpreterImpl.executeDelete(stmt: RR_Statement.Delete, frame: Rt_CallFrame) {
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
                checkAtCount(frame, stmt.errPos, cardinality, count, "records")
            }
            return
        }

        RR_UpdateTargetKind.OBJECT -> {
            val count = executeDeleteSqlCount(stmt, frame)
            checkAtCount(frame, stmt.errPos, AtCardinality.ONE, count, "records")
            return
        }
    }
}

private fun Rt_InterpreterImpl.executeDeleteSqlCount(stmt: RR_Statement.Delete, frame: Rt_CallFrame): Int {
    var count = 0
    executeInLambdaBlock(stmt.lambdaBlock, stmt.lambdaVarPtr, stmt.lambdaExpr, frame) {
        frame.block(stmt.fromBlock) {
            count = executeDeleteSqlInner(stmt, frame)
        }
    }
    return count
}

private fun Rt_InterpreterImpl.executeDeleteSqlInner(stmt: RR_Statement.Delete, frame: Rt_CallFrame): Int {
    val entityDef = rrApp.allEntities[stmt.entity.entityDefIndex]
    val allEntities = listOf(stmt.entity) + (stmt.extraEntities ?: emptyList())

    val sqlGen = DbSqlGen(this, frame.sqlCtx, allEntities)
    val mainAlias = sqlGen.getEntityAlias(stmt.entity.entityId)!!

    // Generate user WHERE first so relationship JOINs are populated, then collect their conditions.
    val userWhereField = stmt.where?.let { sqlGen.dbExprToField(it, frame) }
    val joinConditions = sqlGen.joinConditionsAsConditions()

    val table = entityDef.sqlMapping.table(frame.sqlCtx)
    val aliasedMain: Table<Record> = DSL.table(DSL.name(table)).`as`(DSL.unquotedName(mainAlias))
    val q = JOOQ_CTX.deleteQuery(aliasedMain)

    val extras = stmt.extraEntities
    val joins = sqlGen.getAllRelJoins()
    if (extras != null) {
        for (extra in extras) {
            val extraDef = rrApp.allEntities[extra.entityDefIndex]
            q.addUsing(
                DSL.table(DSL.name(extraDef.sqlMapping.table(frame.sqlCtx)))
                    .`as`(DSL.unquotedName(sqlGen.getEntityAlias(extra.entityId)!!)),
            )
        }
    }
    for (join in joins) {
        q.addUsing(DSL.table(DSL.name(join.table)).`as`(DSL.unquotedName(join.alias)))
    }

    for (cond in joinConditions) q.addConditions(cond)
    if (userWhereField != null) q.addConditions(boolFieldToCondition(userWhereField))

    q.setReturning(aliasedCol(mainAlias, entityDef.sqlMapping.rowidColumn))

    val pSql = ParameterizedSql(renderJooq(q), sqlGen.binds().toImmList())
    val snapshot = frame.exeCtx.opCtx.hasSnapshotContext()
    if (!snapshot) {
        var count = 0
        frame.userSqlExec.executeQuery(pSql) { _ -> count++ }
        return count
    }

    val rowids = mutableListOf<Long>()
    frame.userSqlExec.executeQuery(pSql) { row -> rowids.add(row.getLong(1)) }
    emitDeleteSnapshot(frame, rowids)
    return rowids.size
}

private fun Rt_InterpreterImpl.executeDeleteSql(stmt: RR_Statement.Delete, frame: Rt_CallFrame) {
    frame.block(stmt.fromBlock) {
        executeDeleteSqlInner(stmt, frame)
    }
}

// --- Snapshot emission helpers ---

private fun Rt_InterpreterImpl.emitCreateSnapshot(
    frame: Rt_CallFrame,
    entityDef: RR_EntityDefinition,
    rowid: Long,
    attrValues: Map<String, Rt_Value>,
) {
    val opCtx = frame.exeCtx.opCtx
    if (!opCtx.hasSnapshotContext()) return

    val attrs = entityDef.strAttributes.values.associate { attr ->
        val rtType = resolveType(attr.type)
        val conv: Rt_GtvCompatibleValueClass<*> = checkNotNull(rtType.gtvConversion) {
            "No GTV conversion for type: ${rtType.name}"
        }
        attr.name to conv.rtToGtv(attrValues[attr.name] ?: Rt_NullValue, false)
    }.toImmMap()
    val datum = GtvFactory.gtv(GtvFactory.gtv(entityDef.sqlMapping.metaName), GtvFactory.gtv(attrs))
    opCtx.emitDatum(rowid, datum, false)
}

private fun Rt_InterpreterImpl.entityGtvConversions(
    entityDef: RR_EntityDefinition,
): List<Pair<String, Rt_GtvCompatibleValueClass<*>>> = entityDef.strAttributes.values.map { attr ->
    val rtType = resolveType(attr.type)
    val conv = checkNotNull(rtType.gtvConversion) { "No GTV conversion for type: ${rtType.name}" }
    attr.name to conv
}

private fun emitCreateSnapshotWith(
    frame: Rt_CallFrame,
    metaNameGtv: Gtv,
    rowid: Long,
    convs: List<Pair<String, Rt_GtvCompatibleValueClass<*>>>,
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

private fun Rt_InterpreterImpl.emitUpdateSnapshot(
    frame: Rt_CallFrame,
    entityDef: RR_EntityDefinition,
    rows: List<List<Rt_Value>>,
) {
    val opCtx = frame.exeCtx.opCtx
    val attrs = entityDef.strAttributes.values.toList()
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
