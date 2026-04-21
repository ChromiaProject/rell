/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.utils

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.rr.RR_EntityDefinition
import net.postchain.rell.base.model.rr.RR_ObjectDefinition
import net.postchain.rell.base.runtime.*
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
    fun readObjectState(
        sqlCtx: Rt_SqlContext,
        sqlExec: SqlExecutor,
        entity: RR_EntityDefinition,
        interpreter: Rt_Interpreter,
    ): Gtv {
        val table = entity.sqlMapping.table(sqlCtx)
        val attrs = entity.strAttributes.values.toList()

        val columns = attrs.joinToString(", ") { "\"${it.sqlMapping}\"" }
        val sql = """SELECT $columns FROM "$table" LIMIT 1"""

        val attrValues = mutableMapOf<String, Gtv>()
        sqlExec.executeQuery(sql, SqlPreparator.NULL) { row ->
            for ((i, attr) in attrs.withIndex()) {
                val rtType = interpreter.resolveType(attr.type)
                val sqlAdapter = checkNotNull(rtType.sqlAdapter) { "No SQL adapter for type: ${rtType.name}" }
                val rtValue = sqlAdapter.fromSql(row, i + 1, false)
                val conv: Rt_TypeGtvConversion = checkNotNull(rtType.gtvConversion) {
                    "No GTV conversion for type: ${rtType.name}"
                }
                attrValues[attr.name] = conv.rtToGtv(rtValue, false)
            }
        }

        return GtvFactory.gtv(
            GtvFactory.gtv(entity.sqlMapping.metaName),
            GtvFactory.gtv(attrValues),
        )
    }

    fun insert(
        sqlCtx: Rt_SqlContext,
        entity: RR_EntityDefinition,
        rowid: Long,
        values: List<Rt_Value>,
    ): ParameterizedSql {
        val b = SqlBuilder()

        b.append("INSERT INTO ")
        b.appendName(entity.sqlMapping.table(sqlCtx))

        b.append("(")
        b.appendName(entity.sqlMapping.rowidColumn)
        b.append(entity.attributes.values, "") { attr ->
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
        entity: RR_EntityDefinition,
    ): ParameterizedSql {
        val b = SqlBuilder()
        b.append("DELETE FROM ")
        b.appendName(entity.sqlMapping.table(sqlCtx))
        b.append(";")
        return b.build()
    }

    @Suppress("SqlWithoutWhere") fun initObjectSnapshotIds(
        sqlCtx: Rt_SqlContext,
        sqlExec: SqlExecutor,
        objects: List<RR_ObjectDefinition>,
    ): ImmMap<String, Long> {
        if (objects.isEmpty()) return emptyMap<String, Long>().toImmMap()

        val result = mutableMapOf<String, Long>()
        val rowidFunction = sqlCtx.mainChainMapping().rowidFunction

        for (obj in objects) {
            val entity = obj.rEntity
            val table = entity.sqlMapping.table(sqlCtx)
            val rowidCol = entity.sqlMapping.rowidColumn

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

            result[entity.sqlMapping.metaName] = rowid
        }

        return result.toImmMap()
    }
}
