/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.base.model.R_BooleanType
import net.postchain.rell.base.model.R_DecimalType
import net.postchain.rell.base.model.R_IntegerType
import net.postchain.rell.base.model.rr.RR_Type
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.generator.DataGenerationException
import net.postchain.rell.toolbox.seeder.generator.DataGenerator
import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class RandomGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register(
            "random.integer",
            type = R_IntegerType
        ) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            if (ctx.attributeConfig is AttributeConfig.Range) {
                val min = ctx.attributeConfig.min.toLong()
                val maxFromConfig = ctx.attributeConfig.max.toLong()
                val max = if (maxFromConfig == Long.MAX_VALUE) maxFromConfig - 1 else maxFromConfig
                (randomProvider.nextLong(max - min + 1) + min)
            } else {
                randomProvider.nextLong()
            }
        }
        register(
            "random.boolean",
            type = R_BooleanType
        ) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            randomProvider.nextBoolean()
        }

        register(
            "random.decimal",
            type = R_DecimalType
        ) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            if (ctx.attributeConfig is AttributeConfig.Range) {
                val min = ctx.attributeConfig.min.toDouble()
                val max = ctx.attributeConfig.max.toDouble()
                min + (max - min) * randomProvider.nextDouble()
            } else {
                randomProvider.nextDouble()
            }
        }

        register("random.enum", R_IntegerType) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            val type = ctx.attribute.type
            if (type !is RR_Type.Enum) {
                throw DataGenerationException(
                    "Expected enum, but got ${type::class.simpleName} for `${ctx.attribute.name}` of entity `${ctx.entity.qualifiedName}`"
                )
            }
            val enumDef = ctx.attribute.enumDefinition
                ?: throw DataGenerationException("Enum definition not found for attribute `${ctx.attribute.name}`")
            randomProvider.randomValue(enumDef.attrs.map { it.value })
        }

        register(
            "random.uuid"
        ) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            randomProvider.nextUUID()
        }
        // TODO: check how it works with Rell tuid type
        // TODO: add more random generators if needed
        register(
            "random.text"
        ) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            if (ctx.attributeConfig is AttributeConfig.TextConfig) {
                val min = ctx.attributeConfig.min ?: DataGenerator.TEXT_MIN_DEFAULT
                val max = ctx.attributeConfig.max ?: DataGenerator.TEXT_MAX_DEFAULT
                randomProvider.randomString(min, max)
            } else {
                randomProvider.randomString(
                    DataGenerator.TEXT_MIN_DEFAULT,
                    DataGenerator.TEXT_MAX_DEFAULT
                )
            }
        }

        register(
            "random.json"
        ) { ctx ->
            val randomProvider = if (ctx.isAttributeUnique()) ctx.faker.random.unique else ctx.faker.random
            val id = randomProvider.nextInt()
            """{"id": $id}"""
        }
    }
}
