package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.codegen.util.snakeToUpperCamelCase
import net.postchain.rell.model.R_StructDefinition


class KotlinStruct(struct: R_StructDefinition) : GtvConvertibleSection(
    struct.appLevelName,
    struct.simpleName.snakeToUpperCamelCase(),
    struct.defId.module.substringBefore("["),
    struct.struct.attributes.values.associateBy( { it.name }, { it.type })
), Struct {
    override fun format(): String {
        return """
            |/*
            |* Struct $name 
            |*/
            |${super.format()}
        """.trimMargin()
    }
}
