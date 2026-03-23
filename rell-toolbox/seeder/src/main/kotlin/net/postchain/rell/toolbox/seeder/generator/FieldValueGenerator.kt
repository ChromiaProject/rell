/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import io.github.serpro69.kfaker.exception.RetryLimitException
import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.toolbox.seeder.Attribute
import net.postchain.rell.toolbox.seeder.Entity
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.Distribution
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGenerator
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import kotlin.random.Random
import net.postchain.rell.base.lib.type.R_DecimalType

class FieldValueGenerator(
    private val random: Random,
    private val generatorFactory: FakerGeneratorFactory,
) {
    private val primitiveValueGenerator = PrimitiveValueGenerator(random, generatorFactory)

    fun generateFieldValue(
        attribute: Attribute,
        attributeConfig: AttributeConfig?,
        entity: Entity,
        rellSchema: RellSchema,
        existingEntityData: Map<String, List<EntityRecord>>,
        globalConfig: Configuration,
        currentEntityRecords: List<EntityRecord>,
        currentValues: Map<String, Any?>,
        attributeFakerGenerator: FakerGenerator,
    ): FieldValue<Any>? {
        return try {
            val ctx = DataGeneratorContext(
                attribute,
                entity,
                attributeConfig,
                attributeFakerGenerator.faker,
                attributeFakerGenerator.uniqueProducer
            )
            when (attributeConfig) {
                is AttributeConfig.PredefinedValues -> generateFromPredefinedValues(
                    attributeConfig,
                    currentEntityRecords.size,
                    attribute
                )

                is AttributeConfig.Range -> generateFromRange(ctx)
                is AttributeConfig.TextConfig -> FieldValue(
                    generatorFactory.callGenerator(
                        "random.text",
                        ctx
                    ) as String
                )
                is AttributeConfig.ByteArrayConfig -> FieldValue(
                    primitiveValueGenerator.generateForType(ctx) as WrappedByteArray
                )

                is AttributeConfig.DataPatternConfig -> FieldValue(
                    generatorFactory.callGenerator(
                        attributeConfig.pattern,
                        ctx
                    )
                )

                else -> generateDefaultValue(ctx, existingEntityData)
            }
        } catch (e: RetryLimitException) {
            throw DataGenerationException(
                "Could not generate unique values for '${attribute.name}' of entity '${entity.qualifiedName}'",
                e
            )
        }
    }

    private fun generateFromPredefinedValues(
        config: AttributeConfig.PredefinedValues,
        index: Int,
        attribute: Attribute
    ): FieldValue<Any> {
        val value = when (config.distribution ?: Distribution.SEQUENTIAL) {
            Distribution.SEQUENTIAL -> config.values[index % config.values.size]
            Distribution.RANDOM -> config.values[random.nextInt(config.values.size)]
            Distribution.WEIGHTED -> selectWeightedValue(config)
        }
        val type = attribute.type

        return when (type) {
            is R_EnumType -> FieldValue(getOrdinalValue(value as String, type))
            is R_DecimalType -> FieldValue(value.toString())
            else -> FieldValue(value)
        }
    }

    private fun getOrdinalValue(enumValue: String, enumType: R_EnumType): Int {
        return enumType.values.find { it.asEnum().name == enumValue }?.asEnum()?.value
            ?: throw IllegalArgumentException("Enum value '$enumValue' not found in enum type '${enumType.name}'")
    }

    private fun selectWeightedValue(config: AttributeConfig.PredefinedValues): Any {
        // This is a simplified implementation
        // In a real implementation, we would use the weights to select values
        return config.values[random.nextInt(config.values.size)]
    }

    // TODO: It could be nice to add support for Distribution.SEQUENTIAL for integer range.
    //  Doing so would allow users to set a range and for each new entity a field value increases
    private fun generateFromRange(ctx: DataGeneratorContext): FieldValue<Number> {
        val numberGenerator = NumberValueGenerator(generatorFactory)
        val value = numberGenerator.generateNumberInRange(ctx)
        return FieldValue(value)
    }

    private fun generateDefaultValue(
        ctx: DataGeneratorContext,
        existingEntityData: Map<String, List<EntityRecord>>,
    ): FieldValue<Any>? {
        // Transaction check needs to be before isReference, because transaction is also a reference
        return when {
            ctx.attribute.type.name == "transaction" -> FieldValue("%TX_ENTITY")
            ctx.attribute.type.name == "block" -> FieldValue("%BLOCK_ENTITY")
            ctx.attribute.isReference -> generateReferenceValue(ctx, existingEntityData)
            else -> FieldValue(generatePrimitiveValue(ctx))
        }
    }

    private fun generateReferenceValue(
        ctx: DataGeneratorContext,
        existingEntityData: Map<String, List<EntityRecord>>,
    ): FieldValue<Any>? {
        val records = existingEntityData[ctx.attribute.type.name] ?: return null
        if (records.isEmpty()) return null

        // Returning ordinal value instead of rowid, as rowid is generated at runtime
        val referenceValue = generatorFactory.callGenerator(
            "random.integer",
            ctx.copy(attributeConfig = AttributeConfig.Range(0, records.size - 1))
        )
        return FieldValue(
            referenceValue as Long,
            isReference = true,
            entityReferenceType = ctx.attribute.type.name
        )
    }

    private fun generatePrimitiveValue(ctx: DataGeneratorContext): Any {
        return primitiveValueGenerator.generateForType(ctx)
    }
}
