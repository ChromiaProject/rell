/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV1
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.rell.base.utils.PostchainGtvUtils

object VirtualTestUtils {
    fun argToGtv(args: String) = GtvTestUtils.decodeGtvStr(args)

    fun argToGtv(args: String, paths: String): Gtv {
        val gtv = GtvTestUtils.decodeGtvStr(args)
        return argToGtv(gtv, paths)
    }

    fun argToGtv(gtv: Gtv, paths: String): Gtv {
        val pathsSet = GtvTestUtils.decodeGtvStr(paths).asArray()
            .map { t ->
                val ints = t.asArray()
                    .map {
                        val v: Any = if (it is GtvInteger) it.asInteger().toInt() else it.asString()
                        v
                    }
                    .toTypedArray()
                GtvPathFactory.buildFromArrayOfPointers(ints)
            }
            .toSet()

        val gtvPaths = GtvPathSet(pathsSet)

        val calculator = GtvMerkleHashCalculatorV1(PostchainGtvUtils.cryptoSystem)
        val merkleProofTree = gtv.generateProof(gtvPaths, calculator)
        val proofGtv = merkleProofTree.toGtv()
        return proofGtv
    }
}
