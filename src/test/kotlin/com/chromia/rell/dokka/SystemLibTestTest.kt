package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.containsAtLeast
import com.chromia.rell.dokka.config.RellConfig
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class SystemLibTestTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        this.pluginsConfigurations.add(RellConfig.SYSTEM.toPluginConfig())
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
            }
        }
    }

    @Test
    fun `Rell plugin can generate system lib`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                assertThat(module.packages.map { it.name }).containsAtLeast("rell", "rell.test", "crypto", "op_context", "chain_context")
            }
        }
    }
}