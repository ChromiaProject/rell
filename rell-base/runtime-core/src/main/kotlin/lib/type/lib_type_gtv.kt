/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VirtualType
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.toIntExactOrNull

object Lib_Type_Gtv {
    val LIST_OF_GTV_TYPE = Rt_ListType(Rt_PrimitiveTypes.GTV)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("GTXValue", "gtv", C_MessageType.ERROR, since = "0.6.1")

        enum("gtv_type", since = "0.15.3") {
            """
                Represents the type of a GTV value.

                Use `gtv.type` to get the type of a GTV value.
            """.comment()

            value("NULL")
            value("BYTEARRAY")
            value("STRING")
            value("INTEGER")
            value("DICT")
            value("ARRAY")
            value("BIGINTEGER")
        }

        type(Rt_GtvValue, "gtv", rrType = RR_Type.Primitive(RR_PrimitiveKind.GTV), since = "0.9.0") {
            """
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
            """.comment()

            staticFunction("from_bytes", pure = true, since = "0.9.0") {
                """
                    Decode a GTV from a byte array.

                    Inverse of `gtv.to_bytes()`.
                    @return the decoded GTV
                    @throws exception if the byte array does not encode a well-formed GTV; i.e. if a GTV cannot be
                    decoded
                """.comment()

                val bytes by param(Rt_ByteArrayValue, comment = "the byte array to decode")
                alias("fromBytes", C_MessageType.ERROR, since = "0.6.1")
                body(Rt_GtvValue) {
                    Rt_Utils.wrapErr("fn:gtv.from_bytes") {
                        PostchainGtvUtils.bytesToGtv(bytes.value)
                    }
                }
            }

            staticFunction("from_bytes_or_null", "gtv?", pure = true, since = "0.13.0") {
                """
                    Decode a GTV from a byte array.
                    @return the decoded GTV, or `null` if the byte array does not encode a well-formed GTV; i.e. if a
                    GTV cannot be decoded
                """.comment()
                val bytes by param(Rt_ByteArrayValue, comment = "the byte array to decode")
                body {
                    val gtv = try {
                        PostchainGtvUtils.bytesToGtv(bytes.value)
                    } catch (_: Throwable) {
                        null
                    }
                    if (gtv == null) Rt_NullValue else Rt_GtvValue.get(gtv)
                }
            }

            staticFunction("from_json", pure = true, since = "0.9.0") {
                """
                    Obtain a GTV from JSON text.

                    First parses a JSON value from text, and then converts the JSON value to a GTV.

                    Equivalent to `gtv.from_json(json(text))`.
                    @throws exception when:
                    - the JSON text is ill-formed
                    - the JSON value cannot be converted to a GTV
                """.comment()
                val json by param(Rt_TextValue, comment = "the JSON text to decode")
                alias("fromJSON", C_MessageType.ERROR, since = "0.6.1")
                body(Rt_GtvValue) {
                    Rt_Utils.wrapErr("fn:gtv.from_json(text)") {
                        PostchainGtvUtils.jsonToGtv(json.value)
                    }
                }
            }

            staticFunction("from_json", pure = true, since = "0.9.0") {
                """
                    Convert a JSON value to a GTV.

                    Inverse of `gtv.to_json()`.
                    @throws exception if the JSON value cannot be converted to a GTV
                """.comment()
                val json by param(Rt_JsonValue, comment = "the JSON to convert")
                alias("fromJSON", C_MessageType.ERROR, since = "0.6.1")
                body(Rt_GtvValue) {
                    Rt_Utils.wrapErr("fn:gtv.from_json(json)") {
                        PostchainGtvUtils.jsonToGtv(json.str)
                    }
                }
            }

            staticFunction("legacy_hash", result = "byte_array", pure = true, since = "0.14.5") {
                comment("**Deprecated**; use instead `x.hash()` for a value `x` of any type.")
                generic("T", subOf = "any")
                val value by param("T", cast = Rt_Value)
                val version by param(Rt_IntValue)
                bodyMeta {
                    val valueType = this.fnBodyMeta.rTypeArgs.getValue("T")
                    if (typeArgRrType("T").isVirtual()) {
                        bodyContext { ctx ->
                            calcHashVirtual(ctx, value, version, this.fnQualifiedName)
                        }
                    } else {
                        validateToGtvBody(this, valueType)
                        val valueR = typeArgR("T")
                        bodyContext { ctx ->
                            val valueRt = ctx.exeCtx.appCtx.interpreter.resolveRType(valueR)
                            calcHashNormal(ctx, valueRt, value, version, this.fnQualifiedName)
                        }
                    }
                }
            }

            function("to_bytes", pure = true, since = "0.9.0") {
                """
                    Encode this GTV as byte array.

                    Inverse of `gtv.from_bytes(byte_array)`.
                    @return a byte array containing an encoding of this GTV
                """.comment()
                val self by self()
                alias("toBytes", C_MessageType.ERROR, since = "0.6.1")
                body(Rt_ByteArrayValue) {
                    PostchainGtvUtils.gtvToBytes(self.value)
                }
            }

            function("to_json", "json", pure = true, since = "0.9.0") {
                """
                    Convert this GTV to a JSON value.

                    Big integers are serialized as JSON numbers. Note that when deserializing back with
                    `gtv.from_json()`, small big integers (within standard integer range) will become regular
                    integers, and large big integers cannot be deserialized (will cause a runtime error).

                    @return a JSON value equivalent to this GTV
                """.comment()
                val self by self()
                alias("toJSON", C_MessageType.ERROR, since = "0.6.1")
                body {
                    val json = PostchainGtvUtils.gtvToJson(self.value, true)
                    //TODO consider making a separate function toJSONStr() to avoid unnecessary conversion str -> json -> str.
                    Rt_JsonValue.parse(json)
                }
            }

            property("type", type = "gtv_type", pure = true, since = "0.15.3") {
                """
                    Returns the type of this GTV value.

                    Examples:

                    ```rell
                    (123).to_gtv().type // gtv_type.INTEGER
                    'hello'.to_gtv().type // gtv_type.STRING
                    [1, 2, 3].to_gtv().type // gtv_type.ARRAY
                    ```
                """.comment()
                value(Rt_GtvValue) { a ->
                    val gtv = a.value
                    val ordinal = gtv.type.ordinal
                    Lib_Rell.GTV_TYPE_ENUM.rtGetValueOrNull(ordinal)
                        ?: throw Rt_Exception.common("gtv:type:unknown", "Unknown GTV type: ${gtv.type}")
                }
            }
        }

        namespace("rell") {
            // Functions that are implicitly added to all types (subtypes of any): .hash(), .to_gtv(), .from_gtv(), etc.
            extension("gtv_ext", type = "T", since = "0.9.0") {
                generic("T", subOf = "any")

                staticFunction("from_gtv", result = "T", pure = true, since = "0.9.0") {
                    """
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
                    """.comment()
                    val gtv by param(Rt_GtvValue, comment = "the compact-encoded GTV to decode")
                    makeFromGtvBody(this, { gtv }, pretty = false)
                }

                staticFunction("from_gtv_pretty", result = "T", pure = true, since = "0.9.0") {
                    """
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
                    """.comment()
                    val gtv by param(Rt_GtvValue, comment = "the pretty-encoded GTV to decode")
                    makeFromGtvBody(this, { gtv }, pretty = true, allowVirtual = false)
                }

                function("hash", result = "byte_array", pure = true, since = "0.9.0") {
                    """
                        Compute the Merkle Hash of this value.
                        @return the Merkle Hash of this value as a byte array of length 32
                    """.comment()
                    val self by self(Rt_Value)
                    bodyMeta {
                        if (selfTypeRr.isVirtual()) {
                            bodyContext { ctx ->
                                calcHashVirtual(ctx, self, null, "fn:virtual:hash")
                            }
                        } else {
                            val selfType = this.fnBodyMeta.rSelfType
                            validateToGtvBody(this, selfType)
                            val selfR = selfTypeR
                            bodyContext { ctx ->
                                val selfRt = ctx.exeCtx.appCtx.interpreter.resolveRType(selfR)
                                calcHashNormal(ctx, selfRt, self, null, "fn:any:hash")
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
        val self by self(Rt_Value)
        bodyMeta {
            validateToGtvBody(this, fnBodyMeta.rSelfType)
            val selfR = selfTypeR

            val fnNameCopy = this.fnSimpleName
            bodyContext { ctx ->
                val selfRt = ctx.exeCtx.appCtx.interpreter.resolveRType(selfR)
                val gtv = try {
                    selfRt.gtvConversion!!.rtToGtv(self, pretty)
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

    fun makeFromGtvBody(
        m: Ld_FunctionDsl,
        gtv: () -> Rt_GtvValue,
        pretty: Boolean,
        allowVirtual: Boolean = true,
    ) = with(m) {
        bodyMeta {
            validateFromGtvBody(this, fnBodyMeta.rResultType, allowVirtual = allowVirtual)
            val resR = resultTypeR

            bodyContext { ctx ->
                val resRt = ctx.exeCtx.appCtx.interpreter.resolveRType(resR)
                val gtvArg = gtv().value
                Rt_Utils.wrapErr({ "fn:[${resRt.name}]:from_gtv:$pretty" }) {
                    gtvToRt(ctx, resRt, gtvArg, pretty)
                }
            }
        }
    }

    fun gtvToRt(ctx: Rt_CallContext, rtType: Rt_ValueClass<*>, gtv: Gtv, pretty: Boolean): Rt_Value {
        val convCtx = GtvToRtContext.make(
            pretty = pretty,
            defaultValueEvaluator = GtvToRtDefaultValueEvaluator.getStructDefault(ctx.exeCtx),
            compilerOptions = ctx.globalCtx.compilerOptions,
        )

        val res = rtType.gtvConversion!!.gtvToRt(convCtx, gtv)
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
        valueRt: Rt_ValueClass<*>,
        value: Rt_Value,
        version: Rt_Value?,
        fnName: String,
    ): Rt_Value {
        val hash = Rt_Utils.wrapErr(fnName) {
            val gtv = valueRt.gtvConversion!!.rtToGtv(value, false)
            calcHash0(ctx, gtv, version)
        }
        return Rt_ByteArrayValue.get(hash)
    }

    private fun calcHashVirtual(ctx: Rt_CallContext, value: Rt_Value, version: Rt_Value?, fnName: String): Rt_Value {
        val virtual = (value as Rt_VirtualValue)
        val gtv = virtual.gtv
        val hash = Rt_Utils.wrapErr(fnName) {
            calcHash0(ctx, gtv, version)
        }
        return Rt_ByteArrayValue.get(hash)
    }

    private fun calcHash0(ctx: Rt_CallContext, gtv: Gtv, version: Rt_Value?): ByteArray {
        val iVersion = if (version == null) null else {
            val v = (version as Rt_IntValue).value.toIntExactOrNull()
            requireNotNull(v) { "Hash version out of range: $version" }
        }
        return ctx.appCtx.gtvHashCalculator.hash(gtv, iVersion)
    }
}
