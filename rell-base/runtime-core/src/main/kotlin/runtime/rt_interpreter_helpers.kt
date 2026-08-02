/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.AtCardinality
import net.postchain.rell.base.model.rr.RR_Type

val AtCardinality.isMany: Boolean
    get() = this == AtCardinality.ZERO_MANY || this == AtCardinality.ONE_MANY

fun RR_Type.elementType(): RR_Type = when (this) {
    is RR_Type.List -> element
    is RR_Type.Set -> element
    is RR_Type.Nullable -> value.elementType()
    else -> this
}

/** User-facing message for a failed not-null assertion (`!!` or an implicit null check). */
fun rtNullValueMsg(valueName: String?): String =
    if (valueName != null) "Value of '$valueName' is null" else "Null value"
