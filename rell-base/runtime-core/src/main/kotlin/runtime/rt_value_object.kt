/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

class Rt_ObjectValue(override val type: Rt_ValueClass<*>): Rt_Value {
    val typeName: String get() = type.name

    override val name
        get() = Companion.name

    override fun strCode(showTupleFieldNames: Boolean) = typeName
    override fun str(format: Rt_StrFormat) = typeName

    companion object: Rt_ValueClass<Rt_ObjectValue> {
        override val name
            get() = "object"
    }
}
