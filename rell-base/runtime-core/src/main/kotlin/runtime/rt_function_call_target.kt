/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_FunctionCallTarget

/**
 * Captured RR function call target plus the closure frame, used by [Rt_FunctionValue]
 * for partial application — invoking the value later dispatches through
 * [Rt_Interpreter.callTarget] with [outerFrame].
 */
class Rt_FunctionCallTarget(
    val interpreter: Rt_Interpreter,
    val rrTarget: RR_FunctionCallTarget,
    val outerFrame: Rt_Frame,
)
