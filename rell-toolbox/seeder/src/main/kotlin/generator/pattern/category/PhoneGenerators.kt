/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class PhoneGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("phone_number") {
            selectGenerator(it, it.faker.phoneNumber::phoneNumber, it.faker.phoneNumber.unique::phoneNumber)
        }
        register("phone_number.area_code") {
            selectGenerator(it, it.faker.phoneNumber::areaCode, it.faker.phoneNumber.unique::areaCode)
        }
        register("phone_number.exchange_code") {
            selectGenerator(it, it.faker.phoneNumber::exchangeCode, it.faker.phoneNumber.unique::exchangeCode)
        }
        register("phone_number.country_code") {
            selectGenerator(it, { it.faker.phoneNumber.countryCode() }, { it.faker.phoneNumber.unique.countryCode() })
        }
    }
}