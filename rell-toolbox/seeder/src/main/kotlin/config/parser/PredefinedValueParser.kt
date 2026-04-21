/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import com.fasterxml.jackson.databind.JsonNode
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.toolbox.seeder.schema.Attribute
import java.math.BigInteger

object PredefinedValueParser {

    fun parse(attribute: Attribute, nodes: JsonNode): List<Any> {
        val type = attribute.type
        return when {
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BOOLEAN -> parseBooleanValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.INTEGER -> parseIntegerValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BIG_INTEGER -> parseBigIntegerValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.DECIMAL -> parseDecimalValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.ROWID -> parseRowIdValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.TEXT -> parseTextualValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.JSON -> parseJsonValues(nodes)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BYTE_ARRAY -> parseByteArrayValues(nodes)
            type is RR_Type.Enum -> parseEnumValues(attribute, nodes)
            else -> throw ConfigurationValidationException(
                "predefined values type is not supported for type ${attribute.typeStr()}"
            )
        }
    }

    private fun parseBooleanValues(nodes: JsonNode): List<Boolean> = nodes.map {
        if (!it.isBoolean) {
            throw ConfigurationValidationException(
                "predefined values must be boolean, but found: ${it.nodeType}"
            )
        }
        it.asBoolean()
    }

    private fun parseIntegerValues(nodes: JsonNode): List<Int> = nodes.map {
        when {
            it.isInt -> it.asInt()
            it.isTextual -> {
                it.asText().toIntOrNull() ?: throw ConfigurationValidationException(
                    "predefined values must be of number type, but found: ${it.nodeType}"
                )
            }

            it.isBigInteger -> throw ConfigurationValidationException(
                "predefined value '${it.asText()}' exceeds integer type range, may cause an overflow"
            )

            it.isBigDecimal || it.isDouble || it.isFloat -> throw ConfigurationValidationException(
                "predefined values requires integer, but found: ${it.nodeType}"
            )

            else -> throw ConfigurationValidationException(
                "predefined values must be numbers, but found: ${it.nodeType}"
            )
        }
    }

    private fun parseBigIntegerValues(nodes: JsonNode): List<BigInteger> = nodes.map {
        when {
            it.isTextual -> {
                it.asText().toBigIntegerOrNull() ?: throw ConfigurationValidationException(
                    "predefined values must be valid number, but found: ${it.nodeType}"
                )
            }

            it.isInt -> it.asInt().toBigInteger()
            it.isBigInteger -> it.asInt().toBigInteger()
            else -> throw ConfigurationValidationException(
                "predefined values must be numbers or texts, but found: ${it.nodeType}"
            )
        }
    }

    private fun parseDecimalValues(nodes: JsonNode): List<Double> = nodes.map {
        if (!it.isNumber) {
            throw ConfigurationValidationException(
                "predefined values must be numbers, but found: ${it.nodeType}"
            )
        }
        it.asDouble()
    }

    private fun parseRowIdValues(nodes: JsonNode): List<Int> = nodes.map {
        if (!it.isInt) {
            throw ConfigurationValidationException(
                "predefined values must be of type integer, but found ${it.nodeType}"
            )
        }
        if (it.asInt() < 0) {
            throw ConfigurationValidationException(
                "predefined values must be greater or equal to 0 value was '$it'"
            )
        }
        it.asInt()
    }

    private fun parseTextualValues(nodes: JsonNode) = nodes.map {
        if (!it.isTextual) {
            throw ConfigurationValidationException(
                "predefined values must be strings, but found: ${it.nodeType}"
            )
        }
        it.asText()
    }

    private fun parseJsonValues(nodes: JsonNode): List<String> = nodes.map {
        when {
            it.isObject -> it.toString()
            it.isArray -> it.toString()
            else -> throw ConfigurationValidationException(
                "predefined values must be object or array, but found: ${it.nodeType}"
            )
        }
    }

    private fun parseByteArrayValues(nodes: JsonNode): List<Any> = nodes.map {
        if (!it.isTextual) {
            throw ConfigurationValidationException(
                "predefined values must be strings, but found: ${it.nodeType}"
            )
        }
        if (!it.asText().matches(Regex("[0-9A-Fa-f]+"))) {
            throw ConfigurationValidationException(
                "invalid hexadecimal value. Only characters 0-9, A-F, and a-f are allowed, but found '${it.asText()}'"
            )
        }

        if (it.asText().length % 2 != 0) {
            throw ConfigurationValidationException(
                "invalid hexadecimal value. Length must be even, but found ${it.asText().length}"
            )
        }
        it.asText()
    }

    private fun parseEnumValues(
        attribute: Attribute,
        nodes: JsonNode
    ) = nodes.map { node ->
        val enumDef = attribute.enumDefinition
            ?: throw ConfigurationValidationException("Expected enum type for attribute '${attribute.name}'")
        if (!node.isTextual) {
            throw ConfigurationValidationException(
                "predefined values must be strings, but found: ${node.nodeType}"
            )
        }
        val enumAttrNames = enumDef.attrs.map { it.name }
        if (node.asText() !in enumAttrNames) {
            val validationErrorMessage = enumAttrNames.joinToString(prefix = "[", postfix = "]")
            throw ConfigurationValidationException(
                "enum value '${node.asText()}' is not in enum set $validationErrorMessage"
            )
        }
        node.asText()
    }
}
