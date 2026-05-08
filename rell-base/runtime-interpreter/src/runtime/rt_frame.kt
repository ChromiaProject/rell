/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.ErrorPos
import net.postchain.rell.base.model.FilePos
import net.postchain.rell.base.model.R_StackPos
import net.postchain.rell.base.model.rr.RR_FrameBlock
import net.postchain.rell.base.model.rr.RR_FrameDescriptor
import net.postchain.rell.base.model.rr.RR_VarPtr
import net.postchain.rell.base.utils.*
import java.util.*

/**
 * Outcome of statement execution in a tree-walker frame.
 *
 * Public so a peer backend (e.g. `runtime-truffle`) can build directly on top of [Rt_CallFrame]
 * and reuse the interpreter's statement-result protocol when delegating to fallback evaluation
 * for sub-trees it has not yet translated to native nodes.
 */
sealed interface Rt_StatementResult {
    data class Return(val value: Rt_Value?): Rt_StatementResult
    data object Break: Rt_StatementResult
    data object Continue: Rt_StatementResult
}

/**
 * Tree-walker activation record: a pluggable [Rt_FrameStorage] (defaulting to a heap-backed
 * `Array<Rt_Value?>`) plus block-scope bookkeeping. Implements the core-side [Rt_Frame] marker so
 * stdlib helpers can report errors against the current frame without a compile-time dependency on
 * this class.
 */
class Rt_CallFrame(
    val defCtx: Rt_DefinitionContext,
    val rrFrame: RR_FrameDescriptor,
    state: Rt_CallFrameState?,
    /**
     * Pluggable slot storage. Default is [Rt_HeapFrameStorage] (an `Array<Rt_Value?>`) for the
     * tree-walker.
     */
    @JvmField var storage: Rt_FrameStorage = Rt_HeapFrameStorage(rrFrame.size),
): Rt_Frame {
    val exeCtx = defCtx.exeCtx
    val sqlCtx = defCtx.sqlCtx
    val userSqlExec = exeCtx.userSqlExec
    val appCtx = exeCtx.appCtx

    @JvmField var curBlockUid = rrFrame.rootBlock.uid
    @JvmField var curBlockOffset = rrFrame.rootBlock.offset
    @JvmField var curBlockSize = rrFrame.rootBlock.size

    /**
     * Total slot count (= [rrFrame].size).
     */
    val size: Int
        get() = rrFrame.size

    private var beforeGuardBlock = rrFrame.hasGuardBlock

    init {
        if (state != null) {
            check(rrFrame.size >= state.values.size)
            for ((i, element) in state.values.withIndex()) {
                val v = element.orElse(null)
                if (v != null) this.storage[i] = v
            }
        }
    }

    inline fun <T> block(block: RR_FrameBlock, code: () -> T): T {
        val oldUid = curBlockUid
        val oldOffset = curBlockOffset
        val oldSize = curBlockSize
        check(block.parentUid == oldUid) { "expected current block ${block.parentUid}, was $oldUid" }
        check(block.offset + block.size <= rrFrame.size)

        for (i in 0 until block.size) {
            checkNull(storage[block.offset + i])
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
                storage.clear(block.offset + i)
            }
        }
    }

    inline fun <T> blockOpt(block: RR_FrameBlock?, code: () -> T): T = if (block == null) {
        code()
    } else {
        this.block(block, code)
    }

    fun enterBlockSet(blockUid: Long, blockOffset: Int, blockSize: Int) {
        curBlockUid = blockUid
        curBlockOffset = blockOffset
        curBlockSize = blockSize
    }

    fun clearSlotsRange(base: Int, size: Int) {
        for (i in 0 until size) storage.clear(base + i)
    }

    fun setUnchecked(ptr: RR_VarPtr, value: Rt_Value, overwrite: Boolean) {
        val offset = checkPtr(ptr.blockUid, ptr.offset)
        if (!overwrite) {
            checkNull(storage[offset]) { "Variable already initialized: $ptr" }
        }
        storage[offset] = value
    }

    fun setUncheckedAt(offset: Int, value: Rt_Value) {
        storage[offset] = value
    }

    fun get(ptr: RR_VarPtr): Rt_Value {
        val offset = checkPtr(ptr.blockUid, ptr.offset)
        return checkNotNull(storage[offset]) { "Variable not initialized: $ptr" }
    }

    fun getUncheckedAt(offset: Int): Rt_Value? = storage[offset]

    private fun checkPtr(blockUid: Long, ptrOffset: Int): Int {
        check(blockUid == curBlockUid) { "wrong var block: var_ptr = Block[$blockUid]/Var[$ptrOffset], cur_block = Block[$curBlockUid]" }
        check(ptrOffset >= 0)
        check(ptrOffset < curBlockOffset + curBlockSize)
        return ptrOffset
    }

    fun callCtx(): Rt_CallContext = defCtx.toCallContext()

    fun snapshot(): Rt_CallFrame {
        val heap = Rt_HeapFrameStorage(rrFrame.size)
        val src = storage
        for (i in 0 until rrFrame.size) {
            val v = src[i]
            if (v != null) heap[i] = v
        }
        val copy = Rt_CallFrame(defCtx, rrFrame, null, heap)
        copy.enterBlockSet(curBlockUid, curBlockOffset, curBlockSize)
        return copy
    }

    /**
     * Like [snapshot] but only if the current storage might not survive past this call —
     * i.e. anything other than [Rt_HeapFrameStorage]. Heap-backed frames need no snapshot
     * since they own their slot array; non-heap frames wrap an external store that the caller may release.
     *
     * Returns `this` when no copy is required.
     */
    fun snapshotIfEphemeral(): Rt_CallFrame =
        if (storage is Rt_HeapFrameStorage) this else snapshot()

    fun dumpState(): Rt_CallFrameState {
        checkEquals(curBlockUid, rrFrame.rootBlock.uid)
        val valuesList = (0 until rrFrame.size).mapToImmList { Optional.ofNullable(storage[it]) }
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

    override fun error(pos: ErrorPos, code: String, msg: String): Nothing {
        val err = Rt_CommonError(code, msg)
        val filePos = FilePos(pos.file, pos.line)
        val fullStack = immListOf(R_StackPos(defCtx.defId, filePos))
        val info = Rt_ExceptionInfo(extraMessage = null, stack = fullStack)
        throw Rt_Exception(err, info)
    }

    /**
     * Attach a source position to [e] and rethrow the same exception instance. Mutates
     * `e.info` in place rather than allocating a fresh `Rt_Exception`; per-`new Throwable`
     * `fillInStackTrace` was the dominant catch-path cost in interpreter-heavy workloads.
     */
    fun error(pos: ErrorPos, e: Rt_Exception, nested: Boolean = false): Nothing {
        val stackPos = R_StackPos(defCtx.defId, FilePos(pos.file, pos.line))
        e.attachStackPos(stackPos, nested)
        throw e
    }
}

@JvmInline value class Rt_CallFrameState(val values: ImmList<Optional<Rt_Value>>) {
    companion object {
        val EMPTY = Rt_CallFrameState(immListOf())
    }
}
