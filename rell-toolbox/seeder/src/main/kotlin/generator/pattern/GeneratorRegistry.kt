/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern

class GeneratorRegistry {
    private val generators = mutableMapOf<String, DataPatternGenerator>()

    fun register(generator: DataPatternGenerator) {
        require(!generators.containsKey(generator.identifier)) {
            "Generator with identifier '${generator.identifier}' already exists"
        }
        generators[generator.identifier] = generator
    }

    fun unregister(identifier: String) {
        generators.remove(identifier)
    }

    fun getOrNull(identifier: String): DataPatternGenerator? = generators[identifier]

    fun getOrThrow(identifier: String): DataPatternGenerator =
        generators[identifier] ?: throw IllegalArgumentException("No generator found for identifier: $identifier")

    fun getAllGenerators(): Map<String, DataPatternGenerator> = generators.toMap()
}
