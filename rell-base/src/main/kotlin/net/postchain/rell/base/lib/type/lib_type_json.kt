/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
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
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import org.jooq.SQLDialect
import org.jooq.impl.DefaultDataType
import org.postgresql.util.PGobject

object Lib_Type_Json {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type("json", rType = R_JsonType, since = SINCE0) {
            comment("""
                A JSON (JavaScript Object Notation) datatype.

                JSON values are created from text and support conversion to GTV via `gtv.from_json()`. They can be
                stored in the database as an entity attribute.

                JSON values in Rell are represented internally with text, which has been validated as legal JSON.

                @see 1. <a href="../gtv/index.html"><code>gtv</code> - Rell Standard Library</a>
                @see 2. <a href="../gtv/from_json.html"><code>gtv.from_json()</code> - Rell Standard Library</a>
            """)

            constructor(pure = true, since = SINCE0) {
                comment("""
                    Construct a JSON value from text.

                    @throws exception if `text` is not valid JSON
                """)
                param("value", type = "text", comment = "the JSON text to decode")
                dbFunctionCast("json", "JSONB")
                body { value ->
                    val jsonString = value.asString()
                    val jsonValue = try {
                        Rt_JsonValue.parse(jsonString)
                    } catch (e: IllegalArgumentException) {
                        throw Rt_Exception.common("fn_json_badstr", "Bad JSON: $jsonString")
                    }
                    jsonValue
                }
            }

            function("to_text", result = "text", pure = true, since = "0.9.0") {
                comment("Convert this JSON value to text.")
                alias("str", since = SINCE0)
                dbFunctionCast("json.to_text", "TEXT")
                body { json ->
                    val jsonString = json.asJsonString()
                    Rt_TextValue.get(jsonString)
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

        override fun toSql(params: PreparedStatementParams, idx: Int, value: Rt_Value) {
            val obj = toSqlValue(value)
            params.setObject(idx, obj)
        }

        override fun fromSql(row: ResultSetRow, idx: Int, nullable: Boolean): Rt_Value {
            val v = row.getString(idx)
            return if (v != null) Rt_JsonValue.parse(v) else checkSqlNull(R_JsonType, nullable)
        }
    }
}

class Rt_JsonValue private constructor(private val str: String): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.JSON.type()

    override fun type() = R_JsonType
    override fun asJsonString() = str
    override fun toFormatArg() = str
    override fun str(format: StrFormat) = str
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
