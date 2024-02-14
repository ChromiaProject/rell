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
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.lmodel.dsl.Ld_TypeDefDsl
import net.postchain.rell.base.model.*
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.BytesKeyPair
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.toImmList
import java.util.*

object Lib_RellTest {
    private const val MODULE_NAME_STR = "rell.test"
    private val MODULE_NAME = R_ModuleName.of(MODULE_NAME_STR)

    val NAMESPACE_NAME = C_StringQualifiedName.ofRNames(MODULE_NAME.parts)

    const val BLOCK_SNAME = "block"
    const val TX_SNAME = "tx"
    const val OP_SNAME = "op"

    val BLOCK_TYPE_QNAME = NAMESPACE_NAME.add(BLOCK_SNAME)
    val TX_TYPE_QNAME = NAMESPACE_NAME.add(TX_SNAME)
    val OP_TYPE_QNAME = NAMESPACE_NAME.add(OP_SNAME)

    val BLOCK_TYPE_QNAME_STR = BLOCK_TYPE_QNAME.str()
    val TX_TYPE_QNAME_STR = TX_TYPE_QNAME.str()
    val OP_TYPE_QNAME_STR = OP_TYPE_QNAME.str()

    val BLOCK_RUNNER_KEYPAIR: BytesKeyPair = let {
        val privKey = "42".repeat(32).hexStringToByteArray()
        val pubKey = secp256k1_derivePubKey(privKey)
        BytesKeyPair(privKey, pubKey)
    }

    val MODULE = C_LibModule.make("rell.test", Lib_Rell.MODULE) {
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

private fun typeDefName(name: C_StringQualifiedName) = Lib_RellTest.typeDefName(name)

private object R_TestBlockType: R_LibSimpleType(Lib_RellTest.BLOCK_TYPE_QNAME.str(), typeDefName(Lib_RellTest.BLOCK_TYPE_QNAME)) {
    override fun isReference() = true
    override fun isDirectMutable() = true
    override fun isDirectPure() = false
    override fun createGtvConversion() = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.BLOCK_TYPE
}

private object R_TestTxType: R_LibSimpleType(Lib_RellTest.TX_TYPE_QNAME.str(), typeDefName(Lib_RellTest.TX_TYPE_QNAME)) {
    override fun isReference() = true
    override fun isDirectMutable() = true
    override fun isDirectPure() = false
    override fun createGtvConversion() = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.TX_TYPE
}

object R_TestOpType: R_LibSimpleType(Lib_RellTest.OP_TYPE_QNAME.str(), typeDefName(Lib_RellTest.OP_TYPE_QNAME)) {
    override fun isReference() = true
    override fun isDirectPure() = false
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_RellTest.OP_TYPE
}

private class BlockCommonFunctions(
    typeQName: String,
    private val blockGetter: (self: Rt_Value) -> Rt_TestBlockValue,
) {
    private val runSimpleName = "run"
    private val runMustFailSimpleName = "run_must_fail"

    private val runFullName = "$typeQName.$runSimpleName"
    private val runMustFailFullName = "$typeQName.$runMustFailSimpleName"

    fun define(m: Ld_TypeDefDsl) = with(m) {
        val sFailType = Lib_Test_Assert.FAILURE_TYPE.name

        function(runSimpleName, "unit") {
            bodyContext { ctx, arg ->
                val block = getRunBlock(ctx, arg, runFullName)
                try {
                    ctx.appCtx.blockRunner.runBlock(ctx, block)
                } catch (e: Rt_Exception) {
                    throw e
                } catch (e: Throwable) {
                    throw Rt_Exception.common("fn:$runFullName:fail:${e.javaClass.canonicalName}", "Block execution failed: $e")
                }
                Rt_UnitValue
            }
        }

        function(runMustFailSimpleName, sFailType) {
            bodyContext { ctx, arg ->
                val block = getRunBlock(ctx, arg, runMustFailFullName)
                runMustFail(ctx, block, null)
            }
        }

        function(runMustFailSimpleName, sFailType) {
            param("expected_message", "text")
            bodyContext { ctx, arg1, arg2 ->
                val block = getRunBlock(ctx, arg1, runMustFailFullName)
                val expected = arg2.asString()
                runMustFail(ctx, block, expected)
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
            Lib_Test_Assert.checkErrorMessage(runMustFailSimpleName, expected, actual)
            return Lib_Test_Assert.failureValue(actual)
        }
        throw Rt_Exception.common("fn:$runMustFailFullName:nofail", "Transaction did not fail")
    }
}

private class TxCommonFunctions(private val txGetter: (self: Rt_Value) -> Rt_TestTxValue) {
    fun define(m: Ld_TypeDefDsl) = with(m) {
        val sTxType = R_TestTxType.name

        function("sign", sTxType) {
            param("keypairs", type = "list<rell.test.keypair>")
            body { arg1, arg2 ->
                signByKeyPairs(arg1, arg2.asList())
            }
        }

        function("sign", sTxType) {
            param("privkeys", type = "list<byte_array>")
            body { arg1, arg2 ->
                signByByteArrays(arg1, arg2.asList())
            }
        }

        function("sign", sTxType) {
            param("privkeys", "byte_array", arity = L_ParamArity.ONE_MANY)
            bodyN { args ->
                check(args.isNotEmpty())
                signByByteArrays(args[0], args.subList(1, args.size))
            }
        }

        function("sign", sTxType) {
            param("keypairs", type = "rell.test.keypair", arity = L_ParamArity.ONE_MANY)
            bodyN { args ->
                check(args.isNotEmpty())
                signByKeyPairs(args[0], args.subList(1, args.size))
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

private object Lib_Type_Block {
    private val common = BlockCommonFunctions(Lib_RellTest.BLOCK_TYPE_QNAME_STR) { asTestBlock(it) }

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type(Lib_RellTest.BLOCK_SNAME, rType = R_TestBlockType) {
                common.define(this)

                constructor {
                    param("txs", type = "list<${Lib_RellTest.TX_TYPE_QNAME_STR}>")
                    body { arg ->
                        Rt_TestBlockValue(arg.asList().map { asTestTx(it).toRaw() })
                    }
                }

                constructor {
                    param("txs", type = Lib_RellTest.TX_TYPE_QNAME_STR, arity = L_ParamArity.ZERO_MANY)
                    bodyN { args ->
                        Rt_TestBlockValue(args.map { asTestTx(it).toRaw() })
                    }
                }

                constructor {
                    param("ops", type = "list<${Lib_RellTest.OP_TYPE_QNAME_STR}>")
                    body { arg ->
                        newOps(arg.asList())
                    }
                }

                constructor {
                    param("ops", type = Lib_RellTest.OP_TYPE_QNAME_STR, arity = L_ParamArity.ONE_MANY)
                    bodyN { args ->
                        newOps(args)
                    }
                }

                val sSelfType = "rell.test.block"

                function("copy", sSelfType) {
                    body { arg ->
                        val block = asTestBlock(arg)
                        Rt_TestBlockValue(block.txs())
                    }
                }

                function("tx", sSelfType) {
                    param("txs", type = "list<rell.test.tx>")
                    body { arg1, arg2 ->
                        addTxs(arg1, arg2.asList())
                    }
                }

                function("tx", sSelfType) {
                    param("txs", type = "rell.test.tx", arity = L_ParamArity.ONE_MANY)
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addTxs(args[0], args.subList(1, args.size))
                    }
                }

                function("tx", sSelfType) {
                    param("ops", type = "list<rell.test.op>")
                    body { arg1, arg2 ->
                        addOps(arg1, arg2.asList())
                    }
                }

                function("tx", sSelfType) {
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY)
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOps(args[0], args.subList(1, args.size))
                    }
                }
            }
        }
    }

    private fun newOps(ops: List<Rt_Value>): Rt_Value {
        val rawOps = ops.map { asTestOp(it).toRaw() }
        val tx = RawTestTxValue(rawOps, listOf())
        return Rt_TestBlockValue(listOf(tx))
    }

    private fun addTxs(self: Rt_Value, txs: List<Rt_Value>): Rt_Value {
        val block = asTestBlock(self)
        txs.forEach { block.addTx(asTestTx(it).toRaw()) }
        return self
    }

    private fun addOps(self: Rt_Value, ops: List<Rt_Value>): Rt_Value {
        val block = asTestBlock(self)
        val rawOps = ops.map { asTestOp(it).toRaw() }
        val tx = RawTestTxValue(rawOps, listOf())
        block.addTx(tx)
        return self
    }
}

private object Lib_Type_Tx {
    private val block = BlockCommonFunctions(Lib_RellTest.TX_TYPE_QNAME_STR) {
        Rt_TestBlockValue(listOf(asTestTx(it).toRaw()))
    }

    private val tx = TxCommonFunctions { asTestTx(it) }

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            type(Lib_RellTest.TX_SNAME, rType = R_TestTxType) {
                block.define(this)
                tx.define(this)

                val sSelfType = "rell.test.tx"

                constructor {
                    param("ops", type = "list<${Lib_RellTest.OP_TYPE_QNAME_STR}>")
                    body { arg ->
                        val ops = arg.asList().map { asTestOp(it).toRaw() }
                        newTx(ops)
                    }
                }

                constructor {
                    param("ops", type = Lib_RellTest.OP_TYPE_QNAME_STR, arity = L_ParamArity.ZERO_MANY)
                    bodyN { args ->
                        val ops = args.map { asTestOp(it).toRaw() }
                        newTx(ops)
                    }
                }

                constructor {
                    param("ops", type = "list<-mirror_struct<-operation>>")
                    body { arg ->
                        val list = arg.asList()
                        val ops = list.map { structToOpRaw(it) }
                        newTx(ops)
                    }
                }

                constructor {
                    param("ops", type = "mirror_struct<-operation>", arity = L_ParamArity.ONE_MANY)
                    bodyN { args ->
                        val ops = args.map { structToOpRaw(it) }
                        newTx(ops)
                    }
                }

                function("op", sSelfType) {
                    param("ops", type = "list<rell.test.op>")
                    body { arg1, arg2 ->
                        addOps(arg1, arg2.asList())
                    }
                }

                function("op", sSelfType) {
                    param("ops", type = "rell.test.op", arity = L_ParamArity.ONE_MANY)
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOps(args[0], args.subList(1, args.size))
                    }
                }

                function("op", sSelfType) {
                    param("ops", type = "list<-mirror_struct<-operation>>")
                    body { arg1, arg2 ->
                        addOpStructs(arg1, arg2.asList())
                    }
                }

                function("op", sSelfType) {
                    param("ops", type = "mirror_struct<-operation>", arity = L_ParamArity.ONE_MANY)
                    bodyN { args ->
                        check(args.isNotEmpty())
                        addOpStructs(args[0], args.subList(1, args.size))
                    }
                }

                function("nop", sSelfType) {
                    bodyContext { ctx, arg ->
                        val tx = asTestTx(arg)
                        val op = Lib_Nop.callNoArgs(ctx)
                        tx.addOp(op.toRaw())
                        tx
                    }
                }

                function("nop", sSelfType) {
                    param("x", type = "integer")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("nop", sSelfType) {
                    param("x", "text")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("nop", sSelfType) {
                    param("x", "byte_array")
                    body { arg1, arg2 ->
                        calcNopOneArg(arg1, arg2)
                    }
                }

                function("copy", sSelfType) {
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
    private val block = BlockCommonFunctions(Lib_RellTest.OP_TYPE_QNAME_STR) {
        Rt_TestBlockValue(listOf(selfToTx(it).toRaw()))
    }

    private val tx = TxCommonFunctions(this::selfToTx)

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            extension("struct_op_ext", type = "mirror_struct<-operation>") {
                function("to_test_op", "rell.test.op") {
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

            extension("gtx_operation_ext", type = "gtx_operation") {
                function("to_test_op", "rell.test.op") {
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
                        val gtvArgs = rtArgs.map { it.asGtv() }
                        Rt_TestOpValue(mountName, gtvArgs)
                    }
                }
            }

            type(Lib_RellTest.OP_SNAME, rType = R_TestOpType) {
                block.define(this)
                tx.define(this)

                constructor {
                    param("name", type = "text")
                    param("args", type = "list<gtv>")
                    body { arg1, arg2 ->
                        newOp(arg1, arg2.asList())
                    }
                }

                constructor {
                    param("name", type = "text")
                    param("args", type = "gtv", arity = L_ParamArity.ZERO_MANY)
                    bodyN { args ->
                        check(args.isNotEmpty())
                        val nameArg = args[0]
                        val tailArgs = args.subList(1, args.size)
                        newOp(nameArg, tailArgs)
                    }
                }

                property("name", type = "text", pure = true) {
                    value { self ->
                        asTestOp(self).nameValue
                    }
                }

                property("args", type = "list<gtv>", pure = true) {
                    value { self ->
                        asTestOp(self).argsValue()
                    }
                }

                function("tx", result = "rell.test.tx") {
                    body { arg ->
                        val op = asTestOp(arg).toRaw()
                        Rt_TestTxValue(listOf(op), listOf())
                    }
                }

                function("to_gtx_operation", result = "gtx_operation", pure = true) {
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
        val args = tailArgs.map { it.asGtv() }

        val name = R_MountName.ofOpt(nameStr)
        Rt_Utils.check(name != null && !name.isEmpty()) {
            "${Lib_RellTest.OP_TYPE_QNAME_STR}:bad_name:$nameStr" toCodeMsg "Bad operation name: '$nameStr'"
        }
        name!!

        return Rt_TestOpValue(name, args)
    }
}

private object Lib_Nop {
    private val MOUNT_NAME = R_MountName.of("nop")

    val NAMESPACE = Ld_NamespaceDsl.make {
        namespace("rell.test") {
            val testOpType = R_TestOpType.name

            function("nop", testOpType) {
                bodyContext { ctx ->
                    callNoArgs(ctx)
                }
            }

            function("nop", testOpType) {
                param("x", "integer")
                body { arg ->
                    callOneArg(arg)
                }
            }

            function("nop", testOpType) {
                param("x", "text")
                body { arg ->
                    callOneArg(arg)
                }
            }

            function("nop", testOpType) {
                param("x", "byte_array")
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

    private fun makeValue(arg: Gtv) = Rt_TestOpValue(MOUNT_NAME, listOf(arg))
}

class Rt_TestBlockValue(txs: List<RawTestTxValue>): Rt_Value() {
    private val txs = txs.toMutableList()

    override val valueType = VALUE_TYPE

    override fun type(): R_Type = R_TestBlockType

    override fun strCode(showTupleFieldNames: Boolean): String {
        val txsStr = txs.joinToString(",") { Rt_TestTxValue.strCode(it.ops, it.signers) }
        return "${Lib_RellTest.BLOCK_TYPE_QNAME_STR}[$txsStr]"
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

    fun toRaw() = RawTestTxValue(ops, signers)

    companion object {
        private val VALUE_TYPE = Rt_LibValueType.of("TEST_TX")

        fun strCode(ops: List<RawTestOpValue>, signers: List<BytesKeyPair>): String {
            val opsList = ops.map { Rt_TestOpValue.strCode(it.name, it.args) }
            val signersList = signers.map { it.pub.toHex().substring(0, 6).lowercase()}
            val innerStr = (opsList + signersList).joinToString(",")
            return "${Lib_RellTest.TX_TYPE_QNAME_STR}[$innerStr]"
        }

        fun toString(ops: List<RawTestOpValue>): String {
            val opsStr = ops.joinToString(",")
            return "tx[$opsStr]"
        }
    }
}

class Rt_TestOpValue(private val name: R_MountName, args: List<Gtv>): Rt_Value() {
    val args = args.toImmList()

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
    ops: List<RawTestOpValue>,
    signers: List<BytesKeyPair>,
) {
    val ops = ops.toImmList()
    val signers = signers.toImmList()

    override fun toString() = Rt_TestTxValue.toString(ops)
}

class RawTestOpValue(val name: R_MountName, args: List<Gtv>) {
    val args = args.toImmList()

    override fun toString() = Rt_TestOpValue.toString(name, args)
}

private fun asTestBlock(v: Rt_Value) = v as Rt_TestBlockValue
private fun asTestTx(v: Rt_Value) = v as Rt_TestTxValue
private fun asTestOp(v: Rt_Value) = v as Rt_TestOpValue
