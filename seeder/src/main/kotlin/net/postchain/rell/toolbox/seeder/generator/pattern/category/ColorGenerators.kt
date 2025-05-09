package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class ColorGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("color.name") { selectGenerator(it, it.faker.color::name, it.faker.color.unique::name) }
    }
}
