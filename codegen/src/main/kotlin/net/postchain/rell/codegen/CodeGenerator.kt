package net.postchain.rell.codegen

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.document.Document
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliEnv
import net.postchain.rell.utils.RellCliUtils
import java.io.File

class CodeGenerator(val factory: DocumentFactory) {

    fun createSections(source: File, vararg baseModule: String, generateQueries: Boolean = true, generateOperations: Boolean = true): List<DocumentSection> {
        val app = compile(source, *baseModule)

        val rellEnums = app.modules.flatMap { module -> module.enums.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellEntities = app.modules.flatMap { module -> module.entities.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellStructures = app.modules.flatMap { module -> module.structs.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellQueries = app.modules.flatMap { it.queries.values }.associateBy { it.appLevelName }
        val rellOperations = app.modules.flatMap { it.operations.values }.associateBy { it.appLevelName }.filter { !it.value.moduleLevelName.startsWith("__") }

        val queries = if (generateQueries) rellQueries.values.map { factory.createQuery(it) } else listOf()
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
}

fun compile(source: File, vararg moduleName: String) = RellCliUtils.compile(
        object : RellCliEnv() {
            override fun print(msg: String, err: Boolean) {
                println(msg)
            }

            override fun exit(status: Int): Nothing {
                throw RuntimeException("Rell compilation failed")
            }
        },
        C_SourceDir.diskDir(source),
        C_CompilerModuleSelection(
                listOf(*moduleName).map { R_ModuleName.of(it) }
        ),
        true,
        C_CompilerOptions.DEFAULT
).app ?: throw RuntimeException("Rell compilation failed")
