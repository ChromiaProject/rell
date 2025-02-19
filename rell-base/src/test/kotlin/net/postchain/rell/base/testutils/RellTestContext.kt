/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.common.BlockchainRid
import net.postchain.rell.base.runtime.utils.Rt_SqlManagerUtils
import net.postchain.rell.base.sql.*
import java.io.Closeable
import java.sql.Connection
import java.util.*
import kotlin.test.assertEquals

class RellTestContext(
    val projExt: RellTestProjExt = BaseRellTestProjExt,
    useSql: Boolean = true,
): Closeable {
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
    private var sqlResource: AutoCloseable? = null
    private var sqlMgrHolder: SqlMgrHolder? = null
    private val sqlStats = TestSqlStats()

    var sqlLogging = false

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

    private fun initSql() {
        val conn = SqlTestUtils.createSqlConnection()
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
                    sqlExec.execute(insertsSql)
                }
            }

            this.sqlMgrHolder = sqlMgrHolder
            sqlResource = conn
            closeable = null
        } finally {
            closeable?.close()
        }
    }

    private fun createSqlManager(conn: Connection): SqlMgrHolder {
        val innerMgr = ConnectionSqlManager(conn, sqlLogging)
        val outerMgr = Rt_SqlManagerUtils.makeSqlManager(TestSqlManager(innerMgr, sqlStats), logErrors = false)
        return SqlMgrHolder(innerMgr, outerMgr)
    }

    private fun initSqlInsertBlockchains(sqlExecLoc: SqlExecutor) {
        if (blockchains.isEmpty()) return

        val inserts = blockchains.entries.map { ( chainId, rid ) ->
            val ridStr = rid.toHex()
            """INSERT INTO blockchains(chain_iid, blockchain_rid) VALUES ($chainId, E'\\x$ridStr');"""
        }

        val insertSql = inserts.joinToString("\n") { it }
        sqlExecLoc.execute(insertSql)
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
        return if (useSql) sqlMgrHolder!!.outerMgr else NoConnSqlManager
    }

    fun innerSqlMgr(): SqlManager {
        init()
        return if (useSql) sqlMgrHolder!!.innerMgr else NoConnSqlManager
    }

    fun resetSqlBuffer() {
        sqlStats.sqls.clear()
    }

    fun chkSql(expected: List<String>) {
        assertEquals(expected, sqlStats.sqls.toList())
        sqlStats.sqls.clear()
    }

    fun chkSqlCtr(expected: Int) {
        assertEquals(expected, sqlStats.sqls.size)
        sqlStats.sqls.clear()
    }

    private class TestSqlStats {
        val sqls: Queue<String> = ArrayDeque()
    }

    private class TestSqlManager(private val mgr: SqlManager, private val stats: TestSqlStats): AbstractSqlManager() {
        override val hasConnection = mgr.hasConnection

        override fun <T> execute0(tx: Boolean, code: (SqlExecutor) -> T): T {
            return mgr.execute(tx) { sqlExec ->
                code(TestSqlExecutor(sqlExec))
            }
        }

        private inner class TestSqlExecutor(private val exec: SqlExecutor): SqlExecutor() {
            override fun <T> connection(code: (Connection) -> T): T = exec.connection(code)
            override fun hasRealConnection() = exec.hasRealConnection()

            override fun execute(sql: String) {
                stats.sqls.add(sql)
                exec.execute(sql)
            }

            override fun execute(sql: String, preparator: SqlPreparator) {
                stats.sqls.add(sql)
                exec.execute(sql, preparator)
            }

            override fun executeUpdate(sql: String, preparator: SqlPreparator): Int {
                stats.sqls.add(sql)
                return exec.executeUpdate(sql, preparator)
            }

            override fun executeQuery(sql: String, preparator: SqlPreparator, consumer: (ResultSetRow) -> Unit) {
                stats.sqls.add(sql)
                exec.executeQuery(sql, preparator, consumer)
            }
        }
    }

    private class SqlMgrHolder(val innerMgr: SqlManager, val outerMgr: SqlManager)
}
