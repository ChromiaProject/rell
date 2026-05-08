/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger

@JvmRecord
data class Rt_EntityValue(override val type: Rt_ValueClass<*>, val rowid: Long): Rt_Value {
    override val name
        get() = Companion.name

    override fun strCode(showTupleFieldNames: Boolean) = "${type.name}[$rowid]"
    override fun str(format: Rt_StrFormat) = strCode()

    companion object: Rt_ValueClass<Rt_EntityValue> {
        override val name
            get() = "entity"

        override val klass = Rt_EntityValue::class

        fun gtvConversion(
            rtType: Lazy<Rt_ValueClass<*>>,
            typeName: String,
            track: (GtvToRtContext, Long) -> Unit,
        ): Rt_GtvCompatibleValueClass<*> = object: Rt_UntypedGtvConversion(typeName) {
            override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv = GtvInteger((value as Rt_EntityValue).rowid)
            override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                val rowid = GtvRtUtils.gtvToInteger(ctx, gtv, typeName)
                track(ctx, rowid)
                return Rt_EntityValue(rtType.value, rowid)
            }

            override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                val rowid = GtvRtUtils.gtvToInteger(ctx, gtv, typeName)
                return ctx.rtValue {
                    track(ctx, rowid)
                    Rt_EntityValue(rtType.value, rowid)
                }
            }
        }
    }
}
