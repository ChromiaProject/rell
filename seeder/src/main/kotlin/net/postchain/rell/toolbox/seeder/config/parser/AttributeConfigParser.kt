package net.postchain.rell.toolbox.seeder.config.parser

import com.fasterxml.jackson.databind.JsonNode
import net.postchain.rell.base.lib.type.R_BigIntegerType
import net.postchain.rell.base.lib.type.R_DecimalType
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.base.lib.type.R_JsonType
import net.postchain.rell.base.lib.type.R_RowidType
import net.postchain.rell.toolbox.seeder.Attribute
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.Distribution
import net.postchain.rell.toolbox.seeder.generator.pattern.DataPatternGenerator
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
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
                "is type '${attribute.type}' but generator '${attribute.type}' returns type '${generator.type}'"
            )
        }
        return AttributeConfig.DataPatternConfig(generatorType)
    }

    private fun checkCompatibleTypes(attribute: Attribute, generator: DataPatternGenerator): Boolean {
        if (attribute.type == generator.type) {
            return true
        }

        return when {
            attribute.type is R_BigIntegerType && generator.type is R_IntegerType -> true
            attribute.type is R_DecimalType && generator.type is R_IntegerType -> true
            attribute.type is R_JsonType && generator.identifier == "random.json" -> true
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
                "type is '${attribute.type}' but generator is configured as a numeric range type"
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
        when (attribute.type) {
            is R_BigIntegerType -> inRange(value, BIG_INTEGER_MIN_VALUE, BIG_INTEGER_MAX_VALUE)
            is R_IntegerType -> inRange(value, INTEGER_MIN_VALUE, INTEGER_MAX_VALUE)
            is R_RowidType -> inRange(value, BigInteger.ONE, INTEGER_MAX_VALUE)
            is R_DecimalType -> inRange(value, BIG_INTEGER_MIN_VALUE, BIG_INTEGER_MAX_VALUE)
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
                "type is '${attribute.type}' but generator is as configured as text type"
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
