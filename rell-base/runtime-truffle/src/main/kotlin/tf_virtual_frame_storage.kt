/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.frame.VirtualFrame
import net.postchain.rell.base.runtime.Rt_BooleanValue
import net.postchain.rell.base.runtime.Rt_FrameStorage
import net.postchain.rell.base.runtime.Rt_IntValue
import net.postchain.rell.base.runtime.Rt_Value

/**
 * [Rt_FrameStorage] backed by a Truffle [VirtualFrame].
 *
 * The Truffle backend wraps each callee's [VirtualFrame] in one of these and points the
 * callee's [net.postchain.rell.base.runtime.Rt_CallFrame] at it for the duration of the body.
 * Slow-path interpreter calls (`Rt_InterpreterImpl.assignTo`, `initializeDeclarator`,
 * `evaluateExpr`, `executeStmt` reached through fallback nodes) read and write through this
 * adapter, hitting the same indexed-slot store the hot-path Truffle nodes touch directly. There
 * is no parallel `Rt_CallFrame.values[]` array on this path — the legacy mirror is gone.
 *
 * # Slot semantics
 *
 * - `get(offset)` reads through the descriptor's per-slot kind so a typed `Long` / `Boolean` slot
 *   is unboxed back to a [Rt_IntValue] / [Rt_BooleanValue] for slow-path callers, and an
 *   `Object`-kind slot returns `null` when uninitialised or cleared.
 * - `set(offset, value)` dispatches on the descriptor's slot kind — typed slots receive
 *   [VirtualFrame.setLong] / [VirtualFrame.setBoolean] after unboxing the [Rt_Value]; `Object`
 *   slots take [VirtualFrame.setObject] as before.
 * - `clear(offset)` calls [VirtualFrame.clear] which flips the slot tag to `Illegal`; subsequent
 *   `get` returns `null` regardless of the slot's static kind (the tag check rejects the
 *   typed-getter call before it can throw).
 */
internal class Tf_VirtualFrameStorage(@JvmField val virtualFrame: VirtualFrame) : Rt_FrameStorage {
    /**
     * Cached per-slot kind table, derived from `frameDescriptor.info`'s [Tf_FrameInfo].
     * Read on every `get` / `set` to dispatch typed accessors when the FrameDescriptor reserved
     * a `Long` / `Boolean` slot for the offset. Empty array when no typed slots exist or the
     * descriptor lacks a [Tf_FrameInfo] (e.g. driver-test scaffolding).
     */
    @JvmField
    val slotKinds: ByteArray = (virtualFrame.frameDescriptor.info as? Tf_FrameInfo)?.slotKinds
        ?: ByteArray(0)

    override fun get(offset: Int): Rt_Value? = when (slotKindAt(offset)) {
        TF_SLOT_KIND_LONG ->
            // Tag check — `clear(offset)` flips the slot to `Illegal`, in which case `isLong`
            // returns false and we fall through to the null branch below. Avoids
            // `FrameSlotTypeException` from a typed-getter on a freshly-cleared slot.
            if (virtualFrame.isLong(offset)) Rt_IntValue.get(virtualFrame.getLong(offset)) else null

        TF_SLOT_KIND_BOOLEAN ->
            if (virtualFrame.isBoolean(offset)) Rt_BooleanValue.get(virtualFrame.getBoolean(offset)) else null

        else ->
            if (virtualFrame.isObject(offset)) Tf_Unchecked.cast(virtualFrame.getObject(offset)) else null
    }

    override fun set(offset: Int, value: Rt_Value) = when (slotKindAt(offset)) {
        TF_SLOT_KIND_LONG -> virtualFrame.setLong(offset, Tf_Unchecked.cast<Rt_IntValue>(value).value)
        TF_SLOT_KIND_BOOLEAN -> virtualFrame.setBoolean(offset, Tf_Unchecked.cast<Rt_BooleanValue>(value).value)
        else -> virtualFrame.setObject(offset, value)
    }

    override fun clear(offset: Int) = virtualFrame.clear(offset)

    private fun slotKindAt(offset: Int): Byte =
        if (offset < slotKinds.size) slotKinds[offset] else TF_SLOT_KIND_OBJECT
}
