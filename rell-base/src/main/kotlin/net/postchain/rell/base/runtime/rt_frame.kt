/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_BaseExpr
import net.postchain.rell.base.utils.*
import java.util.*

internal class Rt_CallFrame(
    val defCtx: Rt_DefinitionContext,
    private val rFrame: R_CallFrame,
    state: Rt_CallFrameState?,
) {
    val exeCtx = defCtx.exeCtx
    val sqlCtx = defCtx.sqlCtx
    val sysSqlExec = exeCtx.sysSqlExec
    val userSqlExec = exeCtx.userSqlExec
    val appCtx = exeCtx.appCtx

    private var curBlock = rFrame.rootBlock
    private val values = Array<Rt_Value?>(rFrame.size) { null }

    private var beforeGuardBlock = rFrame.hasGuardBlock

    init {
        if (state != null) {
            check(rFrame.size >= state.values.size)
            for (i in 0 until state.values.size) {
                values[i] = state.values[i].orElse(null)
            }
        }
    }

    fun <T> block(block: R_FrameBlock, code: () -> T): T {
        val oldBlock = curBlock
        check(block.parentUid == oldBlock.uid) { "expected current block ${block.parentUid}, was ${oldBlock.uid}" }
        check(block.offset + block.size <= values.size)

        for (i in 0 until block.size) {
            check(values[block.offset + i] == null)
        }

        curBlock = block
        try {
            val res = code()
            return res
        } finally {
            curBlock = oldBlock
            for (i in 0 until block.size) {
                values[block.offset + i] = null
            }
        }
    }

    fun <T> blockOpt(block: R_FrameBlock?, code: () -> T): T {
        return if (block == null) {
            code()
        } else {
            this.block(block, code)
        }
    }

    fun set(ptr: R_VarPtr, varType: R_Type, value: Rt_Value, overwrite: Boolean) {
        R_BaseExpr.typeCheck(this, varType, value)
        val offset = checkPtr(ptr)
        if (!overwrite) {
            check(values[offset] == null)
        }
        values[offset] = value
    }

    fun get(ptr: R_VarPtr): Rt_Value {
        val value = getOpt(ptr)
        check(value != null) { "Variable not initialized: $ptr" }
        return value
    }

    fun getOpt(ptr: R_VarPtr): Rt_Value? {
        val offset = checkPtr(ptr)
        val value = values[offset]
        return value
    }

    private fun checkPtr(ptr: R_VarPtr): Int {
        val block = curBlock
        check(ptr.blockUid == block.uid) { "wrong var block: var_ptr = $ptr, cur_block = ${block.uid}" }
        val offset = ptr.offset
        check(offset >= 0)
        check(offset < block.offset + block.size)
        return offset
    }

    fun callCtx() = Rt_CallContext(defCtx, dbUpdateAllowed())

    fun dumpState(): Rt_CallFrameState {
        checkEquals(curBlock.uid, rFrame.rootBlock.uid)
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

    fun error(pos: R_ErrorPos, code: String, msg: String): Nothing {
        val err = Rt_CommonError(code, msg)
        val filePos = R_FilePos(pos.file, pos.line)
        val fullStack = immListOf(R_StackPos(defCtx.defId, filePos))
        val info = Rt_ExceptionInfo(extraMessage = null, stack = fullStack)
        throw Rt_Exception(err, info)
    }

    fun error(pos: R_ErrorPos, e: Rt_Exception, nested: Boolean = false): Nothing {
        val stackPos = R_StackPos(defCtx.defId, R_FilePos(pos.file, pos.line))
        val fullStack = if (nested || e.info.stack.isEmpty()) (e.info.stack + stackPos) else e.info.stack
        val info = Rt_ExceptionInfo(extraMessage = e.info.extraMessage, stack = fullStack)
        throw Rt_Exception(e.err, info, e)
    }

    companion object {
        fun createInitFrame(
            exeCtx: Rt_ExecutionContext,
            rDef: R_Definition,
            modsAllowed: Boolean,
        ): Rt_CallFrame {
            return createInitFrame(exeCtx, rDef.defId, rDef.initFrameGetter, modsAllowed)
        }

        fun createInitFrame(
            exeCtx: Rt_ExecutionContext,
            defId: R_DefinitionId,
            initFrameGetter: C_LateGetter<R_CallFrame>,
            modsAllowed: Boolean,
        ): Rt_CallFrame {
            val defCtx = Rt_DefinitionContext(exeCtx, modsAllowed, defId)
            val rInitFrame = initFrameGetter.get()
            return rInitFrame.createRtFrame(defCtx, null)
        }
    }
}

internal class Rt_CallFrameState(val values: ImmList<Optional<Rt_Value>>) {
    companion object {
        val EMPTY = Rt_CallFrameState(immListOf())
    }
}
