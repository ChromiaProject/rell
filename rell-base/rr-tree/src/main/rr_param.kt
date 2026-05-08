/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model.rr

import net.postchain.rell.base.model.Name

/**
 * Function/operation/query parameter (e.g. `x: integer = 5` in `function f(x: integer = 5)`).
 * Carries the parameter name, type, optional default expression, and optional size constraint.
 */
@JvmRecord
data class RR_FunctionParam(
    val name: Name,
    val type: RR_Type,
    val initFrame: RR_FrameDescriptor,
    val defaultExpr: RR_Expr?,
    val sizeConstraint: RR_SizeConstraint?,
)
