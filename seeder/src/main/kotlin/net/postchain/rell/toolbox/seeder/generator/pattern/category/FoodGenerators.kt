package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class FoodGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("food.allergens") {
            selectGenerator(
                it,
                it.faker.food::allergens,
                it.faker.food.unique::allergens
            )
        }
        register("food.dish") {
            selectGenerator(
                it,
                it.faker.food::dish,
                it.faker.food.unique::dish
            )
        }
        register("food.description") {
            selectGenerator(
                it,
                it.faker.food::descriptions,
                it.faker.food.unique::descriptions
            )
        }
        register("food.ingredients") {
            selectGenerator(
                it,
                it.faker.food::ingredients,
                it.faker.food.unique::ingredients
            )
        }
        register("food.fruits") {
            selectGenerator(
                it,
                it.faker.food::fruits,
                it.faker.food.unique::fruits
            )
        }
        register("food.vegetables") {
            selectGenerator(
                it,
                it.faker.food::vegetables,
                it.faker.food.unique::vegetables
            )
        }
        register("food.spices") {
            selectGenerator(
                it,
                it.faker.food::spices,
                it.faker.food.unique::spices
            )
        }
        register("food.measurement_sizes") {
            selectGenerator(
                it,
                it.faker.food::measurementSizes,
                it.faker.food.unique::measurementSizes
            )
        }
        register("food.measurements") {
            selectGenerator(
                it,
                it.faker.food::measurements,
                it.faker.food.unique::measurements
            )
        }
        register("food.metric_measurements") {
            selectGenerator(
                it,
                it.faker.food::metricMeasurements,
                it.faker.food.unique::metricMeasurements
            )
        }
        register("food.sushi") {
            selectGenerator(
                it,
                it.faker.food::sushi,
                it.faker.food.unique::sushi
            )
        }
        register("food.ethnic_category") {
            selectGenerator(
                it,
                it.faker.food::ethnicCategory,
                it.faker.food.unique::ethnicCategory
            )
        }
    }
}
