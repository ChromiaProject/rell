package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class BankGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("bank.name") { selectGenerator(it, it.faker.bank::name, it.faker.bank.unique::name) }
        register("bank.swift_bic") { selectGenerator(it, it.faker.bank::swiftBic, it.faker.bank.unique::swiftBic) }
        register("bank.iban_details") {
            selectGenerator(
                it,
                { it.faker.bank.ibanDetails("") },
                { it.faker.bank.unique.ibanDetails("") }
            )
        }
    }
}
