package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.config.systemLibExternalDocumentationLink
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.testApi.logger.TestLogger
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import testApi.testRunner.DokkaSourceSetBuilder
import testApi.testRunner.TestDokkaConfigurationBuilder

const val TEST_DAPP_NAME = "test-dapp"

abstract class SingleFileRellDokkaPluginTest : BaseAbstractTest(logger = TestLogger(DokkaConsoleLogger(LoggingLevel.WARN))) {
    private fun buildConfiguration(configurationBuilder: TestDokkaConfigurationBuilder.() -> Unit, sourceSetBuilder: DokkaSourceSetBuilder.() -> Unit) = dokkaConfiguration {
        pluginsConfigurations.add(RellDokkaPluginConfiguration(TEST_DAPP_NAME, listOf("main")).toPluginConfig())
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
                externalDocumentationLinks = listOf(systemLibExternalDocumentationLink)
                sourceSetBuilder()
            }
        }
        this.configurationBuilder()
    }

    protected fun singleFileTestInline(content: String, pluginOverrides: List<DokkaPlugin> = listOf(), configurationBuilder: TestDokkaConfigurationBuilder.() -> Unit = {}, sourceSetBuilder: DokkaSourceSetBuilder.() -> Unit = {}, block: BaseTestBuilder.() -> Unit) {
        testInline("""
            |/src/main.rell
            |module;
            |$content
        """.trimIndent(), buildConfiguration(configurationBuilder, sourceSetBuilder), cleanupOutput = false, pluginOverrides = pluginOverrides, block = block)
    }
}
