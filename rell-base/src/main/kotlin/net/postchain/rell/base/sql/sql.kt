/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import mu.KLogging
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immSetOf
import net.postchain.rell.base.utils.toImmSet
import org.jooq.tools.jdbc.MockConnection
import java.io.Closeable
import java.sql.Connection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SqlConstants {
    const val ROWID_COLUMN = "rowid"
    const val ROWID_GEN = "rowid_gen"
    const val MAKE_ROWID = "make_rowid"

    const val FN_INTEGER_POWER = "rell_integer_power"
    const val FN_BIGINTEGER_FROM_TEXT = "rell_biginteger_from_text"
    const val FN_BIGINTEGER_POWER = "rell_biginteger_power"
    const val FN_BYTEA_SUBSTR1 = "rell_bytea_substr1"
    const val FN_BYTEA_SUBSTR2 = "rell_bytea_substr2"
    const val FN_DECIMAL_FROM_TEXT = "rell_decimal_from_text"
    const val FN_DECIMAL_TO_TEXT = "rell_decimal_to_text"
    const val FN_TEXT_REPEAT = "rell_text_repeat"
    const val FN_TEXT_SUBSTR1 = "rell_text_substr1"
    const val FN_TEXT_SUBSTR2 = "rell_text_substr2"
    const val FN_TEXT_GETCHAR = "rell_text_getchar"

    const val BLOCKCHAINS_TABLE = "blockchains"
    const val BLOCKS_TABLE = "blocks"
    const val TRANSACTIONS_TABLE = "transactions"

    // Reserved chain-specific (starting with prefix cN.) tables used by Postchain.
    val SYSTEM_CHAIN_TABLES = immSetOf(
            "events",
            "states",
            "event_pages",
            "snapshot_pages",
            "configurations",
            "gtx_module_version"
    )

    private val SYSTEM_OBJECTS_0 = immSetOf(
            ROWID_GEN,
            MAKE_ROWID,
            BLOCKCHAINS_TABLE,
            BLOCKS_TABLE,
            TRANSACTIONS_TABLE,
            "meta",
            "peerinfos"
    )

    val SYSTEM_OBJECTS = (SYSTEM_OBJECTS_0 + SYSTEM_CHAIN_TABLES).toImmSet()

    val SYSTEM_APP_TABLES = immSetOf(
            BLOCKCHAINS_TABLE,
            "meta",
            "peerinfos",
            "containers"
    )
}

class SqlConnectionLogger {
    private val conId = idCounter.getAndIncrement()

    fun log(s: String) {
        logger.info("[{}] {}", conId, s)
    }

    companion object: KLogging() {
        private val idCounter = AtomicLong()

        fun getOrNull(enabled: Boolean): SqlConnectionLogger? = if (enabled) SqlConnectionLogger() else null
    }
}

interface SqlManager {
    val hasConnection: Boolean
    fun <T> transaction(code: (SqlExecutor) -> T): T = execute(true, code)
    fun <T> access(code: (SqlExecutor) -> T): T = execute(false, code)
    fun <T> execute(tx: Boolean, code: (SqlExecutor) -> T): T
}

abstract class AbstractSqlManager: SqlManager {
    private val busy = AtomicBoolean()

    protected abstract fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T

    final override fun <T> execute(tx: Boolean, code: (SqlExecutor) -> T): T {
        check(busy.compareAndSet(false, true))
        try {
            val res = execute0(tx) { sqlExec ->
                SingleUseSqlExecutor(sqlExec).use(code)
            }
            return res
        } finally {
            check(busy.compareAndSet(true, false))
        }
    }

    private class SingleUseSqlExecutor(private val sqlExec: SqlExecutor): SqlExecutor(), Closeable {
        private var valid = true

        override fun <T> connection(code: (Connection) -> T): T {
            check(valid)
            return sqlExec.connection(code)
        }

        override fun hasRealConnection() = sqlExec.hasRealConnection()

        override fun execute(sql: String) {
            check(valid)
            sqlExec.execute(sql)
        }

        override fun execute(sql: String, preparator: SqlPreparator) {
            check(valid)
            sqlExec.execute(sql, preparator)
        }

        override fun executeUpdate(sql: String, preparator: SqlPreparator): Int {
            check(valid)
            return sqlExec.executeUpdate(sql, preparator)
        }

        override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) {
            check(valid)
            sqlExec.executeQuery(sql, preparator, consumer)
        }

        override fun close() {
            check(valid)
            valid = false
        }
    }
}

class WrappingSqlManager(
    private val sqlMgr: SqlManager,
    private val wrapper: (SqlExecutor) -> SqlExecutor,
): SqlManager {
    override val hasConnection: Boolean get() = sqlMgr.hasConnection

    override fun <T> execute(tx: Boolean, code: (SqlExecutor) -> T): T {
        return sqlMgr.execute(tx) { sqlExec ->
            val sqlExec2 = wrapper(sqlExec)
            code(sqlExec2)
        }
    }

    companion object {
        fun intercepting(sqlMgr: SqlManager, interceptor: SqlInterceptor): SqlManager {
            return WrappingSqlManager(sqlMgr) { sqlExec ->
                InterceptingSqlExecutor(sqlExec, interceptor)
            }
        }
    }
}

fun interface SqlPreparator {
    fun prepare(params: PreparedStatementParams)

    companion object {
        val NULL: SqlPreparator = NullSqlPreparator
    }
}

private object NullSqlPreparator: SqlPreparator {
    override fun prepare(params: PreparedStatementParams) {
        // Do nothing
    }
}

abstract class SqlExecutor {
    enum class Category {
        SYS,
        USER,
    }

    data class Attributes(
        val category: Category = Category.SYS,
    ) {
        companion object {
            val DEFAULT = Attributes()
        }
    }

    abstract fun hasRealConnection(): Boolean
    abstract fun <T> connection(code: (Connection) -> T): T
    abstract fun execute(sql: String)
    abstract fun execute(sql: String, preparator: SqlPreparator)
    abstract fun executeUpdate(sql: String, preparator: SqlPreparator): Int
    abstract fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit)

    /**
     * A way to pass some context information to a logging layer (wrapper) without adding a new parameter to every
     * function. ATM the context has only the query mode, but might be also file pos, etc.
     * Not perfect; consider a better approach when needed.
     */
    open fun withAttributes(attributes: Attributes): SqlExecutor = this
}

object NoConnSqlManager: AbstractSqlManager() {
    override val hasConnection = false

    override fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T {
        val res = code(NoConnSqlExecutor)
        return res
    }
}

object NoConnSqlExecutor: SqlExecutor() {
    override fun <T> connection(code: (Connection) -> T): T {
        val con = MockConnection { TODO() }
        val res = code(con)
        return res
    }

    override fun hasRealConnection() = false
    override fun execute(sql: String) = throw err()
    override fun execute(sql: String, preparator: SqlPreparator) = throw err()
    override fun executeUpdate(sql: String, preparator: SqlPreparator) = throw err()
    override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) = throw err()

    private fun err() = Rt_Exception.common("no_sql", "No database connection")
}

class ConnectionSqlManager(private val con: Connection, logging: Boolean): AbstractSqlManager() {
    override val hasConnection = true

    private val conLogger = SqlConnectionLogger.getOrNull(logging)
    private val sqlExec = makeSqlExecutor(con, conLogger)

    init {
        check(con.autoCommit)
    }

    override fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T {
        val res = if (tx) {
            transaction0(code)
        } else {
            access0(code)
        }
        return res
    }

    private fun <T> transaction0(code: (SqlExecutor) -> T): T {
        val autoCommit = con.autoCommit
        check(autoCommit)
        try {
            con.autoCommit = false
            var rollback = true
            try {
                conLogger?.log("BEGIN TRANSACTION")
                val res = code(sqlExec)
                conLogger?.log("COMMIT TRANSACTION")
                con.commit()
                rollback = false
                return res
            } finally {
                if (rollback) {
                    conLogger?.log("ROLLBACK TRANSACTION")
                    con.rollback()
                }
            }
        } finally {
            con.autoCommit = autoCommit
        }
    }

    private fun <T> access0(code: (SqlExecutor) -> T): T {
        check(con.autoCommit)
        val res = code(sqlExec)
        check(con.autoCommit)
        return res
    }

    companion object {
        fun makeSqlExecutor(con: Connection, conLogger: SqlConnectionLogger? = null): SqlExecutor {
            var res: SqlExecutor = ConnectionSqlExecutor(con)
            if (conLogger != null) {
                res = InterceptingSqlExecutor(res, LoggingSqlInterceptor(conLogger))
            }
            return res
        }
    }
}

private class ConnectionSqlExecutor(private val con: Connection): SqlExecutor() {
    override fun <T> connection(code: (Connection) -> T): T {
        val autoCommit = con.autoCommit
        val res = code(con)
        checkEquals(con.autoCommit, autoCommit)
        return res
    }

    override fun hasRealConnection() = true

    override fun execute(sql: String) {
        execute0 { con ->
            con.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
    }

    override fun execute(sql: String, preparator: SqlPreparator) {
        execute0 { con ->
            con.prepareStatement(sql).use { stmt ->
                val params = PreparedStatementParams.of(stmt)
                preparator.prepare(params)
                stmt.execute()
            }
        }
    }

    override fun executeUpdate(sql: String, preparator: SqlPreparator): Int {
        val res = execute0 { con ->
            con.prepareStatement(sql).use { stmt ->
                val params = PreparedStatementParams.of(stmt)
                preparator.prepare(params)
                stmt.executeUpdate()
            }
        }
        return res
    }

    override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) {
        execute0 { con ->
            con.prepareStatement(sql).use { stmt ->
                val params = PreparedStatementParams.of(stmt)
                preparator.prepare(params)
                stmt.executeQuery().use { rs ->
                    val rsRow = ResultSetRow.of(rs)
                    while (rs.next()) {
                        consumer(rsRow)
                    }
                }
            }
        }
    }

    private fun <T> execute0(code: (Connection) -> T): T {
        val autoCommit = con.autoCommit
        val res = code(con)
        checkEquals(con.autoCommit, autoCommit)
        return res
    }
}

interface SqlInterceptor {
    fun <T> invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> T,
    ): T

    fun invokeUpdate(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int,
    ): Int {
        return invoke(sql, attributes, preparator, code)
    }
}

class InterceptingSqlExecutor(
    private val sqlExec: SqlExecutor,
    private val interceptor: SqlInterceptor,
    private val attributes: Attributes = Attributes(),
): SqlExecutor() {
    override fun hasRealConnection() = sqlExec.hasRealConnection()

    override fun <T> connection(code: (Connection) -> T): T {
        val res = invoke(null, null) {
            sqlExec.connection(code)
        }
        return res
    }

    override fun execute(sql: String) {
        invoke(sql, null) {
            sqlExec.execute(sql)
        }
    }

    override fun execute(sql: String, preparator: SqlPreparator) {
        invoke(sql, preparator) { preparator2 ->
            sqlExec.execute(sql, preparator2!!)
        }
    }

    override fun executeUpdate(sql: String, preparator: SqlPreparator): Int {
        return interceptor.invokeUpdate(sql, attributes, preparator) { preparator2 ->
            sqlExec.executeUpdate(sql, preparator2!!)
        }
    }

    override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) {
        invoke(sql, preparator) { preparator2 ->
            sqlExec.executeQuery(sql, preparator2!!, consumer)
        }
    }

    private fun <T> invoke(sql: String?, preparator: SqlPreparator?, code: (SqlPreparator?) -> T): T {
        return interceptor.invoke(sql, attributes, preparator, code)
    }

    override fun withAttributes(attributes: Attributes): SqlExecutor {
        val sqlExec2 = sqlExec.withAttributes(attributes)
        return if (sqlExec2 === sqlExec && attributes == this.attributes) this else {
            InterceptingSqlExecutor(sqlExec2, interceptor, attributes)
        }
    }
}

private class LoggingSqlInterceptor(private val conLogger: SqlConnectionLogger): SqlInterceptor {
    override fun <T> invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> T,
    ): T {
        if (sql != null) {
            conLogger.log(sql)
        }
        return code(preparator)
    }
}
