package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class IndustryGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("industry") {
            selectGenerator(
                it,
                it.faker.industrySegments::industry,
                it.faker.industrySegments.unique::superSector
            )
        }
        register("industry.super_sector") {
            selectGenerator(it, it.faker.industrySegments::superSector, it.faker.industrySegments.unique::superSector)
        }
        register("industry.sector") {
            selectGenerator(it, it.faker.industrySegments::sector, it.faker.industrySegments.unique::sector)
        }
        register("industry.sub_sector") {
            selectGenerator(it, it.faker.industrySegments::subSector, it.faker.industrySegments.unique::subSector)
        }
    }
}
