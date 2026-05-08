/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.rell.base.model.ErrorPos

class Rt_ListValue(override val type: Rt_ValueClass<*>, internal val elements: MutableList<Rt_Value> = mutableListOf()):
    Rt_Value, Rt_CollectionValue {

    override val collection: MutableCollection<Rt_Value> get() = elements
    override val name
        get() = Companion.name

    override fun equals(other: Any?) = other === this || (other is Rt_ListValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, elements)
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

    companion object: Rt_ValueClass<Rt_ListValue> {
        override val name
            get() = "list"

        override val klass = Rt_ListValue::class

        fun checkIndex(frame: Rt_Frame, errPos: ErrorPos, size: Int, index: Long) {
            val codeMsg = checkIndex0(size, index)
            if (codeMsg != null) {
                frame.error(errPos, codeMsg.first, codeMsg.second)
            }
        }

        fun checkIndex(size: Int, index: Long) {
            val codeMsg = checkIndex0(size, index)
            if (codeMsg != null) {
                throw Rt_Exception.common(codeMsg.first, codeMsg.second)
            }
        }

        private fun checkIndex0(size: Int, index: Long): Pair<String, String>? = when {
            index in 0..<size -> null
            else -> "list:index:$size:$index" to "List index out of bounds: $index (size $size)"
        }

        fun strCode(type: Rt_ValueClass<*>, elements: List<Rt_Value?>): String {
            val elems = elements.joinToString(",") { it?.strCode(false) ?: "null" }
            return "${type.name}[$elems]"
        }

        /** Per-instance Gtv conversion: takes the element type's conversion + the list's runtime type. */
        fun gtvConversion(
            typeName: String,
            elementConversion: Lazy<Rt_GtvCompatibleValueClass<*>>,
            rtType: Lazy<Rt_ValueClass<*>>,
        ): Rt_GtvCompatibleValueClass<*> {
            val elementConv by elementConversion
            val rtTypeRef by rtType
            return object: Rt_UntypedGtvConversion(typeName) {
                override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
                    val list = value as Rt_ListValue
                    return GtvArray(list.elements.map { elementConv.rtToGtv(it, pretty) }.toTypedArray())
                }

                override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val array = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
                    val rtList = array.map { elementConv.gtvToRt(ctx, it) }
                    return Rt_ListValue(rtTypeRef, rtList.toMutableList())
                }

                override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val array = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
                    val rtList = array.map { elementConv.gtvToRt(ctx, it) }
                    return ctx.rtValue { Rt_ListValue(rtTypeRef, rtList.toMutableList()) }
                }
            }
        }
    }
}
