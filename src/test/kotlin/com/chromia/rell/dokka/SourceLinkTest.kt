package com.chromia.rell.dokka

import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import signatures.renderedContent
import utils.TestOutputWriterPlugin
import java.net.URI

class SourceLinkTest : SingleFileRellDokkaPluginTest() {

    private fun Element.getSourceLink() = select(".symbol .floating-right")
            .select("a[href]")
            .attr("href")

    @Test
    fun `source link should lead to name`() {
        val writerPlugin = TestOutputWriterPlugin(failOnOverwrite = false)

        singleFileTestInline("""
                function f_test() = "";
        """.trimIndent(), listOf(writerPlugin), configurationBuilder = {
            addSourceLink(
                    localDirectory = "src",
                    remoteUrl = URI("https://gitlab.com/chromaway/core-tools/rell-dokka-plugin/src").toURL(),
                    remoteLineSuffix = "#L"
            )
        }) {
            renderingStage = { _, _ ->
                val page = writerPlugin.writer.renderedContent("test-dapp/main/f_test.html")
                val sourceLink = page.getSourceLink()
                assertEquals(
                        "https://gitlab.com/chromaway/core-tools/rell-dokka-plugin/src/main.rell#L2",
                        sourceLink
                )
            }
        }
    }
}                                           
