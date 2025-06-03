/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import net.postchain.gtv.Gtv
import net.postchain.rell.api.base.RellCliBasicException
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.api.base.RellConfigGen
import net.postchain.rell.base.utils.PostchainGtvUtils
import picocli.CommandLine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

fun main(args: Array<String>) {
    RellToolsLogUtils.initLogging()
    RellToolsUtils.runCli(args, RellConfigGenCliArgs(), ::main0)
}

private fun main0(args: RellConfigGenCliArgs) {
    val target = RellToolsUtils.getTarget(args.sourceDir, args.module)

    val template = if (args.configTemplateFile == null) null else {
        readFile(File(args.configTemplateFile))
    }

    val configGen = RellConfigGen.create(RellCliEnv.DEFAULT, target)
    val config = configGen.makeConfig(template)

    if (args.outputFile != null) {
        val outputFile = File(args.outputFile)
        verifyCfg(outputFile.absoluteFile.parentFile.isDirectory, "Path not found: $outputFile")
        FileOutputStream(outputFile).use {
            writeResult(args, it, config)
        }
    } else {
        writeResult(args, System.out, config)
    }
}

private fun writeResult(args: RellConfigGenCliArgs, os: OutputStream, config: Gtv) {
    val bytes = if (args.binaryOutput) {
        PostchainGtvUtils.gtvToBytes(config)
    } else {
        val text = RellConfigGen.configToText(config)
        text.toByteArray()
    }
    os.write(bytes)
}

private fun readFile(file: File): String {
    verifyCfg(file.isFile, "File not found: ${file.path}")
    return file.readText()
}

private fun verifyCfg(b: Boolean, msg: String) {
    if (!b) {
        throw RellCliBasicException(msg)
    }
}

@CommandLine.Command(name = "RellConfigGen", description = ["Generates Rell Postchain configuration"])
class RellConfigGenCliArgs: RellBaseCliArgs() {
    @CommandLine.Parameters(index = "0", paramLabel = "MODULE", description = ["Module name"])
    var module: String = ""

    @CommandLine.Parameters(index = "1", arity = "0..1", paramLabel = "OUTPUT_FILE", description = ["Output configuration file"])
    var outputFile: String? = null

    @CommandLine.Option(names = ["--template"], paramLabel = "TEMPLATE_FILE", description = ["Configuration template file"])
    var configTemplateFile: String? = null

    @CommandLine.Option(names = ["--binary-output"], paramLabel = "BINARY_OUTPUT", description = ["Write output as binary"])
    var binaryOutput: Boolean = false
}
