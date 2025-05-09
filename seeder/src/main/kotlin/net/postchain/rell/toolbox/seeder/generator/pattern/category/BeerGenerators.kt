package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class BeerGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("beer.name") { selectGenerator(it, it.faker.beer::name, it.faker.beer.unique::name) }
        register("beer.style") { selectGenerator(it, it.faker.beer::style, it.faker.beer.unique::style) }
        register("beer.hop") { selectGenerator(it, it.faker.beer::hop, it.faker.beer.unique::hop) }
        register("beer.yeast") { selectGenerator(it, it.faker.beer::yeast, it.faker.beer.unique::yeast) }
        register("beer.malts") { selectGenerator(it, it.faker.beer::malt, it.faker.beer.unique::malt) }
        register("beer.brand") { selectGenerator(it, it.faker.beer::brand, it.faker.beer.unique::brand) }
    }
}
