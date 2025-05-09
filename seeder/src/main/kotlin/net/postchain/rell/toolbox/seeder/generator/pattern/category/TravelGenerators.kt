package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class TravelGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        // Airport
        register("airport.usa.large") {
            selectGenerator(it, it.faker.airport.unitedStates::large, it.faker.airport.unique.unitedStates::large)
        }
        register("airport.usa.medium") {
            selectGenerator(it, it.faker.airport.unitedStates::medium, it.faker.airport.unique.unitedStates::medium)
        }
        register("airport.usa.small") {
            selectGenerator(it, it.faker.airport.unitedStates::small, it.faker.airport.unique.unitedStates::small)
        }
        register("airport.usa.iata.large") {
            selectGenerator(
                it,
                it.faker.airport.unitedStates.iataCode::large,
                it.faker.airport.unique.unitedStates.iataCode::large
            )
        }
        register("airport.usa.iata.medium") {
            selectGenerator(
                it,
                it.faker.airport.unitedStates.iataCode::medium,
                it.faker.airport.unique.unitedStates.iataCode::medium
            )
        }
        register("airport.usa.iata.small") {
            selectGenerator(
                it,
                it.faker.airport.unitedStates.iataCode::small,
                it.faker.airport.unique.unitedStates.iataCode::small
            )
        }
        register("airport.eu.large") {
            selectGenerator(it, it.faker.airport.europeanUnion::large, it.faker.airport.unique.europeanUnion::large)
        }
        register("airport.eu.medium") {
            selectGenerator(it, it.faker.airport.europeanUnion::medium, it.faker.airport.unique.europeanUnion::medium)
        }
        register("airport.eu.iata.large") {
            selectGenerator(
                it,
                it.faker.airport.europeanUnion.iataCode::large,
                it.faker.airport.unique.europeanUnion.iataCode::large
            )
        }
        register("airport.eu.iata.medium") {
            selectGenerator(
                it,
                it.faker.airport.europeanUnion.iataCode::medium,
                it.faker.airport.unique.europeanUnion.iataCode::medium
            )
        }

        // Australia
        register("australia.locations") {
            selectGenerator(it, it.faker.australia::locations, it.faker.australia.unique::locations)
        }
        register("australia.animals") {
            selectGenerator(it, it.faker.australia::animals, it.faker.australia.unique::animals)
        }
        register("australia.states") {
            selectGenerator(it, it.faker.australia::states, it.faker.australia.unique::states)
        }

        // Mountains
        register("mountain.name") {
            selectGenerator(it, it.faker.mountain::name, it.faker.mountain.unique::name)
        }
        register("mountain.range") {
            selectGenerator(it, it.faker.mountain::range, it.faker.mountain.unique::range)
        }

        // Nation
        register("nation.nationality") {
            selectGenerator(it, it.faker.nation::nationality, it.faker.nation.unique::nationality)
        }
        register("nation.capital_city") {
            selectGenerator(it, it.faker.nation::capitalCity, it.faker.nation.unique::capitalCity)
        }
        register("nation.language") {
            selectGenerator(it, it.faker.nation::language, it.faker.nation.unique::language)
        }

        // Train Station
        register("train_station.uk.metro") {
            selectGenerator(
                it,
                it.faker.trainStation.unitedKingdom::metro,
                it.faker.trainStation.unitedKingdom.unique::metro
            )
        }
        register("train_station.uk.railway") {
            selectGenerator(
                it,
                it.faker.trainStation.unitedKingdom::railway,
                it.faker.trainStation.unitedKingdom.unique::railway
            )
        }
        register("train_station.spain.metro") {
            selectGenerator(it, it.faker.trainStation.spain::metro, it.faker.trainStation.spain.unique::metro)
        }
        register("train_station.spain.railway") {
            selectGenerator(it, it.faker.trainStation.spain::railway, it.faker.trainStation.spain.unique::railway)
        }
        register("train_station.germany.metro") {
            selectGenerator(it, it.faker.trainStation.germany::metro, it.faker.trainStation.germany.unique::metro)
        }
        register("train_station.germany.railway") {
            selectGenerator(it, it.faker.trainStation.germany::railway, it.faker.trainStation.germany.unique::railway)
        }
        register("train_station.usa.metro") {
            selectGenerator(
                it,
                it.faker.trainStation.unitedStates::metro,
                it.faker.trainStation.unitedStates.unique::metro
            )
        }
        register("train_station.usa.railway") {
            selectGenerator(
                it,
                it.faker.trainStation.unitedStates::railway,
                it.faker.trainStation.unitedStates.unique::railway
            )
        }
    }
}
