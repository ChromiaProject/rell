/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import net.postchain.rell.toolbox.seeder.schema.RellSchema
import kotlin.random.Random

class DataGenerator(
    private val generatorFactory: FakerGeneratorFactory = FakerGeneratorFactory(),
    randomSeed: Long = System.currentTimeMillis()
) {
    private val random = Random(randomSeed)

    companion object {
        const val TEXT_MIN_DEFAULT = 1
        const val TEXT_MAX_DEFAULT = 10
    }

    fun generate(rellSchema: RellSchema, config: Configuration): GeneratedData {
        val entityData = mutableMapOf<String, List<EntityRecord>>()
        val recordGenerator = EntityRecordGenerator(random, generatorFactory)

        for (entity in rellSchema.entities) {
            val entityConfig = config.findEntityConfig(entity.qualifiedName)
            val records = recordGenerator.generateEntityRecords(entity, entityConfig, rellSchema, entityData, config)
            entityData[entity.uniqueName] = records
        }

        return GeneratedData(entityData)
    }
}

class DataGenerationException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
