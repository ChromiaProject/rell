package net.postchain.rell.toolbox.seeder.generator

import net.postchain.rell.toolbox.seeder.Entity
import net.postchain.rell.toolbox.seeder.RellSchema
import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.EntityConfig
import net.postchain.rell.toolbox.seeder.generator.pattern.FakerGeneratorFactory
import kotlin.random.Random

class EntityRecordGenerator(
    random: Random,
    val generatorFactory: FakerGeneratorFactory
) {
    val fieldValueGenerator = FieldValueGenerator(random, generatorFactory)

    fun generateEntityRecords(
        entity: Entity,
        entityConfig: EntityConfig,
        rellSchema: RellSchema,
        existingEntityData: Map<String, List<EntityRecord>>,
        seederConfig: Configuration
    ): List<EntityRecord> {
        val records = mutableListOf<EntityRecord>()

        for (i in 0 until entityConfig.count) {
            val fieldValues = mutableMapOf<String, FieldValue<Any>?>()

            for (field in entity.attributes) {
                val fieldConfig = entityConfig.attributes[field.name]
                val attributeFakerGenerator = generatorFactory.getAttributeFakerGenerator(entity, field)
                val value = fieldValueGenerator.generateFieldValue(
                    field,
                    fieldConfig,
                    entity,
                    rellSchema,
                    existingEntityData,
                    seederConfig,
                    records,
                    fieldValues,
                    attributeFakerGenerator
                )
                fieldValues[field.name] = value
            }

            records.add(EntityRecord(entity.uniqueName, entity.simpleName, fieldValues))
        }

        generatorFactory.clearAttributeFakerGenerators()

        return records
    }
}
