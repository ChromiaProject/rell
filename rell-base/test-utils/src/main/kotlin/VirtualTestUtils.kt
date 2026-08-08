/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import net.postchain.gtvmin.Gtv
import net.postchain.gtvmin.GtvInteger
import net.postchain.gtvmin.generateProof
import net.postchain.gtvmin.merkle.GtvMerkleHashCalculatorV1
import net.postchain.gtvmin.merkle.Sha256Digester
import net.postchain.gtvmin.merkle.path.GtvPathFactory
import net.postchain.gtvmin.merkle.path.GtvPathSet

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

        val calculator = GtvMerkleHashCalculatorV1(Sha256Digester)
        val merkleProofTree = gtv.generateProof(gtvPaths, calculator)
        val proofGtv = merkleProofTree.toGtv()
        return proofGtv
    }
}
