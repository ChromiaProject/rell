/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import com.google.common.collect.Lists
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.lib.type.R_BooleanType
import net.postchain.rell.base.model.R_FrameBlock
import net.postchain.rell.base.model.R_Struct
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList

class Db_AtFromItem(
    val atEntity: R_DbAtEntity,
    private val isOuter: Boolean,
    private val where: Db_Expr?,
    private val rBlock: R_FrameBlock?,
) {
    fun toRed(frame: Rt_CallFrame): RedDb_AtFromItem {
        val redWhere = frame.blockOpt(rBlock) {
            where?.toRedExpr(frame)
        }
        return RedDb_AtFromItem(atEntity, redWhere, isOuter)
    }
}

class RedDb_AtFromItem(val atEntity: R_DbAtEntity, val where: RedDb_Expr?, val isOuter: Boolean)

class RedDb_AtExprFrom(
    private val items: List<RedDb_AtFromItem>,
    private val what: List<Db_AtWhatField>,
    private val where: Db_Expr?,
    private val isMany: Boolean,
    private val selWhat: List<Db_AtWhatValue>,
    private val resultTypes: List<R_Type>,
) {
    fun toRedBase(frame: Rt_CallFrame): RedDb_AtExprBase {
        val redWhere = makeFullWhere(frame)

        val redWhat = what.flatMap { whatField ->
            val redExprs = whatField.value.toRedExprs(frame)
            redExprs.map { RedDb_AtWhatField(it, whatField.flags) }
        }

        return RedDb_AtExprBase(items, redWhere, redWhat, isMany, selWhat, resultTypes)
    }

    private fun makeFullWhere(frame: Rt_CallFrame): RedDb_Expr? {
        val exprs = mutableListOf<Db_Expr?>()
        exprs.add(where)

        for (item in items) {
            val atEntity = item.atEntity
            val expr = atEntity.rEntity.sqlMapping.extraWhereExpr(atEntity)
            exprs.add(expr)
        }

        val validExprs = exprs.filterNotNull()
        val expr = if (validExprs.isEmpty()) {
            null
        } else {
            C_ExprUtils.makeDbBinaryExprChain(R_BooleanType, R_BinaryOp_And, Db_BinaryOp_And, validExprs)
        }

        return expr?.toRedExpr(frame)
    }
}

sealed class Rt_AtWhatItem {
    abstract fun value(): Rt_Value
}

class Rt_AtWhatItem_Value(private val value: Rt_Value): Rt_AtWhatItem() {
    override fun value() = value
}

class Rt_AtWhatItem_RExpr(private val frame: Rt_CallFrame, private val rExpr: R_Expr): Rt_AtWhatItem() {
    private val valueLazy: Rt_Value by lazy {
        rExpr.evaluate(frame)
    }

    override fun value() = valueLazy
}

class Rt_AtWhatItem_Evaluator(
        private val evaluator: Db_ComplexAtWhatEvaluator,
        private val frame: Rt_CallFrame,
        values: List<Rt_AtWhatItem>
): Rt_AtWhatItem() {
    private val values = values.toImmList()

    private val resultLazy: Rt_Value by lazy {
        evaluator.evaluate(frame, this.values)
    }

    override fun value() = resultLazy
}


abstract class Rt_AtWhatCombiner(val dbValueCount: Int) {
    abstract fun combine(dbValues: List<Rt_Value>): Rt_AtWhatItem

    companion object {
        fun combineValues(combiners: List<Rt_AtWhatCombiner>, row: List<Rt_Value>): List<Rt_AtWhatItem> {
            val res: MutableList<Rt_AtWhatItem> = ArrayList(combiners.size)

            var pos = 0
            for (combiner in combiners) {
                val count = combiner.dbValueCount
                val subRow = row.subList(pos, pos + count)
                val value = combiner.combine(subRow)
                res.add(value)
                pos += count
            }

            return res.toImmList()
        }
    }
}

sealed class Db_AtWhatValue {
    abstract fun rawTypes(): List<R_Type>
    abstract fun toRedExprs(frame: Rt_CallFrame): List<RedDb_Expr>
    abstract fun combiner(frame: Rt_CallFrame): Rt_AtWhatCombiner
}

class Db_AtWhatValue_RExpr(private val expr: R_Expr): Db_AtWhatValue() {
    override fun rawTypes() = listOf<R_Type>()
    override fun toRedExprs(frame: Rt_CallFrame) = listOf<RedDb_Expr>()

    override fun combiner(frame: Rt_CallFrame): Rt_AtWhatCombiner = Rt_AtWhatCombiner_RExpr(frame)

    private inner class Rt_AtWhatCombiner_RExpr(private val frame: Rt_CallFrame): Rt_AtWhatCombiner(0) {
        private val result: Rt_AtWhatItem = Rt_AtWhatItem_RExpr(frame, expr)

        override fun combine(dbValues: List<Rt_Value>): Rt_AtWhatItem {
            checkEquals(dbValues.size, 0)
            return result
        }
    }
}

class Db_AtWhatValue_DbExpr(private val expr: Db_Expr, private val resultType: R_Type): Db_AtWhatValue() {
    override fun rawTypes() = listOf(resultType)

    override fun toRedExprs(frame: Rt_CallFrame): List<RedDb_Expr> {
        val redExpr = expr.toRedExpr(frame)
        return listOf(redExpr)
    }

    override fun combiner(frame: Rt_CallFrame): Rt_AtWhatCombiner = Rt_AtWhatCombiner_Simple

    private object Rt_AtWhatCombiner_Simple: Rt_AtWhatCombiner(1) {
        override fun combine(dbValues: List<Rt_Value>): Rt_AtWhatItem {
            checkEquals(dbValues.size, 1)
            return Rt_AtWhatItem_Value(dbValues[0])
        }
    }
}

abstract class Db_ComplexAtWhatEvaluator {
    abstract fun evaluate(frame: Rt_CallFrame, values: List<Rt_AtWhatItem>): Rt_Value
}

class Db_AtWhatValue_Complex(
    subWhatValues: List<Db_AtWhatValue>,
    rExprs: List<R_Expr>,
    items: List<Pair<Boolean, Int>>,
    private val evaluator: Db_ComplexAtWhatEvaluator,
): Db_AtWhatValue() {
    private val subWhatValues = subWhatValues.toImmList()
    private val rawTypes = this.subWhatValues.flatMap { it.rawTypes() }.toImmList()
    private val rExprs = rExprs.toImmList()
    private val items = items.toImmList()

    init {
        items.forEach { (db, i) ->
            require(i >= 0 && i < (if (db) subWhatValues else rExprs).size) { "$db $i" }
        }
    }

    override fun rawTypes() = rawTypes

    override fun toRedExprs(frame: Rt_CallFrame): List<RedDb_Expr> {
        return subWhatValues.flatMap { it.toRedExprs(frame) }
    }

    override fun combiner(frame: Rt_CallFrame): Rt_AtWhatCombiner {
        val subCombiners = subWhatValues.map { it.combiner(frame) }.toImmList()
        val dbValueCount = subCombiners.sumBy { it.dbValueCount }
        return Rt_AtWhatCombiner_Complex(frame, subCombiners, dbValueCount)
    }

    private inner class Rt_AtWhatCombiner_Complex(
            private val frame: Rt_CallFrame,
            private val subCombiners: List<Rt_AtWhatCombiner>,
            dbValueCount: Int
    ): Rt_AtWhatCombiner(dbValueCount) {
        private var rValues: List<Rt_AtWhatItem>? = null

        override fun combine(dbValues: List<Rt_Value>): Rt_AtWhatItem {
            var rVals = rValues
            if (rVals == null) {
                rVals = rExprs.map { Rt_AtWhatItem_RExpr(frame, it) }.toImmList()
                rValues = rVals
            }

            val dbValues2 = combineValues(subCombiners, dbValues)

            val allValues = items.map { (db, i) ->
                if (db) dbValues2[i] else rVals[i]
            }

            return Rt_AtWhatItem_Evaluator(evaluator, frame, allValues)
        }
    }
}

class Db_AtWhatValue_ToStruct(private val rStruct: R_Struct, exprs: List<Db_Expr>): Db_AtWhatValue() {
    private val exprs = exprs.toImmList()

    override fun rawTypes() = exprs.map { it.type }

    override fun toRedExprs(frame: Rt_CallFrame): List<RedDb_Expr> {
        return exprs.map { it.toRedExpr(frame) }
    }

    override fun combiner(frame: Rt_CallFrame): Rt_AtWhatCombiner = Rt_AtWhatCombiner_ToStruct()

    private inner class Rt_AtWhatCombiner_ToStruct: Rt_AtWhatCombiner(exprs.size) {
        override fun combine(dbValues: List<Rt_Value>): Rt_AtWhatItem {
            val attrs = rStruct.attributesList

            if (dbValues.size != attrs.size) {
                throw Rt_Exception.common("to_struct:values_size:${attrs.size}:${dbValues.size}",
                        "Wrong number of values: ${dbValues.size} instead of ${attrs.size}")
            }

            val attrValues = dbValues.toMutableList()
            val resValue = Rt_StructValue(rStruct.type, attrValues)
            return Rt_AtWhatItem_Value(resValue)
        }
    }
}

class Db_AtWhatField(
    val flags: R_AtWhatFieldFlags,
    val value: Db_AtWhatValue,
)

class RedDb_AtWhatField(val expr: RedDb_Expr, val flags: R_AtWhatFieldFlags)

class RedDb_AtExprBase(
    private val from: List<RedDb_AtFromItem>,
    private val where: RedDb_Expr?,
    private val what: List<RedDb_AtWhatField>,
    private val isMany: Boolean,
    private val selWhat: List<Db_AtWhatValue>,
    private val resultTypes: List<R_Type>,
) {
    private val fromEntities = from.map { it.atEntity }.toImmList()

    fun execute(frame: Rt_CallFrame, extras: Rt_AtExprExtras): List<List<Rt_Value>> {
        val rtSql = buildSql(frame.sqlCtx, extras)
        val select = SqlSelect(rtSql, resultTypes)
        val combiners = selWhat.map { it.combiner(frame) }
        val records = select.execute(frame.sqlExec) { row ->
            val items = Rt_AtWhatCombiner.combineValues(combiners, row)
            items.map { it.value() }
        }
        return records
    }

    private fun buildSql(sqlCtx: Rt_SqlContext, extras: Rt_AtExprExtras): ParameterizedSql {
        val ctx = SqlGenContext.createTop(sqlCtx, from)
        val b = SqlBuilder()
        buildSql0(ctx, b, extras)
        return b.build()
    }

    fun buildNestedSql(ctx: SqlGenContext, b: SqlBuilder, extras: Rt_AtExprExtras) {
        val subCtx = ctx.createSub(from)
        buildSql0(subCtx, b, extras)
    }

    private fun buildSql0(ctx: SqlGenContext, b: SqlBuilder, extras: Rt_AtExprExtras) {
        val sqlParts = AtExprSqlParts(ctx, extras)
        appendClause(b, "SELECT", sqlParts.whatSqls)
        appendClause(b, " FROM", sqlParts.fromSql)
        appendClause(b, " WHERE", sqlParts.whereSql)
        appendClause(b, " GROUP BY", sqlParts.groupBySqls)
        appendClause(b, " ORDER BY", sqlParts.orderBySqls)
        appendClause(b, " LIMIT", extras.limit)
        appendClause(b, " OFFSET", extras.offset)
    }

    private fun appendClause(b: SqlBuilder, clause: String, sqls: List<ParameterizedSql>) {
        if (sqls.isNotEmpty()) {
            b.append("$clause ")
            b.append(sqls, ", ") {
                b.append(it)
            }
        }
    }

    private fun appendClause(b: SqlBuilder, clause: String, sql: ParameterizedSql?) {
        appendClause(b, clause, listOfNotNull(sql))
    }

    private fun appendClause(b: SqlBuilder, clause: String, value: Long?) {
        val sql = if (value == null) null else ParameterizedSql.generate { it.append(value) }
        appendClause(b, clause, sql)
    }

    private inner class AtExprSqlParts(ctx: SqlGenContext, private val extras: Rt_AtExprExtras) {
        val whereSql = translateWhere(ctx, where)
        val whatSqls = translateWhat(ctx, what)
        val groupBySqls = translateGroupBy(ctx, what)
        val orderBySqls = translateOrderBy(ctx, what)
        val fromSql = translateFrom(ctx)

        private fun translateFrom(ctx: SqlGenContext): ParameterizedSql {
            val fromWheres = from
                .mapNotNull {
                    if (it.where == null) null else {
                        val b = SqlBuilder()
                        it.where.toSql(ctx, b, false)
                        val sql = b.build()
                        it.atEntity.id to sql
                    }
                }
                .toMap()

            val fromInfo = ctx.getFromInfo()

            val b = SqlBuilder()

            for ((entityId, sqlEntity) in fromInfo.entities) {
                val joinWhere = fromWheres[entityId]
                translateFromItem(ctx.sqlCtx, sqlEntity, joinWhere, b)
            }

            return b.build()
        }

        private fun translateFromItem(
            sqlCtx: Rt_SqlContext,
            sqlEntity: SqlFromEntity,
            joinWhere: ParameterizedSql?,
            b: SqlBuilder,
        ): ParameterizedSql {
            if (!b.isEmpty()) {
                val sep = when {
                    joinWhere == null && !sqlEntity.isOuter -> ", "
                    sqlEntity.isOuter -> " LEFT OUTER JOIN "
                    else -> " JOIN "
                }
                b.append(sep)
            }

            val enclose = joinWhere != null && sqlEntity.joins.isNotEmpty()
            if (enclose) {
                b.append("(")
            }

            val table = sqlEntity.alias.entity.sqlMapping.table(sqlCtx)
            b.appendName(table)
            b.append(" ")
            b.append(sqlEntity.alias.str)

            for (join in sqlEntity.joins) {
                b.append(" JOIN ")
                val joinMapping = join.alias.entity.sqlMapping
                b.appendName(joinMapping.table(sqlCtx))
                b.append(" ")
                b.append(join.alias.str)
                b.append(" ON ")
                b.appendColumn(join.baseAlias, join.attr)
                b.append(" = ")
                b.appendColumn(join.alias, joinMapping.rowidColumn())
            }

            if (enclose) {
                b.append(")")
            }

            if (joinWhere != null) {
                b.append(" ON ")
                b.append(joinWhere)
            }

            return b.build()
        }

        private fun translateWhere(ctx: SqlGenContext, redWhere: RedDb_Expr?): ParameterizedSql? {
            return if (redWhere == null) null else translateExpr(ctx, redWhere)
        }

        private fun translateWhat(ctx: SqlGenContext, redWhat: List<RedDb_AtWhatField>): List<ParameterizedSql> {
            val res = redWhat.filter { !it.flags.omit }.map { translateExpr(ctx, it.expr) }
            return if (res.isNotEmpty()) res else listOf(ParameterizedSql("0", listOf()))
        }

        private fun translateGroupBy(ctx: SqlGenContext, redWhat: List<RedDb_AtWhatField>): List<ParameterizedSql> {
            return redWhat.filter { it.flags.group }.map { translateExpr(ctx, it.expr) }
        }

        private fun translateOrderBy(ctx: SqlGenContext, redWhat: List<RedDb_AtWhatField>): List<ParameterizedSql> {
            val elements = getOrderByElements(redWhat)
            return elements.map { element ->
                ParameterizedSql.generate { element.toSql(ctx, it) }
            }
        }

        private fun translateExpr(ctx: SqlGenContext, redExpr: RedDb_Expr): ParameterizedSql {
            return ParameterizedSql.generate { redExpr.toSql(ctx, it, false) }
        }

        private fun getOrderByElements(redWhat: List<RedDb_AtWhatField>): List<OrderByElement> {
            val elements = mutableListOf<OrderByElement>()

            for (field in redWhat) {
                if (field.flags.sort != null) {
                    elements.add(OrderByElement_Expr(field.expr, field.flags.sort))
                }
            }

            val redGroup = redWhat.filter { it.flags.group }
            if (redGroup.isNotEmpty() || redWhat.any { it.flags.aggregate }) {
                for (field in redGroup) {
                    if (field.flags.sort == null) {
                        elements.add(OrderByElement_Expr(field.expr, R_AtWhatSort.ASC))
                    }
                }
            } else if (isMany || extras.limit != null || extras.offset != null) {
                for (entity in fromEntities) {
                    elements.add(OrderByElement_Entity(entity))
                }
            }

            return elements.toImmList()
        }
    }

    private abstract class OrderByElement {
        abstract fun toSql(ctx: SqlGenContext, b: SqlBuilder)
    }

    private class OrderByElement_Expr(val redExpr: RedDb_Expr, val sort: R_AtWhatSort): OrderByElement() {
        override fun toSql(ctx: SqlGenContext, b: SqlBuilder) {
            redExpr.toSql(ctx, b, false)
            if (!sort.asc) {
                b.append(" DESC")
            }
        }
    }

    private class OrderByElement_Entity(val entity: R_DbAtEntity): OrderByElement() {
        override fun toSql(ctx: SqlGenContext, b: SqlBuilder) {
            val alias = ctx.getEntityAlias(entity)
            b.appendColumn(alias, entity.rEntity.sqlMapping.rowidColumn())
        }
    }
}

class Db_AtExprFrom(
    from: List<Db_AtFromItem>,
    private val block: R_FrameBlock? = null,
) {
    private val from = from.toImmList()

    init {
        val fromEntities = Lists.transform(this.from) { it.atEntity }
        R_DbAtEntity.checkList(fromEntities)
    }

    fun toRedItems(frame: Rt_CallFrame): List<RedDb_AtFromItem> {
        return frame.blockOpt(block) {
            from.map { it.toRed(frame) }
        }
    }
}

class Db_AtExprBase(
    private val from: Db_AtExprFrom,
    what: List<Db_AtWhatField>,
    private val where: Db_Expr?,
    private val isMany: Boolean,
) {
    private val what = what.toImmList()

    private val selWhat = what.filter { !it.flags.omit }.map { it.value }.toImmList()
    private val resultTypes = selWhat.flatMap { it.rawTypes() }.toImmList()

    fun toRedFrom(frame: Rt_CallFrame): RedDb_AtExprFrom {
        val redItems = from.toRedItems(frame)
        return RedDb_AtExprFrom(redItems, what, where, isMany, selWhat, resultTypes)
    }
}

class Db_NestedAtExpr(
    type: R_Type,
    private val base: Db_AtExprBase,
    private val extras: R_AtExprExtras,
    private val block: R_FrameBlock,
): Db_Expr(type) {
    override fun toRedExpr(frame: Rt_CallFrame): RedDb_Expr {
        val redFrom = base.toRedFrom(frame)
        val redBase = frame.block(block) {
            redFrom.toRedBase(frame)
        }

        val rtExtras = extras.evaluate(frame)
        return RedDb_NestedAtExpr(redBase, rtExtras)
    }

    private class RedDb_NestedAtExpr(
        private val redBase: RedDb_AtExprBase,
        private val rtExtras: Rt_AtExprExtras,
    ): RedDb_Expr() {
        override fun toSql0(ctx: SqlGenContext, bld: SqlBuilder) {
            redBase.buildNestedSql(ctx, bld, rtExtras)
        }
    }
}
