/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class TeaGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("tea.type") { selectGenerator(it, it.faker.tea::type, it.faker.tea.unique::type) }
        register("tea.variety.black") {
            selectGenerator(
                it,
                it.faker.tea.variety::black,
                it.faker.tea.variety.unique::black
            )
        }
        register("tea.variety.green") {
            selectGenerator(
                it,
                it.faker.tea.variety::green,
                it.faker.tea.variety.unique::green
            )
        }
        register("tea.variety.white") {
            selectGenerator(
                it,
                it.faker.tea.variety::white,
                it.faker.tea.variety.unique::white
            )
        }
        register("tea.variety.oolong") {
            selectGenerator(
                it,
                it.faker.tea.variety::oolong,
                it.faker.tea.variety.unique::oolong
            )
        }
        register("tea.variety.herbal") {
            selectGenerator(
                it,
                it.faker.tea.variety::herbal,
                it.faker.tea.variety.unique::herbal
            )
        }
    }
}
