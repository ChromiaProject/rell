package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.model.R_StructDefinition

class TypescriptStruct(className: ClassName, struct: R_StructDefinition) :
    DataTypeSection(className, struct.struct.attributes.values.associateBy({ it.name }, { it.type })), Struct
