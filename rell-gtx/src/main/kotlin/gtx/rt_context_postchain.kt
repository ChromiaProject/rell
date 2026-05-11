/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.gtx

import net.postchain.core.BlockEContext
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtx.SnapshotContext
import net.postchain.gtx.data.OpData
import net.postchain.rell.base.lib.Lib_OpContext
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.utils.Bytes
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.ImmMap
import net.postchain.rell.base.utils.immMapOf

abstract class Rt_PostchainTxContext {
    abstract fun emitEvent(type: String, data: Gtv)
}

abstract class Rt_PostchainTxContextFactory {
    abstract fun createTxContext(eContext: TxEContext): Rt_PostchainTxContext
}

object Rt_DefaultPostchainTxContextFactory: Rt_PostchainTxContextFactory() {
    override fun createTxContext(eContext: TxEContext): Rt_PostchainTxContext {
        return Rt_DefaultPostchainPostchainTxContext(eContext)
    }

    private class Rt_DefaultPostchainPostchainTxContext(private val txCtx: TxEContext): Rt_PostchainTxContext() {
        override fun emitEvent(type: String, data: Gtv) {
            txCtx.emitEvent(type, data)
        }
    }
}

object Rt_CheckCorrectnessPostchainTxContext: Rt_PostchainTxContext() {
    override fun emitEvent(type: String, data: Gtv) {
        throw Rt_Exception.common("check_correctness:emit_event", "Cannot emit event during checkCorrectness step.")
    }
}

class Rt_PostchainOpContext(
    private val txCtx: Rt_PostchainTxContext,
    private val lastBlockTime: Long,
    private val transactionIid: Long,
    private val blockHeight: Long,
    private val opIndex: Int,
    private val signers: ImmList<Bytes>,
    private val allOperations: ImmList<OpData>,
    private val eCtx: BlockEContext? = null,
    private val snapshotContext: SnapshotContext? = null,
    private val objectSnapshotIds: ImmMap<String, Long> = immMapOf(),
): Rt_OpContext {
    override fun exists() = true
    override fun lastBlockTime() = lastBlockTime
    override fun transactionIid() = transactionIid
    override fun blockHeight() = blockHeight
    override fun opIndex() = opIndex
    override fun isSigner(pubKey: Bytes) = pubKey in signers
    override fun signers() = signers

    override fun allOperations(interpreter: Rt_Interpreter): List<Rt_Value> =
        allOperations.map { op -> opToRtValue(interpreter, op) }

    override fun currentOperation(interpreter: Rt_Interpreter): Rt_Value {
        val op = allOperations[opIndex]
        return opToRtValue(interpreter, op)
    }

    private fun opToRtValue(interpreter: Rt_Interpreter, op: OpData): Rt_Value =
        Lib_OpContext.gtxTransactionStructValue(interpreter, op.opName, op.args.asList())

    override fun emitEvent(type: String, data: Gtv) {
        txCtx.emitEvent(type, data)
    }

    override fun hasSnapshotContext() = eCtx != null && snapshotContext != null

    override fun objectSnapshotId(metaName: String): Long = objectSnapshotIds.getValue(metaName)

    override fun emitDatum(datumId: Long, datum: Gtv, isPermanent: Boolean) {
        if (eCtx != null) {
            snapshotContext?.emitDatum(eCtx, datumId, datum, isPermanent)
        }
    }
}
