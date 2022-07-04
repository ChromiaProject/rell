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
        app.modules.forEach { module ->
            val moduleFileName = module.name.str().let { if (singleFile) "$it.kt" else it }
            val moduleFile = File(targetFolder, moduleFileName)
            if (singleFile) createdFiles.add(moduleFile)

            module.entities.forEach { (name, kdef) ->
                val entityFile = File(moduleFile, "$name.kt").also { createdFiles.add(it) }
                val entityDocument = factory.createDocument("package $packageName")
                val entity = factory.createEntity(kdef)
                entityDocument.addEntity(entity)
                entityFile.appendText(entityDocument.format())
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
