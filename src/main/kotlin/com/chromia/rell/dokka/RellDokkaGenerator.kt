package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel

class RellDokkaGenerator(private val configBuilder: RellDokkaPluginConfigurationBuilder) {
    fun generate() {
        val config = configBuilder.build()
        DokkaGenerator(config, DokkaConsoleLogger(LoggingLevel.INFO)).generate()
    }
}