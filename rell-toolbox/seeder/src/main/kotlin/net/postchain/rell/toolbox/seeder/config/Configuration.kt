/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.config

import net.postchain.rell.toolbox.seeder.Attribute

data class Configuration(
    val modules: Map<String, ModuleConfig> = emptyMap()
) {
    // TODO: should we generate data for non-configured entities that isn't a reference?
    //  With the null handling ?: EntityConfig(entityName) we always gets config and hence generate data
    //  Consider if we should add a flag that controls this behaviour
    fun findEntityConfig(entityName: String): EntityConfig {
        return modules.values.firstNotNullOfOrNull { it.entityConfigs[entityName] } ?: EntityConfig(entityName)
    }
}

// TODO: create PR to add json schemas to schemastore.org
// Configuration for the https://www.schemastore.org/json/
//  Main seeder config
//  "fileMatch": [".chromia/seeder/**/config.yml"]
//  Modules
//  "fileMatch": [".chromia/seeder/**/modules/**/*.yml"]
data class ModuleConfig(
    val moduleName: String,
    val entityConfigs: Map<String, EntityConfig> = emptyMap(),
)

data class EntityConfig(
    val name: String,
    val attributes: Map<String, AttributeConfig> = emptyMap(),
    val count: Int = 10, // Default number of records to generate
)

sealed class AttributeConfig {
    data class PredefinedValues(
        val values: List<Any>,
        val distribution: Distribution? = null
    ) : AttributeConfig()

    data class Range(
        val min: Number,
        val max: Number
    ) : AttributeConfig()

    data class TextConfig(
        val min: Int? = null,
        val max: Int? = null
    ) : AttributeConfig()

    data class ByteArrayConfig(val size: Int? = null) : AttributeConfig()

    data class DataPatternConfig(val pattern: String) : AttributeConfig()

    data class CustomGenerator(val generator: (Attribute) -> Any?) : AttributeConfig()
}

enum class Distribution {
    SEQUENTIAL, // Use values in sequence
    RANDOM, // Pick values randomly
    WEIGHTED // Pick values based on weights (requires additional configuration)
}
