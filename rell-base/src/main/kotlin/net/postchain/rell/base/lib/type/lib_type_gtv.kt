/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.utils.toIntExactOrNull
import java.math.BigInteger

object Lib_Type_Gtv {
    val LIST_OF_GTV_TYPE = R_ListType(R_GtvType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("GTXValue", "gtv", C_MessageType.ERROR, since = "0.6.1")

        type("gtv", rType = R_GtvType, since = "0.9.0") {
            comment("""
                Generic Transfer Value (GTV) is a data type for the serialization and transfer of structured data, much
                like JSON.

                GTV is used in Rell to encode operation and query arguments and results that are exchanged with clients.
                Unlike JSON, GTV has a stable byte serialization format and well-defined cryptographic hash, making it
                well-suited to this purpose. In addition, GTV supports byte arrays.

                GTV supports the following types:

                | GTV Type     | Closest Rell Equivalent |
                | ------------ | ----------------------- |
                | `NULL`       | `null`                  |
                | `BYTEARRAY`  | `byte_array`            |
                | `STRING`     | `text`                  |
                | `INTEGER`    | `integer`               |
                | `DICT`       | `map<text, gtv>`        |
                | `ARRAY`      | `list<gtv>`             |
                | `BIGINTEGER` | `big_integer`           |

                GTV does not support all Rell types, so not every value in Rell can be converted to GTV. For example,
                GTV has no support for non-integer numbers, and therefore the `decimal` type is encoded in GTV as
                text.

                Rell types can be encoded as GTV in two modes: *compact* and *pretty*, and the distinction between the
                two is a real semantic difference, and is not merely a difference in whitespace when converted to text.
                The two modes differ in the following ways:

                - Compact GTV encode struct values as a lists of attributes, while pretty GTV encode them as a
                  dictionaries (thus struct member names are preserved).
                - Compact GTV encode named-field tuples as a lists of attributes, while pretty GTV encode them as a
                  dictionaries (thus tuple field names are preserved). There is no difference between the two in
                  encoding of unnamed-field tuples.

                Examples of GTV:

                ```rell
                >>> (x = 1, y = 'a', z = true).to_gtv()
                [1,"a",1]
                >>> (x = 1, y = 'a', z = false).to_gtv_pretty()
                {"x":1,"y":"a","z":0}
                >>> [1: 'a', 2: 'b', 3: 'c'].to_gtv()
                [[1,"a"],[2,"b"],[3,"c"]]
                >>> [1: 'a', 2: 'b', 3: 'c'].to_gtv_pretty()
                [[1,"a"],[2,"b"],[3,"c"]]
                >>> set([1, 2, 3, 4]).to_gtv()
                [1,2,3,4]
                >>> set([1, 2, 3, 4]).to_gtv_pretty()
                [1,2,3,4]
                >>> struct a { x: integer; y: decimal; };
                >>> a(10, 10.1).to_gtv()
                [10,"10.1"]
                >>> a(10, 10.1).to_gtv_pretty()
                {"x":10,"y":"10.1"}
                ```

                Rell operations expect their arguments as compact-encoded GTV, whereas queries expect pretty-encoded GTV
                arguments, hence client applications are required to use those respective formats when making operation
                and query calls to Rell applications.
            """)

            staticFunction("from_bytes", "gtv", pure = true, since = "0.9.0") {
                comment("""
                    Decode a GTV from a byte array.

                    Inverse of `gtv.to_bytes()`.
                    @return the decoded GTV
                    @throws exception if the byte array does not encode a well-formed GTV; i.e. if a GTV cannot be
                    decoded
                """)
                alias("fromBytes", C_MessageType.ERROR, since = "0.6.1")
                param("bytes", "byte_array", comment = "the byte array to decode")
                body { a ->
                    val bytes = a.asByteArray()
                    Rt_Utils.wrapErr("fn:gtv.from_bytes") {
                        val gtv = PostchainGtvUtils.bytesToGtv(bytes)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("from_bytes_or_null", "gtv?", pure = true, since = "0.13.0") {
                comment("""
                    Decode a GTV from a byte array.
                    @return the decoded GTV, or `null` if the byte array does not encode a well-formed GTV; i.e. if a
                    GTV cannot be decoded
                """)
                param("bytes", "byte_array", comment = "the byte array to decode")
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
                comment("""
                    Obtain a GTV from JSON text.

                    First parses a JSON value from text, and then converts the JSON value to a GTV.

                    Equivalent to `gtv.from_json(json(text))`.
                    @throws exception when:
                    - the JSON text is ill-formed
                    - the JSON value cannot be converted to a GTV
                """)
                alias("fromJSON", C_MessageType.ERROR, since = "0.6.1")
                param("json", "text", comment = "the JSON text to decode")
                body { a ->
                    val str = a.asString()
                    Rt_Utils.wrapErr("fn:gtv.from_json(text)") {
                        val gtv = PostchainGtvUtils.jsonToGtv(str)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("from_json", "gtv", pure = true, since = "0.9.0") {
                comment("""
                    Convert a JSON value to a GTV.

                    Inverse of `gtv.to_json()`.
                    @throws exception if the JSON value cannot be converted to a GTV
                """)
                alias("fromJSON", C_MessageType.ERROR, since = "0.6.1")
                param("json", "json", comment = "the JSON to convert")
                body { a ->
                    val str = a.asJsonString()
                    Rt_Utils.wrapErr("fn:gtv.from_json(json)") {
                        val gtv = PostchainGtvUtils.jsonToGtv(str)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("legacy_hash", result = "byte_array", pure = true, since = "0.14.5") {
                comment("**Deprecated**; use instead `x.hash()` for a value `x` of any type.")
                generic("T", subOf = "any")
                param("value", "T")
                param("version", "integer")
                bodyMeta {
                    val valueType = this.fnBodyMeta.rTypeArgs.getValue("T")
                    if (valueType is R_VirtualType) {
                        bodyContext { ctx, a, b ->
                            calcHashVirtual(ctx, a, b, this.fnQualifiedName)
                        }
                    } else {
                        validateToGtvBody(this, valueType)
                        bodyContext { ctx, a, b ->
                            calcHashNormal(ctx, valueType, a, b, this.fnQualifiedName)
                        }
                    }
                }
            }

            function("to_bytes", "byte_array", pure = true, since = "0.9.0") {
                comment("""
                    Encode this GTV as byte array.

                    Inverse of `gtv.from_bytes(byte_array)`.
                    @return a byte array containing an encoding of this GTV
                """)
                alias("toBytes", C_MessageType.ERROR, since = "0.6.1")
                body { a ->
                    val gtv = a.asGtv()
                    val bytes = PostchainGtvUtils.gtvToBytes(gtv)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("to_json", "json", pure = true, since = "0.9.0") {
                comment("""
                    Convert this GTV to a JSON value.

                    Inverse of `gtv.from_json(json)`.
                    @return a JSON value equivalent to this GTV
                    @throws exception if this GTV cannot be converted to a JSON value
                """)
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
                    comment("""
                        Decode a value of this type from a compact-encoded GTV.

                        Inverse of `any.to_gtv()`.

                        Note that the encoding (compact or pretty) of the given GTV will affect the types to which that
                        GTV can be decoded. For instance, given the struct:

                        ```rell
                        struct c { x: text; y: text; };
                        ```

                        An instance of `c` cannot be decoded with `c.from_gtv(my_gtv)` if `my_gtv` was pretty-encoded
                        from another `c` (i.e. with `c.to_gtv_pretty()`). In other words, the following throws an
                        exception:

                        ```rell
                        c.from_gtv(c(x = 'lol', y = 'haha').to_gtv_pretty()) // Run-time error
                        ```

                        However, such a GTV *could* be decoded to a `map<text, text>`:

                        ```rell
                        map<text, text>.from_gtv(c(x = 'lol', y = 'haha').to_gtv_pretty()) // returns ['x': 'lol', 'y': 'haha']
                        ```

                        @throws exception if the structure of the given GTV is incompatible with this type
                    """)
                    param("gtv", type = "gtv", comment = "the compact-encoded GTV to decode")
                    makeFromGtvBody(this, pretty = false)
                }

                staticFunction("from_gtv_pretty", result = "T", pure = true, since = "0.9.0") {
                    comment("""
                        Decode a value of this type from a pretty-encoded GTV.

                        Note that the encoding (compact or pretty) of the given GTV will affect the types to which that
                        GTV can be decoded.

                        Tolerates compact-encoded GTV where possible. For instance, given the struct:

                        ```rell
                        struct c { x: text; y: text; };
                        ```

                        A GTV created with `c(...).to_gtv()` can be decoded back to a `c` with `c.from_gtv_pretty(...)`.
                        In other words, the following is legal:

                        ```rell
                        c.from_gtv_pretty(c(x = 'lol', y = 'haha').to_gtv()) // returns c{x=lol,y=haha}
                        ```

                        @throws exception if the structure of the given GTV is incompatible with this type
                    """)
                    param("gtv", type = "gtv", comment = "the pretty-encoded GTV to decode")
                    makeFromGtvBody(this, pretty = true, allowVirtual = false)
                }

                function("hash", result = "byte_array", pure = true, since = "0.9.0") {
                    comment("""
                        Compute the Merkle Hash of this value.
                        @return the Merkle Hash of this value as a byte array of length 32
                    """)
                    bodyMeta {
                        val selfType = this.fnBodyMeta.rSelfType
                        if (selfType is R_VirtualType) {
                            bodyContext { ctx, a ->
                                calcHashVirtual(ctx, a, null, "fn:virtual:hash")
                            }
                        } else {
                            validateToGtvBody(this, selfType)
                            bodyContext { ctx, a ->
                                calcHashNormal(ctx, selfType, a, null, "fn:any:hash")
                            }
                        }
                    }
                }

                function("to_gtv", result = "gtv", pure = true, since = "0.9.0") {
                    comment("Encode this value as a compact GTV.")
                    makeToGtvBody(this, pretty = false)
                }

                function("to_gtv_pretty", result = "gtv", pure = true, since = "0.9.0") {
                    comment("Encode this value as pretty GTV.")
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
                    gtvToRt(ctx, resType, gtv, pretty)
                }
            }
        }
    }

    fun gtvToRt(ctx: Rt_CallContext, type: R_Type, gtv: Gtv, pretty: Boolean): Rt_Value {
        val convCtx = GtvToRtContext.make(
            pretty = pretty,
            defaultValueEvaluator = GtvToRtDefaultValueEvaluator.getStructDefault(ctx.exeCtx),
            compilerOptions = ctx.globalCtx.compilerOptions,
        )

        val res = type.gtvToRt(convCtx, gtv)
        convCtx.finish(ctx.exeCtx)
        return res
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

    private fun calcHashNormal(
        ctx: Rt_CallContext,
        valueType: R_Type,
        value: Rt_Value,
        version: Rt_Value?,
        fnName: String,
    ): Rt_Value {
        val hash = Rt_Utils.wrapErr(fnName) {
            val gtv = valueType.rtToGtv(value, false)
            calcHash0(ctx, gtv, version)
        }
        return Rt_ByteArrayValue.get(hash)
    }

    private fun calcHashVirtual(ctx: Rt_CallContext, value: Rt_Value, version: Rt_Value?, fnName: String): Rt_Value {
        val virtual = value.asVirtual()
        val gtv = virtual.gtv
        val hash = Rt_Utils.wrapErr(fnName) {
            calcHash0(ctx, gtv, version)
        }
        return Rt_ByteArrayValue.get(hash)
    }

    private fun calcHash0(ctx: Rt_CallContext, gtv: Gtv, version: Rt_Value?): ByteArray {
        val iVersion = if (version == null) null else {
            val v = version.asInteger().toIntExactOrNull()
            v ?: throw IllegalArgumentException("Hash version out of range: $version")
        }
        return ctx.appCtx.gtvHashCalculator.hash(gtv, iVersion)
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
        return Rt_GtvValue.get(gtv)
    }
}
