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
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.immSetOf
import net.postchain.rell.base.utils.mapToImmList
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import kotlin.reflect.full.createType

@ConsistentCopyVisibility
@JvmRecord
data class Rt_RowidValue private constructor(val value: Long): Rt_Value {
    init {
        check(value >= 0) { "Negative rowid value: $value" }
    }

    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.ROWID

    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "rowid[$value]"
    override fun str(format: Rt_StrFormat) = "" + value

    companion object:
        Rt_GtvCompatibleValueClass<Rt_RowidValue>,
        Rt_NativeCompatibleValueClass<Rt_RowidValue>,
        Rt_SqlCompatibleValueClass<Rt_RowidValue> {
        override val name
            get() = "rowid"

        override val klass = Rt_RowidValue::class
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.ROWID)
        override val nativeTypes = immSetOf(Long::class.createType())

        override val sqlType: DataType<Long>
            get() = SQLDataType.BIGINT

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> a.asRowid().compareTo(b.asRowid()) }

        private val VALUES: ImmList<Rt_RowidValue> = (0..1000).mapToImmList { Rt_RowidValue(it.toLong()) }

        val ZERO: Rt_RowidValue = VALUES[0]

        fun get(value: Long): Rt_RowidValue {
            return if (value >= 0 && value < VALUES.size) VALUES[value.toInt()] else Rt_RowidValue(value)
        }

        override fun toGtv(value: Rt_RowidValue, pretty: Boolean): Gtv = GtvInteger(value.value)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_RowidValue {
            val v = GtvRtUtils.gtvToInteger(ctx, gtv, "rowid")
            if (v < 0) {
                throw GtvRtUtils.errGtv(ctx, "rowid:negative:$v", "Negative value of rowid type: $v")
            }
            return get(v)
        }

        override fun toNative(value: Rt_RowidValue): Any = value.value

        override fun fromNative(value: Any?): Rt_RowidValue = get(value as Long)

        override fun toSqlValue(value: Rt_RowidValue): Any = value.value

        override fun toSql(value: Rt_RowidValue, params: PreparedStatementParams, idx: Int) =
            params.setLong(idx, value.value)

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getLong(idx)
            return Rt_SqlNull.check(v == 0L, row, name, nullable) ?: get(v)
        }
    }
}
