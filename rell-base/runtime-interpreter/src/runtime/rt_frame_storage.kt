/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

/**
 * Pluggable backing store for an [Rt_CallFrame]'s slot array.
 *
 * The tree-walker only ever reaches frame slots through this abstraction, so a peer backend
 * can supply its own implementation.
 *
 * # Contract
 *
 * Implementations are NOT thread-safe.
 *
 * Reads of unwritten / cleared slots return `null`. The block-uid validation logic in
 * [Rt_CallFrame] is layered on top: storage operates on raw slot offsets and never inspects
 * the block state.
 */
interface Rt_FrameStorage {
    /** Read raw slot at [offset]. Returns `null` if the slot was never written or has been cleared. */
    operator fun get(offset: Int): Rt_Value?

    /** Write raw slot at [offset]. */
    operator fun set(offset: Int, value: Rt_Value)

    /** Clear raw slot at [offset] back to its uninitialised state. */
    fun clear(offset: Int)
}

/**
 * Heap-backed storage: a plain `Array<Rt_Value?>` of fixed size, the tree-walker's canonical
 * representation.
 *
 * Created with size `rrFrame.size`. The tree-walker calls into [get] / [set] / [clear] through
 * the [Rt_FrameStorage] interface; the JIT inlines through the sealed implementation reliably
 * because there are only two final implementations in the entire runtime classpath.
 */
class Rt_HeapFrameStorage(size: Int) : Rt_FrameStorage {
    @PublishedApi
    internal val values: Array<Rt_Value?> = arrayOfNulls(size)

    override fun get(offset: Int): Rt_Value? = values[offset]
    override fun set(offset: Int, value: Rt_Value) {
        values[offset] = value
    }

    override fun clear(offset: Int) {
        values[offset] = null
    }
}
