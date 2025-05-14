/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.crypto.CURVE_PARAMS
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.Signature
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.Rt_TupleValue
import net.postchain.rell.base.runtime.Rt_Exception
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.etherjar.PrivateKey
import net.postchain.rell.base.utils.etherjar.Signer
import net.postchain.rell.base.utils.immListOf
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest

object Lib_Crypto {
    val Sha256 = C_SysFunctionBody.simple(pure = true) { a ->
        val ba = a.asByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        Rt_ByteArrayValue.get(md.digest(ba))
    }

    private val POINT_TYPE = R_TupleType.create(R_BigIntegerType, R_BigIntegerType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias(target = "crypto.verify_signature", since = "0.9.0")
        alias(target = "crypto.sha256", since = "0.10.3")
        alias(target = "crypto.keccak256", since = "0.10.3")
        alias(target = "crypto.eth_ecrecover", since = "0.10.3")

        namespace("crypto", since = "0.10.6") {
            function("sha256", result = "byte_array", since = "0.10.6") {
                comment("""
                    Calculates the SHA-256 digest (hash) of the given byte array.
                    @return the SHA-256 digest as a byte array of length 32
                """)
                param(name = "input", type = "byte_array", comment = "the data to digest")
                bodyRaw(Sha256)
            }

            function("keccak256", result = "byte_array", pure = true, since = "0.10.6") {
                comment("Calculates a Keccak256 hash of a byte array and returns a byte array.")
                param(name = "input", type = "byte_array", comment = "The byte array to be hashed.")
                body { a ->
                    val data = a.asByteArray()
                    val res = keccak256(data)
                    Rt_ByteArrayValue.get(res)
                }
            }

            function("get_signature", result = "byte_array", pure = true, since = "0.13.11") {
                comment("""
                    Calculates a ECDSA (secp256k1) signature. The returned value can be verified with the
                    `verify_signature()` function.
                    @return 64-byte signature
                    @see `verify_signature()`
                """)
                param(name = "data_hash", type = "byte_array") {
                    comment("The hash of the data to be signed. Must 32 bytes.")
                }
                param(name = "privkey", type = "byte_array", comment = "The 32-byte private key used for signing.")
                body { a, b ->
                    val dataHash = a.asByteArray()
                    val privKey = b.asByteArray()
                    checkByteArraySize(dataHash, 32, fnSimpleName, "datahash_size", "data hash")
                    checkPrivKeySize(privKey, fnSimpleName)

                    val privKeyObj = PrivKey(privKey)
                    val pubKeyObj = PostchainGtvUtils.cryptoSystem.derivePubKey(privKeyObj)
                    val sigMaker = PostchainGtvUtils.cryptoSystem.buildSigMaker(KeyPair(pubKeyObj, privKeyObj))
                    val signature = sigMaker.signDigest(dataHash)
                    checkEquals(signature.data.size, 64)

                    Rt_ByteArrayValue.get(signature.data)
                }
            }

            function("verify_signature", result = "boolean", pure = true, since = "0.10.6") {
                comment("""
                    Verifies a signature against a message and public key.
                    @return true if the signature is valid, indicating that the message was indeed signed
                    by the owner of the private key corresponding to the provided public key.
                    @see `get_signature()`
                """)
                param(name = "data_hash", type = "byte_array") {
                    comment("The byte array representing the message that was signed.")
                }
                param(name = "pubkey", type = "pubkey", comment = "The public key to verify the signature against.")
                param(name = "signature", type = "byte_array", comment = "The 64-byte signature to verify.")

                body { a, b, c ->
                    val dataHash = a.asByteArray()
                    checkByteArraySize(dataHash, 32, fnSimpleName, "datahash_size", "data hash")

                    val res = try {
                        val signature = Signature(b.asByteArray(), c.asByteArray())
                        PostchainGtvUtils.cryptoSystem.verifyDigest(dataHash, signature)
                    } catch (e: Exception) {
                        throw Rt_Exception.common("verify_signature", e.message ?: "Signature verification crashed")
                    }

                    Rt_BooleanValue.get(res)
                }
            }

            function("eth_ecrecover", result = "byte_array", pure = true, since = "0.10.6") {
                comment("""
                    Calculate Ethererum public key from a signature and a hash.

                    Does almost the same as the Solidity `ecrecover(...)` function, but isn't its strict equivalent.

                    How this function differs from the Solidity one:

                    - takes `rec_id` instead of `v`, where `rec_id` = `v` - 27
                    - other parameters (`r`, `s`, `hash`) are the same, but in a different order
                    - returns a 64-byte public key, not a 20-byte address; the address is the last 20 bytes of
                      `keccak256(...)` of the public key

                    @return 64-byte public key
                    @see `eth_sign()`
                """)
                param(name = "r", type = "byte_array", comment = "The first component of the Ethereum signature")
                param(name = "s", type = "byte_array", comment = "The second component of the Ethereum signature")
                param(name = "rec_id", type = "integer", comment = "The recovery identifier, normally 0 or 1")
                param(name = "data_hash", type = "byte_array") {
                    comment("The byte array representing the hash that was signed")
                }
                body { a, b, c, d ->
                    val r = a.asByteArray()
                    val s = b.asByteArray()
                    val recId = c.asInteger()
                    val hash = d.asByteArray()

                    check(recId in 0..100000) { "recId out of range: $recId" }
                    val rVal = BigInteger(1, r)
                    val sVal = BigInteger(1, s)
                    val v = recId.toInt() + 27
                    val signature = net.postchain.rell.base.utils.etherjar.Signature(hash, v, rVal, sVal)
                    val res = Signer.ecrecover(signature)

                    Rt_ByteArrayValue.get(res)
                }
            }

            val signatureType = R_TupleType.create(R_ByteArrayType, R_ByteArrayType, R_IntegerType)
            val signatureTypeStr = "(byte_array,byte_array,integer)"

            function("eth_sign", result = signatureTypeStr, pure = true, since = "0.10.6") {
                comment("""
                    Calculates an Ethereum signature.
                    Takes a hash and a private key and returns values `r`, `s`,
                    and `rec_id` that are accepted by `eth_ecrecover`.
                    @return tuple containing the ethereum signature components.
                    @see `eth_ecrecover()`
                """)
                param(name = "data_hash", type = "byte_array") {
                    comment("The byte array representing the hash to be signed.")
                }
                param(name = "privkey", type = "byte_array", comment = "The 32-byte private key used for signing.")
                body { a, b ->
                    val dataHash = a.asByteArray()
                    val privKey = b.asByteArray()
                    checkByteArraySize(dataHash, 32, fnSimpleName, "datahash_size", "data hash")
                    checkPrivKeySize(privKey, fnSimpleName)

                    val signer = Signer(null)
                    val privKeyObj = PrivateKey.create(privKey)
                    val sign =
                        signer.create(dataHash, privKeyObj, net.postchain.rell.base.utils.etherjar.Signature::class.java)

                    val r = bigIntToRS(sign.r)
                    val s = bigIntToRS(sign.s)
                    val recId = sign.recId

                    checkEquals(r.size, 32)
                    checkEquals(s.size, 32)

                    val elems = immListOf(
                        Rt_ByteArrayValue.get(r),
                        Rt_ByteArrayValue.get(s),
                        Rt_IntValue.get(recId.toLong()),
                    )
                    Rt_TupleValue(signatureType, elems)
                }
            }

            function("eth_privkey_to_address", result = "byte_array", pure = true, since = "0.13.5") {
                comment("Derives a 20-byte Ethereum address from a 32-byte private key.")
                param(name = "privkey", type = "byte_array", comment = "The 32-byte private key.")
                body { arg ->
                    val point = privkeyToPubkeyPoint(arg)
                    pointToEthAddressValue(point)
                }
            }

            function("eth_pubkey_to_address", result = "byte_array", pure = true, since = "0.13.5") {
                comment("Derives a 20-byte Ethereum address from a public key (33, 64, or 65 bytes).")
                param(name = "pubkey", type = "byte_array", comment = "The public key (33, 64, or 65 bytes).")
                body { arg ->
                    val point = pubkeyToPoint(arg)
                    pointToEthAddressValue(point)
                }
            }

            function("privkey_to_pubkey", "byte_array", pure = true, since = "0.10.6") {
                comment("Converts a privkey to a pubkey")
                param("privkey", "byte_array", comment = "The private key")
                param("compressed", "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("Whether or not the pubkey should be compressed. Defaults to false (uncompressed).")
                }
                bodyOpt1 { arg1, arg2 ->
                    val compressed = arg2?.asBoolean() ?: false
                    val point = privkeyToPubkeyPoint(arg1)
                    val bytes = pointToBytes(point, compressed)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("pubkey_encode", result = "byte_array", pure = true, since = "0.13.5") {
                comment("Converts a public key between compressed (33-byte) and uncompressed (65-byte) formats.")
                param(name = "pubkey", type = "byte_array", comment = "The public key to be encoded.")
                param(name = "compressed", type = "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        Boolean flag indicating whether to return the compressed (33-byte)
                        or uncompressed (65-byte) public key. Defaults to false (uncompressed).
                    """)
                }
                bodyOpt1 { arg1, arg2 ->
                    val compressed = arg2?.asBoolean() ?: false
                    val point = pubkeyToPoint(arg1)
                    val bytes = pointToBytes(point, compressed)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("pubkey_to_xy", result = "(big_integer,big_integer)", pure = true, since = "0.13.5") {
                comment("Extracts the EC point coordinates (x, y) from a public key.")
                param(name = "pubkey", type = "byte_array", comment = "The public key.")
                body { arg ->
                    val point = pubkeyToPoint(arg)
                    val x = point.xCoord.toBigInteger()
                    val y = point.yCoord.toBigInteger()
                    val xValue = Rt_BigIntegerValue.get(x)
                    val yValue = Rt_BigIntegerValue.get(y)
                    Rt_TupleValue.make(POINT_TYPE, xValue, yValue)
                }
            }

            function("xy_to_pubkey", result = "byte_array", pure = true, since = "0.13.5") {
                comment("Constructs a public key (compressed or uncompressed) from EC point coordinates.")
                param(name = "x", type = "big_integer", comment = "The x-coordinate of the EC point.")
                param(name = "y", type = "big_integer", comment = "The y-coordinate of the EC point.")
                param(name = "compressed", type = "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("""
                        Boolean flag indicating whether to return the compressed (33-byte)
                        or uncompressed (65-byte) public key. Defaults to false (uncompressed).
                    """)
                }
                bodyOpt2 { arg1, arg2, arg3 ->
                    val compressed = arg3?.asBoolean() ?: false
                    val point = xyToPoint(arg1, arg2)
                    val bytes = pointToBytes(point, compressed)
                    bytesToPoint(bytes) // Check that it's a valid public key.
                    Rt_ByteArrayValue.get(bytes)
                }
            }
        }
    }

    private fun bigIntToRS(i: BigInteger): ByteArray {
        val res = i.toByteArray()
        return if (res.size < 32) {
            // Less than 32 bytes -> add zero bytes to the left.
            ByteArray(32 - res.size) { 0 } + res
        } else if (res.size == 33 && res[0] == (0).toByte()) {
            // BigInteger.toByteArray() adds a leading zero byte for negative values, we must remove it.
            res.copyOfRange(1, res.size)
        } else {
            checkEquals(res.size, 32)
            res
        }
    }

    private fun privkeyToPubkeyPoint(privkeyValue: Rt_Value): ECPoint {
        val privKey = privkeyValue.asByteArray()
        checkPrivKeySize(privKey, "privkey_to_pubkey")
        val d = BigInteger(1, privKey)
        return CURVE_PARAMS.g.multiply(d)
    }

    private fun checkPrivKeySize(privKey: ByteArray, fn: String) {
        checkByteArraySize(privKey, 32, fn, "privkey_size", "private key")
    }

    @Suppress("SameParameterValue")
    private fun checkByteArraySize(array: ByteArray, expSize: Int, fn: String, errCode: String, errMsg: String) {
        Rt_Utils.check(array.size == expSize) {
            "fn:$fn:$errCode:${array.size}" toCodeMsg "Wrong size of $errMsg: ${array.size} instead of $expSize"
        }
    }

    private fun pubkeyToPoint(pubkeyValue: Rt_Value): ECPoint {
        val bytes0 = pubkeyValue.asByteArray()
        val bytes = if (bytes0.size == 64) (byteArrayOf(0x04) + bytes0) else bytes0
        return bytesToPoint(bytes)
    }

    private fun bytesToPoint(bytes: ByteArray): ECPoint {
        val point = try {
            CURVE_PARAMS.curve.decodePoint(bytes)
        } catch (e: RuntimeException) {
            throw Rt_Exception.common("crypto:bad_pubkey:${bytes.size}", "Bad public key (size: ${bytes.size})")
        }
        return point
    }

    private fun xyToPoint(xValue: Rt_Value, yValue: Rt_Value): ECPoint {
        val x = xValue.asBigInteger()
        val y = yValue.asBigInteger()
        val point = try {
            CURVE_PARAMS.curve.createPoint(x, y)
        } catch (e: RuntimeException) {
            throw Rt_Exception.common("crypto:bad_point", "Bad EC point coordinates")
        }
        return point
    }

    private fun pointToEthAddressValue(point: ECPoint): Rt_Value {
        val bytes = pointToBytes(point, false)

        val payload = bytes.sliceArray(1 until bytes.size)
        val hash = keccak256(payload)
        checkEquals(hash.size, 32)

        val res = hash.sliceArray(12 until 32)
        return Rt_ByteArrayValue.get(res)
    }

    private fun pointToBytes(point: ECPoint, compressed: Boolean): ByteArray {
        val bytes = point.getEncoded(compressed)
        Rt_Utils.check(bytes.size == if (compressed) 33 else 65) {
            "point_to_bytes:bad_pubkey:${bytes.size}" toCodeMsg "Bad public key (size: ${bytes.size})"
        }
        return bytes
    }

    private fun keccak256(data: ByteArray): ByteArray {
        val md: MessageDigest = Keccak.Digest256()
        return md.digest(data)
    }
}
