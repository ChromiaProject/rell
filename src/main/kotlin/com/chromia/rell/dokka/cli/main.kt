package com.chromia.rell.dokka.cli

import com.chromia.rell.dokka.RellDokkaPlugin
import com.chromia.rell.dokka.config.RellConfig
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File

class DokkaCommand : CliktCommand() {

    private val source by option().file(mustExist = true, canBeFile = false).required()
    private val target by option().file(canBeFile = false).default(File("out"))
    private val modules by option().split(",")


    override fun run() {
        val sourceSet = DokkaSourceSetImpl(sourceRoots = setOf(source), sourceSetID = DokkaSourceSetID("main", "dapp"))
        val rellConfig = PluginConfigurationImpl(
                RellDokkaPlugin::class.qualifiedName!!,
                DokkaConfiguration.SerializationFormat.JSON,
                Json.encodeToString(RellConfig(modules))
        )
        val config = DokkaConfigurationImpl(sourceSets = listOf(sourceSet), outputDir = target,
                pluginsConfiguration = listOf(rellConfig)
        )
        DokkaGenerator(config, DokkaConsoleLogger(LoggingLevel.DEBUG)).generate()
    }
}

fun main(argv: Array<String>) = DokkaCommand().main(argv)
