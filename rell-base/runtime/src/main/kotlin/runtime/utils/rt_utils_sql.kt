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
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toImmList
import net.postchain.rell.base.utils.toImmMap
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL
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

        // No bind values: use jOOQ's typed `selectQuery()` with an inline LIMIT and renderInlined
        // so the literal 1 is embedded. jOOQ's PG dialect renders this as the SQL-standard
        // `OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY` rather than `LIMIT 1` — functionally equivalent
        // on PG, and this is a SYS-category init read that no test pins on byte format.
        val tableRef: Table<Record> = DSL.table(DSL.name(table))
        val q = JOOQ_CTX.selectQuery()
        for (attr in attrs) q.addSelect(DSL.field(DSL.name(attr.sqlMapping)))
        q.addFrom(tableRef)
        q.addLimit(1)
        val sql = JOOQ_CTX.renderInlined(q)

        val attrValues = mutableMapOf<String, Gtv>()
        sqlExec.executeQuery(ParameterizedSql(sql, immListOf())) { row ->
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
        val table: Table<Record> = DSL.table(DSL.name(entity.sqlMapping.table(sqlCtx)))
        val rowidLiteral = DSL.field("$rowid", Any::class.java)
        val placeholder: Field<Any> = DSL.field("?", Any::class.java)
        val row = LinkedHashMap<Field<*>, Field<*>>()
        row[DSL.field(DSL.name(entity.sqlMapping.rowidColumn))] = rowidLiteral
        for (attr in entity.attributes.values) {
            row[DSL.field(DSL.name(attr.sqlMapping))] = placeholder
        }
        val q = JOOQ_CTX.insertQuery(table)
        q.addValues(row)
        return ParameterizedSql("${renderJooq(q)};", values.toImmList())
    }

    fun delete(
        sqlCtx: Rt_SqlContext,
        entity: RR_EntityDefinition,
    ): ParameterizedSql {
        val table: Table<Record> = DSL.table(DSL.name(entity.sqlMapping.table(sqlCtx)))
        val q = JOOQ_CTX.deleteQuery(table)
        return ParameterizedSql("${renderJooq(q)};", immListOf())
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

            // SELECT "rowid" FROM "<table>" LIMIT 1
            val tableRef: Table<Record> = DSL.table(DSL.name(table))
            val rowidField: Field<Any> = DSL.field(DSL.name(rowidCol))
            val selectQ = JOOQ_CTX.selectQuery()
            selectQ.addSelect(rowidField)
            selectQ.addFrom(tableRef)
            selectQ.addLimit(1)

            var rowid = 0L
            sqlExec.executeQuery(ParameterizedSql(JOOQ_CTX.renderInlined(selectQ), immListOf())) { row ->
                rowid = row.getLong(1)
            }

            if (rowid == 0L) {
                // SELECT "<chain>.make_rowid"() — the function call is built via DSL.field with a
                // {0}-substituted Name so the chain-qualified identifier is escaped correctly.
                val rowidFnCall = DSL.field("{0}()", Any::class.java, DSL.name(rowidFunction))
                sqlExec.executeQuery(
                    ParameterizedSql(
                        "SELECT ${renderJooq(rowidFnCall)}",
                        immListOf(),
                    ),
                ) { row ->
                    rowid = row.getLong(1)
                }
                // UPDATE "<table>" SET "rowid" = <rowid-literal>
                val updateQ = JOOQ_CTX.updateQuery(tableRef)
                updateQ.addValues(
                    LinkedHashMap<Field<*>, Field<*>>().also { m ->
                        m[rowidField] = DSL.field("$rowid", Any::class.java)
                    },
                )
                sqlExec.execute(JooqDdlStatement(updateQ))
            }

            result[entity.sqlMapping.metaName] = rowid
        }

        return result.toImmMap()
    }
}
