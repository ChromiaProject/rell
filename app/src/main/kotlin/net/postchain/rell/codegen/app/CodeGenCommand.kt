package net.postchain.rell.codegen.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.rell.codegen.CodeGenerator
import net.postchain.rell.codegen.app.util.LanguageSupport
import net.postchain.rell.codegen.kotlin.KotlinDocumentFactory

class CodeGenCommand : CliktCommand("Generates files based on rell sources") {

    private val source by argument("source", "Source folder").file(true)
    private val target by argument("target", "Target folder").file(canBeFile = false, canBeDir = true)

    private val moduleName by option("--module", help = "Module name").required()
    private val packageName by option("--package", help = "Name of package").required()

    private val language by option("--language", "-l", help = "Language to generate for")
        .enum<LanguageSupport>(ignoreCase = true)
        .default(LanguageSupport.Kotlin)

    override fun run() {
        val factory = when (language) {
            LanguageSupport.Kotlin -> KotlinDocumentFactory()
        }
        val generator = CodeGenerator(factory, false)
        val out = generator.generateFiles(source, target, moduleName, packageName)
        println("Created files: ${out.map { it.name }}")
    }
}