/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.toImmList

class Rt_TupleValue private constructor(
    private val rtType: Rt_Type,
    private val fieldNames: List<String?>?,
    val elements: List<Rt_Value>,
): Rt_Value() {
    constructor(rtType: Rt_Type, elements: List<Rt_Value>): this(rtType, null, elements)

    override val valueType = Rt_CoreValueTypes.TUPLE.type()

    override fun type() = rtType
    override fun asTuple() = elements
    override fun equals(other: Any?) = other === this || (other is Rt_TupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    /** Field names — from explicit parameter or derived from RR_Type.Tuple. */
    private val effectiveFieldNames: List<String?>? by lazy {
        fieldNames ?: (rtType.rrType as? RR_Type.Tuple)?.fields?.map { it.name }
    }

    override fun str(format: StrFormat): String {
        val names = effectiveFieldNames
        return if (names != null) {
            elements.indices.joinToString(",", "(", ")") { i ->
                val n = names[i]
                val v = elements[i].str(format)
                if (n == null) v else "$n=$v"
            }
        } else {
            "(${elements.joinToString(",") { it.str(format) }})"
        }
    }

    override fun strCode(showTupleFieldNames: Boolean): String {
        val names = effectiveFieldNames
        return elements.indices.joinToString(",", "(", ")") { i ->
            val v = elements[i].strCode()
            val n = names?.get(i)
            if (n == null || !showTupleFieldNames) v else "$n=$v"
        }
    }

    override fun strPretty(indent: Int): String {
        if (elements.isEmpty()) {
            return str(StrFormat.V2)
        }

        val indentStr = "    ".repeat(indent)
        val names: List<String?> = effectiveFieldNames ?: List(elements.size) { null }
        return names
            .mapIndexed { i, fieldName ->
                val v = elements[i].strPretty(indent + 1)
                val s = if (fieldName == null) v else "$fieldName = $v"
                "\n$indentStr    $s"
            }
            .joinToString(",", "(", "\n$indentStr)")
    }

    companion object {
        fun make(rtType: Rt_Type, vararg elements: Rt_Value): Rt_Value {
            return Rt_TupleValue(rtType, elements.toImmList())
        }
    }
}
