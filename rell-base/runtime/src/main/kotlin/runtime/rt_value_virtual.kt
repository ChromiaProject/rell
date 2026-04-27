/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvVirtual

sealed class Rt_VirtualValue(val gtv: Gtv): Rt_ValueBase() {
    fun toFull(): Rt_Value {
        if (gtv is GtvVirtual) {
            val typeStr = type.name
            throw Rt_Exception.common("virtual:to_full:notfull:$typeStr", "Value of type $typeStr is not full")
        }

        return toFull0()
    }

    protected abstract fun toFull0(): Rt_Value

    companion object: Rt_ValueClass<Rt_VirtualValue> {
        override val name
            get() = "virtual"

        override val klass = Rt_VirtualValue::class

        fun toFull(v: Rt_Value): Rt_Value = if (v is Rt_VirtualValue) v.toFull() else v
    }
}
