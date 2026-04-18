/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class InternetGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("email") { selectGenerator(it, it.faker.internet::email, it.faker.internet.unique::email) }
        register("internet.domain") { selectGenerator(it, it.faker.internet::domain, it.faker.internet.unique::domain) }
        register("internet.private_ipv4_addr") {
            selectGenerator(
                it,
                it.faker.internet::privateIPv4Address,
                it.faker.internet.unique::privateIPv4Address
            )
        }
        register("internet.public_ipv4_addr") {
            selectGenerator(
                it,
                it.faker.internet::publicIPv4Address,
                it.faker.internet.unique::publicIPv4Address
            )
        }
        register("internet.ipv4_addr") {
            selectGenerator(
                it,
                it.faker.internet::iPv4Address,
                it.faker.internet.unique::iPv4Address
            )
        }
        register("internet.ipv6_addr") {
            selectGenerator(
                it,
                it.faker.internet::iPv6Address,
                it.faker.internet.unique::iPv6Address
            )
        }
        register("internet.mac_addr") {
            selectGenerator(
                it,
                it.faker.internet::macAddress,
                it.faker.internet.unique::macAddress
            )
        }
        register("internet.safe_email") {
            selectGenerator(
                it,
                it.faker.internet::safeEmail,
                it.faker.internet.unique::safeEmail
            )
        }
        register("internet.slug") {
            selectGenerator(it, it.faker.internet::slug, it.faker.internet.unique::slug)
        }
        register("internet.domain_suffix") {
            selectGenerator(it, it.faker.internet::domainSuffix, it.faker.internet.unique::domainSuffix)
        }
        register("internet.user_agent") {
            selectGenerator(
                it,
                { it.faker.internet.userAgent("") },
                { it.faker.internet.unique.userAgent("") }
            )
        }
        register("internet.bot_user_agent") {
            selectGenerator(
                it,
                { it.faker.internet.botUserAgent("") },
                { it.faker.internet.unique.botUserAgent("") }
            )
        }
    }
}