/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx.it

import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GtxBuilder
import net.postchain.rell.base.runtime.PostchainGtvUtils
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
abstract class BaseGtxIntegrationTest: ConfigFileBasedIntegrationTest() {
    protected abstract val configPath: String

    protected var blockchainRid: BlockchainRid? = null

    private val myCS = Secp256K1CryptoSystem()

    protected fun setupNode(): PostchainTestNode {
        val node = createNode(0, configPath)
        blockchainRid = node.getBlockchainRid(1L)
        return node
    }

    protected fun makeTx(opName: String, vararg args: Any?): ByteArray {
        val gtvArgs: List<Gtv> = args.map { v ->
            when (v) {
                null -> GtvNull
                is String -> gtv(v)
                is Int -> gtv(v.toLong())
                is Long -> gtv(v)
                else -> TODO("$v")
            }
        }
        return makeTxGtv(opName, *gtvArgs.toTypedArray())
    }

    protected fun makeTxGtv(opName: String, vararg opArgs: Gtv): ByteArray {
        val ownerIdx = 0
        val owner = KeyPairHelper.pubKey(ownerIdx)
        return GtxBuilder(blockchainRid!!, listOf(owner), myCS, PostchainGtvUtils.merkleHashCalculator)
            .addOperation(opName, *opArgs.toList().toTypedArray())
            .finish()
            .sign(myCS.buildSigMaker(KeyPair(owner, KeyPairHelper.privKey(ownerIdx))))
            .buildGtx()
            .encode()
    }
}
