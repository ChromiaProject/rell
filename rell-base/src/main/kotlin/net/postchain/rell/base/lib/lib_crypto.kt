/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.crypto.CURVE_PARAMS
import net.postchain.crypto.Signature
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.runtime.*
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
        alias(target = "crypto.sha256")
        alias(target = "crypto.keccak256")
        alias(target = "crypto.verify_signature")
        alias(target = "crypto.eth_ecrecover")

        namespace("crypto") {
            function("sha256", "byte_array") {
                param("input", "byte_array")
                bodyRaw(Sha256)
            }

            function("keccak256", "byte_array", pure = true) {
                param("input", "byte_array")
                body { a ->
                    val data = a.asByteArray()
                    val res = keccak256(data)
                    Rt_ByteArrayValue.get(res)
                }
            }

            function("verify_signature", "boolean", pure = true) {
                param("data_hash", "byte_array")
                param("pubkey", "pubkey")
                param("signature", "byte_array")

                body { a, b, c ->
                    val dataHash = a.asByteArray()
                    val res = try {
                        val signature = Signature(b.asByteArray(), c.asByteArray())
                        PostchainGtvUtils.cryptoSystem.verifyDigest(dataHash, signature)
                    } catch (e: Exception) {
                        throw Rt_Exception.common("verify_signature", e.message ?: "Signature verification crashed")
                    }
                    Rt_BooleanValue.get(res)
                }
            }

            function("eth_ecrecover", "byte_array", pure = true) {
                param("r", "byte_array")
                param("s", "byte_array")
                param("rec_id", "integer")
                param("data_hash", "byte_array")

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

            function("eth_sign", signatureTypeStr, pure = true) {
                param("data_hash", "byte_array")
                param("privkey", "byte_array")

                body { a, b ->
                    val hash = a.asByteArray()
                    val privKey = b.asByteArray()
                    checkPrivKeySize(privKey, "eth_sign")

                    val signer = Signer(null)
                    val privKeyObj = PrivateKey.create(privKey)
                    val sign = signer.create(hash, privKeyObj, net.postchain.rell.base.utils.etherjar.Signature::class.java)

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

            function("eth_privkey_to_address", result = "byte_array", pure = true) {
                param(name = "privkey", type = "byte_array")
                body { arg ->
                    val point = privkeyToPubkeyPoint(arg)
                    pointToEthAddressValue(point)
                }
            }

            function("eth_pubkey_to_address", result = "byte_array", pure = true) {
                param(name = "pubkey", type = "byte_array")
                body { arg ->
                    val point = pubkeyToPoint(arg)
                    pointToEthAddressValue(point)
                }
            }

            function("privkey_to_pubkey", "byte_array", pure = true) {
                param("privkey", "byte_array")
                param("compressed", "boolean", arity = L_ParamArity.ZERO_ONE)

                bodyOpt1 { arg1, arg2 ->
                    val compressed = arg2?.asBoolean() ?: false
                    val point = privkeyToPubkeyPoint(arg1)
                    val bytes = pointToBytes(point, compressed)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("pubkey_encode", "byte_array", pure = true) {
                param(name = "pubkey", type = "byte_array")
                param(name = "compressed", type = "boolean", arity = L_ParamArity.ZERO_ONE)
                bodyOpt1 { arg1, arg2 ->
                    val compressed = arg2?.asBoolean() ?: false
                    val point = pubkeyToPoint(arg1)
                    val bytes = pointToBytes(point, compressed)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("pubkey_to_xy", "(big_integer,big_integer)", pure = true) {
                param(name = "pubkey", type = "byte_array")
                body { arg ->
                    val point = pubkeyToPoint(arg)
                    val x = point.xCoord.toBigInteger()
                    val y = point.yCoord.toBigInteger()
                    val xValue = Rt_BigIntegerValue.get(x)
                    val yValue = Rt_BigIntegerValue.get(y)
                    Rt_TupleValue.make(POINT_TYPE, xValue, yValue)
                }
            }

            function("xy_to_pubkey", "byte_array", pure = true) {
                param(name = "x", type = "big_integer")
                param(name = "y", type = "big_integer")
                param(name = "compressed", type = "boolean", arity = L_ParamArity.ZERO_ONE)
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
        val expPrivKeySize = 32
        Rt_Utils.check(privKey.size == expPrivKeySize) {
            "fn:$fn:privkey_size:${privKey.size}" toCodeMsg "Wrong size of private key: ${privKey.size} instead of $expPrivKeySize"
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
