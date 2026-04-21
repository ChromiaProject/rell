/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.python

import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.section.Enumeration

class PythonEnumeration(
    private val className: ClassName,
    private val enum: R_EnumDefinition
) : DocumentSection, Enumeration {

    override val deps = emptySet<ClassName>()
    override val docSymbol: DocSymbol = enum.docSymbol
    override val moduleName get() = className.module
    override val imports = emptyList<String>()

    override fun format(): String {
        val enumValues = enum.attrs.joinToString("\n") { attr ->
            "\t${attr.name.uppercase()} = \"${attr.name}\""
        }
        return """
            |${PythonDocGenerator.formatDoc(docSymbol, wrapInDocComments = true)}
            |class ${className.className}(Enum):
            |$enumValues
            """.trimMargin()
    }
}