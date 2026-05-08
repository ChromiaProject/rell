/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

/** Serializable variable slot reference. Interpreter maps to actual offset. */
@JvmRecord
data class RR_VarPtr(
    val blockUid: Long,
    val offset: Int,
) {
    override fun toString() = "Block[$blockUid]/Var[$offset]"
}

/** Serializable frame block descriptor. */
@JvmRecord
data class RR_FrameBlock(
    val parentUid: Long?,
    val uid: Long,
    val offset: Int,
    val size: Int,
)

/** Serializable frame layout descriptor for a function/query/operation body. */
@JvmRecord
data class RR_FrameDescriptor(
    val size: Int,
    val rootBlock: RR_FrameBlock,
    val hasGuardBlock: Boolean,
)
