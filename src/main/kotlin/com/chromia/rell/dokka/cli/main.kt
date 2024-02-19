package com.chromia.rell.dokka.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.dokka.DokkaConfigurationBuilder
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File

class DokkaCommand: CliktCommand() {

    private val source by option().file(mustExist = true, canBeFile = false).required()
    private val target by option().file(canBeFile = false).default(File("out"))


    override fun run() {
        val sourceSet = DokkaSourceSetImpl(sourceRoots = setOf(source), sourceSetID = DokkaSourceSetID("main", "dapp"))
        val config = DokkaConfigurationImpl(sourceSets = listOf(sourceSet), outputDir = target)
        DokkaGenerator(config, DokkaConsoleLogger(LoggingLevel.DEBUG)).generate()
    }
}

fun main(argv: Array<String>) = DokkaCommand().main(argv)
