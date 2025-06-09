/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.runtime.Rt_CallContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.LazyString
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.toBytes

object Lib_OpContext {
    private const val NAMESPACE_NAME = "op_context"

    private val NAMESPACE_QNAME = R_QualifiedName.of(NAMESPACE_NAME)

    private val TRANSACTION_FN_QNAME = NAMESPACE_QNAME.append("transaction")
    private val TRANSACTION_FN = TRANSACTION_FN_QNAME.str()
    private val TRANSACTION_FN_LAZY = LazyString.of(TRANSACTION_FN)

    private val GET_SIGNERS_RETURN_TYPE: R_Type = R_ListType(R_ByteArrayType)

    val NAMESPACE = Ld_NamespaceDsl.make {
        struct("gtx_operation", since = "0.10.4") {
            attribute("name", type = "text")
            attribute("args", type = "list<gtv>")
        }

        struct("gtx_transaction_body", since = "0.10.4") {
            attribute("blockchain_rid", type = "byte_array")
            attribute("operations", type = "list<gtx_operation>")
            attribute("signers", type = "list<gtv>")
        }

        struct("gtx_transaction", since = "0.10.4") {
            attribute("body", type = "gtx_transaction_body")
            attribute("signatures", type = "list<gtv>")
        }

        // When turning deprecated warning into error in the future, keep backwards-compatibility (version-dependent behavior) -
        // the function is used in existing code.
        function("is_signer", result = "boolean", since = "0.6.0") {
            deprecated(newName = "op_context.is_signer", error = false)
            comment("""
                Check if a given public key is a signer of the current transaction; i.e. if it's in the list of signers
                returned by `op_context.get_signers()`.
                @return `true` if the given public key is found, `false` otherwise
            """)
            param("pubkey", type = "byte_array", comment = "the public key to check")
            validate { ctx -> checkCtx(ctx.exprCtx, ctx.callPos, allowTest = true) }
            bodyContext { ctx, a ->
                val bytes = a.asByteArray().toBytes()
                val r = ctx.exeCtx.opCtx.isSigner(bytes)
                Rt_BooleanValue.get(r)
            }
        }

        namespace("op_context", since = "0.7.0") {
            comment("""
                Access metadata relating to current and previous operations in a Rell DApp.

                Most properties and functions in the `op_context` namespace are only available in an operation context
                (during the execution of an operation, i.e. when there is an operation call in the current call stack).
                When there is no operation context, accessing properties or calling functions in this namespace will
                result in an exception being thrown. To check if there is an operation context, use the `exists`
                property, which indicates whether there is an operation context, in which case other properties and
                functions can be safely used.
            """)
            extension("struct_op_ext", type = "mirror_struct<-operation>", since = "0.10.4") {
                function("to_gtx_operation", "gtx_operation", since = "0.13.4") {
                    comment("""
                        Convert this `struct<operation>` to a `gtx_operation`.

                        For the operation:
                        ```rell
                        operation my_op(foo: integer, bar: list<text>) { ... }
                        ```

                        a corresponding `struct<my_op>` can be instantiated and converted:
                        ```
                        val x: struct<my_op> = struct<my_op>(foo = 1, bar = ["a", "b"]);
                        print(x.to_gtx_operation()); // prints gtx_operation{name=my_op,args=[1, ["a","b"]]}
                        ```

                        @return a `gtx_operation`, i.e. a struct with two members:
                        - `name`, the mount name of the current operation as `text`; and
                        - `args`, the list of arguments provided to the operation (a list of GTV-formatted text values).
                    """)
                    body { a ->
                        val (mountName, gtvArgs) = Lib_Type_Struct.decodeOperation(a)
                        val nameValue = Rt_TextValue.get(mountName.str())
                        val rtArgs = gtvArgs.mapTo(mutableListOf()) { Rt_GtvValue.get(it) }
                        val argsValue = Rt_ListValue(Lib_Type_Gtv.LIST_OF_GTV_TYPE, rtArgs)
                        val attrs = mutableListOf(nameValue, argsValue)
                        Rt_StructValue(Lib_Rell.GTX_OPERATION_STRUCT_TYPE, attrs)
                    }
                }
            }

            property("exists", type = "boolean", pure = false, since = "0.11.0") {
                comment("""
                    Indicates whether there is currently an operation context, i.e. if the currently executing code has
                    been called from an operation.

                    When `false`, accessing other properties and calling functions in this namespace will result in an
                    exception being thrown. When `true`, other properties and functions in this namespace can be used
                    safely.

                    Examples:

                    ```rell
                    function main() {
                        print(op_context.exists); // prints false
                    }
                    ```

                    ```rell
                    operation main() {
                        print(op_context.exists); // prints true
                    }
                    ```
                """)
                value { ctx ->
                    val v = ctx.exeCtx.opCtx.exists()
                    Rt_BooleanValue.get(v)
                }
            }

            property("last_block_time", type = "integer", pure = false, since = "0.6.1") {
                comment("""
                    The timestamp of the most recent completed block in the blockchain, in milliseconds.

                    The most recent completed block is the parent block of the block currently being built.

                    If there is no most recent completed block (i.e. if the first block in the blockchain is still being
                    built), this will have the value `-1`.

                    In either case, this is a [Unix epoch timestamp](https://en.wikipedia.org/wiki/Unix_time), i.e. the
                    number of milliseconds that have elapsed since midnight on 1st January 1970.
                    @throws exception if there is no operation context
                """)
                validate(::checkCtx)
                value { ctx ->
                    Rt_IntValue.get(ctx.exeCtx.opCtx.lastBlockTime())
                }
            }

            property("block_height", type = "integer", pure = false, since = "0.9.0") {
                comment("""
                    The height of the block being built, i.e. the number of completed blocks in the blockchain.

                    For each block, the height is the number of blocks before it, therefore the first block in the chain
                    has height `0`, the second has height `1`, etc.
                    @throws exception if there is no operation context
                """)
                validate(::checkCtx)
                value { ctx ->
                    Rt_IntValue.get(ctx.exeCtx.opCtx.blockHeight())
                }
            }

            property("op_index", type = "integer", pure = false, since = "0.10.4") {
                comment("""
                    The index of the current operation within the transaction.

                    The first operation in the transaction has index `0`.
                    @throws exception if there is no operation context
                """)
                validate(::checkCtx)
                value { ctx ->
                    Rt_IntValue.get(ctx.exeCtx.opCtx.opIndex().toLong())
                }
            }

            property("transaction", PropTransaction, since = "0.7.0")

            function("get_signers", result = "list<byte_array>", since = "0.10.4") {
                comment("""
                    Get the signers of the current transaction.
                    @return a `list<byte_array>` containing the public keys of all the signers of the current
                    transaction.
                    @throws exception if there is no operation context
                """)
                validate(::checkCtx)
                bodyContext { ctx ->
                    val opCtx = ctx.exeCtx.opCtx
                    val elements = opCtx.signers().map { Rt_ByteArrayValue.get(it.toByteArray()) }.toMutableList()
                    Rt_ListValue(GET_SIGNERS_RETURN_TYPE, elements)
                }
            }

            function("is_signer", result = "boolean", since = "0.10.4") {
                comment("""
                    Check if a given public key is a signer of the current transaction; i.e. if it's in the list of
                    signers returned by `op_context.get_signers()`.
                    @return `true` if the given public key is found, or `false` if the public key is not found or there
                    is no operation context
                """)
                param("pubkey", type = "byte_array", comment = "the public key to check")
                validate(::checkCtx)
                bodyContext { ctx, a ->
                    val bytes = a.asByteArray().toBytes()
                    val r = ctx.exeCtx.opCtx.isSigner(bytes)
                    Rt_BooleanValue.get(r)
                }
            }

            function("get_all_operations", result = "list<gtx_operation>", since = "0.10.4") {
                comment("""
                    Gets all operations in the current transaction.

                    @return a `list<gtx_operation>`, i.e. list of struct values, each with two members:
                    - `name`, the mount name of the operation as `text`; and
                    - `args`, the list of arguments provided to the operation (a list of GTV-formatted text values).
                    @throws exception if there is no operation context
                """)
                validate(::checkCtx)
                bodyContext { ctx ->
                    val elements = ctx.exeCtx.opCtx.allOperations().toMutableList()
                    Rt_ListValue(Lib_Rell.OP_CONTEXT_GET_ALL_OPERATIONS_RETURN_TYPE, elements)
                }
            }

            function("get_current_operation", result = "gtx_operation", since = "0.13.3") {
                comment("""
                    Get a struct representing the current operation.

                    @return a `gtx_operation`, i.e. a struct with two members:
                    - `name`, the mount name of the current operation as `text`; and
                    - `args`, the list of arguments provided to the operation (a list of GTV-formatted text values).
                    @throws exception if there is no operation context
                """)
                validate(::checkCtx)
                bodyContext { ctx ->
                    ctx.exeCtx.opCtx.currentOperation()
                }
            }

            function("emit_event", result = "unit", since = "0.10.4") {
                comment("""
                    Register an event for handling by the blockchain at the end of the current transaction.

                    Postchain allows a set of events to be triggered at the end of every transaction, after all
                    operations have been executed. This function is used to trigger such an event.
                    Handlers for these events, sometimes called *event sinks*, are registered with the *block builder*
                    in Postchain extensions.

                    `emit_event` is used internally in several Chromia platform extensions such as
                    [ICMF](https://docs.chromia.com/intro/cross-chain/icmf) and
                    [EIF](https://blog.chromia.com/chromia-explained-eif/).

                    @throws exception if there is no operation context
                    @see EIF <a href="https://gitlab.com/chromaway/core/postchain-eif/blob/c5eea67d38d1bba29f49ffa4430b9e40a60bc622/postchain-eif-rell/rell/src/hbridge/eif_events.rell#L25">Usage of <code>op_context.emit_event()</code> in EIF</a>
                    @see ICMF <a href="https://gitlab.com/chromaway/core/directory-chain/blob/abd4f0d73092020f5160d27057e4dedd8721bad5/src/lib/icmf/module.rell#L10">Usage of <code>op_context.emit_event()</code> in ICMF</a>
                """)
                param("type", type = "text", comment = "the event processor to trigger")
                param("data", type = "gtv", comment = "arguments to the event processor")
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
        return C_ExprUtils.createSysCallRExpr(type, FnTransaction(type), immListOf(), pos, TRANSACTION_FN_LAZY)
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
