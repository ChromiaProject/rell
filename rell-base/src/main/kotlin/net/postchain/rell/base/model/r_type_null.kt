/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.lib.C_LibUtils
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvToRtContext
import net.postchain.rell.base.runtime.Rt_CoreValueTypes
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocType

object R_NullType: R_UniqueType("null", C_LibUtils.defName("null")) {
    override fun defaultValue() = Rt_NullValue
    override fun comparator() = Rt_Comparator.create { 0 }
    override fun calcCommonType(other: R_Type): R_Type = R_NullableType(other)
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Null
    override fun getLibType0() = C_LibType.make(M_Types.NULL)
    override fun docType() = DocType.NULL
}

object Rt_NullValue: Rt_Value() {
    override val valueType = Rt_CoreValueTypes.NULL.type()

    override fun type() = R_NullType
    override fun strCode(showTupleFieldNames: Boolean) = "null"
    override fun str(format: StrFormat) = "null"
}

object GtvRtConversion_Null: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        checkEquals(rt, Rt_NullValue)
        return GtvNull
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        check(gtv.isNull())
        return Rt_NullValue
    }
}
