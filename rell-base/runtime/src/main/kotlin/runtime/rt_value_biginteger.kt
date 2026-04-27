/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.base.lib.type.Lib_BigIntegerMath
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.immSetOf
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.full.createType

@ConsistentCopyVisibility
data class Rt_BigIntegerValue private constructor(val value: BigInteger): Rt_ValueBase() {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.BIG_INTEGER

    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "bigint[${str()}]"
    override fun str(format: Rt_StrFormat): String = value.toString()

    companion object:
        Rt_GtvCompatibleValueClass<Rt_BigIntegerValue>,
        Rt_NativeCompatibleValueClass<Rt_BigIntegerValue>,
        Rt_SqlCompatibleValueClass<Rt_BigIntegerValue> {
        override val name
            get() = "big_integer"

        override val klass = Rt_BigIntegerValue::class
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.BIG_INTEGER)
        override val nativeTypes = immSetOf(BigInteger::class.createType())

        override val sqlType
            get() = Lib_BigIntegerMath.SQL_TYPE

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> a.asBigInteger().compareTo(b.asBigInteger()) }

        val ZERO = Rt_BigIntegerValue(BigInteger.ZERO)

        fun get(v: BigInteger): Rt_BigIntegerValue {
            if (v.signum() == 0) {
                return ZERO
            }

            val res = getOrNull(v)
            if (res != null) {
                return res
            }

            val p = Lib_BigIntegerMath.PRECISION
            val msg = "Big integer value out of range (allowed range is -10^$p..10^$p, exclusive)"
            throw Rt_Exception.common("bigint:overflow", msg)
        }

        fun getOrNull(v: BigInteger): Rt_BigIntegerValue? =
            if (v < Lib_BigIntegerMath.MIN_VALUE || v > Lib_BigIntegerMath.MAX_VALUE) {
                null
            } else {
                Rt_BigIntegerValue(v)
            }

        fun get(v: BigDecimal): Rt_BigIntegerValue {
            val bigInt = try {
                v.toBigIntegerExact()
            } catch (_: ArithmeticException) {
                throw Rt_Exception.common("bigint:nonint:$v", "Value is not an integer: '$v'")
            }
            return get(bigInt)
        }

        fun get(s: String): Rt_BigIntegerValue {
            val v = try {
                BigInteger(s)
            } catch (_: NumberFormatException) {
                throw Rt_Exception.common("bigint:invalid:$s", "Invalid big integer value: '$s'")
            }
            return get(v)
        }

        fun get(v: Long): Rt_BigIntegerValue = get(BigInteger.valueOf(v))

        override fun toGtv(value: Rt_BigIntegerValue, pretty: Boolean): Gtv = GtvFactory.gtv(value.value)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_BigIntegerValue =
            get(GtvRtUtils.gtvToBigInteger(ctx, gtv, "big_integer"))

        override fun toNative(value: Rt_BigIntegerValue): Any = value.value
        override fun fromNative(value: Any?): Rt_BigIntegerValue = get(value as BigInteger)
        override fun toSqlValue(value: Rt_BigIntegerValue): Any = value.value

        override fun toSql(value: Rt_BigIntegerValue, params: PreparedStatementParams, idx: Int) =
            params.setBigDecimal(idx, BigDecimal(value.value))

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            return get(row.getBigDecimal(idx) ?: return Rt_SqlNull.check(name, nullable))
        }
    }
}
