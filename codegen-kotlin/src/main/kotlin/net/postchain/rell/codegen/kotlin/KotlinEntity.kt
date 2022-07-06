package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*

class KotlinEntity(entity: R_EntityDefinition) : GtvConvertibleSection(
    entity.appLevelName,
    entity.simpleName.snakeToUpperCamelCase(),
    entity.defId.module.substringBefore("["),
    entity.attributes.values
), Entity {
    override fun format(): String {
        return """
            |/*
            |* Entity $name 
            |*
            |* Rell entity is typically encoded as a GtvInteger. If used as struct<$name>, then toGtv() is used for encoding.
            |*/
            |${super.format()}
        """.trimMargin()
    }
}