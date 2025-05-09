package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class AddressGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("address.full") {
            selectGenerator(
                it,
                it.faker.address::fullAddress,
                it.faker.address
                    .unique::fullAddress
            )
        }
        register("address.city") { selectGenerator(it, it.faker.address::city, it.faker.address.unique::city) }
        register("address.country") { selectGenerator(it, it.faker.address::country, it.faker.address.unique::country) }
        register("address.country_code") {
            selectGenerator(
                it,
                it.faker.address::countryCode,
                it.faker.address
                    .unique::countryCode
            )
        }
        register("address.street") {
            selectGenerator(it, it.faker.address::streetAddress, it.faker.address.unique::streetAddress)
        }
        register("address.country_code_long") {
            selectGenerator(it, it.faker.address::countryCodeLong, it.faker.address.unique::countryCodeLong)
        }
        register("address.building_number") {
            selectGenerator(it, it.faker.address::buildingNumber, it.faker.address.unique::buildingNumber)
        }
        register("address.community") {
            selectGenerator(it, it.faker.address::community, it.faker.address.unique::community)
        }
        register("address.secondary_address") {
            selectGenerator(it, it.faker.address::secondaryAddress, it.faker.address.unique::secondaryAddress)
        }
        register("address.postcode") {
            selectGenerator(it, it.faker.address::postcode, it.faker.address.unique::postcode)
        }
        register("address.state") {
            selectGenerator(it, it.faker.address::state, it.faker.address.unique::state)
        }
        register("address.state_abbr") {
            selectGenerator(it, it.faker.address::stateAbbr, it.faker.address.unique::stateAbbr)
        }
        register("address.time_zone") {
            selectGenerator(it, it.faker.address::timeZone, it.faker.address.unique::timeZone)
        }
        register("address.city_with_state") {
            selectGenerator(it, it.faker.address::cityWithState, it.faker.address.unique::cityWithState)
        }
        register("address.street_name") {
            selectGenerator(it, it.faker.address::streetName, it.faker.address.unique::streetName)
        }
        register("address.mailbox") {
            selectGenerator(it, it.faker.address::mailbox, it.faker.address.unique::mailbox)
        }
    }
}
