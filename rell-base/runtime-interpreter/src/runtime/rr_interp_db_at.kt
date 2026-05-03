/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.AtCardinality
import net.postchain.rell.base.model.expr.R_PartialArgMapping
import net.postchain.rell.base.model.expr.R_PartialCallMapping
import net.postchain.rell.base.model.expr.Rt_AtExprExtras
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import org.jooq.Field
import org.jooq.SortField
import org.jooq.impl.DSL

internal fun Rt_InterpreterImpl.evaluateDbAt(expr: RR_Expr.DbAt, frame: Rt_CallFrame): Rt_Value {
    val extras = expr.extras?.let { evaluateAtExtras(it, frame) } ?: Rt_AtExprExtras.NULL

    val nonOmitWhat = expr.what.filter { !it.flags.omit }

    // Pre-evaluate entity join WHERE expressions outside internals.block, inside from.block.
    // Their binds go into a separate side-list; the global `bindList` is appended in render
    // order (SELECT → FROM/JOIN ON → WHERE → ...) by [DbSqlGen.buildSelectQuery] using these
    // pre-captured side-binds, so the parallel `?` order is correct even though Field
    // construction here happens before SELECT/WHERE Field construction.
    val sqlGen = DbSqlGen(this, frame.sqlCtx, expr.from.entities)
    val entityJoinWhereSpecs = mutableMapOf<Int, DbSqlGen.JoinWhereSpec>()
    frame.blockOpt(expr.from.block) {
        for (entity in expr.from.entities) {
            val joinWhere = entity.joinWhere
            if (joinWhere != null) {
                val spec = frame.blockOpt(entity.joinBlock) {
                    sqlGen.captureJoinWhere(joinWhere, frame)
                }
                entityJoinWhereSpecs[entity.entityId] = spec
            }
        }
    }

    // If internals.block is a child of from.block, nest them correctly.
    // Otherwise, just enter internals.block directly.
    val fromBlock = expr.from.block
    val internalsBlock = expr.internals.block
    val needsFromBlock = fromBlock != null && internalsBlock != null
            && internalsBlock.parentUid == fromBlock.uid
    val (rCardinality, values) = frame.blockOpt(if (needsFromBlock) fromBlock else null) {
        frame.blockOpt(internalsBlock) {
            // Generate SELECT columns
            val selectFields = nonOmitWhat.map { field -> sqlGen.dbExprToField(field.expr, frame) }
            // Position in bindList where join-where binds need to splice (between SELECT and WHERE
            // binds, matching jOOQ's render order: SELECT → FROM/JOIN ON → WHERE → ...).
            val joinWhereBindPosition = sqlGen.bindCount()

            // Generate WHERE clause
            val whereField = expr.where?.let { w -> sqlGen.dbExprToField(w, frame) }

            // Generate GROUP BY
            val groupByFields = expr.what.filter { it.flags.group }
                .map { field -> sqlGen.dbExprToField(field.expr, frame) }

            // Generate ORDER BY
            val orderByEntries = generateDbAtOrderBy(expr, sqlGen, frame)

            // Build full SELECT
            val sql = sqlGen.buildSelectQuery(
                selectFields,
                whereField,
                groupByFields,
                orderByEntries,
                extras,
                entityJoinWhereSpecs,
                joinWhereBindPosition,
            )
            val resultTypes = nonOmitWhat.map { resolveType(it.resultType) }.toImmList()
            val select = SqlSelectRt(sql, resultTypes)
            var records = select.execute(frame.userSqlExec)

            // For object attribute access, try force-initializing the object if no records found.
            val objDefIdx = expr.objectDefIndex
            if (expr.objectName != null && records.isEmpty() && objDefIdx != null) {
                val rrObj = rrApp.allObjects[objDefIdx]
                val forced = frame.defCtx.appCtx.forceObjectInit(rrObj)
                if (forced) {
                    records = select.execute(frame.userSqlExec)
                }
            }

            // Check cardinality — for object attribute access, use object-specific error codes
            val rCardinality = expr.cardinality
            if (expr.objectName != null && !rCardinality.matches(records.size)) {
                val name = expr.objectName
                val count = records.size
                if (count == 0) {
                    throw Rt_Exception.common("obj_norec:$name", "No record for object '$name' in database")
                } else {
                    throw Rt_Exception.common(
                        "obj_multirec:$name:$count",
                        "Multiple records for object '$name' in database: $count",
                    )
                }
            }
            checkAtCount(frame, expr.errPos, rCardinality, records.size, "records")

            // Build result: decode rows to values (inside internals.block so R-level
            // expressions in field groups can access the correct block context)
            val elementRrType = expr.type.elementType()
            val elementRtType = resolveType(elementRrType)
            // Pre-create lazy R-expression caches (shared across rows for correct side-effect ordering).
            val groupCaches = expr.whatFieldGroups?.map { createGroupRExprCache(it, frame) }
            val values = records.map { row ->
                val logicalValues = applyFieldGroups(expr.whatFieldGroups, row, frame, groupCaches)
                if (elementRrType is RR_Type.Tuple && elementRrType.fields.size == logicalValues.size) {
                    Rt_TupleValue(elementRtType, logicalValues)
                } else {
                    logicalValues[0]
                }
            }.toMutableList()

            Pair(rCardinality, values)
        }
    }

    return if (rCardinality.many) {
        Rt_ListValue(resolveType(expr.type), values)
    } else if (values.isNotEmpty()) {
        values[0]
    } else {
        Rt_NullValue
    }
}


/**
 * Lazy cache for R-level expressions in a field group. Values are evaluated on first access
 * and cached for reuse across rows.
 */
private class LazyRExprCache(
    private val interpreter: Rt_Interpreter,
    private val frame: Rt_CallFrame,
    private val rExprs: List<RR_Expr>,
) {
    private val values = arrayOfNulls<Rt_Value>(rExprs.size)

    fun get(index: Int): Rt_Value {
        var v = values[index]
        if (v == null) {
            v = interpreter.evaluateExpr(rExprs[index], frame)
            values[index] = v
        }
        return v
    }
}

/** Per-group cache holding lazy R-expression values and sub-group caches. */
private class GroupRExprCache(
    val rValues: LazyRExprCache?,
    val subGroupCaches: List<GroupRExprCache>?,
)

private val EMPTY_GROUP_CACHE = GroupRExprCache(null, null)

private fun Rt_InterpreterImpl.createGroupRExprCache(
    group: RR_DbAtWhatFieldGroup,
    frame: Rt_CallFrame,
): GroupRExprCache {
    val rValues = group.rExprs?.let { LazyRExprCache(this, frame, it) }
    val subCaches = group.subGroups?.map { createGroupRExprCache(it, frame) }
    return if (rValues == null && subCaches == null) EMPTY_GROUP_CACHE
    else GroupRExprCache(rValues, subCaches)
}

/**
 * Applies field grouping to a flat row of DB values, combining groups according to their combiner.
 * If [groups] is null, returns the row unchanged (1:1 mapping).
 */
private fun Rt_InterpreterImpl.applyFieldGroups(
    groups: List<RR_DbAtWhatFieldGroup>?,
    row: List<Rt_Value>,
    frame: Rt_CallFrame,
    groupCaches: List<GroupRExprCache>?,
): List<Rt_Value> {
    if (groups == null) return row

    val result = mutableListOf<Rt_Value>()
    var pos = 0
    for ((i, group) in groups.withIndex()) {
        val count = group.columnCount
        val dbValues = row.subList(pos, pos + count)
        val cache = groupCaches?.getOrNull(i) ?: EMPTY_GROUP_CACHE
        val value = applyGroup(group, dbValues, frame, cache)
        result.add(value)
        pos += count
    }
    return result
}

/**
 * Lazily-evaluated list: elements are computed on first access and cached.
 */
private class LazyValueList(override val size: Int, private val compute: (Int) -> Rt_Value): AbstractList<Rt_Value>() {
    private val values = arrayOfNulls<Rt_Value>(size)

    override fun get(index: Int): Rt_Value {
        var v = values[index]
        if (v == null) {
            v = compute(index)
            values[index] = v
        }
        return v
    }
}

/**
 * Applies a single field group to its DB column slice, handling sub-groups, R-level expressions,
 * interleaving, and the group's combiner.
 */
private fun Rt_InterpreterImpl.applyGroup(
    group: RR_DbAtWhatFieldGroup,
    dbValues: List<Rt_Value>,
    frame: Rt_CallFrame,
    cache: GroupRExprCache,
): Rt_Value {
    // Lazy combiner: defer the entire inner evaluation until the lazy value is accessed.
    val combiner = group.combiner
    if (combiner is RR_DbAtFieldCombiner.Lazy) {
        val rtType = resolveType(combiner.type)
        val interpreter = this
        return Rt_DeferredLazyValue(rtType) {
            // Evaluate inner group with a pass-through combiner.
            val innerGroup = RR_DbAtWhatFieldGroup(
                group.columnCount, RR_DbAtFieldCombiner.Single,
                group.rExprs, group.itemOrder, group.subGroups,
            )
            interpreter.applyGroup(innerGroup, dbValues, frame, cache)
        }
    }

    val subGroups = group.subGroups
    val itemOrder = group.itemOrder
    val rExprCache = cache.rValues

    if (itemOrder != null) {
        // When there is interleaving, sub-group reduction must be lazy to preserve
        // the side-effect ordering: values are evaluated in itemOrder access order,
        // matching the original R_ model's lazy Rt_AtWhatItem semantics.
        val lazyDbValues: List<Rt_Value> = if (subGroups != null) {
            val subSlices = buildSubSlices(subGroups)
            LazyValueList(subGroups.size) { i ->
                val (subGroup, from, to) = subSlices[i]
                val subCache = cache.subGroupCaches?.getOrNull(i) ?: EMPTY_GROUP_CACHE
                applyGroup(subGroup, dbValues.subList(from, to), frame, subCache)
            }
        } else {
            dbValues
        }

        // Null-safe short-circuit: if the combiner has a base value and the base is null,
        // skip evaluating remaining R-expression arguments (preserving lazy semantics).
        if (combinerNeedsNullSafeBaseCheck(group.combiner) && itemOrder.isNotEmpty()) {
            val (firstIsDb, firstIdx) = itemOrder[0]
            val baseValue = if (firstIsDb) lazyDbValues[firstIdx] else rExprCache?.get(firstIdx) ?: Rt_NullValue
            if (baseValue == Rt_NullValue) {
                return Rt_NullValue
            }
        }

        // Interleave reduced DB values and R values according to itemOrder (index-based access).
        val allValues = itemOrder.map { (isDb, idx) ->
            if (isDb) lazyDbValues[idx] else rExprCache?.get(idx) ?: Rt_NullValue
        }
        return applyCombiner(group.combiner, allValues, frame)
    } else {
        // No interleaving: reduce sub-groups in order, with null-safe short-circuit when needed.
        val reducedDbValues = if (subGroups != null) {
            val needsNullSafe = combinerNeedsNullSafeBaseCheck(group.combiner) && subGroups.isNotEmpty()
            var subPos = 0
            val result = mutableListOf<Rt_Value>()
            for ((i, subGroup) in subGroups.withIndex()) {
                val subSlice = dbValues.subList(subPos, subPos + subGroup.columnCount)
                subPos += subGroup.columnCount
                val subCache = cache.subGroupCaches?.getOrNull(i) ?: EMPTY_GROUP_CACHE
                val value = applyGroup(subGroup, subSlice, frame, subCache)
                result.add(value)
                // After evaluating the base (first sub-group), check if it's null.
                // If so, skip evaluating remaining sub-groups (null-safe short-circuit).
                if (needsNullSafe && i == 0 && value == Rt_NullValue) {
                    return Rt_NullValue
                }
            }
            result
        } else {
            dbValues.toList()
        }
        return applyCombiner(group.combiner, reducedDbValues, frame)
    }
}

private fun buildSubSlices(
    subGroups: List<RR_DbAtWhatFieldGroup>,
): List<Triple<RR_DbAtWhatFieldGroup, Int, Int>> {
    var pos = 0
    return subGroups.map { subGroup ->
        val from = pos
        pos += subGroup.columnCount
        Triple(subGroup, from, pos)
    }
}

/** Whether the combiner requires checking the first (base) value for null before evaluating remaining args. */
private fun combinerNeedsNullSafeBaseCheck(combiner: RR_DbAtFieldCombiner): Boolean = when (combiner) {
    is RR_DbAtFieldCombiner.FunctionCall -> when (val call = combiner.call) {
        is RR_FunctionCall.Full -> call.target is RR_FunctionCallTarget.SysMember || call.target is RR_FunctionCallTarget.FunctionValue
        is RR_FunctionCall.Partial -> call.target is RR_FunctionCallTarget.SysMember || call.target is RR_FunctionCallTarget.FunctionValue
    }

    is RR_DbAtFieldCombiner.MemberAccess -> combiner.safe
    else -> false
}

private fun Rt_InterpreterImpl.applyCombiner(
    combiner: RR_DbAtFieldCombiner,
    values: List<Rt_Value>,
    frame: Rt_CallFrame,
): Rt_Value = when (combiner) {
    is RR_DbAtFieldCombiner.Single -> {
        if (values.size == 1) values[0] else if (values.isEmpty()) Rt_NullValue else values[0]
    }

    is RR_DbAtFieldCombiner.Tuple -> {
        Rt_TupleValue(resolveType(combiner.tupleType), values)
    }

    is RR_DbAtFieldCombiner.Struct -> {
        val rrStructType = RR_Type.Struct(combiner.structDefIndex)
        val rtType = resolveType(rrStructType)
        val mapping = combiner.attrMapping
        val orderedValues = if (mapping.isNotEmpty()) {
            // Reorder values from call-site order to struct field order using the attribute mapping.
            val reordered = MutableList<Rt_Value>(values.size) { Rt_NullValue }
            for (i in values.indices) {
                reordered[mapping[i]] = values[i]
            }
            reordered
        } else {
            values.toMutableList()
        }
        val attrNames = rrApp.allStructs[combiner.structDefIndex].struct.attributesList.map { it.name }
        Rt_StructValue(rtType, attrNames, orderedValues)
    }

    is RR_DbAtFieldCombiner.FunctionCall -> {
        val call = combiner.call
        if (call is RR_FunctionCall.Full) {
            // For SysMember and FunctionValue targets, the first value is the base object, rest are args.
            // For other targets, all values are args.
            val needsBase =
                call.target is RR_FunctionCallTarget.SysMember || call.target is RR_FunctionCallTarget.FunctionValue
            val (base, rawArgs) = if (needsBase && values.isNotEmpty()) {
                values[0] to values.subList(1, values.size)
            } else {
                null to values
            }
            // Null-safe: if the base is null (from nullable entity / safe access), return null.
            if (needsBase && base == Rt_NullValue) {
                Rt_NullValue
            } else {
                // Apply parameter mapping (paramsToExprs) if present — reorders call-site args to param order.
                val args = if (call.mapping.isNotEmpty() && call.mapping != rawArgs.indices.toList()) {
                    call.mapping.map { rawArgs[it] }
                } else {
                    rawArgs
                }
                callTarget(call.target, base, args, frame, call.callPos)
            }
        } else if (call is RR_FunctionCall.Partial) {
            // Partial application in what-clause: build a partial function value.
            val needsBase =
                call.target is RR_FunctionCallTarget.SysMember || call.target is RR_FunctionCallTarget.FunctionValue
            val (base, rawArgs) = if (needsBase && values.isNotEmpty()) {
                values[0] to values.subList(1, values.size)
            } else {
                null to values
            }
            // Null-safe: if the base is null, return null.
            if (needsBase && base == Rt_NullValue) {
                Rt_NullValue
            } else {
                val rtType = resolveType(call.returnType)
                val mappingArgs = call.mappingValues.mapToImmList { idx ->
                    if (idx < 0) R_PartialArgMapping(true, -(idx + 1))
                    else R_PartialArgMapping(false, idx)
                }
                val mapping = R_PartialCallMapping(rawArgs.size, call.wildArgCount, mappingArgs)
                val rTarget = Rt_FunctionCallTarget(this, call.target, frame)
                createFunctionValueFromTarget(rTarget, rtType, mapping, base, rawArgs)
            }
        } else {
            if (values.size == 1) values[0] else Rt_TupleValue(
                resolveType(RR_Type.Primitive(RR_PrimitiveKind.UNIT)),
                values,
            )
        }
    }

    is RR_DbAtFieldCombiner.ListLiteral -> {
        Rt_ListValue(resolveType(combiner.listType), values.toMutableList())
    }

    is RR_DbAtFieldCombiner.MapLiteral -> {
        val map = mutableMapOf<Rt_Value, Rt_Value>()
        var i = 0
        while (i + 1 < values.size) {
            map[values[i]] = values[i + 1]
            i += 2
        }
        Rt_MapValue(resolveType(combiner.mapType), map)
    }

    is RR_DbAtFieldCombiner.MemberAccess -> {
        val base = if (values.isNotEmpty()) values[0] else Rt_NullValue
        if (combiner.safe && base == Rt_NullValue) {
            Rt_NullValue
        } else {
            evaluateMemberCalculator(combiner.calculator, base, frame)
        }
    }

    is RR_DbAtFieldCombiner.Lazy -> {
        Rt_EagerLazyValue(resolveType(combiner.type), values[0])
    }
}

private data class OrderByElement(val baseField: Field<Any?>, val desc: Boolean)

internal fun Rt_InterpreterImpl.generateDbAtOrderBy(
    expr: RR_Expr.DbAt,
    sqlGen: DbSqlGen,
    frame: Rt_CallFrame,
): List<SortField<*>> {
    // Build entries while deduplicating in-place by rendered base SQL — matches the R_ model's
    // translateOrderBy de-dup. A duplicate entry that has already been built must roll its binds
    // back so the parallel bind list lines up with the `?` placeholders in the rendered SQL.
    val seen = mutableSetOf<Pair<String, List<Rt_Value>>>()
    val elements = mutableListOf<OrderByElement>()

    fun tryAdd(field: Field<Any?>, desc: Boolean, savedBinds: Int) {
        val rendered = renderJooq(field)
        val newBinds = sqlGen.binds().subList(savedBinds, sqlGen.bindCount()).toList()
        // Dedup by rendered SQL AND by the bind values added during this Field's construction.
        // Mirrors the R_ model's `distinctBy { it.baseSql }` where `baseSql` was a (sql, params)
        // ParameterizedSql — two `.firstName[0]` and `.firstName[1]` look identical in the rendered
        // template but differ by their bound `0`/`1` values, so they must remain distinct.
        if (seen.add(rendered to newBinds)) {
            elements.add(OrderByElement(field, desc))
        } else {
            sqlGen.truncateBinds(savedBinds)
        }
    }

    // Explicit sort fields
    for (field in expr.what) {
        if (field.flags.sort != 0) {
            val saved = sqlGen.bindCount()
            val f = sqlGen.dbExprToField(field.expr, frame)
            tryAdd(f, field.flags.sort < 0, saved)
        }
    }

    val hasGroup = expr.what.any { it.flags.group }
    val hasAggregate = expr.what.any { it.flags.aggregate }
    if (hasGroup) {
        for (field in expr.what) {
            if (field.flags.group && field.flags.sort == 0) {
                val saved = sqlGen.bindCount()
                val f = sqlGen.dbExprToField(field.expr, frame)
                tryAdd(f, false, saved)
            }
        }
    } else if (!hasAggregate && (expr.cardinality.isMany || expr.extras?.limit != null || expr.extras?.offset != null)) {
        for (entity in expr.from.entities) {
            val entityDef = rrApp.allEntities[entity.entityDefIndex]
            val alias = sqlGen.getEntityAlias(entity.entityId)
            if (alias != null) {
                val saved = sqlGen.bindCount()
                val f = DbSqlGen.columnFieldExt(alias, entityDef.sqlMapping.rowidColumn)
                tryAdd(f, false, saved)
            }
        }
    }

    // Emit ascending sort as the bare field (no `ASC`) and descending as `field DESC` to match
    // the legacy hand-rolled emitter's byte output. `Field.sortDefault()` renders without any
    // direction keyword; descending sorts wrap the rendered field with a trailing ` DESC`.
    return elements.map { e ->
        if (e.desc) DSL.field(renderJooq(e.baseField) + " DESC", Any::class.java).sortDefault()
        else e.baseField.sortDefault()
    }
}

internal fun Rt_InterpreterImpl.evaluateAtExtras(extras: RR_AtExtras, frame: Rt_CallFrame): Rt_AtExprExtras {
    val limit = extras.limit?.let {
        val v = evaluateExpr(it, frame).asInteger()
        if (v < 0) throw Rt_Exception.common("expr:at:limit:negative:$v", "Negative limit: $v")
        v
    }
    val offset = if (limit != null && limit <= 0L) null else extras.offset?.let {
        val v = evaluateExpr(it, frame).asInteger()
        if (v < 0) throw Rt_Exception.common("expr:at:offset:negative:$v", "Negative offset: $v")
        v
    }
    return Rt_AtExprExtras(limit, offset)
}

internal fun Rt_InterpreterImpl.evaluateColAt(expr: RR_Expr.ColAt, frame: Rt_CallFrame): Rt_Value {
    val extras = expr.extras?.let { evaluateAtExtras(it, frame) } ?: Rt_AtExprExtras.NULL
    val rCardinality = expr.cardinality
    val extrasLimit = extras.limit
    if (extrasLimit != null && extrasLimit <= 0L) {
        checkAtCount(frame, expr.errPos, rCardinality, 0, "values")
        return if (rCardinality.many) Rt_ListValue(resolveType(expr.type), mutableListOf()) else Rt_NullValue
    }

    // Evaluate the source collection in from.block (matches R_ColAtFrom.evaluate)
    val fromValue = frame.blockOpt(expr.from.block) {
        evaluateExpr(expr.from.expr, frame)
    }
    val iterable = when (expr.from.iterableAdapter) {
        RR_IterableAdapterKind.DIRECT -> fromValue.asIterable()
        RR_IterableAdapterKind.LEGACY_MAP -> {
            fromValue.asMap().entries.map { (k, v) ->
                Rt_TupleValue(resolveType(expr.param.type), listOf(k, v))
            }
        }
    }

    // Use native summarization pipeline for all cases (including NONE — uses sorting and selectedFields)
    return evaluateColAtWithNativeSummarization(expr, frame, iterable, extras, rCardinality)
}

/**
 * Native ColAt evaluation with summarization — fully serializable, no R_ model dependencies.
 * Handles grouping, aggregation (sum/min/max/list/set/map), sorting, selectedFields, and row decoding.
 */
private fun Rt_InterpreterImpl.evaluateColAtWithNativeSummarization(
    expr: RR_Expr.ColAt,
    frame: Rt_CallFrame,
    iterable: Iterable<Rt_Value>,
    extras: Rt_AtExprExtras,
    rCardinality: AtCardinality,
): Rt_Value {
    val fieldCount = expr.what.fieldCount
    val hasSorting = expr.sorting.isNotEmpty()
    // For NONE without sorting, apply LIMIT/OFFSET eagerly (before evaluating what-fields)
    val earlyLimiting = expr.summarization == RR_ColAtSummarizationKind.NONE && !hasSorting

    // Collect records — for NONE, just filter; for GROUP/ALL, aggregate
    val summarized: List<List<Rt_Value>> = when (expr.summarization) {
        RR_ColAtSummarizationKind.NONE -> {
            val results = mutableListOf<List<Rt_Value>>()
            var offsetRemaining = if (earlyLimiting) (extras.offset ?: 0L) else 0L
            var limitRemaining = if (earlyLimiting) (extras.limit ?: Long.MAX_VALUE) else Long.MAX_VALUE

            frame.block(expr.block) {
                for (item in iterable) {
                    if (earlyLimiting && limitRemaining <= 0L) break
                    frame.setUnchecked(expr.param.ptr, item, true)
                    val matches = evaluateExpr(expr.where, frame).asBoolean()
                    if (!matches) continue
                    if (earlyLimiting && offsetRemaining > 0L) {
                        offsetRemaining--; continue
                    }
                    val values = expr.what.fields.map { evaluateExpr(it.expr, frame) }
                    results.add(values)
                    if (earlyLimiting) limitRemaining--
                }
            }
            results
        }

        RR_ColAtSummarizationKind.GROUP -> {
            val groupFields = expr.what.groupFields ?: emptyList()
            val map = mutableMapOf<List<Rt_Value>, Array<Rt_Value?>>()

            frame.block(expr.block) {
                for (item in iterable) {
                    frame.setUnchecked(expr.param.ptr, item, true)
                    val matches = evaluateExpr(expr.where, frame).asBoolean()
                    if (!matches) continue

                    val values = expr.what.fields.map { evaluateExpr(it.expr, frame) }
                    val key = groupFields.map { values[it] }
                    val existing = map.getOrPut(key) { arrayOfNulls(fieldCount) }
                    for (i in values.indices) {
                        existing[i] = aggregateValue(expr.fieldSummarizations[i], existing[i], values[i])
                    }
                }
            }

            map.values.map { arr ->
                arr.indices.map { i -> finalizeAggregatedValue(expr.fieldSummarizations[i], arr[i]) }
            }
        }

        RR_ColAtSummarizationKind.ALL -> {
            val aggregated = arrayOfNulls<Rt_Value?>(fieldCount)

            frame.block(expr.block) {
                for (item in iterable) {
                    frame.setUnchecked(expr.param.ptr, item, true)
                    val matches = evaluateExpr(expr.where, frame).asBoolean()
                    if (!matches) continue

                    val values = expr.what.fields.map { evaluateExpr(it.expr, frame) }
                    for (i in values.indices) {
                        aggregated[i] = aggregateValue(expr.fieldSummarizations[i], aggregated[i], values[i])
                    }
                }
            }

            val result =
                aggregated.indices.map { i -> finalizeAggregatedValue(expr.fieldSummarizations[i], aggregated[i]) }
            listOf(result)
        }
        // All cases handled above
    }

    // Sort
    var rows = summarized
    if (hasSorting) {
        rows = rows.sortedWith(
            Comparator { a, b ->
                for (sortEntry in expr.sorting) {
                    val va = a[sortEntry.fieldIndex]
                    val vb = b[sortEntry.fieldIndex]
                    // Use the runtime value's type for comparison (handles aggregated types correctly).
                    val rtType = try {
                        va.type
                    } catch (_: Exception) {
                        null
                    }
                        ?: resolveType(expr.what.fields[sortEntry.fieldIndex].expr.type)
                    val comparator = rtType.comparator ?: Comparator { x, y ->
                        @Suppress("UNCHECKED_CAST")
                        (x as Comparable<Any>).compareTo(y as Comparable<Any>)
                    }
                    val cmp = comparator.compare(va, vb)
                    if (cmp != 0) return@Comparator if (sortEntry.ascending) cmp else -cmp
                }
                0
            },
        )
    }

    // Apply LIMIT/OFFSET (skip if already applied eagerly in the NONE path)
    if (!earlyLimiting) {
        val extrasOffset = extras.offset
        if (extrasOffset != null && extrasOffset > 0) {
            rows = rows.drop(extrasOffset.toInt())
        }
        val extrasLimit2 = extras.limit
        if (extrasLimit2 != null) {
            rows = rows.take(extrasLimit2.toInt())
        }
    }

    // Select output fields and decode rows
    val selectedFields = expr.what.selectedFields
    val elementRrType = expr.type.elementType()
    val elementRtType = resolveType(elementRrType)

    val resList: MutableList<Rt_Value> = ArrayList(rows.size)
    val wrapInTuple = elementRrType is RR_Type.Tuple && elementRrType.fields.size == selectedFields.size
    for (rowValues in rows) {
        val selValues = selectedFields.map { rowValues[it] }
        val value = if (wrapInTuple) Rt_TupleValue(elementRtType, selValues) else selValues[0]
        resList.add(value)
    }

    checkAtCount(frame, expr.errPos, rCardinality, resList.size, "values")

    return if (rCardinality.many) {
        Rt_ListValue(resolveType(expr.type), resList)
    } else if (resList.isNotEmpty()) {
        resList[0]
    } else {
        Rt_NullValue
    }
}

/** Aggregate a single field value into the accumulator based on the summarization kind. */
private fun Rt_InterpreterImpl.aggregateValue(
    info: RR_ColAtFieldSummarizationInfo,
    existing: Rt_Value?,
    newValue: Rt_Value,
): Rt_Value = when (info.kind) {
    RR_ColAtFieldSummarizationKind.NONE, RR_ColAtFieldSummarizationKind.GROUP -> newValue

    RR_ColAtFieldSummarizationKind.SUM -> {
        if (existing == null) newValue else evaluateBinaryOp(info.binaryOpKey!!, existing, newValue)
    }

    RR_ColAtFieldSummarizationKind.MIN -> {
        if (existing == null) newValue else {
            val comparator = existing.type.comparator ?: Comparator { a, b ->
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b as Comparable<Any>)
            }
            val cmp = comparator.compare(existing, newValue)
            if (cmp <= 0) existing else newValue
        }
    }

    RR_ColAtFieldSummarizationKind.MAX -> {
        if (existing == null) newValue else {
            val comparator = existing.type.comparator ?: Comparator { a, b ->
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b as Comparable<Any>)
            }
            val cmp = comparator.compare(existing, newValue)
            if (cmp >= 0) existing else newValue
        }
    }

    RR_ColAtFieldSummarizationKind.LIST -> {
        val list = existing ?: Rt_ListValue(resolveType(info.collectionType!!), mutableListOf())
        list.asList().add(newValue)
        list
    }

    RR_ColAtFieldSummarizationKind.SET -> {
        val set = existing ?: Rt_SetValue(resolveType(info.collectionType!!), mutableSetOf())
        set.asSet().add(newValue)
        set
    }

    RR_ColAtFieldSummarizationKind.MAP -> {
        val map = existing ?: Rt_MapValue(resolveType(info.collectionType!!), mutableMapOf())
        val entry = newValue.asTuple()
        val k = entry[0]
        val v = entry[1]
        val prev = map.asMutableMap().put(k, v)
        if (prev != null) {
            throw Rt_Exception.common("aggregate:map:dupkey:${k.strCode()}", "Duplicate map key: ${k.str()}")
        }
        map
    }
}

/** Finalize an aggregated value after all records have been processed. */
private fun Rt_InterpreterImpl.finalizeAggregatedValue(
    info: RR_ColAtFieldSummarizationInfo,
    value: Rt_Value?,
): Rt_Value = when (info.kind) {
    RR_ColAtFieldSummarizationKind.NONE, RR_ColAtFieldSummarizationKind.GROUP -> value!!
    RR_ColAtFieldSummarizationKind.SUM -> value ?: toRtValue(info.zeroValue!!)
    RR_ColAtFieldSummarizationKind.MIN, RR_ColAtFieldSummarizationKind.MAX -> value ?: Rt_NullValue
    RR_ColAtFieldSummarizationKind.LIST -> value ?: Rt_ListValue(resolveType(info.collectionType!!), mutableListOf())
    RR_ColAtFieldSummarizationKind.SET -> value ?: Rt_SetValue(resolveType(info.collectionType!!), mutableSetOf())
    RR_ColAtFieldSummarizationKind.MAP -> value ?: Rt_MapValue(resolveType(info.collectionType!!), mutableMapOf())
}