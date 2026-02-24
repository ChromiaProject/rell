/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.compiler.base.utils.C_LateGetter
import net.postchain.rell.base.runtime.Rt_CallFrame
import net.postchain.rell.base.runtime.Rt_CallFrameState
import net.postchain.rell.base.runtime.Rt_DefinitionContext
import net.postchain.rell.base.runtime.Rt_Value

internal data class R_VarPtr(
    val blockUid: R_FrameBlockUid,
    val offset: Int,
) {
    override fun toString() = "$blockUid/Var[$offset]"
}

internal class R_FrameBlock(
    val parentUid: R_FrameBlockUid?,
    val uid: R_FrameBlockUid,
    val offset: Int,
    val size: Int,
)

internal class R_CallFrame(
    val defId: R_DefinitionId,
    val size: Int,
    val rootBlock: R_FrameBlock,
    val hasGuardBlock: Boolean,
) {
    fun createRtFrame(defCtx: Rt_DefinitionContext, state: Rt_CallFrameState?): Rt_CallFrame {
        return Rt_CallFrame(defCtx, this, state)
    }

    companion object {
        private val ERROR_BLOCK = R_FrameBlock(null, R_Utils.ERROR_BLOCK_UID, -1, -1)
        val ERROR = R_CallFrame(R_DefinitionId.ERROR, 0, ERROR_BLOCK, false)

        val NONE_INIT_FRAME_GETTER = C_LateGetter.const(ERROR)
    }
}

internal class R_LambdaBlock(
    private val block: R_FrameBlock,
    private val varPtr: R_VarPtr,
    private val varType: R_Type,
) {
    fun <T> execute(frame: Rt_CallFrame, value: Rt_Value, body: () -> T): T {
        val res = frame.block(block) {
            frame.set(varPtr, varType, value, false)
            body()
        }
        return res
    }
}
