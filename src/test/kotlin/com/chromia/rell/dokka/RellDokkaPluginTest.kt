package com.chromia.rell.dokka

import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class RellDokkaPluginTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
            }
        }
    }

    @Test
    fun `rell plugin should find packages and classes`() {
        testInline(
            """
            |/src/main.rell
            |module;
            |/**
            | * My comment
            | */
            |operation my_operation(arg: text, i: integer) {}
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