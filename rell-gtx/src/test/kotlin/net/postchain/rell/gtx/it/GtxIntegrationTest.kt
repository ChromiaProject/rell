/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.it

import net.postchain.concurrent.util.get
import net.postchain.devtools.PostchainTestNode
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.api.nativ.RellNativeEnvironment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("SameParameterValue")
class GtxIntegrationTest: BaseGtxIntegrationTest() {
    override val configPath = "/net/postchain/rell/base/basic/blockchain_config.xml"

    @Test fun testBuildBlock() {
        val node = setupNode()

        enqueueTx(node, makeTx_insertCity("Hello"), 0)
        buildBlockAndCommit(node)
        enqueueTx(node, makeTx_insertCity("Hello"), -1)
        buildBlockAndCommit(node)

        verifyBlockchainTransactions(node)
    }

    @Test fun testOpErrWrongArgCount() {
        val node = setupNode()
        enqueueTx(node, makeTxGtv("insert_city", GtvFactory.gtv("New York"), GtvFactory.gtv("Foo")), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testTryCallRecoversFromSQLError() {
        val node = setupNode()
        enqueueTx(node, makeTxGtv("try_insert_cities", GtvFactory.gtv("Tbilisi"), GtvFactory.gtv("Stockholm")), 0)
        buildBlockAndCommit(node)
        chkQuery(node, "get_all_city_names",
            GtvFactory.gtv(mapOf()),
            GtvFactory.gtv(listOf(GtvFactory.gtv("Tbilisi"), GtvFactory.gtv("Stockholm")))
        )
    }

    @Test fun testOpErrWrongArgType() {
        val node = setupNode()
        enqueueTx(node, makeTxGtv("insert_city", GtvFactory.gtv(12345)), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testOpErrRuntimeError() {
        val node = setupNode()
        enqueueTx(node, makeTxGtv("op_integer_division", GtvFactory.gtv(123), GtvFactory.gtv(0)), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testOpErrNonexistentObjectId() {
        val node = setupNodeAndObjects()
        enqueueTx(node, makeTx_insertPerson("James", 999, "Foo St", 1, 1000), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testOpErrObjectIdOfWrongEntity() {
        val node = setupNodeAndObjects()
        enqueueTx(node, makeTx_insertPerson("James", 5, "Foo St", 1, 1000), 0)
        buildBlockAndCommit(node)
    }

    @Test fun testQueryGetAllCities() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_all_cities",
            GtvFactory.gtv(mapOf()),
            GtvFactory.gtv(listOf(GtvFactory.gtv(1), GtvFactory.gtv(2), GtvFactory.gtv(3)))
        )
    }

    @Test fun testQueryGetAllCityNames() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_all_city_names",
            GtvFactory.gtv(mapOf()),
            GtvFactory.gtv(listOf(GtvFactory.gtv("New York"), GtvFactory.gtv("Los Angeles"), GtvFactory.gtv("Seattle")))
        )
    }

    @Test fun testQueryGetPersonsByCity() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_persons_by_city",
            GtvFactory.gtv("city" to GtvFactory.gtv(1)),
            GtvFactory.gtv(listOf(GtvFactory.gtv(5)))
        )
        chkQuery(node, "get_persons_by_city",
            GtvFactory.gtv("city" to GtvFactory.gtv(2)),
            GtvFactory.gtv(listOf(GtvFactory.gtv(4), GtvFactory.gtv(6)))
        )
        chkQuery(node, "get_persons_by_city", GtvFactory.gtv("city" to GtvFactory.gtv(3)), GtvFactory.gtv(listOf()))
    }

    @Test fun testObject() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_state", GtvFactory.gtv(mapOf()), GtvFactory.gtv(5))

        enqueueTx(node, makeTx_setState(33), 0)
        buildBlockAndCommit(node)

        chkQuery(node, "get_state", GtvFactory.gtv(mapOf()), GtvFactory.gtv(33))
    }

    @Test fun testNativeFunction() {
        val node = setupNodeAndObjects()
        chkQuery(node, "get_natj",
            GtvFactory.gtv(
                "a" to GtvFactory.gtv(123),
                "b" to GtvFactory.gtv(true),
                "c" to GtvFactory.gtv("A"),
                "d" to GtvFactory.gtv("B")
            ),
            GtvFactory.gtv(15129)
        )
        chkQuery(node, "get_natk",
            GtvFactory.gtv(
                "a" to GtvFactory.gtv(123),
                "b" to GtvFactory.gtv(true),
                "c" to GtvFactory.gtv("A"),
                "d" to GtvFactory.gtv("B")
            ),
            GtvFactory.gtv(15129)
        )
    }

    private fun makeTx_insertCity(name: String): ByteArray {
        return makeTxGtv("insert_city", GtvFactory.gtv(name))
    }

    private fun makeTx_insertPerson(name: String, city: Long, street: String, house: Long, score: Long): ByteArray {
        return makeTxGtv("insert_person",
            GtvFactory.gtv(name),
            GtvFactory.gtv(city),
            GtvFactory.gtv(street),
            GtvFactory.gtv(house),
            GtvFactory.gtv(score)
        )
    }

    private fun makeTx_setState(value: Long): ByteArray {
        return makeTxGtv("set_state", GtvFactory.gtv(value))
    }

    private fun setupNodeAndObjects(): PostchainTestNode {
        val node = setupNode()
        insertObjects(node)
        return node
    }

    private fun insertObjects(node: PostchainTestNode) {
        enqueueTx(node, makeTx_insertCity("New York"), 0)
        enqueueTx(node, makeTx_insertCity("Los Angeles"), 0)
        enqueueTx(node, makeTx_insertCity("Seattle"), 0)
        enqueueTx(node, makeTx_insertPerson("Bob", 2, "Main St", 5, 100), 0)
        enqueueTx(node, makeTx_insertPerson("Alice", 1, "Evergreen Ave", 11, 250), 0)
        enqueueTx(node, makeTx_insertPerson("Trudy", 2, "Mulholland Dr", 3, 500), 0)
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

    @AfterTest
    override fun tearDown() {
        super.tearDown()
    }

    @Suppress("unused")
    class NativeModule(val env: RellNativeEnvironment) {
        @Suppress("UNUSED_PARAMETER")
        fun g(a: Long, b: Boolean?, c: String, d: String?): Long {
            return a * a
        }
    }
}
