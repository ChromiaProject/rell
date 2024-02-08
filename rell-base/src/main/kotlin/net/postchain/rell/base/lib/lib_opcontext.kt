/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib

import net.postchain.gtv.Gtv
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.core.C_DefinitionType
import net.postchain.rell.base.compiler.base.core.C_QualifiedName
import net.postchain.rell.base.compiler.base.expr.C_ExprContext
import net.postchain.rell.base.compiler.base.expr.C_ExprUtils
import net.postchain.rell.base.compiler.base.lib.C_SysFunctionCtx
import net.postchain.rell.base.compiler.base.namespace.C_NamespaceProperty
import net.postchain.rell.base.compiler.base.namespace.C_NamespacePropertyContext
import net.postchain.rell.base.compiler.vexpr.V_Expr
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.toBytes

object Lib_OpContext {
    private const val NAMESPACE_NAME = "op_context"

    private val NAMESPACE_QNAME = R_QualifiedName.of(NAMESPACE_NAME)

    private val TRANSACTION_FN_QNAME = NAMESPACE_QNAME.append("transaction")
    private val TRANSACTION_FN = TRANSACTION_FN_QNAME.str()
    private val TRANSACTION_FN_LAZY = LazyString.of(TRANSACTION_FN)

    private val GET_SIGNERS_RETURN_TYPE: R_Type = R_ListType(R_ByteArrayType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        struct("gtx_operation") {
            attribute("name", type = "text")
            attribute("args", type = "list<gtv>")
        }

        struct("gtx_transaction_body") {
            attribute("blockchain_rid", type = "byte_array")
            attribute("operations", type = "list<gtx_operation>")
            attribute("signers", type = "list<gtv>")
        }

        struct("gtx_transaction") {
            attribute("body", type = "gtx_transaction_body")
            attribute("signatures", type = "list<gtv>")
        }

        // When turning deprecated warning into error in the future, keep backwards-compatibility (version-dependent behavior) -
        // the function is used in existing code.
        function("is_signer", result = "boolean") {
            deprecated(newName = "op_context.is_signer", error = false)
            param("pubkey", type = "byte_array")
            validate { ctx -> checkCtx(ctx.exprCtx, ctx.callPos, allowTest = true) }
            bodyContext { ctx, a ->
                val bytes = a.asByteArray().toBytes()
                val r = ctx.exeCtx.opCtx.isSigner(bytes)
                Rt_BooleanValue.get(r)
            }
        }

        namespace("op_context") {
            extension( "struct_op_ext", type = "mirror_struct<-operation>") {
                function("to_gtx_operation", "gtx_operation") {
                    comment( """
                        Converts a mirror struct representing an operation to a `gtx_operation`.
                        The mirror struct should contain the mount name and arguments of the operation.
                    """)
                    body { a ->
                        val (mountName, gtvArgs) = Lib_Type_Struct.decodeOperation(a)
                        val nameValue = Rt_TextValue.get(mountName.str())
                        val rtArgs = gtvArgs.map<Gtv, Rt_Value> { Rt_GtvValue.get(it) }.toMutableList()
                        val argsValue = Rt_ListValue(Lib_Type_Gtv.LIST_OF_GTV_TYPE, rtArgs)
                        val attrs = mutableListOf(nameValue, argsValue)
                        Rt_StructValue(Lib_Rell.GTX_OPERATION_STRUCT_TYPE, attrs)
                    }
                }
            }

            property( "exists", type = "boolean", pure = false) {
                comment("Indicates whether the code is being called from an operation.")
                value { ctx ->
                    val v = ctx.exeCtx.opCtx.exists()
                    Rt_BooleanValue.get(v)
                }
            }

            property( "last_block_time", type = "integer", pure = false) {
                comment("Returns the timestamp of the last block in milliseconds.")
                validate(::checkCtx)
                value { ctx ->
                    Rt_IntValue.get(ctx.exeCtx.opCtx.lastBlockTime())
                }
            }

            property( "block_height", type = "integer", pure = false) {
                comment("Provides the height of the block currently being built.")
                validate(::checkCtx)
                value { ctx ->
                    Rt_IntValue.get(ctx.exeCtx.opCtx.blockHeight())
                }
            }

            property("op_index", type = "integer", pure = false) {
                comment("Indicates the index of the operation being executed within the transaction.")
                validate(::checkCtx)
                value { ctx ->
                    Rt_IntValue.get(ctx.exeCtx.opCtx.opIndex().toLong())
                }
            }

            property("transaction", PropTransaction)

            function("get_signers", result = "list<byte_array>") {
                comment("Returns a list of pubkeys representing the signers of the current transaction.")
                validate(::checkCtx)
                bodyContext { ctx ->
                    val opCtx = ctx.exeCtx.opCtx
                    val elements = opCtx.signers().map { Rt_ByteArrayValue.get(it.toByteArray()) }.toMutableList()
                    Rt_ListValue(GET_SIGNERS_RETURN_TYPE, elements)
                }
            }

            function("is_signer", result = "boolean") {
                comment("Checks if the provided public key is one of the signers of the current transaction.")
                param("pubkey", type = "byte_array", comment = "The public key to check.")
                validate(::checkCtx)
                bodyContext { ctx, a ->
                    val bytes = a.asByteArray().toBytes()
                    val r = ctx.exeCtx.opCtx.isSigner(bytes)
                    Rt_BooleanValue.get(r)
                }
            }

            function("get_all_operations", result = "list<gtx_operation>") {
                comment( """
                    Gets all operations in this transaction.
                    @returns a list of all operations within the current transaction.
                """)
                validate(::checkCtx)
                bodyContext { ctx ->
                    val elements = ctx.exeCtx.opCtx.allOperations().toMutableList()
                    Rt_ListValue(Lib_Rell.OP_CONTEXT_GET_ALL_OPERATIONS_RETURN_TYPE, elements)
                }
            }

            function("get_current_operation", result = "gtx_operation") {
                comment("Retrieves the current operation.")
                validate(::checkCtx)
                bodyContext { ctx ->
                    ctx.exeCtx.opCtx.currentOperation()
                }
            }

            function("emit_event", result = "unit") {
                comment("Emits an event with the provided type and data.")
                param("type", type = "text", comment = "Name of the event to emit.")
                param("data", type = "gtv", comment = "Data to emit")
                validate(::checkCtx)
                bodyContext { ctx, arg1, arg2 ->
                    val type = arg1.asString()
                    val data = arg2.asGtv()
                    ctx.exeCtx.opCtx.emitEvent(type, data)
                    Rt_UnitValue
                }
            }
        }
    }

    fun transactionRExpr(ctx: C_NamespacePropertyContext, pos: S_Pos): R_Expr {
        val type = ctx.modCtx.sysDefsCommon.transactionEntity.type
        return C_ExprUtils.createSysCallRExpr(type, FnTransaction(type), listOf(), pos, TRANSACTION_FN_LAZY)
    }

    private fun transactionExpr(ctx: C_NamespacePropertyContext, pos: S_Pos): V_Expr {
        val type = ctx.modCtx.sysDefsCommon.transactionEntity.type
        return C_ExprUtils.createSysGlobalPropExpr(ctx.exprCtx, type, FnTransaction(type), pos, TRANSACTION_FN, pure = false)
    }

    private fun checkCtx(ctx: C_SysFunctionCtx) {
        checkCtx(ctx.exprCtx, ctx.callPos)
    }

    private fun checkCtx(ctx: C_ExprContext, pos: S_Pos, allowTest: Boolean = false) {
        val dt = ctx.defCtx.definitionType
        if (ctx.modCtx.isTestLib() && !allowTest) {
            ctx.msgCtx.error(pos, "op_ctx:test", "Cannot access '$NAMESPACE_NAME' from a test module")
        } else if (dt != C_DefinitionType.OPERATION && dt != C_DefinitionType.FUNCTION && dt != C_DefinitionType.ENTITY) {
            ctx.msgCtx.error(pos, "op_ctx:noop", "Can access '$NAMESPACE_NAME' only in an operation, function or entity")
        }
    }

    private val LIST_OF_GTV_TYPE = R_ListType(R_GtvType)

    fun gtxTransactionStructValue(name: String, args: List<Gtv>): Rt_Value {
        val nameValue = Rt_TextValue.get(name)
        val argsValue = Rt_ListValue(LIST_OF_GTV_TYPE, args.map { Rt_GtvValue.get(it) }.toMutableList())
        return Rt_StructValue(Lib_Rell.GTX_OPERATION_STRUCT_TYPE, mutableListOf(nameValue, argsValue))
    }

    private object PropTransaction: C_NamespaceProperty() {
        override fun toExpr(ctx: C_NamespacePropertyContext, name: C_QualifiedName): V_Expr {
            checkCtx(ctx.exprCtx, name.pos)
            return transactionExpr(ctx, name.pos)
        }
    }

    private class FnTransaction(private val type: R_EntityType): R_SysFunctionEx_0() {
        override fun call(ctx: Rt_CallContext): Rt_Value {
            val opCtx = ctx.exeCtx.opCtx
            return Rt_EntityValue(type, opCtx.transactionIid())
        }
    }
}
