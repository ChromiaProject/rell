/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.model.rr.RR_FrameBlock
import net.postchain.rell.base.model.rr.RR_FrameDescriptor
import net.postchain.rell.base.model.rr.RR_VarPtr

data class R_VarPtr(
    val blockUid: R_FrameBlockUid,
    val offset: Int,
) {
    override fun toString() = "$blockUid/Var[$offset]"
}

class R_FrameBlock(
    val parentUid: R_FrameBlockUid?,
    val uid: R_FrameBlockUid,
    val offset: Int,
    val size: Int,
)

class R_CallFrame(
    val defId: DefinitionId,
    val size: Int,
    val rootBlock: R_FrameBlock,
    val hasGuardBlock: Boolean,
) {
    companion object {
        private val ERROR_BLOCK = R_FrameBlock(null, R_Utils.ERROR_BLOCK_UID, -1, -1)
        val ERROR = R_CallFrame(DefinitionId.ERROR, 0, ERROR_BLOCK, false)

        val NONE_INIT_FRAME_GETTER = C_LateGetter.const(ERROR)
    }
}

class R_LambdaBlock(
    val block: R_FrameBlock,
    val varPtr: R_VarPtr,
)

fun R_VarPtr.toRR() = RR_VarPtr(blockUid.id, offset)
fun R_FrameBlock.toRR() = RR_FrameBlock(parentUid?.id, uid.id, offset, size)
fun R_CallFrame.toRR() = RR_FrameDescriptor(size, rootBlock.toRR(), hasGuardBlock)
