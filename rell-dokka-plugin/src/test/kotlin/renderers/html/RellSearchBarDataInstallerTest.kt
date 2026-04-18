/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.renderers.html

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.chromia.rell.dokka.SingleFileRellDokkaPluginTest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.dokka.base.renderers.html.SearchRecord
import org.junit.jupiter.api.Test
import utils.TestOutputWriter
import utils.TestOutputWriterPlugin

internal fun TestOutputWriter.pagesJson(): List<SearchRecord> = jacksonObjectMapper().readValue(contents.getValue("scripts/pages.json"))
internal class RellSearchBarDataInstallerTest : SingleFileRellDokkaPluginTest() {

    @Test
    fun `anonymous functions - hashtags are escaped`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)
        singleFileTestInline("""
            @extendable function foo() {}
            @extend(foo) function () {}
        """.trimIndent(), listOf(writerPlugin)) {
            renderingStage = { _, _ ->
                val searchRecords = writerPlugin.writer.pagesJson()
                val anonymousFunction = searchRecords.find {
                    record -> record.description?.contains("main.function#0") == true
                }
                assertThat(anonymousFunction?.location).isEqualTo("test-dapp/main/function%230.html")
            }
        }
    }
}
