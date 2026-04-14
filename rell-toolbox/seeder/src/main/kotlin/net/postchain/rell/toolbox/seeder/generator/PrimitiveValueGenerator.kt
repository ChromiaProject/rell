/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.base.lib.type.*
import net.postchain.rell.base.model.R_EnumType
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.random.Random

class PrimitiveValueGenerator(
    private val random: Random,
    private val generatorFactory: FakerGeneratorFactory,
) {
    companion object {
        const val NUMBER_MIN_VALUE = -1000
        const val NUMBER_MAX_VALUE = 1000
        const val ROWID_MIN_VALUE = 1
        const val ROWID_MAX_VALUE = 1000000
        const val BYTE_ARRAY_MIN_SIZE = 1
        const val BYTE_ARRAY_MAX_SIZE = 32
    }

    fun generateForType(ctx: DataGeneratorContext): Any {
        return when (ctx.attribute.type) {
            is R_IntegerType -> generatorFactory.callGenerator(
                "random.integer",
                ctx.copy(attributeConfig = AttributeConfig.Range(NUMBER_MIN_VALUE, NUMBER_MAX_VALUE))
            )

            is R_BigIntegerType -> BigInteger.valueOf(
                generatorFactory.callGenerator(
                    "random.integer",
                    ctx.copy(attributeConfig = AttributeConfig.Range(NUMBER_MIN_VALUE, NUMBER_MAX_VALUE))
                ) as Long
            )

            is R_DecimalType -> BigDecimal(
                generatorFactory.callGenerator(
                    "random.decimal",
                    ctx.copy(attributeConfig = AttributeConfig.Range(NUMBER_MIN_VALUE, NUMBER_MAX_VALUE))
                ) as Double
            )

            is R_BooleanType -> generatorFactory.callGenerator("random.boolean", ctx)
            is R_TextType -> generatorFactory.callGenerator("random.text", ctx)
            is R_ByteArrayType -> generateRandomBytes(ctx)
            is R_JsonType -> generatorFactory.callGenerator("random.json", ctx) // generateRandomJSON(ctx)
            is R_RowidType -> generatorFactory.callGenerator(
                "random.integer",
                ctx.copy(attributeConfig = AttributeConfig.Range(ROWID_MIN_VALUE, ROWID_MAX_VALUE))
            )

            is R_EnumType -> generatorFactory.callGenerator("random.enum", ctx)

            else -> throw IllegalArgumentException("Unsupported type: ${ctx.attribute.type}")
        }
    }

    private fun generateRandomBytes(ctx: DataGeneratorContext): WrappedByteArray {
        val size = (ctx.attributeConfig as? AttributeConfig.ByteArrayConfig)?.size ?: BYTE_ARRAY_MAX_SIZE
        return if (ctx.isAttributeUnique()) {
            ctx.uniqueProducer.nextUnique(producer = { createByteArrayOfSize(size) }) as WrappedByteArray
        } else {
            createByteArrayOfSize(size)
        }
    }

    private fun createByteArrayOfSize(size: Int): WrappedByteArray {
        require(size >= 0) { "Size must be non-negative" }
        val random = java.security.SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return WrappedByteArray(bytes)
    }
}
