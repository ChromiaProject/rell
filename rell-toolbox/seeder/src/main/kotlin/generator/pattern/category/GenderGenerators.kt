/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class GenderGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("gender.types") { selectGenerator(it, it.faker.gender::types, it.faker.gender.unique::types) }
        register("gender.binary_type") {
            selectGenerator(
                it,
                it.faker.gender::binaryTypes,
                it.faker.gender.unique::binaryTypes
            )
        }
        register("gender.short_binary_type") {
            selectGenerator(
                it,
                it.faker.gender::shortBinaryTypes,
                it.faker.gender.unique::shortBinaryTypes
            )
        }
    }
}
