package net.postchain.rell.codegen.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import net.postchain.rell.codegen.kotlin.KotlinEntityGenerator
import net.postchain.rell.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.compiler.base.core.C_CompilerOptions
import net.postchain.rell.compiler.base.utils.C_SourceDir
import net.postchain.rell.model.R_ModuleName
import net.postchain.rell.utils.RellCliUtils

class KotlinCommand : CliktCommand("Generates kotlin files") {

    private val source by argument("source", "Source folder").file(true)

    private val moduleName by argument("module", "Module name")

    private val target by argument("target", "Target folder").file(canBeFile = false, canBeDir = true)

    private val packageName by option("--package", help = "Name of kotlin package").required()

    override fun run() {


        val a = RellCliUtils.compileApp(
            C_SourceDir.diskDir(source),
            C_CompilerModuleSelection(
                listOf(R_ModuleName.of(moduleName))
            ),
            true,
            C_CompilerOptions.DEFAULT
        )
        KotlinEntityGenerator(packageName).generate(a, target)

    }
}