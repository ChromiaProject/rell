package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_DAPP_NAME = "Test dapp"

class RellDokkaPluginTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        pluginsConfigurations.add(RellDokkaPluginConfiguration(TEST_DAPP_NAME, listOf("main")).toPluginConfig())
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
            }
        }
    }

    private fun singleFileTestInline(content: String, block: BaseTestBuilder.() -> Unit) {
        testInline("""
            |/src/main.rell
            |module;
            |$content
        """.trimIndent(), configuration, cleanupOutput = false, block = block)
    }

    @Test
    fun `rell module can be found`() {
        singleFileTestInline("") {
            documentablesTransformationStage = { m ->
                assertThat(m.name).isEqualTo(TEST_DAPP_NAME)
                assertThat(m.dri.toString()).isEqualTo(DRI("").toString())
                assertThat(m.packages.size).isEqualTo(1)
                assertThat(m.packages.first().packageName).isEqualTo("main")
                assertThat(m.packages.first().dri.toString()).isEqualTo(DRI("main").toString())
            }
        }
    }

    @Test
    fun `global constant can be found`() {
        singleFileTestInline("val my_constant = 32;") {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val properties = testPackage.properties
                assertThat(properties.size).isEqualTo(1)
                val myConstant = properties.first()
                assertThat(myConstant.name).isEqualTo("my_constant")
                assertThat(myConstant.dri.toString()).isEqualTo(DRI("main", classNames = "my_constant").toString())
                assertThat(myConstant.visibility.values.first()).isEqualTo(KotlinVisibility.Public)
                assertThat(myConstant.modifier.values.first()).isEqualTo(KotlinModifier.Empty)
                assertTrue(myConstant.type is GenericTypeConstructor)
                assertThat((myConstant.type as GenericTypeConstructor).dri.classNames).isEqualTo("integer")
            }
        }
    }

    @Test
    fun `rell plugin should find packages and classes`() {
        testInline(
            """
            |/src/main.rell
            |module;
            |entity foo { name; }
            |/**
            | * My comment
            | */
            |operation my_operation(arg: text, i: integer) {}
            |function my_fun(arg: integer) = foo @{};
            """.trimIndent(), configuration, cleanupOutput = false
        ) {
            documentablesTransformationStage = { module ->
                val testedPackage = module.packages.find { it.name == "main" }
                val testedClass = testedPackage?.functions?.find { it.name == "my_operation" }

                assertNotNull(testedPackage)
                assertNotNull(testedClass)
            }
        }
    }
}