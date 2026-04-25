/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.crypto.CURVE_PARAMS
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.Signature
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionBody
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_BooleanValue
import net.postchain.rell.base.lib.type.Rt_ByteArrayValue
import net.postchain.rell.base.lib.type.Rt_IntValue
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.etherjar.PrivateKey
import net.postchain.rell.base.utils.etherjar.Signer
import net.postchain.rell.base.utils.immListOf
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest

internal object Lib_Crypto {
    val Sha256 = C_SysFunctionBody.simple(pure = true) { a ->
        val ba = a.asByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        Rt_ByteArrayValue.get(md.digest(ba))
    }

    private val POINT_TYPE: Rt_Type = rrTypeToRtType(
        RR_Type.Tuple(
            immListOf(
                RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER)),
                RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER)),
            ),
        ),
    )

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias(target = "crypto.verify_signature", since = "0.9.0")
        alias(target = "crypto.sha256", since = "0.10.3")
        alias(target = "crypto.keccak256", since = "0.10.3")
        alias(target = "crypto.eth_ecrecover", since = "0.10.3")

        namespace("crypto", since = "0.10.6") {
            comment("""
                Various cryptographic functions for purposes such as:
                - computing data digests (hashes) (see `sha256()`, `keccak256()`)
                - signing with a private key (see `get_signature()`, `eth_sign()`)
                - verifying signatures (see `verify_signature()`)
                - computing a public key from a signature and a hash (see `eth_ecrecover()`)
                - converting between key formats and addresses (see `eth_pubkey_to_address()`,
                  `eth_privkey_to_address()`, `privkey_to_pubkey()`, `pubkey_encode()`, `pubkey_to_xy()` and
                  `xy_to_pubkey()`)

                Note that for convenience, all functions which accept public key parameters accept them in `33`, `64`
                and `65` byte formats, except `verify_signature()` which accepts only the `33` and `65` byte formats,
                and does not accept the `64` byte format. Note also that not all byte arrays of acceptable length
                constitute valid public keys.

                ### Example 1

                The following example uses several functions from the `crypto` module to create a keypair, convert
                its format, use it to sign a message and then verify the signature of that message:

                ```rell
                // Not a secure way to generate a private key, but this is just an example
                val privkey = x'11'.repeat(32);

                // Convert the private key to a public key, in 65-byte format
                val pubkey_65 = crypto.privkey_to_pubkey(privkey);

                // Convert the public key to other formats
                val pubkey_33 = crypto.pubkey_encode(pubkey_65, true);
                val pubkey_64 = pubkey_65.sub(1); // just drop the first byte
                val (x, y) = crypto.pubkey_to_xy(pubkey_33);
                crypto.pubkey_to_xy(pubkey_65) == (x, y) // returns true, keys are equivalent
                crypto.pubkey_to_xy(pubkey_64) == (x, y) // returns true, keys are equivalent

                // Use the public key to sign a message
                val message = "To whom it may concern...";
                val data_hash = crypto.sha256(message.to_bytes());
                val signature = crypto.get_signature(data_hash, privkey);

                // Assert that the signature is verified successfully
                require(crypto.verify_signature(data_hash, pubkey_33, signature));
                ```

                ### Example 2

                This example demonstrates the usage of `crypto.eth_sign()` and `crypto.eth_ecrecover()` in calculating
                a signature with and subsequently recovering the public key.

                ```rell
                // Another insecure private key
                val privkey = x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f';
                val pubkey = crypto.privkey_to_pubkey(privkey);

                val data_hash = crypto.sha256('Hello'.to_bytes());
                val (r, s, rec_id) = crypto.eth_sign(data_hash, privkey);

                // Prepend x'04' to convert the 64-byte public key to a 65-byte public key
                val recovered_pubkey = x'04' + crypto.eth_ecrecover(r, s, rec_id, data_hash);

                // Assert that we recovered the correct public key
                require(recovered_pubkey == pubkey);
                ```
            """)

            function("sha256", result = "byte_array", since = "0.10.6") {
                comment("""
                    Compute the SHA-256 digest (hash) of the given byte array.
                    @return the SHA-256 digest as a byte array of length 32
                """)
                param(name = "input", type = "byte_array", comment = "the data to digest")
                bodyRaw(Sha256)
            }

            function("keccak256", result = "byte_array", pure = true, since = "0.10.6") {
                comment("""
                    Compute the Keccak256 digest (hash) of the given byte array.
                    @return the Keccak256 digest as a byte array of length 32
                """)
                param(name = "input", type = "byte_array", comment = "the data to digest")
                body { a ->
                    val data = a.asByteArray()
                    val res = keccak256(data)
                    Rt_ByteArrayValue.get(res)
                }
            }

            function("get_signature", result = "byte_array", pure = true, since = "0.13.11") {
                comment("""
                    Sign a 32-byte array with a 32-byte private key using the ECDSA (secp256k1) algorithm.

                    The given 32-byte array `data_hash` is typically a cryptographic hash obtained from a larger data
                    structure using a hashing function such as `hash()`, `sha256()` or `keccak256()`.

                    The returned value can be verified with `verify_signature()` using the public key belonging to the
                    keypair of the given private key.

                    @return a 64-byte signature
                    @throws exception if either `privkey` or `data_hash` are not exactly 32 bytes long
                """)
                param(name = "data_hash", type = "byte_array") {
                    comment("a 32-byte array to be signed")
                }
                param(name = "privkey", type = "byte_array", comment = "the 32-byte private key with which to sign")
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
                    Verify a signature against a message and public key.

                    More precisely, verify that `signature` was obtained with a procedure equivalent to
                    `get_signature(data_hash, privkey)`, where `privkey` and `pubkey` form a keypair.

                    Accepts valid public keys of size `33` or `65` bytes. Note that not all byte arrays of acceptable
                    length constitute valid public keys.

                    @return `true` if the signature is valid, indicating that the message was indeed signed by the
                    private key belonging to the keypair of the given public key; `false` otherwise
                    @throws exception when:
                    - `data_hash` does not have length `32`
                    - `signature` does not have length `64`
                    - `pubkey` does not have length `33` or `65`
                    - `pubkey` is not a valid public key
                """)
                param(name = "data_hash", type = "byte_array") {
                    comment("the original (unsigned) 32-byte array")
                }
                param(name = "pubkey", type = "pubkey", comment = "the public key (33, 64 or 65 bytes)")
                param(name = "signature", type = "byte_array", comment = "the 64-byte signature to verify")

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
                    Compute an Ethererum public key from a signature and a hash.

                    Similar to Solidity's `ecrecover()`, though differs in that:
                    - This function takes `rec_id`, rather than `v`, where `rec_id == v - 27`.
                    - The parameter order is different.
                    - This function returns a 64-byte public key, not a 20-byte address. An address can be obtained by
                     taking the last 20 bytes of the `keccak256()` digest of the returned public key, e.g.:

                     ```rell
                     val address: byte_array = keccak256(eth_ecrecover(...)).sub(44);
                     ```

                    The signature (consisting of the `r`, `s` and `rec_id` components) will typically be obtained with a
                    procedure equivalent to `eth_sign(data_hash, privkey)`, where `privkey` and `pubkey` form a keypair
                    (`pubkey` being returned form this method).

                    The signature component `rec_id` is an _adjusted_ recovery identifier, equivalent to Ethereum's
                    recovery identifier (usually denoted as `v`) **minus 27**, i.e. `rec_id == v - 27`.

                    The given 32-byte array `data_hash` is typically a cryptographic hash obtained from a larger data
                    structure using a hashing function such as `hash()`, `sha256()` or `keccak256()`.

                    ### Example

                    The following is a Node.js script which uses `ecrecover()` (the equivalent to
                    `crypto.eth_ecrecover()`) from the Ethereum Web3 library:

                    ```javascript
                    const Web3 = require('web3');
                    const web3 = new Web3();

                    var r = '0xcf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0';
                    var s = '0xcf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0';
                    var h = '0x53d7b11e61a8059aa4bc3248d24b2936436c9796dfe7f18e414c181004f79427';
                    var v = '0x1c';

                    var address = web3.eth.accounts.recover({'r':r,'s':s,'messageHash':h,'v':v});
                    console.log(address); // prints 0x5b0c087542D5C1E66Df0041e179c4201675B1614
                    ```

                    An equivalent script in Rell is as follows:

                    ```rell
                    val r = x'cf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0';
                    val s = x'cf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0';
                    val h = x'53d7b11e61a8059aa4bc3248d24b2936436c9796dfe7f18e414c181004f79427';
                    val v = 0x1c;

                    val pubkey = eth_ecrecover(r, s, v - 27, h);
                    val address = keccak256(pubkey).sub(12);
                    print(address); // prints 0x5b0c087542d5c1e66df0041e179c4201675b1614
                    ```

                    Note that in the Rell script, `v` is an integer, while in the Node.js script it is an `0x`-prefixed
                    hexadecimal string.

                    @return a 64-byte public key
                """)
                param(name = "r", type = "byte_array", comment = "the first component of the Ethereum signature")
                param(name = "s", type = "byte_array", comment = "the second component of the Ethereum signature")
                param(name = "rec_id", type = "integer", comment = "the recovery identifier, normally `0` or `1`")
                param(name = "data_hash", type = "byte_array", comment = "the original (unsigned) 32-byte array")
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

            val signatureType: Rt_Type = rrTypeToRtType(
                RR_Type.Tuple(
                    immListOf(
                        RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)),
                        RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.BYTE_ARRAY)),
                        RR_TupleField(null, RR_Type.Primitive(RR_PrimitiveKind.INTEGER)),
                    ),
                ),
            )
            val signatureTypeStr = "(byte_array,byte_array,integer)"

            function("eth_sign", result = signatureTypeStr, pure = true, since = "0.10.6") {
                comment("""
                    Compute an Ethereum signature.

                    The given 32-byte array `data_hash` is typically a cryptographic hash obtained from a larger data
                    structure using a hashing function such as `hash()`, `sha256()` or `keccak256()`.

                    The public key corresponding to the given private key can be computed from the original value of
                    `data_hash` and the returned signature tuple `(r, s, rec_id)` using the `eth_ecrecover()` method:

                    ```rell
                    val (r, s, rec_id) = eth_sign(data_hash, privkey);
                    val pubkey = eth_ecrecover(r, s, rec_id, data_hash);
                    ```

                    The returned signature component `rec_id` is an _adjusted_ recovery identifier, equivalent to
                    Ethereum's recovery identifier (usually denoted as `v`) **minus 27**, i.e. `rec_id == v - 27`.

                    @return a tuple containing the signature components:
                    - `r`, the first 32 bytes of the signature
                    - `s`, the second 32 bytes of the signature
                    - `rec_id`, the adjusted recovery identifier (usually `0` or `1`)
                """)
                param(name = "data_hash", type = "byte_array", comment = "a 32-byte array to be signed")
                param(name = "privkey", type = "byte_array", comment = "the 32-byte private key with which to sign")
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
                comment("""
                    Compute a 20-byte Ethereum address from a 32-byte private key.
                    @throws exception if `privkey` is not `32` bytes
                """)
                param(name = "privkey", type = "byte_array", comment = "the 32-byte private key")
                body { arg ->
                    val point = privkeyToPubkeyPoint(arg)
                    pointToEthAddressValue(point)
                }
            }

            function("eth_pubkey_to_address", result = "byte_array", pure = true, since = "0.13.5") {
                comment("""
                    Compute a 20-byte Ethereum address from a public key.

                    Accepts valid public keys of size `33`, `64` or `65` bytes. Note that not all byte arrays of
                    acceptable length constitute valid public keys.
                    @throws exception when
                    - the given byte array does not have length `33`, `64` or `65`
                    - the given byte array is not a valid public key
                """)
                param(name = "pubkey", type = "byte_array", comment = "the public key (33, 64 or 65 bytes)")
                body { arg ->
                    val point = pubkeyToPoint(arg)
                    pointToEthAddressValue(point)
                }
            }

            function("privkey_to_pubkey", "byte_array", pure = true, since = "0.10.6") {
                comment("""
                    Compute a public key from a 32-byte private key.

                    The optional boolean flag `compressed` determines whether a compressed (33-byte), or uncompressed
                    (65-byte) public key, is returned. Defaults to false (uncompressed) if not provided.
                    @return a 65-byte public key in uncompressed mode, or a 33-byte public key in compressed mode
                    @throws exception if `privkey` is not `32` bytes
                """)
                param("privkey", "byte_array", comment = "the 32-byte private key")
                param("compressed", "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("whether or not the public should be compressed; defaults to false (uncompressed)")
                }
                bodyOpt1 { arg1, arg2 ->
                    val compressed = arg2?.asBoolean() ?: false
                    val point = privkeyToPubkeyPoint(arg1)
                    val bytes = pointToBytes(point, compressed)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("pubkey_encode", result = "byte_array", pure = true, since = "0.13.5") {
                comment("""
                    Convert between public key formats.

                    Accepts valid public keys of size `33`, `64` or `65` bytes. Note that not all byte arrays of
                    acceptable length constitute valid public keys.

                    The optional boolean flag `compressed` determines whether a compressed (33-byte), or uncompressed
                    (65-byte) public key, is returned. Defaults to false (uncompressed) if not provided.

                    Examples:
                    ```rell
                    // convert a 33-byte pubkey to a 65-byte pubkey:
                    crypto.pubkey_encode(my_33_byte_pubkey)

                    // convert a 65-byte pubkey to a 33-byte pubkey:
                    crypto.pubkey_encode(my_65_byte_pubkey, true)

                    // convert a 64-byte pubkey to a 65-byte pubkey:
                    crypto.pubkey_encode(my_64_byte_pubkey)

                    // convert a 64-byte pubkey to a 33-byte pubkey:
                    crypto.pubkey_encode(my_64_byte_pubkey, true)
                    ```

                    Note that this function cannot return a 64-byte public key. However a 64-byte public key can be
                    obtained from a 65-byte public key by dropping the first byte with `byte_array.sub(1)`:

                    ```rell
                    // convert a 65-byte pubkey to a 64-byte pubkey:
                    my_65_byte_pubkey.sub(1)

                    // convert a 33-byte pubkey to a 64-byte pubkey:
                    crypto.pubkey_encode(my_33_byte_pubkey).sub(1)
                    ```

                    @return a 65-byte public key in uncompressed mode, or a 33-byte public key in compressed mode
                    @throws exception when
                    - the given byte array does not have length `33`, `64` or `65`
                    - the given byte array is not a valid public key
                """)
                param(name = "pubkey", type = "byte_array", comment = "the public key to compress/uncompress")
                param(name = "compressed", type = "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("whether the returned public key should be compressed, defaults to false")
                }
                bodyOpt1 { arg1, arg2 ->
                    val compressed = arg2?.asBoolean() ?: false
                    val point = pubkeyToPoint(arg1)
                    val bytes = pointToBytes(point, compressed)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("pubkey_to_xy", result = "(big_integer,big_integer)", pure = true, since = "0.13.5") {
                comment("""
                    Extract the `x` and `y` coordinates from an EC point public key. The extracted point is a tuple
                    containing two `big_integer`s, which are the `x` and `y` coordinates of the given public key, a
                    point on the secp256k1 elliptic curve.

                    Inverse of `crypto.xy_to_pubkey()`.

                    Accepts valid `33`, `64` or `65` byte public keys. Note that not all byte arrays of acceptable
                    length constitute valid public keys.
                    @return the EC point `x` and `y` coordinates of the given public key
                    @throws exception when
                    - the given byte array does not have length `33`, `64` or `65`
                    - the given byte array is not a valid public key
                """)
                param(name = "pubkey", type = "byte_array", comment = "the public key")
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
                comment("""
                    Construct a public key from EC point `x` and `y` coordinates. The given `x` and `y` coordinates must
                    encode a point on the secp256k1 elliptic curve in order to constitute a public key.

                    Inverse of `crypto.pubkey_to_xy()`.

                    The optional boolean flag `compressed` determines whether a compressed (33-byte), or uncompressed
                    (65-byte) public key, is returned. Defaults to false (uncompressed) if not provided.
                    @return a 65-byte public key if uncompressed mode is used, or an equivalent 33-byte public key if
                    compressed mode is used
                    @throws exception if the given `x` and `y` coordinates do not encode a point on the secp256k1
                    elliptic curve (and therefore do not constitute a valid public key)
                """)
                param(name = "x", type = "big_integer", comment = "the x-coordinate")
                param(name = "y", type = "big_integer", comment = "the y-coordinate")
                param(name = "compressed", type = "boolean", arity = L_ParamArity.ZERO_ONE) {
                    comment("whether the returned public key should be compressed, defaults to false")
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
            "fn:$fn:$errCode:${array.size}" to "Wrong size of $errMsg: ${array.size} instead of $expSize"
        }
    }

    private fun pubkeyToPoint(pubkeyValue: Rt_Value): ECPoint {
        val bytes0 = pubkeyValue.asByteArray()
        val bytes = if (bytes0.size == 64) (byteArrayOf(0x04) + bytes0) else bytes0
        return bytesToPoint(bytes, bytes0.size)
    }

    private fun bytesToPoint(bytes: ByteArray, possiblyOriginalSize: Int? = null): ECPoint {
        val originalSize = possiblyOriginalSize ?: bytes.size
        val point = try {
            CURVE_PARAMS.curve.decodePoint(bytes)
        } catch (_: RuntimeException) {
            throw Rt_Exception.common(
                "crypto:bad_pubkey:${originalSize}", "Bad public key (size: ${originalSize})")
        }
        return point
    }

    private fun xyToPoint(xValue: Rt_Value, yValue: Rt_Value): ECPoint {
        val x = xValue.asBigInteger()
        val y = yValue.asBigInteger()
        val point = try {
            CURVE_PARAMS.curve.createPoint(x, y)
        } catch (_: RuntimeException) {
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
            "point_to_bytes:bad_pubkey:${bytes.size}" to "Bad public key (size: ${bytes.size})"
        }
        return bytes
    }

    private fun keccak256(data: ByteArray): ByteArray {
        val md: MessageDigest = Keccak.Digest256()
        return md.digest(data)
    }
}
