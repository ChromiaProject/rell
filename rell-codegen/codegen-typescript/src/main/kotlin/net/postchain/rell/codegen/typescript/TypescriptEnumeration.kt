package net.postchain.rell.codegen.typescript

import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.utils.doc.DocSymbol
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.section.Enumeration

class TypescriptEnumeration(private val className: ClassName, enum: R_EnumDefinition) : Enumeration {
    override val moduleName = className.module
    private val enumValues = enum.values()

    override val docSymbol = enum.docSymbol

    override val imports = listOf("")

    override fun format() = """
        |${TypescriptDocGenerator.formatDoc(docSymbol, wrapInDocComments = true)}
        |export enum ${className.className} {
        |${formatEnumValues()}
        |}
    """.trimMargin()

    private fun formatEnumValues() : String {
        return "\t${enumValues.joinToString(",\n\t") { it.asEnum().name }}" }
}
