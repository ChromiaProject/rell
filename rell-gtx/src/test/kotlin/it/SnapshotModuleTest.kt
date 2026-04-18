/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.it

import net.postchain.base.BaseEContext
import net.postchain.base.data.PostgreSQLDatabaseAccess
import net.postchain.base.snapshot.BaseSnapshotDatumRepository
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.SnapshotDatumRepository
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.gtx.SnapshotAware
import net.postchain.rell.base.sql.ConnectionSqlManager
import net.postchain.rell.base.sql.SqlManagerConnection
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.base.utils.toImmList
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals

@Suppress("SameParameterValue")
class SnapshotModuleTest: BaseGtxIntegrationTest() {
    override val configPath = "/net/postchain/rell/base/basic/snapshot_config.xml"

    private val sqlCon = SqlTestUtils.createSimpleConnection("/config.properties")
    private val sqlMgr = ConnectionSqlManager(SqlManagerConnection.create(sqlCon))
    private val eCtx = BaseEContext(sqlCon, 1, PostgreSQLDatabaseAccess())

    @AfterTest fun after() {
        sqlCon.close()
    }

    @Test fun testCreate() {
        val node = setupNode()

        enqueueTx(node, txNewData(10, 123, "bob"), 0)
        buildBlockAndCommit(node)

        enqueueTx(node, txNewData(11, 456, "alice"), -1)
        buildBlockAndCommit(node)

        enqueueTx(node, txNewData(12, 789, "trudy"), -1)
        buildBlockAndCommit(node)

        restore(node)
        chkData(4, "2,10,123,bob", "3,11,456,alice", "4,12,789,trudy")
    }

    @Test fun testCreateWithDefaults() {
        val node = setupNode()

        // Create entity with all defaults - x defaults to -1, y defaults to 'n/a'
        enqueueTx(node, txNewDataDefaults(10), 0)
        buildBlockAndCommit(node)

        // Update to non-default values
        enqueueTx(node, txUpdateData(10, x = 999, y = "updated"), -1)
        buildBlockAndCommit(node)

        // Snapshot sync should restore the updated values, not the defaults
        restore(node)
        chkData(2, "2,10,999,updated")
    }

    @Test fun testCreateWithDefaultsThenNoUpdate() {
        val node = setupNode()

        // Create with defaults, then create another with explicit values
        enqueueTx(node, txNewDataDefaults(10), 0)
        enqueueTx(node, txNewData(11, 456, "alice"), -1)
        buildBlockAndCommit(node)

        // Snapshot sync - entity with defaults should keep default values
        restore(node)
        chkData(3, "2,10,-1,n/a", "3,11,456,alice")
    }

    @Test fun testCreateWithPartialDefaults() {
        val node = setupNode()

        // Create with explicit x but default y
        enqueueTx(node, txNewDataPartial(10, 777), 0)
        buildBlockAndCommit(node)

        // Update y to non-default
        enqueueTx(node, txUpdateData(10, y = "changed"), -1)
        buildBlockAndCommit(node)

        restore(node)
        chkData(2, "2,10,777,changed")
    }

    @Test fun testUpdate() {
        val node = setupNode()

        enqueueTx(node, txNewData(10, 123, "bob"), 0)
        enqueueTx(node, txNewData(11, 456, "alice"), -1)
        enqueueTx(node, txNewData(12, 789, "trudy"), -1)
        buildBlockAndCommit(node)

        enqueueTx(node, txUpdateData(11, x = 654), -1)
        enqueueTx(node, txUpdateData(12, y = "ydurt"), -1)
        buildBlockAndCommit(node)

        restore(node)
        chkData(4, "2,10,123,bob", "3,11,654,alice", "4,12,789,ydurt")
    }

    @Test fun testUpdateAt() {
        val node = setupNode()

        enqueueTx(node, txNewData(10, 123, "bob"), 0)
        enqueueTx(node, txNewData(11, 456, "alice"), -1)
        enqueueTx(node, txNewData(12, 789, "trudy"), -1)
        buildBlockAndCommit(node)

        enqueueTx(node, txUpdateDataAt(11, x = 654), -1)
        enqueueTx(node, txUpdateDataAt(12, y = "ydurt"), -1)
        buildBlockAndCommit(node)

        restore(node)
        chkData(4, "2,10,123,bob", "3,11,654,alice", "4,12,789,ydurt")
    }

    @Test fun testCreateWithDefaultsUpdateAt() {
        val node = setupNode()

        enqueueTx(node, txNewDataDefaults(10), 0)
        buildBlockAndCommit(node)

        enqueueTx(node, txUpdateDataAt(10, x = 999, y = "updated"), -1)
        buildBlockAndCommit(node)

        restore(node)
        chkData(2, "2,10,999,updated")
    }

    @Test fun testDelete() {
        val node = setupNode()

        enqueueTx(node, txNewData(10, 123, "bob"), 0)
        enqueueTx(node, txNewData(11, 456, "alice"), -1)
        enqueueTx(node, txNewData(12, 789, "trudy"), -1)
        buildBlockAndCommit(node)

        enqueueTx(node, txDeleteData(11), -1)
        buildBlockAndCommit(node)

        restore(node)
        chkData(4, "2,10,123,bob", "4,12,789,trudy")
    }

    @Test fun testObject() {
        val node = setupNode()

        enqueueTx(node, txUpdateState(a = 222), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, txUpdateState(b = "bar"), 0)
        buildBlockAndCommit(node)

        restore(node)
        chkState("1,222,bar")
    }

    @Test fun testForeignKey() {
        val node = setupNode()

        enqueueTx(node, txAddCompany("fb"), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, txAddUser("bob", "fb"), 0)
        buildBlockAndCommit(node)

        enqueueTx(node, txAddCompany("amazon"), -1)
        enqueueTx(node, txAddUser("alice", "amazon"), 0)
        buildBlockAndCommit(node)

        enqueueTx(node, txAddCompany("apple"), -1)
        enqueueTx(node, txAddUser("trudy", "apple"), 0)
        enqueueTx(node, txAddUser("bill", "amazon"), 0)
        enqueueTx(node, txAddUser("mike", "fb"), 0)
        buildBlockAndCommit(node)

        restore(node)
        chkTable("company", "2,fb", "4,amazon", "6,apple")
        chkTable("user", "3,2,bob", "5,4,alice", "7,6,trudy", "8,4,bill", "9,2,mike")
    }

    private fun chkData(expRowid: Int, vararg exp: String) {
        chkTable("rowid_gen", "$expRowid")
        chkTable("data", *exp)
    }

    private fun chkState(exp: String) {
        chkTable("state", exp)
    }

    private fun chkTable(table: String, vararg exp: String) {
        val allTablesData = SqlTestUtils.dumpDatabaseTables(sqlMgr)
        val tableData = allTablesData.getValue("c1.$table")
        assertEquals(exp.toList(), tableData)
    }

    private fun restore(node: PostchainTestNode) {
        sqlMgr.transaction {
            it.execute("""UPDATE "c1.rowid_gen" SET last_value = 0;""")
            it.execute("""UPDATE "c1.state" SET a = 111, b = 'foo';""")
            it.execute("""DELETE FROM "c1.data";""")
            it.execute("""DELETE FROM "c1.user";""")
            it.execute("""DELETE FROM "c1.company";""")
        }

        val module = node.getModules(1).filterIsInstance<SnapshotAware>().single()
        val datums = getAllDatums(module)
        val datums2 = datums.take(1) + datums.drop(1).reversed()

        module.initializeImport(eCtx)
        module.constructDatum(eCtx, datums2)
        module.finalizeImport(eCtx)
    }

    private fun getAllDatums(module: SnapshotAware): List<SnapshotDatum> {
        val repo = BaseSnapshotDatumRepository(listOf(module), 2, cryptoSystem)
        return getDatums0(repo, 0L)
    }

    private fun getDatums0(repo: SnapshotDatumRepository, startId: Long): List<SnapshotDatum> {
        val res = mutableListOf<SnapshotDatum>()
        while (true) {
            val fromId = res.lastOrNull()?.let { it.id + 1 } ?: startId
            val datums = repo.getDatums(eCtx, Long.MAX_VALUE, 0, fromId, Long.MAX_VALUE, Long.MAX_VALUE)
            if (datums.isEmpty()) {
                break
            }
            res.addAll(datums)
        }
        return res.toImmList()
    }

    private fun txNewData(k: Int, x: Long, y: String): ByteArray = makeTx("new_data", k, x, y)
    private fun txNewDataDefaults(k: Int): ByteArray = makeTx("new_data_defaults", k)
    private fun txNewDataPartial(k: Int, x: Int): ByteArray = makeTx("new_data_partial", k, x)
    private fun txUpdateData(k: Int, x: Int? = null, y: String? = null): ByteArray = makeTx("update_data", k, x, y)
    private fun txUpdateDataAt(k: Int, x: Int? = null, y: String? = null): ByteArray = makeTx("update_data_at", k, x, y)
    private fun txDeleteData(k: Int): ByteArray = makeTx("delete_data", k)
    private fun txUpdateState(a: Int? = null, b: String? = null): ByteArray = makeTx("update_state", a, b)
    private fun txAddCompany(name: String): ByteArray = makeTx("add_company", name)
    private fun txAddUser(name: String, company: String): ByteArray = makeTx("add_user", name, company)
}
