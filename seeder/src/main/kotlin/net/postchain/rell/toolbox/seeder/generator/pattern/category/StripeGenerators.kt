package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class StripeGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("stripe.valid_card") {
            selectGenerator(
                it,
                { it.faker.stripe.validCards("") },
                { it.faker.stripe.unique.validCards("") }
            )
        }
        register("stripe.valid_token") {
            selectGenerator(
                it,
                { it.faker.stripe.validTokens("") },
                { it.faker.stripe.unique.validTokens("") }
            )
        }
        register("stripe.invalid_card") {
            selectGenerator(
                it,
                { it.faker.stripe.invalidCards("") },
                { it.faker.stripe.unique.invalidCards("") }
            )
        }
    }
}
