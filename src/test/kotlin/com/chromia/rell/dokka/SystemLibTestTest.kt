package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsAtLeast
import assertk.assertions.doesNotContain
import com.chromia.rell.dokka.config.RellConfig
import org.checkerframework.checker.units.qual.t
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class SystemLibTestTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        this.pluginsConfigurations.add(RellConfig.SYSTEM.toPluginConfig())
        sourceSets {
            RellConfig.SYSTEM_SOURCE_SETS.forEach {
                add(lazy { it })
            }
        }
    }

    @Test
    fun `Rell plugin can generate system lib`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                assertThat(module.packages.map { it.name }).containsAtLeast("rell", "rell.test", "crypto", "op_context", "chain_context")
                val rellPackage = module.packages.find { it.name == "rell" }
                assertNotNull(rellPackage)
                val rellTypes = rellPackage.classlikes.map { it.name }
                assertThat(rellTypes).doesNotContain("guid")
                assertThat(rellTypes).doesNotContain("signer")
            }
        }
    }
}