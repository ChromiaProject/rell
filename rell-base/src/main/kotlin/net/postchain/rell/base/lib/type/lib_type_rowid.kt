/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.ImmList
import net.postchain.rell.base.utils.mapToImmList
import org.jooq.impl.SQLDataType

object Lib_Type_Rowid {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("rowid", rType = R_RowidType, since = "0.9.0") {
            comment("""
                The primary key of a database record.

                Implemented as a 64-bit integer, but requires explicit conversion to and from integer with the
                constructor `rowid(integer)` and the method `rowid.to_integer()`. ROWID values cannot be negative.

                ROWID supports the standard complement of comparison operators (`==`, `!=`, `<`, `>`, `<=` and `>=`),
                and conversion to and from GTV.

                Examples:

                ```rell
                function get_rowid(username: text) {
                    val u = user @ { .name == username };
                    return u.rowid;
                }

                val freds_rowid: rowid = user @ { .name == "Fred" } ( .rowid );

                val valid_rowids: list<rowid> = user @* { .rowid >= min_rowid };
                ```

                Note that the recommended way to manipulate entity values is via typed references (e.g. `u: user` in
                the above example), as this is type-safe. Reliance on `rowid` is only recommended in rare cases where
                the standard pattern is not possible, as the compiler does not know what type of entity a given `rowid`
                value is intended to reference. Consider the example below:

                ```rell
                entity user {}
                entity company {}

                val u: user = user @ {};
                val c: company = company @ {};

                val u2: user = c; // Bad, and the compiler tells us so.

                val u_rowid: rowid = c.rowid; // Likely to lead to errors, but the compiler can't help us.
                ```

                @see 1. <a href="../integer/index.html"><code>integer</code> - Rell Standard Library</a>
                @see 2. <a href="../gtv/index.html"><code>gtv</code> - Rell Standard Library</a>
            """)

            // Constructor to create a ROWID from an integer value
            constructor(pure = true, since = "0.11.0") {
                comment("""
                    Construct a ROWID from an integer value.

                    @throws exception if `value` is negative
                """)
                param("value", "integer", comment = "the row ID integer value")
                body { value ->
                    val intValue = value.asInteger()
                    Rt_Utils.check(intValue >= 0) { "rowid(integer):negative:$intValue" toCodeMsg "Negative value: $intValue" }
                    Rt_RowidValue.get(intValue)
                }
            }

            // Method to get the integer value of the ROWID
            function("to_integer", result = "integer", pure = true, since = "0.11.0") {
                comment("Get the integer value of this ROWID.")
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

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            params.setLong(idx, value.asRowid())
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getLong(idx)
            return checkSqlNull(v == 0L, row, R_RowidType, nullable) ?: Rt_RowidValue.get(v)
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
    override fun str(format: StrFormat) = "" + value
    override fun equals(other: Any?) = other is Rt_RowidValue && value == other.value
    override fun hashCode() = java.lang.Long.hashCode(value)

    companion object {
        private val VALUES: ImmList<Rt_Value> = (0 .. 1000).mapToImmList { Rt_RowidValue(it.toLong()) }

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
        return Rt_RowidValue.get(v)
    }
}
