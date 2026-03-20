package net.postchain.rell.toolbox.seeder.generator

import net.postchain.rell.base.lib.type.R_BigIntegerType
import net.postchain.rell.base.lib.type.R_DecimalType
import net.postchain.rell.base.lib.type.R_IntegerType
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import java.math.BigDecimal
import java.math.BigInteger
import net.postchain.rell.base.lib.type.R_RowidType

class NumberValueGenerator(val generatorFactory: FakerGeneratorFactory) {
    fun generateNumberInRange(ctx: DataGeneratorContext): Number {
        if (ctx.attributeConfig !is AttributeConfig.Range) {
            throw DataGenerationException("Wrong Range configuration for ${ctx.attribute.name}")
        }
        return when (ctx.attribute.type) {
            is R_IntegerType -> generatorFactory.callGenerator("random.integer", ctx) as Long
            is R_BigIntegerType -> BigInteger.valueOf(generatorFactory.callGenerator("random.integer", ctx) as Long)
            is R_DecimalType -> BigDecimal(generatorFactory.callGenerator("random.decimal", ctx) as Double)
            is R_RowidType -> generatorFactory.callGenerator("random.integer", ctx) as Long
            else -> throw DataGenerationException("Range configuration not applicable for type ${ctx.attribute.type}")
        }
    }
}
