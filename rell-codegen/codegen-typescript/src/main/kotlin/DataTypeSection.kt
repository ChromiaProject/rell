/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_Type
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.util.rTypeToJsTypeString

open class DataTypeSection(private val className: ClassName,
                           attributes: Map<String, R_Type>,
                           override val docSymbol: DocSymbol) : DocumentSection {
    override val moduleName get() = className.module

    override val imports: List<String> = listOf("")

    private val typeFields = attributes.map { formatAttribute(it.key, it.value) }

    private fun formatAttribute(name: String, type: R_Type) = "$name: ${rTypeToJsTypeString(type)};"

    override val deps = DependencyFinder.findDependencies(attributes.values)

    override fun format() = """
        |${TypescriptDocGenerator.formatDoc(docSymbol, wrapInDocComments = true)}
        |export type ${className.className} = {
        |${"\t"}${typeFields.joinToString("\n\t")}
        |};
    """.trimMargin()
}
