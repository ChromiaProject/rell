/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.model.R_VirtualMapType
import net.postchain.rell.base.utils.immListOf

class Rt_VirtualMapValue(
    gtv: Gtv,
    override val type: Rt_ValueClass<*>,
    private val virtualEntryRtType: Rt_ValueClass<*>,
    private val innerMapRtType: Rt_ValueClass<*>,
    internal val map: Map<Rt_Value, Rt_Value>,
): Rt_VirtualValue(gtv), Rt_MapBackedValue, Rt_IterableValue {
    override val mapView: Map<Rt_Value, Rt_Value> get() = map

    override fun iterator(): Iterator<Rt_Value> = map.entries.map { entry ->
        Rt_TupleValue(virtualEntryRtType, immListOf(entry.key, entry.value))
    }.iterator()

    override val name
        get() = Companion.name

    override fun strCode(showTupleFieldNames: Boolean) = Rt_MapValue.strCode(type, showTupleFieldNames, map)

    override fun str(format: Rt_StrFormat): String {
        return map
            .entries
            .joinToString(", ", "{", "}") { "${it.key.str(format)}=${it.value.str(format)}" }
    }

    override fun equals(other: Any?) = other === this || (other is Rt_VirtualMapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun toFull0(): Rt_Value {
        val resMap = map
            .mapKeys { (k, _) -> toFull(k) }
            .mapValues { (_, v) -> toFull(v) }
            .toMutableMap()
        return Rt_MapValue(innerMapRtType, resMap)
    }

    companion object: Rt_ValueClass<Rt_VirtualMapValue> {
        override val name
            get() = "virtual_map"

        override val klass = Rt_VirtualMapValue::class

        fun gtvConversion(type: R_VirtualMapType): Rt_GtvCompatibleValueClass<*> = gtvConversionOf { ctx, gtv ->
            decodeVirtualMap(ctx, type, deserializeVirtual(ctx, gtv))
        }
    }
}
