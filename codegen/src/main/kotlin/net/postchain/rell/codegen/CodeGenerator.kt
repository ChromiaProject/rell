package net.postchain.rell.codegen

import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils
import java.io.File

class CodeGenerator(val factory: DocumentFactory, val singleFile: Boolean = false) {

    fun generateFiles(source: File, targetFolder: File, moduleName: String, packageName: String): Set<File> {
        val app = compile(source, moduleName)
        val createdFiles = mutableSetOf<File>()

        factory.createBuiltins().forEach { entity ->
            val f = File(targetFolder, "${entity.name}.kt").also { createdFiles.add(it) }
            f.parentFile.mkdirs()
            f.createNewFile()
            val doc = factory.createDocument("package $packageName")
            doc.addSection(entity)
            f.writeText(doc.format())
        }

        app.modules.forEach { module ->
            val moduleFileName = module.name.str().let { if (singleFile) "$it.kt" else it }
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
        val file = File(moduleFile, "$name.kt").also { createdFiles.add(it) }
        val document = factory.createDocument("package $packageName")
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
