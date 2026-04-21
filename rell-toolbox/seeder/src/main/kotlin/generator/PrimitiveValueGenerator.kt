/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import net.postchain.common.types.WrappedByteArray
import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
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
        val type = ctx.attribute.type
        return when {
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.INTEGER -> generatorFactory.callGenerator(
                "random.integer",
                ctx.copy(attributeConfig = AttributeConfig.Range(NUMBER_MIN_VALUE, NUMBER_MAX_VALUE))
            )

            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BIG_INTEGER -> BigInteger.valueOf(
                generatorFactory.callGenerator(
                    "random.integer",
                    ctx.copy(attributeConfig = AttributeConfig.Range(NUMBER_MIN_VALUE, NUMBER_MAX_VALUE))
                ) as Long
            )

            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.DECIMAL -> BigDecimal(
                generatorFactory.callGenerator(
                    "random.decimal",
                    ctx.copy(attributeConfig = AttributeConfig.Range(NUMBER_MIN_VALUE, NUMBER_MAX_VALUE))
                ) as Double
            )

            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BOOLEAN -> generatorFactory.callGenerator("random.boolean", ctx)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.TEXT -> generatorFactory.callGenerator("random.text", ctx)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BYTE_ARRAY -> generateRandomBytes(ctx)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.JSON -> generatorFactory.callGenerator("random.json", ctx)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.ROWID -> generatorFactory.callGenerator(
                "random.integer",
                ctx.copy(attributeConfig = AttributeConfig.Range(ROWID_MIN_VALUE, ROWID_MAX_VALUE))
            )

            type is RR_Type.Enum -> generatorFactory.callGenerator("random.enum", ctx)

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
