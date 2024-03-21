/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
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
import java.sql.PreparedStatement
import java.sql.ResultSet
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

class SqlConnectionLogger(private val logging: Boolean) {
    private val conId = idCounter.getAndIncrement()

    fun log(s: String) {
        if (logging) logger.info("[{}] {}", conId, s)
    }

    companion object: KLogging() {
        private val idCounter = AtomicLong()
    }
}

abstract class SqlManager {
    abstract val hasConnection: Boolean

    private val busy = AtomicBoolean()

    protected abstract fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T

    fun <T> transaction(code: (SqlExecutor) -> T): T = execute(true, code)
    fun <T> access(code: (SqlExecutor) -> T): T = execute(false, code)

    fun <T> execute(tx: Boolean, code: (SqlExecutor) -> T): T {
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

        override fun execute(sql: String, preparator: (PreparedStatement) -> Unit) {
            check(valid)
            sqlExec.execute(sql, preparator)
        }

        override fun executeUpdate(sql: String, preparator: (PreparedStatement) -> Unit): Int {
            check(valid)
            return sqlExec.executeUpdate(sql, preparator)
        }

        override fun executeQuery(sql: String, preparator: (PreparedStatement) -> Unit, consumer: (ResultSet) -> Unit) {
            check(valid)
            sqlExec.executeQuery(sql, preparator, consumer)
        }

        override fun close() {
            check(valid)
            valid = false
        }
    }
}

abstract class SqlExecutor {
    abstract fun <T> connection(code: (Connection) -> T): T
    abstract fun hasRealConnection(): Boolean
    abstract fun execute(sql: String)
    abstract fun execute(sql: String, preparator: (PreparedStatement) -> Unit)
    abstract fun executeUpdate(sql: String, preparator: (PreparedStatement) -> Unit): Int
    abstract fun executeQuery(sql: String, preparator: (PreparedStatement) -> Unit, consumer: (ResultSet) -> Unit)
}

object NoConnSqlManager: SqlManager() {
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
    override fun execute(sql: String, preparator: (PreparedStatement) -> Unit) = throw err()
    override fun executeUpdate(sql: String, preparator: (PreparedStatement) -> Unit) = throw err()
    override fun executeQuery(sql: String, preparator: (PreparedStatement) -> Unit, consumer: (ResultSet) -> Unit) = throw err()

    private fun err() = Rt_Exception.common("no_sql", "No database connection")
}

class ConnectionSqlManager(private val con: Connection, logging: Boolean): SqlManager() {
    override val hasConnection = true

    private val conLogger = SqlConnectionLogger(logging)
    private val sqlExec = ConnectionSqlExecutor(con, conLogger)

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
                conLogger.log("BEGIN TRANSACTION")
                val res = code(sqlExec)
                conLogger.log("COMMIT TRANSACTION")
                con.commit()
                rollback = false
                return res
            } finally {
                if (rollback) {
                    conLogger.log("ROLLBACK TRANSACTION")
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
}

class ConnectionSqlExecutor(private val con: Connection, private val conLogger: SqlConnectionLogger): SqlExecutor() {
    constructor(con: Connection, logging: Boolean = true): this(con, SqlConnectionLogger(logging))

    override fun <T> connection(code: (Connection) -> T): T {
        val autoCommit = con.autoCommit
        val res = code(con)
        checkEquals(con.autoCommit, autoCommit)
        return res
    }

    override fun hasRealConnection() = true

    override fun execute(sql: String) {
        execute0(sql) { con ->
            con.createStatement().use { stmt ->
                stmt.execute(sql)
            }
        }
    }

    override fun execute(sql: String, preparator: (PreparedStatement) -> Unit) {
        execute0(sql) { con ->
            con.prepareStatement(sql).use { stmt ->
                preparator(stmt)
                stmt.execute()
            }
        }
    }

    override fun executeUpdate(sql: String, preparator: (PreparedStatement) -> Unit): Int {
        val res = execute0(sql) { con ->
            con.prepareStatement(sql).use { stmt ->
                preparator(stmt)
                stmt.executeUpdate()
            }
        }
        return res
    }

    override fun executeQuery(sql: String, preparator: (PreparedStatement) -> Unit, consumer: (ResultSet) -> Unit) {
        execute0(sql) { con ->
            con.prepareStatement(sql).use { stmt ->
                preparator(stmt)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        consumer(rs)
                    }
                }
            }
        }
    }

    private fun <T> execute0(sql: String, code: (Connection) -> T): T {
        conLogger.log(sql)
        val autoCommit = con.autoCommit
        val res = code(con)
        checkEquals(con.autoCommit, autoCommit)
        return res
    }
}
