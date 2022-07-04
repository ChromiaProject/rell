package net.postchain.rell.codegen.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.rell.codegen.app.util.compile
import net.postchain.rell.codegen.kotlin.KotlinEntityGenerator

class KotlinCommand : CliktCommand("Generates kotlin files") {

    private val source by argument("source", "Source folder").file(true)
    private val target by argument("target", "Target folder").file(canBeFile = false, canBeDir = true)

    private val moduleName by option("--module", help =  "Module name").required()
    private val packageName by option("--package", help = "Name of kotlin package").required()

    override fun run() {
        val app = compile(source, moduleName)
        val out = KotlinEntityGenerator(packageName).generate(app, target)
        println("Created files: ${out.map { it.name }}")
    }
}