/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.immSetOf
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import kotlin.reflect.full.createType

sealed class Rt_BooleanValue: Rt_ValueBase() {
    abstract val value: Boolean

    final override val name
        get() = Companion.name

    final override val type
        get() = Rt_PrimitiveTypes.BOOLEAN

    final override fun toFormatArg() = value
    final override fun strCode(showTupleFieldNames: Boolean) = if (value) "boolean[true]" else "boolean[false]"
    final override fun str(format: Rt_StrFormat) = if (value) "true" else "false"

    object TRUE: Rt_BooleanValue() {
        override val value
            get() = true
    }

    object FALSE: Rt_BooleanValue() {
        override val value
            get() = false
    }

    companion object:
        Rt_GtvCompatibleValueClass<Rt_BooleanValue>,
        Rt_NativeCompatibleValueClass<Rt_BooleanValue>,
        Rt_SqlCompatibleValueClass<Rt_BooleanValue> {

        override val name
            get() = "boolean"

        override val klass = Rt_BooleanValue::class
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.BOOLEAN)

        override val nativeTypes = immSetOf(Boolean::class.createType())

        override val sqlType: DataType<Boolean>
            get() = SQLDataType.BOOLEAN

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> a.asBoolean().compareTo(b.asBoolean()) }

        fun get(value: Boolean): Rt_BooleanValue = if (value) TRUE else FALSE

        override fun toGtv(value: Rt_BooleanValue, pretty: Boolean): Gtv =
            GtvInteger(if (value.value) 1L else 0L)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_BooleanValue =
            get(GtvRtUtils.gtvToBoolean(ctx, gtv, "boolean"))

        override fun toNative(value: Rt_BooleanValue): Any = value.value
        override fun fromNative(value: Any?): Rt_BooleanValue = get(value as Boolean)
        override fun toSqlValue(value: Rt_BooleanValue): Any = value.value

        override fun toSql(value: Rt_BooleanValue, params: PreparedStatementParams, idx: Int) =
            params.setBoolean(idx, value.value)

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getBoolean(idx)
            return Rt_SqlNull.check(!v, row, name, nullable) ?: get(v)
        }
    }
}
