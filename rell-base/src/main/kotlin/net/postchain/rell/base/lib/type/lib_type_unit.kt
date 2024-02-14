/*
 * Copyright (C) 2023 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None
import net.postchain.rell.base.runtime.Rt_CoreValueTypes
import net.postchain.rell.base.runtime.Rt_Value

object Lib_Type_Unit {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("unit", rType = R_UnitType) {
            constructor(pure = true) {
                body { ->
                    Rt_UnitValue
                }
            }
        }
    }
}

object R_UnitType: R_PrimitiveType("unit") {
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_Rell.UNIT_TYPE
}

object Rt_UnitValue: Rt_Value() {
    override val valueType = Rt_CoreValueTypes.UNIT.type()

    override fun type() = R_UnitType
    override fun strCode(showTupleFieldNames: Boolean) = "unit"
    override fun str(format: StrFormat) = "unit"
}
