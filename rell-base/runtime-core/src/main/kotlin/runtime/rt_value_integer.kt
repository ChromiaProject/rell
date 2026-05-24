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
import java.math.BigInteger
import kotlin.reflect.full.createType

@ConsistentCopyVisibility
@JvmRecord
data class Rt_IntValue private constructor(val value: Long): Rt_Value {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.INTEGER

    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "int[$value]"
    override fun str(format: Rt_StrFormat) = "" + value

    companion object:
        Rt_GtvCompatibleValueClass<Rt_IntValue>,
        Rt_NativeCompatibleValueClass<Rt_IntValue>,
        Rt_SqlCompatibleValueClass<Rt_IntValue>,
        Rt_PrimitiveFactory<Rt_IntValue, Long> {

        override val name
            get() = "integer"
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.INTEGER)
        override val nativeTypes by lazy { immSetOf(Long::class.createType()) }

        override val sqlType: DataType<Long>
            get() = SQLDataType.BIGINT

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> (a as Rt_IntValue).value.compareTo((b as Rt_IntValue).value) }

        const val MAX_VALUE = Long.MAX_VALUE
        const val MIN_VALUE = Long.MIN_VALUE
        val MAX_VALUE_AS_BIGINT: BigInteger = BigInteger.valueOf(MAX_VALUE)
        val MIN_VALUE_AS_BIGINT: BigInteger = BigInteger.valueOf(MIN_VALUE)

        private const val CACHE_RANGE = 1000
        private val CACHE: Array<Rt_IntValue> = Array(2 * CACHE_RANGE + 1) { Rt_IntValue((it - CACHE_RANGE).toLong()) }

        val ZERO: Rt_IntValue = CACHE[CACHE_RANGE]

        @JvmStatic
        fun get(v: Long): Rt_IntValue = if (v >= -CACHE_RANGE && v <= CACHE_RANGE) {
            CACHE[(v + CACHE_RANGE).toInt()]
        } else {
            Rt_IntValue(v)
        }

        override fun wrap(value: Long): Rt_IntValue = get(value)

        override fun toGtv(value: Rt_IntValue, pretty: Boolean): Gtv = GtvInteger(value.value)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_IntValue =
            get(GtvRtUtils.gtvToInteger(ctx, gtv, "integer"))

        override fun toNative(value: Rt_IntValue): Any = value.value

        override fun fromNative(value: Any?): Rt_IntValue = get(value as Long)

        override fun toSqlValue(value: Rt_IntValue): Any = value.value

        override fun toSql(value: Rt_IntValue, params: PreparedStatementParams, idx: Int) =
            params.setLong(idx, value.value)

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getLong(idx)
            return Rt_SqlNull.check(v == 0L, row, name, nullable) ?: get(v)
        }
    }
}
