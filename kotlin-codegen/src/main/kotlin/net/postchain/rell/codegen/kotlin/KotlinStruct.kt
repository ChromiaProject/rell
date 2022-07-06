package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.section.Struct
import net.postchain.rell.model.R_StructDefinition


class KotlinStruct(struct: R_StructDefinition) : GtvContertible(
    struct.simpleName,
    struct.defId.module.substringBefore("["),
    struct.struct.attributes.values
), Struct {
    override fun format(): String {
        return """
            |/*
            |* $name struct
            |*/
            |${super.format()}
        """.trimMargin()
    }
}
