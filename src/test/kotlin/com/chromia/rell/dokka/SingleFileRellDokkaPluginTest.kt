package com.chromia.rell.dokka

import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.config.systemLibExternalDocumentationLink
import org.jetbrains.dokka.ExternalDocumentationLinkImpl
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.plugability.DokkaPlugin
import java.net.URL

const val TEST_DAPP_NAME = "test-dapp"
abstract class SingleFileRellDokkaPluginTest: BaseAbstractTest() {
    protected val configuration = dokkaConfiguration {
        pluginsConfigurations.add(RellDokkaPluginConfiguration(TEST_DAPP_NAME, listOf("main")).toPluginConfig())
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
                externalDocumentationLinks = listOf(systemLibExternalDocumentationLink)
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