/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.api.gtx

import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlInterceptor
import net.postchain.rell.base.sql.SqlPreparator
import java.math.BigDecimal

internal class ListeningSqlInterceptor(private val callback: ((SqlExecutionEvent) -> Unit)): SqlInterceptor {
    override fun invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int?,
    ): Int? {
        sql ?: return code(preparator)

        val preparatorWrapper = preparator?.let { PreparatorWrapper(it) }
        val startTime = System.currentTimeMillis()

        val rowCount = try {
            code(preparatorWrapper?.preparator)
        } catch (e: Exception) {
            fireEvent(sql, attributes, startTime, preparatorWrapper, null, e)
            throw e
        }

        fireEvent(sql, attributes, startTime, preparatorWrapper, rowCount, null)
        return rowCount
    }

    private fun fireEvent(
        sql: String,
        attributes: SqlExecutor.Attributes,
        startTime: Long,
        preparatorWrapper: PreparatorWrapper?,
        rowCount: Int?,
        error: Exception?,
    ) {
        val endTime = System.currentTimeMillis().coerceAtLeast(startTime)
        val isSystem = attributes.category == SqlExecutor.Category.SYS
        val duration = endTime - startTime
        val params = preparatorWrapper?.finish() ?: listOf()
        val event = SqlExecutionEvent(startTime, duration, sql, isSystem, params, rowCount, error)
        callback(event)
    }
}

private class PreparatorWrapper(private val originalPreparator: SqlPreparator) {
    val preparator: SqlPreparator = InterceptingSqlPreparator()

    private var prepared = false
    private val paramsList = mutableListOf<Any?>()

    fun finish(): List<Any?> {
        check(prepared)
        return paramsList.toList()
    }

    private inner class InterceptingSqlPreparator: SqlPreparator {
        override fun prepare(params: PreparedStatementParams) {
            check(!prepared)
            prepared = true
            val params2 = InterceptingPreparedStatementParams(params)
            originalPreparator.prepare(params2)
        }
    }

    private inner class InterceptingPreparedStatementParams(
        private val target: PreparedStatementParams,
    ): PreparedStatementParams {
        override fun setBoolean(parameterIndex: Int, x: Boolean) = set0(parameterIndex, x, target::setBoolean)
        override fun setInt(parameterIndex: Int, x: Int) = set0(parameterIndex, x, target::setInt)
        override fun setLong(parameterIndex: Int, x: Long) = set0(parameterIndex, x, target::setLong)
        override fun setBigDecimal(parameterIndex: Int, x: BigDecimal?) = set0(parameterIndex, x, target::setBigDecimal)
        override fun setString(parameterIndex: Int, x: String?) = set0(parameterIndex, x, target::setString)
        override fun setBytes(parameterIndex: Int, x: ByteArray?) = set0(parameterIndex, x, target::setBytes)
        override fun setObject(parameterIndex: Int, x: Any?) = set0(parameterIndex, x, target::setObject)

        private fun <T> set0(parameterIndex: Int, x: T, setter: (Int, T) -> Unit) {
            require(parameterIndex in 1 .. 32768) { parameterIndex }
            setter(parameterIndex, x)

            while (paramsList.size < parameterIndex) {
                paramsList.add(null)
            }
            paramsList[parameterIndex - 1] = x
        }
    }
}
