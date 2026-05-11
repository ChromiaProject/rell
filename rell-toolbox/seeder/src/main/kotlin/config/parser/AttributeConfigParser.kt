/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config.parser

import com.fasterxml.jackson.databind.JsonNode
import net.postchain.rell.base.model.R_IntegerType
import net.postchain.rell.base.model.R_TextType
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.Distribution
import net.postchain.rell.toolbox.seeder.generator.pattern.DataPatternGenerator
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import net.postchain.rell.toolbox.seeder.schema.Attribute
import java.math.BigInteger

class AttributeConfigParser {
    fun createAttributeConfig(
        attributeNode: JsonNode,
        attribute: Attribute
    ): AttributeConfig {
        val generatorType = attributeNode.get("generator")?.asText()
            ?: throw ConfigurationValidationException("missing generator")

        return when (generatorType) {
            "predefined" -> parsePredefinedValuesConfig(attributeNode, attribute)
            "range" -> parseRangeConfig(attributeNode, attribute)
            "text" -> parseTextConfig(attributeNode, attribute)
            "byte_array" -> parseByteArrayConfig(attributeNode, attribute)
            else -> parseDataConfig(attribute, generatorType)
        }
    }

    private fun parseDataConfig(
        attribute: Attribute,
        generatorType: String
    ): AttributeConfig.DataPatternConfig {
        val generator = FakerGeneratorFactory.default.getRegistry().getOrNull(generatorType)
        generator ?: throw ConfigurationValidationException(
            "generator '$generatorType' not found"
        )
        if (!checkCompatibleTypes(attribute, generator)) {
            throw ConfigurationValidationException(
                "is type '${attribute.typeStr()}' but generator '${attribute.typeStr()}' returns type '${generator.type.str()}'"
            )
        }
        return AttributeConfig.DataPatternConfig(generatorType)
    }

    private fun checkCompatibleTypes(attribute: Attribute, generator: DataPatternGenerator): Boolean {
        val attrType = attribute.type
        val genType = generator.type

        // Check if the types match by comparing RR_Type kind against R_Type
        if (attrType is RR_Type.Primitive && genType is R_TextType && attrType.kind == RR_PrimitiveKind.TEXT) return true
        if (attrType is RR_Type.Primitive && genType is R_IntegerType && attrType.kind == RR_PrimitiveKind.INTEGER) return true

        // Use typeStr() for structural matching: attribute.typeStr() gives a lowercase name
        val attrTypeStr = attribute.typeStr()
        val genTypeStr = genType.str()
        if (attrTypeStr == genTypeStr) return true

        return when {
            attrType is RR_Type.Primitive && attrType.kind == RR_PrimitiveKind.BIG_INTEGER && genType is R_IntegerType -> true
            attrType is RR_Type.Primitive && attrType.kind == RR_PrimitiveKind.DECIMAL && genType is R_IntegerType -> true
            attrType is RR_Type.Primitive && attrType.kind == RR_PrimitiveKind.JSON && generator.identifier == "random.json" -> true
            attrType is RR_Type.Enum && generator.identifier == "random.enum" -> true
            else -> false
        }
    }

    private fun parsePredefinedValuesConfig(
        attributeNode: JsonNode,
        attribute: Attribute
    ): AttributeConfig.PredefinedValues {
        val values = PredefinedValueParser.parse(attribute, attributeNode.withArray("values"))
        val distribution = if (attributeNode.has("distribution")) {
            Distribution.valueOf(attributeNode.get("distribution").asText().uppercase())
        } else {
            Distribution.SEQUENTIAL
        }

        return AttributeConfig.PredefinedValues(values, distribution)
    }

    private fun parseRangeConfig(
        attributeNode: JsonNode,
        attribute: Attribute
    ): AttributeConfig.Range {
        val (min, max) = validateRangeType(attributeNode, attribute)
        return AttributeConfig.Range(min, max)
    }

    private fun parseTextConfig(
        attributeNode: JsonNode,
        attribute: Attribute
    ): AttributeConfig.TextConfig {
        validateTextType(attribute)

        val min = if (attributeNode.has("min")) attributeNode.get("min").asInt() else null
        val max = if (attributeNode.has("max")) attributeNode.get("max").asInt() else null

        return AttributeConfig.TextConfig(min, max)
    }

    private fun parseByteArrayConfig(
        attributeNode: JsonNode,
        attribute: Attribute
    ): AttributeConfig.ByteArrayConfig {
        validateTextType(attribute)
        val size = if (attributeNode.has("size")) attributeNode.get("size").asInt() else null
        return AttributeConfig.ByteArrayConfig(size)
    }

    private fun validateRangeType(
        attributeNode: JsonNode,
        attribute: Attribute
    ): Pair<BigInteger, BigInteger> {
        if (!attribute.isNumberType()) {
            throw ConfigurationValidationException(
                "type is '${attribute.typeStr()}' but generator is configured as a numeric range type"
            )
        }
        val min = attributeNode.get("min").bigIntegerValue()
        validateNumberRange(min, attribute)

        val max = attributeNode.get("max").bigIntegerValue()
        validateNumberRange(max, attribute)

        validateNumberOrder(min, max)
        return Pair(min, max)
    }

    private fun validateNumberRange(value: BigInteger, attribute: Attribute) {
        val type = attribute.type
        when {
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BIG_INTEGER -> inRange(value, BIG_INTEGER_MIN_VALUE, BIG_INTEGER_MAX_VALUE)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.INTEGER -> inRange(value, INTEGER_MIN_VALUE, INTEGER_MAX_VALUE)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.ROWID -> inRange(value, BigInteger.ONE, INTEGER_MAX_VALUE)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.DECIMAL -> inRange(value, BIG_INTEGER_MIN_VALUE, BIG_INTEGER_MAX_VALUE)
            else -> throw ConfigurationValidationException(
                "range generator requires min and max to be within the range of the type"
            )
        }
    }

    private fun inRange(value: BigInteger, min: BigInteger, max: BigInteger) {
        if (value !in min..max) {
            throw ConfigurationValidationException(
                "range generator is not in range [$min, $max]"
            )
        }
    }

    private fun validateNumberOrder(min: BigInteger, max: BigInteger) {
        if (min > max) {
            throw ConfigurationValidationException(
                "min value must be less than or equal to max value. min: $min, max: $max"
            )
        }
    }

    private fun validateTextType(attribute: Attribute) {
        if (!attribute.isTextType()) {
            throw ConfigurationValidationException(
                "type is '${attribute.typeName()}' but generator is as configured as text type"
            )
        }
    }

    companion object {
        private const val PRECISION = 131072

        val BIG_INTEGER_MAX_VALUE: BigInteger = BigInteger.TEN.pow(PRECISION).subtract(BigInteger.ONE)
        val BIG_INTEGER_MIN_VALUE: BigInteger = -BIG_INTEGER_MAX_VALUE

        val INTEGER_MAX_VALUE: BigInteger = Long.MAX_VALUE.toBigInteger()
        val INTEGER_MIN_VALUE: BigInteger = Long.MIN_VALUE.toBigInteger()
    }
}
