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
 */
class Rt_RR_EnumValue(
    private val rtTypeRef: Lazy<Rt_ValueClass<*>>,
    internal val rrAttr: RR_EnumAttr,
): Rt_ValueBase() {
    override val name
        get() = Companion.name

    override val type: Rt_ValueClass<*>
        get() = rtTypeRef.value

    override fun strCode(showTupleFieldNames: Boolean) = "${rtTypeRef.value.name}[${rrAttr.name}]"
    override fun str(format: Rt_StrFormat) = rrAttr.name

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Rt_Value) return false
        // Compare by enum type name + value index for cross-compatibility.
        val otherAttr = try {
            other.asEnum()
        } catch (_: Rt_Exception) {
            return false
        }
        if (otherAttr.value != rrAttr.value) return false
        return other.type.name == rtTypeRef.value.name
    }

    override fun hashCode(): Int = rtTypeRef.value.name.hashCode() * 31 + rrAttr.value

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
