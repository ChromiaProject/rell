/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.type.Lib_Type_Gtv.gtvToRt
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_DestinationExpr
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_MemberCalculator
import net.postchain.rell.base.model.expr.R_StructMemberExpr
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.PostchainGtvUtils
import net.postchain.rell.base.utils.doc.DocCode
import net.postchain.rell.base.utils.mapToImmList

object Lib_Type_Struct {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("struct", abstract = true, hidden = true, since = "0.10.0") {
            supertypeStrategySpecial { mType ->
                L_TypeUtils.getRType(mType) is R_StructType
            }
        }

        type("mirror_struct", abstract = true, hidden = true, since = "0.10.4") {
            generic("T")
        }

        type("mutable_mirror_struct", abstract = true, hidden = true, since = "0.10.4") {
            generic("T")
            parent("mirror_struct<T>")

            rTypeMeta(R_StructType.MUTABLE_META)

            docCode { t ->
                DocCode.builder()
                    .keyword("struct").raw("<").keyword("mutable").raw(" ").append(t).raw(">")
                    .build()
            }

            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRType(mType)
                rType is R_StructType && rType.struct == rType.struct.mirrorStructs?.mutable
            }

            function("to_immutable", result = "immutable_mirror_struct<T>", pure = true, since = "0.10.4") {
                comment("Convert this struct to an immutable version.")
                body { a ->
                    toMutableOrImmutable(a, false, "to_immutable")
                }
            }
        }

        type("immutable_mirror_struct", abstract = true, hidden = true, since = "0.10.4") {
            generic("T")
            parent("mirror_struct<T>")

            rTypeMeta(R_StructType.IMMUTABLE_META)

            docCode { t ->
                DocCode.builder()
                    .keyword("struct").raw("<").append(t).raw(">")
                    .build()
            }

            supertypeStrategySpecial { mType ->
                val rType = L_TypeUtils.getRType(mType)
                rType is R_StructType && rType.struct == rType.struct.mirrorStructs?.immutable
            }

            function("to_mutable", result = "mutable_mirror_struct<T>", pure = true, since = "0.10.4") {
                comment("Convert this structure to a mutable version.")
                body { a ->
                    toMutableOrImmutable(a, true, "to_mutable")
                }
            }
        }

        namespace("rell") {
            extension("struct_ext", type = "T", since = "0.6.1") {
                generic("T", subOf = "struct")

                function("to_bytes", "byte_array", pure = true, since = "0.9.0") {
                    comment("""
                        Convert this structure to a `byte_array` representation.

                        Same as `.to_gtv().to_bytes()`.
                    """)
                    alias("toBytes", C_MessageType.ERROR, since = "0.6.1")
                    bodyMeta {
                        val selfType = this.fnBodyMeta.rSelfType
                        Lib_Type_Gtv.validateToGtvBody(this, selfType)

                        body { a ->
                            val gtv = selfType.rtToGtv(a, false)
                            val bytes = PostchainGtvUtils.gtvToBytes(gtv)
                            Rt_ByteArrayValue.get(bytes)
                        }
                    }
                }

                // Right to_gtv*() functions are added by default, and here we add deprecated functions for compatibility
                // (they used to exist only for structs, not for all types).
                function("toGTXValue", "gtv", pure = true, since = "0.6.1") {
                    deprecated(newName = "to_gtv")
                    Lib_Type_Gtv.makeToGtvBody(this, pretty = false)
                }

                function("toPrettyGTXValue", "gtv", pure = true, since = "0.6.1") {
                    deprecated(newName = "to_gtv_pretty")
                    Lib_Type_Gtv.makeToGtvBody(this, pretty = true)
                }

                staticFunction("from_bytes", result = "T", pure = true, since = "0.9.0") {
                    comment("Decodes a struct from a byte_array. Fails if the bytes cannot represent this struct.")
                    alias("fromBytes", C_MessageType.ERROR, since = "0.6.1")
                    param("bytes", type = "byte_array", comment = "Bytes to decode from.")

                    bodyMeta {
                        val resType = fnBodyMeta.rResultType
                        Lib_Type_Gtv.validateFromGtvBody(this, resType)

                        bodyContext { ctx, a ->
                            val bytes = a.asByteArray()
                            Rt_Utils.wrapErr("fn:struct:from_bytes") {
                                val gtv = PostchainGtvUtils.bytesToGtv(bytes)
                                gtvToRt(ctx, resType, gtv, pretty = false)
                            }
                        }
                    }
                }

                // Right from_gtv*() functions are added by default, and here we add deprecated functions for compatibility
                // (they used to exist only for structs, not for all types).
                staticFunction("fromGTXValue", result = "T", pure = true, since = "0.6.1") {
                    deprecated(newName = "from_gtv")
                    param("gtv", type = "gtv")
                    Lib_Type_Gtv.makeFromGtvBody(this, pretty = false)
                }

                staticFunction("fromPrettyGTXValue", result = "T", pure = true, since = "0.6.1") {
                    deprecated(newName = "from_gtv_pretty")
                    param("gtv", type = "gtv")
                    Lib_Type_Gtv.makeFromGtvBody(this, pretty = true)
                }
            }
        }
    }

    fun getValueMembers(struct: R_Struct): ImmList<C_TypeValueMember> {
        return struct.attributesList.mapToImmList {
            val mem = C_MemberAttr_RegularStructAttr(it, struct)
            C_TypeValueMember_BasicAttr(mem)
        }
    }

    fun decodeOperation(v: Rt_Value): Pair<R_MountName, ImmList<Gtv>> {
        val sv = v.asStruct()

        val structType = sv.type()
        val op = Rt_Utils.checkNotNull(structType.struct.mirrorStructs?.operation) {
            // Must not happen, checking for extra safety.
            "bad_struct_type:${sv.type()}" toCodeMsg "Wrong struct type: ${sv.type()}"
        }

        val rtArgs = structType.struct.attributesList.map { sv.get(it.index) }
        val gtvArgs = rtArgs.mapToImmList { it.type().rtToGtv(it, false) }

        return op.mountName to gtvArgs
    }

    private class C_MemberAttr_RegularStructAttr(
        attr: R_Attribute,
        private val struct: R_Struct,
    ): C_MemberAttr_StructAttr(attr.type, attr) {
        override fun vAttr(exprCtx: C_ExprContext, selfType: R_Type, pos: S_Pos): V_MemberAttr {
            return V_MemberAttr_RegularStructAttr(type, attr, struct)
        }

        private inner class V_MemberAttr_RegularStructAttr(
            type: R_Type,
            attr: R_Attribute,
            private val struct: R_Struct,
        ): V_MemberAttr_StructAttr(type, attr) {
            override fun calculator() = R_MemberCalculator_StructAttr(attr)

            override fun destination(pos: S_Pos, base: R_Expr): R_DestinationExpr {
                if (!attr.mutable) {
                    throw C_Errors.errAttrNotMutable(pos, attr.name, "${struct.name}.${attr.name}")
                }
                return R_StructMemberExpr(base, attr)
            }
        }
    }

    private class R_MemberCalculator_StructAttr(val attr: R_Attribute): R_MemberCalculator(attr.type) {
        override fun calculate(frame: Rt_CallFrame, baseValue: Rt_Value): Rt_Value {
            val structValue = baseValue.asStruct()
            return structValue.get(attr.index)
        }
    }

    private fun toMutableOrImmutable(arg: Rt_Value, returnMutable: Boolean, name: String): Rt_Value {
        val v = arg.asStruct()

        val structType = v.type()
        val mirrorStructs = Rt_Utils.checkNotNull(structType.struct.mirrorStructs) {
            // Must not happen, checking for extra safety.
            "$name:bad_type:${v.type()}" toCodeMsg "Wrong struct type: ${v.type()}"
        }

        val resultType = mirrorStructs.getStruct(returnMutable).type
        if (structType == resultType) {
            return arg
        }

        val values = structType.struct.attributesList.map { v.get(it.index) }.toMutableList()
        return Rt_StructValue.createValidated(resultType, values)
    }
}
