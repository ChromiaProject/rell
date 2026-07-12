/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime.truffle.nodes

import com.oracle.truffle.api.frame.VirtualFrame
import net.postchain.rell.base.runtime.Rt_UnitValue
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.truffle.TF_VALUE_BLOCK_RESULT_AUX_SLOT
import net.postchain.rell.base.runtime.truffle.Tf_Unchecked

/**
 * A value block `{ stmt; ...; result }` used as an if/when expression arm. [body] is a
 * [Tf_BlockStmtNode] over the block's statements plus (when there is a trailing expression) a
 * final [Tf_ValueBlockResultStmtNode] that evaluates the result inside the block's scope and
 * parks it in [TF_VALUE_BLOCK_RESULT_AUX_SLOT]; the slot is read back immediately after the body
 * completes, so nesting and recursion cannot interleave two uses in one frame. A `return`/
 * `break`/`continue` inside the block propagates as its control-flow exception (see
 * `tf_control_flow.kt`) to the enclosing loop or function-body root.
 */
internal class Tf_ValueBlockExprNode(
    @field:Child private var body: Tf_ExprNode,
    private val hasResult: Boolean,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        body.executeStmt(frame)
        if (!hasResult) return Rt_UnitValue
        return Tf_Unchecked.cast(frame.getAuxiliarySlot(TF_VALUE_BLOCK_RESULT_AUX_SLOT) ?: Rt_UnitValue)
    }
}

/**
 * The trailing expression of a value block, executed as the block's last statement-shaped child
 * so it runs inside the block's scope (its locals are visible). The value is parked in
 * [TF_VALUE_BLOCK_RESULT_AUX_SLOT] for the enclosing [Tf_ValueBlockExprNode] to pick up.
 */
internal class Tf_ValueBlockResultStmtNode(
    @field:Child private var expr: Tf_ExprNode,
): Tf_ExprNode() {
    override fun execute(frame: VirtualFrame): Rt_Value {
        executeStmt(frame)
        return Rt_UnitValue
    }

    override fun executeStmt(frame: VirtualFrame) {
        frame.setAuxiliarySlot(TF_VALUE_BLOCK_RESULT_AUX_SLOT, expr.execute(frame))
    }
}
