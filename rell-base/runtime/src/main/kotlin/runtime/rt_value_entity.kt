/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import java.util.*

class Rt_EntityValue(val rtType: Rt_Type, val rowid: Long): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.ENTITY.type()

    override fun type() = rtType
    override fun asObjectId() = rowid
    override fun strCode(showTupleFieldNames: Boolean) = "${rtType.name}[$rowid]"
    override fun str(format: StrFormat) = strCode()
    override fun equals(other: Any?) =
        other === this || (other is Rt_EntityValue && rtType == other.rtType && rowid == other.rowid)

    override fun hashCode() = Objects.hash(rtType, rowid)
}
