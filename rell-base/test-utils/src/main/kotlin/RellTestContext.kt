/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
@file:OptIn(RawSqlAccess::class)

package net.postchain.rell.base.testutils

import net.postchain.common.BlockchainRid
import net.postchain.rell.base.runtime.RawSqlStatement
import net.postchain.rell.base.runtime.utils.Rt_SqlManagerUtils
import net.postchain.rell.base.sql.*
import java.sql.Connection
import java.util.*
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.fail

class RellTestContext(
    val projExt: RellTestProjExt = BaseRellTestProjExt,
    useSql: Boolean = true,
): AutoCloseable {
    class BlockBuilder(private val chainId: Long) {
        private val list = mutableListOf<String>()

        fun list() = list.toList()

        fun block(iid: Long, height: Long, rid: String?, timestamp: Long?): BlockBuilder {
            val ridStr = if (rid == null) "NULL" else """E'\\x$rid'"""
            val timestampStr = if (timestamp == null) "NULL" else """$timestamp"""
            val s = """INSERT INTO "c$chainId.blocks"(block_iid,block_height,block_rid,timestamp)
                VALUES($iid,$height,$ridStr,$timestampStr);"""
            list.add(s)
            return this
        }

        fun tx(iid: Long, block: Long, rid: String, data: String, hash: String): BlockBuilder {
            val txNumber = list.size
            val s = """INSERT INTO "c$chainId.transactions"(tx_iid,tx_rid,tx_data,tx_hash,tx_number,block_iid)
                VALUES($iid,E'\\x$rid',E'\\x$data',E'\\x$hash',$txNumber,$block);"""
            list.add(s)
            return this
        }
    }

    private var inited = false
    private var destroyed = false
    private var sqlResource: Connection? = null
    private var sqlMgrHolder: SqlMgrHolder? = null
    private val sqlStats = TestSqlStats()

    var sqlLogging = false

    /**
     * Optional user-supplied [SqlInterceptor] composed onto the outer SqlManager. Used by tests that need to inspect
     * every SQL string and binding sent to the database.
     */
    var customSqlInterceptor: SqlInterceptor? = null
        set(value) {
            checkNotInited()
            field = value
        }

    var useSql: Boolean = useSql
        set(value) {
            checkNotInited()
            field = value
        }

    private val blockchains = mutableMapOf<Long, BlockchainRid>()
    private val inserts = mutableListOf<String>()

    fun init() {
        if (inited) return
        if (useSql) {
            initSql()
        }
        inited = true
    }

    @Suppress("ConvertTryFinallyToUseCall")
    private fun initSql() {
        val conn = SqlTestUtils.createIsolatedSchemaConnection()
        var closeable: Connection? = conn

        try {
            conn.autoCommit = true

            val sqlMgrHolder = createSqlManager(conn)

            sqlMgrHolder.innerMgr.transaction { sqlExec ->
                SqlUtils.dropAll(sqlExec, true)

                projExt.initSysAppTables(sqlExec)
                initSqlInsertBlockchains(sqlExec)

                if (inserts.isNotEmpty()) {
                    val insertsSql = inserts.joinToString("\n")
                    sqlExec.execute(RawSqlStatement(insertsSql))
                }
            }

            this.sqlMgrHolder = sqlMgrHolder
            sqlResource = conn
            closeable = null
        } finally {
            closeable?.close()
        }
    }

    private fun createSqlManager(con: Connection): SqlMgrHolder {
        var sqlCon = SqlManagerConnection.create(con, sqlLogging)
        val innerMgr = ConnectionSqlManager(sqlCon)
        sqlCon = InterceptingSqlManagerConnection.wrap(sqlCon, StatsSqlInterceptor(sqlStats))

        val interceptor = Rt_SqlManagerUtils.wrapSqlInterceptor(null, logErrors = false)
        sqlCon = InterceptingSqlManagerConnection.wrap(sqlCon, interceptor)
        sqlCon = InterceptingSqlManagerConnection.wrap(sqlCon, customSqlInterceptor)
        val outerMgr = ConnectionSqlManager(sqlCon)

        return SqlMgrHolder(innerMgr, outerMgr)
    }

    private fun initSqlInsertBlockchains(sqlExecLoc: SqlExecutor) {
        if (blockchains.isEmpty()) return

        val inserts = blockchains.entries.map { ( chainId, rid ) ->
            val ridStr = rid.toHex()
            """INSERT INTO blockchains(chain_iid, blockchain_rid) VALUES ($chainId, E'\\x$ridStr');"""
        }

        val insertSql = inserts.joinToString("\n") { it }
        sqlExecLoc.execute(RawSqlStatement(insertSql))
    }

    private fun checkNotInited() {
        check(!inited)
    }

    fun blockchain(chainId: Long, rid: String) {
        checkNotInited()
        val bcRid = RellTestUtils.strToBlockchainRid(rid)
        check(chainId !in blockchains)
        blockchains[chainId] = bcRid
    }

    fun insert(table: String, columns: String, values: String) {
        checkNotInited()
        val ins = SqlTestUtils.mkins(table, columns, values)
        inserts += listOf(ins)
    }

    fun insert(inserts: List<String>) {
        checkNotInited()
        this.inserts += inserts
    }

    override fun close() {
        if (!inited || destroyed) return
        sqlResource?.close()
        sqlResource = null
        destroyed = true
    }

    fun sqlMgr(): SqlManager {
        init()
        return if (useSql) sqlMgrHolder!!.outerMgr else NoConnSqlManager()
    }

    fun innerSqlMgr(): SqlManager {
        init()
        return if (useSql) sqlMgrHolder!!.innerMgr else NoConnSqlManager()
    }

    fun resetSqlBuffer() {
        sqlStats.sqls.clear()
    }

    fun chkSql(expected: List<String>) {
        assertEquals(expected, sqlStats.sqls.toList())
        sqlStats.sqls.clear()
    }

    fun chkSqlRegex(expected: List<String>) {
        val actual = sqlStats.sqls.toList()
        val match = actual.size == expected.size && actual.withIndex().all { (i, act) ->
            Pattern.matches(expected[i], act)
        }

        if (!match) {
            assertEquals(expected, actual) // Throw with a message
            fail() // Make sure it fails
        }

        sqlStats.sqls.clear()
    }

    fun chkSqlCtr(expected: Int) {
        assertEquals(expected, sqlStats.sqls.size)
        sqlStats.sqls.clear()
    }

    private class TestSqlStats {
        val sqls: Queue<String> = ArrayDeque()
    }

    private class StatsSqlInterceptor(private val stats: TestSqlStats): SqlInterceptor {
        override fun invoke(
            sql: String?,
            attributes: SqlExecutor.Attributes,
            preparator: SqlPreparator?,
            code: (SqlPreparator?) -> Int?,
        ): Int? {
            if (sql != null && sql !in TRANSACTION_CONTROL_SQLS) stats.sqls.add(sql)
            return code(preparator)
        }

        companion object {
            private val TRANSACTION_CONTROL_SQLS = setOf("BEGIN TRANSACTION", "COMMIT TRANSACTION", "ROLLBACK TRANSACTION")
        }
    }

    private class SqlMgrHolder(val innerMgr: SqlManager, val outerMgr: SqlManager)
}
