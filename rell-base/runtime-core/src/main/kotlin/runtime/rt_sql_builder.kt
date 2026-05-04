/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.RawSqlAccess
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlPreparator
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immListOf
import org.intellij.lang.annotations.Language
import org.jooq.Query

/**
 * Anything that can run a single SQL statement against a [SqlExecutor]. Pure data — dispatch lives
 * in [SqlExecutor.execute], which exhaustively pattern-matches the sealed hierarchy and is the
 * only place that touches raw JDBC.
 */
sealed interface ExecutableSql

/**
 * A no-bind hand-written SQL statement (DDL, DML, SET, anything jOOQ can't express). The
 * constructor is [RawSqlAccess]-gated so every caller building one from string concatenation
 * has to opt in explicitly — production paths should prefer [JooqDdlStatement].
 */
class RawSqlStatement @RawSqlAccess constructor(@param:Language("SQL") val sql: String) : ExecutableSql {
    override fun toString() = sql
}

/**
 * A jOOQ-built DDL statement (CREATE TABLE, ALTER TABLE, DROP INDEX, …). Renders the jOOQ
 * [Query] AST inline (PostgreSQL forbids bind parameters in DDL). The structural origin — every
 * identifier escaped by jOOQ's PG renderer, every keyword from the dialect — is what distinguishes
 * this from [RawSqlStatement], whose SQL string was hand-written and audit-required.
 */
class JooqDdlStatement(internal val query: Query) : ExecutableSql

/**
 * Hand-written SQL with a free [SqlPreparator] for setting binds via raw JDBC types. Intended for
 * test code that doesn't have an `Rt_Value` representation handy; production paths should build a
 * [ParameterizedSql] from typed values instead. [RawSqlAccess]-gated.
 */
class RawSqlBoundStatement @RawSqlAccess constructor(
    @param:Language("SQL") val sql: String,
    internal val preparator: SqlPreparator,
) : ExecutableSql

/**
 * Hand-written query with a free [SqlPreparator] for binds and row-by-row consumption. Test escape
 * hatch parallel to [RawSqlBoundStatement]. [RawSqlAccess]-gated.
 */
class RawSqlBoundQuery @RawSqlAccess constructor(
    @param:Language("SQL") val sql: String,
    internal val preparator: SqlPreparator = SqlPreparator.NULL,
)

data class ParameterizedSql(val sql: String, val params: ImmList<Rt_Value>) : ExecutableSql {
    override fun toString() = sql

    fun isEmpty() = sql.isEmpty() && params.isEmpty()

    internal fun calcArgs(): SqlArgs {
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
    }
}

@JvmInline value class SqlArgs(private val values: ImmList<Rt_Value>) {
    fun bind(params: PreparedStatementParams) {
        for ((i, value) in values.withIndex()) {
            val rtType = value.type
            val adapter = checkNotNull(rtType.sqlAdapter) { "No SQL adapter for type: ${rtType.name}" }
            adapter.rtToSql(params, i + 1, value)
        }
    }
}

class SqlSelectRt(val pSql: ParameterizedSql, val resultTypes: ImmList<Rt_ValueClass<*>>) {
    fun execute(sqlExec: SqlExecutor): List<List<Rt_Value>> = buildList {
        sqlExec.executeQuery(pSql) { rsRow ->
            val list = buildList(resultTypes.size) {
                for ((i, type) in resultTypes.withIndex()) {
                    val adapter = checkNotNull(type.sqlAdapter) {
                        "No SQL adapter for type: ${type.name}"
                    }
                    add(adapter.fromSql(rsRow, i + 1, false))
                }
            }
            add(list)
        }
    }
}
