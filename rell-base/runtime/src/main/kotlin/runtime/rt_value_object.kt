/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

class Rt_ObjectValue(private val rtType: Rt_Type): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.OBJECT.type()

    override fun type() = rtType
    override fun strCode(showTupleFieldNames: Boolean) = rtType.name
    override fun str(format: StrFormat) = rtType.name
}
