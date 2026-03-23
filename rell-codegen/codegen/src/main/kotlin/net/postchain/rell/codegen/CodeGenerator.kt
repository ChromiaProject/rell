/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.codegen

import net.postchain.rell.api.base.RellApiCompile
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.base.lib.type.R_CollectionType
import net.postchain.rell.base.lib.type.R_MapType
import net.postchain.rell.base.model.R_App
import net.postchain.rell.base.model.R_NullableType
import net.postchain.rell.base.model.R_QueryDefinition
import net.postchain.rell.base.model.R_TupleType
import net.postchain.rell.base.model.R_Type
import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.DocumentSection
import java.io.File

class CodeGenerator(private val factory: DocumentFactory, private val config: CodeGeneratorConfig, private val rellCliEnv: RellCliEnv = RellCliEnv.DEFAULT) {

    @Deprecated(message = "Compile app explicitly or replace with function that has null-support", replaceWith = ReplaceWith("createSections(source, baseModule.asList(), generateQueries, generateOperations)"))
    fun createSections(source: File, vararg baseModule: String): List<DocumentSection> {
        return createSections(source, baseModule.asList())
    }

    fun createSections(source: File, modules: List<String>? = null): List<DocumentSection> {
        val conf = RellApiCompile.Config.Builder()
                .moduleArgsMissingError(false)
                .mountConflictError(false)
                .docSymbolsEnabled(true)
                .cliEnv(rellCliEnv)
                .build()
        val app = RellApiCompile.compileApp(conf, source, modules)
        return createSections(app)
    }

    fun createSections(app: R_App): List<DocumentSection> {
        val rellEnums = app.modules.flatMap { module -> module.enums.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellEntities = app.modules.flatMap { module -> module.entities.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellStructures = app.modules.flatMap { module -> module.structs.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellQueries = app.modules.flatMap { it.queries.values }.associateBy { it.appLevelName }
        val rellOperations = app.modules.flatMap { it.operations.values }.associateBy { it.appLevelName }.filter { !it.value.moduleLevelName.startsWith("__") }

        val queries = if (config.includeQueries()) {
            rellQueries.values
                    .filter { hasSupportedReturnType(it, it.type()) }
                    .map { factory.createQuery(it) }
        } else {
            listOf()
        }

        val operations = if (config.includeOperations()) {
            rellOperations.values.map { factory.createOperation(it) }
        } else {
            listOf()
        }

        val neededObjects = (operations + queries).flatMap { it.deps }.distinctBy { it.module + it.className }
        val enums = rellEnums
                .filterKeys { it in neededObjects }
                .map { factory.createEnum(it.key, it.value) }

        val entities = rellEntities
                .filterKeys { config.allEntities() || it in neededObjects }
                .map { factory.createEntity(it.key, it.value) }

        val structures = rellStructures
                .filterKeys { it in neededObjects }
                .map { factory.createStruct(it.key, it.value) }

        val builtins = factory.getBuiltins(neededObjects)

        return enums + entities + builtins + structures + queries + operations
    }

    fun constructDocuments(sections: List<DocumentSection>): Map<String, StringSerializable> {
        return when (config.fileSaveMode()) {
            FileSaveMode.Module -> sections
                    .groupBy { it.moduleName }
                    .flatMap { (module, sections) ->
                        val document = factory.createDocument(module)
                        sections.forEach { document.addSection(it) }
                        val directoryName = module.replace(".", "/")
                        val fileName = if (module.isBlank()) "root" else module.replace(".", "_")
                        listOf("$directoryName/$fileName.${factory.fileExtension}" to document) +
                                sections.flatMap {
                                    it.extraFiles(module).map { (filename, content) ->
                                        "$directoryName/$filename.${factory.fileExtension}" to content
                                    }
                                }
                    }.toMap()

            FileSaveMode.Dapp -> {
                val document = factory.createDocument("")
                sections.forEach { document.addSection(it) }
                mapOf("rell.${factory.fileExtension}" to document)
            }

            FileSaveMode.Separate -> mapOf()
        } + factory.extraFiles().map { (filename, content) -> "$filename.${factory.fileExtension}" to content }
    }

    private fun hasSupportedReturnType(query: R_QueryDefinition, returnType: R_Type): Boolean {
        if (returnType is R_NullableType) return hasSupportedReturnType(query, returnType.valueType)

        if (returnType is R_CollectionType) return hasSupportedReturnType(query, returnType.elementType)

        if (returnType is R_MapType) return hasSupportedReturnType(query, returnType.valueType)

        return if (returnType is R_TupleType && isMixedTuple(returnType)) {
            rellCliEnv.error("Skipping [${query.appLevelName}] Query return type contains unsupported mixed tuple type: ${returnType.str()}")
            false
        } else {
            true
        }
    }

    private fun isMixedTuple(type: R_TupleType): Boolean {
        return type.fields.any { it.name == null } && type.fields.any { it.name != null }
    }

}
