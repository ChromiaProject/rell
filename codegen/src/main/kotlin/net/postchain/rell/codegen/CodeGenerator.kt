package net.postchain.rell.codegen

import net.postchain.rell.codegen.deps.ImportResolver
import net.postchain.rell.codegen.document.DocumentFactory
import net.postchain.rell.codegen.document.DocumentFile
import net.postchain.rell.codegen.section.DocumentSection
import net.postchain.rell.codegen.section.Enumeration
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.*
import net.postchain.rell.utils.RellCliUtils
import java.io.File

data class EnumFiles(val rellModule: String, val v: Enumeration)
data class EntityFile(val rellModule: String, val v: R_EntityDefinition)
data class StructFile(val rellModule: String, val v: R_StructDefinition)
data class QueryFile(val rellModule: String, val v: R_QueryDefinition)

class CodeGenerator(val factory: DocumentFactory, val singleFile: Boolean = false) {

    val importResolver = ImportResolver()

    fun createSections(source: File, baseModule: String): List<DocumentSection> {
        val app = compile(source, baseModule)

        val rellEnums = app.modules.flatMap { it.enums.values }.associateBy { it.appLevelName }
        val rellEntities = app.modules.flatMap { it.entities.values }.associateBy { it.appLevelName }
        val rellStructures = app.modules.flatMap { it.structs.values }.associateBy { it.appLevelName }
        val rellQueries = app.modules.flatMap { it.queries.values }.associateBy { it.appLevelName }

        val neededObjects = rellQueries.values.flatMap { importResolver.resolveQueryImports(it) }.toSet()

        val enums = rellEnums.values.map { factory.createEnum(it) }
        val queries = rellQueries.values.map { factory.createQuery(it) }

        val entities = rellEntities.filterKeys { it in neededObjects }
            .map { factory.createEntity(it.value) }

        val structures = rellStructures.filterKeys { it in neededObjects }
            .map { factory.createStruct(it.value) }

        return enums + entities + structures + queries
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

    fun generateFiles(source: File, targetFolder: File, moduleName: String, packageName: String): Set<File> {
        val app = compile(source, moduleName)
        val createdFiles = mutableSetOf<File>()

        factory.createBuiltins().forEach { entity ->
            val f = File(targetFolder, "${entity.name}.${factory.fileExtension}").also { createdFiles.add(it) }
            f.parentFile.mkdirs()
            f.createNewFile()
            val doc = factory.createDocument("")
            doc.addSection(entity)
            f.writeText(doc.format())
        }

        app.modules.forEach { module ->
            val moduleFileName = module.name.str().let { if (singleFile) "$it.${factory.fileExtension}" else it }
            val moduleFile = File(targetFolder, moduleFileName)
            moduleFile.mkdirs()

            module.entities.forEach { (name, eDef) ->
                saveDocument(moduleFile, name, createdFiles, packageName) { factory.createEntity(eDef) }
            }

            module.structs.forEach { (name, sDef) ->
                saveDocument(moduleFile, name, createdFiles, packageName) { factory.createStruct(sDef) }
            }

            module.enums.forEach { (name, eDef) ->
                saveDocument(moduleFile, name, createdFiles, packageName) { factory.createEnum(eDef) }
            }

            module.queries.forEach { (name,qDef) ->
                saveDocument(moduleFile, name, createdFiles, packageName) { factory.createQuery(qDef) }
            }
        }
        return createdFiles
    }

    private fun saveDocument(
        moduleFile: File,
        name: String,
        createdFiles: MutableSet<File>,
        packageName: String,
        sectionCreator: () -> DocumentSection
    ) {
        val file = File(moduleFile, "$name.${factory.fileExtension}").also { createdFiles.add(it) }
        val document = factory.createDocument("")
        document.addSection(sectionCreator())
        file.createNewFile()
        file.writeText(document.format())
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
