/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvRtConversion_None
import org.jooq.SQLDialect
import org.jooq.impl.DefaultDataType

object Lib_Type_Signer {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("signer", rType = R_SignerType)
    }
}

object R_SignerType: R_PrimitiveType("signer") {
    private val GTX_SIGNER_SQL_DATA_TYPE = DefaultDataType(null as SQLDialect?, ByteArray::class.java, "gtx_signer")

    //TODO support Gtv
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_None
    override fun getLibTypeDef() = Lib_Rell.SIGNER_TYPE
    //TODO sqlType = GTX_SIGNER_SQL_DATA_TYPE
}
