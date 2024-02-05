/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionMetaBodyDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvType
import net.postchain.rell.base.model.R_ListType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.model.R_VirtualType
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.PostchainGtvUtils

object Lib_Type_Gtv {
    val LIST_OF_GTV_TYPE = R_ListType(R_GtvType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        alias("GTXValue", "gtv", C_MessageType.ERROR)

        type("gtv", rType = R_GtvType) {
            staticFunction("from_bytes", "gtv", pure = true) {
                alias("fromBytes", C_MessageType.ERROR)
                param("bytes", "byte_array")
                body { a ->
                    val bytes = a.asByteArray()
                    Rt_Utils.wrapErr("fn:gtv.from_bytes") {
                        val gtv = PostchainGtvUtils.bytesToGtv(bytes)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("from_bytes_or_null", "gtv?", pure = true) {
                param("bytes", "byte_array")
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

            staticFunction("from_json", "gtv", pure = true) {
                alias("fromJSON", C_MessageType.ERROR)
                param("json", "text")
                body { a ->
                    val str = a.asString()
                    Rt_Utils.wrapErr("fn:gtv.from_json(text)") {
                        val gtv = PostchainGtvUtils.jsonToGtv(str)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            staticFunction("from_json", "gtv", pure = true) {
                alias("fromJSON", C_MessageType.ERROR)
                param("json", "json")
                body { a ->
                    val str = a.asJsonString()
                    Rt_Utils.wrapErr("fn:gtv.from_json(json)") {
                        val gtv = PostchainGtvUtils.jsonToGtv(str)
                        Rt_GtvValue.get(gtv)
                    }
                }
            }

            function("to_bytes", "byte_array", pure = true) {
                alias("toBytes", C_MessageType.ERROR)
                body { a ->
                    val gtv = a.asGtv()
                    val bytes = PostchainGtvUtils.gtvToBytes(gtv)
                    Rt_ByteArrayValue.get(bytes)
                }
            }

            function("to_json", "json", pure = true) {
                alias("toJSON", C_MessageType.ERROR)
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
            extension("gtv_ext", type = "T") {
                generic("T", subOf = "any")

                staticFunction("from_gtv", result = "T", pure = true) {
                    param("gtv", type = "gtv")
                    makeFromGtvBody(this, pretty = false)
                }

                staticFunction("from_gtv_pretty", result = "T", pure = true) {
                    param("gtv", type = "gtv")
                    makeFromGtvBody(this, pretty = true, allowVirtual = false)
                }

                function("hash", result = "byte_array", pure = true) {
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

                function("to_gtv", result = "gtv", pure = true) {
                    makeToGtvBody(this, pretty = false)
                }

                function("to_gtv_pretty", result = "gtv", pure = true) {
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
                    val convCtx = GtvToRtContext.make(pretty = pretty)
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
