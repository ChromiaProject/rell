/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvString
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.postgresql.util.PGobject

class Rt_JsonValue(val node: JsonNode): Rt_Value {
    override val name
        get() = Companion.name

    override val type
        get() = Rt_PrimitiveTypes.JSON

    override fun str(format: Rt_StrFormat) = str
    override fun strCode(showTupleFieldNames: Boolean) = "json[$str]"
    override fun equals(other: Any?) = other === this || (other is Rt_JsonValue && str == other.str)
    override fun hashCode() = str.hashCode()

    val str by lazy { node.toString() }

    companion object:
        Rt_GtvCompatibleValueClass<Rt_JsonValue>,
        Rt_SqlCompatibleValueClass<Rt_JsonValue> {

        override val name
            get() = "json"
        override val rrType: RR_Type = RR_Type.Primitive(RR_PrimitiveKind.JSON)

        override val sqlType: DataType<JSONB>
            get() = SQLDataType.JSONB

        // https://stackoverflow.com/questions/3907929/should-i-declare-jacksons-objectmapper-as-a-static-field
        // `by lazy` (rather than eager `= ObjectMapper()`) keeps Jackson's static-init cascade
        // out of the class-load path, which is needed for TeaVM compat
        private val mapper by lazy { ObjectMapper() }

        fun parse(s: String): Rt_JsonValue {
            require(!s.isBlank()) { s }

            val json: JsonNode? = try {
                mapper.readTree(s)
            } catch (e: JsonProcessingException) {
                throw IllegalArgumentException(s, e)
            }

            requireNotNull(json) { s }
            return Rt_JsonValue(json)
        }

        override fun toGtv(value: Rt_JsonValue, pretty: Boolean): Gtv = GtvString(value.str)

        override fun fromGtv(ctx: GtvToRtContext, gtv: Gtv): Rt_JsonValue =
            cast(GtvRtUtils.gtvToJson(ctx, gtv, "json"))

        override fun toSqlValue(value: Rt_JsonValue): Any = PGobject().apply {
            type = "json"
            this.value = value.str
        }

        override fun toSql(value: Rt_JsonValue, params: PreparedStatementParams, idx: Int) =
            params.setObject(idx, toSqlValue(value))

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getString(idx)
            return if (v != null) parse(v) else Rt_SqlNull.check(name, nullable)
        }
    }
}
