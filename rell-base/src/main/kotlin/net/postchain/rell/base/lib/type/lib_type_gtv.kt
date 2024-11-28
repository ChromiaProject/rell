/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.*
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.immMapOf
import java.math.BigInteger

object Lib_Type_Gtv {
    val LIST_OF_GTV_TYPE = R_ListType(R_GtvType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("GTXValue", "gtv", C_MessageType.ERROR, since = "0.6.1")

        type("gtv", rType = R_GtvType, since = "0.9.0") {
            comment("""
                Generic Transfer Value (GTV) is a general-purpose type for sending and decoding any data structure.
            """)

            staticFunction("from_bytes", "gtv", pure = true, since = "0.9.0") {
                comment("Decodes a `gtv` from a `byte_array`. Fails if it cannod be decoded.")
                alias("fromBytes", C_MessageType.ERROR, since = "0.6.1")
                param("bytes", "byte_array", comment = "Bytes to decode.")
                body { a ->
                    val bytes = a.asByteArray()
                    Rt_Utils.wrapErr("fn:gtv.from_bytes") {
                        val gtv = PostchainGtvUtils.bytesToGtv(bytes)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("from_bytes_or_null", "gtv?", pure = true, since = "0.13.0") {
                comment("Tries to decode a gtv from a `byte_array` and returns `null` if it fails.")
                param("bytes", "byte_array", comment = "Bytes to decode.")
                body { a ->
                    val bytes = a.asByteArray()
                    val gtv = try {
                        PostchainGtvUtils.bytesToGtv(bytes)
                    } catch (e: Throwable) {
                        null
                    }
                    if (gtv == null) Rt_NullValue else Rt_GtvValue.get(gtv)
                }
            }

            staticFunction("from_json", "gtv", pure = true, since = "0.9.0") {
                comment("Decodes a `gtv` from a JSON string representation.")
                alias("fromJSON", C_MessageType.ERROR, since = "0.6.1")
                param("json", "text", comment = "JSON string to decode")
                body { a ->
                    val str = a.asString()
                    Rt_Utils.wrapErr("fn:gtv.from_json(text)") {
                        val gtv = PostchainGtvUtils.jsonToGtv(str)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("from_json", "gtv", pure = true, since = "0.9.0") {
                comment("Decodes a `gtv` from a `json` representation.")
                alias("fromJSON", C_MessageType.ERROR, since = "0.6.1")
                param("json", "json", comment = "json to decode")
                body { a ->
                    val str = a.asJsonString()
                    Rt_Utils.wrapErr("fn:gtv.from_json(json)") {
                        val gtv = PostchainGtvUtils.jsonToGtv(str)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            function("to_bytes", "byte_array", pure = true, since = "0.9.0") {
                comment("Encodes this `gtv` to a `byte_array`.")
                alias("toBytes", C_MessageType.ERROR, since = "0.6.1")
                body { a ->
                    val gtv = a.asGtv()
                    val bytes = PostchainGtvUtils.gtvToBytes(gtv)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("to_json", "json", pure = true, since = "0.9.0") {
                comment("Encodes this `gtv` to a `json` representation.")
                alias("toJSON", C_MessageType.ERROR, since = "0.6.1")
                body { a ->
                    val gtv = a.asGtv()
                    val json = PostchainGtvUtils.gtvToJson(gtv)
                    //TODO consider making a separate function toJSONStr() to avoid unnecessary conversion str -> json -> str.
                    Rt_JsonValue.parse(json)
                }
            }
        }

        namespace("rell") {
            // Functions that are implicitly added to all types (subtypes of any): .hash(), .to_gtv(), .from_gtv(), etc.
            extension("gtv_ext", type = "T", since = "0.9.0") {
                generic("T", subOf = "any")

                staticFunction("from_gtv", result = "T", pure = true, since = "0.9.0") {
                    comment("Constructs this type from a `gtv`.")
                    param("gtv", type = "gtv", comment = "gtv to decode.")
                    makeFromGtvBody(this, pretty = false)
                }

                staticFunction("from_gtv_pretty", result = "T", pure = true, since = "0.9.0") {
                    comment("Constructs this type from a pretty formatted `gtv`.")
                    param("gtv", type = "gtv", comment = "gtv to decode.")
                    makeFromGtvBody(this, pretty = true, allowVirtual = false)
                }

                function("hash", result = "byte_array", pure = true, since = "0.9.0") {
                    comment("Computes the hash of this value.")
                    bodyMeta {
                        val selfType = this.fnBodyMeta.rSelfType
                        if (selfType is R_VirtualType) {
                            body { a ->
                                val virtual = a.asVirtual()
                                val gtv = virtual.gtv
                                val hash = Rt_Utils.wrapErr("fn:virtual:hash") {
                                    PostchainGtvUtils.merkleHash(gtv)
                                }
                                Rt_ByteArrayValue.get(hash)
                            }
                        } else {
                            validateToGtvBody(this, selfType)
                            body { a ->
                                val hash = Rt_Utils.wrapErr("fn:any:hash") {
                                    val gtv = selfType.rtToGtv(a, false)
                                    PostchainGtvUtils.merkleHash(gtv)
                                }
                                Rt_ByteArrayValue.get(hash)
                            }
                        }
                    }
                }

                function("to_gtv", result = "gtv", pure = true, since = "0.9.0") {
                    comment("Encodes this value to a `gtv` representation.")
                    makeToGtvBody(this, pretty = false)
                }

                function("to_gtv_pretty", result = "gtv", pure = true, since = "0.9.0") {
                    comment("Encodes this value to a pretty formatted `gtv` representation.")
                    makeToGtvBody(this, pretty = true)
                }
            }
        }
    }

    fun makeToGtvBody(m: Ld_FunctionDsl, pretty: Boolean): Ld_BodyResult = with(m) {
        bodyMeta {
            val selfType = this.fnBodyMeta.rSelfType
            validateToGtvBody(this, selfType)

            val fnNameCopy = this.fnSimpleName
            body { a ->
                val gtv = try {
                    selfType.rtToGtv(a, pretty)
                } catch (e: Throwable) {
                    throw Rt_Exception.common(fnNameCopy, e.message ?: "error")
                }
                Rt_GtvValue.get(gtv)
            }
        }
    }

    fun validateToGtvBody(m: Ld_FunctionMetaBodyDsl, type: R_Type) {
        val flags = type.completeFlags()
        if (!flags.gtv.toGtv) {
            reportUnavailableFunction(m, type)
        }
    }

    fun makeFromGtvBody(m: Ld_FunctionDsl, pretty: Boolean, allowVirtual: Boolean = true) = with(m) {
        bodyMeta {
            val resType = fnBodyMeta.rResultType
            validateFromGtvBody(this, resType, allowVirtual = allowVirtual)

            bodyContext { ctx, a ->
                val gtv = a.asGtv()
                Rt_Utils.wrapErr({ "fn:[${resType.strCode()}]:from_gtv:$pretty" }) {
                    val convCtx = GtvToRtContext.make(pretty = pretty, compilerOptions = ctx.globalCtx.compilerOptions)
                    val res = resType.gtvToRt(convCtx, gtv)
                    convCtx.finish(ctx.exeCtx)
                    res
                }
            }
        }
    }

    fun validateFromGtvBody(m: Ld_FunctionMetaBodyDsl, type: R_Type, allowVirtual: Boolean = true) {
        val flags = type.completeFlags()
        val valid = allowVirtual || type !is R_VirtualType
        if (!valid || !flags.gtv.fromGtv) {
            reportUnavailableFunction(m, type)
        }
    }

    private fun reportUnavailableFunction(m: Ld_FunctionMetaBodyDsl, type: R_Type) {
        val typeStr = type.name
        val fnName = m.fnSimpleName
        m.validationError("fn:invalid:$typeStr:$fnName", "Function '$fnName' not available for type '$typeStr'")
    }
}

object R_GtvType: R_PrimitiveType("gtv") {
    override fun isReference() = true
    override fun isDirectPure() = true
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Gtv
    override fun getLibTypeDef() = Lib_Rell.GTV_TYPE
}

class Rt_GtvValue private constructor(val value: Gtv): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.GTV.type()

    override fun type() = R_GtvType
    override fun asGtv() = value

    override fun equals(other: Any?) = other === this || (other is Rt_GtvValue && value == other.value)
    override fun hashCode() = value.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = "gtv[${str(StrFormat.V2)}]"

    override fun str(format: StrFormat): String {
        return when(format) {
            StrFormat.V1 -> toString(value)
            StrFormat.V2 -> value.toString()
        }
    }

    override fun strPretty(indent: Int): String {
        if (value.type == GtvType.ARRAY) {
            val array = value.asArray()
            if (array.isNotEmpty()) {
                val indentStr = "    ".repeat(indent)
                return array.joinToString(",", "[", "\n$indentStr]") {
                    val s = Rt_GtvValue(it).strPretty(indent + 1)
                    "\n$indentStr    $s"
                }
            }
        } else if (value.type == GtvType.DICT) {
            val map = value.asDict()
            if (map.isNotEmpty()) {
                val indentStr = "    ".repeat(indent)
                return map.entries.joinToString(",", "[", "\n$indentStr]") {
                    val k = GtvFactory.gtv(it.key).toString()
                    val v = Rt_GtvValue(it.value).strPretty(indent + 1)
                    "\n$indentStr    $k: $v"
                }
            }
        }

        return super.strPretty(indent)
    }

    companion object {
        val NULL: Rt_Value = Rt_GtvValue(GtvNull)

        private val ZERO_INTEGER: Rt_Value = Rt_GtvValue(GtvFactory.gtv(0))
        private val ZERO_BIG_INTEGER: Rt_Value = Rt_GtvValue(GtvFactory.gtv(BigInteger.ZERO))
        private val EMPTY_STRING: Rt_Value = Rt_GtvValue(GtvFactory.gtv(""))
        private val EMPTY_BYTE_ARRAY: Rt_Value = Rt_GtvValue(GtvFactory.gtv(ByteArray(0)))
        private val EMPTY_ARRAY: Rt_Value = Rt_GtvValue(GtvFactory.gtv(immListOf()))
        private val EMPTY_DICT: Rt_Value = Rt_GtvValue(GtvFactory.gtv(immMapOf()))

        fun get(value: Gtv): Rt_Value {
            return when (value) {
                GtvNull -> NULL
                is GtvInteger -> if (value.integer == 0L) ZERO_INTEGER else Rt_GtvValue(value)
                is GtvBigInteger -> if (value.integer == BigInteger.ZERO) ZERO_BIG_INTEGER else Rt_GtvValue(value)
                is GtvString -> if (value.string.isEmpty()) EMPTY_STRING else Rt_GtvValue(value)
                is GtvByteArray -> if (value.bytearray.isEmpty()) EMPTY_BYTE_ARRAY else Rt_GtvValue(value)
                is GtvArray -> if (value.array.isEmpty()) EMPTY_ARRAY else Rt_GtvValue(value)
                is GtvDictionary -> if (value.dict.isEmpty()) EMPTY_DICT else Rt_GtvValue(value)
                else -> Rt_GtvValue(value)
            }
        }

        fun toString(value: Gtv): String {
            return try {
                PostchainGtvUtils.gtvToJson(value)
            } catch (e: Exception) {
                value.toString() // Fallback, just in case (did not happen).
            }
        }
    }
}

private object GtvRtConversion_Gtv: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = rt.asGtv()

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return ctx.rtValue {
            Rt_GtvValue.get(gtv)
        }
    }
}
