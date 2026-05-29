/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle

import net.postchain.rell.base.model.DefinitionId
import net.postchain.rell.base.model.rr.RR_FrameDescriptor

/**
 * Index of the auxiliary slot on every Truffle [com.oracle.truffle.api.frame.FrameDescriptor]
 * built by [Tf_Translator.buildFrameDescriptor] that stores the lazy-cached
 * [net.postchain.rell.base.runtime.Rt_CallFrame] view.
 *
 * One aux slot per FrameDescriptor; index `0` since each function/operation/query body has
 * its own descriptor and we need only one slot for the cache.
 *
 * See [net.postchain.rell.base.runtime.truffle.nodes.tfRtFrame] for the lookup/lazy-alloc
 * protocol.
 */
internal const val TF_RT_FRAME_AUX_SLOT: Int = 0

/**
 * Identity key passed to [com.oracle.truffle.api.frame.FrameDescriptor.findOrAddAuxiliarySlot]
 * to register the [TF_RT_FRAME_AUX_SLOT] slot. The key value itself doesn't matter — we use a
 * sentinel string so it shows up as the slot name in any Truffle inspection output.
 */
internal val TF_RT_FRAME_AUX_KEY: Any = "tf.rt-frame"

/**
 * Index of the auxiliary slot that holds the body's return value when a statement node
 * returns [STATUS_RETURN]. Allocated on every Truffle [com.oracle.truffle.api.frame.FrameDescriptor]
 * built by [Tf_Translator.buildFrameDescriptor]; the function-body root and the user-fn
 * caller read from this slot after the body executes.
 *
 * Slot `1` (right after [TF_RT_FRAME_AUX_SLOT]). The value is either a [net.postchain.rell.base.runtime.Rt_Value]
 * or `null` (for parameterless `return;`). Slot is never read unless the most recent
 * `executeStmt` returned [STATUS_RETURN], so cross-call leakage is impossible.
 */
internal const val TF_RETURN_VALUE_AUX_SLOT: Int = 1

/** Sentinel key for [TF_RETURN_VALUE_AUX_SLOT]. See [TF_RT_FRAME_AUX_KEY]. */
internal val TF_RETURN_VALUE_AUX_KEY: Any = "tf.return-value"

/**
 * Slot-kind encoding for typed [com.oracle.truffle.api.frame.FrameSlotKind] specialisation.
 *
 * Stored as a `byte` per slot on [Tf_FrameInfo.slotKinds] (one byte per slot offset). The
 * translator fills the table at descriptor-build time by walking the body's
 * [net.postchain.rell.base.model.rr.RR_Statement.Var] declarators and the function's parameter vars;
 * each slot is classified once and the kind is pinned for the descriptor's lifetime.
 *
 * Nullable primitive locals (`integer?`, `boolean?`) MUST stay [OBJECT] because `null` is not
 * representable in a `long`/`boolean` slot — the slot-collector explicitly skips
 * [net.postchain.rell.base.model.rr.RR_Type.Nullable] wrappers for that reason.
 */
internal const val TF_SLOT_KIND_OBJECT: Byte = 0
internal const val TF_SLOT_KIND_LONG: Byte = 1
internal const val TF_SLOT_KIND_BOOLEAN: Byte = 2

/**
 * Per-body metadata attached to each Truffle [com.oracle.truffle.api.frame.FrameDescriptor] via
 * `FrameDescriptor.Builder.info(...)`. Reachable on the hot path through
 * `frame.frameDescriptor.info` so [net.postchain.rell.base.runtime.truffle.nodes.tfLazyAllocRtFrame]
 * can build a lazy [net.postchain.rell.base.runtime.Rt_CallFrame] without holding a back-pointer
 * to the [com.oracle.truffle.api.nodes.RootNode].
 *
 * - [defId]: the definition id of the body's enclosing function/op/query — used to construct
 *   the lazy [net.postchain.rell.base.runtime.Rt_DefinitionContext].
 * - [rrFrame]: the body's frame layout — passed into the lazy [net.postchain.rell.base.runtime.Rt_CallFrame]
 *   constructor so
 *   block-state asserts and `values[]` sizing match the tree-walker.
 * - [slotKinds]: per-slot specialisation (one of the `TF_SLOT_KIND_*` constants). Indexed by
 *   `RR_VarPtr.offset`. Read by [net.postchain.rell.base.runtime.truffle.Tf_VirtualFrameStorage]
 *   to dispatch typed `setLong`/`setBoolean` writes from slow-path callers
 *   (`initializeDeclarator`, `assignTo`) so they land in the correct typed slot when the
 *   FrameDescriptor reserves one.
 *
 * Carried as a single object so the hot-path read at lazy-alloc time is one
 * `frameDescriptor.info` field load + one cast (rather than three separate accessors / fields).
 * Treating it as a normal Kotlin class (not `data class`) avoids generating `equals`/`hashCode`
 * for an object that's never compared.
 */
internal class Tf_FrameInfo(
    @JvmField val defId: DefinitionId,
    @JvmField val rrFrame: RR_FrameDescriptor,
    @JvmField val slotKinds: ByteArray,
)
