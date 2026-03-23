/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.testApi.logger.TestLogger
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import java.io.File

const val TEST_DAPP_NAME = "test-dapp"

abstract class SingleFileRellDokkaPluginTest : BaseAbstractTest(logger = TestLogger(DokkaConsoleLogger(LoggingLevel.WARN))) {
    private fun buildConfiguration(configurationBuilder: RellDokkaPluginConfigurationBuilder.() -> Unit, filteredModules: List<String>) =
            RellDokkaPluginConfigurationBuilder(TEST_DAPP_NAME, listOf("main"), File("src/"))
                    .apply(configurationBuilder)
                    .filteredModules(filteredModules)
                    .build()

    protected fun singleFileTestInline(content: String, pluginOverrides: List<DokkaPlugin> = listOf(), filteredModules: List<String> = listOf(), configurationBuilder: RellDokkaPluginConfigurationBuilder.() -> Unit = {}, block: BaseTestBuilder.() -> Unit) {
        testInline("""
            |/src/main.rell
            |module;
            |$content
        """.trimIndent(), buildConfiguration(configurationBuilder, filteredModules), cleanupOutput = false, pluginOverrides = pluginOverrides, block = block)
    }
}
