/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter
import net.postchain.rell.base.compiler.base.core.C_TypeAdapter_Nullable
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.compiler.base.utils.C_FeatureSwitch
import net.postchain.rell.base.mtype.M_Types
import net.postchain.rell.base.runtime.GtvRtConversion
import net.postchain.rell.base.runtime.GtvToRtContext
import net.postchain.rell.base.runtime.Rt_SqlContext
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.runtime.utils.Rt_Utils
import net.postchain.rell.base.runtime.utils.toGtv
import java.sql.PreparedStatement
import java.sql.ResultSet

class R_NullableType(val valueType: R_Type): R_Type(calcName(valueType)) {
    init {
        check(valueType != R_NullType)
        check(valueType !is R_NullableType)
    }

    override fun equals0(other: R_Type) = other is R_NullableType && valueType == other.valueType
    override fun hashCode0() = valueType.hashCode()
    override fun explicitComponentTypes() = listOf(valueType)

    override fun isReference() = valueType.isReference()
    override fun isError() = valueType.isError()
    override fun isDirectMutable() = false
    override fun isDirectPure() = true

    override fun defaultValue() = Rt_NullValue
    override fun comparator() = valueType.comparator()
    override fun fromCli(s: String): Rt_Value = if (s == "null") Rt_NullValue else valueType.fromCli(s)
    override fun strCode() = name
    override fun getLibType0() = C_LibType.make(M_Types.nullable(valueType.mType))

    override fun isAssignableFrom(type: R_Type): Boolean {
        return type == this
                || type == R_NullType
                || (type is R_NullableType && valueType.isAssignableFrom(type.valueType))
                || valueType.isAssignableFrom(type)
    }

    override fun getTypeAdapter(sourceType: R_Type): C_TypeAdapter? {
        var adapter = super.getTypeAdapter(sourceType)
        if (adapter != null) {
            return adapter
        }

        if (sourceType is R_NullableType) {
            adapter = valueType.getTypeAdapter(sourceType.valueType)
            return if (adapter == null) null else C_TypeAdapter_Nullable(this, adapter)
        } else {
            return valueType.getTypeAdapter(sourceType)
        }
    }

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Nullable(this)
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Nullable()

    override fun toMetaGtv() = mapOf(
            "type" to "nullable".toGtv(),
            "value" to valueType.toMetaGtv()
    ).toGtv()

    private inner class R_TypeSqlAdapter_Nullable: R_TypeSqlAdapter_Some(null) {
        override fun isSqlCompatible(compilerOptions: C_CompilerOptions): Boolean {
            val enabled = SQL_COMPATIBILITY_SWITCH.isActive(compilerOptions)
            return enabled && valueType.sqlAdapter.isSqlCompatible(compilerOptions)
        }

        override fun isAllowedForEntityAttributes(compilerOptions: C_CompilerOptions) = false

        override fun metaName(sqlCtx: Rt_SqlContext) = throw Rt_Utils.errNotSupported("Nullable entity attributes are not supported")

        override fun toSqlValue(value: Rt_Value) = valueType.sqlAdapter.toSqlValue(value)

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            stmt.setBoolean(idx, value.asBoolean())
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            return valueType.sqlAdapter.fromSql(rs, idx, true)
        }
    }

    companion object {
        private val SQL_COMPATIBILITY_SWITCH = C_FeatureSwitch("0.13.10")

        private fun calcName(valueType: R_Type): String {
            return when (valueType) {
                is R_FunctionType -> "(${valueType.name})?"
                else -> "${valueType.name}?"
            }
        }
    }
}

private class GtvRtConversion_Nullable(val type: R_NullableType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean): Gtv {
        return if (rt == Rt_NullValue) {
            GtvNull
        } else {
            type.valueType.rtToGtv(rt, pretty)
        }
    }

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        return if (gtv.isNull()) {
            Rt_NullValue
        } else {
            type.valueType.gtvToRt(ctx, gtv)
        }
    }
}
