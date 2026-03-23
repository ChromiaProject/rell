/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class HouseGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("house.furniture") { selectGenerator(it, it.faker.house::furniture, it.faker.house.unique::furniture) }
        register("house.rooms") { selectGenerator(it, it.faker.house::rooms, it.faker.house.unique::rooms) }
    }
}
