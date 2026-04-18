/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class CurrencyGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("currency.code") { selectGenerator(it, it.faker.currency::code, it.faker.currency.unique::code) }
        register("currency.name") { selectGenerator(it, it.faker.currency::name, it.faker.currency.unique::name) }
        register("currency.symbol") { selectGenerator(it, it.faker.currency::symbol, it.faker.currency.unique::symbol) }
    }
}
