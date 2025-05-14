/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.expr

import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.associateToImmMap
import net.postchain.rell.base.utils.chainToIterable
import net.postchain.rell.base.utils.flatMapToImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

data class SqlTableAlias(val entity: R_EntityDefinition, val exprId: R_AtExprId, val str: String)
class SqlTableJoin(val attr: R_Attribute, val alias: SqlTableAlias)

class SqlFromInfo(val entities: ImmMap<R_AtEntityId, SqlFromEntity>)

class SqlFromEntity(
    val alias: SqlTableAlias,
    val isOuter: Boolean,
    val joins: ImmList<SqlFromJoin>,
)

class SqlFromJoin(val baseAlias: SqlTableAlias, val attr: String, val alias: SqlTableAlias)

private class SqlGenAliasAllocator {
    private var aliasCtr = 0

    fun nextAlias(entity: R_EntityDefinition, exprId: R_AtExprId): SqlTableAlias {
        val aliasStr = String.format("A%02d", aliasCtr++)
        return SqlTableAlias(entity, exprId, aliasStr)
    }
}

class SqlGenContext private constructor(
    val sqlCtx: Rt_SqlContext,
    fromItems: List<RedDb_AtFromItem>,
    private val parent: SqlGenContext?,
) {
    private val fromItems = fromItems.toImmList()
    private val aliasAllocator: SqlGenAliasAllocator = parent?.aliasAllocator ?: SqlGenAliasAllocator()

    private val atExprId = R_DbAtEntity.checkList(this.fromItems.map { it.atEntity })
    private val entityAliasMap = mutableMapOf<R_AtEntityId, EntityAliasTbl>()
    private val aliasTableMap = mutableMapOf<SqlTableAlias, EntityAliasTbl>()

    init {
        for (item in this.fromItems) {
            val atEntity = item.atEntity
            val alias = aliasAllocator.nextAlias(atEntity.rEntity, atExprId)
            val tbl = EntityAliasTbl(alias, item.isOuter)
            check(alias !in aliasTableMap) { "${aliasTableMap.keys} $alias" }
            aliasTableMap[alias] = tbl
            check(atEntity.id !in entityAliasMap) { "${entityAliasMap.keys} ${atEntity.id}" }
            entityAliasMap[atEntity.id] = tbl
        }
    }

    fun createSub(fromItems: List<RedDb_AtFromItem>): SqlGenContext {
        return SqlGenContext(sqlCtx, fromItems, this)
    }

    fun getEntityAlias(entity: R_DbAtEntity): SqlTableAlias {
        val ctx = getSqlGenCtxForExpr(entity.id.exprId)
        val tbl = ctx.entityAliasMap.getValue(entity.id)
        return tbl.alias
    }

    fun getRelAlias(baseAlias: SqlTableAlias, rel: R_Attribute, targetEntity: R_EntityDefinition): SqlTableAlias {
        val ctx = getSqlGenCtxForExpr(baseAlias.exprId)
        val tbl = ctx.aliasTableMap.getValue(baseAlias)
        val map = tbl.subAliases.computeIfAbsent(baseAlias) { mutableMapOf() }
        val join = map.computeIfAbsent(rel.name) {
            val alias = aliasAllocator.nextAlias(targetEntity, baseAlias.exprId)
            ctx.aliasTableMap[alias] = tbl
            SqlTableJoin(rel, alias)
        }
        return join.alias
    }

    fun getFromInfo(): SqlFromInfo {
        val entities = fromItems.associateToImmMap {
            val entityId = it.atEntity.id
            val tbl = entityAliasMap.getValue(entityId)
            val joins = tbl.subAliases.entries.flatMapToImmList { (alias, map) ->
                map.values.map { tblJoin -> SqlFromJoin(alias, tblJoin.attr.sqlMapping, tblJoin.alias) }
            }
            entityId to SqlFromEntity(tbl.alias, it.isOuter, joins)
        }
        return SqlFromInfo(entities)
    }

    private fun getSqlGenCtxForExpr(exprId: R_AtExprId): SqlGenContext {
        val iterable = chainToIterable(this) { it.parent }
        return iterable.firstOrNull { it.atExprId == exprId } ?: throw IllegalArgumentException("$exprId")
    }

    private class EntityAliasTbl(val alias: SqlTableAlias, val isOuter: Boolean) {
        val subAliases = mutableMapOf<SqlTableAlias, MutableMap<String, SqlTableJoin>>()
    }

    companion object {
        fun createTop(sqlCtx: Rt_SqlContext, fromItems: List<RedDb_AtFromItem>): SqlGenContext {
            return SqlGenContext(sqlCtx, fromItems, null)
        }
    }
}

class SqlBuilder {
    private val sqlBuf = StringBuilder()
    private val paramsBuf = mutableListOf<Rt_Value>()

    fun isEmpty(): Boolean {
        return sqlBuf.isEmpty() && paramsBuf.isEmpty()
    }

    fun <T> append(list: Iterable<T>, sep: String, block: (T) -> Unit) {
        var s = ""
        for (t in list) {
            append(s)
            block(t)
            s = sep
        }
    }

    fun appendName(name: String) {
        append("\"")
        append(name)
        append("\"")
    }

    fun appendColumn(alias: SqlTableAlias, column: String) {
        appendColumn(alias.str, column)
    }

    fun appendColumn(alias: String, column: String) {
        append(alias)
        append(".")
        appendName(column)
    }

    fun append(sql: String) {
        sqlBuf.append(sql)
    }

    fun append(param: Long) {
        sqlBuf.append("?")
        paramsBuf.add(Rt_IntValue.get(param))
    }

    fun append(value: Rt_Value) {
        sqlBuf.append("?")
        paramsBuf.add(value)
    }

    fun append(buf: SqlBuilder) {
        sqlBuf.append(buf.sqlBuf)
        paramsBuf.addAll(buf.paramsBuf)
    }

    fun append(sql: ParameterizedSql) {
        sqlBuf.append(sql.sql)
        paramsBuf.addAll(sql.params)
    }

    fun appendSep(sep: String) {
        if (!isEmpty()) {
            append(sep)
        }
    }

    fun build(): ParameterizedSql = ParameterizedSql(sqlBuf.toString(), paramsBuf.toImmList())
}

class ParameterizedSql(val sql: String, val params: ImmList<Rt_Value>) {
    constructor(): this("", immListOf())

    fun isEmpty() = sql.isEmpty() && params.isEmpty()

    fun execute(sqlExec: SqlExecutor) {
        val args = calcArgs()
        sqlExec.execute(sql, args::bind)
    }

    fun executeUpdate(sqlExec: SqlExecutor): Int {
        val args = calcArgs()
        val res = sqlExec.executeUpdate(sql, args::bind)
        return res
    }

    fun executeQuery(sqlExec: SqlExecutor, consumer: (ResultSetRow) -> Unit) {
        val args = calcArgs()
        sqlExec.executeQuery(sql, args::bind, consumer)
    }

    private fun calcArgs(): SqlArgs {
        // Was experimentally discovered that passing more than 32767 parameters causes PSQL driver to fail and the
        // connection becomes invalid afterwards. Not allowing this to happen.
        val maxParams = 32767
        Rt_Utils.check(params.size <= maxParams) {
            "sql:too_many_params:${params.size}" toCodeMsg
                    "SQL query is too big (${params.size} parameters, max $maxParams)"
        }
        return SqlArgs(params)
    }

    companion object {
        val TRUE = ParameterizedSql("TRUE", immListOf())

        fun generate(generator: (SqlBuilder) -> Unit): ParameterizedSql {
            val b = SqlBuilder()
            generator(b)
            return b.build()
        }
    }
}

class SqlArgs(private val values: ImmList<Rt_Value>) {
    fun bind(params: PreparedStatementParams) {
        for ((i, value) in values.withIndex()) {
            val type = value.type()
            type.sqlAdapter.toSql(params, i + 1, value)
        }
    }
}

class SqlSelect(val pSql: ParameterizedSql, val resultTypes: List<R_Type>) {
    fun execute(sqlExec: SqlExecutor): List<List<Rt_Value>> {
        return execute(sqlExec) { it }
    }

    fun execute(sqlExec: SqlExecutor, transformer: (List<Rt_Value>) -> List<Rt_Value>): List<List<Rt_Value>> {
        val result = mutableListOf<List<Rt_Value>>()

        pSql.executeQuery(sqlExec) { rsRow ->
            val list = mutableListOf<Rt_Value>()
            for (i in resultTypes.indices) {
                val type = resultTypes[i]
                val value = type.sqlAdapter.fromSql(rsRow, i + 1, false)
                list.add(value)
            }

            val row = list.toImmList()
            val transRow = transformer(row)
            result.add(transRow)
        }

        return result
    }
}
