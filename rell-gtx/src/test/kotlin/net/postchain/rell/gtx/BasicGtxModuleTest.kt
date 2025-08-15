/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GtxBuilder
import net.postchain.rell.base.utils.PostchainGtvUtils
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class BasicGtxModuleTest : ConfigFileBasedIntegrationTest() {
    private var blockchainRid: BlockchainRid? = null
    private val myCS = Secp256K1CryptoSystem()

    @Test fun testBuildBlock() {
        val node = setupNode()

        enqueueTx(node, makeTx_insertCity(0, "Hello"), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, makeTx_insertCity(0, "Hello"), -1)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)
    }

    @Test fun testOpErrWrongArgCount() {
        val node = setupNode()
        enqueueTx(node, makeTx(0, "insert_city", gtv("New York"), gtv("Foo")), 0)
        buildBlockAndCommit(node) //TODO check the error somehow
    }

    @Test fun testTryCallRecoversFromSQLError() {
        val node = setupNode()
        enqueueTx(node, makeTx(0, "try_insert_cities", gtv("Tbilisi"), gtv("Stockholm")), 0)
        buildBlockAndCommit(node)
        chkQuery(node, "get_all_city_names", gtv(mapOf()), gtv(listOf(gtv("Tbilisi"), gtv("Stockholm"))))
    }

    @Test fun testOpErrWrongArgType() {
        val node = setupNode()
        enqueueTx(node, makeTx(0, "insert_city", gtv(12345)), 0)
        buildBlockAndCommit(node) //TODO check the error somehow
    }

    @Test fun testOpErrRuntimeError() {
        val node = setupNode()
        enqueueTx(node, makeTx(0, "op_integer_division", gtv(123), gtv(0)), 0)
        buildBlockAndCommit(node) //TODO check the error somehow
    }

    @Test fun testOpErrNonexistentObjectId() {
        val node = setupNodeAndObjects()
        enqueueTx(node, makeTx_insertPerson(0, "James", 999, "Foo St", 1, 1000), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testOpErrObjectIdOfWrongEntity() {
        val node = setupNodeAndObjects()
        enqueueTx(node, makeTx_insertPerson(0, "James", 5, "Foo St", 1, 1000), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testQueryGetAllCities() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_all_cities", gtv(mapOf()), gtv(listOf(gtv(1), gtv(2), gtv(3))))
    }

    @Test fun testQueryGetAllCityNames() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_all_city_names", gtv(mapOf()), gtv(listOf(gtv("New York"), gtv("Los Angeles"), gtv("Seattle"))))
    }

    @Test fun testQueryGetPersonsByCity() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_persons_by_city", gtv("city" to gtv(1)), gtv(listOf(gtv(5))))
        chkQuery(node, "get_persons_by_city", gtv("city" to gtv(2)), gtv(listOf(gtv(4), gtv(6))))
        chkQuery(node, "get_persons_by_city", gtv("city" to gtv(3)), gtv(listOf()))
    }

    @Test fun testObject() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_state", gtv(mapOf()), gtv(5))

        enqueueTx(node, makeTx_setState(0, 33), 0)
        buildBlockAndCommit(node)

        chkQuery(node, "get_state", gtv(mapOf()), gtv(33))
    }

    private fun makeTx(ownerIdx: Int, opName: String, vararg opArgs: Gtv): ByteArray {
        val owner = KeyPairHelper.pubKey(ownerIdx)
        return GtxBuilder(blockchainRid!!, listOf(owner), myCS, PostchainGtvUtils.merkleHashCalculator)
            .addOperation(opName, *opArgs.toList().toTypedArray())
            .finish()
            .sign(myCS.buildSigMaker(KeyPair(owner, KeyPairHelper.privKey(ownerIdx))))
            .buildGtx()
            .encode()
    }

    private fun makeTx_insertCity(ownerIdx: Int, name: String): ByteArray {
        return makeTx(ownerIdx, "insert_city", gtv(name))
    }

    private fun makeTx_insertPerson(ownerIdx: Int, name: String, city: Long, street: String, house: Long, score: Long): ByteArray {
        return makeTx(ownerIdx, "insert_person", gtv(name), gtv(city), gtv(street), gtv(house), gtv(score))
    }

    private fun makeTx_setState(ownerIdx: Int, value: Long): ByteArray {
        return makeTx(ownerIdx, "set_state", gtv(value))
    }

    private fun setupNodeAndObjects(): PostchainTestNode {
        val node = setupNode()
        insertObjects(node)
        return node
    }

    private fun setupNode(): PostchainTestNode {
        val node = createNode(0, "/net/postchain/rell/base/basic/blockchain_config.xml")
        blockchainRid = node.getBlockchainRid(1L)
        return node
    }

    private fun insertObjects(node: PostchainTestNode) {
        enqueueTx(node, makeTx_insertCity(0, "New York"), 0)
        enqueueTx(node, makeTx_insertCity(0, "Los Angeles"), 0)
        enqueueTx(node, makeTx_insertCity(0, "Seattle"), 0)
        enqueueTx(node, makeTx_insertPerson(0, "Bob", 2, "Main St", 5, 100), 0)
        enqueueTx(node, makeTx_insertPerson(0, "Alice", 1, "Evergreen Ave", 11, 250), 0)
        enqueueTx(node, makeTx_insertPerson(0, "Trudy", 2, "Mulholland Dr", 3, 500), 0)
        buildBlockAndCommit(node)
    }

    private fun chkQuery(node: PostchainTestNode, name: String, args: Gtv, expected: Gtv) {
        val actual = callQuery(node, name, args)
        assertEquals(expected, actual)
    }

    private fun callQuery(node: PostchainTestNode, name: String, args: Gtv): Gtv {
        val blockQueries = node.getBlockchainInstance().blockchainEngine.getBlockQueries()
        return blockQueries.query(name, args).get()
    }

    @AfterTest override fun tearDown() {
        super.tearDown()
    }
}
