/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.immSetOf
import kotlin.reflect.full.createType

object Rt_UnitValue: Rt_Value, Rt_ValueClass<Rt_UnitValue>, Rt_NativeAdapter {
    override val name
        get() = "unit"

    override val klass = Rt_UnitValue::class
    override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.UNIT)

    override val type
        get() = this

    override fun strCode(showTupleFieldNames: Boolean) = "unit"
    override fun str(format: Rt_StrFormat) = "unit"

    override val nativeTypes = immSetOf(Unit::class.createType(), Void::class.createType())
    override fun nativeToRt(value: Any?) = Rt_UnitValue
    override fun rtToNative(value: Rt_Value) = null
}
