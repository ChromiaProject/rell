package net.postchain.rell.codegen

import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.DocumentSection
import java.io.File

class CodeGenerator(private val factory: DocumentFactory, private  val rellCliEnv: RellCliEnv = RellCliEnv.DEFAULT) {

    @Deprecated(message = "Compile app explicitly or replace with function that has null-support", replaceWith = ReplaceWith("createSections(source, baseModule.asList(), generateQueries, generateOperations)"))
    fun createSections(source: File, vararg baseModule: String, generateQueries: Boolean = true, generateOperations: Boolean = true): List<DocumentSection> {
        return createSections(source, baseModule.asList(), generateQueries, generateOperations)
    }

    fun createSections(source: File, modules: List<String>? = null, generateQueries: Boolean = true, generateOperations: Boolean = true): List<DocumentSection> {
        val conf = RellApiCompile.Config.Builder()
                .moduleArgsMissingError(false)
                .mountConflictError(false)
                .build()
        val app = RellApiCompile.compileApp(conf, source, modules)
        return createSections(app, generateQueries, generateOperations)
    }

    fun createSections(app: R_App, generateQueries: Boolean = true, generateOperations: Boolean = true): List<DocumentSection> {

        val rellEnums = app.modules.flatMap { module -> module.enums.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellEntities = app.modules.flatMap { module -> module.entities.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellStructures = app.modules.flatMap { module -> module.structs.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellQueries = app.modules.flatMap { it.queries.values }.associateBy { it.appLevelName }
        val rellOperations = app.modules.flatMap { it.operations.values }.associateBy { it.appLevelName }.filter { !it.value.moduleLevelName.startsWith("__") }

        val queries = if (generateQueries)
            rellQueries.values
                .filter { hasSupportedReturnType(it) }
                .map { factory.createQuery(it) }
        else
            listOf()
        val operations = if (generateOperations) rellOperations.values.map { factory.createOperation(it) } else listOf()

        val neededObjects = (operations + queries).flatMap { it.deps }.distinctBy { it.module + it.className }
        val enums = rellEnums
                .filterKeys { it in neededObjects }
                .map { factory.createEnum(it.key, it.value) }

        val entities = rellEntities
                .filterKeys { it in neededObjects }
                .map { factory.createEntity(it.key, it.value) }

        val structures = rellStructures
                .filterKeys { it in neededObjects }
                .map { factory.createStruct(it.key, it.value) }

        val builtins = factory.getBuiltins(neededObjects)

        return enums + entities + builtins + structures + queries + operations
    }

    fun constructDocuments(sections: List<DocumentSection>, singleFile: Boolean = true): Map<String, Document> {
        if (singleFile) {
            return sections
                    .groupBy { it.moduleName }
                    .map { (module, sections) ->
                        val document = factory.createDocument(module)
                        sections.forEach { document.addSection(it) }
                        val directoryName = module.replace(".", "/")
                        val fileName = if (module.isBlank()) "root" else module.replace(".", "_")
                        "$directoryName/$fileName.${factory.fileExtension}" to document
                    }.toMap()
        }
        return mapOf()
    }

    private fun hasSupportedReturnType(query: R_QueryDefinition): Boolean {
        val returnType = query.type()
        return if (returnType is R_TupleType && isMixedTuple(returnType)) {
            rellCliEnv.error("Skipping [${query.appLevelName}] Query has unsupported mixed tuple return type: $returnType")
            false
        } else {
            true
        }
    }

    private fun isMixedTuple(type: R_TupleType): Boolean {
        return type.fields.any { it.name == null } && type.fields.any { it.name != null }
    }
}
