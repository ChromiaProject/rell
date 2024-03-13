package com.chromia.rell.dokka.cli

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.config.RellModule
import com.chromia.rell.dokka.config.systemLibExternalDocumentationLink
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File

class DokkaCommand : CliktCommand() {

    private val source by option().file(mustExist = true, canBeFile = false).default(File("src"))
    private val target by option().file(canBeFile = false).default(File("out"))
    private val modules by option().split(",")
    private val name by option().default("My Rell Dapp")
    private val styles by option()
    private val assets by option().split(",")
    private val system by option(help = "Generate system library docs", hidden = true).flag()
    private val includes by option(help = "Include documentation files").file().split(",").default(listOf(File("src/main/resources/rell.md")))

    override fun run() {
        val rellConfig = if (system) RellDokkaPluginConfiguration.SYSTEM_CONFIG else RellDokkaPluginConfiguration(name, modules)
        val sourceSets =
                if (system) {
                    RellModule.entries.map { it.sourceSet(includes) }
                } else {
                    listOf(
                            DokkaSourceSetImpl(
                                    sourceRoots = setOf(source),
                                    sourceSetID = DokkaSourceSetID("main", "dapp"),
                                    displayName = "dapp",
                                    analysisPlatform = Platform.wasm,
                                    externalDocumentationLinks = setOf(systemLibExternalDocumentationLink)
                            )
                    )
                }
        val dokkaBaseConf = PluginConfigurationImpl(
                DokkaBase::class.qualifiedName!!,
                DokkaConfiguration.SerializationFormat.JSON,
                """{ 
                        ${styles?.let { "\"customStyleSheets\": [\"$it\"]," } ?: ""}
                        ${assets?.let { a -> "\"customAssets\":[${a.joinToString(",") { "\"$it\"" }}]," } ?: ""}
                        "footerMessage": "© 2024 Chromia"
                    }"""
        )
        val config = DokkaConfigurationImpl(
                moduleName = rellConfig.name,
                sourceSets = sourceSets,
                outputDir = target,
                pluginsConfiguration = listOf(rellConfig.toPluginConfig(), dokkaBaseConf)
        )
        DokkaGenerator(config, DokkaConsoleLogger(LoggingLevel.DEBUG)).generate()
    }
}

fun main(argv: Array<String>) = DokkaCommand().main(argv)
