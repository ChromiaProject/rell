/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils

import com.google.gson.Gson
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.*
import net.postchain.gtv.gtvml.GtvMLEncoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.base.runtime.GtvToRtContext
import net.postchain.rell.base.runtime.GtvToRtDefaultValueEvaluator
import net.postchain.rell.base.runtime.Rt_Value

object PostchainGtvUtils {
    val HASH_V2_SWITCH = C_FeatureSwitch("0.14.5")

    val cryptoSystem: CryptoSystem = Secp256K1CryptoSystem()
    val hashCalculator = HashCalculator()
    val merkleHashCalculator: GtvMerkleHashCalculatorBase = makeMerkleHashCalculator(2)

    private val GSON: Gson = make_gtv_gson()
    private val LENIENT_GSON: Gson = makeLenientGtvGson()
    private val PRETTY_GSON: Gson = makeLenientGtvGsonBuilder().setPrettyPrinting().create()

    fun gtvToBytes(v: Gtv): ByteArray = GtvEncoder.encodeGtv(v)
    fun bytesToGtv(v: ByteArray): Gtv = GtvFactory.decodeGtv(v)

    fun xmlToGtv(s: String): Gtv = GtvMLParser.parseGtvML(s)
    fun gtvToXml(v: Gtv): String = GtvMLEncoder.encodeXMLGtv(v)

    fun gtvToJson(v: Gtv, supportBigInteger: Boolean = false): String =
        (if (supportBigInteger) LENIENT_GSON else GSON).toJson(v, Gtv::class.java)

    fun jsonToGtv(s: String): Gtv = GSON.fromJson(s, Gtv::class.java) ?: GtvNull
    fun gtvToJsonPretty(v: Gtv): String = PRETTY_GSON.toJson(v, Gtv::class.java)

    fun moduleArgsGtvToRt(
        struct: R_StructDefinition,
        gtv: Gtv,
        validateOnly: Boolean = false,
        defaultValueEvaluator: GtvToRtDefaultValueEvaluator?,
        compilerOptions: C_CompilerOptions,
    ): Rt_Value {
        // GtvToRtContext.finish() is not called, because there is no execution context.
        // It's not really needed, because module_args can't use entities, and .finish() is needed only for them.
        val convCtx = GtvToRtContext.make(
            pretty = true,
            validateOnly = validateOnly,
            defaultValueEvaluator = defaultValueEvaluator,
            compilerOptions = compilerOptions,
        )
        return struct.type.gtvToRt(convCtx, gtv)
    }

    class HashCalculator(defaultVersion: Int = 2) {
        private val v1: GtvMerkleHashCalculatorBase = makeMerkleHashCalculator(1)
        private val v2: GtvMerkleHashCalculatorBase = makeMerkleHashCalculator(2)
        private val default: GtvMerkleHashCalculatorBase = getCalculator(defaultVersion)

        fun hash(value: Gtv, version: Int? = null): ByteArray {
            val calculator = if (version == null) default else getCalculator(version)
            return value.merkleHash(calculator)
        }

        private fun getCalculator(version: Int): GtvMerkleHashCalculatorBase = when (version) {
            1 -> v1
            2 -> v2
            else -> throw IllegalArgumentException("Invalid hash version: $version")
        }
    }
}
