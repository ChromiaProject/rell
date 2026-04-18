/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

// Combined Business with finance provider
class BusinessGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("credit_card.number") {
            selectGenerator(
                it,
                { it.faker.finance.creditCard("") },
                { it.faker.finance.unique.creditCard("") },
            )
        }
        register("credit_card.type") {
            selectGenerator(
                it,
                it.faker.business::creditCardTypes,
                it.faker.business::creditCardTypes,
            )
        }
        register("finance.vat_number") {
            selectGenerator(
                it,
                { it.faker.finance.vatNumber("") },
                { it.faker.finance.unique.vatNumber("") },
            )
        }
        register("finance.ticker") {
            selectGenerator(
                it,
                it.faker.finance::ticker,
                it.faker.finance.unique::ticker,
            )
        }
    }
}
