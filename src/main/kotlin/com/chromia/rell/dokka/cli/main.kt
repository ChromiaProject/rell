package com.chromia.rell.dokka.cli

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.config.RellConfig
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File

class DokkaCommand : CliktCommand() {

    private val source by option().file(mustExist = true, canBeFile = false).required()
    private val target by option().file(canBeFile = false).default(File("out"))
    private val modules by option().split(",")
    private val name by option().default("My Rell Dapp")
    private val styles by option().default("src/main/resources/styles/styles.css")
    private val assets by option().split(",").default(listOf("src/main/resources/img/chromia-symbol.png", "src/main/resources/fonts"))

    override fun run() {
        val sourceSet = DokkaSourceSetImpl(sourceRoots = setOf(source), sourceSetID = DokkaSourceSetID("main", "dapp"))
        /*val baseConf = DokkaBaseConfiguration(
                customStyleSheets = listOf(this::class.java.getResource("/styles.css")!!.toURI().toPath().toFile())
        )*/
        val dokkaBaseConf = PluginConfigurationImpl(
                DokkaBase::class.qualifiedName!!,
                DokkaConfiguration.SerializationFormat.JSON,
                """{"customStyleSheets": ["$styles"], "customAssets":[${assets.joinToString(",") { "\"$it\"" }}]}"""
        )
        val rellConfig = RellConfig(name, modules).toPluginConfig()
        val config = DokkaConfigurationImpl(
                sourceSets = listOf(sourceSet),
                outputDir = target,
                pluginsConfiguration = listOf(rellConfig, dokkaBaseConf)
        )
        DokkaGenerator(config, DokkaConsoleLogger(LoggingLevel.DEBUG)).generate()
    }
}

fun main(argv: Array<String>) = DokkaCommand().main(argv)
