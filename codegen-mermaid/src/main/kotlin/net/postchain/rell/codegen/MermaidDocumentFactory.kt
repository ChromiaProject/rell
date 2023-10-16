package net.postchain.rell.codegen

import net.postchain.rell.base.model.R_EntityDefinition
import net.postchain.rell.base.model.R_EnumDefinition
import net.postchain.rell.base.model.R_OperationDefinition
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_StructDefinition
import net.postchain.rell.codegen.deps.ClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.Builtin
import net.postchain.rell.codegen.section.NullSection
import net.postchain.rell.codegen.util.BuiltinType

class MermaidDocumentFactory(private val config: MermaidCodeGeneratorConfig): DocumentFactory {
    override val fileExtension = if (config.mdx()) "md" else "mmd"

    override fun createDocument(moduleName: String) = MermaidDocument(config.mdx())

    override fun createEntity(className: ClassName, rellEntity: R_EntityDefinition) = MermaidEntity(rellEntity)

    override fun createBuiltins(type: BuiltinType) = NullSection

    override fun createStruct(className: ClassName, rellStruct: R_StructDefinition) = NullSection

    override fun createEnum(className: ClassName, rellEnum: R_EnumDefinition) = NullSection

    override fun createQuery(rellQuery: R_QueryDefinition) = NullSection

    override fun createOperation(rellOperation: R_OperationDefinition) = NullSection

    override fun getBuiltins(neededObjects: List<ClassName>) = listOf<Builtin>()
}
