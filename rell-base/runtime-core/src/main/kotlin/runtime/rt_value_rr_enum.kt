/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.rr.RR_EnumAttr

/**
 * Enum value for the deserialized (RR_-only) path, where no `R_EnumType` is available.
 *
 * The previous implementation held a `Lazy<Rt_ValueClass<*>>` wrapper, which routed every
 * `.type` access through `SynchronizedLazyImpl.getValue` (~3% on `bench_locations` due to
 * the equals + strCode hot paths each touching `.type.name` per call). Instances are interned
 * per enum-attr (one Rt_RR_EnumValue per (enum, attr) at definition time), so the lazy
 * indirection added cost without buying anything; replaced with a direct field plus a
 * pre-cached `typeName` so `equals`/`hashCode` skip the `.type.name` lookup entirely.
 */
class Rt_RR_EnumValue(
    override val type: Rt_ValueClass<*>,
    internal val rrAttr: RR_EnumAttr,
): Rt_ValueBase() {
    private val typeName: String = type.name

    override val name
        get() = Companion.name

    override fun strCode(showTupleFieldNames: Boolean) = "$typeName[${rrAttr.name}]"
    override fun str(format: Rt_StrFormat) = rrAttr.name

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Rt_RR_EnumValue) return false
        if (other.rrAttr.value != rrAttr.value) return false
        return other.typeName == typeName
    }

    override fun hashCode(): Int = typeName.hashCode() * 31 + rrAttr.value

    companion object: Rt_ValueClass<Rt_RR_EnumValue> {
        override val name
            get() = "enum"

        override val klass = Rt_RR_EnumValue::class

        fun gtvConversion(enum: R_EnumDefinition): Rt_GtvCompatibleValueClass<*> = gtvConversion(
            typeName = enum.type.strCode(),
            rtByName = { n -> enum.attr(n)?.let { enum.rtGetValue(it) } },
            rtByValue = { v -> enum.attr(v)?.let { enum.rtGetValue(it) } },
        )

        /**
         * Generic factory: looks up enum values by name/integer via the supplied callbacks.
         * Used by both the R_-driven [Rt_RR_EnumValue.gtvConversion] (above) and the
         * pure-RR [Rt_Interpreter] enum type construction.
         */
        fun gtvConversion(
            typeName: String,
            rtByName: (String) -> Rt_Value?,
            rtByValue: (Long) -> Rt_Value?,
        ): Rt_GtvCompatibleValueClass<*> = object: Rt_UntypedGtvConversion(typeName) {
            override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
                val attr = (value as Rt_RR_EnumValue).rrAttr
                return if (pretty) GtvString(attr.name) else GtvInteger(attr.value.toLong())
            }

            override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                return if (ctx.pretty && gtv.type == GtvType.STRING) {
                    val n = GtvRtUtils.gtvToString(ctx, gtv, typeName)
                    rtByName(n) ?: throw GtvRtUtils.errGtvType(
                        ctx, typeName, "enum:bad_value:$n", "invalid value: '$n'",
                    )
                } else {
                    val v = GtvRtUtils.gtvToInteger(ctx, gtv, typeName)
                    rtByValue(v) ?: throw GtvRtUtils.errGtvType(
                        ctx, typeName, "enum:bad_value:$v", "invalid value: $v",
                    )
                }
            }
        }
    }
}
