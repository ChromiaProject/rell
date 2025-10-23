/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lib.type

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvString
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_FunctionDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.R_GtvCompatibility
import net.postchain.rell.base.model.R_PrimitiveType
import net.postchain.rell.base.model.R_TypeSqlAdapter
import net.postchain.rell.base.model.R_TypeSqlAdapter_Primitive
import net.postchain.rell.base.model.Rt_NullValue
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.runtime.utils.Rt_Comparator
import net.postchain.rell.base.sql.PreparedStatementParams
import net.postchain.rell.base.sql.ResultSetRow
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.RellVersions.SINCE_NOW
import net.postchain.rell.base.utils.toIntExact
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
                comment("""
                    Convert this JSON value to text.

                    Note that this method is different to `json.as_text()`. This method converts this entire JSON value
                    to its text representation, regardless of what type of JSON value this is, and does not throw an
                    exception. `json.as_text()` retrieves this text-typed JSON value as Rell text, and throws an
                    exception if this JSON value is not of text type.
                """)
                alias("str", since = SINCE0)
                dbFunctionCast("json.to_text", "TEXT")
                body { json -> Rt_TextValue.get(json.asJson().str) }
            }

            function("get", result = "json", pure = true, since = SINCE_NOW) {
                comment("""
                    Get the element at the specified index of this JSON array.

                    `json_array.get(index)` is equivalent to `json_array[index]`.
                    @throws exception if this JSON value is not an array
                    @throws exception if the specified index is out of bounds
                """)
                param("index", type = "integer", comment = "the array index")
                dbFunctionSimple("json.get", SqlConstants.FN_JSON_ARRAY_GET)
                body { array, index ->
                    val arrayValue = array.asJson().node
                    val indexValue = index.asInteger().toIntExact()
                    when (val result = JsonUtils.arrayGet(arrayValue, indexValue, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        is JsonUtils.Failure -> throw Rt_Exception.common(result.codeMsg.code, result.codeMsg.msg)
                    }
                }
            }

            function("get", result = "json", pure = true, since = SINCE_NOW) {
                comment("""
                    Get the member with the specified key in this JSON object.

                    `json_object.get(key)` is equivalent to `json_object[key]`.
                    @throws exception if this JSON value is not an object
                    @throws exception if the specified key is not found in this object
                """)
                param("key", type = "text", comment = "the object key")
                dbFunctionSimple("json.get", SqlConstants.FN_JSON_OBJECT_GET)
                body { obj, key ->
                    val objValue = obj.asJson().node
                    val keyValue = key.asString()
                    when (val result = JsonUtils.objectGet(objValue, keyValue, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        is JsonUtils.Failure -> throw Rt_Exception.common(result.codeMsg.code, result.codeMsg.msg)
                    }
                }
            }

            function("get_or_null", result = "json?", pure = true, since = SINCE_NOW) {
                comment("""
                    Get the element at the specified index of this JSON array, or null if this JSON value is not an
                    array, or if the specified index is out of bounds.
                """)
                param("index", type = "integer", comment = "the array index")
                dbFunctionSimple("json.get_or_null", SqlConstants.FN_JSON_ARRAY_GET_OR_NULL)
                body { array, index ->
                    val arrayValue = array.asJson().node
                    val indexValue = index.asInteger().toIntExact()
                    when (val result = JsonUtils.arrayGet(arrayValue, indexValue, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        else -> Rt_NullValue
                    }
                }
            }

            function("get_or_null", result = "json?", pure = true, since = SINCE_NOW) {
                comment("""
                    Get the member with the specified key in this JSON object, or null if this JSON value is not an
                    object, or if the specified key is not found in this object.
                """)
                param("key", type = "text", comment = "the object key")
                dbFunctionTemplate("json.get_or_null", 2, "(#0 -> #1)")
                body { obj, key ->
                    val objValue = obj.asJson().node
                    val keyValue = key.asString()
                    when (val result = JsonUtils.objectGet(objValue, keyValue, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        else -> Rt_NullValue
                    }
                }
            }

            function("as_integer", result = "integer", pure = true, since = SINCE_NOW) {
                comment("""
                    Convert this JSON integer value to an integer.
                    @throws exception if this JSON value is not an integer
                """)
                dbFunctionSimple("json.as_integer", SqlConstants.FN_JSON_AS_INTEGER)
                asTypeBody(this, JsonUtils::canBeRellInteger) { Rt_IntValue.get(it.asLong()) }
            }

            function("as_boolean", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Convert this JSON boolean value to a boolean.
                    @throws exception if this JSON value is not a boolean
                """)
                dbFunctionCast("json.as_boolean", "BOOLEAN")
                asTypeBody(this, JsonNode::isBoolean) { Rt_BooleanValue.get(it.asBoolean()) }
            }

            function("as_text", result = "text", pure = true, since = SINCE_NOW) {
                comment("""
                    Convert this JSON text value to text.

                    Note that this method is different to `json.to_text()`. This method retrieves this text-typed JSON
                    value as Rell text, and throws an exception if this JSON value is not of text type. `json.to_text()`
                    converts this entire JSON value to its text representation, regardless of what type of JSON value
                    this is, and does not throw an exception.
                    @throws exception if this JSON value is not text
                """)
                dbFunctionSimple("json.as_text", SqlConstants.FN_JSON_AS_TEXT)
                asTypeBody(this, JsonNode::isTextual) { Rt_TextValue.get(it.asText()) }
            }

            function("as_integer_or_null", result = "integer?", pure = true, since = SINCE_NOW) {
                comment("""
                    Convert this JSON integer value to an integer.
                    @return this JSON integer as an integer, or null if this JSON value is not an integer
                """)
                dbFunctionSimple("json.as_integer_or_null", SqlConstants.FN_JSON_AS_INTEGER_OR_NULL)
                asTypeBody(this, JsonUtils::canBeRellInteger, null_on_error = true) { Rt_IntValue.get(it.asLong()) }
            }

            function("as_boolean_or_null", result = "boolean?", pure = true, since = SINCE_NOW) {
                comment("""
                    Convert this JSON boolean value to a boolean.
                    @return this JSON boolean as a boolean, or null if this JSON value is not a boolean
                """)
                dbFunctionSimple("json.as_boolean_or_null", SqlConstants.FN_JSON_AS_BOOLEAN_OR_NULL)
                asTypeBody(this, JsonNode::isBoolean, null_on_error = true) { Rt_BooleanValue.get(it.asBoolean()) }
            }

            function("as_text_or_null", result = "text?", pure = true, since = SINCE_NOW) {
                comment("""
                    Convert this JSON text value to text.
                    @return this JSON text as text, or null if this JSON value is not text
                """)
                dbFunctionSimple("json.as_text_or_null", SqlConstants.FN_JSON_AS_TEXT_OR_NULL)
                asTypeBody(this, JsonNode::isTextual, null_on_error = true) { Rt_TextValue.get(it.asText()) }
            }

            function("is_object", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Determine whether this JSON value is an object.
                    @return true if this JSON value is an object, false otherwise
                """)
                dbFunctionTemplate("json.is_object", 1, "(JSONB_TYPEOF(#0) = 'object')")
                isTypeBody(this, JsonNode::isObject)
            }

            function("is_array", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Determine whether this JSON value is an array.
                    @return true if this JSON value is an array, false otherwise
                """)
                dbFunctionTemplate("json.is_array", 1, "(JSONB_TYPEOF(#0) = 'array')")
                isTypeBody(this, JsonNode::isArray)
            }

            function("is_text", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Determine whether this JSON value is text.
                    @return true if this JSON value is text, false otherwise
                """)
                dbFunctionTemplate("json.is_text", 1, "(JSONB_TYPEOF(#0) = 'string')")
                isTypeBody(this, JsonNode::isTextual)
            }

            function("is_null", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Determine whether this JSON value is null.
                    @return true if this JSON value is null, false otherwise
                """)
                dbFunctionTemplate("json.is_null", 1, "(JSONB_TYPEOF(#0) = 'null')")
                isTypeBody(this, JsonNode::isNull)
            }

            function("is_integer", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Determine whether this JSON value is an integer.
                    @return true if this JSON value is an integer, false otherwise
                """)
                dbFunctionTemplate("json.is_integer", 1, "(${SqlConstants.FN_JSON_AS_INTEGER_OR_NULL}(#0) IS NOT NULL)")
                isTypeBody(this, JsonUtils::canBeRellInteger)
            }

            function("is_boolean", result = "boolean", pure = true, since = SINCE_NOW) {
                comment("""
                    Determine whether this JSON value is a boolean.
                    @return true if this JSON value is a boolean, false otherwise
                """)
                dbFunctionTemplate("json.is_boolean", 1, "(JSONB_TYPEOF(#0) = 'boolean')")
                isTypeBody(this, JsonNode::isBoolean)
            }

            function("size", result = "integer", pure = true, since = SINCE_NOW) {
                comment("""
                    Get the size of this JSON value; i.e. the number of elements in this JSON array, or the number of
                    key-value pairs in this JSON object.
                    @return the size of this JSON value
                    @throws exception if this JSON value is not an array or an object
                """)
                dbFunctionSimple("json.size", SqlConstants.FN_JSON_SIZE)
                body { json ->
                    val jsonNode = json.asJson().node
                    if (jsonNode.isContainerNode) {
                        Rt_IntValue.get(jsonNode.size().toLong())
                    } else {
                        val nodeTypeName = jsonNode.nodeType.name
                        val msg = "Tried to get the size of a JSON value that was not an object or array (got $nodeTypeName)."
                        throw Rt_Exception.common("json.$fnSimpleName:type_error:$nodeTypeName", msg)
                    }
                }
            }

            function("keys", result = "set<text>", pure = true, since = SINCE_NOW) {
                comment("""
                    Get the keys of this JSON object.
                    @throws exception if this JSON value is not an object
                """)
                body { json ->
                    val jsonNode = json.asJson().node
                    if (jsonNode.isObject) {
                        val set: MutableSet<Rt_Value> = mutableSetOf()
                        for (name in jsonNode.fieldNames()) {
                            set.add(Rt_TextValue.get(name))
                        }
                        Rt_SetValue(R_SetType(R_TextType), set)
                    } else {
                        val nodeTypeName = jsonNode.nodeType.name
                        throw Rt_Exception.common("json.$fnSimpleName:type_error:$nodeTypeName",
                            "Tried to get the keys of a JSON value that was not an object (got $nodeTypeName).")
                    }
                }
            }
        }
    }
}

private fun asTypeBody(
    m: Ld_FunctionDsl,
    checkType: (JsonNode) -> Boolean,
    null_on_error: Boolean = false,
    getType: (JsonNode) -> Rt_Value,
): Ld_BodyResult = with(m) {
    body { json ->
        val jsonValue = json.asJson().node
        when {
            checkType(jsonValue) -> getType(jsonValue)
            null_on_error -> Rt_NullValue
            else -> {
                val nodeTypeName = jsonValue.nodeType.name
                throw Rt_Exception.common(
                    "json.${m.fnSimpleName}:type_error:$nodeTypeName",
                    "JSON ${m.fnSimpleName} failed because the JSON value was a different type (got $nodeTypeName)."
                )
            }
        }
    }
}

private fun isTypeBody(m: Ld_FunctionDsl, checkType: (JsonNode) -> Boolean): Ld_BodyResult = with(m) {
    body { json -> Rt_BooleanValue.get(checkType(json.asJson().node)) }
}

private val JSON_SQL_DATA_TYPE = DefaultDataType(null as SQLDialect?, String::class.java, "jsonb")

object R_JsonType: R_PrimitiveType("json") {
    override fun comparator() = Rt_Comparator.create { it.asJson().str }
    override fun fromCli(s: String): Rt_Value = Rt_JsonValue.parse(s)

    //TODO consider converting between Rt_JsonValue and arbitrary Gtv, not only String
    override fun createGtvConversion(): GtvRtConversion = GtvRtConversion_Json

    override fun createSqlAdapter(): R_TypeSqlAdapter = R_TypeSqlAdapter_Json

    override fun getLibTypeDef() = Lib_Rell.JSON_TYPE

    private object R_TypeSqlAdapter_Json: R_TypeSqlAdapter_Primitive("json", JSON_SQL_DATA_TYPE) {
        override fun toSqlValue(value: Rt_Value): Any {
            val str = value.asJson().str
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

class Rt_JsonValue internal constructor(internal val node: JsonNode): Rt_Value() {
    override val valueType = Rt_CoreValueTypes.JSON.type()

    override fun type() = R_JsonType
    override fun asJson() = this
    override fun str(format: StrFormat) = str
    override fun strCode(showTupleFieldNames: Boolean) = "json[$str]"
    override fun equals(other: Any?) = other === this || (other is Rt_JsonValue && str == other.str)
    override fun hashCode() = str.hashCode()

    internal val str by lazy { node.toString() }

    companion object {
        // https://stackoverflow.com/questions/3907929/should-i-declare-jacksons-objectmapper-as-a-static-field
        private val mapper = ObjectMapper()

        fun parse(s: String): Rt_JsonValue {
            if (s.isBlank()) {
                throw IllegalArgumentException(s)
            }

            val json = try {
                mapper.readTree(s)
            } catch (e: JsonProcessingException) {
                throw IllegalArgumentException(s)
            }

            if (json == null) {
                throw IllegalArgumentException(s)
            }

            return Rt_JsonValue(json)
        }
    }
}

private object GtvRtConversion_Json: GtvRtConversion() {
    override fun directCompatibility() = R_GtvCompatibility(true, true)
    override fun rtToGtv(rt: Rt_Value, pretty: Boolean) = GtvString(rt.asJson().str)
    override fun gtvToRt(ctx: GtvToRtContext, gtv: Gtv) = GtvRtUtils.gtvToJson(ctx, gtv, R_JsonType)
}

internal object JsonUtils {
    internal fun canBeRellInteger(node: JsonNode): Boolean {
        return when {
            node.isLong || node.isInt || node.isShort -> true
            node.isBigInteger -> {
                val value = node.bigIntegerValue()
                value != null &&
                    value.compareTo(Rt_IntValue.MAX_VALUE_AS_BIGINT) < 1 &&     // Would be out of range in Kotlin
                    value.compareTo(Rt_IntValue.MIN_VALUE_AS_BIGINT) > -1       // or BigInteger would truncate
            }
            else -> false
        }
    }

    internal sealed interface GetOperationResult
    internal data class Success(val value: JsonNode): GetOperationResult
    internal data class Failure(val codeMsg: C_CodeMsg): GetOperationResult

    internal fun arrayGet(node: JsonNode, index: Int, userFnName: String): GetOperationResult {
        val result = node.get(index)
        return when {
            result != null -> Success(result)
            node.isArray -> {
                val size = node.size()
                Failure("expr_json_array_${userFnName}_index:$size:$index" toCodeMsg
                    "JSON array index out of bounds: $index (length $size)")
            }
            else -> {
                val nodeType = node.nodeType.name
                Failure("expr_json_array_${userFnName}_nodetype:$nodeType" toCodeMsg
                        "JSON array index on non-array (got $nodeType)")
            }
        }
    }

    internal fun objectGet(node: JsonNode, key: String, userFnName: String): GetOperationResult {
        val result = node.get(key)
        return when {
            result != null -> Success(result)
            node.isObject -> Failure("expr_json_object_${userFnName}_key:novalue:$key" toCodeMsg
                "JSON object key not found: $key")
            else -> {
                val nodeType = node.nodeType.name
                Failure("expr_json_object_${userFnName}_nodetype:$nodeType" toCodeMsg
                    "JSON object lookup on non-object (got $nodeType)")
            }
        }
    }
}