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

            module.entities.forEach { (name, kDef) ->
                val entity = factory.createEntity(kDef)
                val entityFile = File(moduleFile, "${entity.name}.kt").also { createdFiles.add(it) }
                val entityDocument = factory.createDocument("package $packageName")
                entityDocument.addSection(entity)
                entityFile.createNewFile()
                entityFile.writeText(entityDocument.format())
            }

            module.structs.forEach { (name, sDef) ->
                val structFile = File(moduleFile, "$name.kt").also { createdFiles.add(it) }
                val structDocument = factory.createDocument("package $packageName")
                val struct = factory.createStruct(sDef)
                structDocument.addSection(struct)
                structFile.createNewFile()
                structFile.writeText(structDocument.format())
            }

            module.enums.forEach { (name, eDef) ->
                val enumFile = File(moduleFile, "$name.kt").also { createdFiles.add(it) }
                val enumDocument = factory.createDocument("package $packageName")
                val enum = factory.createEnum(eDef)
                enumDocument.addSection(enum)
                enumFile.createNewFile()
                enumFile.writeText(enumDocument.format())
            }
        }
        return createdFiles
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
