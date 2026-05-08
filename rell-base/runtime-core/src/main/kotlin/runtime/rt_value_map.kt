/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.model.rr.RR_TupleField
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.utils.immListOf

fun rrTypeName(rrType: RR_Type): String = when (rrType) {
    is RR_Type.Primitive -> rrType.kind.name.lowercase()
    is RR_Type.Nullable -> "${rrTypeName(rrType.value)}?"
    is RR_Type.List -> "list<${rrTypeName(rrType.element)}>"
    is RR_Type.Set -> "set<${rrTypeName(rrType.element)}>"
    is RR_Type.Map -> "map<${rrTypeName(rrType.key)},${rrTypeName(rrType.value)}>"
    else -> "?"
}

class Rt_MapValue(override val type: Rt_ValueClass<*>, map: MutableMap<Rt_Value, Rt_Value>):
    Rt_Value, Rt_MutableMapBackedValue, Rt_IterableValue, Iterable<Rt_Value> {
    val map: Map<Rt_Value, Rt_Value> = map
    internal val mutableMap = map

    override val mapView: Map<Rt_Value, Rt_Value> get() = map
    override val mutableMapView: MutableMap<Rt_Value, Rt_Value> get() = mutableMap
    override fun iterator(): Iterator<Rt_Value> = mapEntriesAsTuples().iterator()


    override val name
        get() = Companion.name

    override fun equals(other: Any?) = other === this || (other is Rt_MapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun strCode(showTupleFieldNames: Boolean) = strCode(type, showTupleFieldNames, map)

    override fun str(format: Rt_StrFormat): String {
        return map
            .entries
            .joinToString(", ", "{", "}") { "${it.key.str(format)}=${it.value.str(format)}" }
    }

    override fun strPretty(indent: Int): String {
        if (map.isEmpty()) {
            return str(Rt_StrFormat.V2)
        }
        val indentStr = "    ".repeat(indent)
        return map.entries.joinToString(",", "{", "\n$indentStr}") {
            val k = it.key.str(Rt_StrFormat.V2)
            val v = it.value.strPretty(indent + 1)
            "\n$indentStr    $k = $v"
        }
    }

    /** Iteration view used by the [asIterable] extension on [Rt_Value]. */
    internal fun mapEntriesAsTuples(): Iterable<Rt_Value> {
        val mapRR = type.rrType as? RR_Type.Map
        val entryRRType = if (mapRR != null) RR_Type.Tuple(
            immListOf(
                RR_TupleField(null, mapRR.key),
                RR_TupleField(null, mapRR.value),
            ),
        ) else RR_Type.Error
        val entryName = if (mapRR != null) {
            val k = rrTypeName(mapRR.key)
            val v = rrTypeName(mapRR.value)
            "($k,$v)"
        } else "(?, ?)"
        val entryRtType = Rt_GenericRrType(entryRRType, entryName)
        return map.entries.map { entry ->
            Rt_TupleValue(entryRtType, immListOf(entry.key, entry.value))
        }
    }


    companion object: Rt_ValueClass<Rt_MapValue> {
        override val name
            get() = "map"

        override val klass = Rt_MapValue::class

        fun strCode(type: Rt_ValueClass<*>, showTupleFieldNames: Boolean, map: Map<Rt_Value, Rt_Value>): String {
            val entries = map.entries.joinToString(",") { (key, value) ->
                key.strCode(false) + "=" + value.strCode(false)
            }
            return "${type.name}[$entries]"
        }

        /** Per-instance Gtv conversion: encodes as Gtv dict when key is `text`, otherwise as array of [k,v] pairs. */
        fun gtvConversion(
            typeName: String,
            isTextKey: Boolean,
            keyConversion: Lazy<Rt_GtvCompatibleValueClass<*>>,
            valueConversion: Lazy<Rt_GtvCompatibleValueClass<*>>,
            rtType: Lazy<Rt_ValueClass<*>>,
        ): Rt_GtvCompatibleValueClass<*> {
            val keyConv by keyConversion
            val valueConv by valueConversion
            val rtTypeRef by rtType

            fun decodeMap(ctx: GtvToRtContext, gtv: Gtv): MutableMap<Rt_Value, Rt_Value> {
                return if (isTextKey && gtv.type == net.postchain.gtv.GtvType.DICT) {
                    GtvRtUtils.gtvToMap(ctx, gtv, typeName)
                        .mapKeys { (k, _) -> Rt_TextValue.get(k) as Rt_Value }
                        .mapValues { (_, v) -> valueConv.gtvToRt(ctx, v) }
                        .toMutableMap()
                } else {
                    val tmp = mutableMapOf<Rt_Value, Rt_Value>()
                    for (gtvEntry in GtvRtUtils.gtvToArrayAny(ctx, gtv, typeName)) {
                        val array = GtvRtUtils.gtvToArray(ctx, gtvEntry, 2, "map_entry_size", typeName)
                        val key = keyConv.gtvToRt(ctx, array[0])
                        val value = valueConv.gtvToRt(ctx, array[1])
                        if (key in tmp) {
                            throw GtvRtUtils.errGtv(
                                ctx,
                                "map_dup_key:${key.strCode()}",
                                "Duplicate map key: ${key.str()}",
                            )
                        }
                        tmp[key] = value
                    }
                    tmp
                }
            }

            return object: Rt_UntypedGtvConversion(typeName) {
                override fun toGtv(value: Rt_Value, pretty: Boolean): Gtv {
                    val map = (value as Rt_MapValue).map
                    return if (isTextKey) {
                        val m = map.mapKeys { (k, _) -> k.asString() }
                            .mapValues { (_, v) -> valueConv.rtToGtv(v, pretty) }
                        GtvFactory.gtv(m)
                    } else {
                        val entries = map.map { (k, v) ->
                            GtvArray(arrayOf(keyConv.rtToGtv(k, pretty), valueConv.rtToGtv(v, pretty)))
                        }
                        GtvArray(entries.toTypedArray())
                    }
                }

                override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_Value =
                    Rt_MapValue(rtTypeRef, decodeMap(ctx, gtv))

                override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
                    val map = decodeMap(ctx, gtv)
                    return ctx.rtValue { Rt_MapValue(rtTypeRef, map) }
                }
            }
        }
    }
}
