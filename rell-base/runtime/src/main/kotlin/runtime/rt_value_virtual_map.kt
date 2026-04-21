/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.rell.base.lib.type.Rt_MapValue
import net.postchain.rell.base.utils.immListOf

class Rt_VirtualMapValue(
    gtv: Gtv,
    private val rtType: Rt_Type,
    private val virtualEntryRtType: Rt_Type,
    private val innerMapRtType: Rt_Type,
    private val map: Map<Rt_Value, Rt_Value>,
): Rt_VirtualValue(gtv) {

    override val valueType = Rt_CoreValueTypes.VIRTUAL_MAP.type()

    override fun type() = rtType
    override fun asMap() = map
    override fun strCode(showTupleFieldNames: Boolean) = Rt_MapValue.strCode(rtType, showTupleFieldNames, map)

    override fun str(format: StrFormat): String {
        return map
            .entries
            .joinToString(", ", "{", "}") { "${it.key.str(format)}=${it.value.str(format)}" }
    }

    override fun equals(other: Any?) = other === this || (other is Rt_VirtualMapValue && map == other.map)
    override fun hashCode() = map.hashCode()

    override fun asIterable(): Iterable<Rt_Value> {
        return map.entries.map { entry ->
            Rt_TupleValue(virtualEntryRtType, immListOf(entry.key, entry.value))
        }
    }

    override fun toFull0(): Rt_Value {
        val resMap = map
            .mapKeys { (k, _) -> toFull(k) }
            .mapValues { (_, v) -> toFull(v) }
            .toMutableMap()
        return Rt_MapValue(innerMapRtType, resMap)
    }
}
