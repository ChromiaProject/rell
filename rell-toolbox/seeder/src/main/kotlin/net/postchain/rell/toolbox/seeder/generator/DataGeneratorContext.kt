package net.postchain.rell.toolbox.seeder.generator

import io.github.serpro69.kfaker.Faker
import net.postchain.rell.toolbox.seeder.Attribute
import net.postchain.rell.toolbox.seeder.Entity
import net.postchain.rell.toolbox.seeder.config.AttributeConfig

data class DataGeneratorContext(
    val attribute: Attribute,
    val entity: Entity,
    val attributeConfig: AttributeConfig?,
    val faker: Faker,
    val uniqueProducer: UniqueProducer<Any>
) {
    fun isAttributeUnique(): Boolean {
        return entity.keys.any { it.strAttribs.contains(attribute.name) }
    }
}
