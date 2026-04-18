/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.kotlin

import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Struct


class KotlinStruct(className: ClassName, struct: R_StructDefinition) : DataClassSection(
    className,
    struct.struct.attributes.values.associateBy( { it.name }, { it.type }),
    struct.docSymbol,
), Struct {
    override fun format(): String {
        return """
            |/**
            |* Struct ${className.rellName}
            |${KotlinDocGenerator.formatDoc(docSymbol)}
            |*/
            |${super.format()}
        """.trimMargin()
    }
}
