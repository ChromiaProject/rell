/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.serialization

import net.postchain.gtvmin.*
import net.postchain.gtvmin.json.GtvJsonConfig
import net.postchain.gtvmin.json.jackson.GtvJson
import java.util.*

/**
 * Bridges the Kotlin-side string encodings of GTV values (JSON for `RR_ConstantValue.Gtv`,
 * Base64 for `RR_GlobalConstantDefinition.metaGtvJson`) and the canonical ASN.1 DER
 * binary used on the FlatBuffers wire.
 *
 * The Kotlin RR tree is frozen, so this helper does the translation inside the
 * serialization module rather than changing the tree. The resulting FB buffers hold
 * raw GTV bytes which C++ / native backends can consume directly without a JSON parser.
 */
internal object GtvBinaryHelper {
    private val JSON = GtvJson(GtvJsonConfig(gsonCompatible = true))

    fun jsonToBinary(json: String): ByteArray = GtvEncoder.encodeGtv(JSON.decodeFromString(json))

    fun binaryToJson(bytes: ByteArray): String {
        if (bytes.size > DeserLimits.MAX_GTV_SIZE) {
            throw RRDeserializationException(
                "GtvValue binary: size ${bytes.size} exceeds MAX_GTV_SIZE=${DeserLimits.MAX_GTV_SIZE}",
            )
        }
        val gtv = try {
            GtvFactory.decodeGtv(bytes)
        } catch (e: StackOverflowError) {
            throw RRDeserializationException("malformed GTV: decoder recursion exceeded", e)
        } catch (e: RuntimeException) {
            throw RRDeserializationException("malformed GTV: ${e.javaClass.simpleName}", e)
        }
        return try {
            JSON.encodeToString(gtv)
        } catch (e: StackOverflowError) {
            throw RRDeserializationException("malformed GTV: JSON encoder recursion exceeded", e)
        }
    }

    fun base64ToBinary(b64: String): ByteArray = Base64.getDecoder().decode(b64)
    fun binaryToBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
