/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvInteger
import net.postchain.rell.base.compiler.base.lib.C_LibType
import net.postchain.rell.base.lib.type.Lib_Type_Entity
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.runtime.utils.toGtv
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.utils.checkEquals
import net.postchain.rell.base.utils.doc.DocCode
import org.jooq.impl.SQLDataType
import java.util.*

class R_EntityType(val rEntity: R_EntityDefinition): R_Type(rEntity.appLevelName, rEntity.cDefName) {
    init {
        checkEquals(rEntity.type, null) // during initialization
    }

    override fun equals0(other: R_Type): Boolean = other is R_EntityType && other.rEntity == rEntity
    override fun hashCode0(): Int = rEntity.hashCode()

    override fun comparator() = Rt_Comparator.create { it.asObjectId() }
    override fun fromCli(s: String): Rt_Value = Rt_EntityValue(this, s.toLong())
    override fun strCode(): String = name

    override fun isDirectPure() = false
    override fun isCacheable() = true

    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Entity(this)
    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Entity(this)

    override fun toMetaGtv() = rEntity.appLevelName.toGtv()

    override fun getLibType0() = C_LibType.make(
        this,
        DocCode.link(rEntity.moduleLevelName),
        valueMembers = lazy { Lib_Type_Entity.getValueMembers(this) },
    )

    private class R_TypeSqlAdapter_Entity(private val type: R_EntityType): R_TypeSqlAdapter_Some(SQLDataType.BIGINT) {
        override fun toSqlValue(value: Rt_Value) = value.asObjectId()

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            params.setLong(idx, value.asObjectId())
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getLong(idx)
            return checkSqlNull(v == 0L, row, type, nullable) ?: Rt_EntityValue(type, v)
        }

        override fun metaName(sqlCtx: Rt_SqlContext): String {
            val rEntity = type.rEntity
            val chain = sqlCtx.chainMapping(rEntity.external?.chain)
            val metaName = rEntity.metaName
            return "class:${chain.chainId}:$metaName"
        }
    }
}

class Rt_EntityValue(val type: R_EntityType, val rowid: Long): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.ENTITY.type()

    override fun type() = type
    override fun asObjectId() = rowid
    override fun toFormatArg() = str()
    override fun strCode(showTupleFieldNames: Boolean) = "${type.name}[$rowid]"
    override fun str(format: StrFormat) = strCode()
    override fun equals(other: Any?) = other === this || (other is Rt_EntityValue && type == other.type && rowid == other.rowid)
    override fun hashCode() = Objects.hash(type, rowid)
}

private class GtvRtConversion_Entity(val type: R_EntityType): GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(type.rEntity.flags.gtv, type.rEntity.flags.gtv)

    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvInteger(rt.asObjectId())

    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv): Rt_Value {
        val rowid = GtvRtUtils.gtvToInteger(ctx, gtv, type)
        ctx.trackRecord(type.rEntity, rowid)
        return ctx.rtValue {
            Rt_EntityValue(type, rowid)
        }
    }
}
