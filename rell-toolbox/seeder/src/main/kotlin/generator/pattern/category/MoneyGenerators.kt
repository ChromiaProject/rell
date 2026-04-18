/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class MoneyGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("money.amount") {
            // TODO: Money amount generator is very slow, replace it with a faster implementation
            selectGenerator(it, { it.faker.money.amount(0..<1000) }, { it.faker.money.unique.amount(0..<1000) })
        }
    }
}
