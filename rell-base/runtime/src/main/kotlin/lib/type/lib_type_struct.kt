/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.expr.*
import net.postchain.rell.base.compiler.base.fn.*
import net.postchain.rell.base.compiler.base.lib.C_LibFuncCaseCtx
import net.postchain.rell.base.compiler.base.lib.C_MemberRestrictions
import net.postchain.rell.base.compiler.base.lib.C_SpecialLibMemberFunctionBody
import net.postchain.rell.base.compiler.base.lib.V_SpecialMemberFunctionCall
import net.postchain.rell.base.compiler.base.utils.C_Errors
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.compiler.vexpr.V_CreateExprAttr
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.compiler.vexpr.V_StructExpr
import net.postchain.rell.base.compiler.vexpr.V_ValueMemberExpr
import net.postchain.rell.base.lib.type.Lib_Type_Gtv.gtvToRt
import net.postchain.rell.base.lmodel.L_TypeUtils
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.*
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.mtype.M_ParamArity
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode

object Lib_Type_Struct {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("struct", abstract = true, hidden = true, since = "0.10.0") {
            supertypeStrategySpecial { mType ->
                L_TypeUtils.getRTypeOrNull(mType) is R_StructType
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
                val rType = L_TypeUtils.getRTypeOrNull(mType)
                rType is R_StructType && rType.struct == rType.struct.mirrorStructs?.mutable
            }

            function("to_immutable", result = "immutable_mirror_struct<T>", pure = true, since = "0.10.4") {
                comment("Convert this struct to an immutable version.")
                bodyContext { ctx, a ->
                    toMutableOrImmutable(ctx, a, false, "to_immutable")
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
                val rType = L_TypeUtils.getRTypeOrNull(mType)
                rType is R_StructType && rType.struct == rType.struct.mirrorStructs?.immutable
            }

            function("to_mutable", result = "mutable_mirror_struct<T>", pure = true, since = "0.10.4") {
                comment("Convert this structure to a mutable version.")
                bodyContext { ctx, a ->
                    toMutableOrImmutable(ctx, a, true, "to_mutable")
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
                        Lib_Type_Gtv.validateToGtvBody(this, fnBodyMeta.rSelfType)
                        val selfRt = selfTypeRt

                        body { a ->
                            val gtv = selfRt.gtvConversion!!.rtToGtv(a, false)
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
                        Lib_Type_Gtv.validateFromGtvBody(this, fnBodyMeta.rResultType)
                        val resRt = resultTypeRt

                        bodyContext { ctx, a ->
                            val bytes = a.asByteArray()
                            Rt_Utils.wrapErr("fn:struct:from_bytes") {
                                val gtv = PostchainGtvUtils.bytesToGtv(bytes)
                                gtvToRt(ctx, resRt, gtv, pretty = false)
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

                function("copy", fn = C_StructCopyFunction, since = "0.14.16") {
                    comment("""
                        Creates a copy of this struct with optional parameter overrides.

                        All parameters are optional and must be specified by name.
                        If a parameter is not specified, the original value is used.

                        Example:
                        ```rell
                        struct person { name: text; age: integer; }
                        val p1 = person('Alice', 25);
                        val p2 = p1.copy(age = 26);  // p2 has name='Alice', age=26
                        ```
                    """)
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

    fun decodeOperation(ctx: Rt_CallContext, v: Rt_Value): Pair<MountName, ImmList<Gtv>> {
        val sv = v.asStruct()
        val rtType = sv.type
        val interpreter = ctx.exeCtx.appCtx.interpreter
        val rrType = rtType.rrType as? RR_Type.Struct
            ?: throw Rt_Exception.common("decode_operation:bad_type", "Wrong struct runtime type ${rtType.name}")
        val structDef = interpreter.rrApp.allStructs[rrType.defIndex]
        val mirrorInfo = structDef.struct.mirrorInfo
            ?: throw Rt_Exception.common(
                "decode_operation:no_mirror_info",
                "Struct '${structDef.struct.name}' is not a mirror struct",
            )
        if (mirrorInfo.definitionType != "OPERATION") {
            throw Rt_Exception.common(
                "decode_operation:wrong_kind:${mirrorInfo.definitionType}",
                "Mirror struct '${structDef.struct.name}' is not for an operation",
            )
        }
        // Find the corresponding operation by app-level name.
        val rrOp = interpreter.rrApp.allOperations.find { it.base.appLevelName == mirrorInfo.definition }
            ?: throw Rt_Exception.common(
                "decode_operation:no_operation:${mirrorInfo.definition}",
                "Operation not found: '${mirrorInfo.definition}'",
            )
        val attrs = structDef.struct.attributesList
        val gtvArgs = attrs.mapIndexedToImmList { i, attr ->
            val attrRtType = interpreter.resolveType(attr.type)
            val gtvConv = attrRtType.gtvConversion
                ?: throw Rt_Exception.common(
                    "decode_operation:no_gtv:${attr.name}",
                    "No GTV conversion for attribute '${attr.name}' of '${structDef.struct.name}'",
                )
            gtvConv.rtToGtv(sv.get(i), false)
        }
        return rrOp.mountName to gtvArgs
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

    private fun toMutableOrImmutable(
        ctx: Rt_CallContext,
        arg: Rt_Value,
        returnMutable: Boolean,
        name: String,
    ): Rt_Value {
        val v = arg.asStruct()
        val rtType = v.type
        val interpreter = ctx.exeCtx.appCtx.interpreter
        val rrType = rtType.rrType as? RR_Type.Struct
            ?: throw Rt_Exception.common(
                "$name:bad_type", "$name: wrong struct runtime type ${rtType.name}"
            )
        val currentDef = interpreter.rrApp.allStructs[rrType.defIndex]
        val mirrorInfo = currentDef.struct.mirrorInfo
            ?: throw Rt_Exception.common(
                "$name:no_mirror_info", "$name: struct '${currentDef.struct.name}' has no mirror info"
            )

        if (mirrorInfo.mutable == returnMutable) {
            return arg
        }

        val counterpartIndex = interpreter.rrApp.allStructs.indexOfFirst { other ->
            val info = other.struct.mirrorInfo
            info != null &&
                info.definitionType == mirrorInfo.definitionType &&
                info.definition == mirrorInfo.definition &&
                info.mutable == returnMutable
        }
        if (counterpartIndex < 0) {
            throw Rt_Exception.common(
                "$name:no_counterpart",
                "$name: no counterpart for ${mirrorInfo.definitionType}:${mirrorInfo.definition} mutable=$returnMutable"
            )
        }
        val counterpart = interpreter.rrApp.allStructs[counterpartIndex]
        val counterpartRtType = interpreter.resolveType(RR_Type.Struct(counterpartIndex))
        val values = MutableList(currentDef.struct.attributesList.size) { i -> v.get(i) }
        val attrNames = counterpart.struct.attributesList.map { it.name }
        return Rt_StructValue(counterpartRtType, attrNames, values)
    }

    private object C_StructCopyFunction: C_SpecialLibMemberFunctionBody.Complex() {

        override fun callParameters(selfType: R_Type): C_FunctionCallParameters {
            check(selfType is R_StructType) { "copy() can only be called on struct types" }

            val parameters = selfType.struct.attributesList.mapToImmList { attr ->
                C_FunctionCallParameter(
                    name = attr.rName,
                    type = attr.type,
                    index = attr.index,
                    defaultValue = null,
                    restrictions = C_MemberRestrictions.NULL
                )
            }

            return object: C_FunctionCallParameters(parameters) {
                override val bindParams = C_ArgMatchParams(
                    parameters.mapToImmList { param ->
                        C_ArgMatchParam(param.index, param.name, M_ParamArity.ZERO_ONE, null)
                    }
                )
            }
        }

        override fun compileCallComplex(
            ctx: C_ExprContext,
            callCtx: C_LibFuncCaseCtx,
            selfType: R_Type,
            args: C_FullCallArguments,
        ): V_SpecialMemberFunctionCall {
            check(selfType is R_StructType) {
                "copy() can only be called on struct types"
            }

            val struct = selfType.struct

            args.rawArgs.positional.firstOrNull()?.let { arg ->
                ctx.msgCtx.error(arg.value.pos, "copy:unnamed_arg", "All arguments to copy() must be named")
            }

            // Preserve the evaluation order of overrides as provided in the call
            val overrides = mutableListOf<Pair<Int, V_Expr>>()

            for (namedArg in args.rawArgs.named) {
                val name = namedArg.name.rName

                // Find the corresponding attribute
                val attr = struct.attributes[name]
                if (attr == null) {
                    ctx.msgCtx.error(
                        namedArg.name.pos,
                        "expr:call:unknown_named_arg:$name",
                        "Function 'copy' has no parameter '$name'"
                    )
                    continue
                }

                // Compile the argument expression
                val argValue = when (val value = namedArg.value.value) {
                    is C_CallArgumentValue_Expr -> value.vExpr
                    is C_CallArgumentValue_Wildcard -> error("Wildcards not allowed in 'copy'")
                }

                val expectedType = attr.type
                val actualType = argValue.type
                if (!expectedType.isAssignableFrom(actualType)) {
                    ctx.msgCtx.error(
                        namedArg.value.value.pos,
                        "expr_call_argtype:[copy]:$name:${expectedType.strCode()}:${actualType.strCode()}",
                        "Wrong argument type for parameter '$name': ${actualType.str()} instead of ${expectedType.str()}",
                    )
                    continue
                }

                overrides += attr.index to argValue
            }

            return V_StructCopyCall(ctx, selfType, struct, overrides.toImmList(), callCtx.linkPos)
        }

        private class V_StructCopyCall(
            exprCtx: C_ExprContext,
            selfType: R_Type,
            private val struct: R_Struct,
            private val overrides: ImmList<Pair<Int, V_Expr>>,
            private val pos: S_Pos,
        ): V_SpecialMemberFunctionCall(exprCtx, selfType) {
            private val members = getValueMembers(struct)

            override fun calculator(): R_MemberCalculator = error("copy() is lowered to V_StructExpr")

            override fun lower(ctx: C_ExprContext, base: V_Expr): V_Expr {
                val overrideMap = overrides.toMap()
                val overrideIndices = overrideMap.keys

                // Overridden attrs first (in call-site order), then non-overridden attrs.
                // Non-overridden attrs are pure reads from base, so their order doesn't matter.
                val explicitAttrs = overrides.mapToImmList { (idx, vExpr) ->
                    V_CreateExprAttr(struct.attributesList[idx], vExpr)
                } + struct.attributesList.filter { it.index !in overrideIndices }.mapToImmList { attr ->
                    val vMember = members[attr.index].value(ctx, struct.type, pos, null)
                    val vExpr = V_ValueMemberExpr.make(ctx, base, vMember, pos, safe = false, baseNulled = null)
                    V_CreateExprAttr(attr, vExpr)
                }

                return V_StructExpr(ctx, pos, struct, explicitAttrs, immListOf())
            }
        }
    }
}
