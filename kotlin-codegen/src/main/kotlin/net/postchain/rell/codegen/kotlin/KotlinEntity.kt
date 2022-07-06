package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.*
import java.math.BigDecimal
import kotlin.reflect.KClass

class KotlinEntity(entity: R_EntityDefinition) : GtvContertible(
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