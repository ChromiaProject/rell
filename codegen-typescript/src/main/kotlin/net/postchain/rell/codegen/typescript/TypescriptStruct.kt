package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Struct

class TypescriptStruct(className: ClassName, struct: R_StructDefinition) :
    DataTypeSection(className, struct.struct.attributes.values.associateBy({ it.name }, { it.type })), Struct
