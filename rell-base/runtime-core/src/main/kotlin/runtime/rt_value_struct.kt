/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_Struct
import net.postchain.rell.base.model.R_StructType
import net.postchain.rell.base.runtime.utils.Rt_ValueRecursionDetector

class Rt_StructValue private constructor(
    override val type: Rt_ValueClass<*>,
    /** R_StructType for lazy name/validator resolution. Null on pure-RR path. */
    private val rStructType: R_StructType?,
    /** Attribute names for str/strCode formatting. Null when unknown (rare fallback). */
    private val attrNames: List<String>?,
    private val attributes: MutableList<Rt_Value>,
): Rt_ValueBase() {
    constructor(type: R_StructType, attributes: MutableList<Rt_Value>): this(
        rTypeStub(type),
        type,
        null,
        attributes,
    )

    constructor(rtType: Rt_ValueClass<*>, attributes: MutableList<Rt_Value>): this(rtType, null, null, attributes)
    constructor(rtType: Rt_ValueClass<*>, attrNames: List<String>, attributes: MutableList<Rt_Value>): this(
        rtType,
        null,
        attrNames,
        attributes,
    )

    /** Lazily resolved effective names — prefers explicit [attrNames], falls back to [rStructType]. */
    private val effectiveNames: List<String>?
        get() =
            attrNames ?: rStructType?.struct?.attributesList?.map { it.name }

    override val name
        get() = Companion.name

    override fun equals(other: Any?) = other === this || (other is Rt_StructValue && attributes == other.attributes)
    // Hash by type.name, not type.hashCode(): different Rt_ValueClass implementations
    // (Rt_StructType uses defIndex, Rt_GenericRrType-based stubs use name.hashCode(), etc.)
    // produce inconsistent hashes for the "same" struct type built via different routes,
    // breaking HashMap/HashSet semantics.
    override fun hashCode() = type.name.hashCode() * 31 + attributes.hashCode()

    override fun str(format: Rt_StrFormat): String {
        val names = effectiveNames
        return STR_RECURSION_DETECTOR.calculate(this) {
            if (names != null) {
                "${type.name}{${attributes.indices.joinToString(",") { "${names[it]}=${attributes[it].str(format)}" }}}"
            } else {
                "${type.name}{${attributes.joinToString(",") { it.str(format) }}}"
            }
        } ?: "${type.name}{...}"
    }

    override fun strCode(showTupleFieldNames: Boolean): String {
        val names = effectiveNames
        return STR_RECURSION_DETECTOR.calculate(this) {
            if (names != null) {
                "${type.name}[${attributes.indices.joinToString(",") { "${names[it]}=${attributes[it].strCode()}" }}]"
            } else {
                "${type.name}[${attributes.joinToString(",") { it.strCode() }}]"
            }
        } ?: "${type.name}[...]"
    }

    override fun strPretty(indent: Int): String {
        val indentStr = "    ".repeat(indent)
        val name = type.name
        return STR_RECURSION_DETECTOR.calculate(this) {
            val attrs = attributes.withIndex().joinToString(",") { (i, attr) ->
                val n = effectiveNames?.getOrNull(i) ?: "$i"
                val v = attr.strPretty(indent + 1)
                "\n$indentStr    $n = $v"
            }
            "$name{$attrs\n$indentStr}"
        } ?: "$name{...}"
    }

    /** Internal accessors used by [Companion.gtvConversion] which encodes via per-attribute conversions. */
    internal val attributesView: List<Rt_Value>
        get() = attributes

    internal val effectiveNamesView: List<String>?
        get() = effectiveNames

    fun get(index: Int): Rt_Value = attributes[index]
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

    companion object: Rt_ValueClass<Rt_StructValue> {
        override val name
            get() = "struct"

        override val klass = Rt_StructValue::class

        /** Decode a Gtv array as struct attribute values, validating size against [minCount]..struct.attributesList.size. */
        fun gtvToAttrValues(
            ctx: GtvToRtContext,
            gtv: Gtv,
            typeName: String,
            struct: R_Struct,
            minCount: Int,
        ): List<Gtv> {
            val maxCount = struct.attributesList.size
            check(minCount <= maxCount)
            val gtvFields = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
            val actualCount = gtvFields.size
            if (actualCount !in minCount..maxCount) {
                throw errWrongArraySize(ctx, typeName, minCount, maxCount, actualCount)
            }
            return gtvFields.toList()
        }

        fun errWrongArraySize(
            ctx: GtvToRtContext,
            typeName: String,
            minCount: Int,
            maxCount: Int,
            actualCount: Int,
        ): Rt_Exception {
            val expCountStr = if (minCount == maxCount) "$minCount" else "$minCount..$maxCount"
            return GtvRtUtils.errGtv(
                ctx, "struct_size:$typeName:$minCount:$maxCount:$actualCount",
                "Wrong Gtv array size for struct '$typeName': $actualCount instead of $expCountStr",
            )
        }

        private fun gtvToRtStructAttr(
            ctx: GtvToRtContext,
            attrConversions: List<Rt_GtvCompatibleValueClass<*>>,
            struct: R_Struct,
            attr: R_Attribute,
            attrIndex: Int,
            gtvAttr: Gtv?,
        ): Rt_Value {
            if (gtvAttr != null) {
                val attrCtx = ctx.updateSymbol(GtvToRtSymbol_Attr(struct.name, attr))
                return attrConversions[attrIndex].gtvToRt(attrCtx, gtvAttr)
            }
            return ctx.getDefaultValue(struct.rDefBase, attr, struct.name, "struct")
        }

        fun gtvConversion(struct: R_Struct): Rt_GtvCompatibleValueClass<*> {
            val attrConversions by lazy { struct.attributesList.map { gtvConversionFromR(it.type)!! } }
            val attrs = struct.attributesList
            val minAttrCount = attrs.indexOfLast { !it.hasExpr } + 1
            val type = struct.type

            fun decode(ctx: GtvToRtContext, gtv: Gtv): MutableList<Rt_Value> {
                return if (ctx.pretty && gtv.type == net.postchain.gtv.GtvType.DICT) {
                    val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, type.name)
                    val rtAttrs = attrs.mapIndexed { i, attr ->
                        gtvToRtStructAttr(ctx, attrConversions, struct, attr, i, gtvFields[attr.name])
                    }
                    for (key in gtvFields.keys) {
                        if (key !in struct.strAttributes) {
                            throw GtvRtUtils.errGtv(
                                ctx, "struct_badkey:${struct.name}:$key",
                                "Wrong key in Gtv dictionary for type '${struct.name}': '$key'",
                            )
                        }
                    }
                    rtAttrs.toMutableList()
                } else {
                    val gtvAttrValues = gtvToAttrValues(ctx, gtv, type.name, struct, minAttrCount)
                    attrs.mapIndexed { i, attr ->
                        gtvToRtStructAttr(ctx, attrConversions, struct, attr, i, gtvAttrValues.getOrNull(i))
                    }.toMutableList()
                }
            }

            return object: Rt_UntypedGtvConversion(type.name) {
                override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
                    val struct = value as Rt_StructValue
                    return if (pretty) {
                        val names = struct.effectiveNamesView
                            ?: throw Rt_Exception.common(
                                "gtv:struct:no_names",
                                "No attribute names for ${struct.type.name}",
                            )
                        val map = struct.attributesView.mapIndexed { i, v ->
                            names[i] to attrConversions[i].rtToGtv(v, true)
                        }.toMap()
                        GtvFactory.gtv(map)
                    } else {
                        GtvArray(
                            struct.attributesView.mapIndexed { i, v ->
                                attrConversions[i].rtToGtv(v, false)
                            }.toTypedArray(),
                        )
                    }
                }

                override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value =
                    createValidated(type, decode(ctx, gtv))

                override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val attrs = decode(ctx, gtv)
                    return ctx.rtValue { createValidated(type, attrs) }
                }
            }
        }

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
            format: Rt_StrFormat,
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
