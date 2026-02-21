/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.utils

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_ObjectDefinition
import net.postchain.rell.base.model.expr.ParameterizedSql
import net.postchain.rell.base.model.expr.SqlBuilder
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlInterceptor
import net.postchain.rell.base.sql.SqlPreparator
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.toImmMap
import java.sql.SQLException

private const val POSTGRES_SQLSTATE_QUERY_CANCELED = "57014"

/**
 * Checks if this SQLException represents a PostgreSQL query cancellation.
 * This happens when a query times out (statement_timeout) or is explicitly cancelled.
 */
val SQLException.isPostgresQueryCanceled: Boolean
    get() = sqlState == POSTGRES_SQLSTATE_QUERY_CANCELED

object Rt_SqlManagerUtils {
    @Suppress("RedundantNullableReturnType")
    fun wrapSqlInterceptor(base: SqlInterceptor?, logErrors: Boolean): SqlInterceptor? {
        var res = base
        if (logErrors) {
            res = SqlInterceptor.compound(ErrorPrintingSqlInterceptor, res)
        }
        res = SqlInterceptor.compound(ErrorWrappingSqlInterceptor, res)
        return res
    }
}

private object ErrorPrintingSqlInterceptor: SqlInterceptor {
    override fun invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int?,
    ): Int? {
        return try {
            code(preparator)
        } catch (e: SQLException) {
            if (sql != null) {
                System.err.println("SQL: $sql")
            }
            e.printStackTrace()
            throw e
        }
    }
}

private object ErrorWrappingSqlInterceptor: SqlInterceptor {
    override fun invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int?,
    ): Int? {
        return try {
            code(preparator)
        } catch (e: SQLException) {
            if (e.isPostgresQueryCanceled) {
                throw e
            }
            throw Rt_Exception.common("sqlerr:${e.errorCode}", "SQL Error: ${e.message}")
        }
    }
}

object Rt_SnapshotSqlUtils {
    fun insert(
        sqlCtx: Rt_SqlContext,
        rEntity: R_EntityDefinition,
        rowid: Long,
        values: List<Rt_Value>,
    ): ParameterizedSql {
        val b = SqlBuilder()

        b.append("INSERT INTO ")
        b.appendName(rEntity.sqlMapping.table(sqlCtx))

        b.append("(")
        b.appendName(rEntity.sqlMapping.rowidColumn())
        b.append(rEntity.attributes.values, "") { attr ->
            b.append(", ")
            b.appendName(attr.sqlMapping)
        }
        b.append(")")

        b.append(" VALUES (")
        b.append(rowid)

        for (value in values) {
            b.append(", ")
            b.append(value)
        }

        b.append(");")
        return b.build()
    }

    fun delete(
        sqlCtx: Rt_SqlContext,
        rEntity: R_EntityDefinition,
    ): ParameterizedSql {
        val b = SqlBuilder()
        b.append("DELETE FROM ")
        b.appendName(rEntity.sqlMapping.table(sqlCtx))
        b.append(";")
        return b.build()
    }

    @Suppress("SqlWithoutWhere") fun initObjectSnapshotIds(
        sqlCtx: Rt_SqlContext,
        sqlExec: SqlExecutor,
        objects: List<R_ObjectDefinition>,
    ): ImmMap<String, Long> {
        if (objects.isEmpty()) return emptyMap<String, Long>().toImmMap()

        val result = mutableMapOf<String, Long>()
        val rowidFunction = sqlCtx.mainChainMapping().rowidFunction

        for (obj in objects) {
            val rEntity = obj.rEntity
            val table = rEntity.sqlMapping.table(sqlCtx)
            val rowidCol = rEntity.sqlMapping.rowidColumn()

            var rowid = 0L
            sqlExec.executeQuery(
                """SELECT "$rowidCol" FROM "$table" LIMIT 1""",
                SqlPreparator.NULL,
            ) { row ->
                rowid = row.getLong(1)
            }

            if (rowid == 0L) {
                sqlExec.executeQuery("""SELECT "$rowidFunction"()""", SqlPreparator.NULL) { row ->
                    rowid = row.getLong(1)
                }
                sqlExec.execute("""UPDATE "$table" SET "$rowidCol" = $rowid""")
            }

            result[rEntity.metaName] = rowid
        }

        return result.toImmMap()
    }

    fun readObjectState(
        sqlCtx: Rt_SqlContext,
        sqlExec: SqlExecutor,
        rEntity: R_EntityDefinition,
    ): Gtv {
        val table = rEntity.sqlMapping.table(sqlCtx)
        val attrs = rEntity.strAttributes.values.toList()

        val columns = attrs.joinToString(", ") { "\"${it.sqlMapping}\"" }
        val sql = """SELECT $columns FROM "$table" LIMIT 1"""

        val attrValues = mutableMapOf<String, Gtv>()
        sqlExec.executeQuery(sql, SqlPreparator.NULL) { row ->
            for ((i, attr) in attrs.withIndex()) {
                val rtValue = attr.type.sqlAdapter.fromSql(row, i + 1, false)
                attrValues[attr.name] = attr.type.rtToGtv(rtValue, false)
            }
        }

        return GtvFactory.gtv(
            GtvFactory.gtv(rEntity.metaName),
            GtvFactory.gtv(attrValues),
        )
    }
}
