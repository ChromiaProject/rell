/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.utils.C_StringQualifiedName
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.make
import net.postchain.rell.base.lib.type.Lib_Type_Struct
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.isPostgresQueryCanceled
import net.postchain.rell.base.utils.*
import java.sql.SQLException

object Lib_RellTest {
    private const val MODULE_NAME_STR = "rell.test"
    private val MODULE_NAME = ModuleName.of(MODULE_NAME_STR)

    val NAMESPACE_NAME = C_StringQualifiedName.ofRNames(MODULE_NAME.parts)

    val BLOCK_RUNNER_KEYPAIR: BytesKeyPair = let {
        val privKey = "42".repeat(32).hexStringToByteArray()
        val pubKey = secp256k1_derivePubKey(privKey)
        BytesKeyPair(privKey, pubKey)
    }

    val MODULE = C_LibModule.make("rell.test", Lib_Rell.MODULE, versionControl = false) {
        include(Lib_Test_Assert.NAMESPACE)
        include(Lib_Test_Events.NAMESPACE)
        include(Lib_Test_BlockClock.NAMESPACE)
        include(Lib_Test_KeyPairs.NAMESPACE)

        include(Lib_Type_Block.NAMESPACE)
        include(Lib_Type_Tx.NAMESPACE)
        include(Lib_Type_Op.NAMESPACE)

        include(Lib_Nop.NAMESPACE)
    }

    private val KEYPAIR_STRUCT: R_Struct = MODULE.lModule.getStruct("rell.test.keypair").rStruct

    val KEYPAIR_TYPE: R_StructType
        get() = KEYPAIR_STRUCT.type

    val BLOCK_TYPE = MODULE.getTypeDef("rell.test.block")
    val TX_TYPE = MODULE.getTypeDef("rell.test.tx")
    val OP_TYPE = MODULE.getTypeDef("rell.test.op")

    val FAILURE_TYPE = MODULE.getTypeDef("rell.test.failure")

    init {
        // Register R_Type → L_TypeDef bindings AFTER all type defs are constructed.
        R_LibUniqueType.registerLibTypeDef(R_TestBlockType, BLOCK_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_TestTxType, TX_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_TestOpType, OP_TYPE)
        R_LibUniqueType.registerLibTypeDef(R_TestFailureType, FAILURE_TYPE)
    }

    fun typeDefName(name: C_StringQualifiedName) = C_DefinitionName(MODULE_NAME_STR, name)
}

private const val SINCE0 = "0.10.4"

private fun typeDefName(name: String) = Lib_RellTest.typeDefName(C_StringQualifiedName.of(name.split(".")))


internal object R_TestBlockType: R_LibUniqueType("rell.test.block", typeDefName("rell.test.block")) {
    override fun isReference() = true
    override fun isDirectMutable() = true
    override fun isDirectPure() = false
}

internal object R_TestTxType: R_LibUniqueType("rell.test.tx", typeDefName("rell.test.tx")) {
    override fun isReference() = true
    override fun isDirectMutable() = true
    override fun isDirectPure() = false
}

internal object R_TestOpType: R_LibUniqueType("rell.test.op", typeDefName("rell.test.op")) {
    override fun isReference() = true
    override fun isDirectPure() = false
}

private class BlockCommonFunctions(
    typeQName: String,
    private val blockGetter: (self: Rt_Value) -> Rt_TestBlockValue,
) {
    private val runFullName = "$typeQName.run"
    private val runMustFailFullName = "$typeQName.run_must_fail"

    fun runFunction(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit = {}) = with(m) {
        function("run", "unit", since = SINCE0) {
            block()
            bodyContext { ctx, arg ->
                val blk = getRunBlock(ctx, arg, runFullName)
                try {
                    ctx.appCtx.blockRunner.runBlock(ctx, blk)
                } catch (e: Rt_Exception) {
                    throw e
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                } catch (e: SQLException) {
                    if (e.isPostgresQueryCanceled) {
                        throw e
                    }
                    throw Rt_Exception.common("fn:$runFullName:fail:${e.javaClass.canonicalName}", "Block execution failed: $e")
                } catch (e: Throwable) {
                    throw Rt_Exception.common("fn:$runFullName:fail:${e.javaClass.canonicalName}", "Block execution failed: $e")
                }
                Rt_UnitValue
            }
        }
    }

    fun runMustFailFunction(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit = {}) = with(m) {
        function("run_must_fail", "rell.test.failure", since = SINCE0) {
            block()
            bodyContext { ctx, arg ->
                val blk = getRunBlock(ctx, arg, runMustFailFullName)
                runMustFail(ctx, blk, null)
            }
        }
    }

    fun runMustFailWithMessageFunction(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit = {}) = with(m) {
        function("run_must_fail", "rell.test.failure", since = "0.11.0") {
            block()
            param("expected_message", "text", comment = "the expected substring of the error message")
            bodyContext { ctx, arg1, arg2 ->
                val blk = getRunBlock(ctx, arg1, runMustFailFullName)
                val expected = arg2.asString()
                runMustFail(ctx, blk, expected)
            }
        }
    }

    private fun getRunBlock(ctx: Rt_CallContext, arg: Rt_Value, fnName: String): Rt_TestBlockValue {
        val block = blockGetter(arg)
        if (!ctx.appCtx.repl && !ctx.appCtx.test) {
            throw Rt_Exception.common("fn:$fnName:no_repl_test", "Block can be executed only in REPL or test")
        }
        return block
    }

    private fun runMustFail(ctx: Rt_CallContext, block: Rt_TestBlockValue, expected: String?): Rt_Value {
        try {
            ctx.appCtx.blockRunner.runBlock(ctx, block)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: SQLException) {
            if (e.isPostgresQueryCanceled) {
                throw e
            }
            val actual = e.message ?: ""
            Lib_Test_Assert.checkErrorMessage("run_must_fail", expected, actual)
            return Lib_Test_Assert.failureValue(actual)
        } catch (e: Throwable) {
            val actual = e.message ?: ""
            Lib_Test_Assert.checkErrorMessage("run_must_fail", expected, actual)
            return Lib_Test_Assert.failureValue(actual)
        }
        throw Rt_Exception.common("fn:$runMustFailFullName:nofail", "Transaction did not fail")
    }
}

private class TxCommonFunctions(private val txGetter: (self: Rt_Value) -> Rt_TestTxValue) {
    fun signWithKeypairList(m: Ld_TypeDefDsl, objectArticleText: String, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("keypairs", type = "list<rell.test.keypair>") {
                comment("the list of keypairs with which to sign $objectArticleText transaction")
            }
            body { arg1, arg2 ->
                signByKeyPairs(arg1, arg2.asList())
            }
        }
    }

    fun signWithKeypairs(m: Ld_TypeDefDsl, objectArticleText: String, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("keypairs", type = "rell.test.keypair", arity = L_ParamArity.ONE_MANY) {
                comment("the keypairs with which to sign $objectArticleText transaction")
            }
            bodyN { args ->
                check(args.isNotEmpty())
                signByKeyPairs(args[0], args.subList(1, args.size))
            }
        }
    }

    fun signWithPrivkeyList(m: Ld_TypeDefDsl, objectArticleText: String, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("privkeys", type = "list<byte_array>") {
                comment("the list of private keys with which to sign $objectArticleText transaction")
            }
            body { arg1, arg2 ->
                signByByteArrays(arg1, arg2.asList())
            }
        }
    }

    fun signWithPrivkeys(m: Ld_TypeDefDsl, objectArticleText: String, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("privkeys", "byte_array", arity = L_ParamArity.ONE_MANY) {
                comment("the private keys with which to sign $objectArticleText transaction")
            }
            bodyN { args ->
                check(args.isNotEmpty())
                signByByteArrays(args[0], args.subList(1, args.size))
            }
        }
    }

    private fun signByKeyPairs(self: Rt_Value, keyPairs: List<Rt_Value>): Rt_Value {
        val tx = txGetter(self)
        for (keyPairValue in keyPairs) {
            val keyPair = Lib_Test_KeyPairs.structToKeyPair(keyPairValue)
            tx.sign(keyPair)
        }
        return tx
    }

    private fun signByByteArrays(self: Rt_Value, byteArrays: List<Rt_Value>): Rt_Value {
        val tx = txGetter(self)
        for (v in byteArrays) {
            val bs = v.asByteArray()
            val keyPair = privKeyToKeyPair(bs)
            tx.sign(keyPair)
        }
        return tx
    }

    private fun privKeyToKeyPair(priv: ByteArray): BytesKeyPair {
        val privSize = 32
        Rt_Utils.check(priv.size == privSize) { "tx.sign:priv_key_size:$privSize:${priv.size}" to
                "Wrong size of private key: ${priv.size} instead of $privSize"
        }

        val pub = secp256k1_derivePubKey(priv)
        val pubSize = 33
        Rt_Utils.check(pub.size == pubSize) { "tx.sign:pub_key_size:$pubSize:${pub.size}" to
                "Wrong size of calculated public key: ${pub.size} instead of $pubSize"
        }

        return BytesKeyPair(priv, pub)
    }
}

internal object Lib_Type_Block {
    private val common = BlockCommonFunctions("rell.test.block") { asTestBlock(it) }

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type("block", rType = R_TestBlockType, since = SINCE0) {
                comment("""
                    A test block builder; i.e. a [builder](https://en.wikipedia.org/wiki/Builder_pattern) for test
                    blockchain blocks.

                    A test block consists of a list of test transactions that are executed when the block is built. A
                    test transaction consists of a list of test operation calls, and a list of signing keypairs.

                    #### Test block timestamps

                    The timestamps of Rell test blocks are deterministic, and determined by the following rules:

                    1. If the next block's timestamp was set explicitly via `rell.test.set_next_block_time()` or
                       `rell.test.set_next_block_time_delta()`, that timestamp is used for the next block, and is then
                       discarded (and will not be used by subsequent blocks).
                    2. If the next block's timestamp was not set explicitly, and there is a previous block, the next
                       block's timestamp is the timestamp of the previous block plus the block interval
                       (`rell.test.block_interval`, which can be set with `rell.test.set_block_interval()`).
                    3. If the next block's timestamp was not set explicitly, and there is no previous block, the
                       timestamp of the next (and first) block will be `2020-01-01 00:00:00 UTC`.

                    @see 1. <a href="https://en.wikipedia.org/wiki/Builder_pattern">Builder pattern - Wikipedia</a>
                    @see 2. <a href="../tx/index.html"><code>rell.test.tx</code> - Rell Standard Library</a>
                    @see 3. <a href="../op/index.html"><code>rell.test.op</code> - Rell Standard Library</a>
                    @see 4. <a href="../set_next_block_time.html"><code>rell.test.set_next_block_time()</code> - Rell Standard Library</a>
                    @see 5. <a href="../set_next_block_time_delta.html"><code>rell.test.set_next_block_time_delta()</code> - Rell Standard Library</a>
                    @see 6. <a href="../block_interval.html"><code>rell.test.block_interval</code> - Rell Standard Library</a>
                    @see 7. <a href="../set_block_interval.html"><code>rell.test.set_block_interval()</code> - Rell Standard Library</a>
                    @see 8. <a href="https://docs.chromia.com/rell/tests/namespace/block"><code>rell.test.block</code> - Chromia Documentation</a>
                """)

                constructor(since = SINCE0) {
                    comment("Construct a test block builder from the specified list of transactions.")
                    param("txs", type = "list<rell.test.tx>") {
                        comment("the list of test transactions with which to initialize this test block builder")
                    }
                    body { arg ->
                        Rt_TestBlockValue(arg.asList().map { asTestTx(it).toRaw() })
                    }
                }

                constructor(since = SINCE0) {
                    comment("Construct a test block builder from the specified transactions.")
                    param("txs", type = "rell.test.tx", arity = L_ParamArity.ZERO_MANY) {
                        comment("the test transactions with which to initialize this test block builder")
                    }
                    bodyN { args ->
                        Rt_TestBlockValue(args.map { asTestTx(it).toRaw() })
                    }
                }

                constructor(since = SINCE0) {
                    comment("""
                        Construct a test block builder with a single test transaction consisting of the specified list
                        of test operation calls.
                    """)
                    param("ops", type = "list<rell.test.op>") {
                        comment("the list of test operation calls")
                    }
                    body { arg ->
                        newOps(arg.asList())
                    }
                }

                constructor(since = SINCE0) {
                    comment("""
                        Construct a test block builder with a single test transaction consisting of the specified test
                        operation calls.
                    """)
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY) {
                        comment("the test operation calls")
                    }
                    bodyN { args ->
                        newOps(args)
                    }
                }

                common.runFunction(this) {
                    comment("Build a test block from this test block builder.")
                }

                common.runMustFailFunction(this) {
                    comment("""
                        Build a test block from this test block builder, asserting that the building fails (i.e. throws
                        an exception).

                        Typically used to test operation calls that must throw an exception.
                    """)
                }

                common.runMustFailWithMessageFunction(this) {
                    comment("""
                        Build a test block from this test block builder, asserting that the building fails; i.e. throws
                        an exception; with the given exception message.

                        Typically used to test operation calls that must throw an exception.
                    """)
                }

                function("copy", "rell.test.block", since = SINCE0) {
                    comment("Copy this test block builder.")
                    body { arg ->
                        val block = asTestBlock(arg)
                        Rt_TestBlockValue(block.txs())
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("Add a list of test transactions to this test block builder.")
                    param("txs", type = "list<rell.test.tx>") {
                        comment("the list of transactions to be added to this test block builder")
                    }
                    body { arg1, arg2 ->
                        addTxs(arg1, arg2.asList())
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("Add test transactions to this test block builder.")
                    param("txs", type = "rell.test.tx", arity = L_ParamArity.ONE_MANY) {
                        comment("the transactions to be added to this test block builder")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addTxs(args[0], args.subList(1, args.size))
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("""
                        Add a single test transaction to this test block builder; the test transaction consisting of the
                        specified list of test operation calls.
                    """)
                    param("ops", type = "list<rell.test.op>") {
                        comment("the list of test operation calls to include in the transaction")
                    }
                    body { arg1, arg2 ->
                        addOps(arg1, arg2.asList())
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("""
                        Add a single test transaction to this test block builder; the test transaction consisting of the
                        specified test operation calls.
                    """)
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY) {
                        comment("the test operation calls to include in the transaction")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOps(args[0], args.subList(1, args.size))
                    }
                }
            }
        }
    }

    private fun newOps(ops: List<Rt_Value>): Rt_Value {
        val rawOps = ops.mapToImmList { asTestOp(it).toRaw() }
        val tx = RawTestTxValue(rawOps, immListOf())
        return Rt_TestBlockValue(listOf(tx))
    }

    private fun addTxs(self: Rt_Value, txs: List<Rt_Value>): Rt_Value {
        val block = asTestBlock(self)
        txs.forEach { block.addTx(asTestTx(it).toRaw()) }
        return self
    }

    private fun addOps(self: Rt_Value, ops: List<Rt_Value>): Rt_Value {
        val block = asTestBlock(self)
        val rawOps = ops.mapToImmList { asTestOp(it).toRaw() }
        val tx = RawTestTxValue(rawOps, immListOf())
        block.addTx(tx)
        return self
    }
}

private object Lib_Type_Tx {
    private val block = BlockCommonFunctions("rell.test.tx") {
        Rt_TestBlockValue(listOf(asTestTx(it).toRaw()))
    }

    private val tx = TxCommonFunctions { asTestTx(it) }

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type("tx", rType = R_TestTxType, since = SINCE0) {
                comment("""
                    A test transaction builder; i.e. a [builder](https://en.wikipedia.org/wiki/Builder_pattern) for test
                    transactions that comprise blockchain blocks.

                    A test transaction consists of a list of test operation calls, and a list of signing keypairs. The
                    test transaction builder type has functions that facilitate straightforward transaction
                    construction by passing operations and keys in various formats.

                    @see 1. <a href="https://en.wikipedia.org/wiki/Builder_pattern">Builder pattern - Wikipedia</a>
                    @see 2. <a href="../op/index.html"><code>rell.test.op</code> - Rell Standard Library</a>
                    @see 3. <a href="../keypair/index.html"><code>rell.test.keypair</code> - Rell Standard Library</a>
                """)

                constructor(since = SINCE0) {
                    comment("""
                        Construct a new test transaction builder consisting of the specified list of test operation
                        calls.
                    """)
                    param("ops", type = "list<rell.test.op>") {
                        comment("the list of test operation calls to include in this transaction")
                    }
                    body { arg ->
                        val ops = arg.asList().map { asTestOp(it).toRaw() }
                        newTx(ops)
                    }
                }

                constructor(since = SINCE0) {
                    comment("""
                        Construct a new test transaction builder consisting of the specified test operation calls.
                    """)
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ZERO_MANY) {
                        comment("the test operation calls to include in this transaction")
                    }
                    bodyN { args ->
                        val ops = args.map { asTestOp(it).toRaw() }
                        newTx(ops)
                    }
                }

                constructor(since = SINCE0) {
                    comment("""
                        Construct a new test transaction builder consisting of the specified list of test operation
                        calls.

                        The test operation calls are given as `struct<operation>` values.

                        For example, calls to the operation:
                        ```rell
                        operation my_op(foo: integer, bar: list<text>) { ... }
                        ```

                        may be passed as a `struct<my_op>`:
                        ```
                        rell.test.tx([
                            struct<my_op>(foo = 1, bar = ["a", "b"]),
                            struct<my_op>(foo = 2, bar = ["c", "d"])
                        ]);
                        ```
                    """)
                    param("ops", type = "list<-mirror_struct<-operation>>") {
                        comment("the list of test operation calls to include in the built transaction")
                    }
                    bodyContext { ctx, arg ->
                        val list = arg.asList()
                        val ops = list.map { structToOpRaw(ctx, it) }
                        newTx(ops)
                    }
                }

                constructor(since = SINCE0) {
                    comment("""
                        Construct a new test transaction builder consisting of the specified test operation calls.

                        The test operation calls are given as `struct<operation>` values.

                        For example, calls to the operation:
                        ```rell
                        operation my_op(foo: integer, bar: list<text>) { ... }
                        ```

                        may be passed as a `struct<my_op>`:
                        ```
                        rell.test.tx(
                            struct<my_op>(foo = 1, bar = ["a", "b"]),
                            struct<my_op>(foo = 2, bar = ["c", "d"])
                        );
                        ```
                    """)
                    param("ops", type = "mirror_struct<-operation>", arity = L_ParamArity.ONE_MANY) {
                        comment("the test operation calls to include in the built transaction")
                    }
                    bodyContextN { ctx, args ->
                        val ops = args.map { structToOpRaw(ctx, it) }
                        newTx(ops)
                    }
                }

                block.runFunction(this) {
                    comment("Build a test block containing only this test transaction.")
                }

                block.runMustFailFunction(this) {
                    comment("""
                        Build a test block containing only this test transaction, asserting that the building fails
                        (i.e. throws an exception).

                        Typically used to test operation calls that must throw an exception.
                    """)
                }

                block.runMustFailWithMessageFunction(this) {
                    comment("""
                        Build a test block containing only this test transaction, asserting that the building fails;
                        i.e. throws an exception; with the given exception message.

                        Checks that the given message is a substring of the thrown message. For example
                        `my_tx.run_must_fail('out of bounds')` succeeds if an exception with message
                        `Run-time error: List index out of bounds: 0 (size 0)` is thrown.

                        Typically used to test operation calls that must throw an exception.
                    """)
                }

                tx.signWithKeypairList(this, "this") {
                    comment("""
                        Add the given list of keypairs as signers of this test transaction builder.

                        When this builder is built, the transaction will be signed with all keypairs in the list.
                    """)
                }

                tx.signWithKeypairs(this, "this") {
                    comment("""
                        Add the given keypairs as signers of this test transaction builder.

                        When this builder is built, the transaction will be signed with all given keypairs.
                    """)
                }

                tx.signWithPrivkeyList(this, "this") {
                    comment("""
                        Add the given list of private keys as signers of this test transaction builder.

                        When this builder is built, the transaction will be signed with all private keys in the list.
                    """)
                }

                tx.signWithPrivkeys(this, "this") {
                    comment("""
                        Add the given private keys as signers of this test transaction builder.

                        When this builder is built, the transaction will be signed with all given private keys.
                    """)
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("Add a list of test operation calls to this test transaction builder.")
                    param("ops", type = "list<rell.test.op>", comment = "the list of operations to add")
                    body { arg1, arg2 ->
                        addOps(arg1, arg2.asList())
                    }
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("Add test operation calls to this test transaction builder.")
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY) {
                       comment("the operations to add")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOps(args[0], args.subList(1, args.size))
                    }
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("""
                        Add a list of test operation calls to this test transaction builder.

                        The test operation calls are given as `struct<operation>` values.

                        For example, calls to the operation:
                        ```rell
                        operation my_op(foo: integer, bar: list<text>) { ... }
                        ```

                        may be passed as a `struct<my_op>`:
                        ```
                        my_transaction_builder.op([
                            struct<my_op>(foo = 1, bar = ["a", "b"]),
                            struct<my_op>(foo = 2, bar = ["c", "d"])
                        ]);
                        ```
                    """)
                    param("ops", type = "list<-mirror_struct<-operation>>") {
                        comment("the list of test operation calls to add to this transaction builder")
                    }
                    bodyContext { ctx, arg1, arg2 ->
                        addOpStructs(ctx, arg1, arg2.asList())
                    }
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("""
                        Add the given test operation calls to this test transaction builder.

                        The test operation calls are given as `struct<operation>` values.

                        For example, calls to the operation:
                        ```rell
                        operation my_op(foo: integer, bar: list<text>) { ... }
                        ```

                        may be passed as a `struct<my_op>`:
                        ```
                        my_transaction_builder.op(
                            struct<my_op>(foo = 1, bar = ["a", "b"]),
                            struct<my_op>(foo = 2, bar = ["c", "d"])
                        );
                        ```
                    """)
                    param("ops", type = "mirror_struct<-operation>", arity = L_ParamArity.ONE_MANY) {
                        comment("the test operation calls to add to this transaction builder")
                    }
                    bodyContextN { ctx, args ->
                        check(args.isNotEmpty())
                        addOpStructs(ctx, args[0], args.subList(1, args.size))
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("""
                        Add a nop test operation call to this transaction builder.

                        The zero-argument `rell.test.tx.nop()` is effectively an alias of `rell.test.tx.nop(x: integer)`
                        where a unique integer argument `x` is passed each time, serving the purpose of distinguishing
                        transactions that are identical except for the presence of a `nop` call.

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).run();
                        rell.test.tx().op(my_op).run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        // Ok, the transactions are distinct since each nop automatically uses a different nonce.
                        rell.test.tx().op(my_op).nop().run();
                        rell.test.tx().op(my_op).nop().run();
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        """)
                    bodyContext { ctx, arg ->
                        val tx = asTestTx(arg)
                        val op = Lib_Nop.callNoArgs(ctx)
                        tx.addOp(op.toRaw())
                        tx
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("""
                        Add a nop test operation call to this transaction builder.

                        Serves the purpose of distinguishing transactions that are identical except for the presence of
                        a `nop` call.

                        `my_tx_builder.nop(x)` is equivalent to `my_tx_builder.op(rell.test.nop(x))`, where
                        `rell.test.nop(x)` is equivalent to `rell.test.op('nop', x.to_gtv())`, which is a test operation
                        call to the predefined operation:

                        ```rell
                        operation nop(v: gtv) {}
                        ```

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).nop(0).run();
                        rell.test.tx().op(my_op).nop(0).run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        rell.test.tx().op(my_op).nop(0).run();
                        rell.test.tx().op(my_op).nop(1).run(); // Ok, since the transactions are distinct.
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                        """)
                    param("x", type = "integer", comment = "the nonce")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("""
                        Add a nop test operation call to this transaction builder.

                        Serves the purpose of distinguishing transactions that are identical except for the presence of
                        a `nop` call.

                        `my_tx_builder.nop(x)` is equivalent to `my_tx_builder.op(rell.test.nop(x))`, where
                        `rell.test.nop(x)` is equivalent to `rell.test.op('nop', x.to_gtv())`, which is a test operation
                        call to the predefined operation:

                        ```rell
                        operation nop(v: gtv) {}
                        ```

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).nop("a").run();
                        rell.test.tx().op(my_op).nop("a").run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        rell.test.tx().op(my_op).nop("a").run();
                        rell.test.tx().op(my_op).nop("z").run(); // Ok, since the transactions are distinct.
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                        """)
                    param("x", "text", comment = "the nonce")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("""
                        Add a nop test operation call to this transaction builder.

                        Serves the purpose of distinguishing transactions that are identical except for the presence of
                        a `nop` call.

                        `my_tx_builder.nop(x)` is equivalent to `my_tx_builder.op(rell.test.nop(x))`, where
                        `rell.test.nop(x)` is equivalent to `rell.test.op('nop', x.to_gtv())`, which is a test operation
                        call to the predefined operation:

                        ```rell
                        operation nop(v: gtv) {}
                        ```

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).nop(x'00').run();
                        rell.test.tx().op(my_op).nop(x'00').run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        rell.test.tx().op(my_op).nop(x'00').run();
                        rell.test.tx().op(my_op).nop(x'01').run(); // Ok, since the transactions are distinct.
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                        """)
                    param("x", "byte_array", comment = "the nonce")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("copy", "rell.test.tx", since = SINCE0) {
                    comment("Copy this transaction builder.")
                    body { arg ->
                        val tx = asTestTx(arg)
                        tx.copy()
                    }
                }
            }
        }
    }

    private fun newTx(ops: List<RawTestOpValue>): Rt_Value {
        return Rt_TestTxValue(ops, listOf())
    }

    private fun addOps(self: Rt_Value, ops: List<Rt_Value>): Rt_Value {
        val tx = asTestTx(self)
        ops.forEach { tx.addOp(asTestOp(it).toRaw()) }
        return self
    }

    private fun addOpStructs(ctx: Rt_CallContext, self: Rt_Value, ops: List<Rt_Value>): Rt_Value {
        val tx = asTestTx(self)
        ops.forEach { tx.addOp(structToOpRaw(ctx, it)) }
        return self
    }

    private fun calcNopOneArg(arg1: Rt_Value, arg2: Rt_Value): Rt_Value {
        val tx = asTestTx(arg1)
        val op = Lib_Nop.callOneArg(arg2)
        tx.addOp(op.toRaw())
        return tx
    }

    private fun structToOpRaw(ctx: Rt_CallContext, v: Rt_Value): RawTestOpValue = structToOp(ctx, v).toRaw()

    fun structToOp(ctx: Rt_CallContext, a: Rt_Value): Rt_TestOpValue {
        val (mountName, args) = Lib_Type_Struct.decodeOperation(ctx, a)
        return Rt_TestOpValue(mountName, args)
    }
}

private object Lib_Type_Op {
    private val block = BlockCommonFunctions("rell.test.op") {
        Rt_TestBlockValue(listOf(selfToTx(it).toRaw()))
    }

    private val tx = TxCommonFunctions(this::selfToTx)

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            extension("struct_op_ext", type = "mirror_struct<-operation>", since = SINCE0) {
                function("to_test_op", "rell.test.op", since = SINCE0) {
                    comment("Get a test operation call representing this `struct<operation>`.")
                    validate { ctx ->
                        if (!ctx.exprCtx.modCtx.isTestLib()) {
                            val fnName = this.fnSimpleName
                            ctx.exprCtx.msgCtx.error(ctx.callPos,
                                "expr:fn:struct:$fnName:no_test",
                                "Function '$fnName' can be called only in tests or REPL"
                            )
                        }
                    }
                    bodyContext { ctx, a ->
                        Lib_Type_Tx.structToOp(ctx, a)
                    }
                }
            }

            extension("gtx_operation_ext", type = "gtx_operation", since = "0.13.4") {
                function("to_test_op", "rell.test.op", since = "0.13.4") {
                    comment("""
                        Get a test operation call representing this `gtx_operation`.

                        Inverse of `rell.test.op.to_gtx_operation()`.
                    """)
                    validate { ctx ->
                        if (!ctx.exprCtx.modCtx.isTestLib()) {
                            val fnName = this.fnSimpleName
                            ctx.exprCtx.msgCtx.error(ctx.callPos,
                                "expr:fn:$fnName:no_test",
                                "Function '$fnName' can be called only in tests or REPL"
                            )
                        }
                    }
                    body { a ->
                        val sv = a.asStruct()
                        // Validate by struct type name rather than R_StructType identity, so the pure-RR path works.
                        checkEquals(sv.type.name, Lib_Rell.GTX_OPERATION_STRUCT_TYPE.name)
                        val rtName = sv.get(0)
                        val rtArgs = sv.get(1).asList()
                        val mountName = MountName.of(rtName.asString())
                        val gtvArgs = rtArgs.mapToImmList { it.asGtv() }
                        Rt_TestOpValue(mountName, gtvArgs)
                    }
                }
            }

            type("op", rType = R_TestOpType, since = SINCE0) {
                comment("""
                    A test operation call, consisting of a name (an operation's *mount name*), and a list of arguments
                    (a `list<gtv>`).

                    @see 1. <a href="https://docs.chromia.com/rell/language-features/modules/mount">Mount names - Chromia Documentation</a>
                    @see 2. <a href="../../[root]/list/index.html"><code>list</code> - Rell Standard Library</a>
                    @see 3. <a href="../../[root]/gtv/index.html"><code>gtv</code> - Rell Standard Library</a>
                """)

                constructor(since = SINCE0) {
                    comment("""
                        Construct a new test operation call from the given operation name and argument list.
                    """)
                    param("name", type = "text", comment = "the name of the operation")
                    param("args", type = "list<gtv>", comment = "the arguments to the operation")
                    body { arg1, arg2 ->
                        newOp(arg1, arg2.asList())
                    }
                }

                constructor(since = SINCE0) {
                    comment("""
                        Construct a new test operation call from the given operation name and arguments.
                    """)
                    param("name", type = "text", comment = "the name of the operation")
                    param("args", type = "gtv", arity = L_ParamArity.ZERO_MANY) {
                        comment("the arguments to the operation")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        val nameArg = args[0]
                        val tailArgs = args.subList(1, args.size)
                        newOp(nameArg, tailArgs)
                    }
                }

                block.runFunction(this) {
                    comment("""
                        Build a test block containing a single test transaction, which contains only this test
                        operation call.
                    """)
                }

                block.runMustFailFunction(this) {
                    comment("""
                        Assert that this test operation call fails.

                        More precisely:
                        1. Build a test block containing a single test transaction, which contains only this test
                        operation call.
                        2. Assert that the building fails (i.e. that invoking this test transaction
                        call results in a thrown exception).
                    """)
                }

                block.runMustFailWithMessageFunction(this) {
                    comment("""
                        Assert that this test operation call fails.

                        More precisely:
                        1. Build a test block containing a single test transaction, which contains only this test
                        operation call.
                        2. Assert that the building fails (i.e. that invoking this test transaction
                        call results in a thrown exception).

                        Checks that the given message is a substring of the thrown message. For example
                        `my_tx.run_must_fail('out of bounds')` succeeds if an exception with message
                        `Run-time error: List index out of bounds: 0 (size 0)` is thrown.
                    """)
                }

                tx.signWithKeypairList(this, "the returned") {
                    comment("""
                        Create a test transaction builder containing this test operation call, and add the keypairs in
                        the given list as signers of the test transaction.

                        When the returned test transaction builder is built, the transaction will be signed with all
                        keypairs in the list.

                        @return a test transaction builder with this test operation call and given signers
                    """)
                }

                tx.signWithKeypairs(this, "the returned") {
                    comment("""
                        Create a test transaction builder containing this test operation call, and add the given
                        keypairs as signers of the test transaction.

                        When the returned test transaction builder is built, the transaction will be signed with all
                        given keypairs.

                        @return a test transaction builder with this test operation call and given signers
                    """)
                }

                tx.signWithPrivkeyList(this, "the returned") {
                    comment("""
                        Create a test transaction builder containing this test operation call, and add the private keys
                        in the given list as signers of the test transaction.

                        When the returned test transaction builder is built, the transaction will be signed with all
                        private keys in the list.

                        @return a test transaction builder with this test operation call and given signers
                    """)
                }

                tx.signWithPrivkeys(this, "the returned") {
                    comment("""
                        Create a test transaction builder containing this test operation call, and add the given private
                        keys as signers of the test transaction.

                        When the test transaction builder is built, the transaction will be signed with all given
                        private keys.

                        @return a test transaction builder with this test operation call and given signers
                    """)
                }

                property("name", type = "text", pure = true, since = "0.13.4") {
                    comment("The name of operation called by this test operation call.")
                    value { self ->
                        asTestOp(self).nameValue
                    }
                }

                property("args", type = "list<gtv>", pure = true, since = "0.13.4") {
                    comment("The arguments passed to the operation in this test operation call.")
                    value { self ->
                        asTestOp(self).argsValue()
                    }
                }

                function("tx", result = "rell.test.tx", since = SINCE0) {
                    comment("Create a test transaction builder containing this test operation call.")
                    body { arg ->
                        val op = asTestOp(arg).toRaw()
                        Rt_TestTxValue(listOf(op), listOf())
                    }
                }

                function("to_gtx_operation", result = "gtx_operation", pure = true, since = "0.13.4") {
                    comment("""
                        Convert this test operation call to a `gtx_operation`.

                        Inverse of `gtx_operation.to_test_op()`.
                    """)
                    body { self ->
                        val op = asTestOp(self)
                        val attrs = mutableListOf(op.nameValue, op.argsValue())
                        Rt_StructValue(Lib_Rell.GTX_OPERATION_STRUCT_TYPE, attrs)
                    }
                }
            }
        }
    }

    private fun selfToTx(self: Rt_Value): Rt_TestTxValue {
        return Rt_TestTxValue(listOf(asTestOp(self).toRaw()), listOf())
    }

    private fun newOp(nameArg: Rt_Value, tailArgs: List<Rt_Value>): Rt_Value {
        val nameStr = nameArg.asString()
        val args = tailArgs.mapToImmList { it.asGtv() }

        val name = MountName.ofOpt(nameStr)
        Rt_Utils.check(name != null && !name.isEmpty()) {
            "rell.test.op:bad_name:$nameStr" to "Bad operation name: '$nameStr'"
        }
        name!!

        return Rt_TestOpValue(name, args)
    }
}

private object Lib_Nop {
    private val MOUNT_NAME = MountName.of("nop")

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            function("nop", "rell.test.op", since = SINCE0) {
                comment("""
                        Create a no-op test operation call.

                        The zero-argument `rell.test.nop()` is effectively an alias of `rell.test.nop(x: integer)` where
                        a unique integer argument `x` is passed each time, serving the purpose of distinguishing
                        transactions that are identical except for the presence of a `nop` call.

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).run();
                        rell.test.tx().op(my_op).run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        // Ok, the transactions are distinct since each nop automatically uses a different nonce.
                        rell.test.tx().op(my_op).op(rell.test.nop()).run();
                        rell.test.tx().op(my_op).op(rell.test.nop()).run();
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                        """)
                bodyContext { ctx ->
                    callNoArgs(ctx)
                }
            }

            function("nop", "rell.test.op", since = SINCE0) {
                comment("""
                        Create a no-op test operation call.

                        Serves the purpose of distinguishing transactions that are identical except for the presence of
                        a `nop` call.

                        `rell.test.nop(x)` is equivalent to `rell.test.op('nop', x.to_gtv())`, which is a test operation
                        call to the predefined operation:

                        ```rell
                        operation nop(v: gtv) {}
                        ```

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).op(rell.test.nop(0)).run();
                        rell.test.tx().op(my_op).op(rell.test.nop(0)).run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        rell.test.tx().op(my_op).op(rell.test.nop(0)).run();
                        rell.test.tx().op(my_op).op(rell.test.nop(1)).run(); // Ok, since the transactions are distinct.
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                """)
                param("x", "integer", comment = "the nonce")
                body { arg ->
                    callOneArg(arg)
                }
            }

            function("nop", "rell.test.op", since = SINCE0) {
                comment("""
                        Create a no-op test operation call.

                        Serves the purpose of distinguishing transactions that are identical except for the presence of
                        a `nop` call.

                        `rell.test.nop(x)` is equivalent to `rell.test.op('nop', x.to_gtv())`, which is a test operation
                        call to the predefined operation:

                        ```rell
                        operation nop(v: gtv) {}
                        ```

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).op(rell.test.nop("a")).run();
                        rell.test.tx().op(my_op).op(rell.test.nop("a")).run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        rell.test.tx().op(my_op).op(rell.test.nop("a")).run();
                        rell.test.tx().op(my_op).op(rell.test.nop("z")).run(); // Ok, since the transactions are distinct.
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                        """)
                param("x", "text", comment = "the nonce")
                body { arg ->
                    callOneArg(arg)
                }
            }

            function("nop", "rell.test.op", since = SINCE0) {
                comment("""
                        Create a no-op test operation call.

                        Serves the purpose of distinguishing transactions that are identical except for the presence of
                        a `nop` call.

                        `rell.test.nop(x)` is equivalent to `rell.test.op('nop', x.to_gtv())`, which is a test operation
                        call to the predefined operation:

                        ```rell
                        operation nop(v: gtv) {}
                        ```

                        #### Example:
                        ##### Illegal
                        ```rell
                        rell.test.tx().op(my_op).op(rell.test.nop(x'00')).run();
                        rell.test.tx().op(my_op).op(rell.test.nop(x'00')).run(); // Not allowed; all transactions must be unique!
                        ```
                        ##### Legal
                        ```rell
                        rell.test.tx().op(my_op).op(rell.test.nop(x'00')).run();
                        rell.test.tx().op(my_op).op(rell.test.nop(x'01')).run(); // Ok, since the transactions are distinct.
                        ```

                        @see 1. <a href="https://en.wikipedia.org/wiki/NOP_(code)">NOP - Wikipedia</a>
                        @see 2. <a href="https://en.wikipedia.org/wiki/Cryptographic_nonce">Cryptographic nonce - Wikipedia</a>
                        """)
                param("x", "byte_array", comment = "the nonce")
                body { arg ->
                    callOneArg(arg)
                }
            }
        }
    }

    fun callNoArgs(ctx: Rt_CallContext): Rt_TestOpValue {
        val v = ctx.exeCtx.nextNopNonce()
        val gtv = GtvFactory.gtv(v)
        return makeValue(gtv)
    }

    fun callOneArg(arg: Rt_Value): Rt_TestOpValue {
        val gtv = checkNotNull(arg.type.gtvConversion) { "No GTV conversion for ${arg.type.name}" }.rtToGtv(arg, false)
        return makeValue(gtv)
    }

    private fun makeValue(arg: Gtv) = Rt_TestOpValue(MOUNT_NAME, immListOf(arg))
}

class RawTestTxValue(
    val ops: ImmList<RawTestOpValue>,
    val signers: ImmList<BytesKeyPair>,
) {
    override fun toString() = Rt_TestTxValue.toString(ops)
}

class RawTestOpValue(val name: MountName, val args: ImmList<Gtv>) {
    override fun toString() = Rt_TestOpValue.toString(name, args)
}

private fun asTestBlock(v: Rt_Value) = v as Rt_TestBlockValue
private fun asTestTx(v: Rt_Value) = v as Rt_TestTxValue
private fun asTestOp(v: Rt_Value) = v as Rt_TestOpValue
