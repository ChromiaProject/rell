/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.runtime.Rt_ByteArrayValue
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_StructValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.BytesKeyPair
import net.postchain.rell.base.utils.toImmMap

object Lib_Test_KeyPairs {
    private val PREDEFINED_KEYPAIRS = createPredefinedKeyPairs()

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            struct("keypair") {
                attribute("pub", type = "byte_array")
                attribute("priv", type = "byte_array")
            }

            constant("BLOCKCHAIN_SIGNER_KEYPAIR", type = "rell.test.keypair") {
                value { rType -> keyPairToStruct(rType, Lib_RellTest.BLOCK_RUNNER_KEYPAIR) }
            }

            namespace("keypairs") {
                for ((name, keyPair) in PREDEFINED_KEYPAIRS) {
                    constant(name, type = "rell.test.keypair") {
                        value { rType -> keyPairToStruct(rType, keyPair) }
                    }
                }
            }

            namespace("privkeys") {
                for ((name, keyPair) in PREDEFINED_KEYPAIRS) {
                    val value = Rt_ByteArrayValue.get(keyPair.priv.toByteArray())
                    constant(name, type = "byte_array", value = value)
                }
            }

            namespace("pubkeys") {
                for ((name, keyPair) in PREDEFINED_KEYPAIRS) {
                    val value = Rt_ByteArrayValue.get(keyPair.pub.toByteArray())
                    constant(name, type = "byte_array", value = value)
                }
            }
        }
    }

    private fun createPredefinedKeyPairs(): Map<String, BytesKeyPair> {
        // Names are taken from https://en.wikipedia.org/wiki/Alice_and_Bob
        val names = listOf("bob", "alice", "trudy", "charlie", "dave", "eve", "frank", "grace", "heidi")
        return names.mapIndexed { i, name ->
            val privKeyBytes = (i + 1).toString().repeat(64).hexStringToByteArray()
            val pubKeyBytes = secp256k1_derivePubKey(privKeyBytes)
            name to BytesKeyPair(privKeyBytes, pubKeyBytes)
        }.toMap().toImmMap()
    }

    fun structToKeyPair(v: Rt_Value): BytesKeyPair {
        val v2 = v.asStruct()
        val actualType = v2.type()
        val expectedType = Lib_RellTest.KEYPAIR_TYPE
        if (actualType != expectedType) {
            throw Rt_Exception.common(
                "type:struct:$expectedType:$actualType",
                "Wrong struct type: $actualType instead of $expectedType",
            )
        }

        val pub = toByteArray(v2.get(0), 33)
        val priv = toByteArray(v2.get(1), 32)
        return BytesKeyPair(priv, pub)
    }

    private fun toByteArray(v: Rt_Value, n: Int): ByteArray {
        val bs = v.asByteArray()
        Rt_Utils.check(bs.size == n) { "keypair:wrong_byte_array_size:$n:${bs.size}" toCodeMsg
                "Wrong byte array size: ${bs.size} instead of $n" }
        return bs
    }

    private fun keyPairToStruct(rType: R_Type, keyPair: BytesKeyPair): Rt_Value {
        val structType = rType as R_StructType
        val attrs = listOf(keyPair.pub, keyPair.priv)
            .map { Rt_ByteArrayValue.get(it.toByteArray()) }
            .toMutableList<Rt_Value>()
        return Rt_StructValue(structType, attrs)
    }
}
