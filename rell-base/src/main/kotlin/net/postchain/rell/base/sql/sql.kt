/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.sql

import mu.KLogging
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.utils.One
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immSetOf
import net.postchain.rell.base.utils.plus
import org.apache.commons.lang3.mutable.MutableBoolean
import org.apache.commons.lang3.mutable.MutableInt
import org.apache.commons.lang3.mutable.MutableObject
import org.intellij.lang.annotations.Language
import org.jooq.tools.jdbc.MockConnection
import java.io.Closeable
import java.sql.Connection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SqlConstants {
    const val ROWID_COLUMN = "rowid"
    const val ROWID_GEN = "rowid_gen"
    const val MAKE_ROWID = "make_rowid"
    const val MAKE_ROWIDS = "make_rowids"

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
    const val FN_JSON_ARRAY_GET = "rell_json_array_get"
    const val FN_JSON_OBJECT_GET = "rell_json_object_get"
    const val FN_JSON_ARRAY_GET_OR_NULL = "rell_json_array_get_or_null"
    const val FN_JSON_AS_BOOLEAN_OR_NULL = "rell_json_as_boolean_or_null"
    const val FN_JSON_AS_INTEGER = "rell_json_as_integer"
    const val FN_JSON_AS_INTEGER_OR_NULL = "rell_json_as_integer_or_null"
    const val FN_JSON_AS_TEXT = "rell_json_as_text"
    const val FN_JSON_AS_TEXT_OR_NULL = "rell_json_as_text_or_null"
    const val FN_JSON_SIZE = "rell_json_size"

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

    val SYSTEM_OBJECTS = SYSTEM_OBJECTS_0 + SYSTEM_CHAIN_TABLES

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
        check(busy.compareAndSet(false, true)) { "SqlManager is busy" }
        try {
            val res = execute0(tx) { sqlExec ->
                SingleUseSqlExecutor(sqlExec, MutableBoolean(true)).use(code)
            }
            return res
        } finally {
            check(busy.compareAndSet(true, false))
        }
    }

    private class SingleUseSqlExecutor(
        private val sqlExec: SqlExecutor,
        private val valid: MutableBoolean,
    ): SqlExecutor(), Closeable {
        override fun <T> connection(code: (Connection) -> T): T {
            check(valid.get())
            return sqlExec.connection(code)
        }

        override fun hasRealConnection() = sqlExec.hasRealConnection()

        override fun execute(sql: String) {
            check(valid.get())
            sqlExec.execute(sql)
        }

        override fun execute(sql: String, preparator: SqlPreparator) {
            check(valid.get())
            sqlExec.execute(sql, preparator)
        }

        override fun executeUpdate(sql: String, preparator: SqlPreparator): Int {
            check(valid.get())
            return sqlExec.executeUpdate(sql, preparator)
        }

        override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) {
            check(valid.get())
            sqlExec.executeQuery(sql, preparator, consumer)
        }

        override fun withAttributes(attributes: Attributes): SqlExecutor {
            check(valid.get())
            val sqlExec2 = sqlExec.withAttributes(attributes)
            return if (sqlExec2 === sqlExec) this else SingleUseSqlExecutor(sqlExec2, valid)
        }

        override fun close() {
            check(valid.get())
            valid.setFalse()
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
    abstract fun execute(@Language("SQL") sql: String)
    abstract fun execute(@Language("SQL") sql: String, preparator: SqlPreparator)
    abstract fun executeUpdate(sql: String, preparator: SqlPreparator): Int
    abstract fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit)

    /**
     * A way to pass some context information to a logging layer (wrapper) without adding a new parameter to every
     * function. ATM the context has only the query mode, but might be also file pos, etc.
     * Not perfect; consider a better approach when needed.
     */
    open fun withAttributes(attributes: Attributes): SqlExecutor = this
}

class NoConnSqlManager: AbstractSqlManager() {
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

interface SqlManagerConnection {
    fun createExecutor(): SqlExecutor

    /** A transaction without an implicit commit or rollback at the end. */
    fun <T> transactionBody(code: () -> T): T
    fun commit()
    fun rollback()
    fun checkNoTx()

    companion object {
        fun create(con: Connection, sqlLog: Boolean = false): SqlManagerConnection {
            var res: SqlManagerConnection = JdbcSqlManagerConnection(con)
            val interceptor = ConnectionSqlManager.wrapSqlInterceptor(null, SqlConnectionLogger.getOrNull(sqlLog))
            res = InterceptingSqlManagerConnection.wrap(res, interceptor)
            return res
        }
    }
}

private class JdbcSqlManagerConnection(private val con: Connection): SqlManagerConnection {
    private var inTx = false

    override fun createExecutor(): SqlExecutor {
        return ConnectionSqlExecutor(con)
    }

    override fun <T> transactionBody(code: () -> T): T {
        check(!inTx)
        checkNoTx()
        try {
            inTx = true
            con.autoCommit = false
            val res = code()
            check(!con.autoCommit)
            return res
        } finally {
            inTx = false
            con.autoCommit = true
        }
    }

    override fun commit() {
        check(inTx)
        con.commit()
    }

    override fun rollback() {
        check(inTx)
        con.rollback()
    }

    override fun checkNoTx() {
        check(con.autoCommit)
    }
}

class InterceptingSqlManagerConnection(
    private val con: SqlManagerConnection,
    private val interceptor: SqlInterceptor,
): SqlManagerConnection {
    override fun createExecutor(): SqlExecutor {
        val sqlExec = con.createExecutor()
        return InterceptingSqlExecutor(sqlExec, interceptor)
    }

    override fun <T> transactionBody(code: () -> T): T {
        return con.transactionBody {
            invoke("BEGIN TRANSACTION") {}
            code()
        }
    }

    override fun commit() {
        invoke("COMMIT TRANSACTION") {
            con.commit()
        }
    }

    override fun rollback() {
        invoke("ROLLBACK TRANSACTION") {
            con.rollback()
        }
    }

    private fun invoke(@Language("SQL") sql: String, code: () -> Unit) {
        interceptor.invoke(sql, SqlExecutor.Attributes.DEFAULT, null) {
            code()
            null
        }
    }

    override fun checkNoTx() {
        con.checkNoTx()
    }

    companion object {
        fun wrap(con: SqlManagerConnection, interceptor: SqlInterceptor?): SqlManagerConnection {
            return if (interceptor == null) con else InterceptingSqlManagerConnection(con, interceptor)
        }
    }
}

class ConnectionSqlManager(private val con: SqlManagerConnection): AbstractSqlManager() {
    override val hasConnection = true

    private val sqlExec = con.createExecutor()

    override fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T {
        val res = if (tx) {
            transaction0(code)
        } else {
            access0(code)
        }
        return res
    }

    private fun <T> transaction0(code: (SqlExecutor) -> T): T {
        con.checkNoTx()
        return con.transactionBody {
            var rollback = true
            try {
                val res = code(sqlExec)
                con.commit()
                rollback = false
                res
            } finally {
                if (rollback) {
                    con.rollback()
                }
            }
        }
    }

    private fun <T> access0(code: (SqlExecutor) -> T): T {
        con.checkNoTx()
        val res = code(sqlExec)
        con.checkNoTx()
        return res
    }

    companion object {
        fun wrapSqlInterceptor(
            sqlInterceptor: SqlInterceptor?,
            conLogger: SqlConnectionLogger? = null,
        ): SqlInterceptor? {
            conLogger ?: return sqlInterceptor
            return SqlInterceptor.compound(LoggingSqlInterceptor(conLogger), sqlInterceptor)
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
    fun invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int?,
    ): Int?

    companion object {
        fun compound(first: SqlInterceptor, second: SqlInterceptor?): SqlInterceptor {
            return if (second == null) first else CompoundSqlInterceptor(first, second)
        }
    }
}

private class CompoundSqlInterceptor(
    private val first: SqlInterceptor,
    private val second: SqlInterceptor,
): SqlInterceptor {
    override fun invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int?,
    ): Int? {
        return first.invoke(sql, attributes, preparator) { preparator2 ->
            second.invoke(sql, attributes, preparator2, code)
        }
    }
}

class InterceptingSqlExecutor(
    private val sqlExec: SqlExecutor,
    private val interceptor: SqlInterceptor,
    private val attributes: Attributes = Attributes(),
): SqlExecutor() {
    override fun hasRealConnection() = sqlExec.hasRealConnection()

    override fun <T> connection(code: (Connection) -> T): T {
        val ref = MutableObject<One<T>?>()
        invoke(null, null) {
            val res = sqlExec.connection(code)
            ref.value = One(res)
            null
        }
        return ref.get()!!.value
    }

    override fun execute(sql: String) {
        invoke(sql, null) {
            sqlExec.execute(sql)
            null
        }
    }

    override fun execute(sql: String, preparator: SqlPreparator) {
        invoke(sql, preparator) { preparator2 ->
            sqlExec.execute(sql, preparator2!!)
            null
        }
    }

    override fun executeUpdate(sql: String, preparator: SqlPreparator): Int {
        return interceptor.invoke(sql, attributes, preparator) { preparator2 ->
            sqlExec.executeUpdate(sql, preparator2!!)
        }!!
    }

    override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) {
        invoke(sql, preparator) { preparator2 ->
            val rowCount = MutableInt()
            sqlExec.executeQuery(sql, preparator2!!) { row ->
                rowCount.increment()
                consumer(row)
            }
            rowCount.get().toInt()
        }
    }

    private fun invoke(sql: String?, preparator: SqlPreparator?, code: (SqlPreparator?) -> Int?) {
        interceptor.invoke(sql, attributes, preparator, code)
    }

    override fun withAttributes(attributes: Attributes): SqlExecutor {
        val sqlExec2 = sqlExec.withAttributes(attributes)
        return if (sqlExec2 === sqlExec && attributes == this.attributes) this else {
            InterceptingSqlExecutor(sqlExec2, interceptor, attributes)
        }
    }

    companion object {
        fun wrap(sqlExec: SqlExecutor, interceptor: SqlInterceptor?): SqlExecutor {
            return if (interceptor == null) sqlExec else InterceptingSqlExecutor(sqlExec, interceptor)
        }
    }
}

private class LoggingSqlInterceptor(private val conLogger: SqlConnectionLogger): SqlInterceptor {
    override fun invoke(
        sql: String?,
        attributes: SqlExecutor.Attributes,
        preparator: SqlPreparator?,
        code: (SqlPreparator?) -> Int?,
    ): Int? {
        if (sql != null) {
            conLogger.log(sql)
        }
        return code(preparator)
    }
}
