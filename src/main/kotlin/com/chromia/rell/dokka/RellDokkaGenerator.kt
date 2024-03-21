package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration2
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel

class RellDokkaGenerator(private val configBuilder: RellDokkaPluginConfiguration2) {
    fun generate() {
        val config = configBuilder.getConfig()
        DokkaGenerator(config, DokkaConsoleLogger(LoggingLevel.WARN)).generate()
    }
}