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
import net.postchain.rell.base.runtime.DbSqlGen.Companion.SAFE_ALIAS_REGEX
import net.postchain.rell.base.runtime.DbSqlGen.Companion.columnField
import net.postchain.rell.base.utils.toImmList
import org.jooq.*
import org.jooq.conf.*
import org.jooq.impl.DSL
import java.util.*

data class DbSqlAliasInfo(val alias: String, val entityDef: RR_EntityDefinition)
data class DbSqlJoinInfo(
    val table: String,
    val alias: String,
    val baseAlias: String,
    val baseColumn: String,
    val targetRowid: String,
)

/**
 * jOOQ render context shared across the runtime SQL emitter:
 * PostgreSQL dialect, always-quoted identifiers, uppercase keywords, no formatting,
 * indexed `?` placeholders. Generated SQL is rendered once per query and paired with the
 * parallel `Rt_Value` bind list tracked by [DbSqlGen].
 */
internal val JOOQ_CTX: DSLContext = DSL.using(
    SQLDialect.POSTGRES,
    Settings()
        // EXPLICIT_DEFAULT_QUOTED so DSL.name(...) is quoted (default for table/column identifiers)
        // but DSL.unquotedName(...) renders bare. We use unquotedName for aliases (A00, A01, …) so
        // PostgreSQL doesn't case-fold-mismatch them against quoted column-qualifying references.
        .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED)
        .withRenderKeywordCase(RenderKeywordCase.UPPER)
        // Suppress the optional `AS` keyword between table/field and alias — both forms are valid
        // PostgreSQL but the legacy emitter omitted it.
        .withRenderOptionalAsKeywordForTableAliases(RenderOptionalKeyword.OFF)
        .withRenderOptionalAsKeywordForFieldAliases(RenderOptionalKeyword.OFF)
        .withRenderFormatted(false)
        .withParamType(ParamType.INDEXED),
)

/** Renders a jOOQ [QueryPart] as a SQL string in the runtime's standard render context.
 *  Uses indexed `?` placeholders — pair with a parallel bind list. For DDL or any standalone
 *  SQL that must execute without separate binds, use [renderJooqInlined] instead. */
fun renderJooq(part: QueryPart): String = JOOQ_CTX.render(part)

/** Renders a jOOQ [QueryPart] with inline literals (no `?` placeholders). Use for DDL —
 *  PostgreSQL does not allow bind parameters in `CREATE TABLE`, `ALTER TABLE … CHECK (…)`,
 *  `CREATE INDEX`, etc. — and for standalone DML where binds are not tracked separately. */
fun renderJooqInlined(part: QueryPart): String = JOOQ_CTX.renderInlined(part)

/** Renders a quoted identifier (table or column name) for embedding in raw SQL strings. */
fun renderName(name: String): String = renderJooq(DSL.name(name))

/** Wraps a `Field<Boolean>` (or any value-form boolean expression) as a jOOQ [Condition] for use
 *  in `WHERE` / `ON` predicates. */
@Suppress("UNCHECKED_CAST")
fun boolFieldToCondition(field: Field<Any?>): Condition = DSL.condition(field as Field<Boolean>)

/**
 * Generates SQL for at-expression read paths via jOOQ DSL.
 *
 * Each [RR_DbExpr] subtype maps to a jOOQ [Field] (for value-producing expressions) or a
 * jOOQ [Condition] for join-condition contexts. Bind values are tracked in a parallel [bindList]
 * shared across nested sub-queries, with `?` placeholder fields added in DSL-tree order.
 *
 * The final SELECT query is rendered once into a [ParameterizedSql].
 */
class DbSqlGen private constructor(
    private val interpreter: Rt_Interpreter,
    private val sqlCtx: Rt_SqlContext,
    private val entities: List<RR_DbAtEntity>,
    private val parent: DbSqlGen?,
    private val aliasCounter: AliasCounter,
    private val bindList: MutableList<Rt_Value>,
) {
    private val entityAliases = mutableMapOf<Int, DbSqlAliasInfo>()
    private val relJoinAliases = mutableMapOf<Pair<String, String>, DbSqlAliasInfo>()

    /** Relationship JOINs grouped by the root entity alias they stem from. */
    private val entityRelJoins = mutableMapOf<String, MutableList<DbSqlJoinInfo>>()

    /** Maps each alias to its root entity alias (for tracking join ownership). */
    private val aliasToRootEntity = mutableMapOf<String, String>()

    /** Cache of pre-evaluated [RR_DbExpr.Interpreted] expressions. Identity-keyed to avoid
     *  sharing cache entries across different `Interpreted` instances with the same content. */
    private val reducedCache = IdentityHashMap<RR_DbExpr.Interpreted, Rt_Value>()

    constructor(
        interpreter: Rt_Interpreter,
        sqlCtx: Rt_SqlContext,
        entities: List<RR_DbAtEntity>,
    ): this(interpreter, sqlCtx, entities, null, AliasCounter(), mutableListOf())

    init {
        for (entity in entities) {
            val alias = nextAlias()
            val def = interpreter.rrApp.allEntities[entity.entityDefIndex]
            entityAliases[entity.entityId] = DbSqlAliasInfo(alias, def)
            aliasToRootEntity[alias] = alias
        }
    }

    private fun nextAlias() = "A%02d".format(aliasCounter.next())

    /** Creates a child context for correlated sub-queries; alias counter and bind list are shared. */
    private fun createSub(innerEntities: List<RR_DbAtEntity>): DbSqlGen =
        DbSqlGen(interpreter, sqlCtx, innerEntities, this, aliasCounter, bindList)

    /** Shared mutable alias counter so parent and child contexts produce unique aliases. */
    private class AliasCounter {
        private var value = 0
        fun next(): Int = value++
    }

    fun getEntityAlias(entityId: Int): String? = entityAliases[entityId]?.alias

    /** Adds a bind value and returns a `?` placeholder Field. Order of additions must match
     *  the order in which placeholders appear in the rendered SQL. */
    private fun bind(value: Rt_Value): Field<Any?> {
        bindList.add(value)
        return PARAM_PLACEHOLDER
    }

    /** Returns the bind list (read-only view of accumulated `Rt_Value`s, in render order). */
    fun binds(): List<Rt_Value> = bindList.toList()

    /** Returns the current bind-list size. Use with [truncateBinds] to roll back any binds added
     *  while constructing a [Field] that turns out to be discarded (e.g. ORDER BY de-duplication). */
    fun bindCount(): Int = bindList.size

    /** Truncates the bind list back to [size]. The caller is responsible for ensuring no other
     *  Field references those binds — typically used right after `dbExprToField` whose result is
     *  being thrown away. */
    fun truncateBinds(size: Int) {
        while (bindList.size > size) bindList.removeAt(bindList.size - 1)
    }

    /** A captured join-where condition: the rendered Field plus the bind values produced during
     *  its construction (which were temporarily appended to [bindList] then removed by
     *  [captureJoinWhere], so they can be re-inserted at the correct render-order position by
     *  [buildSelectQuery]). */
    class JoinWhereSpec internal constructor(internal val field: Field<Any?>, internal val binds: List<Rt_Value>)

    /** Builds a join-where Field but moves its binds out of the global [bindList] into a local
     *  [JoinWhereSpec]. Callers must build join-where Fields BEFORE the SELECT/WHERE Fields so
     *  any relationship JOINs they trigger are registered early; the binds are then re-inserted
     *  in render order (after SELECT, before WHERE) by [buildSelectQuery]. */
    fun captureJoinWhere(joinWhere: RR_DbExpr, frame: Rt_CallFrame): JoinWhereSpec {
        val before = bindList.size
        val field = dbExprToField(joinWhere, frame)
        val captured = bindList.subList(before, bindList.size).toList()
        truncateBinds(before)
        return JoinWhereSpec(field, captured)
    }

    /** Returns the relationship JOINs as jOOQ conditions, in declaration order. Used by write-path
     *  callers that emit relationship JOINs as a flat WHERE/USING list rather than structural JOINs. */
    fun joinConditionsAsConditions(): List<Condition> = getAllRelJoins().map { join ->
        DSL.condition(
            "{0} = {1}",
            columnField(join.baseAlias, join.baseColumn),
            columnField(join.alias, join.targetRowid),
        )
    }

    /** Returns all relationship JOINs (across all root entities). */
    fun getAllRelJoins(): List<DbSqlJoinInfo> = entityRelJoins.values.flatten()

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
                val rootAlias = ownerCtx.aliasToRootEntity[baseAlias] ?: baseAlias
                ownerCtx.aliasToRootEntity[alias] = rootAlias
                ownerCtx.entityRelJoins.getOrPut(rootAlias) { mutableListOf() }.add(joinInfo)
                alias to targetDef
            }
        }

        else -> error("Cannot resolve table for: ${expr::class.simpleName}")
    }

    private fun getEntityRelJoins(rootAlias: String): List<DbSqlJoinInfo> =
        entityRelJoins[rootAlias] ?: emptyList()

    /**
     * Translates an [RR_DbExpr] into a jOOQ [Field] that produces a single SQL value/expression.
     * Handles all subtypes; boolean predicates are wrapped via [DSL.field] so they can be embedded
     * as values when needed.
     *
     * [enclose] requests parentheses around the rendered output for use as a sub-expression of a
     * larger operator (so that operator precedence is preserved). Top-level callers pass `false`;
     * binary/unary operators recursively call with `true`.
     */
    fun dbExprToField(expr: RR_DbExpr, frame: Rt_CallFrame, enclose: Boolean = false): Field<Any?> = when (expr) {
        is RR_DbExpr.Interpreted -> {
            val value = reducedCache[expr] ?: interpreter.evaluateExpr(expr.expr, frame)
            if (value == Rt_NullValue) NULL_FIELD else bind(value)
        }

        is RR_DbExpr.Binary -> binaryToField(expr, frame, enclose)

        is RR_DbExpr.Unary -> {
            val operand = dbExprToField(expr.expr, frame, true)
            val sql = interpreter.dbUnaryOpSql(expr.op)
            val raw = if (enclose) "($sql {0})" else "$sql {0}"
            DSL.field(raw, Any::class.java, operand)
        }

        is RR_DbExpr.Entity -> {
            val info = resolveEntityAlias(expr.entityId)
            columnField(info.alias, info.entityDef.sqlMapping.rowidColumn)
        }

        is RR_DbExpr.Attr -> {
            val (alias, entityDef) = resolveTableExpr(expr.base)
            val sqlCol = entityDef.strAttributes[expr.attrName]?.sqlMapping ?: expr.attrName
            val col = columnField(alias, sqlCol)
            if (isDecimalType(expr.type)) wrapDecimal(col) else col
        }

        is RR_DbExpr.Rel -> {
            val (baseAlias, baseDef) = resolveTableExpr(expr.base)
            val sqlCol = baseDef.strAttributes[expr.attrName]?.sqlMapping ?: expr.attrName
            columnField(baseAlias, sqlCol)
        }

        is RR_DbExpr.Rowid -> {
            // Optimization: Rowid(Rel(base, attr, target)) reads the FK column from the base table
            // directly without forcing a JOIN, mirroring the legacy R_ model behavior.
            val relBase = expr.base
            if (relBase is RR_DbExpr.Rel) {
                val (baseAlias, baseDef) = resolveTableExpr(relBase.base)
                val sqlCol = baseDef.strAttributes[relBase.attrName]?.sqlMapping ?: relBase.attrName
                columnField(baseAlias, sqlCol)
            } else {
                val (alias, entityDef) = resolveTableExpr(expr.base)
                columnField(alias, entityDef.sqlMapping.rowidColumn)
            }
        }

        is RR_DbExpr.CollectionInterpreted -> {
            val value = interpreter.evaluateExpr(expr.expr, frame)
            val collection = value.asCollection()
            val items = collection.toList().map { v -> bind(v) }
            tupleField(items)
        }

        is RR_DbExpr.In -> inField(expr, frame)
        is RR_DbExpr.Elvis -> elvisField(expr, frame)

        is RR_DbExpr.Call -> {
            val inner = callField(expr.fn, expr.args, frame)
            if (isDecimalType(expr.type)) wrapDecimal(inner) else inner
        }

        is RR_DbExpr.Exists -> existsField(expr, frame)
        is RR_DbExpr.InCollection -> inCollectionField(expr, frame)
        is RR_DbExpr.When -> whenField(expr, frame)
        is RR_DbExpr.NestedAt -> dbExprToField(expr.inner, frame)
        is RR_DbExpr.SubQuery -> subQueryField(expr, frame)
    }

    // -------------------------------------------------------------------------------------------
    // Binary
    // -------------------------------------------------------------------------------------------

    private fun binaryToField(expr: RR_DbExpr.Binary, frame: Rt_CallFrame, enclose: Boolean): Field<Any?> {
        val sql = expr.op  // SQL operator string (e.g., "=", "AND", "||")

        // AND/OR short-circuiting on R-level constants (mirrors Db_BinaryOp_AndOr.toRedExpr).
        if (sql == "AND" || sql == "OR") {
            val short = sql == "OR"
            val lc = tryEvaluateInterpretedBool(expr.left, frame)
            if (lc != null) {
                return if (lc == short) boolField(short) else dbExprToField(expr.right, frame, enclose)
            }
            val rc = tryEvaluateInterpretedBool(expr.right, frame)
            if (rc != null) {
                return if (rc == short) boolField(short) else dbExprToField(expr.left, frame, enclose)
            }
        }

        val wrapDecimal = isDecimalType(expr.type)
        val opTpl = if (enclose) "({0} $sql {1})" else "{0} $sql {1}"

        // Nullable equality with one side known-NULL collapses to IS NULL / IS NOT NULL.
        if (expr.nullableEq) {
            val isEqual = sql == "IS NOT DISTINCT FROM"
            val left = expr.left
            val right = expr.right
            // Evaluate Interpreted operands through `reducedCache` so the value is reused by the
            // subsequent `dbExprToField(expr.left/right, ...)` fall-through, instead of re-running
            // the underlying R expression (which can have side effects).
            val leftConst = if (left is RR_DbExpr.Interpreted) {
                reducedCache.getOrPut(left) { interpreter.evaluateExpr(left.expr, frame) }
            } else null
            val rightConst = if (right is RR_DbExpr.Interpreted) {
                reducedCache.getOrPut(right) { interpreter.evaluateExpr(right.expr, frame) }
            } else null
            val nullCheckOp = if (isEqual) "IS NULL" else "IS NOT NULL"
            val nullTpl = if (enclose) "({0} $nullCheckOp)" else "{0} $nullCheckOp"
            val result = when {
                rightConst == Rt_NullValue ->
                    DSL.field(nullTpl, Any::class.java, dbExprToField(expr.left, frame, true))

                leftConst == Rt_NullValue ->
                    DSL.field(nullTpl, Any::class.java, dbExprToField(expr.right, frame, true))

                rightConst != null ->
                    DSL.field(opTpl, Any::class.java, dbExprToField(expr.left, frame, true), bind(rightConst))

                leftConst != null ->
                    DSL.field(opTpl, Any::class.java, dbExprToField(expr.right, frame, true), bind(leftConst))

                else -> DSL.field(
                    opTpl,
                    Any::class.java,
                    dbExprToField(expr.left, frame, true),
                    dbExprToField(expr.right, frame, true),
                )
            }
            return (if (wrapDecimal) wrapDecimal(result) else result)
        }

        val inner: Field<Any?> = if (sql.startsWith("FN:")) {
            val fnName = sql.removePrefix("FN:")
            DSL.field(
                "$fnName({0}, {1})",
                Any::class.java,
                dbExprToField(expr.left, frame),
                dbExprToField(expr.right, frame),
            )
        } else {
            DSL.field(
                opTpl,
                Any::class.java,
                dbExprToField(expr.left, frame, true),
                dbExprToField(expr.right, frame, true),
            )
        }

        return if (wrapDecimal) wrapDecimal(inner) else inner
    }

    // -------------------------------------------------------------------------------------------
    // IN
    // -------------------------------------------------------------------------------------------

    private fun inField(expr: RR_DbExpr.In, frame: Rt_CallFrame): Field<Any?> {
        val key = dbExprToField(expr.keyExpr, frame)
        val items = expr.exprs.map { dbExprToField(it, frame) }
        val itemsTuple = tupleField(items)
        val op = if (expr.not) "NOT IN" else "IN"
        return DSL.field("{0} $op {1}", Any::class.java, key, itemsTuple)
    }

    private fun inCollectionField(expr: RR_DbExpr.InCollection, frame: Rt_CallFrame): Field<Any?> {
        val rightValue = interpreter.evaluateExpr(expr.right, frame).asCollection()
        if (rightValue.isEmpty()) return boolField(expr.not)
        val left = dbExprToField(expr.left, frame)
        val items = rightValue.toList().map { v -> bind(v) }
        val tuple = tupleField(items)
        val op = if (expr.not) "NOT IN" else "IN"
        return DSL.field("{0} $op {1}", Any::class.java, left, tuple)
    }

    // -------------------------------------------------------------------------------------------
    // Elvis
    // -------------------------------------------------------------------------------------------

    private fun elvisField(expr: RR_DbExpr.Elvis, frame: Rt_CallFrame): Field<Any?> {
        // Pre-reduce so any R-level Interpreted leaves under [expr.left] are evaluated once and
        // cached; without this, the `dbExprToField(expr.left, …)` re-emit branch below would
        // re-evaluate the same Interpreted expression a second time.
        reduceDbExpr(expr.left, frame)
        val leftConst = tryEvaluateInterpreted(expr.left, frame)
        return when {
            leftConst == Rt_NullValue -> dbExprToField(expr.right, frame)
            leftConst != null -> dbExprToField(expr.left, frame)
            else -> DSL.field(
                "COALESCE({0}, {1})",
                Any::class.java,
                dbExprToField(expr.left, frame),
                dbExprToField(expr.right, frame),
            )
        }
    }

    // -------------------------------------------------------------------------------------------
    // Call (system functions)
    // -------------------------------------------------------------------------------------------

    private fun callField(fn: RR_DbSysFn, args: List<RR_DbExpr>, frame: Rt_CallFrame): Field<Any?> = when (fn) {
        is RR_DbSysFn.Simple -> {
            val argFields = args.map { dbExprToField(it, frame) }
            val placeholders = argFields.indices.joinToString(", ") { "{$it}" }
            DSL.field("${fn.sql}($placeholders)", Any::class.java, *argFields.toTypedArray())
        }

        is RR_DbSysFn.Template -> {
            // Template fragments: text literal and arg placeholder. Build the format string
            // with jOOQ's `{N}` placeholders and the argument array.
            val argFields = mutableListOf<Field<Any?>>()
            val tpl = buildString {
                for (frag in fn.fragments) when (frag) {
                    is RR_DbSysFnFragment.Text -> append(frag.text)
                    is RR_DbSysFnFragment.Arg -> {
                        append("{").append(argFields.size).append("}")
                        argFields.add(dbExprToField(args[frag.index], frame))
                    }
                }
            }
            DSL.field(tpl, Any::class.java, *argFields.toTypedArray())
        }
    }

    // -------------------------------------------------------------------------------------------
    // EXISTS
    // -------------------------------------------------------------------------------------------

    private fun existsField(expr: RR_DbExpr.Exists, frame: Rt_CallFrame): Field<Any?> {
        return when (val sub = expr.subExpr) {
            is RR_DbExpr.SubQuery -> {
                val subSql = subQueryParameterizedSql(sub, frame)
                val subField = wrapAsRawField(subSql)
                val op = if (expr.not) "NOT EXISTS" else "EXISTS"
                (DSL.field("$op {0}", Any::class.java, subField))
            }

            is RR_DbExpr.Interpreted -> {
                // Non-subquery (e.g. collection at). Evaluate at R-level, emit literal TRUE/FALSE.
                val value = interpreter.evaluateExpr(sub.expr, frame)
                val exists = if (value === Rt_NullValue) false else value.asCollection().isNotEmpty()
                boolField(if (expr.not) !exists else exists)
            }

            else -> {
                val subField = dbExprToField(sub, frame)
                val op = if (expr.not) "NOT EXISTS" else "EXISTS"
                DSL.field("$op({0})", Any::class.java, subField)
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // WHEN
    // -------------------------------------------------------------------------------------------

    private fun whenField(expr: RR_DbExpr.When, frame: Rt_CallFrame): Field<Any?> {
        val keyExpr = expr.keyExpr ?: return unkeyedWhen(expr, frame)

        // Pre-reduce all R-level sub-expressions in the key.
        reduceDbExpr(keyExpr, frame)
        val keyConst = tryEvaluateInterpreted(keyExpr, frame)
        return if (keyConst != null) keyedWhenWithRConst(expr, frame, keyConst)
        else keyedWhenWithDbKey(expr, frame, keyExpr)
    }

    /** Builds an unkeyed CASE WHEN (each branch's conds is a boolean expression list). */
    private fun unkeyedWhen(expr: RR_DbExpr.When, frame: Rt_CallFrame): Field<Any?> {
        val elseExpr = expr.elseExpr
        val activeCases = mutableListOf<RR_DbWhenCase>()
        var matchedExpr: RR_DbExpr? = null
        for (case in expr.cases) {
            val liveConds = mutableListOf<RR_DbExpr>()
            var caseMatched = false
            for (cond in case.conds) {
                val reduced = reduceDbExpr(cond, frame)
                val constValue = tryEvaluateInterpretedBool(reduced, frame)
                if (constValue == true) {
                    caseMatched = true
                    break
                } else if (constValue == null) {
                    liveConds.add(reduced)
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

        if (matchedExpr != null) return dbExprToField(matchedExpr, frame)
        if (activeCases.isEmpty()) {
            return if (elseExpr != null) dbExprToField(elseExpr, frame) else NULL_FIELD
        }

        var step: CaseConditionStep<Any?>? = null
        for (case in activeCases) {
            val cond: Condition = case.conds
                .map { conditionFromBoolField(dbExprToField(it, frame)) }
                .reduce { a, b -> a.or(b) }
            val thenField = dbExprToField(case.expr, frame)
            step = step?.`when`(cond, thenField) ?: caseStart(cond, thenField)
        }
        return if (elseExpr != null) step!!.otherwise(dbExprToField(elseExpr, frame)) else step!!
    }

    /** Builds a keyed CASE where the key is fully R-evaluable; cases narrow against the cached value. */
    private fun keyedWhenWithRConst(expr: RR_DbExpr.When, frame: Rt_CallFrame, keyConst: Rt_Value): Field<Any?> {
        val elseExpr = expr.elseExpr
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
                } else {
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

        if (matchedExpr != null) return dbExprToField(matchedExpr, frame)
        if (activeCases.isEmpty()) {
            return if (elseExpr != null) dbExprToField(elseExpr, frame) else NULL_FIELD
        }

        val keyIsNull = keyConst == Rt_NullValue
        var step: CaseConditionStep<Any?>? = null
        for (case in activeCases) {
            val cond: Condition = if (keyIsNull) {
                // NULL key: each cond becomes `cond IS NULL`, OR-combined.
                case.conds
                    .map { DSL.condition("{0} IS NULL", dbExprToField(it, frame)) }
                    .reduce { a, b -> a.or(b) }
            } else {
                val keyField = bind(keyConst)
                if (case.conds.size == 1) {
                    DSL.condition("{0} = {1}", keyField, dbExprToField(case.conds[0], frame))
                } else {
                    val items = case.conds.map { dbExprToField(it, frame) }
                    val placeholders = items.indices.joinToString(", ") { "{${it + 1}}" }
                    DSL.condition(
                        "{0} IN ($placeholders)",
                        keyField,
                        *items.toTypedArray(),
                    )
                }
            }
            val thenField = dbExprToField(case.expr, frame)
            step = step?.`when`(cond, thenField) ?: caseStart(cond, thenField)
        }
        return if (elseExpr != null) step!!.otherwise(dbExprToField(elseExpr, frame)) else step!!
    }

    /** Builds a keyed CASE where the key is a DB-level expression; partitions conds into nullable
     *  and normal, mirroring the legacy emitter's `IS NULL` vs. `=`/`IN` split. */
    private fun keyedWhenWithDbKey(
        expr: RR_DbExpr.When,
        frame: Rt_CallFrame,
        keyExpr: RR_DbExpr,
    ): Field<Any?> {
        val elseExpr = expr.elseExpr
        // Pre-reduce key, conditions, THEN, and ELSE so any Interpreted leaves are evaluated
        // exactly once per logical sub-expression. The key expression is emitted once per case
        // (and twice per case when both null/normal cond groups are present), so without this
        // its Interpreted leaves would re-run for every emit.
        reduceDbExpr(keyExpr, frame)
        for (case in expr.cases) {
            for (cond in case.conds) reduceDbExpr(cond, frame)
        }
        for (case in expr.cases) reduceDbExpr(case.expr, frame)
        if (elseExpr != null) reduceDbExpr(elseExpr, frame)

        var step: CaseConditionStep<Any?>? = null
        for (case in expr.cases) {
            val nullConds = mutableListOf<RR_DbExpr>()
            val normalConds = mutableListOf<RR_DbExpr>()
            for (cond in case.conds) {
                val condValue = tryEvaluateInterpreted(cond, frame)
                if (condValue == Rt_NullValue) nullConds.add(cond) else normalConds.add(cond)
            }

            val parts = mutableListOf<Condition>()
            if (normalConds.isNotEmpty()) {
                val keyField = dbExprToField(keyExpr, frame)
                parts.add(
                    if (normalConds.size == 1) {
                        DSL.condition("{0} = {1}", keyField, dbExprToField(normalConds[0], frame))
                    } else {
                        val items = normalConds.map { dbExprToField(it, frame) }
                        val placeholders = items.indices.joinToString(", ") { "{${it + 1}}" }
                        DSL.condition(
                            "{0} IN ($placeholders)",
                            keyField,
                            *items.toTypedArray(),
                        )
                    },
                )
            }
            if (nullConds.isNotEmpty()) {
                parts.add(DSL.condition("{0} IS NULL", dbExprToField(keyExpr, frame)))
            }
            val cond: Condition = if (parts.size == 1) parts[0] else parts.reduce { a, b -> a.or(b) }
            val thenField = dbExprToField(case.expr, frame)
            step = step?.`when`(cond, thenField) ?: caseStart(cond, thenField)
        }
        return if (elseExpr != null) step!!.otherwise(dbExprToField(elseExpr, frame)) else step!!
    }

    /** Wraps a `Field<Boolean>` (or any field that can act as one) as a [Condition]. */
    private fun conditionFromBoolField(field: Field<Any?>): Condition = boolFieldToCondition(field)

    /** Builds an ORDER BY sort entry without rendering an explicit `ASC` keyword (matches the
     *  legacy emitter's byte format). [Field.sortDefault] renders as just `<field>`; for descending
     *  sorts, the field is wrapped as a plain SQL fragment with a trailing ` DESC`. */
    private fun sortField(field: Field<Any?>, desc: Boolean): SortField<*> =
        if (desc) DSL.field(renderJooq(field) + " DESC", Any::class.java).sortDefault()
        else field.sortDefault()

    /** Starts a CASE chain — `DSL.case_()` followed by the first `when_(cond, thenField)`. */
    private fun caseStart(cond: Condition, thenField: Field<Any?>): CaseConditionStep<Any?> =
        DSL.case_().`when`(cond, thenField)


    // -------------------------------------------------------------------------------------------
    // R-level evaluation helpers (mirrors legacy DbSqlGen)
    // -------------------------------------------------------------------------------------------

    private fun tryEvaluateInterpretedBool(expr: RR_DbExpr, frame: Rt_CallFrame): Boolean? {
        val value = tryEvaluateDbExprFully(expr, frame) ?: return null
        return try {
            value.asBoolean()
        } catch (_: Exception) {
            null
        }
    }

    private fun tryEvaluateInterpreted(expr: RR_DbExpr, frame: Rt_CallFrame): Rt_Value? =
        tryEvaluateDbExprFully(expr, frame)

    private fun tryEvaluateDbExprFully(expr: RR_DbExpr, frame: Rt_CallFrame): Rt_Value? {
        if (!isFullyRLevel(expr)) return null
        return try {
            evaluateDbExprFully(expr, frame)
        } catch (_: Exception) {
            null
        }
    }

    private fun isFullyRLevel(expr: RR_DbExpr): Boolean = when (expr) {
        is RR_DbExpr.Interpreted -> true
        is RR_DbExpr.Binary -> isFullyRLevel(expr.left) && isFullyRLevel(expr.right)
        is RR_DbExpr.Unary -> isFullyRLevel(expr.expr)
        else -> false
    }

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

    private fun reduceDbExpr(expr: RR_DbExpr, frame: Rt_CallFrame): RR_DbExpr {
        when (expr) {
            is RR_DbExpr.Interpreted -> {
                if (expr !in reducedCache) {
                    try {
                        reducedCache[expr] = interpreter.evaluateExpr(expr.expr, frame)
                    } catch (_: Exception) {
                        // Leave uncached
                    }
                }
            }

            is RR_DbExpr.Binary -> {
                reduceDbExpr(expr.left, frame)
                reduceDbExpr(expr.right, frame)
            }

            is RR_DbExpr.Unary -> reduceDbExpr(expr.expr, frame)
            else -> {}
        }
        return expr
    }

    // -------------------------------------------------------------------------------------------
    // SubQuery
    // -------------------------------------------------------------------------------------------

    private fun subQueryField(expr: RR_DbExpr.SubQuery, frame: Rt_CallFrame): Field<Any?> {
        val sub = subQueryParameterizedSql(expr, frame)
        return wrapAsRawField(sub)
    }

    /** Renders a sub-query into its raw SQL string + adds its bind values to the shared list.
     *  The returned [ParameterizedSql] has its `params` empty because binds are tracked globally. */
    private fun subQueryParameterizedSql(expr: RR_DbExpr.SubQuery, frame: Rt_CallFrame): String {
        val innerGen = createSub(expr.from.entities)
        val extras = expr.extras?.let { interpreter.evaluateAtExtras(it, frame) } ?: Rt_AtExprExtras.NULL

        // Capture per-entity join-where conditions outside internals.block (they need their own
        // from.block / joinBlock scope), buffering their binds locally so they can be spliced
        // into the parent bindList in render order (after SELECT, before WHERE).
        val joinWhereSpecs = mutableMapOf<Int, JoinWhereSpec>()
        frame.blockOpt(expr.from.block) {
            for (entity in expr.from.entities) {
                val joinWhere = entity.joinWhere
                if (joinWhere != null) {
                    val spec = frame.blockOpt(entity.joinBlock) {
                        innerGen.captureJoinWhere(joinWhere, frame)
                    }
                    joinWhereSpecs[entity.entityId] = spec
                }
            }
        }

        return frame.blockOpt(expr.internals.block) {
            val nonOmitWhat = expr.what.filter { !it.flags.omit }
            val selectFields = nonOmitWhat.map { f -> innerGen.dbExprToField(f.expr, frame) }
            val joinWhereBindPosition = innerGen.bindCount()

            val whereField = expr.where?.let { w -> innerGen.dbExprToField(w, frame) }
            val groupByFields = expr.what.filter { it.flags.group }.map { f -> innerGen.dbExprToField(f.expr, frame) }
            val orderByEntries = innerGen.subQueryOrderBy(expr, frame, extras)

            // Splice join-where binds into innerGen's bindList at the post-SELECT position before
            // rendering, so the parallel `?` order in the rendered SQL matches.
            if (joinWhereSpecs.isNotEmpty()) {
                val joinBinds = joinWhereSpecs.values.flatMap { it.binds }
                innerGen.spliceBindsAt(joinWhereBindPosition, joinBinds)
            }
            val entityJoinWheres: Map<Int, Field<Any?>> = joinWhereSpecs.mapValues { it.value.field }

            innerGen.renderSubQuerySql(
                selectFields,
                whereField,
                groupByFields,
                orderByEntries,
                extras,
                entityJoinWheres,
                expr.from.entities,
            )
        }
    }

    /** Inserts [binds] into [bindList] at [position]. Used by sub-query rendering to weave
     *  pre-captured join-where binds into the right render-order slot. */
    private fun spliceBindsAt(position: Int, binds: List<Rt_Value>) {
        bindList.addAll(position, binds)
    }

    private fun renderSubQuerySql(
        selectFields: List<Field<Any?>>,
        whereField: Field<Any?>?,
        groupByFields: List<Field<Any?>>,
        orderByEntries: List<SortField<*>>,
        extras: Rt_AtExprExtras,
        entityJoinWheres: Map<Int, Field<Any?>>,
        innerEntities: List<RR_DbAtEntity>,
    ): String {
        val q = buildSelectQueryAst(
            selectFields,
            whereField,
            groupByFields,
            orderByEntries,
            entityJoinWheres,
            innerEntities,
        )
        return "(${renderJooq(q)}${renderLimitOffsetSuffix(extras)})"
    }

    /**
     * Builds a jOOQ [SelectQuery] from at-expression parts.
     * The query renders identifiers, operators, structure, joins, and ORDER BY via the DSL;
     * aliases stay unquoted via [DSL.unquotedName]
     * (paired with [RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED])
     * to side-step PostgreSQL's case-folding mismatch between unquoted alias declarations and quoted column-qualifying
     * references.
     */
    private fun buildSelectQueryAst(
        selectFields: List<Field<Any?>>,
        whereField: Field<Any?>?,
        groupByFields: List<Field<Any?>>,
        orderByEntries: List<SortField<*>>,
        entityJoinWheres: Map<Int, Field<Any?>>,
        innerEntities: List<RR_DbAtEntity>,
    ): SelectQuery<Record> {
        val q: SelectQuery<Record> = JOOQ_CTX.selectQuery()
        if (selectFields.isNotEmpty()) {
            for (f in selectFields) q.addSelect(f)
        } else {
            q.addSelect(DSL.field("0", Any::class.java))
        }

        addFromTo(q, innerEntities, entityJoinWheres)

        if (whereField != null) q.addConditions(conditionFromBoolField(whereField))
        for (f in groupByFields) q.addGroupBy(f)
        for (sf in orderByEntries) q.addOrderBy(sf)
        return q
    }

    /** Renders ` LIMIT ?` / ` OFFSET ?` for embedding after a rendered SELECT. Adds the
     *  corresponding bind values to [bindList] in the order they appear in the rendered text. */
    private fun renderLimitOffsetSuffix(extras: Rt_AtExprExtras): String {
        val limit = extras.limit
        val offset = extras.offset
        val limitPart = if (limit != null) {
            bindList.add(Rt_IntValue.get(limit))
            " LIMIT ?"
        } else ""
        val offsetPart = if (offset != null) {
            bindList.add(Rt_IntValue.get(offset))
            " OFFSET ?"
        } else ""
        return limitPart + offsetPart
    }

    /** Adds entity tables, relationship JOINs, and join-where ON conditions to a [SelectQuery]
     *  via jOOQ's structural API (`addFrom`, `addJoin`). When any entity has a join-where (i.e.
     *  the at-expression has `entity @{ x: ... }` style join conditions), siblings are CROSS-JOINed
     *  and the entity-specific join conditions are attached to the cross-join. */
    private fun addFromTo(
        q: SelectQuery<Record>,
        innerEntities: List<RR_DbAtEntity>,
        entityJoinWheres: Map<Int, Field<Any?>>,
    ) {
        val hasJoinWheres = entityJoinWheres.isNotEmpty()
        var firstEntity = true
        for (entity in innerEntities) {
            val alias = getEntityAlias(entity.entityId)!!
            val def = interpreter.rrApp.allEntities[entity.entityDefIndex]
            val tableName = def.sqlMapping.table(sqlCtx)
            val tableExpr = aliasedTable(tableName, alias)
            val joinWhereField = entityJoinWheres[entity.entityId]
            val relJoins = getEntityRelJoins(alias)
            val actualJoinWhere: Field<Any?>? = when {
                joinWhereField != null -> joinWhereField
                !entity.isOuter -> null
                else -> TRUE_FIELD
            }

            // Compose the entity table with its relationship JOINs (if any) using jOOQ's typed
            // join chain. Each rel-join becomes a `JOIN <table> <alias> ON <col> = <col>` step
            // attached to the running [Table] result.
            val composed: Table<*> = relJoins.fold(tableExpr) { acc, join ->
                val joined = aliasedTable(join.table, join.alias)
                val left = columnField(join.baseAlias, join.baseColumn)
                val right = columnField(join.alias, join.targetRowid)
                acc.join(joined).on(left.eq(right))
            }

            if (firstEntity) {
                q.addFrom(composed)
            } else if (actualJoinWhere == null) {
                if (hasJoinWheres) q.addJoin(composed, JoinType.CROSS_JOIN) else q.addFrom(composed)
            } else {
                val joinType = if (entity.isOuter) JoinType.LEFT_OUTER_JOIN else JoinType.JOIN
                q.addJoin(composed, joinType, conditionFromBoolField(actualJoinWhere))
            }
            firstEntity = false
        }
    }

    /** Builds a `<table> <alias>` reference where the table identifier is quoted and the alias is
     *  unquoted — matches the legacy byte format and avoids the PostgreSQL case-folding mismatch
     *  between unquoted alias declarations and quoted column-qualifying references. */
    private fun aliasedTable(tableName: String, alias: String): Table<Record> =
        DSL.table(DSL.name(tableName)).`as`(DSL.unquotedName(alias))

    /** Builds order-by sort fields with rollback-on-duplicate semantics; mirrors the R_ model's
     *  `distinctBy { it.baseSql }` where duplicates are detected by both the rendered SQL AND
     *  any `?` bind values added during construction. */
    private fun subQueryOrderBy(
        expr: RR_DbExpr.SubQuery,
        frame: Rt_CallFrame,
        extras: Rt_AtExprExtras,
    ): List<SortField<*>> {
        val seen = mutableSetOf<Pair<String, List<Rt_Value>>>()
        val pairs = mutableListOf<Pair<Field<Any?>, Boolean>>()

        fun tryAdd(field: Field<Any?>, desc: Boolean, savedBinds: Int) {
            val rendered = renderJooq(field)
            val newBinds = bindList.subList(savedBinds, bindList.size).toList()
            if (seen.add(rendered to newBinds)) {
                pairs.add(field to desc)
            } else {
                truncateBinds(savedBinds)
            }
        }

        for (field in expr.what) {
            if (field.flags.sort != 0) {
                val saved = bindCount()
                val f = dbExprToField(field.expr, frame)
                tryAdd(f, field.flags.sort < 0, saved)
            }
        }
        val hasGroup = expr.what.any { it.flags.group }
        val hasAggregate = expr.what.any { it.flags.aggregate }
        if (hasGroup) {
            for (field in expr.what) {
                if (field.flags.group && field.flags.sort == 0) {
                    val saved = bindCount()
                    val f = dbExprToField(field.expr, frame)
                    tryAdd(f, false, saved)
                }
            }
        }

        // Default rowid ordering only makes sense without aggregation: PostgreSQL rejects
        // `ORDER BY rowid` against an aggregated SELECT that doesn't expose the column.
        if (pairs.isEmpty() && !hasGroup && !hasAggregate
            && (extras.limit != null || extras.offset != null)
        ) {
            for (entity in expr.from.entities) {
                val entityDef = interpreter.rrApp.allEntities[entity.entityDefIndex]
                val alias = getEntityAlias(entity.entityId)
                if (alias != null) {
                    val saved = bindCount()
                    val f = columnField(alias, entityDef.sqlMapping.rowidColumn)
                    tryAdd(f, false, saved)
                }
            }
        }
        return pairs.map { (f, desc) -> sortField(f, desc) }
    }

    // -------------------------------------------------------------------------------------------
    // Top-level SELECT query
    // -------------------------------------------------------------------------------------------

    /**
     * Builds the top-level SELECT query for an at-expression and renders it to [ParameterizedSql].
     * Bind values are collected into the shared [bindList] during DSL construction in tree-traversal
     * order, matching the order of `?` placeholders in the rendered SQL.
     */
    fun buildSelectQuery(
        selectFields: List<Field<Any?>>,
        whereField: Field<Any?>?,
        groupByFields: List<Field<Any?>>,
        orderByEntries: List<SortField<*>>,
        extras: Rt_AtExprExtras,
        entityJoinWhereSpecs: Map<Int, JoinWhereSpec> = emptyMap(),
        joinWhereBindPosition: Int = bindList.size,
    ): ParameterizedSql {
        // Splice each join-where spec's binds into the global bindList at the right render-order
        // position (after SELECT, before WHERE) before calling buildSelectQueryAst — the spec
        // Fields contain `?` placeholders that line up with these binds when rendered as part of
        // the FROM clause.
        if (entityJoinWhereSpecs.isNotEmpty()) {
            val joinBinds = entityJoinWhereSpecs.values.flatMap { it.binds }
            bindList.addAll(joinWhereBindPosition, joinBinds)
        }
        val entityJoinWheres: Map<Int, Field<Any?>> = entityJoinWhereSpecs.mapValues { it.value.field }
        val q = buildSelectQueryAst(
            selectFields,
            whereField,
            groupByFields,
            orderByEntries,
            entityJoinWheres,
            entities,
        )
        val sql = renderJooq(q) + renderLimitOffsetSuffix(extras)
        return ParameterizedSql(sql, bindList.toImmList())
    }

    // -------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------

    private fun wrapDecimal(field: Field<*>): Field<Any?> =
        DSL.field("ROUND({0}, ${Lib_DecimalMath.DECIMAL_FRAC_DIGITS})", Any::class.java, field)

    /** Wraps a pre-rendered SQL string fragment as a raw jOOQ [Field]. The string must already include
     *  any `?` placeholders that line up with bind values added to [bindList] in the same call sequence. */
    private fun wrapAsRawField(rawSql: String): Field<Any?> = DSL.field(rawSql, Any::class.java)

    companion object {
        private val NULL_FIELD: Field<Any?> = DSL.field("NULL", Any::class.java)
        private val TRUE_FIELD: Field<Any?> = DSL.field("TRUE", Any::class.java)
        private val FALSE_FIELD: Field<Any?> = DSL.field("FALSE", Any::class.java)
        private val PARAM_PLACEHOLDER: Field<Any?> = DSL.field("?", Any::class.java)

        private fun boolField(b: Boolean): Field<Any?> = if (b) TRUE_FIELD else FALSE_FIELD

        /** Builds an SQL parenthesised tuple `(f1, f2, ...)` from a list of fields. */
        private fun tupleField(items: List<Field<Any?>>): Field<Any?> {
            if (items.isEmpty()) {
                return DSL.field("()", Any::class.java)
            }
            val placeholders = items.indices.joinToString(",") { "{$it}" }
            return DSL.field("($placeholders)", Any::class.java, *items.toTypedArray())
        }

        /** Pattern matching the alias strings produced by [DbSqlGen.nextAlias] (`A%02d`). The check
         *  in [columnField] is the one place we splice an identifier into raw SQL via string concat
         *  — making the implicit "alias is compile-time-safe" contract explicit. */
        private val SAFE_ALIAS_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*$")

        /** Builds a column reference field `<alias>."<column>"`. The alias is interpolated raw
         *  (must satisfy [SAFE_ALIAS_REGEX]) and the column is rendered via [DSL.name] so PG
         *  identifier escaping applies. The alias is intentionally emitted as bare text so it
         *  matches the unquoted alias declaration in the FROM clause (`FROM "c0.user" A00`); a
         *  quoted reference (`"A00"."col"`) would mis-match PG's case folding of unquoted aliases. */
        internal fun columnField(alias: String, column: String): Field<Any?> {
            require(SAFE_ALIAS_REGEX.matches(alias)) { "Unsafe alias for raw SQL splice: '$alias'" }
            val raw = "$alias.${renderName(column)}"
            return DSL.field(raw, Any::class.java)
        }

        /** Public-facing column field builder for callers outside [DbSqlGen]. */
        fun columnFieldExt(alias: String, column: String): Field<Any?> = columnField(alias, column)
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
private fun evaluateSqlBinaryOp(sqlOp: String, left: Rt_Value, right: Rt_Value): Rt_Value? = when (sqlOp) {
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

private fun evaluateSqlUnaryOp(sqlOp: String, operand: Rt_Value): Rt_Value? = when (sqlOp) {
    "NOT" -> Rt_BooleanValue.get(!operand.asBoolean())
    "-" -> Rt_IntValue.get(-operand.asInteger())
    else -> null
}

private fun isDecimalType(type: RR_Type): Boolean = (type as? RR_Type.Primitive)?.kind == RR_PrimitiveKind.DECIMAL

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
