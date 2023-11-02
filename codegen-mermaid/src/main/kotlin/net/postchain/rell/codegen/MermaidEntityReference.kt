package net.postchain.rell.codegen

import net.postchain.rell.base.model.R_Attribute
import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_Key
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.DependencyFinder
import net.postchain.rell.codegen.section.Entity

class MermaidEntityReference(private val rellEntity: R_EntityDefinition) : Entity {
    override val moduleName: String
        get() = rellEntity.moduleLevelName
    override val imports: List<String>
        get() = listOf()

    private val entityName = nameFromType(rellEntity.type)
    private val attributes = rellEntity.attributes.values.map { AttributeProps.fromAttribue(it) }
    override val deps = DependencyFinder.findDependencies(attributes.map { it.type })

    override fun format(): String {
        return """
            |${"\t"}${attributes.filter { it.isReference }.joinToString("\n\t") { it.formatRelation(entityName, rellEntity.keys) }}
            |${"\t"}${nameFromType(rellEntity.type)} {
            |${"\t\t"}${attributes.joinToString("\n\t\t") { it.format() }}
            |${"\t"}}
        """.trimMargin()
    }

    companion object {
        private fun nameFromType(type: R_Type) = type.defName.qualifiedName.toString().replace(".", "_")
    }

    private class AttributeProps(val name: String, val indexed: Boolean, val mutable: Boolean, val isReference: Boolean, val type: R_Type) {

        fun formatRelation(fromEntity: String, keys: List<R_Key>): String {
            val relation = findRelation(keys)
           return  "$fromEntity ${relation.from}| -- |${relation.to} ${nameFromType(type)} : \"\""
        }
        fun findRelation(keys: List<R_Key>): RelationType {
            val isOneToOne = keys.any { key -> key.attribs.size == 1 && key.attribs.map { it.str }.contains(name) }
            if (isOneToOne) return RelationType.ONE_TO_ONE
            val isManyToMany = keys.any { key -> key.attribs.map { it.str }.contains(type.name) }
            if (isManyToMany) return RelationType.MANY_TO_MANY
            return RelationType.ONE_TO_MANY
        }

        fun format() = "${formatReferenced()}${nameFromType(type)}${formatIndexed()}${formatMutable()} $name"

        fun formatReferenced() = if (isReference) "*" else ""
        fun formatMutable() = if (mutable) "(m)" else ""
        fun formatIndexed() = if (indexed) "[i]" else ""

        companion object {
            fun fromAttribue(attribute: R_Attribute): AttributeProps {
                return AttributeProps(
                        name = attribute.name,
                        mutable = attribute.mutable,
                        isReference = !attribute.type.completeFlags().pure,
                        indexed = attribute.keyIndexKind != null,
                        type = attribute.type
                )
            }

            enum class RelationType(val from: Char, val to: Char) {
                ONE_TO_MANY('|', '{'),
                MANY_TO_MANY('|', '{'), // Will have two legs
                ONE_TO_ONE('|', '|'),

            }
        }
    }
}
