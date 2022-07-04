package net.postchain.rell.codegen.kotlin

import net.postchain.rell.model.R_App
import java.io.File
import java.util.*

class EntityGenerator {

    fun generate(app: R_App, targetFolder: File, packageName: String) {
        if (targetFolder.exists() && !targetFolder.isDirectory) throw IllegalArgumentException("Target folder invalid")
        targetFolder.mkdirs()
        app.modules.forEach { module ->

            val target = File(targetFolder, "${module.name.str()}.kt")
            target.createNewFile()
            module.entities.forEach { (name, kdef) ->
                val packageStr = "package $packageName"
                val imports = listOf("net.postchain.gtv.mapper.Name")
                val c =
                    """
                        |$packageStr
                        |
                        |${imports.joinToString("\n") { "import $it" }}
                    
                        |class ${name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}(
                        |    ${ kdef.attributes.map { "@Name(\"${it.key}\") val ${it.key}: ${mapType(it.value.type.name)}," } .joinToString("\n\t") }
                        |)
                """.trimMargin()
                target.writeText(c)
            }
        }
    }
}

fun mapType(t: String): String {
    return when (t) {
        "text" -> "String"
        "integer" -> "Integer"
        else -> throw IllegalArgumentException("")
    }
}