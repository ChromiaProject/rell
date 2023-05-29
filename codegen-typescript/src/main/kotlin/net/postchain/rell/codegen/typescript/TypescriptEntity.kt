package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Entity

class TypescriptEntity(className: ClassName, entity: R_EntityDefinition) :
        DataTypeSection(className, entity.attributes.values.associateBy({ it.name }, { it.type })), Entity