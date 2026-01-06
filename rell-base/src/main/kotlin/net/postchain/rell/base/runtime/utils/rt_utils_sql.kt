/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.utils

import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlInterceptor
import net.postchain.rell.base.sql.SqlPreparator
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
