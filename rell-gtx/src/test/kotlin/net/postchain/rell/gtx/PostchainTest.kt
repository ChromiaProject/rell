/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.base.BaseEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.gtx.StandardOpsGTXModule
import net.postchain.rell.base.compiler.base.utils.C_ReservedMountNames
import net.postchain.rell.base.model.R_MountName
import net.postchain.rell.base.sql.SqlExecutor
import net.postchain.rell.base.sql.SqlUtils
import net.postchain.rell.base.testutils.BaseResourcefulTest
import net.postchain.rell.base.testutils.RellTestUtils
import net.postchain.rell.base.testutils.SqlTestUtils
import net.postchain.rell.gtx.testutils.RellGtxTester
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class PostchainTest: BaseResourcefulTest() {
    @Test fun testInitializeApp() {
        val con = resource(SqlTestUtils.createIsolatedSchemaConnection())
        SqlUtils.dropAll(SqlTestUtils.createSqlExecutor(con), true)
        chkTables(con, "")

        PostchainBaseUtils.initializeApp(sqlAccess(), con)

        chkTables(con,
            "blockchain_replicas(blockchain_rid:bytea,node:bytea)",
            "blockchains(blockchain_rid:bytea,chain_iid:int8)",
            "containers(container_iid:serial,name:text)",
            "meta(key:text,value:text)",
            "must_sync_until(block_height:int8,chain_iid:int8)",
            "peerinfos(host:text,port:int4,pub_key:bytea,timestamp:timestamp)",
            "snapshot_sync_context_state(chain_iid:int8,context_id:int8,datum_id_offset:int8,max_datum_id:int8,root_hash:bytea)",
            "snapshot_sync_state(chain_iid:int8,height:int8,root_hash:bytea)",
        )

        PostchainBaseUtils.initializeApp(sqlAccess(), con)

        chkTables(con,
            "blockchain_replicas(blockchain_rid:bytea,node:bytea)",
            "blockchains(blockchain_rid:bytea,chain_iid:int8)",
            "containers(container_iid:serial,name:text)",
            "meta(key:text,value:text)",
            "must_sync_until(block_height:int8,chain_iid:int8)",
            "peerinfos(host:text,port:int4,pub_key:bytea,timestamp:timestamp)",
            "snapshot_sync_context_state(chain_iid:int8,context_id:int8,datum_id_offset:int8,max_datum_id:int8,root_hash:bytea)",
            "snapshot_sync_state(chain_iid:int8,height:int8,root_hash:bytea)",
        )
    }

    @Test fun testInitializeBlockchain() {
        val con = resource(SqlTestUtils.createIsolatedSchemaConnection())
        SqlUtils.dropAll(SqlTestUtils.createSqlExecutor(con), true)
        val sa = sqlAccess()
        PostchainBaseUtils.initializeApp(sa, con)

        chkTables(con,
            "blockchain_replicas(blockchain_rid:bytea,node:bytea)",
            "blockchains(blockchain_rid:bytea,chain_iid:int8)",
            "containers(container_iid:serial,name:text)",
            "meta(key:text,value:text)",
            "must_sync_until(block_height:int8,chain_iid:int8)",
            "peerinfos(host:text,port:int4,pub_key:bytea,timestamp:timestamp)",
            "snapshot_sync_context_state(chain_iid:int8,context_id:int8,datum_id_offset:int8,max_datum_id:int8,root_hash:bytea)",
            "snapshot_sync_state(chain_iid:int8,height:int8,root_hash:bytea)",
        )

        val bcRid1 = RellTestUtils.strToBlockchainRid("CEED")
        sa.initializeBlockchain(BaseEContext(con, 123L, sa), bcRid1)

        chkTables(con,
            "blockchain_replicas(blockchain_rid:bytea,node:bytea)",
            "blockchains(blockchain_rid:bytea,chain_iid:int8)",
            "c123.blocks(block_header_data:bytea,block_height:int8,block_iid:bigserial,block_rid:bytea,block_witness:bytea,timestamp:int8)",
            "c123.configurations(configuration_data:bytea,configuration_hash:bytea,height:int8,merkle_hash_version:int8)",
            "c123.sys.faulty_configuration(configuration_hash:bytea,report_height:int8)",
            "c123.sys.snapshot_contexts(context_id:int8,context_name:varchar)",
            "c123.sys.snapshot_updated_datum(context_id:int8,datum:bytea,datum_hash:bytea,datum_id:int8)",
            "c123.sys.transaction_signers(signer:bytea,tx_iid:int8)",
            "c123.transactions(block_iid:int8,tx_data:bytea,tx_hash:bytea,tx_iid:bigserial,tx_number:int8,tx_rid:bytea)",
            "containers(container_iid:serial,name:text)",
            "meta(key:text,value:text)",
            "must_sync_until(block_height:int8,chain_iid:int8)",
            "peerinfos(host:text,port:int4,pub_key:bytea,timestamp:timestamp)",
            "snapshot_sync_context_state(chain_iid:int8,context_id:int8,datum_id_offset:int8,max_datum_id:int8,root_hash:bytea)",
            "snapshot_sync_state(chain_iid:int8,height:int8,root_hash:bytea)",
        )

        PostchainBaseUtils.initializeApp(sa, con)

        chkTables(con,
            "blockchain_replicas(blockchain_rid:bytea,node:bytea)",
            "blockchains(blockchain_rid:bytea,chain_iid:int8)",
            "c123.blocks(block_header_data:bytea,block_height:int8,block_iid:bigserial,block_rid:bytea,block_witness:bytea,timestamp:int8)",
            "c123.configurations(configuration_data:bytea,configuration_hash:bytea,height:int8,merkle_hash_version:int8)",
            "c123.sys.faulty_configuration(configuration_hash:bytea,report_height:int8)",
            "c123.sys.snapshot_contexts(context_id:int8,context_name:varchar)",
            "c123.sys.snapshot_updated_datum(context_id:int8,datum:bytea,datum_hash:bytea,datum_id:int8)",
            "c123.sys.transaction_signers(signer:bytea,tx_iid:int8)",
            "c123.transactions(block_iid:int8,tx_data:bytea,tx_hash:bytea,tx_iid:bigserial,tx_number:int8,tx_rid:bytea)",
            "containers(container_iid:serial,name:text)",
            "meta(key:text,value:text)",
            "must_sync_until(block_height:int8,chain_iid:int8)",
            "peerinfos(host:text,port:int4,pub_key:bytea,timestamp:timestamp)",
            "snapshot_sync_context_state(chain_iid:int8,context_id:int8,datum_id_offset:int8,max_datum_id:int8,root_hash:bytea)",
            "snapshot_sync_state(chain_iid:int8,height:int8,root_hash:bytea)",
        )

        val bcRid2 = RellTestUtils.strToBlockchainRid("FEED")
        sa.initializeBlockchain(BaseEContext(con, 456L, sa), bcRid2)

        chkTables(con,
            "blockchain_replicas(blockchain_rid:bytea,node:bytea)",
            "blockchains(blockchain_rid:bytea,chain_iid:int8)",
            "c123.blocks(block_header_data:bytea,block_height:int8,block_iid:bigserial,block_rid:bytea,block_witness:bytea,timestamp:int8)",
            "c123.configurations(configuration_data:bytea,configuration_hash:bytea,height:int8,merkle_hash_version:int8)",
            "c123.sys.faulty_configuration(configuration_hash:bytea,report_height:int8)",
            "c123.sys.snapshot_contexts(context_id:int8,context_name:varchar)",
            "c123.sys.snapshot_updated_datum(context_id:int8,datum:bytea,datum_hash:bytea,datum_id:int8)",
            "c123.sys.transaction_signers(signer:bytea,tx_iid:int8)",
            "c123.transactions(block_iid:int8,tx_data:bytea,tx_hash:bytea,tx_iid:bigserial,tx_number:int8,tx_rid:bytea)",
            "c456.blocks(block_header_data:bytea,block_height:int8,block_iid:bigserial,block_rid:bytea,block_witness:bytea,timestamp:int8)",
            "c456.configurations(configuration_data:bytea,configuration_hash:bytea,height:int8,merkle_hash_version:int8)",
            "c456.sys.faulty_configuration(configuration_hash:bytea,report_height:int8)",
            "c456.sys.snapshot_contexts(context_id:int8,context_name:varchar)",
            "c456.sys.snapshot_updated_datum(context_id:int8,datum:bytea,datum_hash:bytea,datum_id:int8)",
            "c456.sys.transaction_signers(signer:bytea,tx_iid:int8)",
            "c456.transactions(block_iid:int8,tx_data:bytea,tx_hash:bytea,tx_iid:bigserial,tx_number:int8,tx_rid:bytea)",
            "containers(container_iid:serial,name:text)",
            "meta(key:text,value:text)",
            "must_sync_until(block_height:int8,chain_iid:int8)",
            "peerinfos(host:text,port:int4,pub_key:bytea,timestamp:timestamp)",
            "snapshot_sync_context_state(chain_iid:int8,context_id:int8,datum_id_offset:int8,max_datum_id:int8,root_hash:bytea)",
            "snapshot_sync_state(chain_iid:int8,height:int8,root_hash:bytea)",
        )
    }

    /** Check that test versions of Postchain tables don't differ (much) from the original tables. */
    @Test fun testCreateSysAppTables() {
        val con = resource(SqlTestUtils.createIsolatedSchemaConnection())

        val rellTables = createAndDumpTables(con) { sqlExec ->
            SqlTestUtils.createSysAppTables(sqlExec)
        }

        val postchainTables = createAndDumpTables(con) {
            val sqlAccess = RellGtxTester.createDatabaseAccess()
            PostchainBaseUtils.initializeApp(sqlAccess, con)
        }

        val ignoredTables = listOf(
            "blockchain_replicas",
            "containers",
            "meta",
            "must_sync_until",
            "peerinfos",
            "snapshot_sync_context_state",
            "snapshot_sync_state",
        )

        chkRellPostchainTables(rellTables, postchainTables, "", ignoredTables)
    }

    /** Check that test versions of Postchain tables don't differ (much) from the original tables. */
    @Test fun testCreateSysBlockchainTables() {
        val con = resource(SqlTestUtils.createIsolatedSchemaConnection())
        val chainId = 0L

        val rellTables = createAndDumpTables(con) { sqlExec ->
            SqlTestUtils.createSysBlockchainTables(sqlExec, chainId)
        }

        val postchainTables = createAndDumpTables(con) {
            val sqlAccess = RellGtxTester.createDatabaseAccess()
            PostchainBaseUtils.initializeApp(sqlAccess, con)

            val blockchainRid = BlockchainRid(RellTestUtils.strToRidHex("DEADBEEF").hexStringToByteArray())
            val eCtx: EContext = BaseEContext(con, chainId, sqlAccess)
            sqlAccess.initializeBlockchain(eCtx, blockchainRid)
        }

        val ignoredTables = listOf(
            "c0.configurations",
            "c0.sys.faulty_configuration",
            "c0.sys.snapshot_contexts",
            "c0.sys.snapshot_updated_datum",
            "c0.sys.transaction_signers"
        )

        chkRellPostchainTables(rellTables, postchainTables, "c0.", ignoredTables)
    }

    private fun chkRellPostchainTables(
        rellTables: Map<String, Map<String, String>>,
        postchainTables: Map<String, Map<String, String>>,
        tablePrefix: String,
        ignoredTables: List<String>,
    ) {
        // Remove ignored tables and columns that don't exist in Rell tables.
        val postchainTables2 = postchainTables
            .filterKeys { it.startsWith(tablePrefix) && it !in ignoredTables }
            .mapValues { (table, cols) ->
                val rellCols = rellTables.getValue(table)
                cols.filterKeys { it in rellCols }
            }

        val actual = tablesToString(rellTables)
        val expected = tablesToString(postchainTables2)
        assertEquals(expected, actual)
    }

    private fun createAndDumpTables(con: Connection, code: (SqlExecutor) -> Unit): Map<String, Map<String, String>> {
        val sqlExec: SqlExecutor = SqlTestUtils.createSqlExecutor(con)
        SqlUtils.dropAll(sqlExec, true)
        chkTables(con, "")
        code(sqlExec)
        return SqlTestUtils.dumpTablesStructure(con, true)
    }

    private fun chkTables(con: Connection, vararg expectedTables: String) {
        val dump = SqlTestUtils.dumpTablesStructure(con, true)
        val actual = tablesToString(dump)
        val expected = expectedTables.joinToString(" ")
        assertEquals(expected, actual)
    }

    private fun tablesToString(dump: Map<String, Map<String, String>>): String {
        return dump
            .mapValues { (_, v) -> v.entries.joinToString(",") { it.key + ":" + it.value } }
            .entries.joinToString(" ") { (k, v) -> "$k($v)" }
    }

    // When this test fails, update the hard-coded list of operations.
    @Test fun testReservedOperations() {
        val m = StandardOpsGTXModule()
        chkReservedMounts(C_ReservedMountNames.OPERATIONS, m.getOperations())
    }

    // When this test fails, update the hard-coded list of queries.
    @Test fun testReservedQueries() {
        val m = StandardOpsGTXModule()
        chkReservedMounts(C_ReservedMountNames.QUERIES, m.getQueries())
    }

    private fun chkReservedMounts(actual: Set<R_MountName>, expected: Set<String>) {
        val rExpected = expected.map { R_MountName.of(it) }.sorted()
        assertEquals(rExpected, actual.sorted())
    }

    private fun sqlAccess(): DatabaseAccess = RellGtxTester.createDatabaseAccess()
}
