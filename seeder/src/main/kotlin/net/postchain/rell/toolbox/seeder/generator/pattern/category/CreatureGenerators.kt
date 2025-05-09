package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class CreatureGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("ancient.god") {
            selectGenerator(it, it.faker.ancient::god, it.faker.ancient.unique::god)
        }
        register("ancient.primordial") {
            selectGenerator(it, it.faker.ancient::primordial, it.faker.ancient.unique::primordial)
        }
        register("ancient.titan") {
            selectGenerator(it, it.faker.ancient::titan, it.faker.ancient.unique::titan)
        }
        register("ancient.hero") {
            selectGenerator(it, it.faker.ancient::hero, it.faker.ancient.unique::hero)
        }
        register("animal.name") {
            selectGenerator(it, it.faker.animal::name, it.faker.animal.unique::name)
        }
        register("bird.geo") {
            selectGenerator(it, it.faker.bird::geo, it.faker.bird.unique::geo)
        }
        register("bird.anatomy") {
            selectGenerator(it, it.faker.bird::anatomy, it.faker.bird.unique::anatomy)
        }
        register("bird.colors") {
            selectGenerator(it, it.faker.bird::colors, it.faker.bird.unique::colors)
        }
        register("bird.plausible_name") {
            selectGenerator(it, it.faker.bird::plausibleCommonNames, it.faker.bird.unique::plausibleCommonNames)
        }
        register("bird.family_name") {
            selectGenerator(it, it.faker.bird::commonFamilyName, it.faker.bird.unique::commonFamilyName)
        }
        register("cat.name") {
            selectGenerator(it, it.faker.cat::name, it.faker.cat.unique::name)
        }
        register("cat.breed") {
            selectGenerator(it, it.faker.cat::breed, it.faker.cat.unique::breed)
        }
        register("cat.registry") {
            selectGenerator(it, it.faker.cat::registry, it.faker.cat.unique::registry)
        }
        register("dog.name") {
            selectGenerator(it, it.faker.dog::name, it.faker.dog.unique::name)
        }
        register("dog.breed") {
            selectGenerator(it, it.faker.dog::breed, it.faker.dog.unique::breed)
        }
        register("dog.sound") {
            selectGenerator(it, it.faker.dog::sound, it.faker.dog.unique::sound)
        }
        register("dog.meme_phrase") {
            selectGenerator(it, it.faker.dog::memePhrase, it.faker.dog.unique::memePhrase)
        }
        register("dog.coat_length") {
            selectGenerator(it, it.faker.dog::coatLength, it.faker.dog.unique::coatLength)
        }
        register("dog.size") {
            selectGenerator(it, it.faker.dog::size, it.faker.dog.unique::size)
        }
        register("dog.age") {
            selectGenerator(it, it.faker.dog::age, it.faker.dog.unique::age)
        }
        register("horse.name") {
            selectGenerator(it, it.faker.horse::name, it.faker.horse.unique::name)
        }
        register("horse.breed") {
            selectGenerator(it, it.faker.horse::breed, it.faker.horse.unique::breed)
        }
    }
}
