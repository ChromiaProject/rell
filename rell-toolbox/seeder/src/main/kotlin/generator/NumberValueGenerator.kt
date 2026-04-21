/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import net.postchain.rell.base.model.rr.RR_PrimitiveKind
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import java.math.BigDecimal
import java.math.BigInteger

class NumberValueGenerator(val generatorFactory: FakerGeneratorFactory) {
    fun generateNumberInRange(ctx: DataGeneratorContext): Number {
        if (ctx.attributeConfig !is AttributeConfig.Range) {
            throw DataGenerationException("Wrong Range configuration for ${ctx.attribute.name}")
        }
        val type = ctx.attribute.type
        return when {
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.INTEGER -> generatorFactory.callGenerator("random.integer", ctx) as Long
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.BIG_INTEGER -> BigInteger.valueOf(generatorFactory.callGenerator("random.integer", ctx) as Long)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.DECIMAL -> BigDecimal(generatorFactory.callGenerator("random.decimal", ctx) as Double)
            type is RR_Type.Primitive && type.kind == RR_PrimitiveKind.ROWID -> generatorFactory.callGenerator("random.integer", ctx) as Long
            else -> throw DataGenerationException("Range configuration not applicable for type ${ctx.attribute.type}")
        }
    }
}
