/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools.runcfg

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.rell.base.utils.DirFile
import net.postchain.rell.tools.RellBaseCommand
import net.postchain.rell.tools.RellToolsLogUtils
import net.postchain.rell.tools.RellToolsUtils
import java.nio.file.Path
import kotlin.io.path.*

fun main(args: Array<String>) {
    RellToolsLogUtils.initLogging()
    RellToolsUtils.runCli(args, RellRunConfigGenCommand())
}

abstract class RellRunConfigCommand(name: String) : RellBaseCommand(name) {
    val sourceVersion by option("-s", "--source-version", metavar = "VERSION",
        help = "Version of Rell the source code is compatible with, X.Y.Z")
    val runConfigFile by argument("RUN_CONFIG", help = "Run config file")
}

private class RellRunConfigGenCommand : RellRunConfigCommand("RellRunConfigGen") {
    val outputDir by option("-o", "--output-dir", metavar = "OUTPUT_DIR", help = "Output directory")
    val dryRun by option("--dry-run", help = "Do not create files").flag()

    override fun run() {
        val runConfigFile = RellToolsUtils.checkFile(runConfigFile)
        val sourceDir = RellToolsUtils.checkDir(this.sourceDir ?: ".")
        val sourceVersion = RellToolsUtils.checkVersion(sourceVersion)

        val theOutputDir = this.outputDir

        val outputDir = if (theOutputDir == null) {
            Path(".")
        } else {
            val f = Path(theOutputDir)
            RellToolsUtils.check(f.isDirectory() || f.absolute().parent.isDirectory()) { "Bad output directory: $f" }
            f
        }

        val appConfig = RellRunConfigGenerator.generateCli(sourceDir, runConfigFile, sourceVersion, unitTest = false)
        val files = RellRunConfigGenerator.buildFiles(appConfig.config)

        if (dryRun) {
            printFiles(files)
        } else {
            createFiles(outputDir, files)
        }
    }

    private fun createFiles(outputDir: Path, files: Map<String, DirFile>) {
        outputDir.createDirectories()
        for ((path, file) in files) {
            val javaFile = outputDir / path
            val dir = javaFile.parent
            dir.createDirectories()
            file.write(javaFile.toFile())
        }
    }

    private fun printFiles(files: Map<String, DirFile>) {
        for ((path, file) in files) {
            val text = file.previewText()
            println(path)
            println(text)
            println()
        }
    }
}
