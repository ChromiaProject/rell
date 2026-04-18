/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class CommerceGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("commerce.department") {
            selectGenerator(
                it,
                it.faker.commerce::department,
                it.faker.commerce.unique::department
            )
        }
        register("commerce.product_name") {
            selectGenerator(
                it,
                it.faker.commerce::productName,
                it.faker.commerce.unique::productName
            )
        }
        register("commerce.promotion_code") {
            selectGenerator(
                it,
                it.faker.commerce::promotionCode,
                it.faker.commerce.unique::promotionCode
            )
        }
        register("commerce.brand") { selectGenerator(it, it.faker.commerce::brand, it.faker.commerce.unique::brand) }
        register("commerce.vendor") {
            selectGenerator(
                it,
                it.faker.commerce::vendor,
                it.faker.commerce.unique::vendor
            )
        }
    }
}
