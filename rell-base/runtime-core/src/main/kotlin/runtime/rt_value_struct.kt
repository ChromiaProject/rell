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

/**
 * Abstract base for struct values. Concrete leaves choose their physical layout:
 * [Rt_HeapStruct] for the canonical ArrayList-backed representation used by the tree-walk
 * interpreter and by paths that have not been specialised on the Truffle side; the Truffle
 * backend will add SOM-generated leaves with primitive fields per attribute for the JIT hot
 * path. Layout-vs-capability separation: layout lives on the leaf class, all capability
 * dispatch (str/strCode/strPretty/equals/hashCode/Gtv conversion) routes through the abstract
 * API ([size], [get], [effectiveNamesOrNull]) defined here.
 *
 * Constructor-style callers `Rt_StructValue(type, attrs)` continue to compile via the
 * `operator fun invoke` overloads on the companion — they materialize an [Rt_HeapStruct].
 */
abstract class Rt_StructValue protected constructor(): Rt_ValueBase() {
    // Defaults throw rather than being declared `abstract` so the Truffle Static Object Model
    // can subclass through this hierarchy — its `validateClasses` walks the entire superclass
    // chain and rejects any class with abstract methods (so the SOM-generated final subclass
    // has a clean, instantiable parent). Concrete leaves ([Rt_HeapStruct],
    // [net.postchain.rell.base.runtime.truffle.values.Tf_DynStruct]) override unconditionally.

    override val type: Rt_ValueClass<*>
        get() = error("Rt_StructValue.type not provided by ${javaClass.name}")

    /** Number of attributes in the struct. Concrete leaves override. */
    open fun size(): Int = error("Rt_StructValue.size not provided by ${javaClass.name}")

    /** Read the [index]-th attribute. Concrete leaves override. */
    open fun get(index: Int): Rt_Value =
        error("Rt_StructValue.get not provided by ${javaClass.name}")

    /** Write the [index]-th attribute. Implementations may apply per-attribute validators. */
    open fun set(index: Int, value: Rt_Value) {
        error("Rt_StructValue.set not provided by ${javaClass.name}")
    }

    /**
     * Best-effort attribute names; null when the layout doesn't carry them (rare fallback). Used
     * by [str]/[strCode]/[strPretty] and by the Gtv conversion to emit pretty/dictionary form.
     */
    open val effectiveNamesOrNull: List<String>?
        get() = null

    fun attributeNames(): List<String> = effectiveNamesOrNull ?: List(size()) { it.toString() }

    override val name
        get() = Companion.name

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Rt_StructValue) return false
        val n = size()
        if (n != other.size()) return false
        for (i in 0 until n) if (get(i) != other.get(i)) return false
        return true
    }

    // Hash by type.name, not type.hashCode(): different Rt_ValueClass implementations
    // (Rt_StructType uses defIndex, Rt_GenericRrType-based stubs use name.hashCode(), etc.)
    // produce inconsistent hashes for the "same" struct type built via different routes,
    // breaking HashMap/HashSet semantics.
    override fun hashCode(): Int {
        var h = type.name.hashCode() * 31
        val n = size()
        for (i in 0 until n) h = h * 31 + get(i).hashCode()
        return h
    }

    override fun str(format: Rt_StrFormat): String {
        val names = effectiveNamesOrNull
        val n = size()
        return STR_RECURSION_DETECTOR.calculate(this) {
            if (names != null) {
                "${type.name}{${(0 until n).joinToString(",") { "${names[it]}=${get(it).str(format)}" }}}"
            } else {
                "${type.name}{${(0 until n).joinToString(",") { get(it).str(format) }}}"
            }
        } ?: "${type.name}{...}"
    }

    override fun strCode(showTupleFieldNames: Boolean): String {
        val names = effectiveNamesOrNull
        val n = size()
        return STR_RECURSION_DETECTOR.calculate(this) {
            if (names != null) {
                "${type.name}[${(0 until n).joinToString(",") { "${names[it]}=${get(it).strCode()}" }}]"
            } else {
                "${type.name}[${(0 until n).joinToString(",") { get(it).strCode() }}]"
            }
        } ?: "${type.name}[...]"
    }

    override fun strPretty(indent: Int): String {
        val indentStr = "    ".repeat(indent)
        val typeName = type.name
        val n = size()
        return STR_RECURSION_DETECTOR.calculate(this) {
            val attrs = (0 until n).joinToString(",") { i ->
                val nm = effectiveNamesOrNull?.getOrNull(i) ?: "$i"
                val v = get(i).strPretty(indent + 1)
                "\n$indentStr    $nm = $v"
            }
            "$typeName{$attrs\n$indentStr}"
        } ?: "$typeName{...}"
    }

    /** Internal accessors used by [Companion.gtvConversion]. Materialises a list view; leaves with array-backed storage may override for efficiency. */
    internal open val attributesView: List<Rt_Value>
        get() = List(size()) { get(it) }

    internal val effectiveNamesView: List<String>?
        get() = effectiveNamesOrNull

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

        /**
         * Construct a heap-backed struct value with the given R_-level type and attribute list.
         * Preserves the legacy `Rt_StructValue(type, attrs)` call syntax by routing through the
         * companion's `invoke` operator; the actual instance is an [Rt_HeapStruct].
         */
        operator fun invoke(type: R_StructType, attributes: MutableList<Rt_Value>): Rt_StructValue =
            Rt_HeapStruct(type, attributes)

        operator fun invoke(rtType: Rt_ValueClass<*>, attributes: MutableList<Rt_Value>): Rt_StructValue =
            Rt_HeapStruct(rtType, attributes)

        operator fun invoke(
            rtType: Rt_ValueClass<*>,
            attrNames: List<String>,
            attributes: MutableList<Rt_Value>,
        ): Rt_StructValue = Rt_HeapStruct(rtType, attrNames, attributes)

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
            return Rt_HeapStruct(type, attributes)
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

/**
 * Object[]-backed struct leaf. Canonical representation used by the tree-walk interpreter and
 * the spill target for paths the Truffle backend has not specialised onto a SOM-generated layout.
 * Instances are constructed via [Rt_StructValue.invoke] / [Rt_StructValue.createValidated] /
 * [Rt_StructValue.Builder] — the constructors below are kept `public` to preserve the previous
 * private-constructor / multi-secondary-constructor surface.
 *
 * Backed by a flat `Array<Rt_Value>` rather than `ArrayList<Rt_Value>`: one fewer object per
 * struct (no ArrayList wrapper around the same `Object[]`), no `grow` cost on construction
 * (size is fixed at attribute count), and a cheaper `get(i)` (no virtual List dispatch).
 */
class Rt_HeapStruct private constructor(
    override val type: Rt_ValueClass<*>,
    /** R_StructType for lazy name/validator resolution. Null on pure-RR path. */
    private val rStructType: R_StructType?,
    /** Attribute names for str/strCode formatting. Null when unknown (rare fallback). */
    private val attrNamesArg: List<String>?,
    private val attributes: Array<Rt_Value>,
): Rt_StructValue() {
    constructor(type: R_StructType, attributes: MutableList<Rt_Value>): this(
        rTypeStub(type),
        type,
        null,
        attributes.toTypedArray(),
    )

    constructor(rtType: Rt_ValueClass<*>, attributes: MutableList<Rt_Value>): this(
        rtType,
        null,
        null,
        attributes.toTypedArray(),
    )

    constructor(rtType: Rt_ValueClass<*>, attrNames: List<String>, attributes: MutableList<Rt_Value>): this(
        rtType,
        null,
        attrNames,
        attributes.toTypedArray(),
    )

    override val effectiveNamesOrNull: List<String>?
        get() = attrNamesArg ?: rStructType?.struct?.attributesList?.map { it.name }

    override fun size(): Int = attributes.size

    override fun get(index: Int): Rt_Value = attributes[index]

    override fun set(index: Int, value: Rt_Value) {
        rStructType?.struct?.attributesList?.get(index)?.validator?.check(value)?.raise()
        attributes[index] = value
    }

    override val attributesView: List<Rt_Value>
        get() = attributes.asList()
}
