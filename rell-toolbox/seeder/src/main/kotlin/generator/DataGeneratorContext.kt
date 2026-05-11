/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import io.github.serpro69.kfaker.Faker
import net.postchain.rell.toolbox.seeder.config.AttributeConfig
import net.postchain.rell.toolbox.seeder.schema.Attribute
import net.postchain.rell.toolbox.seeder.schema.Entity

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
