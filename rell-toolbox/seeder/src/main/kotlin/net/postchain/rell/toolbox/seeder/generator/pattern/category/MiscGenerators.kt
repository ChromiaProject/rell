package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class MiscGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        // Artist
        register("artist.names") {
            selectGenerator(it, it.faker.artist::names, it.faker.artist.unique::names)
        }

        // Blood
        register("blood.type") {
            selectGenerator(it, it.faker.blood::type, it.faker.blood.unique::type)
        }
        register("blood.rh_factor") {
            selectGenerator(it, it.faker.blood::rhFactor, it.faker.blood.unique::rhFactor)
        }
        register("blood.group") {
            selectGenerator(it, it.faker.blood::group, it.faker.blood.unique::group)
        }

        // Demographic
        register("demographic.race") {
            selectGenerator(it, it.faker.demographic::race, it.faker.demographic.unique::race)
        }
        register("demographic.sex") {
            selectGenerator(it, it.faker.demographic::sex, it.faker.demographic.unique::sex)
        }
        register("demographic.demonym") {
            selectGenerator(it, it.faker.demographic::demonym, it.faker.demographic.unique::demonym)
        }
        register("demographic.educational_attainment") {
            selectGenerator(
                it,
                it.faker.demographic::educationalAttainment,
                it.faker.demographic.unique::educationalAttainment
            )
        }
        register("demographic.marital_status") {
            selectGenerator(it, it.faker.demographic::maritalStatus, it.faker.demographic.unique::maritalStatus)
        }

        // Driving license
        register("driving_license.license") {
            selectGenerator(it, it.faker.drivingLicense::license, it.faker.drivingLicense.unique::license)
        }

        // Greek philosophers
        register("greek_philosophers.names") {
            selectGenerator(it, it.faker.greekPhilosophers::names, it.faker.greekPhilosophers.unique::names)
        }
        register("greek_philosophers.quotes") {
            selectGenerator(it, it.faker.greekPhilosophers::quotes, it.faker.greekPhilosophers.unique::quotes)
        }

        // Hobby
        register("hobby.activity") {
            selectGenerator(it, it.faker.hobby::activity, it.faker.hobby.unique::activity)
        }

        // Military
        register("military.army_rank") {
            selectGenerator(it, it.faker.military::armyRank, it.faker.military.unique::armyRank)
        }
        register("military.marines_rank") {
            selectGenerator(it, it.faker.military::marinesRank, it.faker.military.unique::marinesRank)
        }
        register("military.navy_rank") {
            selectGenerator(it, it.faker.military::navyRank, it.faker.military.unique::navyRank)
        }
        register("military.coast_guard_rank") {
            selectGenerator(it, it.faker.military::coastGuardRank, it.faker.military.unique::coastGuardRank)
        }
        register("military.air_force_rank") {
            selectGenerator(it, it.faker.military::airForceRank, it.faker.military.unique::airForceRank)
        }
        register("military.space_force_rank") {
            selectGenerator(it, it.faker.military::spaceForceRank, it.faker.military.unique::spaceForceRank)
        }
        register("military.dod_paygrade") {
            selectGenerator(it, it.faker.military::dodPaygrade, it.faker.military.unique::dodPaygrade)
        }
    }
}
