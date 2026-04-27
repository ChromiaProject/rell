/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

class Rt_ObjectValue(val typeName: String): Rt_ValueBase() {
    override val name
        get() = Companion.name

    override val type
        get() = Companion

    override fun strCode(showTupleFieldNames: Boolean) = typeName
    override fun str(format: Rt_StrFormat) = typeName

    companion object: Rt_ValueClass<Rt_ObjectValue> {
        override val name
            get() = "object"

        override val klass = Rt_ObjectValue::class
    }
}
