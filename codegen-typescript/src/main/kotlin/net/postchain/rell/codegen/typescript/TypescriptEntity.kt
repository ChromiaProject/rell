package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Entity
import net.postchain.rell.model.R_EntityDefinition

class TypescriptEntity(className: ClassName, entity: R_EntityDefinition) :
        DataTypeSection(className, entity.attributes.values.associateBy({ it.name }, { it.type })), Entity