/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.rell.base.lib.type.Rt_RangeValue
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.runtime.utils.Rt_ValueRecursionDetector

class Rt_StructValue private constructor(
    private val rtType: Rt_Type,
    /** R_StructType for lazy name/validator resolution. Null on pure-RR path. */
    private val rStructType: R_StructType?,
    /** Attribute names for str/strCode formatting. Null when unknown (rare fallback). */
    private val attrNames: List<String>?,
    private val attributes: MutableList<Rt_Value>,
): Rt_Value() {
    constructor(type: R_StructType, attributes: MutableList<Rt_Value>): this(
        rTypeToRtType(type),
        type,
        null,
        attributes,
    )

    constructor(rtType: Rt_Type, attributes: MutableList<Rt_Value>): this(rtType, null, null, attributes)
    constructor(rtType: Rt_Type, attrNames: List<String>, attributes: MutableList<Rt_Value>): this(
        rtType,
        null,
        attrNames,
        attributes,
    )

    /** Lazily resolved effective names — prefers explicit [attrNames], falls back to [rStructType]. */
    private val effectiveNames: List<String>?
        get() =
            attrNames ?: rStructType?.struct?.attributesList?.map { it.name }

    override val valueType = Rt_CoreValueTypes.STRUCT.type()

    override fun type() = rtType
    override fun asStruct() = this
    override fun equals(other: Any?) = other === this || (other is Rt_StructValue && attributes == other.attributes)
    override fun hashCode() = rtType.hashCode() * 31 + attributes.hashCode()

    override fun str(format: StrFormat): String {
        val names = effectiveNames
        return STR_RECURSION_DETECTOR.calculate(this) {
            if (names != null) {
                "${rtType.name}{${attributes.indices.joinToString(",") { "${names[it]}=${attributes[it].str(format)}" }}}"
            } else {
                "${rtType.name}{${attributes.joinToString(",") { it.str(format) }}}"
            }
        } ?: "${rtType.name}{...}"
    }

    override fun strCode(showTupleFieldNames: Boolean): String {
        val names = effectiveNames
        return STR_RECURSION_DETECTOR.calculate(this) {
            if (names != null) {
                "${rtType.name}[${attributes.indices.joinToString(",") { "${names[it]}=${attributes[it].strCode()}" }}]"
            } else {
                "${rtType.name}[${attributes.joinToString(",") { it.strCode() }}]"
            }
        } ?: "${rtType.name}[...]"
    }

    override fun strPretty(indent: Int): String {
        val indentStr = "    ".repeat(indent)
        val name = rtType.name
        return STR_RECURSION_DETECTOR.calculate(this) {
            val attrs = attributes.withIndex().joinToString(",") { (i, attr) ->
                val n = effectiveNames?.getOrNull(i) ?: "$i"
                val v = attr.strPretty(indent + 1)
                "\n$indentStr    $n = $v"
            }
            "$name{$attrs\n$indentStr}"
        } ?: "$name{...}"
    }

    fun get(index: Int): Rt_Value {
        return attributes[index]
    }

    fun size(): Int = attributes.size

    fun attributeNames(): List<String> = effectiveNames ?: List(attributes.size) { it.toString() }

    fun set(index: Int, value: Rt_Value) {
        rStructType?.struct?.attributesList?.get(index)?.validator?.check(value)?.raise()
        attributes[index] = value
    }

    class Builder(private val type: R_StructType) {
        private val v0: Rt_Value = Rt_RangeValue(0, 0, 0)
        private val values = MutableList(type.struct.attributes.size) { v0 }
        private var done = false

        fun set(attr: R_Attribute, value: Rt_Value) {
            check(!done)
            require(value !== v0)
            val index = attr.index
            require(values[index] === v0) { "$index $attr" }
            values[index] = value
        }

        fun build(): Rt_Value {
            check(!done)
            done = true
            for (index in values.indices) {
                require(values[index] !== v0) { index }
            }
            return createValidated(type, values)
        }
    }

    companion object {
        fun createValidated(type: R_StructType, attributes: MutableList<Rt_Value>): Rt_StructValue {
            for (attr in type.struct.attributesList) {
                attr.validator?.check(attributes[attr.index])?.raise()
            }
            return Rt_StructValue(type, attributes)
        }

        private val STR_RECURSION_DETECTOR = Rt_ValueRecursionDetector()

        /**
         * Format a struct value for `str()` / `strCode()` given an externally-provided
         * type name and attribute name list. Used by [Rt_VirtualStructValue] which holds
         * nullable per-attribute values.
         */
        fun formatStr(
            self: Rt_Value,
            typeName: String,
            attrNames: List<String>,
            attributes: List<Rt_Value?>,
            format: StrFormat,
        ): String {
            return STR_RECURSION_DETECTOR.calculate(self) {
                val attrs = attributes.indices.joinToString(",") { i ->
                    "${attrNames[i]}=${attributes[i]?.str(format)}"
                }
                "$typeName{$attrs}"
            } ?: "$typeName{...}"
        }

        fun formatStrCode(
            self: Rt_Value,
            typeName: String,
            attrNames: List<String>,
            attributes: List<Rt_Value?>,
        ): String {
            return STR_RECURSION_DETECTOR.calculate(self) {
                val attrs = attributes.indices.joinToString(",") { i ->
                    "${attrNames[i]}=${attributes[i]?.strCode()}"
                }
                "$typeName[$attrs]"
            } ?: "$typeName[...]"
        }
    }
}
