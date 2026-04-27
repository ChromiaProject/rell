/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.rr.RR_FrameBlock
import net.postchain.rell.base.model.rr.RR_FrameDescriptor
import net.postchain.rell.base.model.rr.RR_VarPtr
import net.postchain.rell.base.utils.*
import java.util.*

sealed interface Rt_StatementResult
class Rt_StatementResult_Return(val value: Rt_Value?): Rt_StatementResult
data object Rt_StatementResult_Break: Rt_StatementResult
data object Rt_StatementResult_Continue: Rt_StatementResult

class Rt_CallFrame(
    val defCtx: Rt_DefinitionContext,
    private val rrFrame: RR_FrameDescriptor,
    state: Rt_CallFrameState?,
) {
    /** Legacy constructor — converts R_CallFrame to RR_FrameDescriptor. */
    constructor(defCtx: Rt_DefinitionContext, rFrame: R_CallFrame, state: Rt_CallFrameState?)
            : this(defCtx, rFrame.toRR(), state)

    val exeCtx = defCtx.exeCtx
    val sqlCtx = defCtx.sqlCtx
    val sysSqlExec = exeCtx.sysSqlExec
    val userSqlExec = exeCtx.userSqlExec
    val appCtx = exeCtx.appCtx

    private var curBlockUid = rrFrame.rootBlock.uid
    private var curBlockOffset = rrFrame.rootBlock.offset
    private var curBlockSize = rrFrame.rootBlock.size
    private val values = Array<Rt_Value?>(rrFrame.size) { null }

    private var beforeGuardBlock = rrFrame.hasGuardBlock

    init {
        if (state != null) {
            check(rrFrame.size >= state.values.size)
            for ((i, element) in state.values.withIndex()) {
                values[i] = element.orElse(null)
            }
        }
    }

    fun <T> block(block: RR_FrameBlock, code: () -> T): T {
        val oldUid = curBlockUid
        val oldOffset = curBlockOffset
        val oldSize = curBlockSize
        check(block.parentUid == oldUid) { "expected current block ${block.parentUid}, was $oldUid" }
        check(block.offset + block.size <= values.size)

        for (i in 0 until block.size) {
            checkNull(values[block.offset + i])
        }

        curBlockUid = block.uid
        curBlockOffset = block.offset
        curBlockSize = block.size
        try {
            val res = code()
            return res
        } finally {
            curBlockUid = oldUid
            curBlockOffset = oldOffset
            curBlockSize = oldSize
            for (i in 0 until block.size) {
                values[block.offset + i] = null
            }
        }
    }

    fun <T> blockOpt(block: RR_FrameBlock?, code: () -> T): T {
        return if (block == null) {
            code()
        } else {
            this.block(block, code)
        }
    }

    fun setUnchecked(ptr: RR_VarPtr, value: Rt_Value, overwrite: Boolean) {
        val offset = checkPtr(ptr.blockUid, ptr.offset)
        if (!overwrite) {
            checkNull(values[offset])
        }
        values[offset] = value
    }

    fun get(ptr: RR_VarPtr): Rt_Value {
        val offset = checkPtr(ptr.blockUid, ptr.offset)
        return checkNotNull(values[offset]) { "Variable not initialized: $ptr" }
    }

    private fun checkPtr(blockUid: Long, ptrOffset: Int): Int {
        check(blockUid == curBlockUid) { "wrong var block: var_ptr = Block[$blockUid]/Var[$ptrOffset], cur_block = Block[$curBlockUid]" }
        check(ptrOffset >= 0)
        check(ptrOffset < curBlockOffset + curBlockSize)
        return ptrOffset
    }

    fun callCtx() = Rt_CallContext(defCtx)

    fun dumpState(): Rt_CallFrameState {
        checkEquals(curBlockUid, rrFrame.rootBlock.uid)
        val valuesList = values.mapToImmList { Optional.ofNullable(it) }
        return Rt_CallFrameState(valuesList)
    }

    fun guardCompleted() {
        beforeGuardBlock = false
    }

    fun dbUpdateAllowed() = defCtx.dbUpdateAllowed && !beforeGuardBlock

    fun checkDbUpdateAllowed() {
        if (!defCtx.dbUpdateAllowed) {
            throw Rt_Exception.common("no_db_update:def", "Database modifications are not allowed")
        } else if (beforeGuardBlock) {
            throw Rt_Exception.common("no_db_update:guard", "Database modification before or inside a guard block")
        }
    }

    fun error(pos: ErrorPos, code: String, msg: String): Nothing {
        val err = Rt_CommonError(code, msg)
        val filePos = FilePos(pos.file, pos.line)
        val fullStack = immListOf(R_StackPos(defCtx.defId, filePos))
        val info = Rt_ExceptionInfo(extraMessage = null, stack = fullStack)
        throw Rt_Exception(err, info)
    }

    fun error(pos: ErrorPos, e: Rt_Exception, nested: Boolean = false): Nothing {
        val stackPos = R_StackPos(defCtx.defId, FilePos(pos.file, pos.line))
        val fullStack = if (nested || e.info.stack.isEmpty()) (e.info.stack + stackPos) else e.info.stack
        val info = Rt_ExceptionInfo(extraMessage = e.info.extraMessage, stack = fullStack)
        throw Rt_Exception(e.err, info, e)
    }
}

class Rt_CallFrameState(val values: ImmList<Optional<Rt_Value>>) {
    companion object {
        val EMPTY = Rt_CallFrameState(immListOf())
    }
}
