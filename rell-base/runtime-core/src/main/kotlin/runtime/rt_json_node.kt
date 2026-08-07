/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.runtime

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonParser.NumberType
import com.fasterxml.jackson.core.JsonToken
import java.io.StringWriter
import java.math.BigInteger

/**
 * A parsed JSON value, the representation behind Rell's `json` type.
 *
 * The text produced by [toString] is consensus-critical: it is what `json.to_text()` returns, what value equality
 * compares, and what is written to the database and to GTV. [nodeTypeName] reproduces the names of Jackson's
 * `JsonNodeType`, which appear in Rell error codes.
 */
sealed class Rt_JsonNode {
    abstract val nodeTypeName: String

    open val isObject: Boolean
        get() = false

    open val isArray: Boolean
        get() = false

    open val isTextual: Boolean
        get() = false

    open val isBoolean: Boolean
        get() = false

    open val isNull: Boolean
        get() = false

    val isContainer: Boolean
        get() = isObject || isArray

    /** True for a number written without a fraction or an exponent; Jackson's `isIntegralNumber`. */
    open val isIntegral: Boolean
        get() = false

    /** True for an integral number that fits in a [Long]; Jackson's `isInt || isLong || isShort`. */
    open val isLongNumber: Boolean
        get() = false

    /** True for an integral number too large for a [Long]; Jackson's `isBigInteger`. */
    open val isBigIntegerNumber: Boolean
        get() = false

    open fun size(): Int = 0
    open fun fieldNames(): List<String> = emptyList()
    open fun get(index: Int): Rt_JsonNode? = null
    open fun get(key: String): Rt_JsonNode? = null

    open fun asText(): String = ""
    open fun asBoolean(): Boolean = false
    open fun asLong(): Long = 0
    open fun bigIntegerValue(): BigInteger = BigInteger.ZERO

    protected abstract fun write(gen: JsonGenerator)

    final override fun toString(): String {
        val writer = StringWriter()
        FACTORY.createGenerator(writer).use { write(it) }
        return writer.toString()
    }

    data class Obj(private val members: Map<String, Rt_JsonNode>): Rt_JsonNode() {
        override val nodeTypeName get() = "OBJECT"
        override val isObject get() = true
        override fun size() = members.size
        override fun fieldNames() = members.keys.toList()
        override fun get(key: String) = members[key]

        override fun write(gen: JsonGenerator) {
            gen.writeStartObject()
            for ((key, value) in members) {
                gen.writeFieldName(key)
                value.write(gen)
            }
            gen.writeEndObject()
        }
    }

    data class Arr(private val elements: List<Rt_JsonNode>): Rt_JsonNode() {
        override val nodeTypeName get() = "ARRAY"
        override val isArray get() = true
        override fun size() = elements.size
        override fun get(index: Int) = elements.getOrNull(index)

        override fun write(gen: JsonGenerator) {
            gen.writeStartArray()
            elements.forEach { it.write(gen) }
            gen.writeEndArray()
        }
    }

    data class Str(private val value: String): Rt_JsonNode() {
        override val nodeTypeName
            get() = "STRING"

        override val isTextual
            get() = true

        override fun asText() = value
        override fun write(gen: JsonGenerator) = gen.writeString(value)
    }

    data class Bool(private val value: Boolean): Rt_JsonNode() {
        override val nodeTypeName
            get() = "BOOLEAN"

        override val isBoolean
            get() = true

        override fun asBoolean() = value
        override fun write(gen: JsonGenerator) = gen.writeBoolean(value)
    }

    data object Null: Rt_JsonNode() {
        override val nodeTypeName
            get() = "NULL"

        override val isNull
            get() = true

        override fun write(gen: JsonGenerator) = gen.writeNull()
    }

    /**
     * A number, keeping the type Jackson's parser assigned to the literal - `int` and `long` for integral literals
     * within their range, `BigInteger` beyond it, `double` for anything with a fraction or an exponent. Writing the
     * value back through the generator is what canonicalises it.
     */
    @ConsistentCopyVisibility
    data class Num private constructor(
        private val longValue: Long?,
        private val bigValue: BigInteger?,
        private val doubleValue: Double?,
    ): Rt_JsonNode() {
        override val nodeTypeName
            get() = "NUMBER"

        override val isIntegral
            get() = doubleValue == null

        override val isLongNumber
            get() = longValue != null

        override val isBigIntegerNumber
            get() = bigValue != null

        override fun asLong(): Long = longValue ?: bigValue?.toLong() ?: doubleValue!!.toLong()

        override fun bigIntegerValue(): BigInteger = when {
            longValue != null -> BigInteger.valueOf(longValue)
            bigValue != null -> bigValue
            else -> BigInteger.valueOf(doubleValue!!.toLong())
        }

        override fun write(gen: JsonGenerator) {
            when {
                longValue != null -> gen.writeNumber(longValue)
                bigValue != null -> gen.writeNumber(bigValue)
                else -> gen.writeNumber(doubleValue!!)
            }
        }

        companion object {
            fun ofLong(value: Long) = Num(value, null, null)
            fun ofBigInteger(value: BigInteger) = Num(null, value, null)
            fun ofDouble(value: Double) = Num(null, null, value)
        }
    }

    companion object {
        private val FACTORY = JsonFactory()

        /** @throws IllegalArgumentException if [s] is not valid JSON. */
        fun parse(s: String): Rt_JsonNode {
            try {
                FACTORY.createParser(s).use { parser ->
                    require(parser.nextToken() != null) { s }
                    return read(parser)
                }
            } catch (e: Exception) {
                throw IllegalArgumentException(s, e)
            }
        }

        private fun read(parser: JsonParser): Rt_JsonNode = when (val token = parser.currentToken()) {
            JsonToken.VALUE_NULL -> Null
            JsonToken.VALUE_STRING -> Str(parser.text)
            JsonToken.VALUE_TRUE -> Bool(true)
            JsonToken.VALUE_FALSE -> Bool(false)
            JsonToken.VALUE_NUMBER_FLOAT -> Num.ofDouble(parser.doubleValue)

            JsonToken.VALUE_NUMBER_INT -> when (parser.numberType) {
                NumberType.INT, NumberType.LONG -> Num.ofLong(parser.longValue)
                else -> Num.ofBigInteger(parser.bigIntegerValue)
            }

            JsonToken.START_OBJECT -> Obj(
                buildMap {
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        val name = parser.currentName()
                        parser.nextToken()
                        put(name, read(parser))
                    }
                },
            )

            JsonToken.START_ARRAY -> Arr(
                buildList {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        add(read(parser))
                    }
                },
            )

            else -> throw IllegalArgumentException("Unexpected JSON token: $token")
        }
    }
}
