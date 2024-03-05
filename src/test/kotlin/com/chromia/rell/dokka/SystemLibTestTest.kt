package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.config.RellModule
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.lmodel.L_NamespaceMember
import net.postchain.rell.base.lmodel.L_NamespaceMember_Namespace
import net.postchain.rell.base.lmodel.L_NamespaceMember_Type
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.transformers.documentables.isDeprecated
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class SystemLibTestTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        this.pluginsConfigurations.add(RellDokkaPluginConfiguration.SYSTEM_CONFIG.toPluginConfig())
        sourceSets {
            RellModule.entries.forEach {
                add(lazy { it.sourceSet(listOf()) })
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

    @Test
    fun `Aliases are properly named`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "root" }
                assertNotNull(rellPackage)
                val requireAlias = rellPackage.functions.find { it.name == "requireNotEmpty" }
                assertNotNull(requireAlias)
                assertThat(requireAlias.isDeprecated()).isTrue()
                val requireFunction = rellPackage.functions.find { it.name == "require_not_empty" }
                assertNotNull(requireFunction)
                assertThat(requireFunction.isDeprecated()).isFalse()
            }
        }
    }

    @Test
    fun `Test aliases are found in root`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "root" }
                assertNotNull(rellPackage)
                val assertAlias = rellPackage.functions.find { it.name == "assert_equals" }
                assertNotNull(assertAlias)
            }
        }
    }

    @Test
    fun `Structs are created`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "root" }
                assertNotNull(rellPackage)
                val res = rellPackage.classlikes.find { it.name == "gtx_operation" }
                assertNotNull(res)
            }
        }
    }

    @Test
    fun `is_signer is deprecated`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "root" }
                assertNotNull(rellPackage)
                val res = rellPackage.functions.find { it.name == "is_signer" }
                assertNotNull(res)
                assertThat(res.isDeprecated()).isTrue()
            }
        }
    }

    @Test
    fun `aliased types can be found`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.packages.find { it.name == "root" }
                assertNotNull(rellPackage)
                val res = rellPackage.typealiases.find { it.name == "name" }
                assertNotNull(res)
            }
        }
    }

    @Test
    fun `All members of Lib_Rell and Lib_RellTest is covered`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val documentablesInPackage = module.packages.flatMap { it.children }
                val sysLibDefs = Lib_Rell.MODULE.lModule.namespace.getAllDefs()
                val testLibDefs = Lib_RellTest.MODULE.lModule.namespace.getAllDefs()
                val expectedDefs = (sysLibDefs + testLibDefs).filterEmptyNamespaces()
                assertThat(documentablesInPackage.size).isEqualTo(
                        expectedDefs.size
                                - 3 /* Blacklisted types */
                                - 10 /* TODO: Not implemented yet */
                )
            }
        }
    }

    @Test
    fun `All types of Lib_Rell and Lib_RellTest are covered`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val sysLibDefs = Lib_Rell.MODULE.lModule.namespace.getAllDefs()
                val testLibDefs = Lib_RellTest.MODULE.lModule.namespace.getAllDefs()
                val documentablesInTypes = module.packages.flatMap {
                    it.classlikes.flatMap { it.children }
                }
                val expectedTypeDefs = (sysLibDefs+testLibDefs).filterIsInstance<L_NamespaceMember_Type>()
                        .flatMap { it.typeDef.allMembers.all }
                assertThat(documentablesInTypes.size).isEqualTo(expectedTypeDefs.size - 45 /* TODO: Not implemented */)
            }
        }
    }

    private fun List<L_NamespaceMember>.filterEmptyNamespaces() =
            filterNot {
                (it is L_NamespaceMember_Namespace) && it.namespace.getAllDefs().isNotEmpty()
            }
}
