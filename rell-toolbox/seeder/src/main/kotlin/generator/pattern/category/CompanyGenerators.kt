/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class CompanyGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("company.name") { selectGenerator(it, it.faker.company::name, it.faker.company.unique::name) }
        register("company.department") {
            selectGenerator(it, it.faker.company::department, it.faker.company.unique::department)
        }
        register(
            "company.industry"
        ) { selectGenerator(it, it.faker.company::industry, it.faker.company.unique::industry) }
        register("company.profession") {
            selectGenerator(it, it.faker.company::profession, it.faker.company.unique::profession)
        }
        register("company.type") {
            selectGenerator(it, it.faker.company::type, it.faker.company.unique::type)
        }
        register("company.sic_code") {
            selectGenerator(
                it,
                it.faker.company::sicCode,
                it.faker.company.unique::sicCode
            )
        }
    }
}
