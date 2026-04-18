/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class SportsGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        // Basketball
        register("basketball.teams") {
            selectGenerator(it, it.faker.basketball::teams, it.faker.basketball.unique::teams)
        }
        register("basketball.players") {
            selectGenerator(it, it.faker.basketball::players, it.faker.basketball.unique::players)
        }
        register("basketball.coaches") {
            selectGenerator(it, it.faker.basketball::coaches, it.faker.basketball.unique::coaches)
        }
        register("basketball.positions") {
            selectGenerator(it, it.faker.basketball::positions, it.faker.basketball.unique::positions)
        }

        // Chess
        register("chess.players") {
            selectGenerator(it, it.faker.chess::players, it.faker.chess.unique::players)
        }
        register("chess.tournaments") {
            selectGenerator(it, it.faker.chess::tournaments, it.faker.chess.unique::tournaments)
        }
        register("chess.openings") {
            selectGenerator(it, it.faker.chess::openings, it.faker.chess.unique::openings)
        }
        register("chess.titles") {
            selectGenerator(it, it.faker.chess::titles, it.faker.chess.unique::titles)
        }

        // Crossfit
        register("crossfit.competitions") {
            selectGenerator(it, it.faker.crossfit::competitions, it.faker.crossfit.unique::competitions)
        }
        register("crossfit.male_athletes") {
            selectGenerator(it, it.faker.crossfit::maleAthletes, it.faker.crossfit.unique::maleAthletes)
        }
        register("crossfit.female_athletes") {
            selectGenerator(it, it.faker.crossfit::femaleAthletes, it.faker.crossfit.unique::femaleAthletes)
        }
        register("crossfit.movements") {
            selectGenerator(it, it.faker.crossfit::movements, it.faker.crossfit.unique::movements)
        }
        register("crossfit.girl_workouts") {
            selectGenerator(it, it.faker.crossfit::girlWorkouts, it.faker.crossfit.unique::girlWorkouts)
        }
        register("crossfit.hero_workouts") {
            selectGenerator(it, it.faker.crossfit::heroWorkouts, it.faker.crossfit.unique::heroWorkouts)
        }

        // Esport
        register("esport.players") {
            selectGenerator(it, it.faker.eSport::players, it.faker.eSport.unique::players)
        }
        register("esport.teams") {
            selectGenerator(it, it.faker.eSport::teams, it.faker.eSport.unique::teams)
        }
        register("esport.events") {
            selectGenerator(it, it.faker.eSport::events, it.faker.eSport.unique::events)
        }
        register("esport.leagues") {
            selectGenerator(it, it.faker.eSport::leagues, it.faker.eSport.unique::leagues)
        }
        register("esport.games") {
            selectGenerator(it, it.faker.eSport::games, it.faker.eSport.unique::games)
        }

        // Football
        register("football.teams") {
            selectGenerator(it, it.faker.football::teams, it.faker.football.unique::teams)
        }
        register("football.players") {
            selectGenerator(it, it.faker.football::players, it.faker.football.unique::players)
        }
        register("football.coaches") {
            selectGenerator(it, it.faker.football::coaches, it.faker.football.unique::coaches)
        }
        register("football.competitions") {
            selectGenerator(it, it.faker.football::competitions, it.faker.football.unique::competitions)
        }
        register("football.positions") {
            selectGenerator(it, it.faker.football::teams, it.faker.football.unique::positions)
        }

        // Mountaineering
        register("mountaineering.mountaineer") {
            selectGenerator(it, it.faker.mountaineering::mountaineer, it.faker.mountaineering.unique::mountaineer)
        }

        // Sport
        register("sport.summer_olympics") {
            selectGenerator(it, it.faker.sport::summerOlympics, it.faker.sport.unique::summerOlympics)
        }
        register("sport.winter_olympics") {
            selectGenerator(it, it.faker.sport::winterOlympics, it.faker.sport.unique::winterOlympics)
        }
        register("sport.summer_paralympics") {
            selectGenerator(it, it.faker.sport::summerParalympics, it.faker.sport.unique::summerParalympics)
        }
        register("sport.winter_paralympics") {
            selectGenerator(it, it.faker.sport::winterParalympics, it.faker.sport.unique::winterParalympics)
        }
        register("sport.ancient_olympics") {
            selectGenerator(it, it.faker.sport::ancientOlympics, it.faker.sport.unique::ancientOlympics)
        }
        register("sport.unusual") {
            selectGenerator(it, it.faker.sport::unusual, it.faker.sport.unique::unusual)
        }

        // Team
        register("team.name") {
            selectGenerator(it, it.faker.team::name, it.faker.team.unique::name)
        }
        register("team.sport") {
            selectGenerator(it, it.faker.team::sport, it.faker.team.unique::sport)
        }
        register("team.mascot") {
            selectGenerator(it, it.faker.team::mascot, it.faker.team.unique::mascot)
        }

        // Volleyball
        register("volleyball.team") {
            selectGenerator(it, it.faker.volleyball::team, it.faker.volleyball.unique::team)
        }
        register("volleyball.player") {
            selectGenerator(it, it.faker.volleyball::player, it.faker.volleyball.unique::player)
        }
        register("volleyball.coach") {
            selectGenerator(it, it.faker.volleyball::coach, it.faker.volleyball.unique::coach)
        }
        register("volleyball.position") {
            selectGenerator(it, it.faker.volleyball::position, it.faker.volleyball.unique::position)
        }
        register("volleyball.formation") {
            selectGenerator(it, it.faker.volleyball::formation, it.faker.volleyball.unique::formation)
        }

        // World Cup
        register("world_cup.teams") {
            selectGenerator(it, it.faker.worldCup::teams, it.faker.worldCup.unique::teams)
        }
        register("world_cup.stadiums") {
            selectGenerator(it, it.faker.worldCup::stadiums, it.faker.worldCup.unique::stadiums)
        }
        register("world_cup.cities") {
            selectGenerator(it, it.faker.worldCup::cities, it.faker.worldCup.unique::cities)
        }
    }
}
