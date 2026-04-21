/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList

class SqlBuilder {
    private val sqlBuf = StringBuilder()
    private val paramsBuf = mutableListOf<Rt_Value>()

    fun isEmpty(): Boolean = sqlBuf.isEmpty() && paramsBuf.isEmpty()

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

    fun build(): ParameterizedSql = ParameterizedSql(sqlBuf.toString(), paramsBuf.toImmList())
}

data class ParameterizedSql(val sql: String, val params: ImmList<Rt_Value>) {
    override fun toString() = sql

    fun isEmpty() = sql.isEmpty() && params.isEmpty()

    fun execute(sqlExec: SqlExecutor) {
        val args = calcArgs()
        sqlExec.execute(sql, args::bind)
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
            "sql:too_many_params:${params.size}" to "SQL query is too big (${params.size} parameters, max $maxParams)"
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
            val rtType = value.type()
            val adapter = checkNotNull(rtType.sqlAdapter) { "No SQL adapter for type: ${rtType.name}" }
            adapter.toSql(params, i + 1, value)
        }
    }
}

class SqlSelectRt(val pSql: ParameterizedSql, val resultTypes: ImmList<Rt_Type>) {
    fun execute(sqlExec: SqlExecutor): List<List<Rt_Value>> = buildList {
        pSql.executeQuery(sqlExec) { rsRow ->
            val list = mutableListOf<Rt_Value>()
            for ((i, type) in resultTypes.withIndex()) {
                val adapter = checkNotNull(type.sqlAdapter) {
                    "No SQL adapter for type: ${type.name}"
                }
                list.add(adapter.fromSql(rsRow, i + 1, false))
            }
            add(list.toImmList())
        }
    }
}
