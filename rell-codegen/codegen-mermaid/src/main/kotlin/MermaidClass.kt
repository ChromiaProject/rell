/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EntityType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.Entity

class MermaidClass(private val rellEntity: R_EntityDefinition) : Entity {
    override val moduleName: String
        get() = rellEntity.moduleLevelName
    override val imports: List<String>
        get() = listOf()
    override val docSymbol = rellEntity.docSymbol

    private val attributes = rellEntity.attributes.values.associateBy( { it.name }, { it.type })
    override val deps = DependencyFinder.findDependencies(attributes.values)

    override fun format(): String {
        return """
            |${"\t"}${attributes.filter { it.value is R_EntityType }.map { "${formatAttributeName(it.value)} <|-- ${formatAttributeName(rellEntity.type)}"}.joinToString("\n\t")}
            |${"\t"}class ${formatAttributeName(rellEntity.type)} {
            |${"\t\t"}${attributes.map { formatAttribute(it.key, it.value) }.joinToString("\n\t\t")}
            |${"\t"}}
        """.trimMargin()
    }

    private fun formatAttributeName(type: R_Type) = type.defName.qualifiedName.toString().replace(".", "_")

    private fun formatAttribute(name: String, type: R_Type): String {
        val sb = StringBuilder()
        val flags = type.completeFlags()
        if (!flags.pure) sb.append("+")
        if (flags.mutable) sb.append("mutable")
        sb.append("$name: ${formatAttributeName(type)}")
        return sb.toString()
    }
}
