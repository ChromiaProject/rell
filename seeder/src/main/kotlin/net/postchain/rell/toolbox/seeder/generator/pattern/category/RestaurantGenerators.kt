package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class RestaurantGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("restaurant.name") { selectGenerator(it, it.faker.restaurant::name, it.faker.restaurant.unique::name) }
        register("restaurant.type") { selectGenerator(it, it.faker.restaurant::type, it.faker.restaurant.unique::type) }
        register("restaurant.description") {
            selectGenerator(
                it,
                it.faker.restaurant::description,
                it.faker.restaurant.unique::description
            )
        }
        register("restaurant.review") {
            selectGenerator(
                it,
                it.faker.restaurant::review,
                it.faker.restaurant.unique::review
            )
        }
    }
}
