package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.EntityGenerator
import net.postchain.rell.model.R_App
import java.io.File
import java.util.*

class KotlinEntityGenerator(val packageName: String): EntityGenerator {

    val factory = KotlinDocumentFactory()

    override fun generate(app: R_App, targetFolder: File): List<File> {
        if (targetFolder.exists() && !targetFolder.isDirectory) throw IllegalArgumentException("Target folder invalid")
        targetFolder.mkdirs()
        val createdFiles = mutableListOf<File>()
        app.modules.forEach { module ->
            val moduleFile = File(targetFolder, module.name.str())
            moduleFile.mkdir()

            module.entities.forEach { (name, kdef) ->
                val entityFile = File(moduleFile, "$name.kt").also { createdFiles.add(it) }
                entityFile.createNewFile()
                val document = factory.createDocument("package $packageName")
                val entity = factory.createEntity(kdef)
                document.addEntity(entity)
                entityFile.writeText(document.format())
            }
        }
        return createdFiles
    }
}
