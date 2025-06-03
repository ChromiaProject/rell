/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None

object Lib_Type_Guid {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("guid", rType = R_GUIDType, since = "0.6.0")
    }
}

object R_GUIDType: R_PrimitiveType("guid") {
    //TODO support Gtv
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_Rell.GUID_TYPE
    //TODO sqlType = PostgresDataType.BYTEA
}
