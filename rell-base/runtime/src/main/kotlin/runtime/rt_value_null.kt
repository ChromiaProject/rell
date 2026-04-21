/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

object Rt_NullValue: Rt_Value() {
    override val valueType = Rt_CoreValueTypes.NULL.type()

    override fun type() = Rt_PrimitiveTypes.NULL
    override fun strCode(showTupleFieldNames: Boolean) = "null"
    override fun str(format: StrFormat) = "null"
}
