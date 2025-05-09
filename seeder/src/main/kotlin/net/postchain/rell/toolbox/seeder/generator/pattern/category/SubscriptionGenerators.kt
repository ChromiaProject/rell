package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class SubscriptionGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("subscription.plan") {
            selectGenerator(it, it.faker.subscription::plans, it.faker.subscription.unique::plans)
        }
        register("subscription.status") {
            selectGenerator(it, it.faker.subscription::statuses, it.faker.subscription.unique::statuses)
        }
        register("subscription.payment_method") {
            selectGenerator(it, it.faker.subscription::paymentMethods, it.faker.subscription.unique::paymentMethods)
        }
        register("subscription.term") {
            selectGenerator(
                it,
                it.faker.subscription::subscriptionTerms,
                it.faker.subscription.unique::subscriptionTerms
            )
        }
        register("subscription.payment_term") {
            selectGenerator(
                it,
                it.faker.subscription::paymentTerms,
                it.faker.subscription.unique::paymentTerms
            )
        }
    }
}
