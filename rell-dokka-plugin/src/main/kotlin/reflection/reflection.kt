/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.reflection

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.*
import net.postchain.rell.base.model.expr.R_Expr
import net.postchain.rell.base.model.expr.R_RRConstantValueExpr
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.rrConstantToRtValue
import net.postchain.rell.base.runtime.rtValueToGtv

fun R_Expr.getValue(): Rt_Value? = when (this) {
    is R_RRConstantValueExpr -> rrConstantToRtValue(rrValue)
    else -> null
}

fun R_RoutineDefinition.getType(): R_Type = when (this) {
    is R_OperationDefinition -> R_UnitType
    is R_QueryDefinition -> type()
    is R_FunctionDefinition -> fnBase.getHeader().type
}

fun R_GlobalConstantDefinition.getValueGtv(): Gtv? {
    val body = bodyGetter.get()
    val rrValue = body.value ?: return null
    val type = body.type
    if (!type.completeFlags().gtv.toGtv) return null
    val rtValue = rrConstantToRtValue(rrValue) ?: return null
    return rtValueToGtv(type, rtValue, pretty = true)
}
