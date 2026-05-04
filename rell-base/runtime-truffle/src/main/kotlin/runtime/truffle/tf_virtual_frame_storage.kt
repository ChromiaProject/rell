/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import com.oracle.truffle.api.frame.VirtualFrame
import net.postchain.rell.base.runtime.Rt_FrameStorage
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
 * - `get(offset)` returns `null` for any slot whose tag is **not** `Object` (i.e. uninitialised
 *   or cleared). The hot path uses [VirtualFrame.setObject] for every write, so an `Object` tag
 *   is the only "live" state.
 * - `set(offset, value)` always uses [VirtualFrame.setObject] — the FrameDescriptor reserves
 *   `Object`-kind slots, so primitive specialisations (`Long`/`Boolean`) handled by individual
 *   nodes never reach here.
 * - `clear(offset)` calls [VirtualFrame.clear] which flips the slot tag to `Illegal`; subsequent
 *   `get` returns `null`.
 */
internal class Tf_VirtualFrameStorage(@JvmField val virtualFrame: VirtualFrame) : Rt_FrameStorage {
    override fun get(offset: Int): Rt_Value? =
        if (virtualFrame.isObject(offset)) Tf_Unchecked.cast(virtualFrame.getObject(offset)) else null

    override fun set(offset: Int, value: Rt_Value) = virtualFrame.setObject(offset, value)
    override fun clear(offset: Int) = virtualFrame.clear(offset)
}
