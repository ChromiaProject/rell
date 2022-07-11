package net.postchain.rell.codegen

import net.postchain.rell.codegen.deps.CamelCaseClassName
import net.postchain.rell.codegen.deps.ImportResolver
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.document.DocumentFile
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.*
import net.postchain.rell.utils.RellCliUtils
import java.io.File

class CodeGenerator(val factory: DocumentFactory) {

    private val importResolver = ImportResolver()

    fun createSections(source: File, baseModule: String): List<DocumentSection> {
        val app = compile(source, baseModule)

        val rellEnums = app.modules.flatMap { module -> module.enums.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellEntities = app.modules.flatMap { module -> module.entities.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellStructures = app.modules.flatMap { module -> module.structs.values.map { CamelCaseClassName.fromRellDefinition(it) to it } }.toMap()
        val rellClasses = rellEnums.keys + rellEntities.keys + rellStructures
        val rellQueries = app.modules.flatMap { it.queries.values }.associateBy { it.appLevelName }
        val rellOperations = app.modules.flatMap { it.operations.values }.associateBy { it.appLevelName }

        val neededObjects = rellQueries.values.flatMap { importResolver.resolveQueryDependencies(it) } +
                rellOperations.values.flatMap { importResolver.resolveOperationDependencies(it) }

        val queries = rellQueries.values.map { factory.createQuery(it) }
        val operations = rellOperations.values.map { factory.createOperation(it) }

        val enums = rellEnums.filterKeys { it.rellName in neededObjects }
            .map { factory.createEnum(it.key, it.value) }

        val entities = rellEntities.filterKeys { it.rellName in neededObjects }
            .map { factory.createEntity(it.key, it.value) }

        val structures = rellStructures.filterKeys { it.rellName in neededObjects }
            .map { factory.createStruct(it.key, it.value) }

        return enums + entities + structures + queries + operations
    }

    fun constructDocuments(sections: List<DocumentSection>, singleFile: Boolean = true): List<DocumentFile> {
        if (singleFile) {
            return sections
                .groupBy { it.moduleName }
                .map { (module, sections) ->
                val document = factory.createDocument(module)
                sections.forEach { document.addSection(it) }
                DocumentFile("$module/$module.${factory.fileExtension}", document)
            }
        }
        return listOf()
    }
}

fun compile(source: File, moduleName: String) = RellCliUtils.compileApp(
    C_SourceDir.diskDir(source),
    C_CompilerModuleSelection(
        listOf(R_ModuleName.of(moduleName))
    ),
    true,
    C_CompilerOptions.DEFAULT
)
