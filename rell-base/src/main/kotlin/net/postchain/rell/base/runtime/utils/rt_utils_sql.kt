/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.utils

import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.sql.*
import java.sql.SQLException

object Rt_SqlManagerUtils {
    fun makeSqlManager(sqlMgr: SqlManager, logErrors: Boolean): SqlManager {
        return WrappingSqlManager(sqlMgr) { sqlExec ->
            makeSqlExecutor(sqlExec, logErrors)
        }
    }

    fun makeSqlExecutor(sqlExec: SqlExecutor, logErrors: Boolean): SqlExecutor {
        var res = sqlExec
        if (logErrors) {
            res = InterceptingSqlExecutor(res, ErrorPrintingSqlInterceptor)
        }
        res = InterceptingSqlExecutor(res, ErrorWrappingSqlInterceptor)
        return res
    }
}

private object ErrorPrintingSqlInterceptor: SqlInterceptor {
    override fun <T> invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> T,
    ): T {
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
    override fun <T> invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> T,
    ): T {
        return try {
            code(preparator)
        } catch (e: SQLException) {
            throw Rt_Exception.common("sqlerr:${e.errorCode}", "SQL Error: ${e.message}")
        }
    }
}
