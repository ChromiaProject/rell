package net.postchain.rell.codegen.kotlin

import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Struct


class KotlinStruct(className: ClassName, struct: R_StructDefinition) : DataClassSection(
    className,
    struct.struct.attributes.values.associateBy( { it.name }, { it.type })
), Struct {
    override fun format(): String {
        return """
            |/*
            |* Struct ${className.rellName} 
            |*/
            |${super.format()}
        """.trimMargin()
    }
}
