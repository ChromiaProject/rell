/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class DessertGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("dessert.flavor") { selectGenerator(it, it.faker.dessert::flavor, it.faker.dessert.unique::flavor) }
        register("dessert.topping") { selectGenerator(it, it.faker.dessert::topping, it.faker.dessert.unique::topping) }
        register("dessert.variety") { selectGenerator(it, it.faker.dessert::variety, it.faker.dessert.unique::variety) }
        register("dessert") { selectGenerator(it, { it.faker.dessert.dessert()() }, { it.faker.dessert.unique.dessert()() }) }
    }
}
