/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.type.Lib_Type_Enum
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.doc.DocCode
import org.jooq.impl.SQLDataType

class R_EnumType(val enum: R_EnumDefinition): R_SimpleType(enum.appLevelName, enum.cDefName) {
    init {
        checkEquals(enum.type, null) // during initialization
    }

    val values: ImmList<Rt_Value> = enum.attrs.mapToImmList { Rt_EnumValue(this, it) }
    val valuesSet: ImmSet<Rt_Value> = values.toImmSet()

    fun getValueOrNull(index: Int): Rt_Value? {
        return values.getOrNull(index)
    }

    fun getValue(attr: R_EnumAttr): Rt_Value {
        val i = attr.value
        check(enum.attrs[i] === attr)
        return values[i]
    }

    override fun equals0(other: R_Type) = false
    override fun hashCode0() = System.identityHashCode(this)

    override fun comparator() = Rt_Comparator.create { it.asEnum().value }

    override fun fromCli(s: String): Rt_Value {
        val attr = enum.attr(s)
        requireNotNull(attr) { "$name: $s" }
        return values[attr.value]
    }

    override fun isDirectPure() = true

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Enum(enum)
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Enum(this)

    override fun toMetaGtv() = enum.appLevelName.toGtv()

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(enum.moduleLevelName),
        staticMembers = Lib_Type_Enum.getStaticMembers(this),
    )

    private class R_TypeSqlAdapter_Enum(private val type: R_EnumType): R_TypeSqlAdapter_Some(SQLDataType.INTEGER) {
        override fun toSqlValue(value: Rt_Value) = value.asEnum().value

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            params.setInt(idx, value.asEnum().value)
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getInt(idx)
            val res = checkSqlNull(v == 0, row, type, nullable)
            return if (res != null) res else {
                val value = type.getValueOrNull(v)
                requireNotNull(value) { "$type: $v" }
            }
        }

        override fun metaName(sqlCtx: Rt_SqlContext): String {
            return "enum:${type.name}"
        }
    }

    private class Rt_EnumValue(private val type: R_EnumType, private val attr: R_EnumAttr): Rt_Value() {
        override val valueType = Rt_CoreValueTypes.ENUM.type()

        override fun type() = type
        override fun asEnum() = attr
        override fun equals(other: Any?) = other is Rt_EnumValue && attr == other.attr
        override fun hashCode() = type.hashCode() * 31 + attr.value

        override fun str(format: StrFormat): String = attr.name
        override fun strCode(showTupleFieldNames: Boolean) = "${type.name}[${attr.name}]"
    }
}

private class GtvRtConversion_Enum(private val enum: R_EnumDefinition): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(fromGtv = true, toGtv = true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        val e = rt.asEnum()
        return if (pretty) {
            GtvString(e.name)
        } else {
            GtvInteger(e.value.toLong())
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val attr = if (ctx.pretty && gtv.type == GtvType.STRING) {
            val name = GtvRtUtils.gtvToString(ctx, gtv, enum.type)
            val attr = enum.attr(name)
            if (attr == null) {
                val code = "enum:bad_value:$name"
                throw GtvRtUtils.errGtvType(ctx, enum.type, code, "invalid value: '$name'")
            }
            attr
        } else {
            val value = GtvRtUtils.gtvToInteger(ctx, gtv, enum.type)
            val attr = enum.attr(value)
            if (attr == null) {
                val code = "enum:bad_value:$value"
                throw GtvRtUtils.errGtvType(ctx, enum.type, code, "invalid value: $value")
            }
            attr
        }
        return enum.type.getValue(attr)
    }
}
