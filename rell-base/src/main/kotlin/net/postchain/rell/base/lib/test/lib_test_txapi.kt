/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.test

import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.secp256k1_derivePubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.compiler.base.core.C_DefinitionName
import net.postchain.rell.base.compiler.base.lib.C_LibModule
import net.postchain.rell.base.compiler.base.utils.C_StringQualifiedName
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.lmodel.L_ParamArity
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.BytesKeyPair
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.immListOf
import net.postchain.rell.base.utils.mapToImmList
import net.postchain.rell.base.utils.toImmList
import java.util.*

object Lib_RellTest {
    private const val MODULE_NAME_STR = "rell.test"
    private val MODULE_NAME = R_ModuleName.of(MODULE_NAME_STR)

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
    val KEYPAIR_TYPE: R_StructType get() = KEYPAIR_STRUCT.type

    val BLOCK_TYPE = MODULE.getTypeDef("rell.test.block")
    val TX_TYPE = MODULE.getTypeDef("rell.test.tx")
    val OP_TYPE = MODULE.getTypeDef("rell.test.op")

    val FAILURE_TYPE = MODULE.getTypeDef("rell.test.failure")

    fun typeDefName(name: C_StringQualifiedName) = C_DefinitionName(MODULE_NAME_STR, name)
}

private const val SINCE0 = "0.10.4"

private fun typeDefName(name: String) = Lib_RellTest.typeDefName(C_StringQualifiedName.of(name.split(".")))

private object R_TestBlockType: R_LibSimpleType("rell.test.block", typeDefName("rell.test.block")) {
    override fun isReference() = true
    override fun isDirectMutable() = true
    override fun isDirectPure() = false
    override fun createGtvConversion() = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.BLOCK_TYPE
}

private object R_TestTxType: R_LibSimpleType("rell.test.tx", typeDefName("rell.test.tx")) {
    override fun isReference() = true
    override fun isDirectMutable() = true
    override fun isDirectPure() = false
    override fun createGtvConversion() = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.TX_TYPE
}

object R_TestOpType: R_LibSimpleType("rell.test.op", typeDefName("rell.test.op")) {
    override fun isReference() = true
    override fun isDirectPure() = false
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.OP_TYPE
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
            param("expected_message", "text", comment = "String that must be contained in the error message")
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
        } catch (e: Throwable) {
            val actual = e.message ?: ""
            Lib_Test_Assert.checkErrorMessage("run_must_fail", expected, actual)
            return Lib_Test_Assert.failureValue(actual)
        }
        throw Rt_Exception.common("fn:$runMustFailFullName:nofail", "Transaction did not fail")
    }
}

private class TxCommonFunctions(private val txGetter: (self: Rt_Value) -> Rt_TestTxValue) {
    fun signWithKeypairList(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("keypairs", type = "list<rell.test.keypair>", comment = "Keypairs to sign this transaction with.")
            body { arg1, arg2 ->
                signByKeyPairs(arg1, arg2.asList())
            }
        }
    }

    fun signWithKeypairs(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("keypairs", type = "rell.test.keypair", arity = L_ParamArity.ONE_MANY) {
                comment("Keypairs to sign this transaction with.")
            }
            bodyN { args ->
                check(args.isNotEmpty())
                signByKeyPairs(args[0], args.subList(1, args.size))
            }
        }
    }

    fun signWithPrivkeyList(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("privkeys", type = "list<byte_array>", comment = "Private keys to sign this transaction with.")
            body { arg1, arg2 ->
                signByByteArrays(arg1, arg2.asList())
            }
        }
    }

    fun signWithPrivkeys(m: Ld_TypeDefDsl, block: Ld_FunctionDsl.() -> Unit) = with(m) {
        function("sign", "rell.test.tx", since = SINCE0) {
            block()
            param("privkeys", "byte_array", arity = L_ParamArity.ONE_MANY) {
                comment("Private keys to sign this transaction with.")
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
        Rt_Utils.check(priv.size == privSize) { "tx.sign:priv_key_size:$privSize:${priv.size}" toCodeMsg
                "Wrong size of private key: ${priv.size} instead of $privSize"
        }

        val pub = secp256k1_derivePubKey(priv)
        val pubSize = 33
        Rt_Utils.check(pub.size == pubSize) { "tx.sign:pub_key_size:$pubSize:${pub.size}" toCodeMsg
                "Wrong size of calculated public key: ${pub.size} instead of $pubSize"
        }

        return BytesKeyPair(priv, pub)
    }
}

object Lib_Type_Block {
    private val common = BlockCommonFunctions("rell.test.block") { asTestBlock(it) }

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type("block", rType = R_TestBlockType, since = SINCE0) {
                comment("Represents a block builder which can build a block on the blockchain.")

                constructor(since = SINCE0) {
                    comment("Creates a new block builder with specified transactions.")
                    param("txs", type = "list<rell.test.tx>") {
                        comment("Transactions to be included in this block builder")
                    }
                    body { arg ->
                        Rt_TestBlockValue(arg.asList().map { asTestTx(it).toRaw() })
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new block builder with specified transactions.")
                    param("txs", type = "rell.test.tx", arity = L_ParamArity.ZERO_MANY) {
                        comment("Transactions to be included in this block builder")
                    }
                    bodyN { args ->
                        Rt_TestBlockValue(args.map { asTestTx(it).toRaw() })
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new block builder with a transaction containing the specified operations.")
                    param("ops", type = "list<rell.test.op>") {
                        comment("Operations to be included in a transaction in this block builder")
                    }
                    body { arg ->
                        newOps(arg.asList())
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new block builder with a transaction containing the specified operations.")
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY) {
                        comment("Operations to be included in a transaction in this block builder")
                    }
                    bodyN { args ->
                        newOps(args)
                    }
                }

                common.runFunction(this) {
                    comment("Build this block")
                }

                common.runMustFailFunction(this) {
                    comment("Try to build the block and require it to fail.")
                }

                common.runMustFailWithMessageFunction(this) {
                    comment("Try to build the block and require it to fail with an expected message.")
                }

                function("copy", "rell.test.block", since = SINCE0) {
                    comment("Copies this block builder.")
                    body { arg ->
                        val block = asTestBlock(arg)
                        Rt_TestBlockValue(block.txs())
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("Adds a list of transactions to this block builder.")
                    param("txs", type = "list<rell.test.tx>", comment = "Transactions to be added to this block")
                    body { arg1, arg2 ->
                        addTxs(arg1, arg2.asList())
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("Adds a list of transactions to this block builder.")
                    param("txs", type = "rell.test.tx", arity = L_ParamArity.ONE_MANY) {
                        comment("Transactions to be added to this block")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addTxs(args[0], args.subList(1, args.size))
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("Adds a transaction containing a list of operations.")
                    param("ops", type = "list<rell.test.op>") {
                        comment("Operations to include in the transaction")
                    }
                    body { arg1, arg2 ->
                        addOps(arg1, arg2.asList())
                    }
                }

                function("tx", "rell.test.block", since = SINCE0) {
                    comment("Adds a transaction containing a list of operations.")
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY) {
                        comment("Operations to include in the transaction")
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
                    Represents a transaction builder that can build a block
                    containing this transacation on the blockchain.
                """)

                constructor(since = SINCE0) {
                    comment("Creates a new transaction builder containing a list of operations.")
                    param("ops", type = "list<rell.test.op>") {
                        comment("Operations to be included in this transaction")
                    }
                    body { arg ->
                        val ops = arg.asList().map { asTestOp(it).toRaw() }
                        newTx(ops)
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new transaction builder containing any number of operations.")
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ZERO_MANY) {
                        comment("Operations to be included in this transaction")
                    }
                    bodyN { args ->
                        val ops = args.map { asTestOp(it).toRaw() }
                        newTx(ops)
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new transaction builder containing a list of operation structures.")
                    param("ops", type = "list<-mirror_struct<-operation>>") {
                        comment("Operations to be included in this transaction")
                    }
                    body { arg ->
                        val list = arg.asList()
                        val ops = list.map { structToOpRaw(it) }
                        newTx(ops)
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new transaction builder containing any number of operation structures.")
                    param("ops", type = "mirror_struct<-operation>", arity = L_ParamArity.ONE_MANY) {
                        comment("Operations to be included in this transaction")
                    }
                    bodyN { args ->
                        val ops = args.map { structToOpRaw(it) }
                        newTx(ops)
                    }
                }

                block.runFunction(this) {
                    comment("Build a block that contains this transaction.")
                }

                block.runMustFailFunction(this) {
                    comment("Try to build a block that contains this transaction and require it to fail.")
                }

                block.runMustFailWithMessageFunction(this) {
                    comment("""
                        Try to build a block that contains this transaction and
                        require it to fail with an expected message.
                    """)
                }

                tx.signWithKeypairList(this) {
                    comment("Sign this transaction with a number of keypairs.")
                }

                tx.signWithKeypairs(this) {
                    comment("Sign this transaction with a number of keypairs.")
                }

                tx.signWithPrivkeyList(this) {
                    comment("Sign this transaction with a number of private keys.")
                }

                tx.signWithPrivkeys(this) {
                    comment("Sign this transaction with a number of private keys.")
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("Adds a list op operations to this transaction builder.")
                    param("ops", type = "list<rell.test.op>", comment = "The operations to add")
                    body { arg1, arg2 ->
                        addOps(arg1, arg2.asList())
                    }
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("Adds a list op operations to this transaction builder.")
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY) {
                       comment("The operations to add")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOps(args[0], args.subList(1, args.size))
                    }
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("Adds a list op operation structures to this transaction builder.")
                    param("ops", type = "list<-mirror_struct<-operation>>") {
                       comment("The operation structures to add")
                    }
                    body { arg1, arg2 ->
                        addOpStructs(arg1, arg2.asList())
                    }
                }

                function("op", "rell.test.tx", since = SINCE0) {
                    comment("Adds a list op operation structures to this transaction builder.")
                    param("ops", type = "mirror_struct<-operation>", arity = L_ParamArity.ONE_MANY) {
                        comment("The operation structures to add")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOpStructs(args[0], args.subList(1, args.size))
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("Adds a nop operation to this transaction builder.")
                    bodyContext { ctx, arg ->
                        val tx = asTestTx(arg)
                        val op = Lib_Nop.callNoArgs(ctx)
                        tx.addOp(op.toRaw())
                        tx
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("Adds a nop operation with a given nonce to this transaction builder.")
                    param("x", type = "integer", comment = "nonce to use")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("Adds a nop operation with a given nonce to this transaction builder.")
                    param("x", "text", comment = "nonce to use")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("nop", "rell.test.tx", since = SINCE0) {
                    comment("Adds a nop operation with a given nonce to this transaction builder.")
                    param("x", "byte_array", comment = "nonce to use")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("copy", "rell.test.tx", since = SINCE0) {
                    comment("copies this transaction builder.")
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

    private fun addOpStructs(self: Rt_Value, ops: List<Rt_Value>): Rt_Value {
        val tx = asTestTx(self)
        ops.forEach { tx.addOp(structToOpRaw(it)) }
        return self
    }

    private fun calcNopOneArg(arg1: Rt_Value, arg2: Rt_Value): Rt_Value {
        val tx = asTestTx(arg1)
        val op = Lib_Nop.callOneArg(arg2)
        tx.addOp(op.toRaw())
        return tx
    }

    private fun structToOpRaw(v: Rt_Value): RawTestOpValue = structToOp(v).toRaw()

    fun structToOp(a: Rt_Value): Rt_TestOpValue {
        val (mountName, args) = Lib_Type_Struct.decodeOperation(a)
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
                    comment("Convert this struct to a test operation.")
                    validate { ctx ->
                        if (!ctx.exprCtx.modCtx.isTestLib()) {
                            val fnName = this.fnSimpleName
                            ctx.exprCtx.msgCtx.error(ctx.callPos,
                                "expr:fn:struct:$fnName:no_test",
                                "Function '$fnName' can be called only in tests or REPL"
                            )
                        }
                    }
                    body { a ->
                        Lib_Type_Tx.structToOp(a)
                    }
                }
            }

            extension("gtx_operation_ext", type = "gtx_operation", since = "0.13.4") {
                function("to_test_op", "rell.test.op", since = "0.13.4") {
                    comment("Convert this struct to a test operation.")
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
                        checkEquals(sv.type(), Lib_Rell.GTX_OPERATION_STRUCT_TYPE)
                        val rtName = sv.get(0)
                        val rtArgs = sv.get(1).asList()
                        val mountName = R_MountName.of(rtName.asString())
                        val gtvArgs = rtArgs.mapToImmList { it.asGtv() }
                        Rt_TestOpValue(mountName, gtvArgs)
                    }
                }
            }

            type("op", rType = R_TestOpType, since = SINCE0) {
                comment("Represent a operation that can be included in a transaction.")

                constructor(since = SINCE0) {
                    comment("Creates a new test operation.")
                    param("name", type = "text", comment = "Name of the operation")
                    param("args", type = "list<gtv>", comment = "Arguments to be supplied to the operation")
                    body { arg1, arg2 ->
                        newOp(arg1, arg2.asList())
                    }
                }

                constructor(since = SINCE0) {
                    comment("Creates a new test operation.")
                    param("name", type = "text", comment = "Name of the operation")
                    param("args", type = "gtv", arity = L_ParamArity.ZERO_MANY) {
                        comment("Arguments to be supplied to the operation")
                    }
                    bodyN { args ->
                        check(args.isNotEmpty())
                        val nameArg = args[0]
                        val tailArgs = args.subList(1, args.size)
                        newOp(nameArg, tailArgs)
                    }
                }

                block.runFunction(this) {
                    comment("Build a block that contains a transaction with this operation.")
                }

                block.runMustFailFunction(this) {
                    comment("""
                        Try to build a block that contains a transaction including this operation and require it to fail.
                    """)
                }

                block.runMustFailWithMessageFunction(this) {
                    comment("""
                        Try to build a block that contains a transaction including this operation and
                        require it to fail with an expected message.
                    """)
                }

                tx.signWithKeypairList(this) {
                    comment("Creates a new transaction builder and signs it with a number of keypairs.")
                }

                tx.signWithKeypairs(this) {
                    comment("Creates a new transaction builder and signs it with a number of keypairs.")
                }

                tx.signWithPrivkeyList(this) {
                    comment("Creates a new transaction builder and signs it with a number of private keys.")
                }

                tx.signWithPrivkeys(this) {
                    comment("Creates a new transaction builder and signs it with a number of private keys.")
                }

                property("name", type = "text", pure = true, since = "0.13.4", comment = "Name of this operaiton") {
                    value { self ->
                        asTestOp(self).nameValue
                    }
                }

                property("args", type = "list<gtv>", pure = true, since = "0.13.4") {
                    comment("Arguments to this operation")
                    value { self ->
                        asTestOp(self).argsValue()
                    }
                }

                function("tx", result = "rell.test.tx", since = SINCE0) {
                    comment("Creates a new transaction builder that contains this operation.")
                    body { arg ->
                        val op = asTestOp(arg).toRaw()
                        Rt_TestTxValue(listOf(op), listOf())
                    }
                }

                function("to_gtx_operation", result = "gtx_operation", pure = true, since = "0.13.4") {
                    comment("Convert this operation to a `gtx_operation` struct.")
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

        val name = R_MountName.ofOpt(nameStr)
        Rt_Utils.check(name != null && !name.isEmpty()) {
            "rell.test.op:bad_name:$nameStr" toCodeMsg "Bad operation name: '$nameStr'"
        }
        name!!

        return Rt_TestOpValue(name, args)
    }
}

private object Lib_Nop {
    private val MOUNT_NAME = R_MountName.of("nop")

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            function("nop", "rell.test.op", since = SINCE0) {
                comment("Creates a new no-op operation.")
                bodyContext { ctx ->
                    callNoArgs(ctx)
                }
            }

            function("nop", "rell.test.op", since = SINCE0) {
                comment("Creates a new no-op operation with a given nonce.")
                param("x", "integer", comment = "nonce to use")
                body { arg ->
                    callOneArg(arg)
                }
            }

            function("nop", "rell.test.op", since = SINCE0) {
                comment("Creates a new no-op operation with a given nonce.")
                param("x", "text", comment = "nonce to use")
                body { arg ->
                    callOneArg(arg)
                }
            }

            function("nop", "rell.test.op", since = SINCE0) {
                comment("Creates a new no-op operation with a given nonce.")
                param("x", "byte_array", comment = "nonce to use")
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
        val gtv = arg.type().rtToGtv(arg, false)
        return makeValue(gtv)
    }

    private fun makeValue(arg: Gtv) = Rt_TestOpValue(MOUNT_NAME, immListOf(arg))
}

class Rt_TestBlockValue(txs: List<RawTestTxValue>): Rt_Value() {
    private val txs = txs.toMutableList()

    override val valueType = VALUE_TYPE

    override fun type(): R_Type = R_TestBlockType

    override fun strCode(showTupleFieldNames: Boolean): String {
        val txsStr = txs.joinToString(",") { Rt_TestTxValue.strCode(it.ops, it.signers) }
        return "${R_TestBlockType.str()}[$txsStr]"
    }

    override fun str(format: StrFormat) = "block(${txs.joinToString(",")})"

    override fun equals(other: Any?) = other === this || (other is Rt_TestBlockValue && txs == other.txs)
    override fun hashCode() = Objects.hash(txs)

    fun txs() = txs.toImmList()

    fun addTx(tx: RawTestTxValue) {
        txs.add(tx)
    }

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("TEST_BLOCK")
    }
}

class Rt_TestTxValue(
        ops: List<RawTestOpValue>,
        signers: List<BytesKeyPair>
): Rt_Value() {
    private val ops = ops.toMutableList()
    private val signers = signers.toMutableList()

    override val valueType = VALUE_TYPE

    override fun type(): R_Type = R_TestTxType

    override fun str(format: StrFormat) = toString(ops)
    override fun strCode(showTupleFieldNames: Boolean) = strCode(ops, signers)

    override fun equals(other: Any?) = other === this || (other is Rt_TestTxValue && ops == other.ops && signers == other.signers)
    override fun hashCode() = Objects.hash(ops, signers)

    fun addOp(op: RawTestOpValue) {
        ops.add(op)
    }

    fun sign(keyPair: BytesKeyPair) {
        signers.add(keyPair)
    }

    fun copy() = Rt_TestTxValue(ops, signers)

    fun toRaw() = RawTestTxValue(ops.toImmList(), signers.toImmList())

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("TEST_TX")

        fun strCode(ops: List<RawTestOpValue>, signers: List<BytesKeyPair>): String {
            val opsList = ops.map { Rt_TestOpValue.strCode(it.name, it.args) }
            val signersList = signers.map { it.pub.toHex().substring(0, 6).lowercase()}
            val innerStr = (opsList + signersList).joinToString(",")
            return "${R_TestTxType.str()}[$innerStr]"
        }

        fun toString(ops: List<RawTestOpValue>): String {
            val opsStr = ops.joinToString(",")
            return "tx[$opsStr]"
        }
    }
}

class Rt_TestOpValue(private val name: R_MountName, val args: ImmList<Gtv>): Rt_Value() {
    val nameValue: Rt_Value by lazy {
        Rt_TextValue.get(name.str())
    }

    override val valueType = VALUE_TYPE

    override fun type(): R_Type = R_TestOpType

    override fun str(format: StrFormat) = toString(name, args)
    override fun strCode(showTupleFieldNames: Boolean) = strCode(name, args)

    override fun equals(other: Any?) = other === this || (other is Rt_TestOpValue && name == other.name && args == other.args)
    override fun hashCode() = Objects.hash(name, args)

    fun toRaw() = RawTestOpValue(name, args)

    fun argsValue(): Rt_Value {
        val argValues: MutableList<Rt_Value> = args.map { Rt_GtvValue.get(it) }.toMutableList()
        return Rt_ListValue(Lib_Type_Gtv.LIST_OF_GTV_TYPE, argValues)
    }

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("TEST_OP")

        fun strCode(name: R_MountName, args: List<Gtv>): String {
            val argsStr = args.joinToString(",") { Rt_GtvValue.get(it).str(StrFormat.V2) }
            return "op[$name($argsStr)]"
        }

        fun toString(name: R_MountName, args: List<Gtv>): String {
            val argsStr = args.joinToString(",") { Rt_GtvValue.get(it).str(StrFormat.V2) }
            return "$name($argsStr)"
        }
    }
}

class RawTestTxValue(
    val ops: ImmList<RawTestOpValue>,
    val signers: ImmList<BytesKeyPair>,
) {
    override fun toString() = Rt_TestTxValue.toString(ops)
}

class RawTestOpValue(val name: R_MountName, val args: ImmList<Gtv>) {
    override fun toString() = Rt_TestOpValue.toString(name, args)
}

private fun asTestBlock(v: Rt_Value) = v as Rt_TestBlockValue
private fun asTestTx(v: Rt_Value) = v as Rt_TestTxValue
private fun asTestOp(v: Rt_Value) = v as Rt_TestOpValue
