/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern

import io.github.serpro69.kfaker.Faker
import io.github.serpro69.kfaker.fakerConfig
import net.postchain.rell.toolbox.seeder.schema.Attribute
import net.postchain.rell.toolbox.seeder.schema.Entity
import net.postchain.rell.toolbox.seeder.generator.DataGeneratorContext
import net.postchain.rell.toolbox.seeder.generator.UniqueProducer
import net.postchain.rell.toolbox.seeder.generator.pattern.category.*

class FakerGeneratorFactory {
    private val registry = GeneratorRegistry()
    private val attributeFakerGenerators = mutableMapOf<String, FakerGenerator>()

    init {
        registerAllGenerators()
    }

    fun getRegistry(): GeneratorRegistry = registry

    fun callGenerator(name: String, ctx: DataGeneratorContext) = registry.getOrThrow(name).generate(ctx)

    private fun registerAllGenerators() {
        val generatorCategories = listOf(
            PersonGenerators(registry),
            InternetGenerators(registry),
            CompanyGenerators(registry),
            AddressGenerators(registry),
            PhoneGenerators(registry),
            RandomGenerators(registry),
            BankGenerators(registry),
            ColorGenerators(registry),
            CommerceGenerators(registry),
            CryptoGenerators(registry),
            CurrencyGenerators(registry),
            FileGenerators(registry),
            GenderGenerators(registry),
            MeasurementGenerators(registry),
            MoneyGenerators(registry),
            EduGenerators(registry),
            LoremGenerators(registry),
            MiscGenerators(registry),
            SportsGenerators(registry),
            TechGenerators(registry),
            TravelGenerators(registry),
            BeerGenerators(registry),
            BusinessGenerators(registry),
            CoffeeGenerators(registry),
            CreatureGenerators(registry),
            DessertGenerators(registry),
            FoodGenerators(registry),
            HouseGenerators(registry),
            IndustryGenerators(registry),
            RestaurantGenerators(registry),
            StripeGenerators(registry),
            SubscriptionGenerators(registry),
            TeaGenerators(registry),
            BarcodeGenerators(registry),
        )

        generatorCategories.forEach { it.register() }
    }

    fun getAttributeFakerGenerator(entity: Entity, attribute: Attribute): FakerGenerator {
        val fakerId = entity.uniqueName + "." + attribute.name
        return attributeFakerGenerators.computeIfAbsent(fakerId) {
            FakerGenerator(
                registry,
                Faker(fakerConfig { uniqueGeneratorRetryLimit = MAX_RETRIES }),
                UniqueProducer(maxRetries = MAX_RETRIES)
            )
        }
    }

    fun clearAttributeFakerGenerators() {
        attributeFakerGenerators.clear()
    }

    companion object {
        val default = FakerGeneratorFactory()
        private const val MAX_RETRIES = 1000
    }
}

class FakerGenerator(val registry: GeneratorRegistry, val faker: Faker, val uniqueProducer: UniqueProducer<Any>)
