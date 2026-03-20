package net.postchain.rell.codegen.kotlin

import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Entity

class KotlinEntity(className: ClassName, entity: R_EntityDefinition) : DataClassSection(
    className,
    entity.attributes.values.associateBy( { it.name }, { it.type }),
    entity.docSymbol,
), Entity {
    override fun format(): String {
        return """
            |/**
            |* Entity ${className.rellName} 
            |*
            |* Rell entity is typically encoded as a GtvInteger. If used as struct<${className.rellName}>, then GtvObjectMapper.toGtvArray() is used for encoding.
            |${KotlinDocGenerator.formatDoc(docSymbol)}
            |*/
            |${super.format()}
        """.trimMargin()
    }
}