/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

@file:Suppress("UnstableApiUsage")

package net.postchain.rell.base.lib.type

import com.fasterxml.jackson.databind.JsonNode
import net.postchain.rell.base.compiler.base.utils.C_CodeMsg
import net.postchain.rell.base.compiler.base.utils.toCodeMsg
import net.postchain.rell.base.lmodel.dsl.Ld_BodyResult
import net.postchain.rell.base.lmodel.dsl.Ld_MethodDsl
import net.postchain.rell.base.lmodel.dsl.Ld_NamespaceDsl
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.base.runtime.*
import net.postchain.rell.base.sql.SqlConstants
import net.postchain.rell.base.utils.toIntExact

private val SET_OF_TEXT_RR_TYPE: RR_Type =
    RR_Type.Set(RR_Type.Primitive(RR_PrimitiveKind.TEXT))

object Lib_Type_Json {
    private const val SINCE0 = "0.6.0"

    val NAMESPACE = Ld_NamespaceDsl.make {
        type(Rt_JsonValue, "json", rrType = RR_Type.Primitive(RR_PrimitiveKind.JSON), since = SINCE0) {
            """
                A JSON (JavaScript Object Notation) datatype.

                JSON values are created from text and support conversion to GTV via `gtv.from_json()`. They can be
                stored in the database as an entity attribute.

                JSON values in Rell are represented internally with text, which has been validated as legal JSON.

                @see 1. <a href="../gtv/index.html"><code>gtv</code> - Rell Standard Library</a>
                @see 2. <a href="../gtv/from_json.html"><code>gtv.from_json()</code> - Rell Standard Library</a>
            """.comment()

            constructor(pure = true, since = SINCE0) {
                """
                    Construct a JSON value from text.

                    @throws exception if `text` is not valid JSON
                """.comment()
                val value by param(Rt_TextValue, comment = "the JSON text to decode")
                dbFunctionCast("json", "JSONB")
                body {
                    val jsonValue = try {
                        Rt_JsonValue.parse(value.value)
                    } catch (_: IllegalArgumentException) {
                        throw Rt_Exception.common("fn_json_badstr", "Bad JSON: ${value.value}")
                    }
                    jsonValue
                }
            }

            function("to_text", pure = true, since = "0.9.0") {
                """
                    Convert this JSON value to text.

                    Note that this method is different to `json.as_text()`. This method converts this entire JSON value
                    to its text representation, regardless of what type of JSON value this is, and does not throw an
                    exception. `json.as_text()` retrieves this text-typed JSON value as Rell text, and throws an
                    exception if this JSON value is not of text type.
                """.comment()
                val self by self()
                alias("str", since = SINCE0)
                dbFunctionCast("json.to_text", "TEXT")
                body(Rt_TextValue) { self.str }
            }

            function("get", result = "json", pure = true, since = "0.14.16") {
                """
                    Get the element at the specified index of this JSON array.

                    `json_array.get(index)` is equivalent to `json_array[index]`.
                    @throws exception if this JSON value is not an array
                    @throws exception if the specified index is out of bounds
                """.comment()
                val self by self()
                val index by param(Rt_IntValue, comment = "the array index")
                dbFunctionSimple("json.get", SqlConstants.FN_JSON_ARRAY_GET)
                body {
                    val arrayValue = self.node
                    val indexValue = index.value.toIntExact()
                    when (val result = JsonUtils.arrayGet(arrayValue, indexValue, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        is JsonUtils.Failure -> throw Rt_Exception.common(result.codeMsg.code, result.codeMsg.msg)
                    }
                }
            }

            function("get", result = "json", pure = true, since = "0.14.16") {
                """
                    Get the member with the specified key in this JSON object.

                    `json_object.get(key)` is equivalent to `json_object[key]`.
                    @throws exception if this JSON value is not an object
                    @throws exception if the specified key is not found in this object
                """.comment()

                val self by self()
                val key by param(Rt_TextValue, comment = "the object key")
                dbFunctionSimple("json.get", SqlConstants.FN_JSON_OBJECT_GET)

                body {
                    when (val result = JsonUtils.objectGet(self.node, key.value, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        is JsonUtils.Failure -> throw Rt_Exception.common(result.codeMsg.code, result.codeMsg.msg)
                    }
                }
            }

            function("get_or_null", result = "json?", pure = true, since = "0.14.16") {
                """
                    Get the element at the specified index of this JSON array, or null if this JSON value is not an
                    array, or if the specified index is out of bounds.
                """.comment()
                val self by self()
                val index by param(Rt_IntValue, comment = "the array index")
                dbFunctionSimple("json.get_or_null", SqlConstants.FN_JSON_ARRAY_GET_OR_NULL)
                body {
                    val arrayValue = self.node
                    val indexValue = index.value.toIntExact()
                    when (val result = JsonUtils.arrayGet(arrayValue, indexValue, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        else -> Rt_NullValue
                    }
                }
            }

            function("get_or_null", result = "json?", pure = true, since = "0.14.16") {
                """
                    Get the member with the specified key in this JSON object, or null if this JSON value is not an
                    object, or if the specified key is not found in this object.
                """.comment()

                val self by self()
                val key by param(Rt_TextValue, comment = "the object key")
                dbFunctionTemplate("json.get_or_null", 2, "(#0 -> #1)")

                body {
                    when (val result = JsonUtils.objectGet(self.node, key.value, fnSimpleName)) {
                        is JsonUtils.Success -> Rt_JsonValue(result.value)
                        else -> Rt_NullValue
                    }
                }
            }

            function("as_integer", result = "integer", pure = true, since = "0.14.16") {
                """
                    Convert this JSON integer value to an integer.

                    All JSON values that can be converted to `integer` with `as_integer()` can also be converted to
                    `big_integer` with `as_big_integer()`, though the reverse is not true, since `big_integer` values
                    may fall outside the `integer` range.
                    @throws exception if this JSON value is not an integer
                """.comment()
                dbFunctionSimple("json.as_integer", SqlConstants.FN_JSON_AS_INTEGER)
                asTypeBody(this, JsonUtils::canBeRellInteger) { Rt_IntValue.get(it.asLong()) }
            }

            function("as_big_integer", result = "big_integer", pure = true, since = "0.14.16") {
                """
                    Convert this JSON value to a `big_integer`.

                    All JSON values that can be converted to `integer` with `as_integer()` can also be converted to
                    `big_integer` with `as_big_integer()`, though the reverse is not true, since `big_integer` values
                    may fall outside the `integer` range.
                    @throws exception if this JSON value cannot be converted to a `big_integer`
                """.comment()
                dbFunctionSimple("json.as_big_integer", SqlConstants.FN_JSON_AS_BIG_INTEGER)
                asTypeBody(this, JsonUtils::canBeRellBigInteger) { Rt_BigIntegerValue.get(it.bigIntegerValue()) }
            }

            function("as_boolean", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Convert this JSON boolean value to a boolean.
                    @throws exception if this JSON value is not a boolean
                """.comment()
                dbFunctionCast("json.as_boolean", "BOOLEAN")
                asTypeBody(this, JsonNode::isBoolean) { Rt_BooleanValue.get(it.asBoolean()) }
            }

            function("as_text", result = "text", pure = true, since = "0.14.16") {
                """
                    Convert this JSON text value to text.

                    Note that this method is different to `json.to_text()`. This method retrieves this text-typed JSON
                    value as Rell text, and throws an exception if this JSON value is not of text type. `json.to_text()`
                    converts this entire JSON value to its text representation, regardless of what type of JSON value
                    this is, and does not throw an exception.
                    @throws exception if this JSON value is not text
                """.comment()
                dbFunctionSimple("json.as_text", SqlConstants.FN_JSON_AS_TEXT)
                asTypeBody(this, JsonNode::isTextual) { Rt_TextValue.get(it.asText()) }
            }

            function("as_integer_or_null", result = "integer?", pure = true, since = "0.14.16") {
                """
                    Convert this JSON integer value to an integer.

                    All JSON values that can be converted to `integer` with `as_integer_or_null()` can also be converted
                    to `big_integer` with `as_big_integer_or_null()`, though the reverse is not true, since
                    `big_integer` values may fall outside the `integer` range.
                    @return this JSON integer as an integer, or null if this JSON value is not an integer
                """.comment()
                dbFunctionSimple("json.as_integer_or_null", SqlConstants.FN_JSON_AS_INTEGER_OR_NULL)
                asTypeBody(this, JsonUtils::canBeRellInteger, nullOnError = true) { Rt_IntValue.get(it.asLong()) }
            }

            function("as_big_integer_or_null", result = "big_integer?", pure = true, since = "0.14.16") {
                """
                    Convert this JSON value to a `big_integer`.

                    All JSON values that can be converted to `integer` with `as_integer_or_null()` can also be converted
                    to `big_integer` with `as_big_integer_or_null()`, though the reverse is not true, since
                    `big_integer` values may fall outside the `integer` range.
                    @return this JSON value as a `big_integer`, or null if it cannot be converted to a `big_integer`
                """.comment()
                dbFunctionSimple("json.as_big_integer_or_null", SqlConstants.FN_JSON_AS_BIG_INTEGER_OR_NULL)
                asTypeBody(this, JsonUtils::canBeRellBigInteger, nullOnError = true) {
                    Rt_BigIntegerValue.get(it.bigIntegerValue())
                }
            }

            function("as_boolean_or_null", result = "boolean?", pure = true, since = "0.14.16") {
                """
                    Convert this JSON boolean value to a boolean.
                    @return this JSON boolean as a boolean, or null if this JSON value is not a boolean
                """.comment()
                dbFunctionSimple("json.as_boolean_or_null", SqlConstants.FN_JSON_AS_BOOLEAN_OR_NULL)
                asTypeBody(this, JsonNode::isBoolean, nullOnError = true) { Rt_BooleanValue.get(it.asBoolean()) }
            }

            function("as_text_or_null", result = "text?", pure = true, since = "0.14.16") {
                """
                    Convert this JSON text value to text.
                    @return this JSON text as text, or null if this JSON value is not text
                """.comment()
                dbFunctionSimple("json.as_text_or_null", SqlConstants.FN_JSON_AS_TEXT_OR_NULL)
                asTypeBody(this, JsonNode::isTextual, nullOnError = true) { Rt_TextValue.get(it.asText()) }
            }

            function("is_object", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value is an object.
                    @return true if this JSON value is an object, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_object", 1, "(JSONB_TYPEOF(#0) = 'object')")
                isTypeBody(this, JsonNode::isObject)
            }

            function("is_array", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value is an array.
                    @return true if this JSON value is an array, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_array", 1, "(JSONB_TYPEOF(#0) = 'array')")
                isTypeBody(this, JsonNode::isArray)
            }

            function("is_text", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value is text.
                    @return true if this JSON value is text, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_text", 1, "(JSONB_TYPEOF(#0) = 'string')")
                isTypeBody(this, JsonNode::isTextual)
            }

            function("is_null", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value is null.
                    @return true if this JSON value is null, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_null", 1, "(JSONB_TYPEOF(#0) = 'null')")
                isTypeBody(this, JsonNode::isNull)
            }

            function("is_integer", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value is an integer.

                    All JSON values that return `true` with `is_integer()` also return `true` with `is_big_integer()`,
                    though the reverse is not the case, since `big_integer` values may fall outside the `integer` range.
                    @return true if this JSON value is an integer, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_integer", 1, "(${SqlConstants.FN_JSON_AS_INTEGER_OR_NULL}(#0) IS NOT NULL)")
                isTypeBody(this, JsonUtils::canBeRellInteger)
            }

            function("is_big_integer", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value can be converted to `big_integer`.

                    All JSON values that return `true` with `is_integer()` also return `true` with `is_big_integer()`,
                    though the reverse is not the case, since `big_integer` values may fall outside the `integer` range.
                    @return true if this JSON value can be converted to `big_integer`, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_big_integer", 1,
                    "(${SqlConstants.FN_JSON_AS_BIG_INTEGER_OR_NULL}(#0) IS NOT NULL)")
                isTypeBody(this, JsonUtils::canBeRellBigInteger)
            }

            function("is_boolean", result = "boolean", pure = true, since = "0.14.16") {
                """
                    Determine whether this JSON value is a boolean.
                    @return true if this JSON value is a boolean, false otherwise
                """.comment()
                dbFunctionTemplate("json.is_boolean", 1, "(JSONB_TYPEOF(#0) = 'boolean')")
                isTypeBody(this, JsonNode::isBoolean)
            }

            function("size", pure = true, since = "0.14.16") {
                """
                    Get the size of this JSON value; i.e. the number of elements in this JSON array, or the number of
                    key-value pairs in this JSON object.
                    @return the size of this JSON value
                    @throws exception if this JSON value is not an array or an object
                """.comment()
                val self by self()
                dbFunctionSimple("json.size", SqlConstants.FN_JSON_SIZE)
                body(Rt_IntValue) {
                    val jsonNode = self.node
                    if (jsonNode.isContainerNode) {
                        jsonNode.size().toLong()
                    } else {
                        val nodeTypeName = jsonNode.nodeType.name
                        val msg = "Tried to get the size of a JSON value that was not an object or array (got $nodeTypeName)."
                        throw Rt_Exception.common("json.$fnSimpleName:type_error:$nodeTypeName", msg)
                    }
                }
            }

            function("keys", result = "set<text>", pure = true, since = "0.14.16") {
                """
                    Get the keys of this JSON object.
                    @throws exception if this JSON value is not an object
                """.comment()
                val self by self()
                bodyContext { ctx ->
                    val jsonNode = self.node
                    if (jsonNode.isObject) {
                        val set: MutableSet<Rt_Value> = mutableSetOf()
                        for (name in jsonNode.fieldNames()) {
                            set.add(Rt_TextValue.get(name))
                        }
                        val rtType = ctx.exeCtx.appCtx.interpreter.resolveType(SET_OF_TEXT_RR_TYPE)
                        Rt_SetValue(rtType, set)
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
    m: Ld_MethodDsl<Rt_JsonValue>,
    checkType: (JsonNode) -> Boolean,
    nullOnError: Boolean = false,
    getType: (JsonNode) -> Rt_Value,
): Ld_BodyResult = with(m) {
    val self by self()
    body {
        val jsonValue = self.node
        when {
            checkType(jsonValue) -> getType(jsonValue)
            nullOnError -> Rt_NullValue
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

private fun isTypeBody(m: Ld_MethodDsl<Rt_JsonValue>, checkType: (JsonNode) -> Boolean): Ld_BodyResult = with(m) {
    val self by self()
    body(Rt_BooleanValue) { checkType(self.node) }
}

object JsonUtils {
    fun canBeRellInteger(node: JsonNode): Boolean = when {
        node.isLong || node.isInt || node.isShort -> true
        node.isBigInteger -> {
            val value = node.bigIntegerValue()
            value != null &&
                    value <= Rt_IntValue.MAX_VALUE_AS_BIGINT &&    // Would be out of range in Kotlin
                    value >= Rt_IntValue.MIN_VALUE_AS_BIGINT       // or BigInteger would truncate
        }
        else -> false
    }

    fun canBeRellBigInteger(node: JsonNode): Boolean = node.isIntegralNumber &&
            node.bigIntegerValue() <= Lib_BigIntegerMath.MAX_VALUE &&
            node.bigIntegerValue() >= Lib_BigIntegerMath.MIN_VALUE

    sealed interface GetOperationResult
    data class Success(val value: JsonNode): GetOperationResult
    data class Failure(val codeMsg: C_CodeMsg): GetOperationResult

    fun arrayGet(node: JsonNode, index: Int, userFnName: String): GetOperationResult {
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

    fun objectGet(node: JsonNode, key: String, userFnName: String): GetOperationResult {
        val result = node[key]
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