/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.model.rr.RR_Type

object Rt_NullValue: Rt_ValueBase(), Rt_ValueClass<Rt_NullValue> {
    override val name
        get() = NULL_NAME

    override val klass = Rt_NullValue::class
    override val rrType: RR_Type = RR_Type.Null
    override val comparator: Comparator<Rt_Value> = Comparator { _, _ -> 0 }

    override val type
        get() = this

    override fun strCode(showTupleFieldNames: Boolean) = "null"
    override fun str(format: Rt_StrFormat) = "null"

    private const val NULL_NAME = "null"

    /** Conversion for the singular `null` type — accepts any null-typed Gtv, encodes as [GtvNull]. */
    override val gtvConversion: Rt_GtvCompatibleValueClass<*> = object: Rt_UntypedGtvConversion(NULL_NAME) {
        override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv = GtvNull
        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
            if (!gtv.isNull()) {
                throw GtvRtUtils.errGtv(
                    ctx,
                    "null:expected_null:${gtv.type}",
                    "Expected null Gtv, got: ${gtv.type}",
                )
            }
            return Rt_NullValue
        }
    }

    /** Wraps an inner conversion to also accept Gtv null and decode to/encode from [Rt_NullValue]. */
    fun gtvConversionNullable(valueConversion: Lazy<Rt_GtvCompatibleValueClass<*>>): Rt_GtvCompatibleValueClass<*> {
        val inner by valueConversion
        return object: Rt_UntypedGtvConversion(NULL_NAME) {
            override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv =
                if (value === Rt_NullValue) GtvNull else inner.rtToGtv(value, pretty)

            override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value =
                if (gtv.isNull()) Rt_NullValue else inner.gtvToRt(ctx, gtv)
        }
    }
}
