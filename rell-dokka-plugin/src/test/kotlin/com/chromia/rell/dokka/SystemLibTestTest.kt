/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.chromia.rell.dokka.config.RellDokkaPluginConfigurationBuilder
import com.chromia.rell.dokka.dri.from
import com.chromia.rell.dokka.dri.isAlias
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.test.Lib_RellTest
import net.postchain.rell.base.lmodel.L_NamespaceMember
import net.postchain.rell.base.lmodel.L_NamespaceMember_Namespace
import net.postchain.rell.base.lmodel.L_NamespaceMember_Type
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.transformers.documentables.isDeprecated
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.testApi.logger.TestLogger
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class SystemLibTestTest : BaseAbstractTest(logger = TestLogger(DokkaConsoleLogger(LoggingLevel.WARN))) {
    private val configuration = RellDokkaPluginConfigurationBuilder.SYSTEM.build()

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
                val rellPackage = module.rootPackage
                assertNotNull(rellPackage)
                val requireAlias = rellPackage.functions.find { it.name == "requireNotEmpty" }
                assertNotNull(requireAlias)
                assertThat(requireAlias.isDeprecated()).isTrue()
                assertThat(requireAlias.dri.isAlias()).isTrue()
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
                val rellPackage = module.rootPackage
                assertNotNull(rellPackage)
                val assertAlias = rellPackage.functions.find { it.name == "assert_equals" }
                assertNotNull(assertAlias)
                assertThat(assertAlias.dri.isAlias()).isTrue()
            }
        }
    }

    @Test
    fun `Type aliases are found one each type`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.rootPackage
                assertNotNull(rellPackage)
                val integerType = rellPackage.classlikes.find { it.name == "integer" }
                assertNotNull(integerType)
                val alias = integerType.functions.find { it.name == "parseHex" }
                assertNotNull(alias)
                assertThat(alias.dri.isAlias()).isTrue()
                assertThat(alias.isDeprecated()).isTrue()
            }
        }
    }

    @Test
    fun `Structs are created`() {
        testFromData(configuration, cleanupOutput = false) {
            documentablesTransformationStage = { module ->
                val rellPackage = module.rootPackage
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
                val rellPackage = module.rootPackage
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
                val rellPackage = module.rootPackage
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
                                - 11 /* TODO: Not implemented yet */
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
                    it.classlikes.filterIsInstance<DClass>().flatMap { cls -> cls.children.map { child -> child.dri } }
                }
                val expectedTypeDefs = (sysLibDefs + testLibDefs).filterIsInstance<L_NamespaceMember_Type>()
                        .flatMap { it.typeDef.allMembers.all.map { t -> DRI.from(t, DRI.from(it)) } }

                assertThat(documentablesInTypes.size).isEqualTo(expectedTypeDefs.size + 8 /* TODO: Not computed correctly? */)
            }
        }
    }

    private fun List<L_NamespaceMember>.filterEmptyNamespaces() =
            filterNot {
                (it is L_NamespaceMember_Namespace) && it.namespace.getAllDefs().isNotEmpty()
            }

    private val DModule.rootPackage get() = packages.find { it.packageName.isEmpty() }
}
