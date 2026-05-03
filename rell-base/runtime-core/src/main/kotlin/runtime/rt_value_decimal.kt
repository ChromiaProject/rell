/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvType
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.immSetOf
import java.math.BigDecimal
import kotlin.reflect.full.createType

@ConsistentCopyVisibility
data class Rt_DecimalValue private constructor(val value: BigDecimal): Rt_ValueBase() {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.DECIMAL

    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "dec[${str()}]"
    override fun str(format: Rt_StrFormat) = Lib_DecimalMath.toString(value)

    companion object:
        Rt_GtvCompatibleValueClass<Rt_DecimalValue>,
        Rt_NativeCompatibleValueClass<Rt_DecimalValue>,
        Rt_SqlCompatibleValueClass<Rt_DecimalValue> {

        override val name
            get() = "decimal"

        override val klass = Rt_DecimalValue::class
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.DECIMAL)
        override val nativeTypes = immSetOf(BigDecimal::class.createType())

        override val sqlType
            get() = Lib_DecimalMath.DECIMAL_SQL_TYPE

        override val comparator: Comparator<Rt_Value> =
            Comparator { a, b -> a.asDecimal().compareTo(b.asDecimal()) }

        val ZERO = Rt_DecimalValue(BigDecimal.ZERO)

        fun get(v: BigDecimal): Rt_DecimalValue =
            getOrNull(v) ?: throw errOverflow("decimal:overflow", "Decimal value out of range")

        fun getOrNull(v: BigDecimal): Rt_DecimalValue? {
            if (v.signum() == 0) {
                return ZERO
            }
            val t = Lib_DecimalMath.scale(v)
            return if (t == null) null else Rt_DecimalValue(t)
        }

        fun get(s: String): Rt_DecimalValue {
            val v = try {
                Lib_DecimalMath.parse(s)
            } catch (_: NumberFormatException) {
                throw Rt_Exception.common("decimal:invalid:$s", "Invalid decimal value: '$s'")
            }
            return get(v)
        }

        fun get(v: Long): Rt_DecimalValue = get(BigDecimal(v))

        internal fun errOverflow(code: String, msg: String): Rt_Exception {
            val p = Lib_DecimalMath.DECIMAL_INT_DIGITS
            return Rt_Exception.common(code, "$msg (allowed range is -10^$p..10^$p, exclusive)")
        }

        override fun toGtv(value: Rt_DecimalValue, pretty: Boolean): Gtv =
            GtvFactory.gtv(Lib_DecimalMath.toString(value.value))

        override fun toNative(value: Rt_DecimalValue): Any = value.value

        override fun fromNative(value: Any?): Rt_DecimalValue = get(value as BigDecimal)

        override fun toSqlValue(value: Rt_DecimalValue): Any = value.value

        override fun toSql(value: Rt_DecimalValue, params: PreparedStatementParams, idx: Int) =
            params.setBigDecimal(idx, value.value)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_DecimalValue = when {
            !ctx.strictGtvConversion && gtv.type == GtvType.INTEGER -> {
                val v = GtvRtUtils.gtvToInteger(ctx, gtv, "decimal")
                get(v)
            }

            !ctx.strictGtvConversion && ctx.bigIntegerSupport && gtv.type == GtvType.BIGINTEGER -> {
                val v = gtv.asBigInteger()
                val bd = BigDecimal(v)
                get(bd)
            }

            else -> {
                val s = GtvRtUtils.gtvToString(ctx, gtv, "decimal")
                get(s)
            }
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getBigDecimal(idx)
            return if (v != null) get(v) else Rt_SqlNull.check(name, nullable)
        }
    }
}
