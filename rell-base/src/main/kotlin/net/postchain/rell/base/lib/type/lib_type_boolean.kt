/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.immSetOf
import org.jooq.impl.SQLDataType

object Lib_Type_Boolean {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("boolean", rType = R_BooleanType, since = "0.6.0") {
            comment("A simple data type that represents two values: true and false.")
        }
    }
}

object R_BooleanType: R_PrimitiveType("boolean") {
    override fun defaultValue() = Rt_BooleanValue.FALSE
    override fun comparator() = Rt_Comparator.create { it.asBoolean() }

    override fun fromCli(s: String): Rt_Value {
        return when (s) {
            "false" -> Rt_BooleanValue.FALSE
            "true" -> Rt_BooleanValue.TRUE
            else -> throw IllegalArgumentException(s)
        }
    }

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Boolean
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Boolean

    override fun getLibTypeDef() = Lib_Rell.BOOLEAN_TYPE

    private object R_TypeSqlAdapter_Boolean: R_TypeSqlAdapter_Primitive("boolean", SQLDataType.BOOLEAN) {
        override fun toSqlValue(value: Rt_Value) = value.asBoolean()

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            params.setBoolean(idx, value.asBoolean())
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getBoolean(idx)
            return checkSqlNull(!v, row, R_BooleanType, nullable) ?: Rt_BooleanValue.get(v)
        }
    }
}

class Rt_BooleanValue private constructor(val value: Boolean): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.BOOLEAN.type()

    private val strCode = "boolean[$value]"

    override fun type() = R_BooleanType
    override fun asBoolean() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = strCode
    override fun str(format: StrFormat) = if (value) "true" else "false"
    override fun equals(other: Any?) = other is Rt_BooleanValue && value == other.value
    override fun hashCode() = java.lang.Boolean.hashCode(value)

    companion object {
        val TRUE: Rt_Value = Rt_BooleanValue(true)
        val FALSE: Rt_Value = Rt_BooleanValue(false)

        val ALL_VALUES: Set<Rt_Value> = immSetOf(FALSE, TRUE)

        fun get(value: Boolean): Rt_Value {
            return if (value) TRUE else FALSE
        }
    }
}

private object GtvRtConversion_Boolean: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(if (rt.asBoolean()) 1L else 0L)

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToBoolean(ctx, gtv, R_BooleanType)
        return Rt_BooleanValue.get(v)
    }
}
