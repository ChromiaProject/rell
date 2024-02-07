/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvString
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import org.jooq.SQLDialect
import org.jooq.impl.DefaultDataType
import org.postgresql.util.PGobject
import java.sql.PreparedStatement
import java.sql.ResultSet

object Lib_Type_Json {
    val NAMESPACE = Ld_NamespaceDsl.make {
        type("json", rType = R_JsonType) {
            constructor(pure = true) {
                param("value", type = "text")
                dbFunctionCast("json", "JSONB")
                body { a ->
                    val s = a.asString()
                    val r = try {
                        Rt_JsonValue.parse(s)
                    } catch (e: IllegalArgumentException) {
                        throw Rt_Exception.common("fn_json_badstr", "Bad JSON: $s")
                    }
                    r
                }
            }

            function("to_text", result = "text", pure = true) {
                alias("str")
                dbFunctionCast("json.to_text", "TEXT")
                body { a ->
                    val s = a.asJsonString()
                    Rt_TextValue.get(s)
                }
            }
        }
    }
}

private val JSON_SQL_DATA_TYPE = DefaultDataType(null as SQLDialect?, String::class.java, "jsonb")

object R_JsonType: R_PrimitiveType("json") {
    override fun comparator() = Rt_Comparator.create { it.asJsonString() }
    override fun fromCli(s: String): Rt_Value = Rt_JsonValue.parse(s)

    //TODO consider converting between Rt_JsonValue and arbitrary Gtv, not only String
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Json

    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Json

    override fun getLibTypeDef() = Lib_Rell.JSON_TYPE

    private object R_TypeSqlAdapter_Json: R_TypeSqlAdapter_Primitive("json", JSON_SQL_DATA_TYPE) {
        override fun toSqlValue(value: Rt_Value): Any {
            val str = value.asJsonString()
            val obj = PGobject()
            obj.type = "json"
            obj.value = str
            return obj
        }

        override fun toSql(stmt: PreparedStatement, idx: Int, value: Rt_Value) {
            val obj = toSqlValue(value)
            stmt.setObject(idx, obj)
        }

        override fun fromSql(rs: ResultSet, idx: Int, nullable: Boolean): Rt_Value {
            val str = rs.getString(idx)
            return checkSqlNull(str, R_JsonType, nullable) ?: Rt_JsonValue.parse(str)
        }
    }
}

class Rt_JsonValue private constructor(private val str: String): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.JSON.type()

    override fun type() = R_JsonType
    override fun asJsonString() = str
    override fun toFormatArg() = str
    override fun str() = str
    override fun strCode(showTupleFieldNames: Boolean) = "json[$str]"
    override fun equals(other: Any?) = other === this || (other is Rt_JsonValue && str == other.str)
    override fun hashCode() = str.hashCode()

    companion object {
        fun parse(s: String): Rt_Value {
            if (s.isBlank()) {
                throw IllegalArgumentException(s)
            }

            val mapper = ObjectMapper()

            val json = try {
                mapper.readTree(s)
            } catch (e: JsonProcessingException) {
                throw IllegalArgumentException(s)
            }

            if (json == null) {
                throw IllegalArgumentException(s)
            }

            val str = json.toString()
            return Rt_JsonValue(str)
        }
    }
}

private object GtvRtConversion_Json: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvString(rt.asJsonString())
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv) = GtvRtUtils.gtvToJson(ctx, gtv, R_JsonType)
}
