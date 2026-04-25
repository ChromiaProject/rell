/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.model.R_EntitySqlMapping
import net.postchain.rell.base.model.expr.Rt_AtExprExtras
import net.postchain.rell.base.model.rr.*
import net.postchain.rell.base.utils.toImmList

data class DbSqlAliasInfo(val alias: String, val entityDef: RR_EntityDefinition)
data class DbSqlJoinInfo(
    val table: String,
    val alias: String,
    val baseAlias: String,
    val baseColumn: String,
    val targetRowid: String
)

class DbSqlGen private constructor(
    private val interpreter: Rt_Interpreter,
    private val sqlCtx: Rt_SqlContext,
    private val entities: List<RR_DbAtEntity>,
    private val parent: DbSqlGen?,
    private val aliasCounter: AliasCounter,
) {
    private val entityAliases = mutableMapOf<Int, DbSqlAliasInfo>()
    private val relJoinAliases = mutableMapOf<Pair<String, String>, DbSqlAliasInfo>()

    /** Relationship JOINs grouped by the root entity alias they stem from. */
    private val entityRelJoins = mutableMapOf<String, MutableList<DbSqlJoinInfo>>()

    /** Maps each alias to its root entity alias (for tracking join ownership). */
    private val aliasToRootEntity = mutableMapOf<String, String>()

    /** Cache of pre-evaluated [RR_DbExpr.Interpreted] expressions. Uses identity-based keys
     *  to avoid sharing cache entries across different Interpreted instances with the same content.
     *  Used by WHEN short-circuiting to avoid re-evaluating R-level expressions with side effects. */
    private val reducedCache = java.util.IdentityHashMap<RR_DbExpr.Interpreted, Rt_Value>()

    constructor(
        interpreter: Rt_Interpreter,
        sqlCtx: Rt_SqlContext,
        entities: List<RR_DbAtEntity>,
    ): this(interpreter, sqlCtx, entities, null, AliasCounter())

    init {
        for (entity in entities) {
            val alias = nextAlias()
            val def = interpreter.rrApp.allEntities[entity.entityDefIndex]
            entityAliases[entity.entityId] = DbSqlAliasInfo(alias, def)
            aliasToRootEntity[alias] = alias
        }
    }

    private fun nextAlias() = "A%02d".format(aliasCounter.next())

    /** Creates a child [DbSqlGen] that inherits entity aliases from this context.
     *  Used for correlated subqueries (EXISTS/IN) where inner queries reference outer entities. */
    private fun createSub(innerEntities: List<RR_DbAtEntity>): DbSqlGen {
        return DbSqlGen(interpreter, sqlCtx, innerEntities, this, aliasCounter)
    }

    /** Shared mutable alias counter so parent and child contexts produce unique aliases. */
    private class AliasCounter {
        private var value = 0
        fun next(): Int = value++
    }

    fun getEntityAlias(entityId: Int): String? = entityAliases[entityId]?.alias

    private fun getEntityRelJoins(rootAlias: String): List<DbSqlJoinInfo> = entityRelJoins[rootAlias] ?: emptyList()

    /** Returns all relationship JOINs. */
    fun getAllRelJoins(): List<DbSqlJoinInfo> = entityRelJoins.values.flatten()

    fun appendJoinConditions(b: SqlBuilder) {
        var first = true
        for (join in getAllRelJoins()) {
            if (!first) b.append(" AND ")
            b.append("(")
            b.appendColumn(join.baseAlias, join.baseColumn)
            b.append(" = ")
            b.appendColumn(join.alias, join.targetRowid)
            b.append(")")
            first = false
        }
    }

    private fun resolveEntityAlias(entityId: Int): DbSqlAliasInfo = checkNotNull(
        entityAliases[entityId] ?: parent?.resolveEntityAlias(entityId),
    ) {
        "Entity alias not found for entityId=$entityId"
    }

    /** Finds which context in the parent chain owns the given entity alias. */
    private fun findOwnerForAlias(alias: String): DbSqlGen = if (alias in aliasToRootEntity) {
        this
    } else {
        parent?.findOwnerForAlias(alias) ?: this
    }

    private fun resolveTableExpr(expr: RR_DbExpr): Pair<String, RR_EntityDefinition> = when (expr) {
        is RR_DbExpr.Entity -> {
            val info = resolveEntityAlias(expr.entityId)
            info.alias to info.entityDef
        }

        is RR_DbExpr.Rel -> {
            val (baseAlias, baseDef) = resolveTableExpr(expr.base)
            val key = baseAlias to expr.attrName
            // Find the context that owns the base alias — joins must be registered there
            // so that relationship JOINs for outer entities appear in the outer FROM clause.
            val ownerCtx = findOwnerForAlias(baseAlias)
            val cached = ownerCtx.relJoinAliases[key]
            if (cached != null) {
                cached.alias to cached.entityDef
            } else {
                val targetDef = interpreter.rrApp.allEntities[expr.targetEntityDefIndex]
                val alias = nextAlias()
                val info = DbSqlAliasInfo(alias, targetDef)
                ownerCtx.relJoinAliases[key] = info
                val baseColumn = baseDef.strAttributes[expr.attrName]?.sqlMapping ?: expr.attrName
                val table = targetDef.sqlMapping.table(sqlCtx)
                val joinInfo = DbSqlJoinInfo(table, alias, baseAlias, baseColumn, targetDef.sqlMapping.rowidColumn)
                // Track which root entity this join belongs to
                val rootAlias = ownerCtx.aliasToRootEntity[baseAlias] ?: baseAlias
                ownerCtx.aliasToRootEntity[alias] = rootAlias
                ownerCtx.entityRelJoins.getOrPut(rootAlias) { mutableListOf() }.add(joinInfo)
                alias to targetDef
            }
        }

        else -> error("Cannot resolve table for: ${expr::class.simpleName}")
    }

    fun dbExprToSql(expr: RR_DbExpr, b: SqlBuilder, frame: Rt_CallFrame, enclose: Boolean) {
        when (expr) {
            is RR_DbExpr.Interpreted -> {
                // Check the pre-evaluation cache first (populated by reduceDbExpr during WHEN processing)
                val value = reducedCache[expr] ?: interpreter.evaluateExpr(expr.expr, frame)
                if (value == Rt_NullValue) {
                    b.append("NULL")
                } else {
                    b.append(value)
                }
            }

            is RR_DbExpr.Binary -> {
                val sql = expr.op  // op is now the SQL operator string directly (e.g., "||", "=", "AND")

                // Short-circuit AND/OR: mirrors Db_BinaryOp_AndOr.toRedExpr behavior.
                // For AND: if left is constant false, emit FALSE; if left is constant true, emit right only.
                // For OR: if left is constant true, emit TRUE; if left is constant false, emit right only.
                if (sql == "AND" || sql == "OR") {
                    val shortCircuitValue = sql == "OR" // OR short-circuits on true, AND on false
                    val leftConst = tryEvaluateInterpretedBool(expr.left, frame)
                    if (leftConst != null) {
                        if (leftConst == shortCircuitValue) {
                            b.append(if (shortCircuitValue) "TRUE" else "FALSE")
                            return
                        } else {
                            // Left is the non-short-circuit value — emit right only
                            dbExprToSql(expr.right, b, frame, enclose)
                            return
                        }
                    }
                    val rightConst = tryEvaluateInterpretedBool(expr.right, frame)
                    if (rightConst != null) {
                        if (rightConst == shortCircuitValue) {
                            b.append(if (shortCircuitValue) "TRUE" else "FALSE")
                            return
                        } else {
                            // Right is the non-short-circuit value — emit left only
                            dbExprToSql(expr.left, b, frame, enclose)
                            return
                        }
                    }
                }

                val wrapDecimal = isDecimalType(expr.type)
                if (wrapDecimal) b.append("ROUND(")
                if (enclose) b.append("(")
                // Handle nullable equality specially
                if (expr.nullableEq) {
                    val isEqual = sql == "IS NOT DISTINCT FROM"
                    // Check for constant NULL on either side
                    val left = expr.left
                    val right = expr.right
                    val leftConst =
                        if (left is RR_DbExpr.Interpreted) interpreter.evaluateExpr(left.expr, frame) else null
                    val rightConst =
                        if (right is RR_DbExpr.Interpreted) interpreter.evaluateExpr(right.expr, frame) else null
                    if (rightConst == Rt_NullValue) {
                        dbExprToSql(expr.left, b, frame, true)
                        b.append(if (isEqual) " IS NULL" else " IS NOT NULL")
                    } else if (leftConst == Rt_NullValue) {
                        dbExprToSql(expr.right, b, frame, true)
                        b.append(if (isEqual) " IS NULL" else " IS NOT NULL")
                    } else if (rightConst != null) {
                        dbExprToSql(expr.left, b, frame, true)
                        b.append(" $sql ")
                        b.append(rightConst)
                    } else if (leftConst != null) {
                        dbExprToSql(expr.right, b, frame, true)
                        b.append(" $sql ")
                        b.append(leftConst)
                    } else {
                        dbExprToSql(expr.left, b, frame, true)
                        b.append(" $sql ")
                        dbExprToSql(expr.right, b, frame, true)
                    }
                } else if (sql.startsWith("FN:")) {
                    // Function-call style: FN:DIV → DIV(left, right)
                    val fnName = sql.removePrefix("FN:")
                    b.append("$fnName(")
                    dbExprToSql(expr.left, b, frame, false)
                    b.append(", ")
                    dbExprToSql(expr.right, b, frame, false)
                    b.append(")")
                } else {
                    dbExprToSql(expr.left, b, frame, true)
                    b.append(" $sql ")
                    dbExprToSql(expr.right, b, frame, true)
                }
                if (enclose) b.append(")")
                if (wrapDecimal) b.append(", ${Lib_DecimalMath.DECIMAL_FRAC_DIGITS})")
            }

            is RR_DbExpr.Unary -> {
                val sql = interpreter.dbUnaryOpSql(expr.op)
                if (enclose) b.append("(")
                b.append(sql)
                b.append(" ")
                dbExprToSql(expr.expr, b, frame, true)
                if (enclose) b.append(")")
            }

            is RR_DbExpr.Entity -> {
                val info = resolveEntityAlias(expr.entityId)
                b.appendColumn(info.alias, info.entityDef.sqlMapping.rowidColumn)
            }

            is RR_DbExpr.Attr -> {
                val (alias, entityDef) = resolveTableExpr(expr.base)
                val sqlCol = entityDef.strAttributes[expr.attrName]?.sqlMapping ?: expr.attrName
                val wrapDecimal = isDecimalType(expr.type)
                if (wrapDecimal) b.append("ROUND(")
                b.appendColumn(alias, sqlCol)
                if (wrapDecimal) b.append(", ${Lib_DecimalMath.DECIMAL_FRAC_DIGITS})")
            }

            is RR_DbExpr.Rel -> {
                val (baseAlias, baseDef) = resolveTableExpr(expr.base)
                val sqlCol = baseDef.strAttributes[expr.attrName]?.sqlMapping ?: expr.attrName
                b.appendColumn(baseAlias, sqlCol)
            }

            is RR_DbExpr.Rowid -> {
                // Optimization: Rowid(Rel(base, attr, target)) means "get the rowid of the entity
                // referenced by a foreign key column". Since the FK column already stores the target
                // rowid, we can read it directly from the base table without creating a JOIN.
                // This mirrors the old R_ model behavior where Db_RowidExpr delegates to
                // Db_RelExpr.toRedExpr() which outputs the FK column directly.
                val relBase = expr.base
                if (relBase is RR_DbExpr.Rel) {
                    val (baseAlias, baseDef) = resolveTableExpr(relBase.base)
                    val sqlCol = baseDef.strAttributes[relBase.attrName]?.sqlMapping ?: relBase.attrName
                    b.appendColumn(baseAlias, sqlCol)
                } else {
                    val (alias, entityDef) = resolveTableExpr(expr.base)
                    b.appendColumn(alias, entityDef.sqlMapping.rowidColumn)
                }
            }

            is RR_DbExpr.CollectionInterpreted -> {
                val value = interpreter.evaluateExpr(expr.expr, frame)
                val collection = value.asCollection()
                b.append("(")
                b.append(collection.toList(), ",") { v: Rt_Value -> b.append(v) }
                b.append(")")
            }

            is RR_DbExpr.In -> {
                dbExprToSql(expr.keyExpr, b, frame, true)
                if (expr.not) b.append(" NOT")
                b.append(" IN (")
                b.append(expr.exprs.toList(), ",") { e: RR_DbExpr ->
                    dbExprToSql(e, b, frame, false)
                }
                b.append(")")
            }

            is RR_DbExpr.Elvis -> {
                // Short-circuit if left is an R-level constant:
                // null -> use right directly; non-null -> use left directly
                val leftConst = tryEvaluateInterpreted(expr.left, frame)
                if (leftConst != null && leftConst == Rt_NullValue) {
                    dbExprToSql(expr.right, b, frame, false)
                } else if (leftConst != null) {
                    dbExprToSql(expr.left, b, frame, false)
                } else {
                    b.append("COALESCE(")
                    dbExprToSql(expr.left, b, frame, false)
                    b.append(", ")
                    dbExprToSql(expr.right, b, frame, false)
                    b.append(")")
                }
            }

            is RR_DbExpr.Call -> {
                val wrapDecimal = isDecimalType(expr.type)
                if (wrapDecimal) b.append("ROUND(")
                dbFnToSql(expr.fn, expr.args, b, frame)
                if (wrapDecimal) b.append(", ${Lib_DecimalMath.DECIMAL_FRAC_DIGITS})")
            }

            is RR_DbExpr.Exists -> {
                when (val subExpr = expr.subExpr) {
                    is RR_DbExpr.SubQuery -> {
                        // Correlated subquery — generate EXISTS(SELECT ...) inline.
                        // SubQuery already wraps itself in parentheses, so EXISTS(...) is correct.
                        if (expr.not) b.append("NOT ")
                        b.append("EXISTS")
                        dbExprToSql(subExpr, b, frame, false)
                    }

                    is RR_DbExpr.Interpreted -> {
                        // Non-subquery expression (e.g. collection at inside EXISTS in a DB context).
                        // Evaluate the expression directly and emit TRUE/FALSE.
                        val value = interpreter.evaluateExpr(subExpr.expr, frame)
                        val exists = when {
                            value === Rt_NullValue -> false
                            else -> try {
                                value.asCollection().isNotEmpty()
                            } catch (_: Exception) {
                                true
                            }
                        }
                        val result = if (expr.not) !exists else exists
                        b.append(if (result) "TRUE" else "FALSE")
                    }

                    else -> {
                        // Fallback — generate EXISTS(...) and hope for the best
                        if (expr.not) b.append("NOT ")
                        b.append("EXISTS(")
                        dbExprToSql(subExpr, b, frame, false)
                        b.append(")")
                    }
                }
            }

            is RR_DbExpr.InCollection -> {
                val rightValue = interpreter.evaluateExpr(expr.right, frame).asCollection()
                if (rightValue.isEmpty()) {
                    b.append(if (expr.not) "TRUE" else "FALSE")
                } else {
                    dbExprToSql(expr.left, b, frame, true)
                    if (expr.not) b.append(" NOT")
                    b.append(" IN (")
                    b.append(rightValue.toList(), ",") { v: Rt_Value -> b.append(v) }
                    b.append(")")
                }
            }

            is RR_DbExpr.When -> {
                dbWhenToSql(expr, b, frame)
            }

            is RR_DbExpr.NestedAt -> {
                dbExprToSql(expr.inner, b, frame, false)
            }

            is RR_DbExpr.SubQuery -> {
                // Subquery SQL must always be enclosed in parentheses for correct syntax
                // when used as operand of IN or other operators.
                // Note: EXISTS already wraps in EXISTS(...), so extra parens are harmless.
                b.append("(")
                buildInlineSubQuery(expr, b, frame)
                b.append(")")
            }
        }
    }

    /**
     * Generates an inline SQL subquery for [RR_DbExpr.SubQuery].
     * Creates a child [DbSqlGen] that can resolve entity aliases from the outer query,
     * enabling correlated subqueries (e.g. EXISTS/IN).
     */
    private fun buildInlineSubQuery(expr: RR_DbExpr.SubQuery, b: SqlBuilder, frame: Rt_CallFrame) {
        val innerSqlGen = createSub(expr.from.entities)

        val extras = expr.extras?.let { interpreter.evaluateAtExtras(it, frame) } ?: Rt_AtExprExtras.NULL

        // Pre-evaluate entity join WHERE expressions (from nested join at-expressions like
        // `h: home @* { h.person == p, h.city == c.city }`). These must be evaluated before
        // entering internals.block, and each entity's joinBlock provides the correct scope.
        val entityJoinWheres = mutableMapOf<Int, ParameterizedSql>()
        frame.blockOpt(expr.from.block) {
            for (entity in expr.from.entities) {
                val joinWhere = entity.joinWhere
                if (joinWhere != null) {
                    val joinSql = frame.blockOpt(entity.joinBlock) {
                        ParameterizedSql.generate { sb -> innerSqlGen.dbExprToSql(joinWhere, sb, frame, false) }
                    }
                    entityJoinWheres[entity.entityId] = joinSql
                }
            }
        }

        frame.blockOpt(expr.internals.block) {
            // Generate SELECT and WHERE SQL first, because traversing expressions creates
            // relationship JOINs lazily. We need all JOINs before generating the FROM clause.
            val nonOmitWhat = expr.what.filter { !it.flags.omit }
            val selectSqls = nonOmitWhat.map { field ->
                ParameterizedSql.generate { sb -> innerSqlGen.dbExprToSql(field.expr, sb, frame, false) }
            }
            val whereSql = expr.where?.let { w ->
                ParameterizedSql.generate { sb -> innerSqlGen.dbExprToSql(w, sb, frame, false) }
            }

            // SELECT
            if (selectSqls.isNotEmpty()) {
                b.append("SELECT ")
                b.append(selectSqls, ", ") { sql: ParameterizedSql -> b.append(sql) }
            } else {
                b.append("SELECT 0")
            }

            // FROM — entity tables with JOIN ON for entities that have join WHERE conditions.
            // Mirrors the logic from buildSelectQuery for proper correlated subquery generation.
            val hasJoinWheres = entityJoinWheres.isNotEmpty()
            val crossJoin = if (hasJoinWheres) " CROSS JOIN " else ", "
            b.append(" FROM ")
            var firstEntity = true
            for (entity in expr.from.entities) {
                val alias = innerSqlGen.getEntityAlias(entity.entityId)!!
                val def = interpreter.rrApp.allEntities[entity.entityDefIndex]
                val table = def.sqlMapping.table(sqlCtx)
                val joinWhereSql = entityJoinWheres[entity.entityId]
                val relJoins = innerSqlGen.getEntityRelJoins(alias)
                val actualJoinWhere =
                    if (joinWhereSql != null || !entity.isOuter) joinWhereSql else ParameterizedSql.TRUE

                if (!firstEntity) {
                    val sep = when {
                        actualJoinWhere == null -> crossJoin
                        entity.isOuter -> " LEFT OUTER JOIN "
                        else -> " JOIN "
                    }
                    b.append(sep)
                }

                // Enclose entity + its relationship JOINs in parentheses when both ON and JOINs are present
                val enclose = actualJoinWhere != null && relJoins.isNotEmpty()
                if (enclose) b.append("(")

                b.appendName(table)
                b.append(" ")
                b.append(alias)

                // Add relationship JOINs belonging to this entity
                for (join in relJoins) {
                    b.append(" JOIN ")
                    b.appendName(join.table)
                    b.append(" ")
                    b.append(join.alias)
                    b.append(" ON ")
                    b.appendColumn(join.baseAlias, join.baseColumn)
                    b.append(" = ")
                    b.appendColumn(join.alias, join.targetRowid)
                }

                if (enclose) b.append(")")

                if (!firstEntity && actualJoinWhere != null) {
                    b.append(" ON ")
                    b.append(actualJoinWhere)
                }

                firstEntity = false
            }

            // WHERE
            if (whereSql != null && !whereSql.isEmpty()) {
                b.append(" WHERE ")
                b.append(whereSql)
            }

            // GROUP BY
            val groupBySqls = expr.what.filter { it.flags.group }.map { field ->
                ParameterizedSql.generate { sb -> innerSqlGen.dbExprToSql(field.expr, sb, frame, false) }
            }
            if (groupBySqls.isNotEmpty()) {
                b.append(" GROUP BY ")
                b.append(groupBySqls, ", ") { sql: ParameterizedSql -> b.append(sql) }
            }

            // ORDER BY
            val orderBySqls = generateSubQueryOrderBy(expr, innerSqlGen, frame, extras)
            if (orderBySqls.isNotEmpty()) {
                b.append(" ORDER BY ")
                b.append(orderBySqls, ", ") { sql: ParameterizedSql -> b.append(sql) }
            }

            // LIMIT / OFFSET
            val extrasLimit = extras.limit
            if (extrasLimit != null) {
                b.append(" LIMIT ")
                b.append(extrasLimit)
            }
            val extrasOffset = extras.offset
            if (extrasOffset != null) {
                b.append(" OFFSET ")
                b.append(extrasOffset)
            }
        }
    }

    /** Generates ORDER BY SQL for an inline subquery. Only includes explicit sort fields
     *  and group-field ordering — NOT the default entity rowid ordering, since subqueries
     *  inside EXISTS/IN typically don't need it, and it would conflict with aggregation. */
    private fun generateSubQueryOrderBy(
        expr: RR_DbExpr.SubQuery,
        innerSqlGen: DbSqlGen,
        frame: Rt_CallFrame,
        @Suppress("UNUSED_PARAMETER") extras: Rt_AtExprExtras,
    ): List<ParameterizedSql> {
        val result = mutableListOf<ParameterizedSql>()

        // Explicit sort fields
        for (field in expr.what) {
            if (field.flags.sort != 0) {
                val sql = ParameterizedSql.generate { sb -> innerSqlGen.dbExprToSql(field.expr, sb, frame, false) }
                val desc = field.flags.sort < 0
                result.add(if (desc) ParameterizedSql("${sql.sql} DESC", sql.params) else sql)
            }
        }

        // When grouping, auto-add group fields to ORDER BY for deterministic results.
        val hasGroup = expr.what.any { it.flags.group }
        if (hasGroup) {
            for (field in expr.what) {
                if (field.flags.group && field.flags.sort == 0) {
                    val sql = ParameterizedSql.generate { sb -> innerSqlGen.dbExprToSql(field.expr, sb, frame, false) }
                    result.add(sql)
                }
            }
        }

        // For subqueries with LIMIT/OFFSET but no explicit sort, add default entity ordering
        // only when there's no aggregation (GROUP BY or aggregate functions).
        if (result.isEmpty() && !hasGroup && (extras.limit != null || extras.offset != null)) {
            for (entity in expr.from.entities) {
                val entityDef = interpreter.rrApp.allEntities[entity.entityDefIndex]
                val alias = innerSqlGen.getEntityAlias(entity.entityId)
                if (alias != null) {
                    val sql = ParameterizedSql.generate { sb ->
                        sb.appendColumn(alias, entityDef.sqlMapping.rowidColumn)
                    }
                    result.add(sql)
                }
            }
        }

        return result
    }

    /**
     * Generates SQL for a WHEN (CASE) expression with smart short-circuiting.
     * Evaluates R-level sub-expressions eagerly (in order),
     * caches their results, and short-circuits when conditions are constants.
     * This ensures side effects happen in the correct order and avoids double-evaluation.
     */
    private fun dbWhenToSql(expr: RR_DbExpr.When, b: SqlBuilder, frame: Rt_CallFrame) {
        val elseExpr = expr.elseExpr
        val keyExpr = expr.keyExpr
        if (keyExpr == null) {
            // General when (no key): conditions are boolean expressions.
            // Process conditions in order, evaluating R-level sub-expressions eagerly.
            val activeCases = mutableListOf<RR_DbWhenCase>()
            var matchedExpr: RR_DbExpr? = null
            for (case in expr.cases) {
                val liveConds = mutableListOf<RR_DbExpr>()
                var caseMatched = false
                for (cond in case.conds) {
                    // Pre-evaluate all Interpreted sub-expressions within the condition,
                    // replacing them with cached ConstantValue nodes to avoid re-evaluation.
                    val reduced = reduceDbExpr(cond, frame)
                    val constValue = tryEvaluateInterpretedBool(reduced, frame)
                    if (constValue == true) {
                        caseMatched = true
                        break
                    } else if (constValue == null) {
                        // Not fully constant — include the reduced form in SQL
                        liveConds.add(reduced)
                    }
                    // constValue == false: skip this condition
                }
                if (caseMatched) {
                    matchedExpr = case.expr
                    break
                }
                if (liveConds.isNotEmpty()) {
                    activeCases.add(RR_DbWhenCase(liveConds.toImmList(), case.expr))
                }
            }

            if (matchedExpr != null) {
                dbExprToSql(matchedExpr, b, frame, false)
                return
            }

            if (activeCases.isEmpty()) {
                if (elseExpr != null) {
                    dbExprToSql(elseExpr, b, frame, false)
                } else {
                    b.append("NULL")
                }
                return
            }

            // Generate CASE WHEN ... END with surviving cases (already reduced)
            b.append("CASE")
            for (case in activeCases) {
                b.append(" WHEN ")
                if (case.conds.size == 1) {
                    dbExprToSql(case.conds[0], b, frame, false)
                } else {
                    b.append(case.conds.toList(), " OR ") { cond: RR_DbExpr ->
                        dbExprToSql(cond, b, frame, true)
                    }
                }
                b.append(" THEN ")
                dbExprToSql(case.expr, b, frame, false)
            }
            if (elseExpr != null) {
                b.append(" ELSE ")
                dbExprToSql(elseExpr, b, frame, false)
            }
            b.append(" END")
        } else {
            // Keyed when: generate CASE key WHEN cond THEN expr ... END
            // Mirrors Db_WhenExpr.makeRedCasesKeyed: evaluates conditions eagerly.
            // Pre-evaluate all R-level sub-expressions in the key to avoid re-evaluation.
            reduceDbExpr(keyExpr, frame)
            val keyConst = tryEvaluateInterpreted(keyExpr, frame)

            if (keyConst != null) {
                // Key is R-level — evaluate each case's conditions against the key.
                // Conditions that are constant and match → take that branch.
                // Conditions that are constant and don't match → skip.
                // Conditions that are non-constant (DB) → include in CASE.
                val activeCases = mutableListOf<RR_DbWhenCase>()
                var matchedExpr: RR_DbExpr? = null

                for (case in expr.cases) {
                    var caseMatched = false
                    val liveConds = mutableListOf<RR_DbExpr>()
                    for (cond in case.conds) {
                        reduceDbExpr(cond, frame)
                        val condValue = tryEvaluateInterpreted(cond, frame)
                        if (condValue != null) {
                            if (condValue == keyConst) {
                                caseMatched = true
                                break
                            }
                            // Condition is constant but doesn't match — skip it
                        } else {
                            // Non-constant condition — include in CASE
                            liveConds.add(cond)
                        }
                    }
                    if (caseMatched) {
                        matchedExpr = case.expr
                        break
                    }
                    if (liveConds.isNotEmpty()) {
                        activeCases.add(RR_DbWhenCase(liveConds.toImmList(), case.expr))
                    }
                }

                if (matchedExpr != null) {
                    dbExprToSql(matchedExpr, b, frame, false)
                    return
                }

                if (activeCases.isEmpty()) {
                    // No cases with non-constant conditions — emit the else
                    if (elseExpr != null) {
                        dbExprToSql(elseExpr, b, frame, false)
                    } else {
                        b.append("NULL")
                    }
                    return
                }

                // Generate CASE with the cached key value as parameter, only surviving cases.
                // When keyConst is null, use IS NULL for conditions (since NULL = x is always NULL in SQL).
                val keyIsNull = keyConst == Rt_NullValue
                b.append("CASE")
                for (case in activeCases) {
                    b.append(" WHEN ")
                    if (keyIsNull) {
                        // NULL key: generate "cond1 IS NULL [OR cond2 IS NULL ...]"
                        if (case.conds.size == 1) {
                            dbExprToSql(case.conds[0], b, frame, true)
                            b.append(" IS NULL")
                        } else {
                            b.append(case.conds.toList(), " OR ") { cond: RR_DbExpr ->
                                b.append("(")
                                dbExprToSql(cond, b, frame, true)
                                b.append(" IS NULL)")
                            }
                        }
                    } else if (case.conds.size == 1) {
                        b.append(keyConst) // Use cached value, no re-evaluation
                        b.append(" = ")
                        dbExprToSql(case.conds[0], b, frame, true)
                    } else {
                        b.append(keyConst)
                        b.append(" IN (")
                        b.append(case.conds.toList(), ", ") { cond: RR_DbExpr ->
                            dbExprToSql(cond, b, frame, false)
                        }
                        b.append(")")
                    }
                    b.append(" THEN ")
                    dbExprToSql(case.expr, b, frame, false)
                }
                if (elseExpr != null) {
                    b.append(" ELSE ")
                    dbExprToSql(elseExpr, b, frame, false)
                }
                b.append(" END")
            } else {
                // Key is DB-level — reduce conditions to cache R-level parts, then generate full CASE.
                // Pre-reduce all conditions so R-level sub-expressions are evaluated once in order.
                for (case in expr.cases) {
                    for (cond in case.conds) {
                        reduceDbExpr(cond, frame)
                    }
                }
                // Also reduce the else expression and all THEN expressions
                for (case in expr.cases) {
                    reduceDbExpr(case.expr, frame)
                }
                if (elseExpr != null) {
                    reduceDbExpr(elseExpr, frame)
                }

                // Partition conditions into nullable (R-level null) and normal.
                // Nullable conditions need IS NULL; normal conditions use = / IN.
                // Mirrors Db_WhenExpr.makeRedCaseKeyed which uses IS NOT DISTINCT FROM for nullable cases.
                b.append("CASE")
                for (case in expr.cases) {
                    val nullConds = mutableListOf<RR_DbExpr>()
                    val normalConds = mutableListOf<RR_DbExpr>()
                    for (cond in case.conds) {
                        val condValue = tryEvaluateInterpreted(cond, frame)
                        if (condValue == Rt_NullValue) {
                            nullConds.add(cond)
                        } else {
                            normalConds.add(cond)
                        }
                    }

                    b.append(" WHEN ")
                    val parts = mutableListOf<() -> Unit>()

                    // Normal conditions: key = cond or key IN (cond1, cond2, ...)
                    if (normalConds.isNotEmpty()) {
                        parts.add {
                            if (normalConds.size == 1) {
                                dbExprToSql(keyExpr, b, frame, true)
                                b.append(" = ")
                                dbExprToSql(normalConds[0], b, frame, true)
                            } else {
                                dbExprToSql(keyExpr, b, frame, true)
                                b.append(" IN (")
                                b.append(normalConds.toList(), ", ") { cond: RR_DbExpr ->
                                    dbExprToSql(cond, b, frame, false)
                                }
                                b.append(")")
                            }
                        }
                    }
                    // Nullable conditions: key IS NULL
                    if (nullConds.isNotEmpty()) {
                        parts.add {
                            dbExprToSql(keyExpr, b, frame, true)
                            b.append(" IS NULL")
                        }
                    }

                    // Combine parts with OR
                    if (parts.size == 1) {
                        parts[0]()
                    } else {
                        for ((i, part) in parts.withIndex()) {
                            if (i > 0) b.append(" OR ")
                            b.append("(")
                            part()
                            b.append(")")
                        }
                    }

                    b.append(" THEN ")
                    dbExprToSql(case.expr, b, frame, false)
                }
                if (elseExpr != null) {
                    b.append(" ELSE ")
                    dbExprToSql(elseExpr, b, frame, false)
                }
                b.append(" END")
            }
        }
    }

    /**
     * Tries to evaluate an [RR_DbExpr] as an R-level boolean constant.
     * Handles simple [RR_DbExpr.Interpreted] cases as well as Binary expressions
     * where both sides are evaluable R-level expressions.
     * Returns true/false if evaluable, null if it contains DB-level parts.
     */
    private fun tryEvaluateInterpretedBool(expr: RR_DbExpr, frame: Rt_CallFrame): Boolean? {
        val value = tryEvaluateDbExprFully(expr, frame) ?: return null
        return try {
            value.asBoolean()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Tries to evaluate an [RR_DbExpr] as an R-level constant value.
     * Returns the value if the expression is fully evaluable at R-level, null otherwise.
     */
    private fun tryEvaluateInterpreted(expr: RR_DbExpr, frame: Rt_CallFrame): Rt_Value? {
        return tryEvaluateDbExprFully(expr, frame)
    }

    /**
     * Recursively tries to evaluate a [RR_DbExpr] entirely at R-level.
     *
     * IMPORTANT: Uses a two-pass approach to avoid side effects from partial evaluation.
     * First checks if the expression is fully R-level (no DB refs), then evaluates.
     */
    private fun tryEvaluateDbExprFully(expr: RR_DbExpr, frame: Rt_CallFrame): Rt_Value? {
        // Phase 1: check if the expression is fully R-level (no DB references)
        if (!isFullyRLevel(expr)) return null
        // Phase 2: safe to evaluate — all leaves are R-level
        return try {
            evaluateDbExprFully(expr, frame)
        } catch (_: Exception) {
            null
        }
    }

    /** Returns true if the expression tree has no DB-level nodes (Entity, Attr, Rel, Rowid, etc.). */
    private fun isFullyRLevel(expr: RR_DbExpr): Boolean = when (expr) {
        is RR_DbExpr.Interpreted -> true
        is RR_DbExpr.Binary -> isFullyRLevel(expr.left) && isFullyRLevel(expr.right)
        is RR_DbExpr.Unary -> isFullyRLevel(expr.expr)
        else -> false
    }

    /** Evaluates a DB expression known to be fully R-level. Uses the reduced cache. */
    private fun evaluateDbExprFully(expr: RR_DbExpr, frame: Rt_CallFrame): Rt_Value? = when (expr) {
        is RR_DbExpr.Interpreted -> reducedCache[expr] ?: interpreter.evaluateExpr(expr.expr, frame)
        is RR_DbExpr.Binary -> {
            val left = evaluateDbExprFully(expr.left, frame) ?: return null
            val right = evaluateDbExprFully(expr.right, frame) ?: return null
            evaluateSqlBinaryOp(expr.op, left, right)
        }

        is RR_DbExpr.Unary -> {
            val operand = evaluateDbExprFully(expr.expr, frame) ?: return null
            evaluateSqlUnaryOp(expr.op, operand)
        }

        else -> null
    }

    /**
     * Pre-evaluates all [RR_DbExpr.Interpreted] sub-expressions within a DB expression tree,
     * caching their results in [reducedCache]. When generating SQL later,
     * [dbExprToSql] checks the cache before re-evaluating Interpreted expressions.
     *
     * Returns the same expression tree (unchanged structurally), but all Interpreted nodes
     * within it have been evaluated and their results stored in the cache.
     */
    private fun reduceDbExpr(expr: RR_DbExpr, frame: Rt_CallFrame): RR_DbExpr {
        when (expr) {
            is RR_DbExpr.Interpreted -> {
                if (expr !in reducedCache) {
                    try {
                        val value = interpreter.evaluateExpr(expr.expr, frame)
                        reducedCache[expr] = value
                    } catch (_: Exception) {
                        // Leave uncached — will be re-evaluated during SQL gen
                    }
                }
            }

            is RR_DbExpr.Binary -> {
                reduceDbExpr(expr.left, frame)
                reduceDbExpr(expr.right, frame)
            }

            is RR_DbExpr.Unary -> {
                reduceDbExpr(expr.expr, frame)
            }

            else -> { /* DB-level nodes — nothing to reduce */
            }
        }
        return expr
    }

    private fun dbFnToSql(fn: RR_DbSysFn, args: List<RR_DbExpr>, b: SqlBuilder, frame: Rt_CallFrame) {
        when (fn) {
            is RR_DbSysFn.Simple -> {
                b.append(fn.sql)
                b.append("(")
                for (i in args.indices) {
                    if (i > 0) b.append(", ")
                    dbExprToSql(args[i], b, frame, false)
                }
                b.append(")")
            }

            is RR_DbSysFn.Template -> {
                for (fragment in fn.fragments) {
                    when (fragment) {
                        is RR_DbSysFnFragment.Text -> b.append(fragment.text)
                        is RR_DbSysFnFragment.Arg -> dbExprToSql(args[fragment.index], b, frame, false)
                    }
                }
            }
        }
    }

    fun buildSelectQuery(
        selectSqls: List<ParameterizedSql>,
        whereSql: ParameterizedSql?,
        groupBySqls: List<ParameterizedSql>,
        orderBySqls: List<ParameterizedSql>,
        extras: Rt_AtExprExtras,
        isMany: Boolean,
        entityJoinWheres: Map<Int, ParameterizedSql> = emptyMap(),
    ): ParameterizedSql {
        val b = SqlBuilder()

        // SELECT
        if (selectSqls.isNotEmpty()) {
            b.append("SELECT ")
            b.append(selectSqls, ", ") { sql: ParameterizedSql -> b.append(sql) }
        } else {
            b.append("SELECT 0")
        }

        // FROM - use JOIN with ON for entities that have join WHERE conditions (from join at-expressions).
        // Mirrors the logic from [RedDb_AtExprBase.AtExprSqlParts.translateFromItem] in the R_ model.
        val hasJoinWheres = entityJoinWheres.isNotEmpty()
        val crossJoin = if (hasJoinWheres) " CROSS JOIN " else ", "
        b.append(" FROM ")
        var first = true
        for (entity in entities) {
            val info = entityAliases[entity.entityId] ?: continue
            val joinWhereSql = entityJoinWheres[entity.entityId]
            val relJoins = getEntityRelJoins(info.alias)
            val actualJoinWhere = if (joinWhereSql != null || !entity.isOuter) joinWhereSql else ParameterizedSql.TRUE

            if (!first) {
                val sep = when {
                    actualJoinWhere == null -> crossJoin
                    entity.isOuter -> " LEFT OUTER JOIN "
                    else -> " JOIN "
                }
                b.append(sep)
            }

            // Enclose entity + its relationship JOINs in parentheses when both ON and JOINs are present
            val enclose = actualJoinWhere != null && relJoins.isNotEmpty()
            if (enclose) b.append("(")

            val table = info.entityDef.sqlMapping.table(sqlCtx)
            b.appendName(table)
            b.append(" ")
            b.append(info.alias)

            // Add relationship JOINs that belong to this entity
            for (join in relJoins) {
                b.append(" JOIN ")
                b.appendName(join.table)
                b.append(" ")
                b.append(join.alias)
                b.append(" ON ")
                b.appendColumn(join.baseAlias, join.baseColumn)
                b.append(" = ")
                b.appendColumn(join.alias, join.targetRowid)
            }

            if (enclose) b.append(")")

            if (!first && actualJoinWhere != null) {
                b.append(" ON ")
                b.append(actualJoinWhere)
            }

            first = false
        }

        // WHERE
        if (whereSql != null && !whereSql.isEmpty()) {
            b.append(" WHERE ")
            b.append(whereSql)
        }

        // GROUP BY
        if (groupBySqls.isNotEmpty()) {
            b.append(" GROUP BY ")
            b.append(groupBySqls, ", ") { sql: ParameterizedSql -> b.append(sql) }
        }

        // ORDER BY
        if (orderBySqls.isNotEmpty()) {
            b.append(" ORDER BY ")
            b.append(orderBySqls, ", ") { sql: ParameterizedSql -> b.append(sql) }
        }

        // LIMIT / OFFSET
        val extrasLimit2 = extras.limit
        if (extrasLimit2 != null) {
            b.append(" LIMIT ")
            b.append(extrasLimit2)
        }
        val extrasOffset2 = extras.offset
        if (extrasOffset2 != null) {
            b.append(" OFFSET ")
            b.append(extrasOffset2)
        }

        return b.build()
    }
}

@Suppress("UnusedReceiverParameter")
fun Rt_Interpreter.dbUnaryOpSql(code: String): String = when (code) {
    "-" -> "-"; "not" -> "NOT"
    else -> code
}

/**
 * Evaluates a SQL binary operation on two R-level values.
 * Used for short-circuiting: when both operands of a DB binary expression
 * are R-level constants, compute the result without generating SQL.
 */
@Suppress("UNCHECKED_CAST")
private fun evaluateSqlBinaryOp(sqlOp: String, left: Rt_Value, right: Rt_Value): Rt_Value? {
    return when (sqlOp) {
        "=" -> Rt_BooleanValue.get(left == right)
        "<>" -> Rt_BooleanValue.get(left != right)
        "<" -> {
            val cmp = (left as Comparable<Any>).compareTo(right as Comparable<Any>)
            Rt_BooleanValue.get(cmp < 0)
        }

        ">" -> {
            val cmp = (left as Comparable<Any>).compareTo(right as Comparable<Any>)
            Rt_BooleanValue.get(cmp > 0)
        }

        "<=" -> {
            val cmp = (left as Comparable<Any>).compareTo(right as Comparable<Any>)
            Rt_BooleanValue.get(cmp <= 0)
        }

        ">=" -> {
            val cmp = (left as Comparable<Any>).compareTo(right as Comparable<Any>)
            Rt_BooleanValue.get(cmp >= 0)
        }

        "AND" -> Rt_BooleanValue.get(left.asBoolean() && right.asBoolean())
        "OR" -> Rt_BooleanValue.get(left.asBoolean() || right.asBoolean())
        else -> null
    }
}

/**
 * Evaluates a SQL unary operation on an R-level value.
 */
private fun evaluateSqlUnaryOp(sqlOp: String, operand: Rt_Value): Rt_Value? {
    return when (sqlOp) {
        "NOT" -> Rt_BooleanValue.get(!operand.asBoolean())
        "-" -> Rt_IntValue.get(-operand.asInteger())
        else -> null
    }
}

/** Checks if an RR type represents a decimal (needs ROUND wrapping in SQL). */
private fun isDecimalType(type: RR_Type): Boolean {
    return type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.DECIMAL
}

/**
 * Resolves the SQL table name for an [RR_EntitySqlMapping] at runtime.
 * This replaces the virtual dispatch of [R_EntitySqlMapping.table].
 */
fun RR_EntitySqlMapping.table(sqlCtx: Rt_SqlContext): String {
    val chainMapping = when (kind) {
        RR_EntitySqlMappingKind.REGULAR -> sqlCtx.mainChainMapping()
        RR_EntitySqlMappingKind.EXTERNAL -> sqlCtx.chainMappingByIndex(externalChainIndex)
        RR_EntitySqlMappingKind.TRANSACTION, RR_EntitySqlMappingKind.BLOCK -> {
            if (externalChainIndex >= 0) {
                sqlCtx.chainMappingByIndex(externalChainIndex)
            } else {
                sqlCtx.mainChainMapping()
            }
        }
    }
    return when (kind) {
        RR_EntitySqlMappingKind.TRANSACTION -> chainMapping.transactionsTable
        RR_EntitySqlMappingKind.BLOCK -> chainMapping.blocksTable
        else -> chainMapping.fullName(mountName)
    }
}
