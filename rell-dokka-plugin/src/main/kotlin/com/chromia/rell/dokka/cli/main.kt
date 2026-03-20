package com.chromia.rell.dokka.cli

import com.chromia.rell.dokka.RellDokkaGenerator
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.net.URI
import java.util.Calendar

class DokkaCommand : CliktCommand() {
    private val source by option().file(mustExist = true, canBeFile = false).default(File("src"))
    private val target by option().file(canBeFile = false).default(File("out"))
    private val modules by option().split(",")
    private val additionalModules by option().split(",").default(emptyList())
    private val name by option().default("My Rell Dapp")
    private val styles by option().split(",")
    private val assets by option().split(",")
    private val system by option(help = "Generate system library docs", hidden = true).flag()
    private val includes by option(help = "Include documentation files").file().split(",").default(listOf())
    private val sourceLink by option(help = "link to a web site for browsing the source code in format <path>=<url>[#lineSuffix]")
    private val filteredModules by option(help = "list of modules to filter out from navigation page").split(",").default(listOf())

    override fun run() {
        val builder = if (system) RellDokkaPluginConfigurationBuilder.SYSTEM else RellDokkaPluginConfigurationBuilder(
                name,
                modules,
                source,
        )
        builder.targetFolder(target)
                .customStyleSheets(styles)
                .customAssets(assets)
                .footerMessage("© ${Calendar.getInstance().get(Calendar.YEAR)} Chromia")
                .includes(includes)
                .filteredModules(filteredModules)
                .additionalModules(additionalModules)
        sourceLink?.let {
            val localDirectory = it.substringBefore('=', "")
            val remoteUrlWithSuffix = it.substringAfter('=')
            val remoteUrl = remoteUrlWithSuffix.substringBefore("#")
            val remoteLineSuffix = remoteUrlWithSuffix.substringAfterLast('#', "")
            builder.addSourceLink(localDirectory, URI(remoteUrl), if (remoteLineSuffix.isNotEmpty()) "#$remoteLineSuffix" else null)
        }
        RellDokkaGenerator(builder).generate()
    }
}

fun main(argv: Array<String>) = DokkaCommand().main(argv)
