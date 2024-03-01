package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.doesNotContain
import com.chromia.rell.dokka.config.RellConfig
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

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
                val rellTypes = rellPackage!!.classlikes.map { it.name }
                assertThat(rellTypes).doesNotContain("guid")
                assertThat(rellTypes).doesNotContain("signer")
            }
        }
    }
    @Test
    fun `Aliases are properly named`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "[root]" }
                assertNotNull(rellPackage)
                val requireAlias = rellPackage!!.functions.find { it.name == "requireNotEmpty" }
                assertNotNull(requireAlias)
                val requireFunction = rellPackage.functions.find { it.name == "require_not_empty" }
                assertNotNull(requireFunction)
            }
        }
    }

    @Test
    fun `Test aliases are found in root`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "[root]" }
                assertNotNull(rellPackage)
                val assertAlias = rellPackage!!.functions.find { it.name == "assert_equals" }
                assertNotNull(assertAlias)
            }
        }
    }

    @Test
    fun `Structs are created`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "[root]" }
                assertNotNull(rellPackage)
                val res = rellPackage!!.classlikes.find { it.name == "gtx_operation" }
                assertNotNull(res)
            }
        }
    }
}
