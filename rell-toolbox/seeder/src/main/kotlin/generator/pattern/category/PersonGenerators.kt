/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class PersonGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("first_name") { selectGenerator(it, it.faker.name::firstName, it.faker.name.unique::firstName) }
        register("last_name") { selectGenerator(it, it.faker.name::lastName, it.faker.name.unique::lastName) }
        register("name") { selectGenerator(it, it.faker.name::name, it.faker.name.unique::name) }
        register("male_first_name") {
            selectGenerator(
                it,
                it.faker.name::maleFirstName,
                it.faker.name.unique::maleFirstName
            )
        }
        register("female_first_name") {
            selectGenerator(
                it,
                it.faker.name::femaleFirstName,
                it.faker.name.unique::femaleFirstName
            )
        }
        register("neutral_first_name") {
            selectGenerator(
                it,
                it.faker.name::neutralFirstName,
                it.faker.name.unique::neutralFirstName
            )
        }
        register("name_with_middle") {
            selectGenerator(
                it,
                it.faker.name::nameWithMiddle,
                it.faker.name.unique::nameWithMiddle
            )
        }
    }
}
