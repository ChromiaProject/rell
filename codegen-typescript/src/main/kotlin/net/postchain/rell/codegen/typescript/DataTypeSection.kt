package net.postchain.rell.codegen.typescript

import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.typescript.util.rTypeToString
import net.postchain.rell.codegen.util.snakeToLowerCamelCase
import net.postchain.rell.model.R_Type

open class DataTypeSection(private val className: ClassName, attributes: Map<String, R_Type>) : DocumentSection {
    override val moduleName get() = className.module

    override val imports: List<String> = listOf("")

    private val typeFields = attributes.map { formatAttribute(it.key, it.value) }

    private fun formatAttribute(name: String, type: R_Type) = "${name.snakeToLowerCamelCase()}: ${rTypeToString(type)};"

    override val deps = DependencyFinder.findDependencies(attributes.values)

    override fun format() = """
        |export type ${className.className} = {
        |${"\t"}${typeFields.joinToString("\n\t")}
        |};
    """.trimMargin()
}
