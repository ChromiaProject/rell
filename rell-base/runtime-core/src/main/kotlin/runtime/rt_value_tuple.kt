/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.toImmList

class Rt_TupleValue private constructor(
    override val type: Rt_ValueClass<*>,
    private val fieldNames: List<String?>?,
    val elements: List<Rt_Value>,
): Rt_Value {
    constructor(rtType: Rt_ValueClass<*>, elements: List<Rt_Value>): this(rtType, null, elements)

    override val name
        get() = Companion.name

    override fun equals(other: Any?) = other === this || (other is Rt_TupleValue && elements == other.elements)
    override fun hashCode() = elements.hashCode()

    /** Field names — from explicit parameter or derived from RR_Type.Tuple. */
    private val effectiveFieldNames: List<String?>? by lazy {
        fieldNames ?: (type.rrType as? RR_Type.Tuple)?.fields?.map { it.name }
    }

    override fun str(format: Rt_StrFormat): String {
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
            return str(Rt_StrFormat.V2)
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

    /** Used by the gtv-conversion factory; returns null if any field name is absent. */
    internal val fieldNamesForEncoding: List<String?>?
        get() = effectiveFieldNames

    companion object: Rt_ValueClass<Rt_TupleValue> {
        override val name
            get() = "tuple"

        override val klass = Rt_TupleValue::class

        fun make(rtType: Rt_ValueClass<*>, vararg elements: Rt_Value): Rt_TupleValue =
            Rt_TupleValue(rtType, elements.toImmList())

        /** Decode a Gtv array as tuple field values; throws if the field count doesn't match. */
        fun gtvArrayToFields(ctx: GtvToRtContext, typeName: String, fieldCount: Int, gtv: Gtv): List<Gtv> {
            val gtvFields = GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)
            checkTupleFieldCount(ctx, gtvFields.size, fieldCount, "array")
            return gtvFields.toList()
        }

        private fun checkTupleFieldCount(
            ctx: GtvToRtContext,
            actualCount: Int,
            expectedCount: Int,
            structure: String,
        ) {
            if (actualCount != expectedCount) {
                throw GtvRtUtils.errGtv(
                    ctx, "tuple_count:$expectedCount:$actualCount",
                    "Wrong Gtv $structure size: $actualCount instead of $expectedCount",
                )
            }
        }

        fun gtvConversion(
            typeName: String,
            fieldNames: net.postchain.rell.base.utils.ImmList<String?>,
            fieldConversions: Lazy<net.postchain.rell.base.utils.ImmList<Rt_GtvCompatibleValueClass<*>>>,
            rtType: Lazy<Rt_ValueClass<*>>,
        ): Rt_GtvCompatibleValueClass<*> {
            val convs by fieldConversions
            val allFieldsNamed = fieldNames.all { it != null }

            return object: Rt_UntypedGtvConversion(typeName) {
                override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
                    val tuple = value as Rt_TupleValue
                    val names = tuple.fieldNamesForEncoding
                    return if (pretty && names != null && names.all { it != null }) {
                        val map = tuple.elements.mapIndexed { i, v -> names[i]!! to convs[i].rtToGtv(v, true) }.toMap()
                        GtvFactory.gtv(map)
                    } else {
                        GtvArray(tuple.elements.mapIndexed { i, v -> convs[i].rtToGtv(v, false) }.toTypedArray())
                    }
                }

                override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val rtFields = decodeFields(ctx, gtv)
                    return Rt_TupleValue(rtType.value, rtFields)
                }

                override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val rtFields = decodeFields(ctx, gtv)
                    return ctx.rtValue { Rt_TupleValue(rtType.value, rtFields) }
                }

                private fun decodeFields(ctx: GtvToRtContext, gtv: Gtv): List<Rt_Value> {
                    return if (ctx.pretty && allFieldsNamed && gtv.type == net.postchain.gtv.GtvType.DICT) {
                        val gtvFields = GtvRtUtils.gtvToMap(ctx, gtv, typeName)
                        checkTupleFieldCount(ctx, gtvFields.size, fieldNames.size, "dictionary")
                        fieldNames.mapIndexed { i, name ->
                            val key = name!!
                            if (key !in gtvFields) {
                                throw GtvRtUtils.errGtv(
                                    ctx,
                                    "tuple_nokey:$key",
                                    "Key missing in Gtv dictionary: '$key'",
                                )
                            }
                            convs[i].gtvToRt(ctx, gtvFields.getValue(key))
                        }
                    } else {
                        val gtvFields = gtvArrayToFields(ctx, typeName, fieldNames.size, gtv)
                        gtvFields.mapIndexed { i, gtvField -> convs[i].gtvToRt(ctx, gtvField) }
                    }
                }
            }
        }
    }
}
