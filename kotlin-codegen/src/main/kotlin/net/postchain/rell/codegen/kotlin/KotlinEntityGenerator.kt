package net.postchain.rell.codegen.kotlin

import net.postchain.rell.codegen.EntityGenerator
import net.postchain.rell.model.R_App
import java.io.File
import java.util.*

class KotlinEntityGenerator(val packageName: String): EntityGenerator {

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
                val packageStr = "package $packageName"
                val imports = listOf("net.postchain.gtv.mapper.Name")
                val c =
                    """
                        |$packageStr
                        |
                        |${imports.joinToString("\n") { "import $it" }}
                    
                        |class ${capitalize(name)}(
                        |    ${ kdef.attributes.map { "@Name(\"${it.key}\") val ${it.key}: ${mapType(it.value.type.name)}," } .joinToString("\n\t") }
                        |)
                """.trimMargin()
                entityFile.writeText(c)
            }
        }
        return createdFiles
    }

}

private fun capitalize(name: String) =
    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
fun mapType(t: String): String {
    return when (t) {
        "text" -> "String"
        "integer" -> "Integer"
        "boolean" -> "Boolean"
        "byte_array" -> "ByteArray"
        else -> capitalize(t)
    }
}