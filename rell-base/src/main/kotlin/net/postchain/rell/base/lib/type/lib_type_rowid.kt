/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.utils.toImmList
import org.jooq.impl.SQLDataType
import java.sql.PreparedStatement
import java.sql.ResultSet

object Lib_Type_Rowid {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("rowid", rType = R_RowidType) {
            comment("The primary key of a database record, a 64-bit integer.")

            // Constructor to create a ROWID from an integer value
            constructor(pure = true) {
                comment("Constructs a ROWID from an integer value.")
                param("value", "integer", comment = "The integer value to be converted to ROWID.")
                body { value ->
                    val intValue = value.asInteger()
                    Rt_Utils.check(intValue >= 0) { "rowid(integer):negative:$intValue" toCodeMsg "Negative value: $intValue" }
                    Rt_RowidValue.get(intValue)
                }
            }

            // Method to get the integer value of the ROWID
            function("to_integer", result = "integer", pure = true) {
                comment("Returns the integer value of the ROWID.")
                dbFunctionTemplate("rowid.to_integer", 1, "#0")
                body { rowid ->
                    val v = rowid.asRowid()
                    Rt_IntValue.get(v)
                }
            }
        }
    }
}

object R_RowidType: R_PrimitiveType("rowid") {
    override fun defaultValue() = Rt_RowidValue.ZERO
    override fun comparator() = Rt_Comparator.create { it.asRowid() }
    override fun fromCli(s: String): Rt_Value = Rt_RowidValue.get(s.toLong())

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Rowid
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Rowid

    override fun getLibTypeDef() = Lib_Rell.ROWID_TYPE

    private object R_TypeSqlAdapter_Rowid: R_TypeSqlAdapter_Primitive("rowid", SQLDataType.BIGINT) {
        override fun toSqlValue(value: Rt_Value) = value.asRowid()

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            stmt.setLong(idx, value.asRowid())
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val v = rs.getLong(idx)
            return checkSqlNull(v == 0L, rs, R_RowidType, nullable) ?: Rt_RowidValue.get(v)
        }
    }
}

class Rt_RowidValue private constructor(val value: Long): Rt_Value() {
    init {
        check(value >= 0) { "Negative rowid value: $value" }
    }

    override val valueType = Rt_CoreValueTypes.ROWID.type()

    override fun type() = R_RowidType
    override fun asRowid() = value
    override fun toFormatArg() = value
    override fun strCode(showTupleFieldNames: Boolean) = "rowid[$value]"
    override fun str() = "" + value
    override fun equals(other: Any?) = other is Rt_RowidValue && value == other.value
    override fun hashCode() = java.lang.Long.hashCode(value)

    companion object {
        private val VALUES: List<Rt_Value> = (0 .. 1000).map { Rt_RowidValue(it.toLong()) }.toImmList()

        val ZERO = VALUES[0]

        fun get(value: Long): Rt_Value {
            return if (value >= 0 && value < VALUES.size) VALUES[value.toInt()] else Rt_RowidValue(value)
        }
    }
}

private object GtvRtConversion_Rowid: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asRowid())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val v = GtvRtUtils.gtvToInteger(ctx, gtv, R_RowidType)
        if (v < 0) {
            throw GtvRtUtils.errGtv(ctx, "rowid:negative:$v", "Negative value of $R_RowidType type: $v")
        }
        return ctx.rtValue {
            Rt_RowidValue.get(v)
        }
    }
}
