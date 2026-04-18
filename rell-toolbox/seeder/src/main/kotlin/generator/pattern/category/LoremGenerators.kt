/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class LoremGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        // Adjective
        register("adjective.positive") {
            selectGenerator(it, it.faker.adjective::positive, it.faker.adjective.unique::positive)
        }
        register("adjective.negative") {
            selectGenerator(it, it.faker.adjective::negative, it.faker.adjective.unique::negative)
        }

        // Emotion
        register("emotion.adjective") {
            selectGenerator(it, it.faker.emotion::adjective, it.faker.emotion.unique::adjective)
        }
        register("emotion.noun") {
            selectGenerator(it, it.faker.emotion::noun, it.faker.emotion.unique::noun)
        }

        // Hipster
        register("hipster.words") {
            selectGenerator(it, it.faker.hipster::words, it.faker.hipster.unique::words)
        }

        // Lorem
        register("lorem.words") {
            selectGenerator(it, it.faker.lorem::words, it.faker.lorem.unique::words)
        }
        register("lorem.supplemental") {
            selectGenerator(it, it.faker.lorem::supplemental, it.faker.lorem.unique::supplemental)
        }
        register("lorem.punctuation") {
            selectGenerator(it, it.faker.lorem::punctuation, it.faker.lorem.unique::punctuation)
        }

        // Markdown
        register("markdown.headers") {
            selectGenerator(it, it.faker.markdown::headers, it.faker.markdown.unique::headers)
        }
        register("markdown.emphasis") {
            selectGenerator(it, it.faker.markdown::emphasis, it.faker.markdown.unique::emphasis)
        }

        // Nato
        register("nato.alphabet") {
            selectGenerator(it, it.faker.natoPhoneticAlphabet::codeWord, it.faker.natoPhoneticAlphabet.unique::codeWord)
        }

        // Slack emoji
        register("slack_emoji.people") {
            selectGenerator(it, it.faker.slackEmoji::people, it.faker.slackEmoji.unique::people)
        }
        register("slack_emoji.nature") {
            selectGenerator(it, it.faker.slackEmoji::nature, it.faker.slackEmoji.unique::nature)
        }
        register("slack_emoji.food_and_drink") {
            selectGenerator(it, it.faker.slackEmoji::foodAndDrink, it.faker.slackEmoji.unique::foodAndDrink)
        }
        register("slack_emoji.celebration") {
            selectGenerator(it, it.faker.slackEmoji::celebration, it.faker.slackEmoji.unique::celebration)
        }
        register("slack_emoji.activity") {
            selectGenerator(it, it.faker.slackEmoji::activity, it.faker.slackEmoji.unique::activity)
        }
        register("slack_emoji.travel_and_places") {
            selectGenerator(it, it.faker.slackEmoji::travelAndPlaces, it.faker.slackEmoji.unique::travelAndPlaces)
        }
        register("slack_emoji.objects_and_symbols") {
            selectGenerator(it, it.faker.slackEmoji::objectsAndSymbols, it.faker.slackEmoji.unique::objectsAndSymbols)
        }
        register("slack_emoji.custom") {
            selectGenerator(it, it.faker.slackEmoji::custom, it.faker.slackEmoji.unique::custom)
        }
        register("slack_emoji.emoji") {
            selectGenerator(it, it.faker.slackEmoji::emoji, it.faker.slackEmoji.unique::emoji)
        }

        // Quotes
        register("quote.famous_last_words") {
            selectGenerator(it, it.faker.quote::famousLastWords, it.faker.quote.unique::famousLastWords)
        }
        register("quote.matz") {
            selectGenerator(it, it.faker.quote::matz, it.faker.quote.unique::matz)
        }
        register("quote.most_interesting_man_in_the_world") {
            selectGenerator(
                it,
                it.faker.quote::mostInterestingManInTheWorld,
                it.faker.quote.unique::mostInterestingManInTheWorld
            )
        }
        register("quote.robin") {
            selectGenerator(it, it.faker.quote::robin, it.faker.quote.unique::robin)
        }
        register("quote.singular_siegler") {
            selectGenerator(it, it.faker.quote::singularSiegler, it.faker.quote.unique::singularSiegler)
        }
        register("quote.yoda") {
            selectGenerator(it, it.faker.quote::yoda, it.faker.quote.unique::yoda)
        }
        register("quote.fortune_cookie") {
            selectGenerator(it, it.faker.quote::fortuneCookie, it.faker.quote.unique::fortuneCookie)
        }

        // Verbs
        register("verbs.base") {
            selectGenerator(it, it.faker.verbs::base, it.faker.verbs.unique::base)
        }
        register("verbs.past") {
            selectGenerator(it, it.faker.verbs::past, it.faker.verbs.unique::past)
        }
        register("verbs.past_participle") {
            selectGenerator(it, it.faker.verbs::pastParticiple, it.faker.verbs.unique::pastParticiple)
        }
        register("verbs.ing_form") {
            selectGenerator(it, it.faker.verbs::ingForm, it.faker.verbs.unique::ingForm)
        }
        register("verbs.simple_present") {
            selectGenerator(it, it.faker.verbs::simplePresent, it.faker.verbs.unique::simplePresent)
        }
    }
}
