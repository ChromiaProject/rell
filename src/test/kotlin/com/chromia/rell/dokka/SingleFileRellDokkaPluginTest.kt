package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.plugability.DokkaPlugin

const val TEST_DAPP_NAME = "test-dapp"
abstract class SingleFileRellDokkaPluginTest: BaseAbstractTest() {
    protected val configuration = dokkaConfiguration {
        pluginsConfigurations.add(RellDokkaPluginConfiguration(TEST_DAPP_NAME, listOf("main")).toPluginConfig())
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
            }
        }
    }

    protected fun singleFileTestInline(content: String, pluginOverrides: List<DokkaPlugin> = listOf(), block: BaseTestBuilder.() -> Unit) {
        testInline("""
            |/src/main.rell
            |module;
            |$content
        """.trimIndent(), configuration, cleanupOutput = false, pluginOverrides = pluginOverrides, block = block)
    }
}