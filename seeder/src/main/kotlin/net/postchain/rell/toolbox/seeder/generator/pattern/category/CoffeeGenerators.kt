package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class CoffeeGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("coffee.blend_name") {
            selectGenerator(
                it,
                it.faker.coffee::blendName,
                it.faker.coffee.unique::blendName
            )
        }
        register("coffee.country") { selectGenerator(it, it.faker.coffee::country, it.faker.coffee.unique::country) }
        register("coffee.notes") { selectGenerator(it, it.faker.coffee::notes, it.faker.coffee.unique::notes) }
        register("coffee.variety") { selectGenerator(it, it.faker.coffee::variety, it.faker.coffee.unique::variety) }
        register("coffee.region") {
            selectGenerator(
                it,
                { it.faker.coffee.regions("") },
                { it.faker.coffee.unique.regions("") },
            )
        }
    }
}
