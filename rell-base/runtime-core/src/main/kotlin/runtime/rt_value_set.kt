/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray

class Rt_SetValue(override val type: Rt_ValueClass<*>, internal val elements: MutableSet<Rt_Value>):
    Rt_ValueBase(), Rt_CollectionValue {

    override val collection: MutableCollection<Rt_Value> get() = elements
    override val name
        get() = Companion.name

    override fun equals(other: Any?) = other === this || (other is Rt_SetValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, elements, showTupleFieldNames)
    override fun str(format: Rt_StrFormat) = elements.joinToString(", ", "[", "]") { it.str(format) }

    override fun strPretty(indent: Int): String {
        if (elements.isEmpty()) {
            return str(Rt_StrFormat.V2)
        }
        val indentStr = "    ".repeat(indent)
        return elements.joinToString(",", "[", "\n$indentStr]") {
            val s = it.strPretty(indent + 1)
            "\n$indentStr    $s"
        }
    }

    companion object: Rt_ValueClass<Rt_SetValue> {
        override val name
            get() = "set"

        override val klass = Rt_SetValue::class

        fun strCode(type: Rt_ValueClass<*>, elements: Set<Rt_Value>, showTupleFieldNames: Boolean): String =
            "${type.name}[${elements.joinToString(",") { it.strCode(false) }}]"

        /** Build a Rt_Value set from an iterable, rejecting duplicates with a Gtv error. */
        fun listToSet(ctx: GtvToRtContext, elements: Iterable<Rt_Value>): MutableSet<Rt_Value> {
            val set = mutableSetOf<Rt_Value>()
            for (elem in elements) {
                if (!set.add(elem)) {
                    throw GtvRtUtils.errGtv(ctx, "set_dup:${elem.strCode()}", "Duplicate set element: ${elem.str()}")
                }
            }
            return set
        }

        /** Per-instance Gtv conversion: takes the element type's conversion + the set's runtime type. */
        fun gtvConversion(
            typeName: String,
            elementConversion: Lazy<Rt_GtvCompatibleValueClass<*>>,
            rtType: Lazy<Rt_ValueClass<*>>,
        ): Rt_GtvCompatibleValueClass<*> {
            val elementConv by elementConversion
            val rtTypeRef by rtType
            return object: Rt_UntypedGtvConversion(typeName) {
                override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
                    val set = value as Rt_SetValue
                    return GtvArray(set.elements.map { elementConv.rtToGtv(it, pretty) }.toTypedArray())
                }

                override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val array = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
                    val list = array.map { elementConv.gtvToRt(ctx, it) }
                    val set = listToSet(ctx, list)
                    return Rt_SetValue(rtTypeRef, set)
                }

                override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val array = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
                    val list = array.map { elementConv.gtvToRt(ctx, it) }
                    val set = listToSet(ctx, list)
                    return ctx.rtValue { Rt_SetValue(rtTypeRef, set) }
                }
            }
        }
    }
}
