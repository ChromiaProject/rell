/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.tools

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import net.postchain.gtv.Gtv
import net.postchain.rell.api.base.RellCliBasicException
import net.postchain.rell.api.base.RellCliEnv
import net.postchain.rell.api.base.RellConfigGen
import net.postchain.rell.base.utils.PostchainGtvUtils
import java.io.OutputStream
import kotlin.io.path.*

fun main(args: Array<String>) {
    RellToolsLogUtils.initLogging()
    RellToolsUtils.runCli(args, RellConfigGenCommand())
}

private class RellConfigGenCommand : RellBaseCommand("RellConfigGen") {
    val module by argument("MODULE", help = "Module name")
    val outputFile by argument("OUTPUT_FILE", help = "Output configuration file").optional()
    val configTemplateFile by option("--template", metavar = "TEMPLATE_FILE", help = "Configuration template file")
    val binaryOutput by option("--binary-output", help = "Write output as binary").flag()

    override fun run() {
        val target = RellToolsUtils.getTarget(sourceDir, module)

        val theConfigTemplateFile = configTemplateFile

        val template = if (theConfigTemplateFile == null) null else {
            val file = Path(theConfigTemplateFile)
            verifyCfg(file.isRegularFile(), "File not found: $file")
            file.readText()
        }

        val configGen = RellConfigGen.create(RellCliEnv.DEFAULT, target)
        val config = configGen.makeConfig(template)

        val theOutputFile = outputFile

        if (theOutputFile != null) {
            val outFile = Path(theOutputFile)
            verifyCfg(outFile.absolute().parent.isDirectory(), "Path not found: $outFile")
            outFile.outputStream().use {
                writeResult(it, config)
            }
        } else {
            writeResult(System.out, config)
        }
    }

    private fun writeResult(os: OutputStream, config: Gtv) {
        val bytes = if (binaryOutput) {
            PostchainGtvUtils.gtvToBytes(config)
        } else {
            val text = RellConfigGen.configToText(config)
            text.toByteArray()
        }
        os.write(bytes)
    }

    private fun verifyCfg(b: Boolean, msg: String) {
        if (!b) {
            throw RellCliBasicException(msg)
        }
    }
}
