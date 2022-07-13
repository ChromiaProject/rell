package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.model.*

class KotlinEntity(className: ClassName, entity: R_EntityDefinition) : DataClassSection(
    className,
    entity.attributes.values.associateBy( { it.name }, { it.type })
), Entity {
    override fun format(): String {
        return """
            |/*
            |* Entity ${className.rellName} 
            |*
            |* Rell entity is typically encoded as a GtvInteger. If used as struct<${className.rellName}>, then toGtv() is used for encoding.
            |*/
            |${super.format()}
        """.trimMargin()
    }
}